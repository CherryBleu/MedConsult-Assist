package com.medconsult.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseCandidate;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MatchSource;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;
import com.medconsult.ai.util.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DiseaseCacheService {
    private final StringRedisTemplate redisTemplate;
    private final AiProperties properties;

    public DiseaseCacheService(StringRedisTemplate redisTemplate, AiProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Optional<DiseaseKnowledge> get(String userText, DiseaseCandidate candidate, MetadataQuery metadataQuery) {
        try {
            String normalizedName = normalizeCandidateName(candidate.diseaseName());
            if (normalizedName.isBlank()) {
                return Optional.empty();
            }
            String cached = redisTemplate.opsForValue().get(stableDiseaseKey(normalizedName));
            if (cached == null || cached.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(readCachedKnowledge(cached));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public void put(String userText, DiseaseCandidate candidate, MetadataQuery metadataQuery, DiseaseKnowledge knowledge) {
        if (knowledge == null) {
            return;
        }
        try {
            String normalizedName = normalizeCandidateName(candidate.diseaseName());
            if (normalizedName.isBlank()) {
                return;
            }
            ObjectNode root = JsonUtils.MAPPER.createObjectNode();
            root.put("source", knowledge.source().name());
            root.put("score", knowledge.score());
            root.set("document", JsonUtils.MAPPER.valueToTree(toDocumentMap(knowledge)));
            Duration ttl = Duration.ofSeconds(Math.max(60, properties.redis().cacheSeconds()));
            redisTemplate.opsForValue().set(stableDiseaseKey(normalizedName), JsonUtils.toJson(root), ttl);
        } catch (RuntimeException ignored) {
            // Cache failure must not affect medical response generation.
        }
    }

    private DiseaseKnowledge readCachedKnowledge(String cached) {
        JsonNode root = JsonUtils.readTree(cached);
        if (root.has("document")) {
            JsonNode document = root.path("document");
            MatchSource source = matchSource(root.path("source").asText());
            double score = root.path("score").asDouble(0);
            return fromDocument(document, score, source);
        }
        Map<String, Object> value = JsonUtils.toMap(cached);
        return new DiseaseKnowledge(
                string(value.get("vectorId")),
                string(value.get("sourceId")),
                string(value.get("diseaseName")),
                string(value.get("desc")),
                list(value.get("symptoms")),
                (Map<String, Object>) value.getOrDefault("metadata", Map.of()),
                string(value.get("fieldName")),
                string(value.get("chunkText")),
                number(value.get("score")),
                matchSource(value.get("source"))
        );
    }

    private DiseaseKnowledge fromDocument(JsonNode document, double score, MatchSource source) {
        String name = document.path("name").asText("");
        String desc = document.path("desc").asText("");
        List<String> symptoms = list(JsonUtils.MAPPER.convertValue(document.path("symptom"), Object.class));
        Map<String, Object> metadata = new LinkedHashMap<>();
        document.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!List.of("_id", "name", "desc", "symptom").contains(key)) {
                metadata.put(key, JsonUtils.MAPPER.convertValue(entry.getValue(), Object.class));
            }
        });
        String chunk = "疾病名称：" + name + "\n症状：" + String.join("、", symptoms) + "\n疾病描述：" + desc;
        return new DiseaseKnowledge(
                document.path("_id").asText(""),
                "DISEASE_JSON:" + name,
                name,
                desc,
                symptoms,
                metadata,
                "name",
                chunk,
                score,
                source
        );
    }

    private Map<String, Object> toDocumentMap(DiseaseKnowledge knowledge) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("_id", knowledge.vectorId());
        document.put("name", knowledge.diseaseName());
        document.put("desc", knowledge.desc());
        document.put("symptom", knowledge.symptoms());
        document.putAll(knowledge.metadata());
        return document;
    }

    private String stableDiseaseKey(String diseaseName) {
        return properties.redis().keyPrefix() + "disease:" + diseaseName;
    }

    private static String normalizeCandidateName(String diseaseName) {
        return string(diseaseName)
                .replace("待鉴别", "")
                .replace("可能", "")
                .trim();
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(string(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static List<String> list(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static MatchSource matchSource(Object value) {
        try {
            return MatchSource.valueOf(string(value));
        } catch (RuntimeException ex) {
            return MatchSource.REDIS_CACHE;
        }
    }
}

package com.medconsult.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MatchSource;
import com.medconsult.ai.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MilvusRestClient {
    private static final Logger log = LoggerFactory.getLogger(MilvusRestClient.class);

    private final AiProperties properties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public MilvusRestClient(AiProperties properties) {
        this.properties = properties;
    }

    public List<DiseaseKnowledge> search(List<Float> vector, int topK) {
        long started = System.currentTimeMillis();
        AiProperties.MilvusProperties milvus = properties.milvus();
        if (vector == null || vector.isEmpty() || milvus == null || isBlank(milvus.uri()) || isBlank(milvus.collection())) {
            log.info("[AI-TIMER] milvus.search={}ms success=false reason=config_or_vector_missing", elapsed(started));
            return List.of();
        }
        try {
            ObjectNode payload = JsonUtils.MAPPER.createObjectNode();
            payload.put("dbName", milvus.database());
            payload.put("collectionName", milvus.collection());
            payload.put("annsField", "vector");
            payload.put("limit", Math.max(1, topK));
            payload.putArray("outputFields").add("id").add("name").add("text").add("metadata");
            ArrayNode data = payload.putArray("data");
            ArrayNode vectorNode = data.addArray();
            vector.forEach(vectorNode::add);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(trimRightSlash(milvus.uri()) + "/v2/vectordb/entities/search"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(payload), StandardCharsets.UTF_8));
            if (!isBlank(milvus.token())) {
                builder.header("Authorization", "Bearer " + milvus.token());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.info("[AI-TIMER] milvus.search={}ms success=false status={}", elapsed(started), response.statusCode());
                return List.of();
            }
            JsonNode root = JsonUtils.readTree(response.body());
            if (root.path("code").asInt(0) != 0 || !root.path("data").isArray()) {
                log.info("[AI-TIMER] milvus.search={}ms success=false code={}", elapsed(started), root.path("code").asInt(0));
                return List.of();
            }
            List<DiseaseKnowledge> matches = new ArrayList<>();
            for (JsonNode entity : root.path("data")) {
                double score = entity.path("distance").asDouble(entity.path("score").asDouble(0));
                if (score < milvus.minScore()) {
                    continue;
                }
                matches.add(toKnowledge(entity, score));
            }
            log.info("[AI-TIMER] milvus.search={}ms success=true matches={}", elapsed(started), matches.size());
            return matches;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.info("[AI-TIMER] milvus.search={}ms success=false error={}", elapsed(started), ex.getClass().getSimpleName());
            return List.of();
        } catch (RuntimeException ex) {
            log.info("[AI-TIMER] milvus.search={}ms success=false error={}", elapsed(started), ex.getClass().getSimpleName());
            return List.of();
        }
    }

    private static DiseaseKnowledge toKnowledge(JsonNode entity, double score) {
        String id = entity.path("id").asText("");
        String name = entity.path("name").asText("");
        String text = entity.path("text").asText("");
        Map<String, Object> metadata = parseMetadata(entity.path("metadata"));
        return new DiseaseKnowledge(
                id,
                "DISEASE_JSON:" + name,
                name,
                extractDescription(text),
                extractSymptoms(text),
                metadata,
                metadata.getOrDefault("field_name", "text").toString(),
                text,
                score,
                MatchSource.MILVUS_SEMANTIC
        );
    }

    private static Map<String, Object> parseMetadata(JsonNode metadata) {
        if (metadata == null || metadata.isMissingNode() || metadata.isNull()) {
            return new LinkedHashMap<>();
        }
        if (metadata.isTextual()) {
            return new LinkedHashMap<>(JsonUtils.toMap(metadata.asText("")));
        }
        return JsonUtils.MAPPER.convertValue(metadata, Map.class);
    }

    private static String extractDescription(String text) {
        return Optional.ofNullable(text)
                .map(value -> {
                    int index = value.indexOf("疾病描述：");
                    return index < 0 ? value : value.substring(index + "疾病描述：".length()).trim();
                })
                .orElse("");
    }

    private static List<String> extractSymptoms(String text) {
        String value = text == null ? "" : text;
        int start = value.indexOf("症状：");
        if (start < 0) {
            return List.of();
        }
        int end = value.indexOf('\n', start);
        String symptoms = end < 0 ? value.substring(start + "症状：".length()) : value.substring(start + "症状：".length(), end);
        return List.of(symptoms.split("[、,，]")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static String trimRightSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static long elapsed(long started) {
        return System.currentTimeMillis() - started;
    }
}

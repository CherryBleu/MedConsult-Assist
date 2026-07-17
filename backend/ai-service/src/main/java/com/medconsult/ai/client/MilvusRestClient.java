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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

@Component
public class MilvusRestClient {
    private static final Logger log = LoggerFactory.getLogger(MilvusRestClient.class);

    private final AiProperties properties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public MilvusRestClient(AiProperties properties) {
        this.properties = properties;
    }

    public long countEntities() {
        long started = System.currentTimeMillis();
        AiProperties.MilvusProperties milvus = properties.milvus();
        if (milvus == null || isBlank(milvus.uri()) || isBlank(milvus.collection())) {
            log.info("[AI-TIMER] milvus.countEntities={}ms success=false reason=config_missing", elapsed(started));
            return -1L;
        }
        try {
            ObjectNode payload = JsonUtils.MAPPER.createObjectNode();
            payload.put("dbName", milvus.database());
            payload.put("collectionName", milvus.collection());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(trimRightSlash(milvus.uri()) + "/v2/vectordb/collections/get_stats"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(payload), StandardCharsets.UTF_8));
            if (!isBlank(milvus.token())) {
                builder.header("Authorization", "Bearer " + milvus.token());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.info("[AI-TIMER] milvus.countEntities={}ms success=false status={}", elapsed(started), response.statusCode());
                return -1L;
            }
            JsonNode root = JsonUtils.readTree(response.body());
            if (root.path("code").asInt(0) != 0) {
                log.info("[AI-TIMER] milvus.countEntities={}ms success=false code={}", elapsed(started), root.path("code").asInt(0));
                return -1L;
            }
            long count = parseRowCount(root.path("data"));
            log.info("[AI-TIMER] milvus.countEntities={}ms success={} count={}", elapsed(started), count >= 0, count);
            return count;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.info("[AI-TIMER] milvus.countEntities={}ms success=false error=InterruptedException", elapsed(started));
            return -1L;
        } catch (IOException | RuntimeException ex) {
            log.info("[AI-TIMER] milvus.countEntities={}ms success=false error={}", elapsed(started), ex.getClass().getSimpleName());
            return -1L;
        }
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
                    .timeout(searchTimeout(milvus))
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
                OptionalDouble normalizedScore = normalizedScore(entity, milvus.metricType());
                if (normalizedScore.isEmpty() || !passesMinScore(normalizedScore.getAsDouble(), milvus.minScore())) {
                    continue;
                }
                matches.add(toKnowledge(entity, normalizedScore.getAsDouble()));
            }
            log.info("[AI-TIMER] milvus.search={}ms success=true matches={}", elapsed(started), matches.size());
            return matches;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.info("[AI-TIMER] milvus.search={}ms success=false error=InterruptedException", elapsed(started));
            return List.of();
        } catch (IOException ex) {
            log.info("[AI-TIMER] milvus.search={}ms success=false error=IOException", elapsed(started));
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

    static OptionalDouble normalizedScore(JsonNode entity, String metricType) {
        if (entity == null || entity.isMissingNode() || entity.isNull()) {
            return OptionalDouble.empty();
        }
        JsonNode distance = entity.path("distance");
        if (distance.isNumber()) {
            double value = distance.asDouble();
            if (isLowerDistanceBetter(metricType)) {
                return OptionalDouble.of(1.0 / (1.0 + Math.max(0.0, value)));
            }
            return OptionalDouble.of(value);
        }
        JsonNode score = entity.path("score");
        if (score.isNumber()) {
            return OptionalDouble.of(score.asDouble());
        }
        return OptionalDouble.empty();
    }

    static boolean passesMinScore(double score, double minScore) {
        return Double.isFinite(score) && score >= minScore;
    }

    static Duration searchTimeout(AiProperties.MilvusProperties milvus) {
        int seconds = milvus == null ? 15 : milvus.searchTimeoutSeconds();
        return Duration.ofSeconds(Math.max(1, seconds));
    }

    private static boolean isLowerDistanceBetter(String metricType) {
        String normalized = metricType == null ? "COSINE" : metricType.trim().toUpperCase(Locale.ROOT);
        return "L2".equals(normalized) || "EUCLIDEAN".equals(normalized);
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

    private static long parseRowCount(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return -1L;
        }
        List<String> keys = List.of("rowCount", "row_count", "num_entities", "numEntities", "count");
        if (data.isObject()) {
            for (String key : keys) {
                JsonNode value = data.path(key);
                if (value.isNumber()) {
                    return value.asLong();
                }
                if (value.isTextual()) {
                    try {
                        return Long.parseLong(value.asText());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (data.isArray()) {
            for (JsonNode item : data) {
                long parsed = parseRowCount(item);
                if (parsed >= 0) {
                    return parsed;
                }
            }
        }
        return -1L;
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

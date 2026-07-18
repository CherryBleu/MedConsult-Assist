package com.medconsult.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MatchSource;
import com.medconsult.ai.util.JsonUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilvusRestClientTest {

    @Test
    void countEntitiesShouldPostStatsPayloadWithBearerTokenAndParseStringCount() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = server("/v2/vectordb/collections/get_stats", requestBody, authorization,
                200, "{\"code\":0,\"data\":{\"row_count\":\"8807\"}}");
        server.start();
        try {
            String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            MilvusRestClient client = new MilvusRestClient(properties(uri, 0.6, "COSINE", 2, "secret-token"));

            long count = client.countEntities();

            assertEquals(8807L, count);
            assertEquals("Bearer secret-token", authorization.get());
            JsonNode payload = JsonUtils.readTree(requestBody.get());
            assertEquals("medical", payload.path("dbName").asText());
            assertEquals("data", payload.path("collectionName").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void countEntitiesShouldReturnMinusOneForMissingConfigAndBadResponses() throws Exception {
        assertEquals(-1L, new MilvusRestClient(propertiesWithMilvus(null)).countEntities());
        assertEquals(-1L, new MilvusRestClient(properties("", 0.6, "COSINE", 2)).countEntities());

        HttpServer httpError = server("/v2/vectordb/collections/get_stats", new AtomicReference<>(),
                new AtomicReference<>(), 503, "{\"code\":0,\"data\":{\"rowCount\":1}}");
        httpError.start();
        try {
            String uri = "http://127.0.0.1:" + httpError.getAddress().getPort();
            assertEquals(-1L, new MilvusRestClient(properties(uri, 0.6, "COSINE", 2)).countEntities());
        } finally {
            httpError.stop(0);
        }

        HttpServer milvusError = server("/v2/vectordb/collections/get_stats", new AtomicReference<>(),
                new AtomicReference<>(), 200, "{\"code\":7,\"data\":{\"rowCount\":1}}");
        milvusError.start();
        try {
            String uri = "http://127.0.0.1:" + milvusError.getAddress().getPort();
            assertEquals(-1L, new MilvusRestClient(properties(uri, 0.6, "COSINE", 2)).countEntities());
        } finally {
            milvusError.stop(0);
        }
    }

    @Test
    void searchShouldPostVectorPayloadAndMapTextualMetadata() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        String text = "症状：发热、咳嗽\n疾病描述：急性上呼吸道感染";
        String metadata = JsonUtils.toJson(Map.of("field_name", "symptom", "cause", "virus"));
        String response = JsonUtils.toJson(Map.of(
                "code", 0,
                "data", List.of(Map.of(
                        "id", "d1",
                        "name", "Flu",
                        "text", text,
                        "metadata", metadata,
                        "score", 0.77
                ))
        ));
        HttpServer server = server("/v2/vectordb/entities/search", requestBody, authorization, 200, response);
        server.start();
        try {
            String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            MilvusRestClient client = new MilvusRestClient(properties(uri, 0.6, "COSINE", 0, "secret-token"));

            List<DiseaseKnowledge> matches = client.search(List.of(0.1f, 0.2f), 0);

            assertEquals(1, matches.size());
            DiseaseKnowledge match = matches.getFirst();
            assertEquals("d1", match.vectorId());
            assertEquals("DISEASE_JSON:Flu", match.sourceId());
            assertEquals("Flu", match.diseaseName());
            assertEquals("急性上呼吸道感染", match.desc());
            assertEquals(List.of("发热", "咳嗽"), match.symptoms());
            assertEquals("virus", match.metadata().get("cause"));
            assertEquals("symptom", match.fieldName());
            assertEquals(text, match.chunkText());
            assertEquals(0.77, match.score());
            assertEquals(MatchSource.MILVUS_SEMANTIC, match.source());
            assertEquals("Bearer secret-token", authorization.get());

            JsonNode payload = JsonUtils.readTree(requestBody.get());
            assertEquals("medical", payload.path("dbName").asText());
            assertEquals("data", payload.path("collectionName").asText());
            assertEquals("vector", payload.path("annsField").asText());
            assertEquals(1, payload.path("limit").asInt());
            assertEquals("metadata", payload.path("outputFields").get(3).asText());
            assertEquals(0.1, payload.path("data").get(0).get(0).asDouble(), 0.0001);
            assertEquals(0.2, payload.path("data").get(0).get(1).asDouble(), 0.0001);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void searchShouldReturnEmptyForMissingConfigAndBadResponses() throws Exception {
        MilvusRestClient noConfig = new MilvusRestClient(propertiesWithMilvus(null));
        assertTrue(noConfig.search(List.of(0.1f), 3).isEmpty());

        MilvusRestClient noVector = new MilvusRestClient(properties("http://localhost:19530", 0.6, "COSINE", 2));
        assertTrue(noVector.search(List.of(), 3).isEmpty());

        HttpServer httpError = server("/v2/vectordb/entities/search", new AtomicReference<>(),
                new AtomicReference<>(), 500, "{\"code\":0,\"data\":[]}");
        httpError.start();
        try {
            String uri = "http://127.0.0.1:" + httpError.getAddress().getPort();
            assertTrue(new MilvusRestClient(properties(uri, 0.6, "COSINE", 2)).search(List.of(0.1f), 3).isEmpty());
        } finally {
            httpError.stop(0);
        }

        HttpServer milvusError = server("/v2/vectordb/entities/search", new AtomicReference<>(),
                new AtomicReference<>(), 200, "{\"code\":7,\"data\":[]}");
        milvusError.start();
        try {
            String uri = "http://127.0.0.1:" + milvusError.getAddress().getPort();
            assertTrue(new MilvusRestClient(properties(uri, 0.6, "COSINE", 2)).search(List.of(0.1f), 3).isEmpty());
        } finally {
            milvusError.stop(0);
        }

        HttpServer malformed = server("/v2/vectordb/entities/search", new AtomicReference<>(),
                new AtomicReference<>(), 200, "{\"code\":0,\"data\":{}}");
        malformed.start();
        try {
            String uri = "http://127.0.0.1:" + malformed.getAddress().getPort();
            assertTrue(new MilvusRestClient(properties(uri, 0.6, "COSINE", 2)).search(List.of(0.1f), 3).isEmpty());
        } finally {
            malformed.stop(0);
        }
    }

    @Test
    void searchShouldFilterByNormalizedL2Similarity() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/vectordb/entities/search", exchange -> {
            byte[] body = """
                    {"code":0,"data":[
                      {"id":"d1","name":"近距离疾病","text":"症状：咳嗽\\n疾病描述：近距离命中","metadata":{"field_name":"symptoms"},"distance":0.05},
                      {"id":"d2","name":"远距离疾病","text":"症状：咳嗽\\n疾病描述：远距离噪声","metadata":{"field_name":"symptoms"},"distance":1.0}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();
        try {
            String uri = "http://127.0.0.1:" + server.getAddress().getPort();
            MilvusRestClient client = new MilvusRestClient(properties(uri, 0.9, "L2", 2));

            List<DiseaseKnowledge> matches = client.search(List.of(0.1f, 0.2f), 2);

            assertEquals(List.of("近距离疾病"), matches.stream().map(DiseaseKnowledge::diseaseName).toList());
            assertEquals(1.0 / 1.05, matches.getFirst().score(), 0.0001);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void l2DistanceShouldBeConvertedToSimilarityBeforeMinScoreFiltering() {
        JsonNode entity = JsonUtils.readTree("{\"distance\":0.05}");

        double score = MilvusRestClient.normalizedScore(entity, "L2").orElseThrow();

        assertEquals(1.0 / 1.05, score, 0.0001);
        assertTrue(MilvusRestClient.passesMinScore(score, 0.9));
    }

    @Test
    void l2DistanceShouldRejectLowSimilarityAfterNormalization() {
        JsonNode entity = JsonUtils.readTree("{\"distance\":1.0}");

        double score = MilvusRestClient.normalizedScore(entity, "L2").orElseThrow();

        assertEquals(0.5, score, 0.0001);
        assertFalse(MilvusRestClient.passesMinScore(score, 0.9));
    }

    @Test
    void cosineAndIpScoresShouldRemainHigherIsBetter() {
        JsonNode entity = JsonUtils.readTree("{\"distance\":0.88}");

        double score = MilvusRestClient.normalizedScore(entity, "COSINE").orElseThrow();

        assertEquals(0.88, score, 0.0001);
        assertTrue(MilvusRestClient.passesMinScore(score, 0.6));
    }

    @Test
    void scoreFieldShouldBeUsedWhenDistanceIsMissing() {
        JsonNode entity = JsonUtils.readTree("{\"score\":0.72}");

        double score = MilvusRestClient.normalizedScore(entity, "IP").orElseThrow();

        assertEquals(0.72, score, 0.0001);
        assertTrue(MilvusRestClient.passesMinScore(score, 0.6));
    }

    @Test
    void missingOrInvalidScoresShouldBeRejected() {
        assertTrue(MilvusRestClient.normalizedScore(null, "COSINE").isEmpty());
        assertTrue(MilvusRestClient.normalizedScore(JsonUtils.readTree("{}"), "COSINE").isEmpty());
        assertFalse(MilvusRestClient.passesMinScore(Double.NaN, 0.1));
        assertFalse(MilvusRestClient.passesMinScore(Double.POSITIVE_INFINITY, 0.1));
    }

    @Test
    void euclideanAliasShouldUseL2DistanceSemantics() {
        JsonNode entity = JsonUtils.readTree("{\"distance\":0.05}");
        AiProperties.MilvusProperties properties = new AiProperties.MilvusProperties(
                "http://localhost:19530", "token", "medical", "data", 0.9, "euclidean", 5);

        double score = MilvusRestClient.normalizedScore(entity, properties.metricType()).orElseThrow();

        assertEquals("L2", properties.metricType());
        assertEquals(1.0 / 1.05, score, 0.0001);
        assertTrue(MilvusRestClient.passesMinScore(score, properties.minScore()));
    }

    @Test
    void milvusPropertiesShouldDefaultMetricAndSearchTimeoutForExistingDeployments() {
        AiProperties.MilvusProperties properties = new AiProperties.MilvusProperties(
                "http://localhost:19530", "token", "medical", "data", 0.6, "", 0);

        assertEquals("COSINE", properties.metricType());
        assertEquals(15, properties.searchTimeoutSeconds());
        assertEquals(Duration.ofSeconds(15), MilvusRestClient.searchTimeout(properties));
        assertEquals(Duration.ofSeconds(15), MilvusRestClient.searchTimeout(null));
    }

    @Test
    void countEntitiesShouldParseNestedArrayCountsAndReturnMinusOneForInvalidJsonOrUri() throws Exception {
        HttpServer nested = server("/v2/vectordb/collections/get_stats", new AtomicReference<>(),
                new AtomicReference<>(), 200, "{\"code\":0,\"data\":[{\"count\":42}]}");
        nested.start();
        try {
            String uri = "http://127.0.0.1:" + nested.getAddress().getPort();
            assertEquals(42L, new MilvusRestClient(properties(uri, 0.6, "COSINE", 2)).countEntities());
        } finally {
            nested.stop(0);
        }

        HttpServer invalidJson = server("/v2/vectordb/collections/get_stats", new AtomicReference<>(),
                new AtomicReference<>(), 200, "not-json");
        invalidJson.start();
        try {
            String uri = "http://127.0.0.1:" + invalidJson.getAddress().getPort();
            assertEquals(-1L, new MilvusRestClient(properties(uri, 0.6, "COSINE", 2)).countEntities());
        } finally {
            invalidJson.stop(0);
        }

        assertEquals(-1L, new MilvusRestClient(properties("http://[invalid", 0.6, "COSINE", 2))
                .countEntities());
    }

    @Test
    void searchShouldSkipMissingAndLowScoresAndDefaultMetadataFields() throws Exception {
        String response = """
                {"code":0,"data":[
                  {"id":"missing","name":"NoScore","text":"plain text","metadata":{}},
                  {"id":"low","name":"LowScore","text":"plain text","metadata":{},"score":0.1},
                  {"id":"ok","name":"Plain","text":"plain description","metadata":null,"score":0.8}
                ]}
                """;
        HttpServer server = server("/v2/vectordb/entities/search", new AtomicReference<>(),
                new AtomicReference<>(), 200, response);
        server.start();
        try {
            String uri = "http://127.0.0.1:" + server.getAddress().getPort();
            MilvusRestClient client = new MilvusRestClient(properties(uri, 0.6, "COSINE", 2));

            List<DiseaseKnowledge> matches = client.search(List.of(0.1f, 0.2f), 3);

            assertEquals(1, matches.size());
            DiseaseKnowledge match = matches.getFirst();
            assertEquals("ok", match.vectorId());
            assertEquals("Plain", match.diseaseName());
            assertEquals("plain description", match.desc());
            assertTrue(match.symptoms().isEmpty());
            assertTrue(match.metadata().isEmpty());
            assertEquals("text", match.fieldName());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void searchShouldReturnEmptyForInvalidUriRuntimeFailure() {
        MilvusRestClient client = new MilvusRestClient(properties("http://[invalid", 0.6, "COSINE", 2));

        assertTrue(client.search(List.of(0.1f), 1).isEmpty());
    }

    private static AiProperties properties(String milvusUri, double minScore, String metricType, int timeoutSeconds) {
        return properties(milvusUri, minScore, metricType, timeoutSeconds, "");
    }

    private static AiProperties properties(String milvusUri, double minScore, String metricType, int timeoutSeconds,
                                           String token) {
        return propertiesWithMilvus(new AiProperties.MilvusProperties(milvusUri, token, "medical", "data",
                minScore, metricType, timeoutSeconds));
    }

    private static AiProperties propertiesWithMilvus(AiProperties.MilvusProperties milvus) {
        return new AiProperties(
                new AiProperties.LlmProperties("http://llm", "key", "model", 5),
                new AiProperties.EmbeddingProperties("http://embedding", "key", "model", 5),
                new AiProperties.MongoProperties("mongodb://localhost:27017", "medconsult", "diseases"),
                new AiProperties.RedisProperties("medical:", 60),
                milvus,
                new AiProperties.ImagingProperties("provider", "model"),
                new AiProperties.VisionProperties("http://vision", "key", "model", 5, 1024),
                new AiProperties.FileStorageProperties("http://minio", "http://minio", "ak", "sk", "us-east-1", "bucket", "imaging", "chunks", true, 300),
                new AiProperties.InternalProperties("ai-service", "key"),
                new AiProperties.RateLimitProperties(true, 60, 60, false, Map.of()),
                new AiProperties.RagProperties(8807, 8807, 512, true, false)
        );
    }

    private static HttpServer server(String path, AtomicReference<String> requestBody,
                                     AtomicReference<String> authorization,
                                     int status, String responseText) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        return server;
    }
}

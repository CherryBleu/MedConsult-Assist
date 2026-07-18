package com.medconsult.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.util.JsonUtils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilvusRestClientTest {

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
    }

    private static AiProperties properties(String milvusUri, double minScore, String metricType, int timeoutSeconds) {
        return new AiProperties(
                new AiProperties.LlmProperties("http://llm", "key", "model", 5),
                new AiProperties.EmbeddingProperties("http://embedding", "key", "model", 5),
                new AiProperties.MongoProperties("mongodb://localhost:27017", "medconsult", "diseases"),
                new AiProperties.RedisProperties("medical:", 60),
                new AiProperties.MilvusProperties(milvusUri, "", "medical", "data", minScore, metricType, timeoutSeconds),
                new AiProperties.ImagingProperties("provider", "model"),
                new AiProperties.VisionProperties("http://vision", "key", "model", 5, 1024),
                new AiProperties.FileStorageProperties("http://minio", "http://minio", "ak", "sk", "us-east-1", "bucket", "imaging", "chunks", true, 300),
                new AiProperties.InternalProperties("ai-service", "key"),
                new AiProperties.RateLimitProperties(true, 60, 60, false, Map.of()),
                new AiProperties.RagProperties(8807, 8807, 512, true, false)
        );
    }
}

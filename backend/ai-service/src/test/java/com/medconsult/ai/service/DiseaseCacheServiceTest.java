package com.medconsult.ai.service;

import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseCandidate;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseKnowledge;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MatchSource;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;
import com.medconsult.ai.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiseaseCacheServiceTest {

    @Test
    void diseaseCacheKeyShouldIncludeQueryAndKnowledgeVersionDimensions() {
        DiseaseCandidate candidate = new DiseaseCandidate("肺炎", List.of("咳嗽"), "候选疾病");
        MetadataQuery symptomFields = new MetadataQuery(List.of("symptom"), Map.of());
        MetadataQuery causeFields = new MetadataQuery(List.of("cause"), Map.of());
        AiProperties currentKnowledge = aiProperties("BAAI/bge-small-zh-v1.5", "medical", "data");
        AiProperties reindexedKnowledge = aiProperties("BAAI/bge-m3", "medical", "data");

        String symptomKey = DiseaseCacheService.diseaseCacheKeyFor(
                currentKnowledge, "咳嗽三天", candidate, symptomFields);
        String causeKey = DiseaseCacheService.diseaseCacheKeyFor(
                currentKnowledge, "咳嗽三天", candidate, causeFields);
        String reindexedKey = DiseaseCacheService.diseaseCacheKeyFor(
                reindexedKnowledge, "咳嗽三天", candidate, symptomFields);

        assertNotEquals(symptomKey, causeKey);
        assertNotEquals(symptomKey, reindexedKey);
    }

    @Test
    void getShouldReadDocumentEnvelopeAndMapMetadata() {
        RedisFixture fixture = redisFixture();
        String cached = """
                {
                  "source": "MILVUS_SEMANTIC",
                  "score": 0.82,
                  "document": {
                    "_id": "d-1",
                    "name": "Flu",
                    "desc": "respiratory infection",
                    "symptom": ["fever", "cough"],
                    "cause": "virus",
                    "check": ["blood test"]
                  }
                }
                """;
        when(fixture.valueOperations.get(anyString())).thenReturn(cached);
        DiseaseCacheService service = new DiseaseCacheService(fixture.redisTemplate, aiProperties());

        Optional<DiseaseKnowledge> result = service.get(" fever   cough ",
                new DiseaseCandidate("Flu", List.of("fever"), "candidate"),
                new MetadataQuery(List.of("check", "cause"), Map.of()));

        assertTrue(result.isPresent());
        DiseaseKnowledge knowledge = result.orElseThrow();
        assertEquals("d-1", knowledge.vectorId());
        assertEquals("DISEASE_JSON:Flu", knowledge.sourceId());
        assertEquals("Flu", knowledge.diseaseName());
        assertEquals(List.of("fever", "cough"), knowledge.symptoms());
        assertEquals("virus", knowledge.metadata().get("cause"));
        assertEquals(List.of("blood test"), knowledge.metadata().get("check"));
        assertEquals(0.82, knowledge.score());
        assertEquals(MatchSource.MILVUS_SEMANTIC, knowledge.source());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.valueOperations).get(keyCaptor.capture());
        assertTrue(keyCaptor.getValue().startsWith("medical:disease:v2:Flu:"));
    }

    @Test
    void getShouldReadLegacyCacheAndFallbackUnknownSourceToRedisCache() {
        RedisFixture fixture = redisFixture();
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("vectorId", "legacy-1");
        legacy.put("sourceId", "legacy-source");
        legacy.put("diseaseName", "Cold");
        legacy.put("desc", "legacy desc");
        legacy.put("symptoms", List.of("sneeze"));
        legacy.put("metadata", Map.of("cause", "virus"));
        legacy.put("fieldName", "name");
        legacy.put("chunkText", "chunk");
        legacy.put("score", "0.67");
        legacy.put("source", "UNKNOWN_SOURCE");
        when(fixture.valueOperations.get(anyString())).thenReturn(JsonUtils.toJson(legacy));
        DiseaseCacheService service = new DiseaseCacheService(fixture.redisTemplate, aiProperties());

        DiseaseKnowledge knowledge = service.get("sneeze",
                        new DiseaseCandidate("Cold", List.of("sneeze"), ""),
                        new MetadataQuery(List.of(), Map.of()))
                .orElseThrow();

        assertEquals("legacy-1", knowledge.vectorId());
        assertEquals("Cold", knowledge.diseaseName());
        assertEquals(List.of("sneeze"), knowledge.symptoms());
        assertEquals("virus", knowledge.metadata().get("cause"));
        assertEquals(0.67, knowledge.score());
        assertEquals(MatchSource.REDIS_CACHE, knowledge.source());
    }

    @Test
    void getShouldReturnEmptyForBlankCandidateMissingValueAndRedisFailure() {
        RedisFixture fixture = redisFixture();
        DiseaseCacheService service = new DiseaseCacheService(fixture.redisTemplate, aiProperties());

        assertTrue(service.get("text", new DiseaseCandidate(" ", List.of(), ""),
                new MetadataQuery(List.of(), Map.of())).isEmpty());
        verify(fixture.redisTemplate, never()).opsForValue();

        RedisFixture missing = redisFixture();
        when(missing.valueOperations.get(anyString())).thenReturn(" ");
        DiseaseCacheService missingService = new DiseaseCacheService(missing.redisTemplate, aiProperties());
        assertTrue(missingService.get("text", new DiseaseCandidate("Flu", List.of(), ""),
                new MetadataQuery(List.of(), Map.of())).isEmpty());

        RedisFixture failing = redisFixture();
        when(failing.valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis down"));
        DiseaseCacheService failingService = new DiseaseCacheService(failing.redisTemplate, aiProperties());
        assertTrue(failingService.get("text", new DiseaseCandidate("Flu", List.of(), ""),
                new MetadataQuery(List.of(), Map.of())).isEmpty());
    }

    @Test
    void putShouldWriteDocumentEnvelopeWithMinimumTtl() {
        RedisFixture fixture = redisFixture();
        DiseaseCacheService service = new DiseaseCacheService(fixture.redisTemplate,
                aiProperties("BAAI/bge-small-zh-v1.5", "medical", "data", 5));
        DiseaseKnowledge knowledge = knowledge("d-1", "Flu", MatchSource.MONGODB_NAME_EXACT);

        service.put(" fever cough ",
                new DiseaseCandidate("Flu", List.of("fever"), ""),
                new MetadataQuery(List.of("cause"), Map.of("department", List.of(" respiratory ", "ai"))),
                knowledge);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(fixture.valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertTrue(keyCaptor.getValue().startsWith("medical:disease:v2:Flu:"));
        assertEquals(Duration.ofSeconds(60), ttlCaptor.getValue());
        var root = JsonUtils.readTree(valueCaptor.getValue());
        assertEquals("MONGODB_NAME_EXACT", root.path("source").asText());
        assertEquals(0.91, root.path("score").asDouble());
        assertEquals("d-1", root.path("document").path("_id").asText());
        assertEquals("Flu", root.path("document").path("name").asText());
        assertEquals("virus", root.path("document").path("cause").asText());
    }

    @Test
    void putShouldSkipNullOrBlankKnowledgeAndIgnoreRedisFailure() {
        RedisFixture blank = redisFixture();
        DiseaseCacheService blankService = new DiseaseCacheService(blank.redisTemplate, aiProperties());
        blankService.put("text", new DiseaseCandidate(" ", List.of(), ""),
                new MetadataQuery(List.of(), Map.of()), knowledge("d-1", "Flu", MatchSource.MONGODB_NAME_EXACT));
        blankService.put("text", new DiseaseCandidate("Flu", List.of(), ""),
                new MetadataQuery(List.of(), Map.of()), null);
        verify(blank.redisTemplate, never()).opsForValue();

        RedisFixture failing = redisFixture();
        doThrow(new IllegalStateException("redis down"))
                .when(failing.valueOperations).set(anyString(), anyString(), any(Duration.class));
        DiseaseCacheService failingService = new DiseaseCacheService(failing.redisTemplate, aiProperties());

        assertDoesNotThrow(() -> failingService.put("text",
                new DiseaseCandidate("Flu", List.of("fever"), ""),
                new MetadataQuery(List.of(), Map.of()),
                knowledge("d-1", "Flu", MatchSource.MONGODB_NAME_EXACT)));
    }

    @Test
    void cacheKeyShouldNormalizeOrderWhitespaceAndInfrastructureVersions() {
        DiseaseCandidate leftCandidate = new DiseaseCandidate("Flu", List.of("fever"), "");
        DiseaseCandidate rightCandidate = new DiseaseCandidate(" Flu ", List.of("fever"), "");
        MetadataQuery left = new MetadataQuery(
                List.of(" check ", "cause", "check"),
                Map.of("department", List.of(" respiratory ", "ai"), "empty", List.of("")));
        MetadataQuery right = new MetadataQuery(
                List.of("cause", "check"),
                Map.of("department", List.of("ai", "respiratory"), "empty", List.of()));

        String leftKey = DiseaseCacheService.diseaseCacheKeyFor(aiProperties(), "fever    cough", leftCandidate, left);
        String rightKey = DiseaseCacheService.diseaseCacheKeyFor(aiProperties(), "fever cough", rightCandidate, right);

        assertEquals(leftKey, rightKey);
    }

    private static RedisFixture redisFixture() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        return new RedisFixture(redisTemplate, valueOperations);
    }

    private static DiseaseKnowledge knowledge(String id, String name, MatchSource source) {
        return new DiseaseKnowledge(
                id,
                "DISEASE_JSON:" + name,
                name,
                "respiratory infection",
                List.of("fever", "cough"),
                Map.of("cause", "virus"),
                "name",
                "chunk",
                0.91,
                source
        );
    }

    private static AiProperties aiProperties() {
        return aiProperties("BAAI/bge-small-zh-v1.5", "medical", "data");
    }

    private static AiProperties aiProperties(String embeddingModel, String milvusDatabase, String milvusCollection) {
        return aiProperties(embeddingModel, milvusDatabase, milvusCollection, 60);
    }

    private static AiProperties aiProperties(String embeddingModel, String milvusDatabase,
                                             String milvusCollection, long cacheSeconds) {
        return new AiProperties(
                new AiProperties.LlmProperties("http://llm", "key", "model", 5),
                new AiProperties.EmbeddingProperties("http://embedding", "key", embeddingModel, 5),
                new AiProperties.MongoProperties("mongodb://localhost:27017", "medconsult", "diseases"),
                new AiProperties.RedisProperties("medical:", cacheSeconds),
                new AiProperties.MilvusProperties("http://localhost:19530", "token", milvusDatabase,
                        milvusCollection, 0.6, "COSINE", 15),
                new AiProperties.ImagingProperties("provider", "model"),
                new AiProperties.VisionProperties("http://vision", "key", "model", 5, 1024),
                new AiProperties.FileStorageProperties("http://minio", "http://minio", "ak", "sk", "us-east-1", "bucket",
                        "imaging", "chunks", true, 300),
                new AiProperties.InternalProperties("ai-service", "key"),
                new AiProperties.RateLimitProperties(true, 60, 60, false, Map.of()),
                new AiProperties.RagProperties(8807, 8807, 512, true, false)
        );
    }

    private record RedisFixture(
            StringRedisTemplate redisTemplate,
            ValueOperations<String, String> valueOperations
    ) {
    }
}

package com.medconsult.ai.service;

import com.medconsult.ai.config.AiProperties;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.DiseaseCandidate;
import com.medconsult.ai.knowledge.DiseaseKnowledgeModels.MetadataQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

    private static AiProperties aiProperties(String embeddingModel, String milvusDatabase, String milvusCollection) {
        return new AiProperties(
                new AiProperties.LlmProperties("http://llm", "key", "model", 5),
                new AiProperties.EmbeddingProperties("http://embedding", "key", embeddingModel, 5),
                new AiProperties.MongoProperties("mongodb://localhost:27017", "medconsult", "diseases"),
                new AiProperties.RedisProperties("medical:", 60),
                new AiProperties.MilvusProperties("http://localhost:19530", "token", milvusDatabase,
                        milvusCollection, 0.6, "COSINE", 15),
                new AiProperties.ImagingProperties("provider", "model"),
                new AiProperties.VisionProperties("http://vision", "key", "model", 5, 1024),
                new AiProperties.FileStorageProperties("http://minio", "http://minio", "ak", "sk", "bucket",
                        "imaging", "chunks", true),
                new AiProperties.InternalProperties("ai-service", "key"),
                new AiProperties.RateLimitProperties(true, 60, 60, false, Map.of()),
                new AiProperties.RagProperties(8807, 8807, 512, true, false)
        );
    }
}

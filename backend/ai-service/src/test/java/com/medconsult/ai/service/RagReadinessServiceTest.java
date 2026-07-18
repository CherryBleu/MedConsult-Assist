package com.medconsult.ai.service;

import com.medconsult.ai.client.MilvusRestClient;
import com.medconsult.ai.client.MongoDiseaseRepository;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RAG 数据就绪自检回归测试。
 *
 * <p>Mongo/Milvus/Embedding 任一为空或维度不匹配，都不能被静默当作“无命中”。
 */
class RagReadinessServiceTest {

    @Test
    void shouldBeReadyWhenCountsAndDimensionMatch() {
        MongoDiseaseRepository mongo = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvus = mock(MilvusRestClient.class);
        OpenAiCompatibleClient embedding = mock(OpenAiCompatibleClient.class);
        when(mongo.countDocuments()).thenReturn(8807L);
        when(milvus.countEntities()).thenReturn(8807L);
        when(embedding.embedOne("RAG_SELF_CHECK")).thenReturn(Optional.of(List.of(0.1f, 0.2f, 0.3f)));

        RagReadinessService service = new RagReadinessService(properties(3, 3, 3), mongo, milvus, embedding);

        RagReadinessService.RagReadiness readiness = service.refresh();

        assertTrue(readiness.ready());
        assertTrue(readiness.checks().stream().allMatch(check -> "UP".equals(check.status())));
    }

    @Test
    void shouldFailWhenMongoIsEmpty() {
        MongoDiseaseRepository mongo = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvus = mock(MilvusRestClient.class);
        OpenAiCompatibleClient embedding = mock(OpenAiCompatibleClient.class);
        when(mongo.countDocuments()).thenReturn(0L);
        when(milvus.countEntities()).thenReturn(8807L);
        when(embedding.embedOne("RAG_SELF_CHECK")).thenReturn(Optional.of(List.of(0.1f, 0.2f, 0.3f)));

        RagReadinessService service = new RagReadinessService(properties(8807, 8807, 3), mongo, milvus, embedding);

        RagReadinessService.RagReadiness readiness = service.refresh();

        assertFalse(readiness.ready());
        assertTrue(readiness.checks().stream().anyMatch(check -> "mongo".equals(check.name()) && "DOWN".equals(check.status())));
    }

    @Test
    void shouldFailWhenEmbeddingDimensionMismatchesMilvusSchema() {
        MongoDiseaseRepository mongo = mock(MongoDiseaseRepository.class);
        MilvusRestClient milvus = mock(MilvusRestClient.class);
        OpenAiCompatibleClient embedding = mock(OpenAiCompatibleClient.class);
        when(mongo.countDocuments()).thenReturn(8807L);
        when(milvus.countEntities()).thenReturn(8807L);
        when(embedding.embedOne("RAG_SELF_CHECK")).thenReturn(Optional.of(List.of(0.1f, 0.2f)));

        RagReadinessService service = new RagReadinessService(properties(8807, 8807, 3), mongo, milvus, embedding);

        RagReadinessService.RagReadiness readiness = service.refresh();

        assertFalse(readiness.ready());
        assertTrue(readiness.checks().stream().anyMatch(check -> "embedding".equals(check.name()) && "DOWN".equals(check.status())));
    }

    private static AiProperties properties(long expectedMongo, long expectedMilvus, int expectedDimension) {
        return new AiProperties(
                new AiProperties.LlmProperties("http://llm", "key", "model", 5),
                new AiProperties.EmbeddingProperties("http://embedding", "key", "model", 5),
                new AiProperties.MongoProperties("mongodb://localhost:27017", "medconsult", "diseases"),
                new AiProperties.RedisProperties("medical:", 60),
                new AiProperties.MilvusProperties("http://localhost:19530", "token", "medical", "data", 0.6, "COSINE", 15),
                new AiProperties.ImagingProperties("provider", "model"),
                new AiProperties.VisionProperties("http://vision", "key", "model", 5, 1024),
                new AiProperties.FileStorageProperties("http://minio", "http://minio", "ak", "sk", "us-east-1", "bucket", "imaging", "chunks", true, 300),
                new AiProperties.InternalProperties("ai-service", "key"),
                new AiProperties.RateLimitProperties(true, 60, 60, false, Map.of()),
                new AiProperties.RagProperties(expectedMongo, expectedMilvus, expectedDimension, true, false)
        );
    }
}

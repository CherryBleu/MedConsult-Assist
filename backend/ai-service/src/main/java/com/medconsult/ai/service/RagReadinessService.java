package com.medconsult.ai.service;

import com.medconsult.ai.client.MilvusRestClient;
import com.medconsult.ai.client.MongoDiseaseRepository;
import com.medconsult.ai.client.OpenAiCompatibleClient;
import com.medconsult.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 知识库就绪自检。
 *
 * <p>Mongo、Milvus、Embedding 是 symptom-chat 的关键依赖。自检把“依赖不可用/数据未导入/维度错配”
 * 与“真实无命中”区分开，避免再次出现知识库为空却被误判为问答可用的问题。
 */
@Service
public class RagReadinessService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RagReadinessService.class);
    private static final String SELF_CHECK_TEXT = "RAG_SELF_CHECK";

    private final AiProperties properties;
    private final MongoDiseaseRepository mongoDiseaseRepository;
    private final MilvusRestClient milvusRestClient;
    private final OpenAiCompatibleClient embeddingClient;

    private volatile RagReadiness lastReadiness = new RagReadiness(false, LocalDateTime.now(), List.of());

    public RagReadinessService(AiProperties properties,
                               MongoDiseaseRepository mongoDiseaseRepository,
                               MilvusRestClient milvusRestClient,
                               OpenAiCompatibleClient embeddingClient) {
        this.properties = properties;
        this.mongoDiseaseRepository = mongoDiseaseRepository;
        this.milvusRestClient = milvusRestClient;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        AiProperties.RagProperties rag = properties.rag();
        if (rag == null || !rag.startupCheckEnabled()) {
            lastReadiness = new RagReadiness(true, LocalDateTime.now(),
                    List.of(new RagCheck("rag", "UP", "startup", 0, 0, "CHECK_DISABLED")));
            log.info("[RAG-READINESS] startup check disabled");
            return;
        }
        RagReadiness readiness = refresh();
        log.info("[RAG-READINESS] ready={} checks={}", readiness.ready(), readiness.checks());
        if (!readiness.ready() && rag.failFast()) {
            throw new IllegalStateException("RAG knowledge base is not ready: " + readiness.checks());
        }
    }

    public RagReadiness current() {
        return lastReadiness;
    }

    public RagReadiness refresh() {
        AiProperties.RagProperties rag = properties.rag();
        long expectedMongoCount = rag == null ? 0 : rag.expectedMongoCount();
        long expectedMilvusCount = rag == null ? 0 : rag.expectedMilvusCount();
        int expectedEmbeddingDimension = rag == null ? 0 : rag.expectedEmbeddingDimension();

        List<RagCheck> checks = new ArrayList<>();
        long mongoCount = mongoDiseaseRepository.countDocuments();
        checks.add(countCheck("mongo", mongoCount, expectedMongoCount,
                properties.mongo() == null ? "" : properties.mongo().database() + "." + properties.mongo().collection()));

        long milvusCount = milvusRestClient.countEntities();
        checks.add(countCheck("milvus", milvusCount, expectedMilvusCount,
                properties.milvus() == null ? "" : properties.milvus().database() + "." + properties.milvus().collection()));

        int embeddingDimension = embeddingClient.embedOne(SELF_CHECK_TEXT)
                .map(List::size)
                .orElse(-1);
        checks.add(dimensionCheck("embedding", embeddingDimension, expectedEmbeddingDimension,
                properties.embedding() == null ? "" : properties.embedding().model()));

        boolean ready = checks.stream().allMatch(check -> "UP".equals(check.status()));
        RagReadiness readiness = new RagReadiness(ready, LocalDateTime.now(), List.copyOf(checks));
        lastReadiness = readiness;
        return readiness;
    }

    private static RagCheck countCheck(String name, long actual, long expected, String target) {
        if (actual < 0) {
            return new RagCheck(name, "DOWN", target, actual, expected, "COUNT_UNAVAILABLE");
        }
        if (expected > 0 && actual < expected) {
            return new RagCheck(name, "DOWN", target, actual, expected, "COUNT_BELOW_EXPECTED");
        }
        return new RagCheck(name, "UP", target, actual, expected, "OK");
    }

    private static RagCheck dimensionCheck(String name, int actual, int expected, String target) {
        if (actual <= 0) {
            return new RagCheck(name, "DOWN", target, actual, expected, "EMBEDDING_UNAVAILABLE");
        }
        if (expected > 0 && actual != expected) {
            return new RagCheck(name, "DOWN", target, actual, expected, "DIMENSION_MISMATCH");
        }
        return new RagCheck(name, "UP", target, actual, expected, "OK");
    }

    public record RagReadiness(boolean ready, LocalDateTime checkedAt, List<RagCheck> checks) {
    }

    public record RagCheck(String name, String status, String target, long actual, long expected, String reason) {
    }
}

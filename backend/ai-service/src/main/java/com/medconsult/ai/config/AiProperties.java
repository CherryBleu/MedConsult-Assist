package com.medconsult.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.Map;

@ConfigurationProperties(prefix = "medconsult.ai")
public record AiProperties(
        LlmProperties llm,
        EmbeddingProperties embedding,
        MongoProperties mongo,
        RedisProperties redis,
        MilvusProperties milvus,
        ImagingProperties imaging,
        VisionProperties vision,
        FileStorageProperties fileStorage,
        InternalProperties internal,
        RateLimitProperties rateLimit,
        RagProperties rag
) {
    public record LlmProperties(String baseUrl, String apiKey, String model, int timeoutSeconds) {
    }

    public record EmbeddingProperties(String baseUrl, String apiKey, String model, int timeoutSeconds) {
    }

    public record MongoProperties(String uri, String database, String collection) {
    }

    public record RedisProperties(String keyPrefix, long cacheSeconds) {
    }

    public record MilvusProperties(
            String uri,
            String token,
            String database,
            String collection,
            double minScore,
            String metricType,
            int searchTimeoutSeconds
    ) {
        public MilvusProperties {
            metricType = normalizeMilvusMetricType(metricType);
            if (searchTimeoutSeconds <= 0) {
                searchTimeoutSeconds = 15;
            }
        }

        private static String normalizeMilvusMetricType(String value) {
            String normalized = value == null || value.isBlank() ? "COSINE" : value.trim().toUpperCase(Locale.ROOT);
            if ("EUCLIDEAN".equals(normalized)) {
                return "L2";
            }
            return switch (normalized) {
                case "COSINE", "IP", "L2" -> normalized;
                default -> throw new IllegalArgumentException("Unsupported Milvus metric type: " + value);
            };
        }
    }

    public record ImagingProperties(String provider, String model) {
    }

    public record VisionProperties(
            String baseUrl,
            String apiKey,
            String model,
            int timeoutSeconds,
            long maxBytesPerImage
    ) {
    }

    public record FileStorageProperties(
            String endpoint,
            String publicEndpoint,
            String accessKey,
            String secretKey,
            String region,
            String bucket,
            String objectPrefix,
            String chunkPrefix,
            boolean autoCreateBucket,
            int presignedUrlExpirySeconds
    ) {
    }

    /**
     * 服务身份配置（向 auth-service 换发 SERVICE JWT 用，架构文档 §4.2）。
     * serviceCode + apiKey 用于 ServiceTokenProvider 调用 auth-service 的
     * /internal/auth/service-token 换取服务身份 JWT，下游 SecurityContext.requireService 校验。
     */
    public record InternalProperties(
            String serviceCode,
            String apiKey
    ) {
    }

    public record RateLimitProperties(
            boolean enabled,
            int maxRequests,
            int windowSeconds,
            boolean includeInternal,
            Map<String, Integer> endpointMaxRequests
    ) {
    }

    /**
     * RAG 数据就绪自检配置。
     *
     * <p>expected* 用于防止 Mongo/Milvus 未导入数据或 embedding 维度错配时被误判为“无命中”。
     */
    public record RagProperties(
            long expectedMongoCount,
            long expectedMilvusCount,
            int expectedEmbeddingDimension,
            boolean startupCheckEnabled,
            boolean failFast,
            String knowledgeVersion
    ) {
        public RagProperties {
            knowledgeVersion = knowledgeVersion == null ? "" : knowledgeVersion.trim();
        }
    }
}

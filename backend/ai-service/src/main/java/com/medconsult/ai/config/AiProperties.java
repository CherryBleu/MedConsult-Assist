package com.medconsult.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
        RateLimitProperties rateLimit
) {
    public record LlmProperties(String baseUrl, String apiKey, String model, int timeoutSeconds) {
    }

    public record EmbeddingProperties(String baseUrl, String apiKey, String model, int timeoutSeconds) {
    }

    public record MongoProperties(String uri, String database, String collection) {
    }

    public record RedisProperties(String keyPrefix, long cacheSeconds) {
    }

    public record MilvusProperties(String uri, String token, String database, String collection, double minScore) {
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
            String bucket,
            String objectPrefix,
            String chunkPrefix,
            boolean autoCreateBucket
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
}

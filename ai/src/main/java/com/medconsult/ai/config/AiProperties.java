package com.medconsult.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "medconsult.ai")
public record AiProperties(
        LlmProperties llm,
        EmbeddingProperties embedding,
        MongoProperties mongo,
        RedisProperties redis,
        MilvusProperties milvus,
        ImagingProperties imaging
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
}

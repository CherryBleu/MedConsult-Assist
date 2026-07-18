package com.medconsult.ai.config;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.client.AuthFeignClient;
import com.medconsult.common.feign.dto.ServiceTokenDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiServiceTokenProviderTest {
    private AuthFeignClient authFeignClient;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private AiServiceTokenProvider provider;

    @BeforeEach
    void setUp() {
        authFeignClient = mock(AuthFeignClient.class);
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        provider = new AiServiceTokenProvider(authFeignClient, redis, properties("ai-service", "secret"));
    }

    @Test
    void getShouldReturnCachedTokenWithoutCallingAuthService() {
        when(valueOperations.get("medconsult:ai:service-token")).thenReturn("cached-token");

        String token = provider.get();

        assertEquals("cached-token", token);
        verify(authFeignClient, never()).issueServiceToken(any());
    }

    @Test
    void getShouldIssueAndCacheTokenWhenCacheMisses() {
        when(valueOperations.get("medconsult:ai:service-token")).thenReturn(null);
        when(authFeignClient.issueServiceToken(any())).thenReturn(Result.ok(
                new ServiceTokenDTO.Response("issued-token", "Bearer", 3600, "ai-service")));

        String token = provider.get();

        assertEquals("issued-token", token);
        ArgumentCaptor<ServiceTokenDTO.Request> requestCaptor =
                ArgumentCaptor.forClass(ServiceTokenDTO.Request.class);
        verify(authFeignClient).issueServiceToken(requestCaptor.capture());
        assertEquals("ai-service", requestCaptor.getValue().serviceCode());
        assertEquals("secret", requestCaptor.getValue().apiKey());
        verify(valueOperations).set(
                "medconsult:ai:service-token", "issued-token", Duration.ofSeconds(3300));
    }

    @Test
    void getShouldFallbackToAuthServiceWhenRedisReadFails() {
        when(valueOperations.get("medconsult:ai:service-token")).thenThrow(new RuntimeException("redis down"));
        when(authFeignClient.issueServiceToken(any())).thenReturn(Result.ok(
                new ServiceTokenDTO.Response("issued-token", "Bearer", 3600, "ai-service")));

        assertEquals("issued-token", provider.get());
    }

    @Test
    void getShouldReturnIssuedTokenWhenRedisWriteFails() {
        when(valueOperations.get("medconsult:ai:service-token")).thenReturn(null);
        when(authFeignClient.issueServiceToken(any())).thenReturn(Result.ok(
                new ServiceTokenDTO.Response("issued-token", "Bearer", 3600, "ai-service")));
        doThrow(new RuntimeException("redis write failed")).when(valueOperations)
                .set(eq("medconsult:ai:service-token"), eq("issued-token"), eq(Duration.ofSeconds(3300)));

        assertEquals("issued-token", provider.get());
    }

    @Test
    void getShouldReturnNullWhenInternalCredentialsAreMissing() {
        provider = new AiServiceTokenProvider(authFeignClient, redis, properties(null, "secret"));

        assertNull(provider.get());
        verify(authFeignClient, never()).issueServiceToken(any());

        provider = new AiServiceTokenProvider(authFeignClient, redis, properties("ai-service", null));
        assertNull(provider.get());
        verify(authFeignClient, never()).issueServiceToken(any());
    }

    @Test
    void getShouldReturnNullWhenAuthServiceReturnsEmptyToken() {
        when(authFeignClient.issueServiceToken(any())).thenReturn(Result.ok(
                new ServiceTokenDTO.Response(null, "Bearer", 3600, "ai-service")));

        assertNull(provider.get());
    }

    @Test
    void getShouldReturnNullWhenAuthServiceThrows() {
        when(authFeignClient.issueServiceToken(any())).thenThrow(new RuntimeException("auth unavailable"));

        assertNull(provider.get());
    }

    private static AiProperties properties(String serviceCode, String apiKey) {
        return new AiProperties(
                null, null, null, null, null, null, null, null,
                new AiProperties.InternalProperties(serviceCode, apiKey),
                null, null);
    }
}

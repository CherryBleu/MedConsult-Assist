package com.medconsult.common.feign.dto;

/**
 * 服务 token 换发 DTO（架构文档 §4.2，与 auth-service ServiceAccountDTO 对齐）。
 *
 * <p>跨服务共享：ai-service 的 ServiceTokenProvider 用此 DTO 调用 auth-service 换发服务身份 JWT。
 */
public class ServiceTokenDTO {

    /** 换发请求：serviceCode + apiKey */
    public record Request(String serviceCode, String apiKey) {}

    /** 换发响应：服务身份 JWT */
    public record Response(String accessToken, String tokenType, long expiresIn, String serviceCode) {}
}

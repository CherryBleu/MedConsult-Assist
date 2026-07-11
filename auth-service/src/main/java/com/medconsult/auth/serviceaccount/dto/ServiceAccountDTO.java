package com.medconsult.auth.serviceaccount.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 服务账号相关 DTO（服务 token 换发用，架构文档 §4.2）。
 */
public class ServiceAccountDTO {

    @Data
    @Schema(description = "服务 token 换发请求")
    public static class ServiceTokenRequest {
        @Schema(description = "服务编码（如 ai-service）")
        @NotBlank(message = "serviceCode 不能为空")
        private String serviceCode;

        @Schema(description = "API Key")
        @NotBlank(message = "apiKey 不能为空")
        private String apiKey;
    }

    @Schema(description = "服务 token 换发响应")
    public record ServiceTokenResponse(
            @Schema(description = "服务身份 JWT（subjectType=SERVICE）") String accessToken,
            @Schema(description = "令牌类型") String tokenType,
            @Schema(description = "过期时间（秒）") long expiresIn,
            @Schema(description = "服务编码") String serviceCode
    ) {}
}

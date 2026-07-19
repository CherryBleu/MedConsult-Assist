package com.medconsult.auth.serviceaccount.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

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

    @Schema(description = "用户 Token 校验响应")
    public record UserTokenVerifyResponse(
            @Schema(description = "用户主键 ID") Long userId,
            @Schema(description = "角色列表") List<String> roles,
            @Schema(description = "主角色") String primaryRole,
            @Schema(description = "权限点列表") List<String> scope,
            @Schema(description = "过期时间（Unix 秒）") Long exp
    ) {}

    @Schema(description = "服务 Token 校验响应")
    public record ServiceTokenVerifyResponse(
            @Schema(description = "服务编码") String serviceCode,
            @Schema(description = "权限点列表") List<String> scope,
            @Schema(description = "过期时间（Unix 秒）") Long exp
    ) {}

    @Schema(description = "用户角色查询响应")
    public record UserRolesResponse(
            @Schema(description = "角色列表") List<String> roles,
            @Schema(description = "主角色") String primaryRole
    ) {}
}

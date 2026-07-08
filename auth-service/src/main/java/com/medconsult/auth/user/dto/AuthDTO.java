package com.medconsult.auth.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 认证相关请求/响应 DTO（字段命名对齐《接口文档》§2.1）。
 */
public class AuthDTO {

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "账号不能为空")
        private String account;
        @NotBlank(message = "密码不能为空")
        private String password;
        private String phone;
        @NotBlank(message = "姓名不能为空")
        private String name;
        /** PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN（冒烟期接受角色码；RBAC 五表阶段改为查 sys_role） */
        private String role;
        private String patientId;
        private String doctorId;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "账号不能为空")
        private String account;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank(message = "refreshToken 不能为空")
        private String refreshToken;
    }

    @Data
    public static class LogoutRequest {
        @NotBlank(message = "refreshToken 不能为空")
        private String refreshToken;
    }

    /** 登录响应（对齐《接口文档》§2.1.2） */
    public record LoginResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserInfo user
    ) {}

    public record UserInfo(
            String userId,
            String name,
            String role,
            String patientId,
            String doctorId,
            String status
    ) {}

    public record RefreshResponse(
            String accessToken,
            String tokenType,
            long expiresIn
    ) {}

    /** 当前用户信息响应（对齐《接口文档》§2.1.5，含脱敏） */
    public record MeResponse(
            String userId,
            String account,
            String name,
            String phoneMasked,
            String role,
            String patientId,
            String doctorId,
            String status
    ) {}
}

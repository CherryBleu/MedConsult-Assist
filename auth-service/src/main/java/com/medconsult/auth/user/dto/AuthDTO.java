package com.medconsult.auth.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * 认证相关请求/响应 DTO（字段命名对齐《接口文档》§2.1）。
 */
public class AuthDTO {

    /** 允许的角色码白名单（冒烟期；RBAC 五表阶段改为查 sys_role） */
    public static final Set<String> ALLOWED_ROLES =
            Set.of("PATIENT", "DOCTOR", "PHARMACY_ADMIN", "HOSPITAL_ADMIN");

    @Data
    public static class RegisterRequest {
        /** 账号：4-32 位字母数字下划线（防止注入和脏数据） */
        @NotBlank(message = "账号不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$",
                message = "账号须为 4-32 位字母/数字/下划线")
        private String account;

        /** 密码：8-64 位，至少含字母和数字（防弱密码） */
        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                message = "密码须 8-64 位且至少含字母和数字")
        private String password;

        /** 手机号：中国大陆 11 位（选填，但填了必须合法） */
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$",
                message = "手机号格式非法（须 11 位 1[3-9] 开头）")
        private String phone;

        /** 姓名：1-50 位（中文/字母/空格/点），禁止纯数字和特殊符号 */
        @NotBlank(message = "姓名不能为空")
        @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z·.\\s]{1,50}$",
                message = "姓名须为 1-50 位中文/字母/空格/点（不允许纯数字或特殊符号）")
        private String name;

        /** PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN */
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

    /** 在 service 层调用的角色白名单校验（DTO 静态导入用） */
    public static boolean isValidRole(String role) {
        return role == null || role.isBlank() || ALLOWED_ROLES.contains(role);
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

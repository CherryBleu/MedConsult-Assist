package com.medconsult.auth.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * 认证相关请求/响应 DTO（字段命名对齐《接口文档》§2.1）。
 */
public class AuthDTO {

    /**
     * 允许的角色码白名单（冒烟期；RBAC 五表阶段改为查 sys_role）。
     * <p>注意：{@link #SELF_REGISTER_ROLES} 才是自助注册允许的角色子集。
     */
    public static final Set<String> ALLOWED_ROLES =
            Set.of("PATIENT", "DOCTOR", "PHARMACY_ADMIN", "HOSPITAL_ADMIN");

    /**
     * 自助注册允许的角色子集（安全：管理类角色不可自助注册，防止越权）。
     * <p>公开的 /register 接口只接受 PATIENT / DOCTOR；
     * PHARMACY_ADMIN / HOSPITAL_ADMIN 必须由已登录管理员通过后台接口授予。
     */
    public static final Set<String> SELF_REGISTER_ROLES = Set.of("PATIENT", "DOCTOR");

    @Data
    @Schema(description = "注册请求")
    public static class RegisterRequest {
        /** 账号：4-32 位字母数字下划线（防止注入和脏数据） */
        @Schema(description = "账号：4-32 位字母/数字/下划线")
        @NotBlank(message = "账号不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$",
                message = "账号须为 4-32 位字母/数字/下划线")
        private String account;

        /** 密码：8-64 位，至少含字母和数字（防弱密码） */
        @Schema(description = "密码：8-64 位，至少含字母和数字")
        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                message = "密码须 8-64 位且至少含字母和数字")
        private String password;

        /** 手机号：中国大陆 11 位（选填，但填了必须合法） */
        @Schema(description = "手机号：中国大陆 11 位（选填）")
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$",
                message = "手机号格式非法（须 11 位 1[3-9] 开头）")
        private String phone;

        /** 姓名：1-50 位（中文/字母/空格/点），禁止纯数字和特殊符号 */
        @Schema(description = "姓名：1-50 位中文/字母/空格/点")
        @NotBlank(message = "姓名不能为空")
        @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z·.\\s]{1,50}$",
                message = "姓名须为 1-50 位中文/字母/空格/点（不允许纯数字或特殊符号）")
        private String name;

        /** PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN */
        @Schema(description = "角色：PATIENT / DOCTOR / PHARMACY_ADMIN / HOSPITAL_ADMIN")
        private String role;

        /**
         * 身份证号：PATIENT 角色注册时必填（service 层强制校验），用于自动建档。
         * 18 位标准身份证或 15 位旧版身份证。DOCTOR 角色不校验。
         */
        @Schema(description = "身份证号（PATIENT 角色必填，用于自动建档）")
        @Pattern(regexp = "^$|^[1-9]\\d{16}[0-9Xx]$|^[1-9]\\d{14}$",
                message = "身份证号格式非法（须 15 或 18 位）")
        private String idCard;

        @Schema(description = "患者编号（注册 DOCTOR 时可关联已有患者档案）")
        private String patientId;
        @Schema(description = "医生编号（注册 PATIENT 时可关联已有医生档案）")
        private String doctorId;
    }

    @Data
    @Schema(description = "登录请求")
    public static class LoginRequest {
        @Schema(description = "账号")
        @NotBlank(message = "账号不能为空")
        private String account;
        @Schema(description = "密码")
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    /** 在 service 层调用的角色白名单校验（DTO 静态导入用） */
    public static boolean isValidRole(String role) {
        return role == null || role.isBlank() || ALLOWED_ROLES.contains(role);
    }

    @Data
    @Schema(description = "刷新 Token 请求")
    public static class RefreshRequest {
        @Schema(description = "刷新令牌")
        @NotBlank(message = "refreshToken 不能为空")
        private String refreshToken;
    }

    @Data
    @Schema(description = "退出登录请求")
    public static class LogoutRequest {
        @Schema(description = "刷新令牌")
        @NotBlank(message = "refreshToken 不能为空")
        private String refreshToken;
    }

    /** 登录响应（对齐《接口文档》§2.1.2） */
    @Schema(description = "登录响应")
    public record LoginResponse(
            @Schema(description = "访问令牌") String accessToken,
            @Schema(description = "刷新令牌") String refreshToken,
            @Schema(description = "令牌类型") String tokenType,
            @Schema(description = "过期时间（秒）") long expiresIn,
            @Schema(description = "用户信息") UserInfo user
    ) {}

    @Schema(description = "用户信息")
    public record UserInfo(
            @Schema(description = "用户编号") String userId,
            @Schema(description = "姓名") String name,
            @Schema(description = "角色") String role,
            @Schema(description = "患者编号") String patientId,
            @Schema(description = "医生编号") String doctorId,
            @Schema(description = "药师编号") String pharmacistId,
            @Schema(description = "状态：ACTIVE / DISABLED / LOCKED") String status
    ) {}

    @Schema(description = "刷新 Token 响应")
    public record RefreshResponse(
            @Schema(description = "访问令牌") String accessToken,
            @Schema(description = "令牌类型") String tokenType,
            @Schema(description = "过期时间（秒）") long expiresIn
    ) {}

    /** 当前用户信息响应（对齐《接口文档》§2.1.5，含脱敏） */
    @Schema(description = "当前用户信息响应（含脱敏）")
    public record MeResponse(
            @Schema(description = "用户编号") String userId,
            @Schema(description = "账号") String account,
            @Schema(description = "姓名") String name,
            @Schema(description = "脱敏手机号") String phoneMasked,
            @Schema(description = "角色") String role,
            @Schema(description = "患者编号") String patientId,
            @Schema(description = "医生编号") String doctorId,
            @Schema(description = "药师编号") String pharmacistId,
            @Schema(description = "状态：ACTIVE / DISABLED / LOCKED") String status
    ) {}

    /**
     * 绑定患者档案请求（补建档场景）。
     *
     * <p>用于历史脏账号（sys_user.patient_id 为 NULL，绕过"注册即建档"流程直接 SQL 插入）
     * 补全患者档案关联。后端自动用 sys_user 已有的 name/phone 建档，前端只需补充建档缺失的字段
     *（身份证号、性别、出生日期等），无需重复填写注册时的手机号和姓名。
     *
     * <p>安全约束：仅 PATIENT 角色且当前 patient_id 为 null 时允许绑定；已绑定则拒绝（防覆盖他人档案）。
     * 绑定成功后重签 JWT（让新 token 带上 patientId），前端存储新 token 后无需重新登录即可使用全部功能。
     */
    @Data
    @Schema(description = "绑定患者档案请求（补建档）")
    public static class BindPatientRequest {
        @Schema(description = "身份证号（PATIENT 建档必填，用于唯一性校验）")
        @Pattern(regexp = "^$|^[1-9]\\d{16}[0-9Xx]$|^[1-9]\\d{14}$",
                message = "身份证号格式非法（须 15 或 18 位）")
        private String idCard;

        @Schema(description = "性别：MALE / FEMALE / UNKNOWN（选填，默认 UNKNOWN）")
        private String gender;

        @Schema(description = "出生日期（选填，格式 YYYY-MM-DD）")
        private String birthDate;
    }

    /**
     * 绑定患者档案响应（含重签的 JWT，前端存储后无需重新登录）。
     */
    @Schema(description = "绑定患者档案响应（含新 token）")
    public record BindPatientResponse(
            @Schema(description = "新的访问令牌（已带 patientId）") String accessToken,
            @Schema(description = "新的刷新令牌") String refreshToken,
            @Schema(description = "令牌类型") String tokenType,
            @Schema(description = "过期时间（秒）") long expiresIn,
            @Schema(description = "用户信息（含新的 patientId）") MeResponse user
    ) {}

    /**
     * 用户列表项（管理员后台「用户管理」页 GET /api/v1/auth/users）。
     *
     * <p><b>安全约束</b>：绝不包含 passwordHash 字段——SysUser 虽有密码摘要，
     * 列表 DTO 显式不暴露，避免任何渠道泄露密码摘要。
     *
     * <p>角色字段 role 来自 Redis（medconsult:auth:role:{userId}，冒烟期 sys_user_role 未落地的临时方案），
     * 读不到时兜底 PATIENT（与登录流程一致）。
     */
    @Schema(description = "用户列表项（不含密码摘要）")
    public record UserListItem(
            @Schema(description = "用户主键 ID") Long id,
            @Schema(description = "用户编号") String userNo,
            @Schema(description = "登录账号") String account,
            @Schema(description = "手机号") String phone,
            @Schema(description = "姓名") String name,
            @Schema(description = "角色（来自 Redis，兜底 PATIENT）") String role,
            @Schema(description = "关联患者档案 ID") Long patientId,
            @Schema(description = "关联医生 ID") Long doctorId,
            @Schema(description = "关联药师 ID") Long pharmacistId,
            @Schema(description = "状态：ACTIVE / DISABLED / LOCKED") String status,
            @Schema(description = "创建时间") java.time.LocalDateTime createdAt
    ) {}
}

package com.medconsult.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 患者档案相关请求/响应 DTO（字段命名对齐《接口文档》§2.2）。
 *
 * <p>JSON 字段（allergies / pastMedicalHistory / familyHistory / emergencyContact）
 * 在请求侧用 List/Object 接收，落库时由 service 序列化为 JSON 串；响应侧再反序列化回结构化字段。
 */
public class PatientDTO {

    /** 性别白名单 */
    public static final Set<String> ALLOWED_GENDER = Set.of("MALE", "FEMALE", "UNKNOWN");
    /** 证件类型白名单 */
    public static final Set<String> ALLOWED_ID_TYPE = Set.of("ID_CARD", "PASSPORT", "OTHER");
    /** 档案状态白名单 */
    public static final Set<String> ALLOWED_STATUS = Set.of("ACTIVE", "DISABLED", "MERGED");

    // ===== §2.2.1 创建患者档案 =====

    @Data
    public static class CreateRequest {
        /** 姓名：1-50 位（中文/字母/空格/点），禁止纯数字和特殊符号 */
        @NotBlank(message = "姓名不能为空")
        @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z·.\\s]{1,50}$",
                message = "姓名须为 1-50 位中文/字母/空格/点（不允许纯数字或特殊符号）")
        private String name;

        /** MALE / FEMALE / UNKNOWN（不传默认 UNKNOWN，传则校验白名单） */
        @Pattern(regexp = "^$|^(MALE|FEMALE|UNKNOWN)$",
                message = "性别须为 MALE / FEMALE / UNKNOWN")
        private String gender;

        /** 出生日期：必须为过去日期 */
        @Past(message = "出生日期必须为过去时间")
        private LocalDate birthDate;

        /** ID_CARD / PASSPORT / OTHER */
        @Pattern(regexp = "^$|^(ID_CARD|PASSPORT|OTHER)$",
                message = "证件类型须为 ID_CARD / PASSPORT / OTHER")
        private String idType;

        /** 证件号：选填，填了校验长度（5-32 位，字母数字） */
        @Pattern(regexp = "^$|^[A-Za-z0-9]{5,32}$",
                message = "证件号须为 5-32 位字母/数字")
        private String idNo;

        /** 手机号：选填，填了校验中国大陆 11 位格式 */
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$",
                message = "手机号格式非法（须 11 位 1[3-9] 开头）")
        private String phone;

        private String address;
        /** 过敏史（如 ["青霉素"]） */
        private List<String> allergies;
        /** 既往病史 */
        private List<String> pastMedicalHistory;
        /** 家族病史 */
        private List<String> familyHistory;
        /** 紧急联系人 */
        private EmergencyContact emergencyContact;
    }

    /** 紧急联系人（创建/更新请求共用） */
    @Data
    public static class EmergencyContact {
        private String name;
        private String relation;
        private String phone;
    }

    /** §2.2.1 创建响应（SummaryResponse） */
    public record SummaryResponse(
            String patientId,   // patient_no（业务可读）
            String name,
            String status
    ) {}

    // ===== §2.2.2 查询详情 =====

    /** §2.2.2 查询响应（DetailResponse，含脱敏） */
    public record DetailResponse(
            String patientId,           // patient_no
            String name,
            String gender,
            LocalDate birthDate,
            String idNoMasked,          // 脱敏后证件号
            String phoneMasked,         // 脱敏后手机号
            List<String> allergies,
            List<String> pastMedicalHistory,
            String status
    ) {}

    // ===== §2.2.3 分页查询 =====

    /** §2.2.3 分页列表 item（ListItem，含脱敏） */
    public record ListItem(
            String patientId,           // patient_no
            String name,
            String phoneMasked,         // 脱敏后手机号
            String status
    ) {}

    // ===== §2.2.4 更新档案 =====

    @Data
    public static class UpdateRequest {
        /** 手机号：选填，填了校验中国大陆 11 位格式 */
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$",
                message = "手机号格式非法（须 11 位 1[3-9] 开头）")
        private String phone;
        private String address;
        private List<String> allergies;
        private List<String> pastMedicalHistory;
        private List<String> familyHistory;
        private EmergencyContact emergencyContact;
    }

    /** §2.2.4 更新响应（UpdateResponse） */
    public record UpdateResponse(
            String patientId,           // patient_no
            OffsetDateTime updatedAt
    ) {}

    // ===== §2.2.5 更新状态 =====

    @Data
    public static class StatusUpdateRequest {
        /** ACTIVE / DISABLED / MERGED */
        @NotBlank(message = "状态不能为空")
        private String status;
        /** 变更原因（如"重复档案，已完成合并"） */
        private String reason;
    }

    /** §2.2.5 状态更新响应（StatusResponse） */
    public record StatusResponse(
            String patientId,           // patient_no
            String status
    ) {}
}

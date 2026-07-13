package com.medconsult.patient.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Schema(description = "创建患者档案请求")
    public static class CreateRequest {
        /** 姓名：1-50 位（中文/字母/空格/点），禁止纯数字和特殊符号 */
        @Schema(description = "姓名")
        @NotBlank(message = "姓名不能为空")
        @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z·.\\s]{1,50}$",
                message = "姓名须为 1-50 位中文/字母/空格/点（不允许纯数字或特殊符号）")
        private String name;

        /** MALE / FEMALE / UNKNOWN（不传默认 UNKNOWN，传则校验白名单） */
        @Schema(description = "性别：MALE / FEMALE / UNKNOWN")
        @Pattern(regexp = "^$|^(MALE|FEMALE|UNKNOWN)$",
                message = "性别须为 MALE / FEMALE / UNKNOWN")
        private String gender;

        /** 出生日期：必须为过去日期 */
        @Schema(description = "出生日期")
        @Past(message = "出生日期必须为过去时间")
        private LocalDate birthDate;

        /** ID_CARD / PASSPORT / OTHER */
        @Schema(description = "证件类型")
        @Pattern(regexp = "^$|^(ID_CARD|PASSPORT|OTHER)$",
                message = "证件类型须为 ID_CARD / PASSPORT / OTHER")
        private String idType;

        /** 证件号：选填，填了校验长度（5-32 位，字母数字） */
        @Schema(description = "证件号")
        @Pattern(regexp = "^$|^[A-Za-z0-9]{5,32}$",
                message = "证件号须为 5-32 位字母/数字")
        private String idNo;

        /** 手机号：选填，填了校验中国大陆 11 位格式 */
        @Schema(description = "手机号")
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$",
                message = "手机号格式非法（须 11 位 1[3-9] 开头）")
        private String phone;

        @Schema(description = "地址")
        @Size(max = 255, message = "地址不能超过 255 字")
        private String address;
        /** 过敏史（如 ["青霉素"]） */
        @Schema(description = "过敏史")
        private List<String> allergies;
        /** 既往病史 */
        @Schema(description = "既往病史")
        private List<String> pastMedicalHistory;
        /** 家族病史 */
        @Schema(description = "家族病史")
        private List<String> familyHistory;
        /** 紧急联系人 */
        @Schema(description = "紧急联系人信息")
        private EmergencyContact emergencyContact;
    }

    /** 紧急联系人（创建/更新请求共用） */
    @Data
    @Schema(description = "紧急联系人")
    public static class EmergencyContact {
        @Schema(description = "联系人姓名")
        private String name;
        @Schema(description = "与患者关系")
        private String relation;
        @Schema(description = "联系人手机号")
        private String phone;
    }

    /** §2.2.1 创建响应（SummaryResponse） */
    @Schema(description = "创建患者档案响应")
    public record SummaryResponse(
            @Schema(description = "患者编号") String patientId,   // patient_no（业务可读）
            @Schema(description = "姓名") String name,
            @Schema(description = "档案状态：ACTIVE / DISABLED / MERGED") String status
    ) {}

    // ===== §2.2.2 查询详情 =====

    /** §2.2.2 查询响应（DetailResponse，含脱敏） */
    @Schema(description = "患者档案详情响应")
    public record DetailResponse(
            @Schema(description = "患者编号") String patientId,           // patient_no
            @Schema(description = "姓名") String name,
            @Schema(description = "性别") String gender,
            @Schema(description = "出生日期") LocalDate birthDate,
            @Schema(description = "脱敏证件号") String idNoMasked,          // 脱敏后证件号
            @Schema(description = "脱敏手机号") String phoneMasked,         // 脱敏后手机号
            @Schema(description = "过敏史") List<String> allergies,
            @Schema(description = "既往病史") List<String> pastMedicalHistory,
            @Schema(description = "档案状态") String status
    ) {}

    // ===== §2.2.3 分页查询 =====

    /** §2.2.3 分页列表 item（ListItem，含脱敏） */
    @Schema(description = "患者列表项")
    public record ListItem(
            @Schema(description = "患者编号") String patientId,           // patient_no
            @Schema(description = "姓名") String name,
            @Schema(description = "性别：MALE/FEMALE/UNKNOWN") String gender,
            @Schema(description = "年龄") Integer age,
            @Schema(description = "脱敏手机号") String phoneMasked,         // 脱敏后手机号
            @Schema(description = "脱敏身份证号") String idNoMasked,        // 脱敏后身份证号
            @Schema(description = "档案状态") String status,
            @Schema(description = "注册时间") java.time.LocalDateTime createdAt
    ) {}

    // ===== §2.2.4 更新档案 =====

    @Data
    @Schema(description = "更新患者档案请求")
    public static class UpdateRequest {
        /** 手机号：选填，填了校验中国大陆 11 位格式 */
        @Schema(description = "手机号")
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$",
                message = "手机号格式非法（须 11 位 1[3-9] 开头）")
        private String phone;
        @Schema(description = "地址")
        @Size(max = 255, message = "地址不能超过 255 字")
        private String address;
        @Schema(description = "过敏史")
        private List<String> allergies;
        @Schema(description = "既往病史")
        private List<String> pastMedicalHistory;
        @Schema(description = "家族病史")
        private List<String> familyHistory;
        @Schema(description = "紧急联系人信息")
        private EmergencyContact emergencyContact;
    }

    /** §2.2.4 更新响应（UpdateResponse） */
    @Schema(description = "更新患者档案响应")
    public record UpdateResponse(
            @Schema(description = "患者编号") String patientId,           // patient_no
            @Schema(description = "更新时间") OffsetDateTime updatedAt
    ) {}

    // ===== §2.2.5 更新状态 =====

    @Data
    @Schema(description = "更新患者档案状态请求")
    public static class StatusUpdateRequest {
        /** ACTIVE / DISABLED / MERGED */
        @Schema(description = "目标状态：ACTIVE / DISABLED / MERGED")
        @NotBlank(message = "状态不能为空")
        private String status;
        /** 变更原因（如"重复档案，已完成合并"） */
        @Schema(description = "变更原因")
        private String reason;
    }

    /** §2.2.5 状态更新响应（StatusResponse） */
    @Schema(description = "更新患者档案状态响应")
    public record StatusResponse(
            @Schema(description = "患者编号") String patientId,           // patient_no
            @Schema(description = "当前状态") String status
    ) {}
}

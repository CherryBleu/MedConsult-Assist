package com.medconsult.patient.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 患者档案相关请求/响应 DTO（字段命名对齐《接口文档》§2.2）。
 *
 * <p>JSON 字段（allergies / pastMedicalHistory / familyHistory / emergencyContact）
 * 在请求侧用 List/Object 接收，落库时由 service 序列化为 JSON 串；响应侧再反序列化回结构化字段。
 */
public class PatientDTO {

    // ===== §2.2.1 创建患者档案 =====

    @Data
    public static class CreateRequest {
        @NotBlank(message = "姓名不能为空")
        private String name;
        /** MALE / FEMALE / UNKNOWN */
        private String gender;
        private LocalDate birthDate;
        /** ID_CARD / PASSPORT / OTHER */
        private String idType;
        private String idNo;
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

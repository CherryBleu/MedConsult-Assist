package com.medconsult.medicalrecord.medicalrecord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 电子病历相关请求/响应 DTO（对齐《接口文档》§2.6 + 《修改建议》§2.1 调整）。
 *
 * <p>{@code recordId} 实为 {@code record_no}（业务可读编号，如 MR202607060001）。
 * {@code patientId}/{@code doctorId} 接口文档示例传业务编号串，service 层经 Feign 反查
 * patient/outpatient 服务落真实 BIGINT 主键（patient_no/doctor_no 不存在时下游返回 NOT_FOUND，
 * 事务回滚）。{@code appointmentId} 暂仍存正哈希占位（outpatient 尚未暴露反查接口，且非查询键）。
 *
 * <p><b>修订项 §2.1</b>：CreateRequest <b>不再</b>接受 prescriptions 入参——处方独立走
 * {@code POST /api/v1/prescriptions}，通过 record_id 反查。
 */
public class MedicalRecordDTO {

    // ===== §2.6.1 创建电子病历 =====

    @Data
    public static class CreateRequest {
        /** 患者编号 patient_no（如 P202607060001） */
        @NotBlank(message = "患者编号不能为空")
        private String patientId;
        /** 医生编号 doctor_no（如 D10001） */
        @NotBlank(message = "医生编号不能为空")
        private String doctorId;
        /** 预约编号 appointment_no（可空，复诊/急诊可能无预约） */
        private String appointmentId;

        /** 主诉 */
        @Size(max = 1000, message = "主诉不能超过 1000 字")
        private String chiefComplaint;
        /** 现病史 */
        private String presentIllness;
        /** 既往史 */
        private String pastHistory;
        /** 体格检查 */
        private String physicalExam;
        /** 初步诊断（字符串数组，service 层序列化为 JSON 串入库） */
        private List<String> initialDiagnosis;
        /** 医嘱 */
        private String doctorAdvice;
    }

    /** §2.6.1 创建响应 */
    public record CreateResponse(
            String recordId,     // record_no
            String status        // DRAFT
    ) {}

    // ===== §2.6.2 病历详情 =====

    /** §2.6.2 详情响应（精简版，对齐《接口文档》示例字段） */
    public record DetailResponse(
            String recordId,                  // record_no
            String patientId,                 // patient_no（透传业务编号）
            String doctorId,                  // doctor_no
            String chiefComplaint,
            List<String> initialDiagnosis,    // 解析 JSON 串
            String status
    ) {}

    // ===== §2.6.3 分页列表 =====

    /** §2.6.3 列表项 */
    public record ListItem(
            String recordId,          // record_no
            String doctorName,        // 本批无医生信息（跨服务），暂留 null，后续 Feign 组装
            String chiefComplaint,
            String status
    ) {}

    // ===== §2.6.4 更新草稿 =====

    @Data
    public static class UpdateDraftRequest {
        /** 现病史（DRAFT 可改） */
        private String presentIllness;
        /** 既往史 */
        private String pastHistory;
        /** 体格检查 */
        private String physicalExam;
        /** 主诉 */
        @Size(max = 1000, message = "主诉不能超过 1000 字")
        private String chiefComplaint;
        /** 最终诊断（归档前可补，JSON 数组串） */
        private List<String> finalDiagnosis;
        /** 医嘱 */
        private String doctorAdvice;
    }

    /** §2.6.4 更新响应 */
    public record UpdateResponse(
            String recordId,       // record_no
            LocalDateTime updatedAt
    ) {}

    // ===== §2.6.5 归档 =====

    @Data
    public static class ArchiveRequest {
        /** 确认人编号 doctor_no（医生本人） */
        @NotBlank(message = "确认人不能为空")
        private String confirmBy;
        /** 确认识注 */
        @Size(max = 500, message = "确认识注不能超过 500 字")
        private String confirmNote;
    }

    /** §2.6.5 归档响应 */
    public record ArchiveResponse(
            String recordId,       // record_no
            String status          // ARCHIVED
    ) {}

    // ===== 内部接口 /internal/medical-records/{id}/full =====

    /**
     * 病历全文响应（架构文档 §2.3，供 ai-service 病历摘要）。
     * <p>含完整字段；ai-service 按 scope 裁剪后喂给大模型。
     */
    public record FullRecordResponse(
            String recordNo,
            Long patientId,                    // BIGINT 主键（跨服务内部调用用主键更稳定）
            Long doctorId,
            String chiefComplaint,
            String presentIllness,
            String pastHistory,
            String physicalExam,
            List<String> initialDiagnosis,
            List<String> finalDiagnosis,
            String doctorAdvice,
            String status,
            LocalDateTime createdAt,
            LocalDateTime archivedAt
    ) {}
}

package com.medconsult.common.feign.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 病历全文响应（架构文档 §2.3，供 ai-service 病历摘要）。
 *
 * <p>跨服务共享 DTO，与 medical-record-service 的
 * {@code MedicalRecordDTO.FullRecordResponse} 字段对齐。
 * ai-service 按此结构接收病历全文，裁剪后喂给大模型做摘要 / 影像分析。
 *
 * @param recordNo         病历业务编号
 * @param patientId        患者主键
 * @param doctorId         医生主键
 * @param chiefComplaint   主诉
 * @param presentIllness   现病史
 * @param pastHistory      既往史
 * @param physicalExam     体格检查
 * @param initialDiagnosis 初步诊断
 * @param finalDiagnosis   最终诊断
 * @param doctorAdvice     医嘱
 * @param status           病历状态
 * @param createdAt        创建时间
 * @param archivedAt       归档时间
 */
public record MedicalRecordFullDTO(
        String recordNo,
        Long patientId,
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
) {
    /** 空对象（下游异常兜底用） */
    public static MedicalRecordFullDTO empty() {
        return new MedicalRecordFullDTO(null, null, null, "", "", "", "",
                List.of(), List.of(), "", null, null, null);
    }
}

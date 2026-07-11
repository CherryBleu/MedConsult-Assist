package com.medconsult.common.feign.dto;

import java.util.List;

/**
 * 患者上下文 DTO（架构文档 §2.3 内部接口 GET /internal/patients/{id}/context）。
 *
 * <p>patient-service 提供给 ai-service 做用药/分诊分析的脱敏上下文。
 * 字段按调用方 scope 裁剪（§4.4：AI_SERVICE 受限字段）。
 *
 * <p><b>故意不含敏感原始字段</b>（idNo 明文、完整病历）——AI 只需分析必要信息。
 */
public record PatientContextDTO(
        Long patientId,
        String name,            // 已脱敏（如"张*"）
        Integer age,
        String gender,          // MALE/FEMALE/UNKNOWN
        List<String> allergies,
        List<String> pastMedicalHistory,
        List<String> currentMedications  // 当前在用药品（prescription_item where not cancelled）
) {
    public static PatientContextDTO empty(Long patientId) {
        return new PatientContextDTO(patientId, null, null, null, List.of(), List.of(), List.of());
    }
}

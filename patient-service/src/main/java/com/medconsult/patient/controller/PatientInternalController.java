package com.medconsult.patient.controller;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.PatientContextDTO;
import com.medconsult.patient.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者档案对内接口（对齐架构文档 §2.3）。
 *
 * <p>路径前缀 /internal/patients（对内，不走 Gateway 路由，由调用方带服务 JWT 鉴权）。
 * <p>路径变量 {@code patientId} 是 BIGINT 主键（与对外接口用 patient_no 不同）。
 *
 * <p>供 ai-service 做用药/分诊分析（§2.3 patient-service 提供的 2 个接口）：
 * <ul>
 *   <li>GET /internal/patients/{id}/context → 脱敏基础信息 + 过敏史 + 既往史</li>
 *   <li>GET /internal/patients/{id}/allergies → string[] 过敏史</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/patients")
@RequiredArgsConstructor
public class PatientInternalController {

    private final PatientService patientService;

    /** 架构文档 §2.3：查患者上下文（供 ai-service） */
    @GetMapping("/{patientId}/context")
    public Result<PatientContextDTO> context(@PathVariable Long patientId) {
        return Result.ok(patientService.internalContext(patientId));
    }

    /** 架构文档 §2.3：查患者过敏史（供 ai-service / drug-service 用药分析） */
    @GetMapping("/{patientId}/allergies")
    public Result<List<String>> allergies(@PathVariable Long patientId) {
        return Result.ok(patientService.internalAllergies(patientId));
    }
}

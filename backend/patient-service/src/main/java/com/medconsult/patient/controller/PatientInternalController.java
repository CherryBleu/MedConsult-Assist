package com.medconsult.patient.controller;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.common.feign.dto.PatientContextDTO;
import com.medconsult.common.feign.dto.PatientRegisterRequest;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.patient.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者档案对内接口（对齐架构文档 §2.3）。
 *
 * <p>路径前缀 /internal/patients（对内，不走 Gateway 路由，由调用方带服务 JWT 鉴权）。
 * <p>路径变量 {@code patientId} 是 BIGINT 主键（与对外接口用 patient_no 不同）。
 *
 * <p><b>鉴权</b>：每个端点调 {@link SecurityContext#requireService()} 强制服务身份——
 * 只有带有效服务 JWT（X-Service-Code 头，由 AuthRelayInterceptor 注入）的内部调用方才能访问。
 * 防御纵深：即便网关误配路由把 /internal/* 暴露，无服务身份的请求也会被拒（401）。
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
        SecurityContext.requireService();
        return Result.ok(patientService.internalContext(patientId));
    }

    /** 架构文档 §2.3：查患者过敏史（供 ai-service / drug-service 用药分析） */
    @GetMapping("/{patientId}/allergies")
    public Result<List<String>> allergies(@PathVariable Long patientId) {
        SecurityContext.requireService();
        return Result.ok(patientService.internalAllergies(patientId));
    }

    /**
     * 架构文档 §2.3 补充：按 patient_no 反查 BIGINT 主键（供 medical-record 落库存真实主键）。
     * <p>替代正哈希占位，根治跨患者数据串号风险。未找到返回 404。
     */
    @GetMapping("/no/{patientNo}/id")
    public Result<EntityIdDTO> resolveId(@PathVariable String patientNo) {
        SecurityContext.requireService();
        return Result.ok(patientService.internalResolveId(patientNo));
    }

    /**
     * 注册即建档（供 auth-service 注册 PATIENT 角色时调用）。
     *
     * <p>auth-service 在 sys_user insert 前，先调本端点为患者建档案，拿到主键 id 后回写
     * sys_user.patient_id，使新注册用户 JWT 即携带 patientId，挂号链路打通。
     * 证件/手机冲突时返回 409 CONFLICT，auth-service 据此提示注册失败。
     */
    @PostMapping("/register")
    public Result<EntityIdDTO> createForRegister(@RequestBody PatientRegisterRequest req) {
        SecurityContext.requireService();
        return Result.ok(patientService.internalCreate(req.name(), req.idNo(), req.phone(), req.idType()));
    }

    /**
     * 内部接口：按主键 ID 批量查患者姓名（供 outpatient-service 预约列表显示患者名）。
     * <p>GET 参数传逗号分隔的 ID 列表，返回 patientId → name 映射。
     */
    @GetMapping("/names")
    public Result<java.util.Map<Long, String>> namesByIds(@RequestParam("ids") List<Long> ids) {
        SecurityContext.requireService();
        return Result.ok(patientService.internalNamesByIds(ids));
    }
}

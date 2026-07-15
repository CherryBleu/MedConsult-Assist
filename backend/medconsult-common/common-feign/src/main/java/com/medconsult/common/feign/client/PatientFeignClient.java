package com.medconsult.common.feign.client;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.EntityIdDTO;
import com.medconsult.common.feign.dto.PatientContextDTO;
import com.medconsult.common.feign.dto.PatientRegisterRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * patient-service 的 Feign 客户端（架构文档 §2.3）。
 *
 * <p>供 medical-record-service / ai-service 调用 patient-service 的 /internal/patients/* 内部接口。
 * name = {@code "patient-service"} 对应 Nacos 注册的服务名。
 *
 * <p><b>身份透传 / 错误处理</b>：同 {@link DrugFeignClient}，由 common-feign 的
 * {@link com.medconsult.common.feign.AuthRelayInterceptor} 和 {@link com.medconsult.common.feign.FeignErrorDecoder} 处理。
 */
@FeignClient(name = "patient-service", contextId = "patientFeignClient")
public interface PatientFeignClient {

    /** 内部：患者上下文（供 ai-service） */
    @GetMapping("/internal/patients/{patientId}/context")
    Result<PatientContextDTO> context(@PathVariable("patientId") Long patientId);

    /** 内部：患者过敏史（供 ai-service / drug-service） */
    @GetMapping("/internal/patients/{patientId}/allergies")
    Result<List<String>> allergies(@PathVariable("patientId") Long patientId);

    /**
     * 内部：按 patient_no 反查 BIGINT 主键（供 medical-record 落库存真实主键）。
     * <p>patientNo 不存在时下游返回 NOT_FOUND，由 FeignErrorDecoder 转为 BusinessException。
     */
    @GetMapping("/internal/patients/no/{patientNo}/id")
    Result<EntityIdDTO> resolveId(@PathVariable("patientNo") String patientNo);

    /**
     * 内部：注册即建档（供 auth-service 注册 PATIENT 角色时调用）。
     *
     * <p>建档成功返回 BIGINT 主键，auth-service 据此回写 sys_user.patient_id；
     * 证件/手机冲突时下游返回 409 CONFLICT，由 FeignErrorDecoder 转为 BusinessException。
     *
     * @param req 建档最小字段集（name/idNo/phone/idType）
     * @return 新建档案的主键 id
     */
    @PostMapping("/internal/patients/register")
    Result<EntityIdDTO> createForRegister(@RequestBody PatientRegisterRequest req);

    /**
     * 内部：按主键 ID 批量查患者姓名（供 outpatient-service 预约列表显示患者名）。
     * <p>返回 patientId → name 映射；不存在的 ID 不含在结果中。
     */
    @GetMapping("/internal/patients/names")
    Result<java.util.Map<Long, String>> namesByIds(@RequestParam("ids") List<Long> ids);
}

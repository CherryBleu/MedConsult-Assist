package com.medconsult.common.feign.client;

import com.medconsult.common.core.Result;
import com.medconsult.common.feign.dto.MedicalRecordFullDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * medical-record-service 的 Feign 客户端（架构文档 §2.3）。
 *
 * <p>供 ai-service 调用 medical-record-service 的 /internal/medical-records/* 内部接口。
 * name = {@code "medical-record-service"} 对应 Nacos 注册的服务名。
 *
 * <p><b>身份透传 / 错误处理</b>：同 {@link PatientFeignClient}，由 common-feign 的
 * {@link com.medconsult.common.feign.AuthRelayInterceptor} 和
 * {@link com.medconsult.common.feign.FeignErrorDecoder} 处理。
 */
@FeignClient(name = "medical-record-service", contextId = "medicalRecordFeignClient")
public interface MedicalRecordFeignClient {

    /**
     * 内部：病历全文（架构文档 §2.3，供 ai-service 病历摘要）。
     *
     * @param recordId 病历 BIGINT 主键（跨服务内部调用用主键更稳定）
     */
    @GetMapping("/internal/medical-records/{recordId}/full")
    Result<MedicalRecordFullDTO> getFullRecord(@PathVariable("recordId") Long recordId);
}

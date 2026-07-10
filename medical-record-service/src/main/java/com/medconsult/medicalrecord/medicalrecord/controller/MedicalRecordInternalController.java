package com.medconsult.medicalrecord.medicalrecord.controller;

import com.medconsult.common.core.Result;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.medicalrecord.medicalrecord.dto.MedicalRecordDTO;
import com.medconsult.medicalrecord.medicalrecord.service.MedicalRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 电子病历对内接口（架构文档 §2.3，/internal/medical-records）。
 *
 * <p>路径前缀 /internal/medical-records（不配 Gateway 路由，由 JwtAuthFilter 拦截，仅服务间 Feign 调用）。
 * <p>路径变量 {@code id} 是 BIGINT 主键（跨服务内部调用用主键更稳定，区别于对外的 record_no）。
 *
 * <p><b>鉴权</b>：强制服务身份（{@link SecurityContext#requireService()}），防网关误配暴露病历全文。
 *
 * <p>调用方：
 * <ul>
 *   <li>ai-service：getByIdFull 拿病历全文，做病历摘要 / 影像分析的数据源</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/medical-records")
@RequiredArgsConstructor
public class MedicalRecordInternalController {

    private final MedicalRecordService medicalRecordService;

    /** 病历全文（架构文档 §2.3，供 ai-service 病历摘要） */
    @GetMapping("/{id}/full")
    public Result<MedicalRecordDTO.FullRecordResponse> getByIdFull(@PathVariable Long id) {
        SecurityContext.requireService();
        return Result.ok(medicalRecordService.getByIdFull(id));
    }
}

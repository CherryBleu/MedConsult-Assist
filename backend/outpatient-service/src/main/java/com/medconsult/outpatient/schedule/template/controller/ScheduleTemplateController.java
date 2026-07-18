package com.medconsult.outpatient.schedule.template.controller;

import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.outpatient.schedule.template.dto.ScheduleTemplateDTO;
import com.medconsult.outpatient.schedule.template.service.ScheduleTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 排班模板对外接口（后端修改.md #16 默认排班）。
 *
 * <p>路径前缀 /api/v1/schedule-templates（对外，走 Gateway 鉴权）。
 * <p>全部接口仅 HOSPITAL_ADMIN（手动 SecurityContext 校验）。
 * <p>路径变量 {@code templateId} 实为 {@code template_no}（业务可读编号）。
 */
@RestController
@RequestMapping("/api/v1/schedule-templates")
@RequiredArgsConstructor
@Tag(name = "排班模板接口", description = "默认排班模板管理 + 一键生成（#16）")
public class ScheduleTemplateController {

    private final ScheduleTemplateService scheduleTemplateService;

    /** 查询模板列表 */
    @GetMapping
    @Operation(summary = "查询排班模板列表")
    public Result<PageResult<ScheduleTemplateDTO.TemplateListItem>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "医生编号") @RequestParam(required = false) String doctorId,
            @Parameter(description = "科室编号") @RequestParam(required = false) String departmentId,
            @Parameter(description = "是否启用") @RequestParam(required = false) Boolean enabled) {
        return Result.ok(scheduleTemplateService.list(page, pageSize, doctorId, departmentId, enabled));
    }

    /** 创建模板 */
    @PostMapping
    @Operation(summary = "创建排班模板")
    public Result<ScheduleTemplateDTO.SaveResponse> create(@Valid @RequestBody ScheduleTemplateDTO.CreateRequest req) {
        requireHospitalAdmin();
        return Result.ok(scheduleTemplateService.create(req));
    }

    /** 更新模板（不改归属医生） */
    @PutMapping("/{templateId}")
    @Operation(summary = "更新排班模板")
    public Result<ScheduleTemplateDTO.SaveResponse> update(
            @Parameter(description = "模板编号", required = true) @PathVariable String templateId,
            @Valid @RequestBody ScheduleTemplateDTO.UpdateRequest req) {
        requireHospitalAdmin();
        return Result.ok(scheduleTemplateService.update(templateId, req));
    }

    /** 删除模板（软删） */
    @DeleteMapping("/{templateId}")
    @Operation(summary = "删除排班模板")
    public Result<Void> delete(@Parameter(description = "模板编号", required = true) @PathVariable String templateId) {
        requireHospitalAdmin();
        scheduleTemplateService.delete(templateId);
        return Result.ok();
    }

    /** 一键按模板生成排班（核心） */
    @PostMapping("/apply")
    @Operation(summary = "一键生成排班")
    public Result<ScheduleTemplateDTO.ApplyResponse> apply(@Valid @RequestBody ScheduleTemplateDTO.ApplyRequest req) {
        requireHospitalAdmin();
        return Result.ok(scheduleTemplateService.apply(req.getStartDate(), req.getWeeks(), req.getDoctorId()));
    }

    private void requireHospitalAdmin() {
        JwtPayload p = SecurityContext.requireUser();
        if (!p.hasRole("HOSPITAL_ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅医院管理员可管理排班模板");
        }
    }
}

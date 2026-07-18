package com.medconsult.outpatient.schedule.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.outpatient.schedule.dto.ScheduleDTO;
import com.medconsult.outpatient.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 医生排班对外接口（对齐《接口文档》§2.4）。
 *
 * <p>路径前缀 /api/v1/schedules（对外，走 Gateway 鉴权）。
 * <p>路径变量 {@code scheduleId} 实为 {@code schedule_no}（业务可读编号）。
 */
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "排班接口", description = "医生排班管理（§2.4）")
public class ScheduleController {

    private final ScheduleService scheduleService;

    /** §2.4.1 创建排班 */
    @PostMapping
    @Operation(summary = "创建医生排班")
    public Result<ScheduleDTO.CreateResponse> create(@Valid @RequestBody ScheduleDTO.CreateRequest req) {
        return Result.ok(scheduleService.create(req));
    }

    /** §2.4.2 查询排班列表 */
    @GetMapping
    @Operation(summary = "查询排班列表")
    public Result<PageResult<ScheduleDTO.ListItem>> list(
            @Parameter(description = "页码", required = true) @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", required = true) @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "科室编号", required = false) @RequestParam(required = false) String departmentId,
            @Parameter(description = "开始日期", required = false) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "结束日期", required = false) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.ok(scheduleService.list(page, pageSize, departmentId, dateFrom, dateTo));
    }

    /** §2.4.3 查询可预约号源 */
    @GetMapping("/available")
    @Operation(summary = "查询可预约号源")
    public Result<List<ScheduleDTO.AvailableItem>> available(
            @Parameter(description = "科室编号", required = false) @RequestParam(required = false) String departmentId,
            @Parameter(description = "医生编号", required = false) @RequestParam(required = false) String doctorId,
            @Parameter(description = "日期", required = false) @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(scheduleService.available(departmentId, doctorId, date));
    }

    /** §2.4.4 更新排班状态 */
    @PatchMapping("/{scheduleId}/status")
    @Operation(summary = "更新排班状态")
    public Result<ScheduleDTO.StatusResponse> updateStatus(@Parameter(description = "排班编号", required = true) @PathVariable String scheduleId,
                                                            @Valid @RequestBody ScheduleDTO.StatusUpdateRequest req) {
        return Result.ok(scheduleService.updateStatus(scheduleId, req));
    }

    /** 管理员删除排班（软删，有未完成预约时拒绝） */
    @DeleteMapping("/{scheduleId}")
    @Operation(summary = "删除排班")
    public Result<Void> delete(@Parameter(description = "排班编号", required = true) @PathVariable String scheduleId) {
        JwtPayload p = SecurityContext.requireUser();
        if (!p.hasRole("HOSPITAL_ADMIN")) {
            throw new com.medconsult.common.core.BusinessException(com.medconsult.common.core.ErrorCode.FORBIDDEN, "仅医院管理员可删除排班");
        }
        scheduleService.delete(scheduleId);
        return Result.ok();
    }
}

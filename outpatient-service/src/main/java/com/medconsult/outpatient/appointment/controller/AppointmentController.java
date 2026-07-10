package com.medconsult.outpatient.appointment.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.outpatient.appointment.dto.AppointmentDTO;
import com.medconsult.outpatient.appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 预约挂号对外接口（对齐《接口文档》§2.5）。
 *
 * <p>路径前缀 /api/v1/appointments（对外，走 Gateway 鉴权）。
 * <p>路径变量 {@code appointmentId} 实为 {@code appointment_no}（业务可读编号）。
 */
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "预约挂号接口", description = "预约挂号管理（§2.5）")
public class AppointmentController {

    private final AppointmentService appointmentService;

    /** §2.5.1 创建预约（抢号，Redis 分布式锁） */
    @PostMapping
    @Operation(summary = "创建预约")
    public Result<AppointmentDTO.CreateResponse> create(@Valid @RequestBody AppointmentDTO.CreateRequest req) {
        return Result.ok(appointmentService.create(req));
    }

    /** §2.5.2 查询预约详情 */
    @GetMapping("/{appointmentId}")
    @Operation(summary = "查询预约详情")
    public Result<AppointmentDTO.DetailResponse> detail(@Parameter(description = "预约编号", required = true) @PathVariable String appointmentId) {
        return Result.ok(appointmentService.detail(appointmentId));
    }

    /** §2.5.3 分页查询预约 */
    @GetMapping
    @Operation(summary = "分页查询预约")
    public Result<PageResult<AppointmentDTO.ListItem>> list(
            @Parameter(description = "页码", required = true) @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", required = true) @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "患者编号", required = false) @RequestParam(required = false) String patientId,
            @Parameter(description = "预约状态", required = false) @RequestParam(required = false) String status) {
        return Result.ok(appointmentService.list(page, pageSize, patientId, status));
    }

    /** §2.5.4 取消预约（Redis 锁内释放号源） */
    @PostMapping("/{appointmentId}/cancel")
    @Operation(summary = "取消预约")
    public Result<AppointmentDTO.CancelResponse> cancel(@Parameter(description = "预约编号", required = true) @PathVariable String appointmentId,
                                                         @RequestBody(required = false) AppointmentDTO.CancelRequest req) {
        return Result.ok(appointmentService.cancel(appointmentId, req));
    }

    /** §2.5.5 更新支付状态 */
    @PatchMapping("/{appointmentId}/payment")
    @Operation(summary = "更新支付状态")
    public Result<AppointmentDTO.PaymentResponse> updatePayment(@Parameter(description = "预约编号", required = true) @PathVariable String appointmentId,
                                                                 @Valid @RequestBody AppointmentDTO.PaymentUpdateRequest req) {
        return Result.ok(appointmentService.updatePayment(appointmentId, req));
    }

    /** §2.5.6 更新就诊状态（状态机校验） */
    @PatchMapping("/{appointmentId}/status")
    @Operation(summary = "更新就诊状态")
    public Result<AppointmentDTO.StatusResponse> updateStatus(@Parameter(description = "预约编号", required = true) @PathVariable String appointmentId,
                                                               @Valid @RequestBody AppointmentDTO.StatusUpdateRequest req) {
        return Result.ok(appointmentService.updateStatus(appointmentId, req));
    }
}

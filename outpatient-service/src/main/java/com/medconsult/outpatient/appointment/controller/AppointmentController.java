package com.medconsult.outpatient.appointment.controller;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.core.Result;
import com.medconsult.outpatient.appointment.dto.AppointmentDTO;
import com.medconsult.outpatient.appointment.service.AppointmentService;
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
public class AppointmentController {

    private final AppointmentService appointmentService;

    /** §2.5.1 创建预约（抢号，Redis 分布式锁） */
    @PostMapping
    public Result<AppointmentDTO.CreateResponse> create(@Valid @RequestBody AppointmentDTO.CreateRequest req) {
        return Result.ok(appointmentService.create(req));
    }

    /** §2.5.2 查询预约详情 */
    @GetMapping("/{appointmentId}")
    public Result<AppointmentDTO.DetailResponse> detail(@PathVariable String appointmentId) {
        return Result.ok(appointmentService.detail(appointmentId));
    }

    /** §2.5.3 分页查询预约 */
    @GetMapping
    public Result<PageResult<AppointmentDTO.ListItem>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String status) {
        return Result.ok(appointmentService.list(page, pageSize, patientId, status));
    }

    /** §2.5.4 取消预约（Redis 锁内释放号源） */
    @PostMapping("/{appointmentId}/cancel")
    public Result<AppointmentDTO.CancelResponse> cancel(@PathVariable String appointmentId,
                                                         @RequestBody(required = false) AppointmentDTO.CancelRequest req) {
        return Result.ok(appointmentService.cancel(appointmentId, req));
    }

    /** §2.5.5 更新支付状态 */
    @PatchMapping("/{appointmentId}/payment")
    public Result<AppointmentDTO.PaymentResponse> updatePayment(@PathVariable String appointmentId,
                                                                 @Valid @RequestBody AppointmentDTO.PaymentUpdateRequest req) {
        return Result.ok(appointmentService.updatePayment(appointmentId, req));
    }

    /** §2.5.6 更新就诊状态（状态机校验） */
    @PatchMapping("/{appointmentId}/status")
    public Result<AppointmentDTO.StatusResponse> updateStatus(@PathVariable String appointmentId,
                                                               @Valid @RequestBody AppointmentDTO.StatusUpdateRequest req) {
        return Result.ok(appointmentService.updateStatus(appointmentId, req));
    }
}

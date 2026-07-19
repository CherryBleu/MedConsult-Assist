package com.medconsult.outpatient.appointment.service;

import com.medconsult.common.core.PageResult;
import com.medconsult.common.feign.dto.AppointmentOwnershipDTO;
import com.medconsult.outpatient.appointment.dto.AppointmentDTO;

/**
 * 预约挂号服务接口（对齐《接口文档》§2.5）。
 *
 * <p>方法对应 6 个对外接口（create/detail/list/cancel/updatePayment/updateStatus）。
 * <p>抢号并发控制由 Redis 分布式锁保证（架构文档 §7.1，lock:schedule:{scheduleId}）。
 */
public interface AppointmentService {

    /** §2.5.1 创建预约（Redis 锁内扣减号源） */
    AppointmentDTO.CreateResponse create(AppointmentDTO.CreateRequest req);

    /** §2.5.2 查询预约详情 */
    AppointmentDTO.DetailResponse detail(String appointmentNo);

    /** §2.5.3 分页查询预约，可按 patientId(patient_no)/status 过滤 */
    PageResult<AppointmentDTO.ListItem> list(int page, int pageSize, String patientId, String status);

    /** §2.5.4 取消预约（Redis 锁内释放号源） */
    AppointmentDTO.CancelResponse cancel(String appointmentNo, AppointmentDTO.CancelRequest req);

    /** §2.5.5 更新支付状态 */
    AppointmentDTO.PaymentResponse updatePayment(String appointmentNo, AppointmentDTO.PaymentUpdateRequest req);

    /** 患者签到（仅允许本人已支付预约 BOOKED -> CHECKED_IN） */
    AppointmentDTO.StatusResponse checkIn(String appointmentNo);

    /** §2.5.6 更新就诊状态（状态机校验） */
    AppointmentDTO.StatusResponse updateStatus(String appointmentNo, AppointmentDTO.StatusUpdateRequest req);

    /** Internal: appointment_no -> appointment primary key and patient/doctor ownership. */
    AppointmentOwnershipDTO internalResolveOwnership(String appointmentNo);
}

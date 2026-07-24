package com.medconsult.outpatient.appointment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.mq.audit.AuditLog;
import com.medconsult.outpatient.appointment.dto.AppointmentDTO;
import com.medconsult.outpatient.appointment.entity.Appointment;
import com.medconsult.outpatient.appointment.mapper.AppointmentMapper;
import com.medconsult.outpatient.notification.NotificationOutboxProducer;
import com.medconsult.outpatient.schedule.entity.DoctorSchedule;
import com.medconsult.outpatient.schedule.mapper.DoctorScheduleMapper;
import com.medconsult.outpatient.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 预约挂号事务体（架构文档 §7.1 抢号锁的事务层）。
 *
 * <p>独立 Bean 避免 self-injection 循环依赖：{@link AppointmentServiceImpl} 在 Redis 锁内
 * 调用本类的事务方法（{@link #createInTx} / {@link #cancelInTx}），保证"持锁期间事务已提交"。
 *
 * <p>本类所有 @Transactional 方法被外部锁包裹，事务边界与锁边界对齐：
 * 加锁 → 开事务 → DB 操作 → 提交事务 → 释放锁。其他线程在锁释放后才能读到已提交的 booked_quota。
 *
 * <p>核心原子操作（本地事务保证，架构文档 §7.1：不引入 Seata）：
 * <ul>
 *   <li>创建：schedule.booked_quota++（+ 可能 status=FULL）+ appointment 插入</li>
 *   <li>取消：schedule.booked_quota--（+ 可能 status=AVAILABLE）+ appointment.status=CANCELLED</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentTxService {

    private final AppointmentMapper appointmentMapper;
    private final DoctorScheduleMapper scheduleMapper;
    private final ScheduleService scheduleService;
    private final NotificationOutboxProducer notificationOutboxProducer;

    private static final Set<String> OCCUPIED_APPOINTMENT_STATUS = Set.of("BOOKED", "CHECKED_IN", "IN_PROGRESS");

    // ===== 创建预约事务体（锁内）=====

    /**
     * 创建预约事务体（锁内执行）。
     *
     * @param scheduleNo 排班编号（锁外解析，事务内按 no 重查拿最新值）
     */
    @Transactional
    @AuditLog(
            resourceType = "APPOINTMENT",
            action = "CREATE",
            resourceId = "#result.appointmentId()",
            targetOwnerId = "#p1.patientId",
            detail = "'appointment created'")
    public AppointmentDTO.CreateResponse createInTx(String scheduleNo, AppointmentDTO.CreateRequest req) {
        // 锁内按 no 重查最新排班（防读旧值）
        DoctorSchedule s = scheduleService.requireByNo(scheduleNo);
        if ("SUSPENDED".equals(s.getStatus()) || "CANCELLED".equals(s.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "排班不可预约，当前状态: " + s.getStatus());
        }
        int total = s.getTotalQuota() == null ? 0 : s.getTotalQuota();
        int occupied = countOccupiedAppointments(s.getId());
        int remaining = total - occupied;
        if (remaining <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "号源已约满: " + s.getScheduleNo());
        }

        // 解析 patient_id（BIGINT 主键）。
        // enforceCreateScope 已把 PATIENT 的 req.patientId 覆盖为本人 JWT pid（= patient 表主键 id）；
        // DOCTOR/管理员代挂号时 req.patientId 也是 patient 主键 id 字符串（前端从 userInfo.patientId 传入）。
        // 直接解析为 long 写入 appointment.patient_id，list/ownership 用同一值比对，无需 Feign 反查。
        String patientIdStr = req.getPatientId();
        if (patientIdStr == null || patientIdStr.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "患者编号不能为空");
        }
        long patientId;
        try {
            patientId = Long.parseLong(patientIdStr.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "患者编号格式不正确: " + patientIdStr);
        }

        // 重复预约限制：同 patient + 同 schedule 已有已支付且未结束预约才算占用。
        // UNPAID / CANCELLED / COMPLETED / NO_SHOW / REFUNDED 均视为空闲，不阻断重新预约。
        Long dupCount = appointmentMapper.selectCount(new QueryWrapper<Appointment>()
                .eq("patient_id", patientId)
                .eq("schedule_id", s.getId())
                .eq("payment_status", "PAID")
                .in("appointment_status", OCCUPIED_APPOINTMENT_STATUS));
        if (dupCount != null && dupCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该患者在此排班已有预约或未取消");
        }

        if (!"AVAILABLE".equals(s.getStatus())) {
            s.setStatus("AVAILABLE");
            scheduleMapper.updateById(s);
        }

        // 创建待支付预约不占用号源；支付成功时再占用。序号按该排班历史预约顺序生成。
        Long scheduleAppointmentCount = appointmentMapper.selectCount(new QueryWrapper<Appointment>()
                .eq("schedule_id", s.getId()));
        int queueNo = (scheduleAppointmentCount == null ? 0 : scheduleAppointmentCount.intValue()) + 1;

        // 创建预约
        Appointment a = new Appointment();
        a.setAppointmentNo(generateAppointmentNo());
        a.setPatientId(patientId);
        // patient_no 业务编号串需 Feign 到 patient-service 反查；冒烟期无 Feign，
        // 暂以 patient_id 主键字符串占位（list/detail 的 PATIENT 过滤走 patient_id，不依赖此列）。
        a.setPatientNo(String.valueOf(patientId));
        a.setDoctorId(s.getDoctorId());
        a.setDepartmentId(s.getDepartmentId());
        a.setScheduleId(s.getId());
        a.setAppointmentDate(s.getScheduleDate());
        a.setPeriod(s.getPeriod());
        a.setQueueNo(queueNo);
        a.setFee(s.getRegistrationFee());
        a.setPaymentStatus("UNPAID");
        a.setAppointmentStatus("BOOKED");
        a.setVisitReason(req.getVisitReason());
        a.setSource(req.getSource());
        appointmentMapper.insert(a);

        log.info("预约创建成功: appointmentNo={} scheduleNo={} queueNo={} patientId={}",
                a.getAppointmentNo(), s.getScheduleNo(), queueNo, patientId);
        notificationOutboxProducer.enqueuePatient(
                a.getPatientId(),
                "APPOINTMENT",
                "预约已创建",
                "您已创建预约 " + a.getAppointmentNo() + "，请及时完成支付。",
                "APPOINTMENT",
                a.getAppointmentNo());
        notificationOutboxProducer.enqueueDoctor(
                a.getDoctorId(),
                "APPOINTMENT",
                "新的预约",
                "患者已创建预约 " + a.getAppointmentNo() + "，等待支付。",
                "APPOINTMENT",
                a.getAppointmentNo());
        return new AppointmentDTO.CreateResponse(
                a.getAppointmentNo(), queueNo, a.getFee(), a.getPaymentStatus(), a.getAppointmentStatus());
    }

    // ===== 取消预约事务体（锁内）=====

    /**
     * 取消预约事务体（锁内执行）：释放号源 + 置 CANCELLED。
     *
     * @param appointmentNo 预约编号（锁内按 no 重查拿最新值）
     */
    @Transactional
    @AuditLog(
            resourceType = "APPOINTMENT",
            action = "CANCEL",
            resourceId = "#result.appointmentId()",
            detail = "'appointment cancelled'")
    public AppointmentDTO.CancelResponse cancelInTx(String appointmentNo, AppointmentDTO.CancelRequest req,
                                                     java.util.Set<String> cancellableStatus) {
        Appointment a = requireByNo(appointmentNo);
        // 锁内二次校验状态（防并发取消）
        if (!cancellableStatus.contains(a.getAppointmentStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "当前状态不可取消: " + a.getAppointmentStatus());
        }

        // 释放号源（规则 3：取消后释放号源）：booked_quota--，若原 FULL 则转 AVAILABLE
        DoctorSchedule s = scheduleMapper.selectById(a.getScheduleId());
        if (s == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "排班不存在: " + a.getScheduleId());
        }
        int releasedQuota = 0;
        if (occupiesQuota(a)) {
            decrementBookedQuota(s);
            releasedQuota = 1;
        }

        // 置预约 CANCELLED
        a.setAppointmentStatus("CANCELLED");
        a.setCancelReason(req != null ? req.getCancelReason() : null);
        a.setCancelOperatorType(req != null ? req.getOperatorType() : null);
        appointmentMapper.updateById(a);

        log.info("预约取消成功: appointmentNo={} scheduleNo={} releasedQuota=1",
                a.getAppointmentNo(), s.getScheduleNo());
        notificationOutboxProducer.enqueuePatient(
                a.getPatientId(),
                "APPOINTMENT",
                "预约已取消",
                "您的预约 " + a.getAppointmentNo() + " 已取消。",
                "APPOINTMENT",
                a.getAppointmentNo());
        notificationOutboxProducer.enqueueDoctor(
                a.getDoctorId(),
                "APPOINTMENT",
                "预约已取消",
                "预约 " + a.getAppointmentNo() + " 已取消。",
                "APPOINTMENT",
                a.getAppointmentNo());
        return new AppointmentDTO.CancelResponse(a.getAppointmentNo(), a.getAppointmentStatus(), releasedQuota);
    }

    @Transactional
    @AuditLog(
            resourceType = "APPOINTMENT",
            action = "PAYMENT",
            resourceId = "#result.appointmentId()",
            detail = "'paymentStatus=' + #result.paymentStatus()")
    public AppointmentDTO.PaymentResponse updatePaymentInTx(String appointmentNo,
                                                            AppointmentDTO.PaymentUpdateRequest req,
                                                            java.util.Set<String> allowedPaymentStatus) {
        Appointment a = requireByNo(appointmentNo);
        if ("REFUNDING".equals(req.getPaymentStatus()) || "REFUNDED".equals(req.getPaymentStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "退款状态只能通过退款流程更新");
        }
        if (!allowedPaymentStatus.contains(req.getPaymentStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法支付状态: " + req.getPaymentStatus());
        }

        String oldPaymentStatus = a.getPaymentStatus();
        if ("PAID".equals(req.getPaymentStatus()) && !"PAID".equals(oldPaymentStatus)) {
            if (!OCCUPIED_APPOINTMENT_STATUS.contains(a.getAppointmentStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前预约状态不可支付: " + a.getAppointmentStatus());
            }
            DoctorSchedule s = scheduleMapper.selectById(a.getScheduleId());
            if (s == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "排班不存在: " + a.getScheduleId());
            }
            if ("SUSPENDED".equals(s.getStatus()) || "CANCELLED".equals(s.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "排班不可预约，当前状态: " + s.getStatus());
            }
            int total = s.getTotalQuota() == null ? 0 : s.getTotalQuota();
            int occupied = countOccupiedAppointments(s.getId());
            if (occupied >= total) {
                throw new BusinessException(ErrorCode.CONFLICT, "该时段已有预约或未取消");
            }
            s.setBookedQuota(occupied + 1);
            s.setStatus(occupied + 1 >= total ? "FULL" : "AVAILABLE");
            scheduleMapper.updateById(s);
        } else if ("UNPAID".equals(req.getPaymentStatus()) && "PAID".equals(oldPaymentStatus) && occupiesQuota(a)) {
            DoctorSchedule s = scheduleMapper.selectById(a.getScheduleId());
            if (s != null) {
                decrementBookedQuota(s);
            }
        }

        a.setPaymentStatus(req.getPaymentStatus());
        if (req.getPaymentNo() != null) {
            a.setPaymentNo(req.getPaymentNo());
        }
        if (req.getPaidAmount() != null) {
            a.setPaidAmount(req.getPaidAmount());
        }
        appointmentMapper.updateById(a);
        if ("PAID".equals(req.getPaymentStatus()) && !"PAID".equals(oldPaymentStatus)) {
            notificationOutboxProducer.enqueuePatient(
                    a.getPatientId(),
                    "APPOINTMENT",
                    "预约支付成功",
                    "您的预约 " + a.getAppointmentNo() + " 已支付成功。",
                    "APPOINTMENT",
                    a.getAppointmentNo());
            notificationOutboxProducer.enqueueDoctor(
                    a.getDoctorId(),
                    "APPOINTMENT",
                    "预约支付成功",
                    "预约 " + a.getAppointmentNo() + " 已支付成功。",
                    "APPOINTMENT",
                    a.getAppointmentNo());
        }
        return new AppointmentDTO.PaymentResponse(a.getAppointmentNo(), a.getPaymentStatus());
    }

    @Transactional
    @AuditLog(
            resourceType = "APPOINTMENT",
            action = "STATUS_CHANGE",
            resourceId = "#result.appointmentId()",
            detail = "'status=' + #result.appointmentStatus()")
    public AppointmentDTO.StatusResponse transitionStatusInTx(String appointmentNo,
                                                              String newStatus,
                                                              java.util.Map<String, java.util.Set<String>> transitions,
                                                              java.util.Set<String> allowedStatus) {
        return transitionStatusInternal(appointmentNo, newStatus, transitions, allowedStatus);
    }

    @Transactional
    @AuditLog(
            resourceType = "APPOINTMENT",
            action = "CHECK_IN",
            resourceId = "#result.appointmentId()",
            detail = "'status=' + #result.appointmentStatus()")
    public AppointmentDTO.StatusResponse checkInInTx(String appointmentNo,
                                                     java.util.Map<String, java.util.Set<String>> transitions,
                                                     java.util.Set<String> allowedStatus) {
        return transitionStatusInternal(appointmentNo, "CHECKED_IN", transitions, allowedStatus);
    }

    private AppointmentDTO.StatusResponse transitionStatusInternal(String appointmentNo,
                                                                   String newStatus,
                                                                   java.util.Map<String, java.util.Set<String>> transitions,
                                                                   java.util.Set<String> allowedStatus) {
        Appointment a = requireByNo(appointmentNo);
        if (!allowedStatus.contains(newStatus)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法预约状态: " + newStatus);
        }
        String current = a.getAppointmentStatus();
        Set<String> allowed = transitions.get(current);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT, "非法状态流转: " + current + " -> " + newStatus);
        }
        boolean occupiedBefore = occupiesQuota(a);
        a.setAppointmentStatus(newStatus);
        boolean occupiedAfter = occupiesQuota(a);
        if (occupiedBefore && !occupiedAfter) {
            DoctorSchedule s = scheduleMapper.selectById(a.getScheduleId());
            if (s != null) {
                decrementBookedQuota(s);
            }
        }
        appointmentMapper.updateById(a);
        enqueueStatusNotification(a, newStatus);
        return new AppointmentDTO.StatusResponse(a.getAppointmentNo(), newStatus);
    }

    // ===== 私有助手 =====

    /** 按预约编号查询，未找到抛 NOT_FOUND */
    private Appointment requireByNo(String appointmentNo) {
        if (appointmentNo == null || appointmentNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "预约编号不能为空");
        }
        Appointment a = appointmentMapper.selectOne(
                new QueryWrapper<Appointment>().eq("appointment_no", appointmentNo));
        if (a == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在: " + appointmentNo);
        }
        return a;
    }

    private int countOccupiedAppointments(Long scheduleId) {
        Long count = appointmentMapper.selectCount(new QueryWrapper<Appointment>()
                .eq("schedule_id", scheduleId)
                .eq("payment_status", "PAID")
                .in("appointment_status", OCCUPIED_APPOINTMENT_STATUS));
        return count == null ? 0 : count.intValue();
    }

    private static boolean occupiesQuota(Appointment a) {
        return a != null
                && "PAID".equals(a.getPaymentStatus())
                && OCCUPIED_APPOINTMENT_STATUS.contains(a.getAppointmentStatus());
    }

    private void decrementBookedQuota(DoctorSchedule s) {
        int booked = s.getBookedQuota() == null ? 0 : s.getBookedQuota();
        s.setBookedQuota(Math.max(0, booked - 1));
        if ("FULL".equals(s.getStatus())) {
            s.setStatus("AVAILABLE");
        }
        scheduleMapper.updateById(s);
    }

    private void enqueueStatusNotification(Appointment a, String newStatus) {
        String title;
        String content;
        if ("CHECKED_IN".equals(newStatus)) {
            title = "签到成功";
            content = "您的预约 " + a.getAppointmentNo() + " 已签到，请等待叫号。";
        } else if ("IN_PROGRESS".equals(newStatus)) {
            title = "开始就诊";
            content = "您的预约 " + a.getAppointmentNo() + " 已开始就诊。";
        } else if ("COMPLETED".equals(newStatus)) {
            title = "就诊完成";
            content = "您的预约 " + a.getAppointmentNo() + " 已完成就诊。";
        } else if ("NO_SHOW".equals(newStatus)) {
            title = "预约爽约";
            content = "您的预约 " + a.getAppointmentNo() + " 已标记为爽约。";
        } else {
            return;
        }
        notificationOutboxProducer.enqueuePatient(
                a.getPatientId(),
                "APPOINTMENT",
                title,
                content,
                "APPOINTMENT",
                a.getAppointmentNo());
    }

    /** 生成预约编号：A + 雪花序列（IdWorker 的 Long 无符号 base36）。DB 有 uk_appointment_no 兜底 */
    private static String generateAppointmentNo() {
        long id = IdWorker.getId();
        return "A" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}

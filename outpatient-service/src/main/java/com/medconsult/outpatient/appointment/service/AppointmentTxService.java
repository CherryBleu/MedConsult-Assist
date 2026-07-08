package com.medconsult.outpatient.appointment.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.outpatient.appointment.dto.AppointmentDTO;
import com.medconsult.outpatient.appointment.entity.Appointment;
import com.medconsult.outpatient.appointment.mapper.AppointmentMapper;
import com.medconsult.outpatient.schedule.entity.DoctorSchedule;
import com.medconsult.outpatient.schedule.mapper.DoctorScheduleMapper;
import com.medconsult.outpatient.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // ===== 创建预约事务体（锁内）=====

    /**
     * 创建预约事务体（锁内执行）。
     *
     * @param scheduleNo 排班编号（锁外解析，事务内按 no 重查拿最新值）
     */
    @Transactional
    public AppointmentDTO.CreateResponse createInTx(String scheduleNo, AppointmentDTO.CreateRequest req) {
        // 锁内按 no 重查最新排班（防读旧值）
        DoctorSchedule s = scheduleService.requireByNo(scheduleNo);
        if (!"AVAILABLE".equals(s.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "排班不可预约，当前状态: " + s.getStatus());
        }
        int booked = s.getBookedQuota() == null ? 0 : s.getBookedQuota();
        int total = s.getTotalQuota() == null ? 0 : s.getTotalQuota();
        int remaining = total - booked;
        if (remaining <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "号源已约满: " + s.getScheduleNo());
        }

        // 解析 patient_id（BIGINT 主键）。req.getPatientId() 是 patient_no，本地存 BIGINT。
        // 此处不调 patient-service（跨服务），只做本地格式校验：非空数字。
        Long patientId = parseLong(req.getPatientId());
        if (patientId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "患者编号格式非法: " + req.getPatientId());
        }

        // 重复预约限制（规则 5 简化版）：同 patient + 同 schedule 已有未取消预约 → CONFLICT
        Long dupCount = appointmentMapper.selectCount(new QueryWrapper<Appointment>()
                .eq("patient_id", patientId)
                .eq("schedule_id", s.getId())
                .ne("appointment_status", "CANCELLED"));
        if (dupCount != null && dupCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该患者在此排班已有未取消预约");
        }

        // 扣减号源（规则 2：预约成功扣减剩余号源并生成就诊序号）
        s.setBookedQuota(booked + 1);
        if (booked + 1 >= total) {
            s.setStatus("FULL"); // 号满自动转 FULL
        }
        scheduleMapper.updateById(s);

        // 生成就诊序号 queue_no（该排班已预约数+1 = 当前 booked）
        int queueNo = booked + 1;

        // 创建预约
        Appointment a = new Appointment();
        a.setAppointmentNo(generateAppointmentNo());
        a.setPatientId(patientId);
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
        int booked = s.getBookedQuota() == null ? 0 : s.getBookedQuota();
        if (booked > 0) {
            s.setBookedQuota(booked - 1);
            if ("FULL".equals(s.getStatus())) {
                s.setStatus("AVAILABLE");
            }
            scheduleMapper.updateById(s);
        }

        // 置预约 CANCELLED
        a.setAppointmentStatus("CANCELLED");
        a.setCancelReason(req != null ? req.getCancelReason() : null);
        a.setCancelOperatorType(req != null ? req.getOperatorType() : null);
        appointmentMapper.updateById(a);

        log.info("预约取消成功: appointmentNo={} scheduleNo={} releasedQuota=1",
                a.getAppointmentNo(), s.getScheduleNo());
        return new AppointmentDTO.CancelResponse(a.getAppointmentNo(), a.getAppointmentStatus(), 1);
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

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 生成预约编号：A + 雪花序列（IdWorker 的 Long 无符号 base36）。DB 有 uk_appointment_no 兜底 */
    private static String generateAppointmentNo() {
        long id = IdWorker.getId();
        return "A" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}

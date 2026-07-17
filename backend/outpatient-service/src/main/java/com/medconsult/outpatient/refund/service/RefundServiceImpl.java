package com.medconsult.outpatient.refund.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.common.redis.DistributedLock;
import com.medconsult.common.redis.RedisKey;
import com.medconsult.common.security.JwtPayload;
import com.medconsult.common.security.SecurityContext;
import com.medconsult.outpatient.appointment.entity.Appointment;
import com.medconsult.outpatient.appointment.mapper.AppointmentMapper;
import com.medconsult.outpatient.refund.dto.RefundDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 退款服务：锁外做权限校验，锁内委托事务体完成状态机流转。
 */
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private static final Duration LOCK_LEASE = Duration.ofSeconds(5);
    private static final int LOCK_RETRY_TIMES = 20;
    private static final long LOCK_RETRY_INTERVAL_MS = 50L;

    private final AppointmentMapper appointmentMapper;
    private final DistributedLock distributedLock;
    private final RefundTxService txService;

    @Override
    public RefundDTO.RefundResponse apply(String appointmentNo, RefundDTO.RefundRequest req) {
        Appointment a = requireByNo(appointmentNo);
        enforcePatientOwnership(a);

        String lockKey = RedisKey.PREFIX + ":lock:appointment:refund:" + appointmentNo;
        RefundDTO.RefundRequest request = req == null ? new RefundDTO.RefundRequest() : req;
        for (int i = 0; i < LOCK_RETRY_TIMES; i++) {
            try {
                return distributedLock.withLock(lockKey, LOCK_LEASE,
                        () -> txService.refundInTx(appointmentNo, request));
            } catch (DistributedLock.LockNotAcquiredException e) {
                waitBeforeRetry(appointmentNo);
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT, "退款申请处理中，请稍后重试: " + appointmentNo);
    }

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

    private void enforcePatientOwnership(Appointment a) {
        JwtPayload payload = SecurityContext.getPayload();
        if (payload == null || !payload.isUser()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要用户登录");
        }
        if (!isPatient(payload)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅患者可申请退款");
        }
        Long selfPatientId = payload.patientId();
        if (selfPatientId == null || !selfPatientId.equals(a.getPatientId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权申请该预约退款");
        }
    }

    private static boolean isPatient(JwtPayload p) {
        if ("PATIENT".equals(p.primaryRole())) {
            return true;
        }
        return p.roles() != null && p.roles().contains("PATIENT");
    }

    private static void waitBeforeRetry(String appointmentNo) {
        try {
            Thread.sleep(LOCK_RETRY_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.CONFLICT, "退款申请处理中，请稍后重试: " + appointmentNo);
        }
    }
}

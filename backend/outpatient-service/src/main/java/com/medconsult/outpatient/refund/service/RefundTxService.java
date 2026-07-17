package com.medconsult.outpatient.refund.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.medconsult.common.core.BusinessException;
import com.medconsult.common.core.ErrorCode;
import com.medconsult.outpatient.appointment.entity.Appointment;
import com.medconsult.outpatient.appointment.mapper.AppointmentMapper;
import com.medconsult.outpatient.refund.dto.RefundDTO;
import com.medconsult.outpatient.refund.entity.RefundOrder;
import com.medconsult.outpatient.refund.mapper.RefundOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款锁内事务体。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundTxService {

    private static final String PROVIDER_MOCK = "MOCK";
    private static final String CHANNEL_ORIGINAL = "ORIGINAL";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";

    private final AppointmentMapper appointmentMapper;
    private final RefundOrderMapper refundOrderMapper;

    @Transactional
    public RefundDTO.RefundResponse refundInTx(String appointmentNo, RefundDTO.RefundRequest req) {
        Appointment a = requireByNo(appointmentNo);
        RefundOrder existing = findByAppointmentId(a.getId());
        if (existing != null) {
            return toResponse(existing, a.getPaymentStatus());
        }

        if (!"PAID".equals(a.getPaymentStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "仅已支付预约可申请退款，当前支付状态: " + a.getPaymentStatus());
        }

        RefundOrder order = buildSucceededOrder(a, req);
        try {
            refundOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            RefundOrder concurrent = findByAppointmentId(a.getId());
            if (concurrent != null) {
                return toResponse(concurrent, "REFUNDED");
            }
            throw e;
        }

        a.setPaymentStatus("REFUNDED");
        appointmentMapper.updateById(a);
        log.info("挂号退款成功: appointmentNo={} refundNo={} amount={}",
                a.getAppointmentNo(), order.getRefundNo(), order.getRefundAmount());
        return toResponse(order, a.getPaymentStatus());
    }

    private RefundOrder buildSucceededOrder(Appointment a, RefundDTO.RefundRequest req) {
        BigDecimal amount = a.getPaidAmount() != null ? a.getPaidAmount() : a.getFee();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "退款金额无效: " + amount);
        }

        LocalDateTime now = LocalDateTime.now();
        RefundOrder order = new RefundOrder();
        order.setRefundNo(generateRefundNo());
        order.setAppointmentId(a.getId());
        order.setAppointmentNo(a.getAppointmentNo());
        order.setPatientId(a.getPatientId());
        order.setRefundAmount(amount);
        order.setProvider(PROVIDER_MOCK);
        order.setChannel(CHANNEL_ORIGINAL);
        order.setIdempotencyKey(req.getIdempotencyKey());
        order.setReason(req.getReason());
        order.setStatus(STATUS_SUCCEEDED);
        order.setRequestedAt(now);
        order.setProcessedAt(now);
        order.setSucceededAt(now);
        return order;
    }

    private Appointment requireByNo(String appointmentNo) {
        Appointment a = appointmentMapper.selectOne(
                new QueryWrapper<Appointment>().eq("appointment_no", appointmentNo));
        if (a == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在: " + appointmentNo);
        }
        return a;
    }

    private RefundOrder findByAppointmentId(Long appointmentId) {
        return refundOrderMapper.selectOne(
                new QueryWrapper<RefundOrder>().eq("appointment_id", appointmentId));
    }

    private static RefundDTO.RefundResponse toResponse(RefundOrder order, String paymentStatus) {
        return new RefundDTO.RefundResponse(
                order.getRefundNo(),
                order.getAppointmentNo(),
                order.getStatus(),
                paymentStatus,
                order.getProvider(),
                order.getRefundAmount());
    }

    private static String generateRefundNo() {
        long id = IdWorker.getId();
        return "R" + Long.toUnsignedString(id, Character.MAX_RADIX).toUpperCase();
    }
}

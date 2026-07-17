package com.medconsult.outpatient.refund.service;

import com.medconsult.outpatient.refund.dto.RefundDTO;

/**
 * 挂号退款服务。
 */
public interface RefundService {

    RefundDTO.RefundResponse apply(String appointmentNo, RefundDTO.RefundRequest req);
}

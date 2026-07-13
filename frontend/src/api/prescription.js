import request from '@/utils/request'

// 处方 API（对齐后端 medical-record-service PrescriptionController，
// 接口集见 docs/接口文档.md §2.9 处方管理 / docs/修改建议.md §2.1 八态状态机）
//
// 后端 prescriptionId 实为 prescription_no（业务可读编号）。
// 状态机：DRAFT → PENDING_REVIEW → APPROVED → PAID → DISPENSED → COMPLETED
//                  │                     │
//                  ↓ 驳回               ↓ 退方
//                REJECTED             CANCELLED

// 处方列表（对齐 GET /prescriptions，可按 status 过滤——药师审方工作台）
export const getPrescriptionListApi = (params) => {
  return request({ url: '/prescriptions', method: 'get', params })
}

// 处方详情（含明细，对齐 GET /prescriptions/{prescriptionId}）
export const getPrescriptionDetailApi = (id) => {
  return request({ url: `/prescriptions/${id}`, method: 'get' })
}

// 提交审方（DRAFT → PENDING_REVIEW，对齐 POST /prescriptions/{id}/submit）
export const submitPrescriptionApi = (id) => {
  return request({ url: `/prescriptions/${id}/submit`, method: 'post' })
}

// 药师审方（PENDING_REVIEW → APPROVED | REJECTED，对齐 POST /prescriptions/{id}/review）
// payload: { action: 'APPROVE'|'REJECT', pharmacistId, reviewComment?, rejectReason? }
export const reviewPrescriptionApi = (id, data) => {
  return request({ url: `/prescriptions/${id}/review`, method: 'post', data })
}

// 处方缴费（APPROVED → PAID，对齐 POST /prescriptions/{id}/pay）
// payload: { paidAmount, paymentNo }
export const payPrescriptionApi = (id, data) => {
  return request({ url: `/prescriptions/${id}/pay`, method: 'post', data })
}

// 调剂发药（APPROVED/PAID → DISPENSED，对齐 POST /prescriptions/{id}/dispense）
// payload: { pharmacistId, itemDrugNoMap? }
export const dispensePrescriptionApi = (id, data) => {
  return request({ url: `/prescriptions/${id}/dispense`, method: 'post', data })
}

// 发药完成（DISPENSED → COMPLETED，对齐 POST /prescriptions/{id}/complete）
export const completePrescriptionApi = (id) => {
  return request({ url: `/prescriptions/${id}/complete`, method: 'post' })
}

// 退方（APPROVED/PAID → CANCELLED，对齐 POST /prescriptions/{id}/cancel）
// payload: { cancelReason, operatorId }
export const cancelPrescriptionApi = (id, data) => {
  return request({ url: `/prescriptions/${id}/cancel`, method: 'post', data })
}

import request from '@/utils/request'
import {
  mockPrescriptionList,
  mockPrescriptionDetail,
  mockCreatePrescription,
  mockSubmitPrescription,
  mockReviewPrescription,
  mockPayPrescription,
  mockDispensePrescription,
  mockCompletePrescription,
  mockCancelPrescription
} from '@/mock/prescription'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 处方 API（对齐后端 medical-record-service PrescriptionController，
// 接口集见 docs/接口文档.md §2.9 处方管理 / docs/修改建议.md §2.1 八态状态机）
//
// 后端 prescriptionId 实为 prescription_no（业务可读编号）。
// 状态机：DRAFT → PENDING_REVIEW → APPROVED → PAID → DISPENSED → COMPLETED
//                  │                     │
//                  ↓ 驳回               ↓ 退方
//                REJECTED             CANCELLED

const mapPrescriptionItem = (item = {}) => ({
  id: item.itemId ?? item.id,
  itemId: item.itemId ?? item.id,
  drugName: item.drugName ?? item.drugNameSnapshot,
  specification: item.specification ?? item.specificationSnapshot,
  dosage: item.dosage,
  frequency: item.frequency,
  route: item.route,
  days: item.days,
  quantity: item.quantity,
  unit: item.unit,
  unitPrice: item.unitPrice,
  subtotal: item.subtotal
})

const mapPrescription = (p = {}) => ({
  id: p.prescriptionId ?? p.prescriptionNo ?? p.prescription_no ?? p.id,
  prescriptionId: p.prescriptionId ?? p.prescriptionNo ?? p.prescription_no ?? p.id,
  recordId: p.recordId ?? p.recordNo ?? p.record_id,
  status: p.status,
  totalFee: p.totalFee ?? p.total_fee,
  paidAmount: p.paidAmount ?? p.paid_amount,
  paymentStatus: p.paymentStatus ?? p.payment_status,
  paymentNo: p.paymentNo ?? p.payment_no,
  source: p.source,
  pharmacyPharmacistId: p.pharmacyPharmacistId ?? p.pharmacy_pharmacist_id,
  reviewedAt: p.reviewedAt ?? p.reviewed_at,
  reviewComment: p.reviewComment ?? p.review_comment,
  rejectReason: p.rejectReason ?? p.reject_reason,
  createdAt: p.createdAt ?? p.created_at,
  items: Array.isArray(p.items) ? p.items.map(mapPrescriptionItem) : []
})

const mapPage = (data) => {
  const list = data?.items ?? data?.records ?? (Array.isArray(data) ? data : [])
  const mapped = list.map(mapPrescription)
  return {
    ...(data && !Array.isArray(data) ? data : {}),
    items: mapped,
    records: mapped,
    total: data?.total ?? mapped.length
  }
}

// 处方列表（对齐 GET /prescriptions，可按 status 过滤）
export const getPrescriptionListApi = async (params) => {
  if (USE_MOCK) return Promise.resolve(mockPrescriptionList(params))
  const res = await request({ url: '/prescriptions', method: 'get', params })
  res.data = mapPage(res.data)
  return res
}

// 处方详情（含明细，对齐 GET /prescriptions/{prescriptionId}）
export const getPrescriptionDetailApi = async (id) => {
  if (USE_MOCK) return Promise.resolve(mockPrescriptionDetail(id))
  const res = await request({ url: `/prescriptions/${id}`, method: 'get' })
  if (res.data) res.data = mapPrescription(res.data)
  return res
}

// 医生开方（DRAFT，对齐 POST /prescriptions）
export const createPrescriptionApi = (data) => {
  if (USE_MOCK) return Promise.resolve(mockCreatePrescription(data))
  return request({ url: '/prescriptions', method: 'post', data })
}

// 提交审方（DRAFT → PENDING_REVIEW，对齐 POST /prescriptions/{id}/submit）
export const submitPrescriptionApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockSubmitPrescription(id))
  return request({ url: `/prescriptions/${id}/submit`, method: 'post' })
}

// 药师审方（PENDING_REVIEW → APPROVED | REJECTED，对齐 POST /prescriptions/{id}/review）
// payload: { action: 'APPROVE'|'REJECT', pharmacistId, reviewComment?, rejectReason? }
export const reviewPrescriptionApi = (id, data) => {
  if (USE_MOCK) return Promise.resolve(mockReviewPrescription(id, data))
  return request({ url: `/prescriptions/${id}/review`, method: 'post', data })
}

// 处方缴费（APPROVED → PAID，对齐 POST /prescriptions/{id}/pay）
// payload: { paidAmount, paymentNo }
export const payPrescriptionApi = (id, data) => {
  if (USE_MOCK) return Promise.resolve(mockPayPrescription(id, data))
  return request({ url: `/prescriptions/${id}/pay`, method: 'post', data })
}

// 调剂发药（APPROVED/PAID → DISPENSED，对齐 POST /prescriptions/{id}/dispense）
// payload: { pharmacistId, itemDrugNoMap? }
export const dispensePrescriptionApi = (id, data) => {
  if (USE_MOCK) return Promise.resolve(mockDispensePrescription(id, data))
  return request({ url: `/prescriptions/${id}/dispense`, method: 'post', data })
}

// 发药完成（DISPENSED → COMPLETED，对齐 POST /prescriptions/{id}/complete）
export const completePrescriptionApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockCompletePrescription(id))
  return request({ url: `/prescriptions/${id}/complete`, method: 'post' })
}

// 退方（APPROVED/PAID → CANCELLED，对齐 POST /prescriptions/{id}/cancel）
// payload: { cancelReason, operatorId }
export const cancelPrescriptionApi = (id, data) => {
  if (USE_MOCK) return Promise.resolve(mockCancelPrescription(id, data))
  return request({ url: `/prescriptions/${id}/cancel`, method: 'post', data })
}

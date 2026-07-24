import dayjs from 'dayjs'

let mockPrescriptions = [
  {
    prescriptionId: 'RX202607190001',
    recordId: 'MR202607190001',
    patientId: 1001,
    status: 'APPROVED',
    source: 'OUTPATIENT',
    totalFee: 86.50,
    paidAmount: null,
    paymentStatus: 'UNPAID',
    paymentNo: '',
    reviewedAt: dayjs().subtract(1, 'hour').format('YYYY-MM-DD HH:mm:ss'),
    reviewComment: '剂量与疗程已核对，可缴费取药。',
    rejectReason: '',
    createdAt: dayjs().subtract(2, 'hour').format('YYYY-MM-DD HH:mm:ss'),
    items: [
      { id: 10001, drugName: '阿莫西林胶囊', specification: '0.25g*24粒', dosage: '0.5g', frequency: '每日三次', route: '口服', days: 5, quantity: 2, unit: '盒', unitPrice: 18.50, subtotal: 37.00 },
      { id: 10002, drugName: '氨溴索口服液', specification: '100ml', dosage: '10ml', frequency: '每日三次', route: '口服', days: 5, quantity: 1, unit: '瓶', unitPrice: 49.50, subtotal: 49.50 }
    ]
  },
  {
    prescriptionId: 'RX202607180006',
    recordId: 'MR202607180004',
    patientId: 1001,
    status: 'PAID',
    source: 'OUTPATIENT',
    totalFee: 42.00,
    paidAmount: 42.00,
    paymentStatus: 'PAID',
    paymentNo: 'PAY202607180006',
    reviewedAt: '2026-07-18 15:30:00',
    reviewComment: '请按医嘱完成疗程。',
    rejectReason: '',
    createdAt: '2026-07-18 15:02:00',
    items: [
      { id: 10003, drugName: '布洛芬缓释胶囊', specification: '0.3g*20粒', dosage: '0.3g', frequency: '每日两次', route: '口服', days: 3, quantity: 1, unit: '盒', unitPrice: 42.00, subtotal: 42.00 }
    ]
  },
  {
    prescriptionId: 'RX202607170003',
    recordId: 'MR202607170002',
    patientId: 1001,
    status: 'COMPLETED',
    source: 'OUTPATIENT',
    totalFee: 63.80,
    paidAmount: 63.80,
    paymentStatus: 'PAID',
    paymentNo: 'PAY202607170003',
    reviewedAt: '2026-07-17 11:15:00',
    reviewComment: '已完成发药。',
    rejectReason: '',
    createdAt: '2026-07-17 10:45:00',
    items: [
      { id: 10004, drugName: '孟鲁司特钠片', specification: '10mg*5片', dosage: '10mg', frequency: '每晚一次', route: '口服', days: 5, quantity: 1, unit: '盒', unitPrice: 63.80, subtotal: 63.80 }
    ]
  },
  {
    prescriptionId: 'RX202607160002',
    recordId: 'MR202607160002',
    patientId: 1001,
    status: 'PENDING_REVIEW',
    source: 'OUTPATIENT',
    totalFee: 28.00,
    paidAmount: null,
    paymentStatus: 'UNPAID',
    paymentNo: '',
    reviewedAt: '',
    reviewComment: '',
    rejectReason: '',
    createdAt: '2026-07-16 09:40:00',
    items: [
      { id: 10005, drugName: '盐酸西替利嗪片', specification: '10mg*12片', dosage: '10mg', frequency: '每日一次', route: '口服', days: 3, quantity: 1, unit: '盒', unitPrice: 28.00, subtotal: 28.00 }
    ]
  },
  {
    prescriptionId: 'RX202607150001',
    recordId: 'MR202607150001',
    patientId: 1001,
    status: 'REJECTED',
    source: 'OUTPATIENT',
    totalFee: 35.00,
    paidAmount: null,
    paymentStatus: 'UNPAID',
    paymentNo: '',
    reviewedAt: '2026-07-15 16:20:00',
    reviewComment: '',
    rejectReason: '处方剂量需医生重新确认。',
    createdAt: '2026-07-15 15:58:00',
    items: [
      { id: 10006, drugName: '复方感冒灵颗粒', specification: '14g*10袋', dosage: '1袋', frequency: '每日三次', route: '冲服', days: 3, quantity: 1, unit: '盒', unitPrice: 35.00, subtotal: 35.00 }
    ]
  }
]

const consumeFailOnce = (...keys) => {
  if (typeof localStorage === 'undefined') return false
  for (const key of keys) {
    if (localStorage.getItem(key) === '1') {
      localStorage.removeItem(key)
      return true
    }
  }
  return false
}

const consumeDelayOnce = (key) => {
  if (typeof localStorage === 'undefined') return false
  if (localStorage.getItem(key) === '1') {
    localStorage.removeItem(key)
    return true
  }
  return false
}

const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms))

const findPrescription = (id) => mockPrescriptions.find(item =>
  item.prescriptionId === id ||
  item.id === id ||
  String(item.prescriptionId) === String(id)
)

const pageResult = (records, params = {}) => {
  const page = Number(params.page ?? params.pageNum ?? 1)
  const pageSize = Number(params.pageSize ?? 10)
  const total = records.length
  const items = records.slice((page - 1) * pageSize, page * pageSize)
  return { records: items, items, total, page, pageNum: page, pageSize }
}

export const mockPrescriptionList = (params = {}) => {
  const isPatientPage = typeof window !== 'undefined' && window.location.pathname === '/patient/prescriptions'
  const isPharmacyReviewPage = typeof window !== 'undefined' && window.location.pathname === '/pharmacy/prescription-review'
  if (isPatientPage && consumeFailOnce('mock_patient_prescription_list_fail_once', 'mock_prescription_list_fail_once')) {
    return Promise.reject(new Error('处方列表加载失败，请重试'))
  }
  if (isPharmacyReviewPage && consumeFailOnce('mock_pharmacy_prescription_list_fail_once')) {
    return Promise.reject(new Error('处方列表加载失败，请重试'))
  }

  let records = [...mockPrescriptions]
  if (params.status) {
    records = records.filter(item => item.status === params.status)
  }
  records.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)))
  const response = { code: 0, message: 'success', data: pageResult(records, params) }
  if (params.status === 'PENDING_REVIEW' && consumeDelayOnce('mock_prescription_pending_review_delay_once')) {
    return delay(600).then(() => response)
  }
  return response
}

export const mockPrescriptionDetail = (id) => {
  if (consumeFailOnce('mock_patient_prescription_detail_fail_once')) {
    return Promise.reject(new Error('处方详情加载失败，请重试'))
  }
  const detail = findPrescription(id)
  if (!detail) return Promise.reject(new Error('处方不存在'))
  return { code: 0, message: 'success', data: { ...detail, items: [...detail.items] } }
}

export const mockCreatePrescription = (data = {}) => {
  if (consumeFailOnce('mock_prescription_create_fail_once')) {
    return Promise.reject(new Error('处方提交失败，请重试'))
  }
  const prescription = {
    prescriptionId: 'RX' + Date.now(),
    recordId: data.recordId,
    patientId: data.patientId || 1001,
    status: 'APPROVED',
    source: data.source || 'OUTPATIENT',
    totalFee: (data.items || []).reduce((sum, item) => {
      const subtotal = item.subtotal ?? (Number(item.quantity || 0) * Number(item.unitPrice || 0))
      return sum + Number(subtotal || 0)
    }, 0),
    paidAmount: null,
    paymentStatus: 'UNPAID',
    paymentNo: '',
    reviewedAt: '',
    reviewComment: '',
    rejectReason: '',
    createdAt: dayjs().format('YYYY-MM-DD HH:mm:ss'),
    items: (data.items || []).map((item, index) => ({
      id: Date.now() + index,
      ...item,
      subtotal: item.subtotal ?? (Number(item.quantity || 0) * Number(item.unitPrice || 0))
    }))
  }
  mockPrescriptions.unshift(prescription)
  return { code: 0, message: 'success', data: { prescriptionId: prescription.prescriptionId, status: prescription.status, totalFee: prescription.totalFee } }
}

export const mockSubmitPrescription = (id) => {
  if (consumeFailOnce('mock_prescription_submit_fail_once')) {
    return Promise.reject(new Error('处方提交审方失败，请重试'))
  }
  const prescription = findPrescription(id)
  if (!prescription) return Promise.reject(new Error('处方不存在'))
  prescription.status = 'PENDING_REVIEW'
  return { code: 0, message: 'success', data: { prescriptionId: prescription.prescriptionId, status: prescription.status } }
}

export const mockReviewPrescription = (id, data = {}) => {
  if (consumeFailOnce('mock_prescription_review_fail_once')) {
    return Promise.reject(new Error('审方提交失败，请重试'))
  }
  const prescription = findPrescription(id)
  if (!prescription) return Promise.reject(new Error('处方不存在'))
  if (data.action === 'REJECT') {
    prescription.status = 'REJECTED'
    prescription.rejectReason = data.rejectReason || '处方已驳回'
  } else {
    prescription.status = 'APPROVED'
  }
  prescription.reviewComment = data.reviewComment || prescription.reviewComment
  prescription.reviewedAt = dayjs().format('YYYY-MM-DD HH:mm:ss')
  return { code: 0, message: 'success', data: { prescriptionId: prescription.prescriptionId, status: prescription.status, reviewedAt: prescription.reviewedAt } }
}

export const mockPayPrescription = (id, data = {}) => {
  if (consumeFailOnce('mock_patient_prescription_pay_fail_once', 'mock_prescription_pay_fail_once')) {
    return Promise.reject(new Error('缴费失败，请检查支付状态后重试'))
  }
  const prescription = findPrescription(id)
  if (!prescription) return Promise.reject(new Error('处方不存在'))
  if (prescription.status !== 'APPROVED' || prescription.paymentStatus === 'PAID') {
    return Promise.reject(new Error('当前处方暂不可缴费'))
  }
  prescription.status = 'PAID'
  prescription.paymentStatus = 'PAID'
  prescription.paidAmount = Number(data.paidAmount ?? prescription.totalFee)
  prescription.paymentNo = data.paymentNo || 'PAY' + Date.now()
  return {
    code: 0,
    message: '支付成功',
    data: {
      prescriptionId: prescription.prescriptionId,
      status: prescription.status,
      paymentStatus: prescription.paymentStatus,
      paidAmount: prescription.paidAmount,
      paymentNo: prescription.paymentNo
    }
  }
}

export const mockDispensePrescription = (id, data = {}) => {
  if (consumeFailOnce('mock_prescription_dispense_fail_once')) {
    return Promise.reject(new Error('发药失败，请重试'))
  }
  if (!String(data.pharmacistId || '').trim()) {
    return Promise.reject(new Error('调剂药师编号不能为空'))
  }
  const prescription = findPrescription(id)
  if (!prescription) return Promise.reject(new Error('处方不存在'))
  prescription.status = 'DISPENSED'
  return { code: 0, message: 'success', data: { prescriptionId: prescription.prescriptionId, status: prescription.status, items: [] } }
}

export const mockCompletePrescription = (id) => {
  const prescription = findPrescription(id)
  if (!prescription) return Promise.reject(new Error('处方不存在'))
  prescription.status = 'COMPLETED'
  return { code: 0, message: 'success', data: { prescriptionId: prescription.prescriptionId, status: prescription.status } }
}

export const mockCancelPrescription = (id, data = {}) => {
  const prescription = findPrescription(id)
  if (!prescription) return Promise.reject(new Error('处方不存在'))
  prescription.status = 'CANCELLED'
  prescription.cancelReason = data.cancelReason || data.reason || '已取消'
  return { code: 0, message: 'success', data: { prescriptionId: prescription.prescriptionId, status: prescription.status, cancelReason: prescription.cancelReason } }
}

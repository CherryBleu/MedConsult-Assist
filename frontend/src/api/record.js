import request from '@/utils/request'
import { mockRecordList, mockRecordDetail } from '@/mock/record'
import { mockCreateRecord, mockUpdateRecord, mockArchiveRecord } from '@/mock/record'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

const toArray = (value) => {
  if (Array.isArray(value)) return value.filter(Boolean)
  if (typeof value === 'string' && value.trim()) return [value.trim()]
  return []
}

const joinText = (value) => toArray(value).join('、')

const mapPrescriptionItem = (item = {}) => ({
  prescriptionId: item.prescriptionId ?? item.prescriptionNo ?? '',
  prescriptionStatus: item.prescriptionStatus ?? '',
  drugNo: item.drugNo ?? '',
  drugName: item.drugName ?? item.name ?? '',
  name: item.name ?? item.drugName ?? '',
  specification: item.specification ?? '',
  dosage: [item.route, item.dosage, item.frequency, item.days ? `${item.days}天` : ''].filter(Boolean).join(' / '),
  frequency: item.frequency ?? '',
  route: item.route ?? '',
  days: item.days ?? '',
  quantity: item.quantity ?? '',
  unit: item.unit ?? '',
  unitPrice: item.unitPrice ?? '',
  subtotal: item.subtotal ?? ''
})

const mapRecord = (r = {}) => {
  const initialDiagnosis = toArray(r.initialDiagnosis)
  const finalDiagnosis = toArray(r.finalDiagnosis)
  return {
    id: r.recordId ?? r.recordNo ?? r.id,
    recordId: r.recordId ?? r.recordNo,
    recordNo: r.recordNo ?? r.recordId,
    patientName: r.patientName ?? r.patient?.name ?? r.name ?? '',
    patientId: r.patientId,
    doctorId: r.doctorId,
    doctorName: r.doctorName ?? r.doctor?.name ?? '',
    departmentName: r.departmentName ?? r.deptName ?? '',
    deptName: r.departmentName ?? r.deptName ?? '',
    chiefComplaint: r.chiefComplaint ?? '',
    presentIllness: r.presentIllness ?? '',
    pastHistory: r.pastHistory ?? '',
    physicalExam: r.physicalExam ?? '',
    doctorAdvice: r.doctorAdvice ?? '',
    initialDiagnosis,
    finalDiagnosis,
    initialDiagnosisText: joinText(initialDiagnosis),
    finalDiagnosisText: joinText(finalDiagnosis),
    diagnosisText: joinText(finalDiagnosis.length ? finalDiagnosis : initialDiagnosis),
    createdAt: r.createdAt ?? '',
    archivedAt: r.archivedAt ?? '',
    prescriptions: Array.isArray(r.prescriptions) ? r.prescriptions.map(mapPrescriptionItem) : [],
    status: r.status ?? r.recordStatus
  }
}

export const getRecordListApi = async (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRecordList())
  }
  const res = await request({ url: '/medical-records', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  const mapped = list.map(mapRecord)
  res.data = { ...(res.data || {}), records: mapped, items: mapped, total: res.data?.total ?? list.length }
  return res
}

export const getRecordDetailApi = async (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRecordDetail(id))
  }
  const res = await request({ url: `/medical-records/${id}`, method: 'get' })
  if (res.data) res.data = mapRecord(res.data)
  return res
}

export const createRecordApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockCreateRecord(data))
  }
  return request({
    url: '/medical-records',
    method: 'post',
    data
  })
}

export const updateRecordApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdateRecord(id, data))
  }
  return request({
    url: `/medical-records/${id}`,
    method: 'put',
    data
  })
}

export const archiveRecordApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockArchiveRecord(id))
  }
  return request({
    url: `/medical-records/${id}/archive`,
    method: 'post',
    data
  })
}

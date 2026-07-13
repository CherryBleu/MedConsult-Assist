import request from '@/utils/request'
import { mockPatientInfo, mockHealthArchive, mockUpdatePatientInfo, mockPatientList, mockPatientDetail, mockAddPatient, mockUpdatePatientStatus } from '@/mock/patient'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 后端 DetailResponse 字段 → 前端期望字段映射
// 后端: patientId(patient_no)/name/gender/birthDate/idNoMasked/phoneMasked/allergies/pastMedicalHistory/status
// 前端 UserInfo 期望: patientNo/name/gender/birthDate/idNo/phone/idType/address
const mapPatientDetail = (p) => ({
  patientNo: p.patientId ?? p.patientNo,
  patientId: p.patientId,
  name: p.name,
  gender: p.gender,
  birthDate: p.birthDate,
  idNo: p.idNoMasked ?? p.idNo,
  phone: p.phoneMasked ?? p.phone,
  idType: p.idType ?? 'ID_CARD',
  address: p.address ?? '',
  allergies: p.allergies,
  pastMedicalHistory: p.pastMedicalHistory,
  familyHistory: p.familyHistory,
  emergencyContact: p.emergencyContact,
  status: p.status
})

// 获取患者个人信息（当前登录患者的档案，对齐后端 GET /patients/{patientId}）
export const getPatientInfoApi = async (patientId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockPatientInfo())
  }
  const res = await request({ url: `/patients/${patientId}`, method: 'get' })
  if (res.data) res.data = mapPatientDetail(res.data)
  return res
}

// 获取健康档案（后端无独立健康档案端点，复用患者详情）
export const getHealthArchiveApi = async (patientId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockHealthArchive())
  }
  const res = await request({ url: `/patients/${patientId}`, method: 'get' })
  if (res.data) res.data = mapPatientDetail(res.data)
  return res
}

// 更新患者信息（对齐后端 PUT /patients/{patientId}）
export const updatePatientInfoApi = (patientId, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdatePatientInfo(data))
  }
  return request({
    url: `/patients/${patientId}`,
    method: 'put',
    data
  })
}

// 管理员：患者列表（对齐后端 GET /patients）
// 后端 ListItem: patientId/name/gender/age/phoneMasked/idNoMasked/status/createdAt
// 前端 PatientManage 期望: patientNo/name/gender/age/phone/idCard/status/createdAt
const mapAdminPatient = (p) => ({
  patientNo: p.patientId ?? p.patientNo,
  patientId: p.patientId,
  name: p.name,
  gender: p.gender,
  age: p.age,
  phone: p.phoneMasked ?? p.phone,
  idCard: p.idNoMasked ?? p.idCard,
  status: p.status,
  createdAt: p.createdAt
})

export const getAdminPatientListApi = async (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockPatientList(params))
  }
  const res = await request({
    url: '/patients',
    method: 'get',
    params
  })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  const mapped = list.map(mapAdminPatient)
  res.data = { ...(res.data || {}), records: mapped, items: mapped, total: res.data?.total ?? mapped.length }
  return res
}

// 管理员：患者详情（对齐后端 GET /patients/{patientId}）
export const getPatientDetailApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockPatientDetail(id))
  }
  return request({
    url: `/patients/${id}`,
    method: 'get'
  })
}

// 管理员：更新患者状态（对齐后端 PATCH /patients/{patientId}/status）
export const updatePatientStatusApi = (id, status) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdatePatientStatus(id, status))
  }
  return request({
    url: `/patients/${id}/status`,
    method: 'patch',
    data: { status }
  })
}

// 管理员：新增患者（对齐后端 POST /patients）
export const addPatientApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAddPatient(data))
  }
  return request({
    url: '/patients',
    method: 'post',
    data
  })
}

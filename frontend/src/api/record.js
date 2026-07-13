import request from '@/utils/request'
import { mockRecordList, mockRecordDetail } from '@/mock/record'
import { mockCreateRecord, mockUpdateRecord, mockArchiveRecord } from '@/mock/record'
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 后端 MedicalRecord 字段 → 前端期望字段映射
// 后端 ListItem: recordId/doctorName/chiefComplaint/status
// 详情接口额外含: presentIllness/pastHistory/physicalExam/doctorAdvice
const mapRecord = (r) => ({
  id: r.recordId ?? r.recordNo ?? r.id,
  recordId: r.recordId ?? r.recordNo,
  recordNo: r.recordNo ?? r.recordId,
  // 后端 ListItem 暂不返回 patientName（跨服务聚合待补），先回退到 patientId 避免显示 undefined
  patientName: r.patientName ?? r.patientId ?? '',
  patientId: r.patientId,
  doctorName: r.doctorName ?? '',
  deptName: r.departmentName ?? r.deptName,
  chiefComplaint: r.chiefComplaint ?? '',
  presentIllness: r.presentIllness,
  pastHistory: r.pastHistory,
  physicalExam: r.physicalExam,
  doctorAdvice: r.doctorAdvice,
  initialDiagnosis: Array.isArray(r.initialDiagnosis) ? r.initialDiagnosis.join('；') : (r.initialDiagnosis ?? ''),
  status: r.status ?? r.recordStatus
})

// 获取病历列表（分页，对齐后端 GET /medical-records）
export const getRecordListApi = async (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRecordList())
  }
  const res = await request({ url: '/medical-records', method: 'get', params })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = { ...(res.data || {}), records: list.map(mapRecord), items: list.map(mapRecord), total: res.data?.total ?? list.length }
  return res
}

// 获取病历详情（对齐后端 GET /medical-records/{recordId}）
export const getRecordDetailApi = async (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRecordDetail(id))
  }
  const res = await request({ url: `/medical-records/${id}`, method: 'get' })
  if (res.data) res.data = mapRecord(res.data)
  return res
}

// 创建病历（对齐后端 POST /medical-records）
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

// 更新病历草稿（对齐后端 PUT /medical-records/{recordId}）
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

// 归档病历（对齐后端 POST /medical-records/{recordId}/archive）
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

import request from '@/utils/request'
import { mockDoctorList } from '@/mock/doctor'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 后端 Doctor 字段 → 前端期望字段映射（后端 doctorId/doctorName/departmentName，前端 id/name/deptName）
const mapDoctor = (d) => ({
  id: d.doctorId ?? d.id,
  name: d.doctorName ?? d.name,
  departmentId: d.departmentId,
  deptName: d.departmentName ?? d.deptName,
  title: d.title,
  specialties: Array.isArray(d.specialties) ? d.specialties.join('、') : (d.specialties ?? ''),
  fee: d.registrationFee ?? d.fee,
  enabled: d.enabled
})

// 获取医生列表（对齐后端 GET /doctors?departmentId=，支持按科室过滤）
export const getDoctorListApi = async (deptId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDoctorList(deptId))
  }
  const res = await request({
    url: '/doctors',
    method: 'get',
    params: deptId ? { departmentId: deptId } : {}
  })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  const mapped = list.map(mapDoctor)
  res.data = mapped
  return res
}

// 获取医生详情（后端 DoctorController 暂无 /{id} 端点，从列表过滤）
export const getDoctorDetailApi = async (id) => {
  if (USE_MOCK) {
    const list = mockDoctorList().data
    return Promise.resolve({ code: 0, message: 'success', data: list.find(i => i.id === Number(id)) })
  }
  const res = await request({ url: '/doctors', method: 'get' })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  const mapped = list.map(mapDoctor)
  // 按 id 匹配单条（兼容字符串/数字 id）
  const found = mapped.find(i => String(i.id) === String(id))
  res.data = found || null
  return res
}

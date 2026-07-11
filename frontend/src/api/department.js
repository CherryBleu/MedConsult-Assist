import request from '@/utils/request'
import { mockDepartmentList } from '@/mock/department'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 后端 Department 字段 → 前端期望字段映射（后端 departmentId/departmentName，前端 id/name）
const mapDept = (d) => ({
  id: d.departmentId ?? d.id,
  name: d.departmentName ?? d.name,
  description: d.location ?? d.description,
  location: d.location,
  enabled: d.enabled
})

// 获取科室列表
export const getDepartmentListApi = async () => {
  if (USE_MOCK) {
    return Promise.resolve(mockDepartmentList())
  }
  const res = await request({ url: '/departments', method: 'get' })
  // 后端返回 PageResult {items,total}，拦截器已补 records；取数组并映射字段
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = list.map(mapDept)
  return res
}

import request from '@/utils/request'
import { mockDoctorList } from '@/mock/doctor'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 获取医生列表（对齐后端 GET /doctors?departmentId=，支持按科室过滤）
export const getDoctorListApi = (deptId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDoctorList(deptId))
  }
  return request({
    url: '/doctors',
    method: 'get',
    params: deptId ? { departmentId: deptId } : {}
  })
}

// 获取医生详情（后端 DoctorController 暂无 /{id} 端点，从列表过滤）
export const getDoctorDetailApi = (id) => {
  if (USE_MOCK) {
    const list = mockDoctorList().data
    return Promise.resolve({ code: 0, message: 'success', data: list.find(i => i.id === Number(id)) })
  }
  return request({
    url: '/doctors',
    method: 'get'
  })
}

import request from '@/utils/request'
import { mockDoctorList } from '@/mock/doctor'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 根据科室ID获取医生列表
export const getDoctorListApi = (deptId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockDoctorList(deptId))
  }
  return request({
    url: '/doctor/list',
    method: 'get',
    params: { departmentId: deptId }
  })
}

// 获取医生详情
export const getDoctorDetailApi = (id) => {
  if (USE_MOCK) {
    const list = mockDoctorList().data
    return Promise.resolve({ code: 0, message: 'success', data: list.find(i => i.id === Number(id)) })
  }
  return request({
    url: `/doctor/${id}`,
    method: 'get'
  })
}
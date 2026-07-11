import request from '@/utils/request'
import { mockDepartmentList } from '@/mock/department'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 获取科室列表
export const getDepartmentListApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockDepartmentList())
  }
  return request({
    url: '/departments',
    method: 'get'
  })
}
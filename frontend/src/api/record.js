import request from '@/utils/request'
import { mockRecordList, mockRecordDetail } from '@/mock/record'
import { mockCreateRecord, mockUpdateRecord, mockArchiveRecord } from '@/mock/record'
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 获取病历列表（分页，对齐后端 GET /medical-records）
export const getRecordListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRecordList())
  }
  return request({
    url: '/medical-records',
    method: 'get',
    params
  })
}

// 获取病历详情（对齐后端 GET /medical-records/{recordId}）
export const getRecordDetailApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRecordDetail(id))
  }
  return request({
    url: `/medical-records/${id}`,
    method: 'get'
  })
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

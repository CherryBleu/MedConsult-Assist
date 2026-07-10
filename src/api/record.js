import request from '@/utils/request'
import { mockRecordList, mockRecordDetail } from '@/mock/record'
import { mockCreateRecord, mockUpdateRecord, mockArchiveRecord } from '@/mock/record'
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 获取我的病历列表（分页）
export const getRecordListApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRecordList())
  }
  return request({
    url: '/medical-record/page',
    method: 'get',
    params
  })
}

// 获取病历详情
export const getRecordDetailApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRecordDetail(id))
  }
  return request({
    url: `/medical-record/${id}`,
    method: 'get'
  })
}

// 创建病历
export const createRecordApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockCreateRecord(data))
  }
  return request({
    url: '/medical-record',
    method: 'post',
    data
  })
}

// 更新病历（草稿状态）
export const updateRecordApi = (id, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockUpdateRecord(id, data))
  }
  return request({
    url: `/medical-record/${id}`,
    method: 'put',
    data
  })
}

// 归档病历
export const archiveRecordApi = (id) => {
  if (USE_MOCK) {
    return Promise.resolve(mockArchiveRecord(id))
  }
  return request({
    url: `/medical-record/${id}/archive`,
    method: 'post'
  })
}


import request from '@/utils/request'
import {
  mockNoticeList, mockUnreadCount, mockMarkRead, mockMarkAllRead, mockDeleteNotice
} from '@/mock/notice'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

export const getNoticeListApi = (params) => {
  if (USE_MOCK) return Promise.resolve(mockNoticeList(params))
  return request({ url: '/notices', method: 'get', params })
}

export const getUnreadCountApi = () => {
  if (USE_MOCK) return Promise.resolve(mockUnreadCount())
  return request({ url: '/notices/unread-count', method: 'get' })
}

export const markReadApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockMarkRead(id))
  return request({ url: `/notices/${id}/read`, method: 'post' })
}

export const markAllReadApi = () => {
  if (USE_MOCK) return Promise.resolve(mockMarkAllRead())
  return request({ url: '/notices/read-all', method: 'post' })
}

export const deleteNoticeApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockDeleteNotice(id))
  return request({ url: `/notices/${id}`, method: 'delete' })
}

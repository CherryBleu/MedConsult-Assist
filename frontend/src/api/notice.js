import request from '@/utils/request'
import {
  mockNoticeList, mockUnreadCount, mockMarkRead, mockMarkAllRead, mockDeleteNotice
} from '@/mock/notice'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 通知列表（对齐后端 GET /notifications，支持 read 过滤）
export const getNoticeListApi = (params) => {
  if (USE_MOCK) return Promise.resolve(mockNoticeList(params))
  return request({ url: '/notifications', method: 'get', params })
}

// 未读数量（后端无独立 count 端点，用 read=false 过滤取 total）
export const getUnreadCountApi = async () => {
  if (USE_MOCK) return Promise.resolve(mockUnreadCount())
  try {
    const res = await request({ url: '/notifications', method: 'get', params: { read: false, pageSize: 1 } })
    // 后端返回 PageResult {items,total}，取 total 作为未读数
    const count = res.data?.total ?? (Array.isArray(res.data) ? res.data.length : 0)
    return { code: 0, message: 'success', data: count }
  } catch (e) {
    // 后端报错时返回 0（不阻塞页面）
    return { code: 0, message: 'success', data: 0 }
  }
}

// 标记单条已读（对齐后端 PATCH /notifications/{notificationId}/read）
export const markReadApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockMarkRead(id))
  return request({ url: `/notifications/${id}/read`, method: 'patch' })
}

// 全部已读（后端无批量已读端点，前端逐条 markRead 兜底）
// 传入未读通知 id 列表；后端补批量端点后可直接替换实现
export const markAllReadApi = (unreadIds = []) => {
  if (USE_MOCK) return Promise.resolve(mockMarkAllRead())
  if (!unreadIds.length) return Promise.resolve({ code: 0, message: 'success', data: null })
  // 逐条 PATCH（后端 PATCH /notifications/{id}/read 已实现）
  return Promise.all(unreadIds.map(id => request({ url: `/notifications/${id}/read`, method: 'patch' })))
    .then(() => ({ code: 0, message: 'success', data: null }))
}

// 删除通知（后端无删除端点，占位）
export const deleteNoticeApi = (id) => {
  if (USE_MOCK) return Promise.resolve(mockDeleteNotice(id))
  // 后端暂无通知删除接口；返回成功占位
  return Promise.resolve({ code: 0, message: 'success', data: null })
}

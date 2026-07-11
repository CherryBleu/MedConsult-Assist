import request from '@/utils/request'
import { mockAiCallLog, mockAiFeedbackList, mockProcessFeedback } from '@/mock/ai'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// AI调用日志
export const getAiCallLogApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAiCallLog())
  }
  return request({
    url: '/ai/call-log',
    method: 'get',
    params
  })
}

// AI反馈列表
export const getAiFeedbackApi = (params) => {
  if (USE_MOCK) {
    return Promise.resolve(mockAiFeedbackList())
  }
  return request({
    url: '/ai/feedback',
    method: 'get',
    params
  })
}

// 提交AI反馈（用户端）
export const submitAiFeedbackApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve({ code: 0, message: '提交成功', data: null })
  }
  return request({
    url: '/ai/feedback',
    method: 'post',
    data
  })
}

// 处理反馈（管理员端）
export const processFeedbackApi = (id, reply) => {
  if (USE_MOCK) {
    return Promise.resolve(mockProcessFeedback(id, reply))
  }
  return request({
    url: `/ai/feedback/${id}/reply`,
    method: 'post',
    data: { reply }
  })
}
import request from '@/utils/request'
import { mockAiCallLog, mockAiFeedbackList, mockProcessFeedback } from '@/mock/ai'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

const SERVICE_TYPE_LABELS = {
  SYMPTOM_CHAT: '症状自诊',
  TRIAGE: '智能分诊',
  SUMMARY: '病历摘要',
  MEDICAL_RECORD_SUMMARY: '病历摘要',
  MEDICATION_ANALYSIS: '用药分析',
  IMAGING_DETECTION: '影像识别',
  REPORT_ANALYSIS: '报告分析'
}

const serviceTypeLabel = (value) => {
  const key = String(value || '').trim()
  return SERVICE_TYPE_LABELS[key] || key || '-'
}

const mapFeedback = (item) => ({
  id: item.id ?? item.feedbackId ?? item.feedbackNo,
  feedbackNo: item.feedbackNo ?? item.feedbackId ?? '-',
  feedbackId: item.feedbackId ?? item.feedbackNo ?? item.id,
  aiResultType: item.aiResultType ?? item.serviceType,
  aiResultId: item.aiResultId ?? item.resultId ?? item.submittedData,
  serviceType: serviceTypeLabel(item.serviceType ?? item.aiResultType),
  submittedData: item.submittedData ?? item.aiResultId ?? item.resultId ?? '-',
  userId: item.userId ?? item.feedbackBy,
  userName: item.userName ?? (item.feedbackBy ? `用户 ${item.feedbackBy}` : '-'),
  rating: Number(item.rating || 0),
  content: item.content ?? item.comment ?? '-',
  status: item.status ?? (item.adminReply || item.reply ? 'PROCESSED' : 'PENDING'),
  processedBy: item.processedBy,
  processedAt: item.processedAt,
  reply: item.reply ?? item.adminReply ?? '',
  createdAt: item.createdAt ?? item.createTime ?? '-'
})

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
export const getAiFeedbackApi = async (params) => {
  if (USE_MOCK) {
    const res = await mockAiFeedbackList()
    const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
    return Promise.resolve({ ...res, data: list.map(mapFeedback) })
  }
  const res = await request({
    url: '/ai/feedback',
    method: 'get',
    params
  })
  const list = res.data?.items ?? res.data?.records ?? (Array.isArray(res.data) ? res.data : [])
  res.data = list.map(mapFeedback)
  return res
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

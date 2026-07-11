import request from '@/utils/request'
import { mockTriageResult, mockCreateSession, mockSendMessage, mockSessionHistory } from '@/mock/ai'
import { mockRecordSummary, mockRecordSummaryByText, mockMedicationAnalysis, mockImagingSubmit, mockImagingResult, mockConfirmSummary, mockReviewImagingDetection, mockImagingHistoryList } from '@/mock/ai'
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 智能分诊
export const triageApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockTriageResult(data.symptoms))
  }
  return request({
    url: '/ai/triage',
    method: 'post',
    data
  })
}

// 创建AI问诊会话（症状自诊，纯RAG不调大模型）
export const createSessionApi = () => {
  if (USE_MOCK) {
    return Promise.resolve(mockCreateSession())
  }
  return request({
    url: '/ai/symptom-chat/session',
    method: 'post'
  })
}

// 发送问诊消息（症状自诊，纯RAG不调大模型）
export const sendChatMessageApi = (sessionId, message) => {
  if (USE_MOCK) {
    return Promise.resolve(mockSendMessage(sessionId, message))
  }
  return request({
    url: '/ai/symptom-chat',
    method: 'post',
    data: { sessionId, message }
  })
}

// 获取会话历史
export const getSessionHistoryApi = (sessionId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockSessionHistory(sessionId))
  }
  return request({
    url: `/ai/symptom-chat/history/${sessionId}`,
    method: 'get'
  })
}

// 生成病历摘要（按病历号）
export const generateSummaryByRecordApi = (recordNo) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRecordSummary(recordNo))
  }
  return request({
    url: `/ai/summary/by-record/${recordNo}`,
    method: 'post'
  })
}

// 生成病历摘要（按文本）
export const generateSummaryByTextApi = (text) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRecordSummaryByText(text))
  }
  return request({
    url: '/ai/summary/by-text',
    method: 'post',
    data: { text }
  })
}

// 医生确认/修正病历摘要
export const confirmSummaryApi = (summaryId, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockConfirmSummary(summaryId, data))
  }
  return request({
    url: `/ai/summary/${summaryId}/confirm`,
    method: 'put',
    data
  })
}

// 用药分析
export const medicationAnalysisApi = (recordId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockMedicationAnalysis(recordId))
  }
  return request({
    url: '/ai/medication-analysis',
    method: 'post',
    data: { recordId }
  })
}

// 提交影像异常检测
export const submitImagingDetectionApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockImagingSubmit(data))
  }
  return request({
    url: '/ai/imaging-detection',
    method: 'post',
    data
  })
}

// 查询影像检测结果
export const getImagingResultApi = (taskId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockImagingResult(taskId))
  }
  return request({
    url: `/ai/imaging-detection/${taskId}`,
    method: 'get'
  })
}

// 医生复核影像检测结果
export const reviewImagingDetectionApi = (taskId, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockReviewImagingDetection(taskId, data))
  }
  return request({
    url: `/ai/imaging-detection/${taskId}/review`,
    method: 'put',
    data
  })
}

// 获取影像检测历史记录列表
export const getImagingHistoryListApi = (role) => {
  if (USE_MOCK) {
    return Promise.resolve(mockImagingHistoryList(role))
  }
  return request({
    url: '/ai/imaging-detection/list',
    method: 'get',
    params: { role }
  })
}
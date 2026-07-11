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
// 后端 MedicationAnalysisRequest 需要 prescriptions 列表 + patientContext，
// 前端只传 recordId 时后端会因 @NotEmpty 校验失败（400）。
// 改为接受完整 data 对象，由调用方组装 prescriptions。
export const medicationAnalysisApi = (data) => {
  if (USE_MOCK) {
    // 兼容旧调用：传 recordId 字符串时走 mock
    if (typeof data === 'string') {
      return Promise.resolve(mockMedicationAnalysis(data))
    }
    return Promise.resolve(mockMedicationAnalysis(data.recordId))
  }
  // 兼容旧调用：传 recordId 字符串时包装为最小请求体（无处方则后端返回空风险）
  const payload = typeof data === 'string'
    ? { recordId: data, prescriptions: [{ drugName: 'placeholder' }] }
    : data
  return request({
    url: '/ai/medication-analysis',
    method: 'post',
    data: payload
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
// 后端返回 ImageDetectionResponse {detectionId, status, abnormalDetected, findings, disclaimer}
// 前端期望 taskId → 映射为 detectionId
export const getImagingResultApi = (detectionId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockImagingResult(detectionId))
  }
  return request({
    url: `/ai/imaging-detection/${detectionId}`,
    method: 'get'
  })
}

// 医生复核影像检测结果
// 后端 AiReviewRequest 需要 {reviewedBy, reviewResult, reviewComment}
export const reviewImagingDetectionApi = (detectionId, data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockReviewImagingDetection(detectionId, data))
  }
  return request({
    url: `/ai/imaging-detection/${detectionId}/review`,
    method: 'put',
    data
  })
}

// 获取影像检测历史记录列表
// 后端 GET /ai/imaging-detection/list 接受 patientId 参数（非 role）
export const getImagingHistoryListApi = (patientId) => {
  if (USE_MOCK) {
    return Promise.resolve(mockImagingHistoryList(patientId))
  }
  return request({
    url: '/ai/imaging-detection/list',
    method: 'get',
    params: patientId ? { patientId } : {}
  })
}

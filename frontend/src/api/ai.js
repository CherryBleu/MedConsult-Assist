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
// 后端 MedicalRecordSummaryTextRequest 字段为 recordText（@NotBlank），不是 text
export const generateSummaryByTextApi = (text) => {
  if (USE_MOCK) {
    return Promise.resolve(mockRecordSummaryByText(text))
  }
  return request({
    url: '/ai/summary/by-text',
    method: 'post',
    data: { recordText: text }
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

const normalizeMedicationAnalysisPayload = (data) => {
  if (!data || typeof data === 'string') {
    throw new Error('用药分析需要真实药品明细，不能只传病历编号')
  }
  const prescriptions = Array.isArray(data.prescriptions)
    ? data.prescriptions.filter(item => {
        const drugName = String(item?.drugName || '').trim()
        return drugName && drugName.toLowerCase() !== 'placeholder'
      })
    : []
  if (prescriptions.length === 0) {
    throw new Error('用药分析至少需要 1 条真实药品')
  }
  return { ...data, prescriptions }
}

// 用药分析
// 后端 MedicationAnalysisRequest 需要 prescriptions 列表 + patientContext。
// 调用方必须传真实药品，禁止用 placeholder 绕过 @NotEmpty 后制造“空风险”假成功。
export const medicationAnalysisApi = (data) => {
  const payload = normalizeMedicationAnalysisPayload(data)
  if (USE_MOCK) {
    return Promise.resolve(mockMedicationAnalysis(payload.recordId))
  }
  return request({
    url: '/ai/medication-analysis',
    method: 'post',
    data: payload
  })
}

// 提交影像异常检测
// 公共端 ImageDetectionRequest 使用 {imageType, fileIds: List<String>, patientId?, recordId?}。
// 前端先上传文件获取不透明 fileId；imageUrls 仅供可信服务兼容历史数据。
export const submitImagingDetectionApi = (data) => {
  const payload = { ...data, fileIds: Array.isArray(data?.fileIds) ? data.fileIds : [] }
  delete payload.imageUrls
  if (USE_MOCK) {
    return Promise.resolve(mockImagingSubmit(payload))
  }
  return request({
    url: '/ai/imaging-detection',
    method: 'post',
    data: payload
  })
}

// 上传医学影像文件到 MinIO，后续业务请求仅引用返回的 fileId。
// 后端 POST /api/v1/files/upload (multipart/form-data) → FileUploadResponse {fileUrl, fileId, ...}
export const uploadImageFileApi = (file) => {
  if (USE_MOCK) {
    return Promise.resolve({ code: 0, message: 'success', data: { fileUrl: URL.createObjectURL(file), fileId: 'mock-' + Date.now() } })
  }
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/files/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 查询影像检测结果
// 后端返回检测结果及 reviewStatus/reviewResult/reviewComment/reviewedBy/reviewedAt 复核字段。
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
// reviewedBy 由服务端从 JWT 推导，客户端只提交复核结论和意见。
export const reviewImagingDetectionApi = (detectionId, data) => {
  const payload = {
    reviewResult: data?.reviewResult,
    reviewComment: data?.reviewComment
  }
  if (USE_MOCK) {
    return Promise.resolve(mockReviewImagingDetection(detectionId, payload))
  }
  return request({
    url: `/ai/imaging-detection/${detectionId}/review`,
    method: 'put',
    data: payload
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

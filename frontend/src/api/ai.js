import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import { mockTriageResult, mockCreateSession, mockSendMessage, mockSessionHistory, mockSubmitFeedback } from '@/mock/ai'
import { mockRecordSummary, mockRecordSummaryStream, mockRecordSummaryByText, mockMedicationAnalysis, mockMedicationAnalysisStream, mockImagingSubmit, mockImagingResult, mockConfirmSummary, mockReviewImagingDetection, mockImagingHistoryList } from '@/mock/ai'
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

const parseSsePayload = (raw) => {
  if (!raw || raw === '[DONE]') return null
  try {
    return JSON.parse(raw)
  } catch (e) {
    return raw
  }
}

const parseSseFrame = (frame) => {
  const lines = frame.split(/\r?\n/)
  let eventName = 'message'
  const dataLines = []

  for (const line of lines) {
    if (!line || line.startsWith(':')) continue
    const separatorIndex = line.indexOf(':')
    const field = separatorIndex === -1 ? line : line.slice(0, separatorIndex)
    let value = separatorIndex === -1 ? '' : line.slice(separatorIndex + 1)
    if (value.startsWith(' ')) value = value.slice(1)

    if (field === 'event') eventName = value
    if (field === 'data') dataLines.push(value)
  }

  if (eventName === 'message' && dataLines.length === 0) return null
  return {
    eventName,
    payload: parseSsePayload(dataLines.join('\n'))
  }
}

const readSseResponse = async (response, callbacks = {}) => {
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应读取')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finalResult = null

  const handleFrame = (frame) => {
    const event = parseSseFrame(frame)
    if (!event) return

    const { eventName, payload } = event
    if (eventName === 'start') callbacks.onStart?.(payload)
    if (eventName === 'delta') callbacks.onDelta?.(payload)
    if (eventName === 'result') {
      finalResult = payload
      callbacks.onResult?.(payload)
    }
    if (eventName === 'done') callbacks.onDone?.(payload)
    if (eventName === 'error') {
      callbacks.onError?.(payload)
      throw new Error(payload?.message || 'AI 流式请求失败')
    }
  }

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split(/\r?\n\r?\n/)
      buffer = frames.pop() || ''
      frames.forEach(handleFrame)
    }

    buffer += decoder.decode()
    if (buffer.trim()) handleFrame(buffer)
    return finalResult
  } finally {
    reader.releaseLock()
  }
}

const parseErrorText = (text) => {
  if (!text) return ''
  try {
    const parsed = JSON.parse(text)
    return parsed?.message || text
  } catch (e) {
    return text
  }
}

const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms))

const consumeMockFailOnce = (key) => {
  if (typeof localStorage === 'undefined') return false
  if (localStorage.getItem(key) !== '1') return false
  localStorage.removeItem(key)
  return true
}

const mockTriageStream = async (data, callbacks = {}) => {
  if (consumeMockFailOnce('mock_triage_stream_fail_once')) {
    await delay(120)
    const error = new Error('AI 分诊流服务暂时不可用，请稍后重试')
    callbacks.onError?.({ status: 'FAILED', message: error.message })
    throw error
  }

  const result = mockTriageResult(data.symptoms).data
  callbacks.onStart?.({ status: 'PROCESSING' })
  for (const item of result.recommendations || []) {
    await delay(180)
    callbacks.onDelta?.(item)
  }
  await delay(120)
  callbacks.onResult?.(result)
  callbacks.onDone?.({ status: 'COMPLETED' })

  return {
    code: 0,
    message: 'success',
    data: result
  }
}

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

// 智能分诊（SSE 流式）
export const triageStreamApi = async (data, callbacks = {}) => {
  if (USE_MOCK) {
    return mockTriageStream(data, callbacks)
  }

  const token = getToken()
  const headers = {
    Accept: 'text/event-stream',
    'Content-Type': 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/v1/ai/triage/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify(data)
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(parseErrorText(errorText) || `AI 流式请求失败（HTTP ${response.status}）`)
  }

  const streamData = await readSseResponse(response, callbacks)
  return {
    code: 0,
    message: 'success',
    data: streamData
  }
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
    const res = mockRecordSummary(recordNo)
    return res.code === 0 ? Promise.resolve(res) : Promise.reject(new Error(res.message))
  }
  return request({
    url: `/ai/summary/by-record/${recordNo}`,
    method: 'post'
  })
}

// 生成病历摘要（按病历号，SSE 流式）
// 后端流式接口是 POST + text/event-stream，浏览器端用 fetch + ReadableStream 消费命名 SSE 事件。
export const generateSummaryByRecordStreamApi = async (recordNo, callbacks = {}) => {
  if (USE_MOCK) {
    return mockRecordSummaryStream(recordNo, callbacks)
  }

  const token = getToken()
  const headers = {
    Accept: 'text/event-stream',
    'Content-Type': 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/v1/ai/summary/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      recordId: recordNo,
      summaryType: 'STRUCTURED',
      saveDraft: false
    })
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(parseErrorText(errorText) || `AI 流式请求失败（HTTP ${response.status}）`)
  }

  const data = await readSseResponse(response, callbacks)
  return {
    code: 0,
    message: 'success',
    data
  }
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
    const res = mockMedicationAnalysis(payload.recordId)
    return res.code === 0 ? Promise.resolve(res) : Promise.reject(new Error(res.message))
  }
  return request({
    url: '/ai/medication-analysis',
    method: 'post',
    data: payload
  })
}

// 用药分析（SSE 流式）
// 后端流式接口是 POST + text/event-stream，路径对齐网关实际 /api/v1/ai/medication-analysis/stream。
export const medicationAnalysisStreamApi = async (data, callbacks = {}) => {
  const payload = normalizeMedicationAnalysisPayload(data)
  if (USE_MOCK) {
    return mockMedicationAnalysisStream(payload, callbacks)
  }

  const token = getToken()
  const headers = {
    Accept: 'text/event-stream',
    'Content-Type': 'application/json'
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/v1/ai/medication-analysis/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(parseErrorText(errorText) || `AI 流式请求失败（HTTP ${response.status}）`)
  }

  const streamData = await readSseResponse(response, callbacks)
  return {
    code: 0,
    message: 'success',
    data: streamData
  }
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

// 提交 AI 结果反馈（patient/doctor 端调用，对齐后端 POST /ai/feedback）
// body: { aiResultType, aiResultId, useful, adopted?, comment? }
// feedbackBy 由后端从 JWT 取，前端不传
export const submitFeedbackApi = (data) => {
  if (USE_MOCK) {
    return Promise.resolve(mockSubmitFeedback(data))
  }
  return request({
    url: '/ai/feedback',
    method: 'post',
    data
  })
}


// 生成病历摘要
const consumeFailOnce = (key) => {
  if (typeof localStorage === 'undefined') return false
  if (localStorage.getItem(key) !== '1') return false
  localStorage.removeItem(key)
  return true
}

const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms))

export const mockRecordSummary = (recordId) => {
  // 失败一次的开关：保留原 mock_summary_stream_fail_once 以便旧测试兼容
  if (consumeFailOnce('mock_summary_stream_fail_once')) {
    return {
      code: 500,
      message: 'AI 摘要服务暂时不可用，请稍后重试',
      data: null
    }
  }
  return {
    code: 0,
    message: 'success',
    data: {
      summaryId: 'SUM' + Date.now(),
      summaryNo: 'SUM' + Date.now(),
      summaryType: 'STRUCTURED',
      summaryContent: {
        chiefComplaint: '咳嗽、咳痰3天，伴低热',
        diagnosis: '急性支气管炎',
        medications: ['阿莫西林胶囊', '氨溴索口服溶液'],
        treatmentPlan: '抗感染、化痰治疗，注意休息',
        followUpAdvice: '注意休息，多饮水，3天后复诊',
        medication: '阿莫西林胶囊、氨溴索口服溶液',
        advice: '注意休息，多饮水，3天后复诊'
      },
      modelName: 'medical-summary-v1.0',
      createdAt: new Date().toLocaleString()
    }
  }
}

export const mockRecordSummaryStream = async (recordId, handlers = {}) => {
  if (consumeFailOnce('mock_summary_stream_fail_once')) {
    await delay(120)
    const error = new Error('AI 摘要流服务暂时不可用，请稍后重试')
    handlers.onError?.({ status: 'FAILED', message: error.message })
    throw error
  }

  const result = mockRecordSummary(recordId).data
  const tokens = [
    '咳嗽、咳痰3天，伴低热',
    '；初步诊断为急性支气管炎',
    '；建议抗感染、化痰治疗并按时复诊'
  ]

  handlers.onStart?.({ status: 'PROCESSING' })
  for (const token of tokens) {
    await delay(120)
    handlers.onDelta?.({ token })
  }
  await delay(80)
  handlers.onResult?.(result)
  handlers.onDone?.({ status: 'COMPLETED' })

  return {
    code: 0,
    message: 'success',
    data: result
  }
}

// 按文本生成病历摘要
export const mockRecordSummaryByText = (text) => {
  return {
    code: 0,
    message: 'success',
    data: {
      summaryId: 'SUM' + Date.now(),
      summaryNo: 'SUM' + Date.now(),
      summaryType: 'STRUCTURED',
      summaryContent: {
        chiefComplaint: text?.substring(0, 50) || '咳嗽、咳痰3天，伴低热',
        diagnosis: '急性支气管炎',
        medication: '阿莫西林胶囊、氨溴索口服溶液',
        advice: '注意休息，多饮水，3天后复诊，不适随诊'
      },
      modelName: 'medical-summary-v1.0',
      createdAt: new Date().toLocaleString()
    }
  }
}

// 确认/修正病历摘要
export const mockConfirmSummary = (summaryId, data) => {
  return {
    code: 0,
    message: '确认成功',
    data: {
      summaryId,
      status: 'CONFIRMED',
      ...data
    }
  }
}

const imagingTasks = {}

export const mockImagingSubmit = (data) => {
  const taskId = 'IMG' + Date.now()
  imagingTasks[taskId] = {
    taskId,
    taskNo: taskId,
    status: 'PENDING',
    imagingType: data.imageType || 'CT',
    bodyPart: data.bodyPart || '胸部',
    fileIds: Array.isArray(data.fileIds) ? data.fileIds : [],
    createdAt: new Date().toLocaleString(),
    pollCount: 0
  }
  return {
    code: 0,
    message: '提交成功',
    data: {
      taskId,
      status: 'PENDING'
    }
  }
}

export const mockImagingResult = (taskId) => {
  let task = imagingTasks[taskId]
  if (!task) {
    task = {
      taskId,
      taskNo: taskId,
      status: 'COMPLETED',
      imagingType: 'CT',
      bodyPart: '胸部',
      imageUrl: '',
      createdAt: new Date().toLocaleString(),
      pollCount: 10
    }
    imagingTasks[taskId] = task
  }

  task.pollCount = (task.pollCount || 0) + 1

  if (task.pollCount <= 1) {
    task.status = 'PENDING'
  } else if (task.pollCount <= 3) {
    task.status = 'PROCESSING'
  } else {
    task.status = 'COMPLETED'
  }

  if (task.status === 'COMPLETED' && !task.hasOwnProperty('hasAbnormal')) {
    const hasAbnormal = Math.random() > 0.4
    task.hasAbnormal = hasAbnormal
    task.confidence = hasAbnormal ? Math.floor(85 + Math.random() * 12) : Math.floor(92 + Math.random() * 7)
    task.modelName = 'imaging-ct-v2.0'
    task.completedAt = new Date().toLocaleString()

    if (hasAbnormal) {
      task.aiFindings = '右肺上叶可见一类圆形高密度结节影，大小约12mm×10mm，边缘可见分叶征及毛刺征，周围可见胸膜牵拉征。余肺野清晰，未见明显异常密度影。纵隔居中，心影大小形态正常，双侧肋膈角锐利。'
      task.aiDiagnosis = '右肺上叶结节，考虑周围型肺癌可能性大，建议进一步增强CT或穿刺活检明确诊断。'
      task.regions = [
        { x: 180, y: 120, width: 60, height: 50, label: '结节影', confidence: 92 },
        { x: 200, y: 140, width: 25, height: 20, label: '毛刺征', confidence: 87 }
      ]
      task.suggestions = [
        '建议尽快行胸部增强CT检查',
        '建议肿瘤标志物检测',
        '必要时CT引导下穿刺活检',
        '请胸外科会诊评估手术指征'
      ]
    } else {
      task.aiFindings = '双肺纹理清晰，走行自然，未见明显异常密度影。气管及主支气管通畅，纵隔居中，心影大小形态正常，双侧胸膜无增厚，肋膈角锐利。'
      task.aiDiagnosis = '胸部CT平扫未见明显异常。'
      task.regions = []
      task.suggestions = [
        '目前影像学检查未见明显异常',
        '建议每年定期体检复查',
        '如有不适请及时就诊'
      ]
    }
    task.reviewStatus = 'PENDING'
  }

  return {
    code: 0,
    message: 'success',
    data: {
      taskId: task.taskId,
      taskNo: task.taskNo,
      status: task.status,
      imagingType: task.imagingType,
      bodyPart: task.bodyPart,
      imageUrl: task.imageUrl,
      hasAbnormal: task.hasAbnormal,
      confidence: task.confidence,
      modelName: task.modelName,
      aiFindings: task.aiFindings,
      aiDiagnosis: task.aiDiagnosis,
      regions: task.regions,
      suggestions: task.suggestions,
      reviewStatus: task.reviewStatus,
      reviewResult: task.reviewResult,
      reviewComment: task.reviewComment,
      reviewedBy: task.reviewedBy,
      reviewedAt: task.reviewedAt,
      createdAt: task.createdAt,
      completedAt: task.completedAt
    }
  }
}

export const mockReviewImagingDetection = (taskId, data) => {
  const task = imagingTasks[taskId]
  if (task) {
    task.reviewStatus = 'REVIEWED'
    task.reviewResult = data.reviewResult
    task.reviewComment = data.reviewComment
    task.reviewedBy = 900
    task.reviewedAt = new Date().toISOString()
  }
  return {
    code: 0,
    message: '复核成功',
    data: task
      ? {
          detectionId: taskId,
          reviewStatus: task.reviewStatus,
          reviewResult: task.reviewResult,
          reviewComment: task.reviewComment,
          reviewedBy: task.reviewedBy,
          reviewedAt: task.reviewedAt
        }
      : null
  }
}

export const mockImagingHistoryList = (role = 'patient') => {
  const baseList = [
    {
      taskId: 'IMG1720500000001',
      taskNo: 'IMG1720500000001',
      imagingType: 'CT',
      bodyPart: '胸部',
      status: 'COMPLETED',
      hasAbnormal: true,
      confidence: 92,
      reviewStatus: role === 'doctor' ? 'PENDING' : 'REVIEWED',
      patientName: '李患者',
      createdAt: '2026-07-09 14:30:00',
      completedAt: '2026-07-09 14:35:22'
    },
    {
      taskId: 'IMG1720400000002',
      taskNo: 'IMG1720400000002',
      imagingType: 'X线',
      bodyPart: '胸部',
      status: 'COMPLETED',
      hasAbnormal: false,
      confidence: 96,
      reviewStatus: 'REVIEWED',
      patientName: '王患者',
      createdAt: '2026-07-08 10:15:00',
      completedAt: '2026-07-08 10:18:45'
    },
    {
      taskId: 'IMG1720300000003',
      taskNo: 'IMG1720300000003',
      imagingType: 'MRI',
      bodyPart: '头颅',
      status: 'COMPLETED',
      hasAbnormal: true,
      confidence: 88,
      reviewStatus: 'REVIEWED',
      patientName: '赵患者',
      createdAt: '2026-07-07 16:20:00',
      completedAt: '2026-07-07 16:28:10'
    },
    {
      taskId: 'IMG1720200000004',
      taskNo: 'IMG1720200000004',
      imagingType: 'CT',
      bodyPart: '腹部',
      status: 'COMPLETED',
      hasAbnormal: false,
      confidence: 94,
      reviewStatus: 'PENDING',
      patientName: '刘患者',
      createdAt: '2026-07-06 09:00:00',
      completedAt: '2026-07-06 09:06:33'
    }
  ]
  return {
    code: 0,
    message: 'success',
    data: baseList,
    total: baseList.length
  }
}

// 用药分析
export const mockMedicationAnalysis = (recordId) => {
  // 失败一次开关：保留 mock_medication_analysis_fail_once 与旧测试兼容
  if (consumeFailOnce('mock_medication_analysis_fail_once')) {
    return {
      code: 500,
      message: '用药分析请求失败，请重试',
      data: null
    }
  }
  return {
    code: 0,
    message: 'success',
    data: {
      analysisNo: 'ANA' + Date.now(),
      overallRiskLevel: 'LOW',
      allergyRisks: [],
      contraindicationRisks: [],
      interactionRisks: [
        { drugA: '阿莫西林', drugB: '氨溴索', level: 'LOW', desc: '无明显相互作用，可联合使用' }
      ],
      reminders: [
        '阿莫西林建议饭后服用，减少胃肠道刺激',
        '服药期间禁止饮酒',
        '氨溴索建议多饮水，利于痰液稀释排出'
      ],
      modelName: 'medication-check-v1.0'
    }
  }
}

export const mockMedicationAnalysisStream = async (payload, handlers = {}) => {
  if (consumeFailOnce('mock_medication_analysis_stream_fail_once')) {
    await delay(120)
    const error = new Error('用药分析流式服务暂时不可用，请稍后重试')
    handlers.onError?.({ status: 'FAILED', message: error.message })
    throw error
  }

  const result = mockMedicationAnalysis(payload.recordId).data
  const drugNames = (payload.prescriptions || [])
    .map(item => item.drugName)
    .filter(Boolean)
    .join('、') || '已选药品'
  const tokens = [
    `已接收 ${drugNames}，正在校验禁忌风险。`,
    '阿莫西林风险校验完成，正在分析药物相互作用。',
    '用药提醒生成中：饭后服用、避免饮酒。'
  ]

  handlers.onStart?.({ status: 'PROCESSING', stage: '正在连接 AI 用药分析服务' })
  for (const token of tokens) {
    await delay(120)
    handlers.onDelta?.({ token })
  }
  await delay(80)
  handlers.onResult?.(result)
  handlers.onDone?.({ status: 'COMPLETED' })

  return {
    code: 0,
    message: 'success',
    data: result
  }
}


// 智能分诊结果（对齐 ai_triage_result 表字段）
export const mockTriageResult = (symptoms) => {
  return {
    code: 0,
    message: 'success',
    data: {
      triageNo: 'TRI' + Date.now(),
      symptoms: symptoms,
      duration: '3天',
      severity: 'MEDIUM',
      recommendations: [
        { departmentId: 2, departmentName: '呼吸内科', confidence: 0.85, reason: '咳嗽、咳痰、低热为呼吸系统典型症状' },
        { departmentId: 1, departmentName: '心血管内科', confidence: 0.40, reason: '胸闷症状需排除心血管疾病' }
      ],
      emergencyRecommended: 0,
      riskLevel: 'LOW',
      citations: ['急性支气管炎', '上呼吸道感染'],
      modelName: 'medical-triage-v1.0',
      createdAt: new Date().toLocaleString()
    }
  }
}

// 创建AI会话
export const mockCreateSession = () => {
  return {
    code: 0,
    message: 'success',
    data: {
      sessionId: 'SES' + Date.now(),
      title: '新的问诊会话',
      status: 'ACTIVE',
      createdAt: new Date().toLocaleString()
    }
  }
}

// 发送消息获取AI回答（对齐 ai_chat_message 表字段）
export const mockSendMessage = (sessionId, userMessage) => {
  if (consumeFailOnce('mock_symptom_chat_fail_once')) {
    return Promise.reject(new Error('AI 问诊服务暂时不可用，请稍后重试'))
  }

  const answers = {
    '咳嗽有痰怎么办': '根据您的症状，初步考虑为呼吸道感染。建议：1. 多饮温水，保持呼吸道湿润；2. 注意休息，避免劳累；3. 若症状持续超过3天或出现高热、胸痛，请及时到呼吸内科就诊；4. 可暂时服用化痰药物缓解症状。以上建议仅供参考，具体诊疗请遵医嘱。',
    '胸痛大汗怎么办': '胸痛伴大汗属于需要优先排除急性心血管事件的高风险表现。建议立即停止活动，尽快前往急诊科或拨打 120，由医生完成心电图、心肌酶和生命体征评估。AI 只能提供分诊提醒，不能替代急诊判断。',
    '胸闷是什么原因': '胸闷的常见原因包括：1. 心血管疾病：冠心病、心绞痛、心律失常等；2. 呼吸系统疾病：支气管炎、哮喘、气胸等；3. 情绪因素：焦虑、紧张也可能引发胸闷。若胸闷频繁发作或伴随胸痛、大汗，请立即就医。',
    'default': '已收到您的症状描述，正在为您分析。建议您补充以下信息：症状持续时间、是否有发热、是否有既往病史。温馨提示：AI分析仅供参考，不能替代医生诊断，如有不适请及时就医。'
  }

  const reply = answers[userMessage] || answers['default']
  const highRisk = userMessage === '胸痛大汗怎么办'
  
  return {
    code: 0,
    message: 'success',
    data: {
      id: Date.now(),
      sessionId,
      userMessage,
      answer: reply,
      aiAnswer: reply,
      answerSource: 'VECTOR_SEARCH_AND_RULE',
      possibleCauses: highRisk ? ['急性冠脉综合征', '心绞痛', '急性心肌梗死'] : ['急性支气管炎', '上呼吸道感染'],
      suggestedDepartments: highRisk ? ['急诊科', '心血管内科'] : ['呼吸内科'],
      riskLevel: highRisk ? 'HIGH' : 'LOW',
      emergencyAdvice: highRisk ? 1 : 0,
      vectorMatches: highRisk ? [
        {
          vectorId: 'vec_mock_chest_001',
          score: 0.91,
          sourceId: 'DISEASE_JSON:急性冠脉综合征',
          diseaseName: '急性冠脉综合征',
          fieldName: 'symptom',
          chunkText: '胸痛、出汗、胸闷等表现需优先排除急性冠脉综合征。'
        },
        {
          vectorId: 'vec_mock_chest_002',
          score: 0.86,
          sourceId: 'DISEASE_JSON:心绞痛',
          diseaseName: '心绞痛',
          fieldName: 'check',
          chunkText: '如伴胸痛大汗，建议尽快完成心电图、心肌酶和生命体征评估。'
        }
      ] : [
        {
          vectorId: 'vec_mock_001',
          score: 0.89,
          sourceId: 'DISEASE_JSON:急性支气管炎',
          diseaseName: '急性支气管炎',
          fieldName: 'symptom',
          chunkText: '症状包含咳嗽、咳痰，可伴随发热、咽痛等表现。'
        },
        {
          vectorId: 'vec_mock_002',
          score: 0.82,
          sourceId: 'DISEASE_JSON:上呼吸道感染',
          diseaseName: '上呼吸道感染',
          fieldName: 'prevent',
          chunkText: '建议注意休息、多饮温水，并避免受凉和持续熬夜。'
        }
      ],
      citations: highRisk ? [
        {
          sourceId: 'DISEASE_JSON:急性冠脉综合征',
          diseaseName: '急性冠脉综合征',
          matchedFields: ['symptom', 'check', 'cure_department'],
          snippet: '胸痛伴大汗需优先排除急性冠脉综合征；建议急诊科及时评估并尽快完成相关检查。',
          score: 0.91
        }
      ] : [
        {
          sourceId: 'DISEASE_JSON:急性支气管炎',
          diseaseName: '急性支气管炎',
          matchedFields: ['symptom', 'prevent', 'cure_department'],
          snippet: '症状包含咳嗽、咳痰，可伴随发热、咽痛等表现；日常可先注意休息、多饮水，并按需前往呼吸内科。',
          score: 0.89
        }
      ],
      createdAt: new Date().toLocaleString()
    }
  }
}

// 会话历史
export const mockSessionHistory = (sessionId) => {
  return {
    code: 0,
    message: 'success',
    data: [
      {
        id: 1,
        role: 'user',
        content: '医生你好，我最近咳嗽还有痰'
      },
      {
        id: 2,
        role: 'ai',
        content: '您好，请问您的症状持续多久了？有没有发热、咽痛、胸痛等其他不适？'
      }
    ]
  }
}

// AI调用日志
export const mockAiCallLog = () => {
  if (consumeFailOnce('mock_ai_call_log_fail_once')) {
    return Promise.reject(new Error('AI调用日志加载失败，请重试'))
  }
  return {
    code: 0,
    message: 'success',
    data: [
      {
        id: 1,
        logNo: 'LOG' + Date.now(),
        serviceType: '病历摘要',
        modelName: 'medical-summary-v1.0',
        userId: 1001,
        userName: '测试患者',
        inputLength: 28,
        outputLength: 156,
        costTime: 12,
        status: 'SUCCESS',
        callerService: 'medical-record-service',
        traceId: 'trace-cache-001',
        requestId: 'REQ-cache-001',
        cacheHit: true,
        promptTokens: 0,
        completionTokens: 0,
        totalTokens: 0,
        costTokens: 0,
        estimatedCostYuan: '0.000000',
        createdAt: '2026-07-09 10:20:30'
      },
      {
        id: 2,
        logNo: 'LOG' + (Date.now() - 1000),
        serviceType: 'AI问诊',
        modelName: 'medical-chat-v1.2',
        userId: 1001,
        userName: '测试患者',
        inputLength: 15,
        outputLength: 210,
        costTime: 350,
        status: 'SUCCESS',
        callerService: 'frontend',
        traceId: 'trace-chat-002',
        requestId: 'REQ-chat-002',
        cacheHit: false,
        promptTokens: 4,
        completionTokens: 53,
        totalTokens: 57,
        costTokens: 57,
        estimatedCostYuan: '0.000000',
        createdAt: '2026-07-09 10:25:12'
      },
      {
        id: 3,
        logNo: 'LOG' + (Date.now() - 2000),
        serviceType: '病历摘要',
        modelName: 'medical-summary-v1.0',
        userId: 1,
        userName: '张明',
        inputLength: 320,
        outputLength: 86,
        costTime: 200,
        status: 'SUCCESS',
        callerService: 'medical-record-service',
        traceId: 'trace-summary-003',
        requestId: 'REQ-summary-003',
        cacheHit: false,
        promptTokens: 80,
        completionTokens: 22,
        totalTokens: 102,
        costTokens: 102,
        estimatedCostYuan: '0.000204',
        createdAt: '2026-07-09 09:15:00'
      },
      {
        id: 4,
        logNo: 'LOG' + (Date.now() - 3000),
        serviceType: '用药分析',
        modelName: 'medication-check-v1.0',
        userId: 1,
        userName: '张明',
        inputLength: 120,
        outputLength: 280,
        costTime: 180,
        status: 'SUCCESS',
        callerService: 'doctor-service',
        traceId: 'trace-medication-004',
        requestId: 'REQ-medication-004',
        cacheHit: false,
        promptTokens: 30,
        completionTokens: 70,
        totalTokens: 100,
        costTokens: 100,
        estimatedCostYuan: '0.000200',
        createdAt: '2026-07-09 09:20:45'
      },
      {
        id: 5,
        logNo: 'LOG' + (Date.now() - 4000),
        serviceType: '智能分诊',
        modelName: 'medical-triage-v1.0',
        userId: 1002,
        userName: '李患者',
        inputLength: 22,
        outputLength: 0,
        costTime: 5000,
        status: 'FAILED',
        callerService: 'frontend',
        traceId: 'trace-triage-005',
        requestId: 'REQ-triage-005',
        cacheHit: false,
        promptTokens: 6,
        completionTokens: 0,
        totalTokens: 6,
        costTokens: 6,
        estimatedCostYuan: '0.000000',
        createdAt: '2026-07-08 16:30:00'
      }
    ],
    total: 5
  }
}

// AI反馈列表
export const mockAiFeedbackList = () => {
  if (consumeFailOnce('mock_ai_feedback_fail_once')) {
    return Promise.reject(new Error('AI反馈列表加载失败，请重试'))
  }
  return {
    code: 0,
    message: 'success',
    data: [
      { id: 1, feedbackNo: 'FB20260709001', serviceType: '智能分诊', userId: 1001, userName: '测试患者', rating: 4, content: '分诊结果比较准确，推荐的科室符合预期', status: 'PENDING', createdAt: '2026-07-09 10:30:00' },
      { id: 2, feedbackNo: 'FB20260708002', serviceType: 'AI问诊', userId: 1002, userName: '李患者', rating: 2, content: '回答太笼统，没有实际帮助', status: 'PROCESSED', processedBy: '管理员', processedAt: '2026-07-08 17:00:00', reply: '感谢反馈，已同步优化模型', createdAt: '2026-07-08 15:20:00' },
      { id: 3, feedbackNo: 'FB20260707003', serviceType: '病历摘要', userId: 1, userName: '张明', rating: 5, content: '摘要提取很精准，节省了很多时间', status: 'PENDING', createdAt: '2026-07-07 14:10:00' }
    ],
    total: 3
  }
}

// 处理反馈
export const mockProcessFeedback = (id, reply) => {
  return {
    code: 0,
    message: '处理成功',
    data: { id, status: 'PROCESSED', reply }
  }
}

// 提交 AI 结果反馈（patient 端 AiConsult 调用，对齐后端 POST /ai/feedback）
// body 字段：{ aiResultType, aiResultId, rating(1-5), comment? }
export const mockSubmitFeedback = (data) => {
  return {
    code: 0,
    message: 'success',
    data: {
      feedbackId: 'FB' + Date.now(),
      aiResultId: data?.aiResultId || ''
    }
  }
}

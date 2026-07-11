
// 生成病历摘要
export const mockRecordSummary = (recordId) => {
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
        medication: '阿莫西林胶囊、氨溴索口服溶液',
        advice: '注意休息，多饮水，3天后复诊'
      },
      modelName: 'medical-summary-v1.0',
      createdAt: new Date().toLocaleString()
    }
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
    imagingType: data.imagingType || 'CT',
    bodyPart: data.bodyPart || '胸部',
    imageUrl: data.imageUrl || '',
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
      doctorOpinion: task.doctorOpinion,
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
    task.doctorOpinion = data.doctorOpinion
    task.reviewedBy = '张医生'
    task.reviewedAt = new Date().toLocaleString()
    if (data.correctedRegions) {
      task.regions = data.correctedRegions
    }
    if (data.correctedDiagnosis) {
      task.aiDiagnosis = data.correctedDiagnosis
    }
  }
  return {
    code: 0,
    message: '复核成功',
    data: null
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
        { departmentId: 2, departmentName: '呼吸内科', confidence: 85, reason: '咳嗽、咳痰、低热为呼吸系统典型症状' },
        { departmentId: 1, departmentName: '心血管内科', confidence: 40, reason: '胸闷症状需排除心血管疾病' }
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
  const answers = {
    '咳嗽有痰怎么办': '根据您的症状，初步考虑为呼吸道感染。建议：1. 多饮温水，保持呼吸道湿润；2. 注意休息，避免劳累；3. 若症状持续超过3天或出现高热、胸痛，请及时到呼吸内科就诊；4. 可暂时服用化痰药物缓解症状。以上建议仅供参考，具体诊疗请遵医嘱。',
    '胸闷是什么原因': '胸闷的常见原因包括：1. 心血管疾病：冠心病、心绞痛、心律失常等；2. 呼吸系统疾病：支气管炎、哮喘、气胸等；3. 情绪因素：焦虑、紧张也可能引发胸闷。若胸闷频繁发作或伴随胸痛、大汗，请立即就医。',
    'default': '已收到您的症状描述，正在为您分析。建议您补充以下信息：症状持续时间、是否有发热、是否有既往病史。温馨提示：AI分析仅供参考，不能替代医生诊断，如有不适请及时就医。'
  }

  const reply = answers[userMessage] || answers['default']
  
  return {
    code: 0,
    message: 'success',
    data: {
      id: Date.now(),
      sessionId,
      userMessage,
      aiAnswer: reply,
      suggestedDepartments: ['呼吸内科'],
      riskLevel: 'LOW',
      emergencyAdvice: 0,
      citations: ['急性支气管炎', '上呼吸道感染'],
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
  return {
    code: 0,
    message: 'success',
    data: [
      { id: 1, logNo: 'LOG' + Date.now(), serviceType: '智能分诊', modelName: 'medical-triage-v1.0', userId: 1001, userName: '测试患者', inputLength: 28, outputLength: 156, costTime: 120, status: 'SUCCESS', createdAt: '2026-07-09 10:20:30' },
      { id: 2, logNo: 'LOG' + (Date.now() - 1000), serviceType: 'AI问诊', modelName: 'medical-chat-v1.2', userId: 1001, userName: '测试患者', inputLength: 15, outputLength: 210, costTime: 350, status: 'SUCCESS', createdAt: '2026-07-09 10:25:12' },
      { id: 3, logNo: 'LOG' + (Date.now() - 2000), serviceType: '病历摘要', modelName: 'medical-summary-v1.0', userId: 1, userName: '张明', inputLength: 320, outputLength: 86, costTime: 200, status: 'SUCCESS', createdAt: '2026-07-09 09:15:00' },
      { id: 4, logNo: 'LOG' + (Date.now() - 3000), serviceType: '用药分析', modelName: 'medication-check-v1.0', userId: 1, userName: '张明', inputLength: 120, outputLength: 280, costTime: 180, status: 'SUCCESS', createdAt: '2026-07-09 09:20:45' },
      { id: 5, logNo: 'LOG' + (Date.now() - 4000), serviceType: '智能分诊', modelName: 'medical-triage-v1.0', userId: 1002, userName: '李患者', inputLength: 22, outputLength: 0, costTime: 5000, status: 'FAILED', createdAt: '2026-07-08 16:30:00' }
    ],
    total: 5
  }
}

// AI反馈列表
export const mockAiFeedbackList = () => {
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
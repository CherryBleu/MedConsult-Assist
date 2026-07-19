import dayjs from 'dayjs'

const consumeFailOnce = (key) => {
  if (typeof localStorage === 'undefined') return false
  if (localStorage.getItem(key) !== '1') return false
  localStorage.removeItem(key)
  return true
}



// 创建病历（草稿）
export const mockCreateRecord = (data) => {
  return {
    code: 0,
    message: '病历已创建',
    data: {
      id: Date.now(),
      recordNo: 'MR' + Date.now(),
      ...data,
      status: 'DRAFT',
      createdAt: dayjs().format('YYYY-MM-DD HH:mm:ss')
    }
  }
}

// 更新病历
export const mockUpdateRecord = (id, data) => {
  return {
    code: 0,
    message: '病历已更新',
    data: { id, ...data }
  }
}

// 归档病历
export const mockArchiveRecord = (id) => {
  return {
    code: 0,
    message: '病历已归档',
    data: { 
      id, 
      status: 'ARCHIVED',
      archivedAt: dayjs().format('YYYY-MM-DD HH:mm:ss')
    }
  }
}

// 我的病历列表（对齐 medical_record 表字段）
export const mockRecordList = () => {
  if (consumeFailOnce('mock_patient_record_list_fail_once') || consumeFailOnce('mock_doctor_record_list_fail_once')) {
    return Promise.reject(new Error('病历列表加载失败，请重试'))
  }
  return {
    code: 0,
    message: 'success',
    data: [
      {
        id: 1,
        recordNo: 'MR20260708001',
        patientId: 1001,
        doctorId: 2,
        doctorName: '李华',
        deptName: '呼吸内科',
        appointmentId: 2,
        chiefComplaint: '咳嗽、咳痰3天，伴低热',
        presentIllness: '患者3天前受凉后出现咳嗽，咳黄色黏痰，伴低热，体温最高37.8℃，无胸痛、咯血。',
        initialDiagnosis: '急性支气管炎',
        status: 'ARCHIVED',
        createdAt: '2026-07-08 15:30:00',
        archivedAt: '2026-07-08 16:10:00'
      },
      {
        id: 2,
        recordNo: 'MR20260615002',
        patientId: 1001,
        doctorId: 1,
        doctorName: '张明',
        deptName: '心血管内科',
        appointmentId: 5,
        chiefComplaint: '反复胸闷1月，加重3天',
        presentIllness: '患者1月前劳累后出现胸闷，休息后可缓解，3天前胸闷加重，伴心悸。',
        initialDiagnosis: '冠心病 心绞痛',
        status: 'ARCHIVED',
        createdAt: '2026-06-15 09:20:00',
        archivedAt: '2026-06-15 10:05:00'
      },
      {
        id: 3,
        recordNo: 'MR20260520003',
        patientId: 1001,
        doctorId: 4,
        doctorName: '王强',
        deptName: '骨科',
        appointmentId: 8,
        chiefComplaint: '腰椎疼痛1周',
        presentIllness: '患者1周前搬重物后出现腰椎疼痛，活动受限，无下肢麻木。',
        initialDiagnosis: '急性腰肌劳损',
        status: 'ARCHIVED',
        createdAt: '2026-05-20 14:00:00',
        archivedAt: '2026-05-20 14:40:00'
      }
    ]
  }
}

// 病历详情
export const mockRecordDetail = (id) => {
  const list = mockRecordList().data
  const detail = list.find(i => i.id === Number(id))
  return {
    code: 0,
    message: 'success',
    data: {
      ...detail,
      pastHistory: '既往体健，无高血压、糖尿病病史',
      physicalExam: '双肺呼吸音粗，未闻及干湿性啰音，心率78次/分，律齐',
      finalDiagnosis: '急性支气管炎',
      prescriptions: [
        { name: '阿莫西林胶囊', specification: '0.5g*24粒', dosage: '0.5g 每日3次', quantity: 2 },
        { name: '氨溴索口服溶液', specification: '100ml', dosage: '10ml 每日3次', quantity: 1 }
      ],
      doctorAdvice: '注意休息，多饮水，清淡饮食，3天后复诊，症状加重及时就诊。'
    }
  }
}

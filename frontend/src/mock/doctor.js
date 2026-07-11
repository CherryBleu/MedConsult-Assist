import dayjs from 'dayjs'
export const mockDoctorList = (deptId) => {
  const list = [
    { id: 1, doctorNo: 'D10001', name: '张明', departmentId: 1, deptName: '心血管内科', title: '主任医师', specialties: '冠心病、高血压、心力衰竭', introduction: '从事心血管内科临床工作20年，擅长冠心病介入治疗。', registrationFee: 50, enabled: 1 },
    { id: 2, doctorNo: 'D10002', name: '刘建国', departmentId: 1, deptName: '心血管内科', title: '副主任医师', specialties: '心律失常、心肌病、先天性心脏病', introduction: '擅长心律失常的射频消融治疗，临床经验丰富。', registrationFee: 35, enabled: 1 },
    { id: 3, doctorNo: 'D10003', name: '赵雪', departmentId: 1, deptName: '心血管内科', title: '主治医师', specialties: '高血压、高血脂、冠心病预防', introduction: '专注于心血管疾病的早期筛查与预防。', registrationFee: 20, enabled: 1 },
    { id: 4, doctorNo: 'D10004', name: '李华', departmentId: 2, deptName: '呼吸内科', title: '主任医师', specialties: '哮喘、慢阻肺、肺部感染', introduction: '呼吸内科专家，擅长呼吸系统疑难病症诊治。', registrationFee: 50, enabled: 1 },
    { id: 5, doctorNo: 'D10005', name: '陈静', departmentId: 2, deptName: '呼吸内科', title: '副主任医师', specialties: '慢性咳嗽、支气管镜', introduction: '擅长慢性咳嗽诊治及支气管镜检查。', registrationFee: 35, enabled: 1 },
    { id: 6, doctorNo: 'D10006', name: '王强', departmentId: 3, deptName: '消化内科', title: '主任医师', specialties: '胃肠疾病、肝病、内镜治疗', introduction: '消化内科专家，从事消化系疾病诊治25年。', registrationFee: 50, enabled: 1 },
    { id: 7, doctorNo: 'D10007', name: '刘洋', departmentId: 4, deptName: '骨科', title: '副主任医师', specialties: '骨折、关节置换、脊柱疾病', introduction: '擅长创伤骨折及关节置换手术。', registrationFee: 40, enabled: 1 },
    { id: 8, doctorNo: 'D10008', name: '王芳', departmentId: 5, deptName: '儿科', title: '主任医师', specialties: '小儿呼吸系统、消化系统疾病', introduction: '儿科专家，具有丰富的儿童疾病诊疗经验。', registrationFee: 30, enabled: 1 },
    { id: 9, doctorNo: 'D10009', name: '周明', departmentId: 6, deptName: '皮肤科', title: '副主任医师', specialties: '皮炎、湿疹、痤疮、银屑病', introduction: '擅长各种皮肤病及性病诊治。', registrationFee: 35, enabled: 1 }
  ]
  return {
    code: 0,
    message: 'success',
    data: deptId ? list.filter(i => i.departmentId === Number(deptId)) : list
  }
}

// 医生接诊列表（对齐 appointment 表字段，关联患者信息）
export const mockReceptionList = () => {
  return {
    code: 0,
    message: 'success',
    data: [
      {
        id: 1,
        appointmentNo: 'APT20260710001',
        patientId: 1001,
        patientName: '测试患者',
        gender: 'MALE',
        age: 36,
        doctorId: 1,
        departmentId: 1,
        scheduleId: 1,
        scheduleDate: dayjs().format('YYYY-MM-DD'),
        period: 'MORNING',
        queueNo: 3,
        fee: 50,
        paymentStatus: 'PAID',
        appointmentStatus: 'PAID',
        visitReason: '胸闷气短一周，活动后加重',
        createdAt: '2026-07-09 10:20:00'
      },
      {
        id: 2,
        appointmentNo: 'APT20260710002',
        patientId: 1002,
        patientName: '李患者',
        gender: 'FEMALE',
        age: 28,
        doctorId: 1,
        departmentId: 1,
        scheduleId: 1,
        scheduleDate: dayjs().format('YYYY-MM-DD'),
        period: 'MORNING',
        queueNo: 5,
        fee: 50,
        paymentStatus: 'PAID',
        appointmentStatus: 'CHECKED_IN',
        visitReason: '反复咳嗽、咳痰3天',
        createdAt: '2026-07-09 14:30:00'
      },
      {
        id: 3,
        appointmentNo: 'APT20260710003',
        patientId: 1003,
        patientName: '王患者',
        gender: 'MALE',
        age: 45,
        doctorId: 1,
        departmentId: 1,
        scheduleId: 2,
        scheduleDate: dayjs().format('YYYY-MM-DD'),
        period: 'AFTERNOON',
        queueNo: 2,
        fee: 50,
        paymentStatus: 'PAID',
        appointmentStatus: 'IN_PROGRESS',
        visitReason: '高血压复查，头晕2天',
        createdAt: '2026-07-08 09:10:00'
      },
      {
        id: 4,
        appointmentNo: 'APT20260709004',
        patientId: 1004,
        patientName: '赵患者',
        gender: 'FEMALE',
        age: 52,
        doctorId: 1,
        departmentId: 1,
        scheduleId: 3,
        scheduleDate: dayjs().subtract(1, 'day').format('YYYY-MM-DD'),
        period: 'MORNING',
        queueNo: 1,
        fee: 50,
        paymentStatus: 'PAID',
        appointmentStatus: 'COMPLETED',
        visitReason: '冠心病常规复诊',
        createdAt: '2026-07-07 16:00:00'
      }
    ]
  }
}

// 开始就诊
export const mockStartVisit = (appointmentId) => {
  return {
    code: 0,
    message: '已开始就诊',
    data: {
      id: appointmentId,
      appointmentStatus: 'IN_PROGRESS',
      startTime: dayjs().format('YYYY-MM-DD HH:mm:ss')
    }
  }
}

// 结束就诊
export const mockEndVisit = (appointmentId) => {
  return {
    code: 0,
    message: '就诊已完成',
    data: {
      id: appointmentId,
      appointmentStatus: 'COMPLETED',
      endTime: dayjs().format('YYYY-MM-DD HH:mm:ss')
    }
  }
}
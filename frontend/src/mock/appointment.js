import dayjs from 'dayjs'

let mockAppointments = [
  { id: 1, appointmentNo: 'APT20260710001', patientId: 1001, patientName: '测试患者', gender: 'MALE', age: 36, doctorId: 1, departmentId: 1, doctorName: '张明', deptName: '心血管内科', scheduleId: 1, scheduleDate: dayjs().format('YYYY-MM-DD'), period: 'MORNING', queueNo: 3, fee: 50, paymentStatus: 'PAID', appointmentStatus: 'BOOKED', visitReason: '胸闷气短一周，活动后加重', createdAt: '2026-07-09 10:20:00' },
  { id: 10, appointmentNo: 'APT20260715010', patientId: 1001, patientName: '测试患者', gender: 'MALE', age: 36, doctorId: 2, departmentId: 2, doctorName: '李华', deptName: '呼吸内科', scheduleId: 10, scheduleDate: dayjs().add(1, 'day').format('YYYY-MM-DD'), period: 'AFTERNOON', queueNo: 0, fee: 35, paymentStatus: 'UNPAID', appointmentStatus: 'BOOKED', visitReason: '', createdAt: dayjs().format('YYYY-MM-DD HH:mm:ss') },
  { id: 2, appointmentNo: 'APT20260710002', patientId: 1002, patientName: '李患者', gender: 'FEMALE', age: 28, doctorId: 1, departmentId: 1, doctorName: '张明', deptName: '心血管内科', scheduleId: 1, scheduleDate: dayjs().format('YYYY-MM-DD'), period: 'MORNING', queueNo: 5, fee: 50, paymentStatus: 'PAID', appointmentStatus: 'CHECKED_IN', visitReason: '反复咳嗽、咳痰3天', createdAt: '2026-07-09 14:30:00' },
  { id: 3, appointmentNo: 'APT20260710003', patientId: 1003, patientName: '王患者', gender: 'MALE', age: 45, doctorId: 1, departmentId: 1, doctorName: '张明', deptName: '心血管内科', scheduleId: 2, scheduleDate: dayjs().format('YYYY-MM-DD'), period: 'AFTERNOON', queueNo: 2, fee: 50, paymentStatus: 'PAID', appointmentStatus: 'IN_PROGRESS', visitReason: '高血压复查，头晕2天', createdAt: '2026-07-08 09:10:00' },
  { id: 4, appointmentNo: 'APT20260709004', patientId: 1004, patientName: '赵患者', gender: 'FEMALE', age: 52, doctorId: 1, departmentId: 1, doctorName: '张明', deptName: '心血管内科', scheduleId: 3, scheduleDate: dayjs().subtract(1, 'day').format('YYYY-MM-DD'), period: 'MORNING', queueNo: 1, fee: 50, paymentStatus: 'PAID', appointmentStatus: 'COMPLETED', visitReason: '冠心病常规复诊', createdAt: '2026-07-07 16:00:00' },
  { id: 5, appointmentNo: 'APT20260708005', patientId: 1001, patientName: '测试患者', gender: 'MALE', age: 36, doctorId: 2, departmentId: 2, doctorName: '李华', deptName: '呼吸内科', scheduleId: 4, scheduleDate: '2026-07-08', period: 'AFTERNOON', queueNo: 5, fee: 35, paymentStatus: 'PAID', appointmentStatus: 'COMPLETED', visitReason: '咳嗽咳痰', createdAt: '2026-07-06 11:20:00' },
  { id: 6, appointmentNo: 'APT20260705006', patientId: 1001, patientName: '测试患者', gender: 'MALE', age: 36, doctorId: 4, departmentId: 4, doctorName: '王强', deptName: '骨科', scheduleId: 5, scheduleDate: '2026-07-05', period: 'MORNING', queueNo: 0, fee: 20, paymentStatus: 'REFUNDED', appointmentStatus: 'CANCELLED', cancelReason: '个人时间冲突', visitReason: '腰部酸痛', createdAt: '2026-07-03 09:00:00' }
]

let nextId = 7

let mockSchedules = []
const doctorData = [
  { id: 1, name: '张明', departmentId: 1, deptName: '心血管内科', fee: 50 },
  { id: 2, name: '刘建国', departmentId: 1, deptName: '心血管内科', fee: 35 },
  { id: 4, name: '李华', departmentId: 2, deptName: '呼吸内科', fee: 50 },
  { id: 6, name: '王强', departmentId: 3, deptName: '消化内科', fee: 50 },
  { id: 7, name: '刘洋', departmentId: 4, deptName: '骨科', fee: 40 },
  { id: 8, name: '王芳', departmentId: 5, deptName: '儿科', fee: 30 }
]
let scheduleNextId = 1
for (let i = 0; i < 14; i++) {
  const date = dayjs().add(i - 3, 'day').format('YYYY-MM-DD')
  doctorData.forEach((doc, idx) => {
    const bookedM = Math.floor(Math.random() * 20)
    const bookedA = Math.floor(Math.random() * 15)
    mockSchedules.push({
      id: scheduleNextId++,
      scheduleNo: 'SCH' + String(scheduleNextId).padStart(6, '0'),
      doctorId: doc.id,
      departmentId: doc.departmentId,
      doctorName: doc.name,
      deptName: doc.deptName,
      scheduleDate: date,
      period: 'MORNING',
      startTime: '08:00',
      endTime: '12:00',
      totalQuota: 20,
      bookedQuota: bookedM,
      registrationFee: doc.fee,
      status: bookedM >= 20 ? 'FULL' : (i === 0 && idx === 0 ? 'STOPPED' : 'AVAILABLE')
    })
    mockSchedules.push({
      id: scheduleNextId++,
      scheduleNo: 'SCH' + String(scheduleNextId).padStart(6, '0'),
      doctorId: doc.id,
      departmentId: doc.departmentId,
      doctorName: doc.name,
      deptName: doc.deptName,
      scheduleDate: date,
      period: 'AFTERNOON',
      startTime: '14:00',
      endTime: '17:30',
      totalQuota: 15,
      bookedQuota: bookedA,
      registrationFee: doc.fee,
      status: bookedA >= 15 ? 'FULL' : 'AVAILABLE'
    })
  })
}

export const mockScheduleList = (doctorId) => {
  let list = [...mockSchedules]
  if (doctorId) {
    list = list.filter(s => s.doctorId === Number(doctorId))
  }
  return { code: 0, message: 'success', data: list }
}

export const mockCreateAppointment = (data) => {
  const apt = {
    id: nextId++,
    appointmentNo: 'APT' + Date.now(),
    patientId: 1001,
    patientName: '测试患者',
    gender: 'MALE',
    age: 36,
    doctorId: data.doctorId || 1,
    departmentId: data.departmentId || 1,
    doctorName: data.doctorName || '张明',
    deptName: data.deptName || '心血管内科',
    scheduleId: data.scheduleId || 1,
    scheduleDate: data.appointmentDate || dayjs().format('YYYY-MM-DD'),
    period: data.period || 'MORNING',
    queueNo: 0,
    fee: data.fee || 50,
    paymentStatus: 'UNPAID',
    appointmentStatus: 'BOOKED',
    visitReason: data.visitReason || '',
    createdAt: dayjs().format('YYYY-MM-DD HH:mm:ss')
  }
  mockAppointments.unshift(apt)
  return { code: 0, message: '预约成功，请在30分钟内完成支付', data: apt }
}

export const mockMyAppointmentList = (params = {}) => {
  let list = mockAppointments.filter(a => a.patientId === 1001)
  if (params.status) list = list.filter(a => a.appointmentStatus === params.status)
  return { code: 0, message: 'success', data: { records: list, total: list.length, pageNum: params.pageNum || 1, pageSize: params.pageSize || 10 } }
}

export const mockCancelAppointment = (id) => {
  const apt = mockAppointments.find(a => a.id === Number(id))
  if (apt) {
    apt.appointmentStatus = 'CANCELLED'
    apt.cancelReason = '用户主动取消'
    if (apt.paymentStatus === 'PAID') apt.paymentStatus = 'REFUNDED'
  }
  return { code: 0, message: '取消成功', data: { id } }
}

export const mockAppointmentDetail = (id) => {
  const apt = mockAppointments.find(a => a.id === Number(id))
  return { code: 0, message: 'success', data: apt || mockAppointments[0] }
}

export const mockCheckInAppointment = (id) => {
  const apt = mockAppointments.find(a => a.id === Number(id))
  if (apt && apt.appointmentStatus === 'BOOKED' && apt.paymentStatus === 'PAID') {
    apt.appointmentStatus = 'CHECKED_IN'
    apt.checkinTime = dayjs().format('YYYY-MM-DD HH:mm:ss')
    apt.queueNo = Math.floor(Math.random() * 10) + 1
  }
  return { code: 0, message: '签到成功，请等待叫号', data: apt }
}

export const mockPayAppointment = (id) => {
  const apt = mockAppointments.find(a => a.id === Number(id))
  if (apt && apt.paymentStatus === 'UNPAID') {
    apt.paymentStatus = 'PAID'
    apt.payTime = dayjs().format('YYYY-MM-DD HH:mm:ss')
    apt.transactionNo = 'PAY' + Date.now()
  }
  return { code: 0, message: '支付成功', data: apt }
}

export const mockMarkNoShow = (id) => {
  const apt = mockAppointments.find(a => a.id === Number(id))
  if (apt) {
    apt.appointmentStatus = 'NO_SHOW'
  }
  return { code: 0, message: '已标记为爽约', data: apt }
}

export const mockReceptionList = (params = {}) => {
  let list = mockAppointments.filter(a => a.doctorId === 1)
  if (params.status && params.status !== 'ALL') list = list.filter(a => a.appointmentStatus === params.status)
  return { code: 0, message: 'success', data: { records: list, total: list.length, pageNum: params.pageNum || 1, pageSize: params.pageSize || 10 } }
}

export const mockStartVisit = (appointmentId) => {
  const apt = mockAppointments.find(a => a.id === Number(appointmentId))
  if (apt) {
    apt.appointmentStatus = 'IN_PROGRESS'
    apt.startTime = dayjs().format('YYYY-MM-DD HH:mm:ss')
  }
  return { code: 0, message: '已开始就诊', data: apt }
}

export const mockEndVisit = (appointmentId) => {
  const apt = mockAppointments.find(a => a.id === Number(appointmentId))
  if (apt) {
    apt.appointmentStatus = 'COMPLETED'
    apt.endTime = dayjs().format('YYYY-MM-DD HH:mm:ss')
  }
  return { code: 0, message: '就诊已完成', data: apt }
}

export const mockAdminAppointmentList = (params = {}) => {
  let list = [...mockAppointments]
  if (params.status && params.status !== 'ALL') list = list.filter(a => a.appointmentStatus === params.status)
  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    list = list.filter(a => a.patientName.includes(kw) || a.appointmentNo.includes(kw) || a.doctorName.includes(kw))
  }
  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 10
  const total = list.length
  const records = list.slice((pageNum - 1) * pageSize, pageNum * pageSize)
  return { code: 0, message: 'success', data: { records, total, pageNum, pageSize } }
}

export const mockScheduleManageList = (params = {}) => {
  let list = [...mockSchedules]
  if (params.departmentId) {
    list = list.filter(s => s.departmentId === Number(params.departmentId))
  }
  if (params.doctorId) {
    list = list.filter(s => s.doctorId === Number(params.doctorId))
  }
  if (params.status) {
    list = list.filter(s => s.status === params.status)
  }
  if (params.startDate) {
    list = list.filter(s => s.scheduleDate >= params.startDate)
  }
  if (params.endDate) {
    list = list.filter(s => s.scheduleDate <= params.endDate)
  }
  list.sort((a, b) => b.scheduleDate.localeCompare(a.scheduleDate) || a.period.localeCompare(b.period))
  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 10
  const total = list.length
  const records = list.slice((pageNum - 1) * pageSize, pageNum * pageSize)
  return { code: 0, message: 'success', data: { records, total, pageNum, pageSize } }
}

export const mockCreateSchedule = (data) => {
  const doctor = doctorData.find(d => d.id === Number(data.doctorId)) || doctorData[0]
  const newSchedule = {
    id: scheduleNextId++,
    scheduleNo: 'SCH' + String(scheduleNextId).padStart(6, '0'),
    doctorId: Number(data.doctorId),
    departmentId: doctor.departmentId,
    doctorName: doctor.name,
    deptName: doctor.deptName,
    scheduleDate: data.scheduleDate,
    period: data.period,
    startTime: data.startTime,
    endTime: data.endTime,
    totalQuota: Number(data.totalQuota),
    bookedQuota: 0,
    registrationFee: Number(data.registrationFee),
    status: 'AVAILABLE'
  }
  mockSchedules.push(newSchedule)
  return { code: 0, message: '排班创建成功', data: newSchedule }
}

export const mockUpdateSchedule = (data) => {
  const idx = mockSchedules.findIndex(s => s.id === Number(data.id))
  if (idx !== -1) {
    const doctor = doctorData.find(d => d.id === Number(data.doctorId)) || mockSchedules[idx]
    mockSchedules[idx] = {
      ...mockSchedules[idx],
      doctorId: Number(data.doctorId),
      departmentId: doctor.departmentId || mockSchedules[idx].departmentId,
      doctorName: doctor.name || mockSchedules[idx].doctorName,
      deptName: doctor.deptName || mockSchedules[idx].deptName,
      scheduleDate: data.scheduleDate,
      period: data.period,
      startTime: data.startTime,
      endTime: data.endTime,
      totalQuota: Number(data.totalQuota),
      registrationFee: Number(data.registrationFee)
    }
    return { code: 0, message: '排班更新成功', data: mockSchedules[idx] }
  }
  return { code: 0, message: '排班更新成功', data }
}

export const mockDeleteSchedule = (id) => {
  const idx = mockSchedules.findIndex(s => s.id === Number(id))
  if (idx !== -1) {
    mockSchedules.splice(idx, 1)
  }
  return { code: 0, message: '排班已删除', data: { id } }
}

export const mockToggleScheduleStatus = (id, status) => {
  const schedule = mockSchedules.find(s => s.id === Number(id))
  if (schedule) {
    schedule.status = status
  }
  return { code: 0, message: status === 'STOPPED' ? '已停诊' : '已恢复', data: { id, status } }
}

import dayjs from 'dayjs'

// 患者个人信息（对齐 patient 表字段）
export const mockPatientInfo = () => {
  return {
    code: 0,
    message: 'success',
    data: {
      id: 1001,
      patientNo: 'P202607060001',
      name: '测试患者',
      gender: 'MALE',
      birthDate: '1990-05-15',
      idType: '身份证',
      idNo: '4201**********1234',
      phone: '13800138000',
      address: '湖北省武汉市洪山区XX街道XX小区',
      status: 'ACTIVE',
      allergies: ['青霉素过敏', '海鲜过敏'],
      pastMedicalHistory: ['急性支气管炎（2026年）', '腰肌劳损（2026年）'],
      familyHistory: ['父亲高血压', '母亲糖尿病'],
      emergencyContact: {
        name: '张三',
        relation: '配偶',
        phone: '13900139000'
      },
      createdAt: '2026-07-06 10:00:00'
    }
  }
}

// 健康档案（病史、过敏史等）
export const mockHealthArchive = () => {
  return {
    code: 0,
    message: 'success',
    data: {
      allergies: '青霉素过敏、海鲜过敏',
      pastMedicalHistory: '急性支气管炎（2026年）、腰肌劳损（2026年）',
      familyHistory: '父亲高血压、母亲糖尿病',
      emergencyContact: {
        name: '张三',
        relation: '配偶',
        phone: '13900139000'
      }
    }
  }
}

// 更新患者信息
export const mockUpdatePatientInfo = (data) => {
  // 同步更新 mockPatientInfo 的数据
  const mock = mockPatientInfo()
  if (data.phone) mock.data.phone = data.phone
  if (data.address !== undefined) mock.data.address = data.address
  if (data.allergies) mock.data.allergies = data.allergies
  if (data.pastMedicalHistory) mock.data.pastMedicalHistory = data.pastMedicalHistory
  if (data.familyHistory) mock.data.familyHistory = data.familyHistory
  if (data.emergencyContact !== undefined) mock.data.emergencyContact = data.emergencyContact
  return {
    code: 0,
    message: '更新成功',
    data
  }
}

const patientListData = [
  { id: 1, patientNo: 'P202601010001', name: '王伟', gender: 'MALE', age: 35, phone: '13800000001', idCard: '110101199001011234', status: 'ACTIVE', createdAt: '2026-01-01 09:00:00' },
  { id: 2, patientNo: 'P202601020002', name: '李娜', gender: 'FEMALE', age: 28, phone: '13800000002', idCard: '310101199503025678', status: 'ACTIVE', createdAt: '2026-01-02 10:30:00' },
  { id: 3, patientNo: 'P202601050003', name: '张伟', gender: 'MALE', age: 45, phone: '13800000003', idCard: '440101197805159012', status: 'ACTIVE', createdAt: '2026-01-05 14:20:00' },
  { id: 4, patientNo: 'P202601100004', name: '王芳', gender: 'FEMALE', age: 52, phone: '13800000004', idCard: '330101197109283456', status: 'DISABLED', createdAt: '2026-01-10 08:45:00' },
  { id: 5, patientNo: 'P202602010005', name: '刘洋', gender: 'MALE', age: 30, phone: '13800000005', idCard: '510101199302147890', status: 'ACTIVE', createdAt: '2026-02-01 11:15:00' },
  { id: 6, patientNo: 'P202602150006', name: '陈静', gender: 'FEMALE', age: 41, phone: '13800000006', idCard: '320101198206082345', status: 'ACTIVE', createdAt: '2026-02-15 16:00:00' },
  { id: 7, patientNo: 'P202603010007', name: '杨帆', gender: 'MALE', age: 26, phone: '13800000007', idCard: '420101199707226789', status: 'ACTIVE', createdAt: '2026-03-01 13:30:00' },
  { id: 8, patientNo: 'P202603080008', name: '赵敏', gender: 'FEMALE', age: 38, phone: '13800000008', idCard: '610101198512030123', status: 'DISABLED', createdAt: '2026-03-08 09:20:00' },
  { id: 9, patientNo: 'P202604010009', name: '周杰', gender: 'MALE', age: 49, phone: '13800000009', idCard: '500101197404114567', status: 'ACTIVE', createdAt: '2026-04-01 10:00:00' },
  { id: 10, patientNo: 'P202604150010', name: '吴婷', gender: 'FEMALE', age: 33, phone: '13800000010', idCard: '120101199008188901', status: 'ACTIVE', createdAt: '2026-04-15 15:45:00' }
]

const consumeFailOnce = (key) => {
  if (typeof localStorage === 'undefined') return false
  if (localStorage.getItem(key) !== '1') return false
  localStorage.removeItem(key)
  return true
}

export const mockPatientList = (params) => {
  if (consumeFailOnce('mock_patient_list_fail_once')) {
    return Promise.reject(new Error('患者列表加载失败，请重试'))
  }
  const { keyword = '', pageNum = 1, pageSize = 10 } = params || {}
  let filteredList = [...patientListData]
  if (keyword) {
    const lowerKeyword = keyword.toLowerCase()
    filteredList = filteredList.filter(item => 
      item.name.includes(keyword) || 
      item.phone.includes(keyword) || 
      item.patientNo.toLowerCase().includes(lowerKeyword)
    )
  }
  const total = filteredList.length
  const start = (pageNum - 1) * pageSize
  const end = start + pageSize
  const records = filteredList.slice(start, end)
  return {
    code: 0,
    message: 'success',
    data: {
      records,
      total,
      pageNum,
      pageSize
    }
  }
}

export const mockPatientDetail = (id) => {
  const patient = patientListData.find(item => item.id === Number(id)) || patientListData[0]
  return {
    code: 0,
    message: 'success',
    data: {
      ...patient,
      birthDate: '1990-05-15',
      idType: '身份证',
      address: '湖北省武汉市洪山区XX街道XX小区',
      allergies: patient.id % 2 === 0 ? '青霉素过敏' : '无',
      pastMedicalHistory: patient.gender === 'MALE' ? '高血压（2023年）' : '无重大病史',
      familyHistory: '父亲高血压、母亲糖尿病',
      emergencyContact: {
        name: '家属',
        relation: '配偶',
        phone: '13900139000'
      }
    }
  }
}

export const mockAddPatient = (data) => {
  const newId = patientListData.length + 1
  const newPatient = {
    id: newId,
    patientNo: `P${dayjs().format('YYYYMMDDHHmmss')}`,
    status: 'ACTIVE',
    createdAt: dayjs().format('YYYY-MM-DD HH:mm:ss'),
    ...data
  }
  patientListData.push(newPatient)
  return {
    code: 0,
    message: '新增成功',
    data: newPatient
  }
}

export const mockUpdatePatientStatus = (id, status) => {
  const patient = patientListData.find(item => item.id === Number(id))
  if (patient) {
    patient.status = status
  }
  return {
    code: 0,
    message: status === 'ACTIVE' ? '启用成功' : '禁用成功',
    data: { id: Number(id), status }
  }
}

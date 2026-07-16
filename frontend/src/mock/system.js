import dayjs from 'dayjs'

// 用户列表
export const mockUserList = () => {
  return {
    code: 0,
    message: 'success',
    data: [
      { id: 1, userNo: 'U001', account: 'admin', name: '系统管理员', phone: '13800000001', role: 'HOSPITAL_ADMIN', status: 'ACTIVE', createdAt: '2026-01-01 10:00:00' },
      { id: 2, userNo: 'U002', account: 'pharmacy01', name: '药房管理员', phone: '13800000002', role: 'PHARMACY_ADMIN', status: 'ACTIVE', createdAt: '2026-02-15 09:30:00' },
      { id: 3, userNo: 'D10001', account: 'zhangming', name: '张明', phone: '13800000003', role: 'DOCTOR', status: 'ACTIVE', createdAt: '2026-03-10 14:20:00' },
      { id: 4, userNo: 'D10002', account: 'lihua', name: '李华', phone: '13800000004', role: 'DOCTOR', status: 'ACTIVE', createdAt: '2026-03-12 11:00:00' },
      { id: 5, userNo: 'P10001', account: 'patient001', name: '测试患者', phone: '13800138000', role: 'PATIENT', status: 'ACTIVE', createdAt: '2026-07-06 10:00:00' },
      { id: 6, userNo: 'P10002', account: 'patient002', name: '李患者', phone: '13800000006', role: 'PATIENT', status: 'DISABLED', createdAt: '2026-06-20 16:00:00' }
    ],
    total: 6
  }
}

// 新增用户
export const mockAddUser = (data) => {
  return {
    code: 0,
    message: '新增成功',
    data: { id: Date.now(), ...data, createdAt: dayjs().format('YYYY-MM-DD HH:mm:ss') }
  }
}

// 更新用户
export const mockUpdateUser = (id, data) => {
  return {
    code: 0,
    message: '更新成功',
    data: { id, ...data }
  }
}

// 删除用户
export const mockDeleteUser = (id) => {
  return {
    code: 0,
    message: '删除成功',
    data: { id }
  }
}

// 科室列表
export const mockDeptList = () => {
  return {
    code: 0,
    message: 'success',
    data: [
      { id: 1, departmentNo: 'DEP_CARDIOLOGY', name: '心血管内科', description: '高血压、冠心病、心律失常', location: '门诊楼3层', enabled: 1, doctorCount: 8, createdAt: '2026-01-01' },
      { id: 2, departmentNo: 'DEP_RESPIRATORY', name: '呼吸内科', description: '肺炎、哮喘、慢性支气管炎', location: '门诊楼3层', enabled: 1, doctorCount: 6, createdAt: '2026-01-01' },
      { id: 3, departmentNo: 'DEP_GASTRO', name: '消化内科', description: '胃炎、胃溃疡、肝病', location: '门诊楼4层', enabled: 1, doctorCount: 5, createdAt: '2026-01-02' },
      { id: 4, departmentNo: 'DEP_ORTHOPEDICS', name: '骨科', description: '骨折、颈椎病、腰椎病', location: '门诊楼5层', enabled: 1, doctorCount: 7, createdAt: '2026-01-02' },
      { id: 5, departmentNo: 'DEP_PEDIATRICS', name: '儿科', description: '儿童常见病、生长发育', location: '门诊楼2层', enabled: 1, doctorCount: 9, createdAt: '2026-01-03' },
      { id: 6, departmentNo: 'DEP_DERMATOLOGY', name: '皮肤科', description: '皮炎、湿疹、痤疮', location: '门诊楼4层', enabled: 0, doctorCount: 4, createdAt: '2026-01-03' }
    ],
    total: 6
  }
}

// 新增科室
let deptAutoIncrement = 100
export const mockAddDept = (data) => {
  const newDeptNo = `DEP_${String(deptAutoIncrement++).padStart(3, '0')}`
  return {
    code: 0,
    message: '新增成功',
    data: { id: Date.now(), departmentNo: newDeptNo, ...data, enabled: 1, doctorCount: 0, createdAt: dayjs().format('YYYY-MM-DD') }
  }
}

// 更新科室
export const mockUpdateDept = (id, data) => {
  return {
    code: 0,
    message: '更新成功',
    data: { id, ...data }
  }
}

// 删除科室
export const mockDeleteDept = (id) => {
  return {
    code: 0,
    message: '删除成功',
    data: { id }
  }
}

// 医生列表
export const mockDoctorList = () => {
  return {
    code: 0,
    message: 'success',
    data: [
      { id: 1, doctorNo: 'D10001', name: '张明', gender: 'MALE', title: '主任医师', departmentId: 1, departmentName: '心血管内科', specialties: '冠心病、高血压、心力衰竭', phone: '13800000003', registrationFee: 50, status: 'ACTIVE', createdAt: '2026-03-10' },
      { id: 2, doctorNo: 'D10002', name: '刘建国', gender: 'MALE', title: '副主任医师', departmentId: 1, departmentName: '心血管内科', specialties: '心律失常、心肌病', phone: '13800000010', registrationFee: 35, status: 'ACTIVE', createdAt: '2026-03-12' },
      { id: 3, doctorNo: 'D10003', name: '赵雪', gender: 'FEMALE', title: '主治医师', departmentId: 1, departmentName: '心血管内科', specialties: '高血压、高血脂', phone: '13800000011', registrationFee: 20, status: 'ACTIVE', createdAt: '2026-04-01' },
      { id: 4, doctorNo: 'D10004', name: '李华', gender: 'MALE', title: '副主任医师', departmentId: 2, departmentName: '呼吸内科', specialties: '哮喘、肺炎、慢阻肺', phone: '13800000004', registrationFee: 35, status: 'ACTIVE', createdAt: '2026-03-15' },
      { id: 5, doctorNo: 'D10005', name: '王芳', gender: 'FEMALE', title: '主治医师', departmentId: 3, departmentName: '消化内科', specialties: '胃炎、胃溃疡、肝病', phone: '13800000012', registrationFee: 20, status: 'DISABLED', createdAt: '2026-03-20' }
    ],
    total: 5
  }
}

// 新增医生
export const mockAddDoctor = (data) => {
  return {
    code: 0,
    message: '新增成功',
    data: { id: Date.now(), ...data, createdAt: new Date().toLocaleDateString() }
  }
}

// 更新医生
export const mockUpdateDoctor = (id, data) => {
  return {
    code: 0,
    message: '更新成功',
    data: { id, ...data }
  }
}

// 删除医生
export const mockDeleteDoctor = (id) => {
  return {
    code: 0,
    message: '删除成功',
    data: { id }
  }
}

// 修改密码
export const mockChangePassword = (data) => {
  if (!data.oldPassword || !data.newPassword) {
    return {
      code: -1,
      message: '请输入原密码和新密码'
    }
  }
  if (data.oldPassword === data.newPassword) {
    return {
      code: -1,
      message: '新密码不能与原密码相同'
    }
  }
  if (data.newPassword.length < 6) {
    return {
      code: -1,
      message: '新密码长度不能少于6位'
    }
  }
  return {
    code: 0,
    message: '密码修改成功'
  }
}
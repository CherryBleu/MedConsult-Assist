const LOGIN_ACCOUNT_KEY = 'hospital_login_account'

export const mockRegister = (data) => {
  return {
    code: 0,
    message: '注册成功',
    data: {
      userId: Date.now(),
      userNo: 'U' + Date.now(),
      account: data.account,
      role: data.role || 'PATIENT',
      status: 'ACTIVE'
    }
  }
}

export const mockLogin = (payload) => {
  const account = typeof payload === 'string' ? payload : payload?.account
  const clientType = typeof payload === 'string' ? null : payload?.clientType
  const user = getUserByAccount(account)
  if (clientType === 'PATIENT' && user.role !== 'PATIENT') {
    return Promise.reject(new Error('该账号不是患者账号，请从工作人员入口登录'))
  }
  if (clientType === 'STAFF' && user.role === 'PATIENT') {
    return Promise.reject(new Error('该账号不是工作人员账号，请从患者入口登录'))
  }
  localStorage.setItem(LOGIN_ACCOUNT_KEY, account || '')
  return Promise.resolve({
    code: 0,
    message: '登录成功',
    data: {
      accessToken: 'mock-access-token-' + Date.now(),
      refreshToken: 'mock-refresh-token-' + Date.now(),
      expiresIn: 7200
    }
  })
}

export const mockRefreshToken = (refreshToken) => {
  return {
    code: 0,
    message: '刷新成功',
    data: {
      accessToken: 'mock-access-token-' + Date.now(),
      refreshToken: refreshToken,
      expiresIn: 7200
    }
  }
}

export const mockLogout = () => {
  localStorage.removeItem(LOGIN_ACCOUNT_KEY)
  return {
    code: 0,
    message: '登出成功',
    data: null
  }
}

const getUserByAccount = (account) => {
  const acc = (account || '').toLowerCase()
  const users = {
    patient: { id: 1, userNo: 'U202607060001', account: 'patient', name: '测试患者', phone: '138******00', role: 'PATIENT', patientId: 1001, doctorId: null, adminId: null, title: '' },
    patient001: { id: 1, userNo: 'U202607060001', account: 'patient001', name: '测试患者', phone: '138******00', role: 'PATIENT', patientId: 1001, doctorId: null, adminId: null, title: '' },
    doctor: { id: 2, userNo: 'U202607060002', account: 'doctor', name: '张医生', phone: '139******00', role: 'DOCTOR', patientId: null, doctorId: 1, adminId: null, title: '主任医师', departmentId: 1, departmentName: '心血管内科' },
    doctor001: { id: 2, userNo: 'U202607060002', account: 'doctor001', name: '张明医生', phone: '139******00', role: 'DOCTOR', patientId: null, doctorId: 1, adminId: null, title: '主任医师', departmentId: 1, departmentName: '心血管内科' },
    admin: { id: 3, userNo: 'U202607060003', account: 'admin', name: '系统管理员', phone: '137******00', role: 'HOSPITAL_ADMIN', patientId: null, doctorId: null, adminId: 1, title: '' },
    admin001: { id: 3, userNo: 'U202607060003', account: 'admin001', name: '系统管理员', phone: '137******00', role: 'HOSPITAL_ADMIN', patientId: null, doctorId: null, adminId: 1, title: '' },
    pharmacy: { id: 4, userNo: 'U202607060004', account: 'pharmacy', name: '药房管理员', phone: '136******00', role: 'PHARMACY_ADMIN', patientId: null, doctorId: null, adminId: null, title: '' },
    pharmacy001: { id: 4, userNo: 'U202607060004', account: 'pharmacy001', name: '药房管理员', phone: '136******00', role: 'PHARMACY_ADMIN', patientId: null, doctorId: null, adminId: null, title: '' }
  }
  if (users[acc]) return users[acc]
  if (acc.includes('doctor')) return users.doctor
  if (acc.includes('admin')) return users.admin
  if (acc.includes('pharmacy')) return users.pharmacy
  return users.patient
}

export const mockGetUserInfo = () => {
  const account = localStorage.getItem(LOGIN_ACCOUNT_KEY) || 'patient'
  const user = getUserByAccount(account)
  return {
    code: 0,
    message: 'success',
    data: { ...user, status: 'ACTIVE' }
  }
}

/**
 * 用户角色枚举
 * 对应数据库 sys_user 表 role 字段
 */
export const ROLE_ENUM = {
  PATIENT: {
    value: 'PATIENT',
    label: '患者'
  },
  DOCTOR: {
    value: 'DOCTOR',
    label: '医生'
  },
  PHARMACY_ADMIN: {
    value: 'PHARMACY_ADMIN',
    label: '药房管理员'
  },
  HOSPITAL_ADMIN: {
    value: 'HOSPITAL_ADMIN',
    label: '医院管理员'
  }
}

// 获取角色中文名称
export const getRoleLabel = (value) => {
  const item = Object.values(ROLE_ENUM).find(i => i.value === value)
  return item ? item.label : '未知角色'
}
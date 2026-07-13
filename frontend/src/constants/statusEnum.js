/**
 * 全业务状态枚举
 * 对齐状态转移图与数据库状态字段
 */

// 预约挂号状态（appointmentStatus 字段，与 paymentStatus 分开）
export const APPOINTMENT_STATUS = {
  BOOKED: { value: 'BOOKED', label: '待就诊', type: 'primary' },
  CANCELLED: { value: 'CANCELLED', label: '已取消', type: 'info' },
  CHECKED_IN: { value: 'CHECKED_IN', label: '已签到', type: 'success' },
  IN_PROGRESS: { value: 'IN_PROGRESS', label: '就诊中', type: 'primary' },
  COMPLETED: { value: 'COMPLETED', label: '已完成', type: 'success' },
  NO_SHOW: { value: 'NO_SHOW', label: '爽约', type: 'danger' }
}

// 支付状态（paymentStatus 字段，与 appointmentStatus 分开）
export const PAYMENT_STATUS = {
  UNPAID: { value: 'UNPAID', label: '待支付', type: 'warning' },
  PAID: { value: 'PAID', label: '已支付', type: 'success' },
  REFUNDING: { value: 'REFUNDING', label: '退款中', type: 'warning' },
  REFUNDED: { value: 'REFUNDED', label: '已退款', type: 'info' }
}

// 电子病历状态
export const MEDICAL_RECORD_STATUS = {
  DRAFT: { value: 'DRAFT', label: '草稿', type: 'info' },
  ARCHIVED: { value: 'ARCHIVED', label: '已归档', type: 'success' },
  REVISED: { value: 'REVISED', label: '已修订', type: 'primary' }
}

// 医生排班状态
export const SCHEDULE_STATUS = {
  AVAILABLE: { value: 'AVAILABLE', label: '可预约', type: 'success' },
  FULL: { value: 'FULL', label: '号满', type: 'warning' },
  SUSPENDED: { value: 'SUSPENDED', label: '停诊', type: 'danger' },
  CANCELLED: { value: 'CANCELLED', label: '已取消', type: 'info' }
}

// AI任务状态
export const AI_TASK_STATUS = {
  PENDING: { value: 'PENDING', label: '待处理', type: 'warning' },
  PROCESSING: { value: 'PROCESSING', label: '处理中', type: 'primary' },
  REVIEWED: { value: 'REVIEWED', label: '已完成', type: 'success' },
  FAILED: { value: 'FAILED', label: '失败', type: 'danger' }
}

// 药品库存预警状态
export const STOCK_STATUS = {
  NORMAL: { value: 'NORMAL', label: '库存充足', type: 'success' },
  LOW_STOCK: { value: 'LOW_STOCK', label: '库存不足', type: 'warning' },
  EXPIRED_WARNING: { value: 'EXPIRED_WARNING', label: '临期预警', type: 'warning' },
  DISABLED: { value: 'DISABLED', label: '禁用', type: 'danger' }
}

// 通用获取状态文本方法
export const getStatusLabel = (enumObj, value) => {
  const item = enumObj[value]
  return item ? item.label : '未知状态'
}

// 通用获取状态类型方法（对应element-plus tag类型）
export const getStatusType = (enumObj, value) => {
  const item = enumObj[value]
  return item ? item.type : 'info'
}
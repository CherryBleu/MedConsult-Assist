import dayjs from 'dayjs'

const LOGIN_ACCOUNT_KEY = 'hospital_login_account'
const NOTICE_CACHE_KEY = 'hospital_notice_cache'

const getCurrentAccount = () => localStorage.getItem(LOGIN_ACCOUNT_KEY) || 'patient'

const getCurrentRole = () => {
  const account = getCurrentAccount().toLowerCase()
  if (account.includes('doctor')) return 'DOCTOR'
  if (account.includes('pharmacy')) return 'PHARMACY_ADMIN'
  if (account.includes('admin')) return 'HOSPITAL_ADMIN'
  return 'PATIENT'
}

const buildDefaultNotices = (role) => {
  const now = dayjs()
  if (role === 'DOCTOR') {
    return [
      { id: 1, type: 'APPOINTMENT', title: '新预约提醒', content: '患者「测试患者」预约了您明天上午的心血管内科门诊，请留意排班。', isRead: false, createdAt: now.subtract(1, 'hour').format('YYYY-MM-DD HH:mm:ss') },
      { id: 2, type: 'APPOINTMENT', title: '候诊提醒', content: '患者「李患者」已签到，当前候诊队列共3人，请准备接诊。', isRead: false, createdAt: now.subtract(2, 'hour').format('YYYY-MM-DD HH:mm:ss') },
      { id: 3, type: 'SYSTEM', title: '系统维护通知', content: '系统将于今晚22:00-24:00进行维护升级，期间可能无法使用部分功能，请提前安排。', isRead: true, createdAt: now.subtract(5, 'hour').format('YYYY-MM-DD HH:mm:ss') },
      { id: 4, type: 'AI', title: '影像AI检测结果待审核', content: '患者「赵患者」的胸部CT影像AI检测已完成，发现疑似异常区域2处，请及时审核。', isRead: false, createdAt: now.subtract(3, 'hour').format('YYYY-MM-DD HH:mm:ss') },
      { id: 5, type: 'APPOINTMENT', title: '排班变更提醒', content: '您下周三（' + now.add(3, 'day').format('MM-DD') + '）的下午排班已调整为14:30开始，请留意。', isRead: true, createdAt: now.subtract(1, 'day').format('YYYY-MM-DD HH:mm:ss') },
      { id: 6, type: 'AI', title: '病历摘要已生成', content: '患者「王患者」的门诊病历AI摘要已生成，请查看并确认。', isRead: true, createdAt: now.subtract(2, 'day').format('YYYY-MM-DD HH:mm:ss') },
      { id: 7, type: 'SYSTEM', title: '欢迎使用智慧医院系统', content: '欢迎登录医生工作台，祝您工作顺利！', isRead: true, createdAt: now.subtract(7, 'day').format('YYYY-MM-DD HH:mm:ss') },
      { id: 8, type: 'APPOINTMENT', title: '停诊提醒', content: '您昨日申请的停诊已审核通过，相关预约患者已收到通知。', isRead: true, createdAt: now.subtract(3, 'day').format('YYYY-MM-DD HH:mm:ss') }
    ]
  }
  if (role === 'HOSPITAL_ADMIN') {
    return [
      { id: 1, type: 'SYSTEM', title: '运营日报', content: '今日共预约挂号126人次，已就诊89人次，待就诊37人次，无爽约异常。', isRead: false, createdAt: dayjs().subtract(1, 'hour').format('YYYY-MM-DD HH:mm:ss') },
      { id: 2, type: 'SYSTEM', title: '库存预警', content: '药品「阿莫西林胶囊」库存已低于预警线（剩余23盒），请及时补货。', isRead: false, createdAt: dayjs().subtract(2, 'hour').format('YYYY-MM-DD HH:mm:ss') },
      { id: 3, type: 'SYSTEM', title: '系统维护通知', content: '系统将于今晚22:00-24:00进行维护升级，期间可能无法使用部分功能。', isRead: true, createdAt: dayjs().subtract(5, 'hour').format('YYYY-MM-DD HH:mm:ss') },
      { id: 4, type: 'AI', title: 'AI服务统计', content: '本月AI分诊服务共使用1203次，AI问诊856次，影像检测234次，整体满意度96%。', isRead: false, createdAt: dayjs().subtract(4, 'hour').format('YYYY-MM-DD HH:mm:ss') },
      { id: 5, type: 'SYSTEM', title: '医生审核提醒', content: '有2位新注册医生等待资质审核，请及时处理。', isRead: true, createdAt: dayjs().subtract(1, 'day').format('YYYY-MM-DD HH:mm:ss') },
      { id: 6, type: 'SYSTEM', title: '欢迎使用智慧医院系统', content: '欢迎登录管理后台，祝您工作顺利！', isRead: true, createdAt: dayjs().subtract(7, 'day').format('YYYY-MM-DD HH:mm:ss') }
    ]
  }
  if (role === 'PHARMACY_ADMIN') {
    return [
      { id: 1, type: 'SYSTEM', title: '新处方待配药', content: '医生张明新开了3张处方，等待药房配药发药。', isRead: false, createdAt: dayjs().subtract(30, 'minute').format('YYYY-MM-DD HH:mm:ss') },
      { id: 2, type: 'SYSTEM', title: '库存预警', content: '药品「布洛芬缓释胶囊」库存已低于预警线（剩余15盒），请及时补货。', isRead: false, createdAt: dayjs().subtract(2, 'hour').format('YYYY-MM-DD HH:mm:ss') },
      { id: 3, type: 'SYSTEM', title: '近效期提醒', content: '有5个批次药品将在30天内过期，请及时处理。', isRead: true, createdAt: dayjs().subtract(3, 'hour').format('YYYY-MM-DD HH:mm:ss') },
      { id: 4, type: 'SYSTEM', title: '系统维护通知', content: '系统将于今晚22:00-24:00进行维护升级，期间可能无法使用部分功能。', isRead: true, createdAt: dayjs().subtract(5, 'hour').format('YYYY-MM-DD HH:mm:ss') },
      { id: 5, type: 'SYSTEM', title: '药品入库通知', content: '采购单PO20260710001已入库，共20种药品，合计金额¥12,580。', isRead: true, createdAt: dayjs().subtract(1, 'day').format('YYYY-MM-DD HH:mm:ss') },
      { id: 6, type: 'SYSTEM', title: '欢迎使用智慧医院系统', content: '欢迎登录药房管理系统，祝您工作顺利！', isRead: true, createdAt: dayjs().subtract(7, 'day').format('YYYY-MM-DD HH:mm:ss') }
    ]
  }
  return [
    { id: 1, type: 'APPOINTMENT', title: '预约提醒', content: '您预约的张明医生心血管内科门诊将于明天上午08:00开始，请提前15分钟到达医院签到。', isRead: false, createdAt: dayjs().subtract(1, 'hour').format('YYYY-MM-DD HH:mm:ss') },
    { id: 2, type: 'PAYMENT', title: '支付成功通知', content: '您已成功支付预约挂号费用50元，订单号：PAY' + dayjs().format('YYYYMMDD') + '001。', isRead: false, createdAt: dayjs().subtract(3, 'hour').format('YYYY-MM-DD HH:mm:ss') },
    { id: 3, type: 'SYSTEM', title: '系统维护通知', content: '系统将于今晚22:00-24:00进行维护升级，期间可能无法使用部分功能，请提前安排。', isRead: true, createdAt: dayjs().subtract(5, 'hour').format('YYYY-MM-DD HH:mm:ss') },
    { id: 4, type: 'AI', title: 'AI问诊报告已生成', content: '您的AI初诊报告已生成完成，建议就诊科室：呼吸内科，可查看详细报告。', isRead: false, createdAt: dayjs().subtract(8, 'hour').format('YYYY-MM-DD HH:mm:ss') },
    { id: 5, type: 'APPOINTMENT', title: '叫号提醒', content: '您的排队号码为003号，当前就诊号为001号，请做好就诊准备。', isRead: true, createdAt: dayjs().subtract(1, 'day').format('YYYY-MM-DD HH:mm:ss') },
    { id: 6, type: 'PAYMENT', title: '退款通知', content: '您取消的预约订单已完成退款，金额20元将在1-3个工作日内原路返回。', isRead: true, createdAt: dayjs().subtract(2, 'day').format('YYYY-MM-DD HH:mm:ss') },
    { id: 7, type: 'SYSTEM', title: '欢迎使用智慧医院系统', content: '感谢您注册使用智慧医院系统，祝您身体健康！如需帮助请联系客服。', isRead: true, createdAt: dayjs().subtract(7, 'day').format('YYYY-MM-DD HH:mm:ss') },
    { id: 8, type: 'AI', title: '用药提醒', content: '根据您的病历记录，AI助手提醒您：请按时服用降压药物，每日一次，每次一片。', isRead: true, createdAt: dayjs().subtract(3, 'day').format('YYYY-MM-DD HH:mm:ss') },
    { id: 9, type: 'APPOINTMENT', title: '就诊完成通知', content: '您的就诊已完成，请及时查看病历记录和处方信息。', isRead: true, createdAt: dayjs().subtract(2, 'day').format('YYYY-MM-DD HH:mm:ss') },
    { id: 10, type: 'SYSTEM', title: '密码修改成功', content: '您的账户密码已修改成功，如非本人操作请立即联系客服。', isRead: true, createdAt: dayjs().subtract(10, 'day').format('YYYY-MM-DD HH:mm:ss') }
  ]
}

const getCache = () => {
  try {
    return JSON.parse(localStorage.getItem(NOTICE_CACHE_KEY) || '{}')
  } catch (e) { return {} }
}

const setCache = (cache) => {
  localStorage.setItem(NOTICE_CACHE_KEY, JSON.stringify(cache))
}

const getCurrentNotices = () => {
  const account = getCurrentAccount()
  const role = getCurrentRole()
  const cache = getCache()
  if (!cache[account]) {
    cache[account] = buildDefaultNotices(role)
    setCache(cache)
  }
  return cache[account]
}

const saveCurrentNotices = (list) => {
  const account = getCurrentAccount()
  const cache = getCache()
  cache[account] = list
  setCache(cache)
}

export const mockNoticeList = (params = {}) => {
  let list = [...getCurrentNotices()]
  if (params.isRead !== undefined && params.isRead !== '' && params.isRead !== 'ALL') {
    list = list.filter(n => n.isRead === (params.isRead === 'true' || params.isRead === true))
  }
  if (params.type && params.type !== 'ALL') {
    list = list.filter(n => n.type === params.type)
  }
  list = list.sort((a, b) => dayjs(b.createdAt).valueOf() - dayjs(a.createdAt).valueOf())
  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 10
  const total = list.length
  const records = list.slice((pageNum - 1) * pageSize, pageNum * pageSize)
  return { code: 0, message: 'success', data: { records, total, pageNum, pageSize } }
}

export const mockUnreadCount = () => {
  const list = getCurrentNotices()
  const count = list.filter(n => !n.isRead).length
  return { code: 0, message: 'success', data: count }
}

export const mockMarkRead = (id) => {
  const list = getCurrentNotices()
  const notice = list.find(n => n.id === Number(id))
  if (notice) notice.isRead = true
  saveCurrentNotices(list)
  return { code: 0, message: 'success', data: { id } }
}

export const mockMarkAllRead = () => {
  const list = getCurrentNotices()
  list.forEach(n => { n.isRead = true })
  saveCurrentNotices(list)
  return { code: 0, message: 'success', data: null }
}

export const mockDeleteNotice = (id) => {
  let list = getCurrentNotices()
  list = list.filter(n => n.id !== Number(id))
  saveCurrentNotices(list)
  return { code: 0, message: 'success', data: { id } }
}

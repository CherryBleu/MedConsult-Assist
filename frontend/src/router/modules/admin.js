import MainLayout from '@/layouts/MainLayout.vue'

const adminRoutes = [
  {
    path: '/admin',
    component: MainLayout,
    redirect: '/admin/user',
    meta: { role: 'HOSPITAL_ADMIN', requiresAuth: true },
    children: [
      // 系统管理
      {
        path: 'user',
        name: 'UserManage',
        component: () => import('@/views/admin/system/UserManage.vue'),
        meta: { title: '医院管理员管理' }
      },
      {
        path: 'pharmacy-admin',
        name: 'PharmacyAdminManage',
        component: () => import('@/views/admin/system/PharmacyAdminManage.vue'),
        meta: { title: '药房管理员管理' }
      },
      {
        path: 'patient',
        name: 'PatientManage',
        component: () => import('@/views/admin/system/PatientManage.vue'),
        meta: { title: '患者管理' }
      },
      {
        path: 'doctor',
        name: 'DoctorManage',
        component: () => import('@/views/admin/system/DoctorManage.vue'),
        meta: { title: '医生管理' }
      },
      {
        path: 'department',
        name: 'DepartmentManage',
        component: () => import('@/views/admin/system/DepartmentManage.vue'),
        meta: { title: '科室管理' }
      },
      // 药品库存
      {
        path: 'drug',
        name: 'DrugList',
        component: () => import('@/views/admin/drug/DrugList.vue'),
        meta: { title: '药品管理' }
      },
      {
        path: 'stock',
        name: 'StockManage',
        component: () => import('@/views/admin/drug/StockManage.vue'),
        meta: { title: '库存管理' }
      },
      {
        path: 'stock-warning',
        name: 'StockWarning',
        component: () => import('@/views/admin/drug/StockWarning.vue'),
        meta: { title: '库存预警' }
      },
      {
        path: 'stock-flow',
        name: 'AdminStockFlow',
        component: () => import('@/views/admin/drug/StockFlow.vue'),
        meta: { title: '库存流水' }
      },
      // 排班管理
      {
        path: 'schedule',
        name: 'ScheduleManage',
        component: () => import('@/views/admin/schedule/ScheduleManage.vue'),
        meta: { title: '排班管理' }
      },
      // 排班模板（默认排班，后端修改.md #16）
      {
        path: 'schedule-template',
        name: 'ScheduleTemplate',
        component: () => import('@/views/admin/schedule/ScheduleTemplate.vue'),
        meta: { title: '排班模板' }
      },
      // AI管理
      {
        path: 'ai-call-log',
        name: 'AiCallLog',
        component: () => import('@/views/admin/ai-manage/AiCallLog.vue'),
        meta: { title: '调用日志' }
      },
      {
        path: 'ai-feedback',
        name: 'AiFeedback',
        component: () => import('@/views/admin/ai-manage/AiFeedback.vue'),
        meta: { title: '反馈管理' }
      },
      // 审计日志
      {
        path: 'audit-log',
        name: 'AuditLog',
        component: () => import('@/views/admin/audit/AuditLog.vue'),
        meta: { title: '审计日志' }
      },
      // 个人中心
      {
        path: 'profile',
        name: 'AdminProfile',
        component: () => import('@/views/admin/profile/UserInfo.vue'),
        meta: { title: '个人中心' }
      }
    ]
  }
]

export default adminRoutes
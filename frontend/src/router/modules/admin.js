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
        meta: { title: '用户管理' }
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
        // 复用药房管理员的库存流水页（系统管理员只读查看，不做出入库操作）
        component: () => import('@/views/pharmacy/drug/StockFlow.vue'),
        meta: { title: '库存流水' }
      },
      // 排班管理
      {
        path: 'schedule',
        name: 'ScheduleManage',
        component: () => import('@/views/admin/schedule/ScheduleManage.vue'),
        meta: { title: '排班管理' }
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
      }
    ]
  }
]

export default adminRoutes
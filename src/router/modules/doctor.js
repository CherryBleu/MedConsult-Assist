import MainLayout from '@/layouts/MainLayout.vue'

const doctorRoutes = [
  {
    path: '/doctor',
    component: MainLayout,
    redirect: '/doctor/workbench',
    meta: { role: 'DOCTOR', requiresAuth: true },
    children: [
      {
        path: 'workbench',
        name: 'DoctorWorkbench',
        component: () => import('@/views/doctor/workbench/DoctorWorkbench.vue'),
        meta: { title: '医生工作台' }
      },
      {
        path: 'reception',
        name: 'ReceptionList',
        component: () => import('@/views/doctor/appointment/ReceptionList.vue'),
        meta: { title: '接诊管理' }
      },
      {
        path: 'records',
        name: 'DoctorRecordList',
        component: () => import('@/views/doctor/record/RecordList.vue'),
        meta: { title: '病历管理' }
      },
      {
        path: 'record/write',
        name: 'RecordWrite',
        component: () => import('@/views/doctor/record/RecordWrite.vue'),
        meta: { title: '书写病历' }
      },
      {
        path: 'record/:id',
        name: 'DoctorRecordDetail',
        component: () => import('@/views/patient/record/RecordDetail.vue'),
        meta: { title: '病历详情' }
      },
      {
        path: 'schedule',
        name: 'MySchedule',
        component: () => import('@/views/doctor/schedule/MySchedule.vue'),
        meta: { title: '我的排班' }
      },
      {
        path: 'record-summary',
        name: 'RecordSummary',
        component: () => import('@/views/doctor/ai-tool/RecordSummary.vue'),
        meta: { title: '病历摘要' }
      },
      {
        path: 'medication-analysis',
        name: 'MedicationAnalysis',
        component: () => import('@/views/doctor/ai-tool/MedicationAnalysis.vue'),
        meta: { title: '用药分析' }
      },
      {
        path: 'ai/imaging',
        name: 'ImagingAssist',
        component: () => import('@/views/doctor/ai-tool/ImagingAssist.vue'),
        meta: { title: '影像辅助' }
      }
    ]
  }
]

export default doctorRoutes
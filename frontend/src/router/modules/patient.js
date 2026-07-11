import MainLayout from '@/layouts/MainLayout.vue'

const patientRoutes = [
  {
    path: '/patient',
    component: MainLayout,
    redirect: '/patient/home',
    meta: { role: 'PATIENT', requiresAuth: true },
    children: [
      {
        path: 'home',
        name: 'PatientHome',
        component: () => import('@/views/patient/home/PatientHome.vue'),
        meta: { title: '患者首页' }
      },
      {
        path: 'appointment',
        name: 'MyAppointment',
        component: () => import('@/views/patient/appointment/MyAppointment.vue'),
        meta: { title: '我的预约' }
      },
	  {
	    path: 'appointment/department',
	    name: 'DepartmentList',
	    component: () => import('@/views/patient/appointment/DepartmentList.vue'),
	    meta: { title: '选择科室' }
	  },
	  {
	    path: 'record/:id',
	    name: 'RecordDetail',
	    component: () => import('@/views/patient/record/RecordDetail.vue'),
	    meta: { title: '病历详情' }
	  },
	  {
	    path: 'health-archive',
	    name: 'HealthArchive',
	    component: () => import('@/views/patient/profile/HealthArchive.vue'),
	    meta: { title: '健康档案' }
	  },
	  {
	    path: 'appointment/doctor',
	    name: 'DoctorList',
	    component: () => import('@/views/patient/appointment/DoctorList.vue'),
	    meta: { title: '选择医生' }
	  },
	  {
	    path: 'triage',
	    name: 'Triage',
	    component: () => import('@/views/patient/ai-service/Triage.vue'),
	    meta: { title: '智能分诊' }
	  },
	  {
	    path: 'ai-consult',
	    name: 'AiConsult',
	    component: () => import('@/views/patient/ai-service/AiConsult.vue'),
	    meta: { title: 'AI问诊' }
	  },
	  {
	    path: 'ai/imaging',
	    name: 'ImagingDetection',
	    component: () => import('@/views/patient/ai-service/ImagingDetection.vue'),
	    meta: { title: '影像检测' }
	  },
	  {
	    path: 'appointment/confirm',
	    name: 'AppointmentConfirm',
	    component: () => import('@/views/patient/appointment/AppointmentConfirm.vue'),
	    meta: { title: '预约确认' }
	  },
	  {
	    path: 'appointment/schedule',
	    name: 'ScheduleSelect',
	    component: () => import('@/views/patient/appointment/ScheduleSelect.vue'),
	    meta: { title: '选择排班' }
	  },
      {
        path: 'records',
        name: 'RecordList',
        component: () => import('@/views/patient/record/RecordList.vue'),
        meta: { title: '我的病历' }
      },
      {
        path: 'profile',
        name: 'UserInfo',
        component: () => import('@/views/patient/profile/UserInfo.vue'),
        meta: { title: '个人中心' }
      }
    ]
  }
]

export default patientRoutes
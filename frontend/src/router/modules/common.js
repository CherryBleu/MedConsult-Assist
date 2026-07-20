const commonRoutes = [
  {
    path: '/',
    redirect: '/patient-login'
  },
  {
    path: '/patient-login',
    name: 'PatientLogin',
    component: () => import('@/views/common/PatientLogin.vue'),
    meta: { title: '患者登录' }
  },
  {
    path: '/staff-login',
    name: 'StaffLogin',
    component: () => import('@/views/common/StaffLogin.vue'),
    meta: { title: '工作人员登录' }
  },
  {
    path: '/login',
    name: 'Login',
    redirect: to => ({ path: '/patient-login', query: to.query }),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/common/Register.vue'),
    meta: { title: '注册' }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/common/NotFound.vue'),
    meta: { title: '404' }
  },
  {
    path: '/notice',
    name: 'NoticeList',
    component: () => import('@/views/common/NoticeList.vue'),
    meta: { title: '通知中心' }
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/common/Home.vue'),
    meta: { title: '首页' }
  }
]

export default commonRoutes

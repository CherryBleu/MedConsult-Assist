const commonRoutes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/common/Login.vue'),
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
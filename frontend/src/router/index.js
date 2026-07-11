import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import commonRoutes from './modules/common'
import patientRoutes from './modules/patient'
import doctorRoutes from './modules/doctor'
import adminRoutes from './modules/admin'
import pharmacyRoutes from './modules/pharmacy'

const routes = [
  ...commonRoutes,
  ...patientRoutes,
  ...doctorRoutes,
  ...adminRoutes,
  ...pharmacyRoutes,
  // 404兜底
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const hasToken = userStore.token

  const whiteList = ['/login', '/register', '/404']

  if (hasToken) {
    if (to.path === '/login') {
      next('/home')
    } else {
      if (!userStore.role) {
        try {
          await userStore.getUserInfo()
        } catch (e) {
          userStore.logout()
          next(`/login?redirect=${to.path}`)
          return
        }
      }
      const requiredRole = to.matched.find(record => record.meta && record.meta.role)?.meta?.role
      if (requiredRole) {
        const roles = Array.isArray(requiredRole) ? requiredRole : [requiredRole]
        if (!roles.includes(userStore.role)) {
          next('/home')
          return
        }
      }
      next()
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
    }
  }
})

export default router
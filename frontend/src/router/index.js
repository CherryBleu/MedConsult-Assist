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
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const getLoginPathForRole = (role) => role === 'PATIENT' ? '/patient-login' : '/staff-login'

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const hasToken = userStore.token

  const loginPaths = ['/login', '/patient-login', '/staff-login']
  const whiteList = [...loginPaths, '/register', '/404']

  if (hasToken) {
    if (loginPaths.includes(to.path)) {
      await userStore.logout()
      next()
      return
    }

    if (!userStore.role) {
      try {
        await userStore.getUserInfo()
      } catch (e) {
        const requiredRole = to.matched.find(record => record.meta && record.meta.role)?.meta?.role
        const loginPath = getLoginPathForRole(requiredRole)
        await userStore.logout()
        next(`${loginPath}?redirect=${to.path}`)
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
    return
  }

  if (whiteList.includes(to.path)) {
    next()
    return
  }

  const requiredRole = to.matched.find(record => record.meta && record.meta.role)?.meta?.role
  const loginPath = getLoginPathForRole(requiredRole)
  next(`${loginPath}?redirect=${to.path}`)
})

export default router

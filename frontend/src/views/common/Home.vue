<template>
  <div class="home-loading">
    <el-icon class="is-loading" :size="40"><Loading /></el-icon>
    <p>正在跳转...</p>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { ROLE_ENUM } from '@/constants'

const router = useRouter()
const userStore = useUserStore()

onMounted(() => {
  // 确保用户信息已获取
  const init = async () => {
    if (!userStore.role) {
      try {
        await userStore.getUserInfo()
      } catch (err) {
        router.replace('/login')
        return
      }
    }

    const role = userStore.role
    const pathMap = {
      [ROLE_ENUM.PATIENT.value]: '/patient/home',
      [ROLE_ENUM.DOCTOR.value]: '/doctor/workbench',
      [ROLE_ENUM.HOSPITAL_ADMIN.value]: '/admin/user',
      [ROLE_ENUM.PHARMACY_ADMIN.value]: '/pharmacy/workbench'
    }

    const targetPath = pathMap[role] || '/login'
    router.replace(targetPath)
  }

  init()
})
</script>

<style scoped>
.home-loading {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--text-secondary);
}
</style>
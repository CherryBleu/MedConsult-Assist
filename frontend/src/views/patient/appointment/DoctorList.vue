<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <el-button @click="$router.back()" :icon="ArrowLeft">返回</el-button>
        <h2 class="page-title">{{ deptName || '全部医生' }}</h2>
      </div>

      <div v-loading="loading" class="doctor-list">
        <div v-for="doctor in doctorList" :key="doctor.id" class="doctor-item">
          <el-avatar :size="64" class="doctor-avatar">
            {{ doctor.name.charAt(0) }}
          </el-avatar>
          <div class="doctor-content">
            <div class="doctor-top">
              <span class="doctor-name">{{ doctor.name }}</span>
              <el-tag type="primary" size="small">{{ doctor.title }}</el-tag>
            </div>
            <div class="doctor-special">
              <span class="label">擅长：</span>
              <span>{{ doctor.specialties }}</span>
            </div>
            <div class="doctor-intro">{{ doctor.introduction }}</div>
          </div>
          <div class="doctor-action">
            <div class="fee-text">挂号费：¥{{ doctor.registrationFee }}</div>
            <el-button type="primary" @click="goToSchedule(doctor.id)">
              预约挂号
            </el-button>
          </div>
        </div>

        <el-empty v-if="!loading && doctorList.length === 0" description="该科室暂无出诊医生" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getDoctorListApi } from '@/api/doctor'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const deptName = ref('')
const doctorList = ref([])

// 获取医生列表
const getDoctorList = async () => {
  const deptId = route.query.deptId
  if (!deptId) return

  loading.value = true
  try {
    const res = await getDoctorListApi(deptId)
    doctorList.value = res.data
  } catch (e) {
    // 科室不存在或接口错误时清空列表，拦截器已展示错误消息
    doctorList.value = []
  } finally {
    loading.value = false
  }
}

const goToSchedule = (doctorId) => {
  router.push({
    path: '/patient/appointment/schedule',
    query: { 
      doctorId, 
      doctorName: doctorList.value.find(i => i.id === doctorId)?.name,
      deptName: deptName.value 
    }
  })
}

onMounted(() => {
  deptName.value = route.query.deptName || ''
  getDoctorList()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.doctor-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.doctor-item {
  display: flex;
  gap: 20px;
  padding: 20px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  align-items: center;
}
.doctor-avatar {
  background: var(--primary-color);
  font-size: 24px;
  flex-shrink: 0;
}
.doctor-content {
  flex: 1;
}
.doctor-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.doctor-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}
.doctor-special {
  font-size: 13px;
  color: var(--text-regular);
  margin-bottom: 6px;
}
.doctor-special .label {
  color: var(--text-secondary);
}
.doctor-intro {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}
.doctor-action {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  flex-shrink: 0;
}
.fee-text {
  font-size: 14px;
  color: var(--danger-color);
  font-weight: 600;
}
</style>
<template>
  <div class="page-container">
    <div class="card-box">
      <h2 class="page-title">个人信息</h2>

      <div v-loading="loading" class="info-card">
        <div class="avatar-section">
          <el-avatar :size="80" class="user-avatar">
            {{ patientInfo.name?.charAt(0) || '用' }}
          </el-avatar>
          <div class="user-name">{{ patientInfo.name }}</div>
          <el-tag size="small" type="primary">{{ patientInfo.patientNo }}</el-tag>
        </div>

        <el-form label-width="100px" class="info-form">
          <el-form-item label="姓名">
            <span class="info-text">{{ patientInfo.name }}</span>
          </el-form-item>
          <el-form-item label="性别">
            <span class="info-text">{{ patientInfo.gender === 'MALE' ? '男' : '女' }}</span>
          </el-form-item>
          <el-form-item label="出生日期">
            <span class="info-text">{{ patientInfo.birthDate }}</span>
          </el-form-item>
          <el-form-item label="手机号">
            <span class="info-text">{{ patientInfo.phone }}</span>
          </el-form-item>
          <el-form-item label="证件类型">
            <span class="info-text">{{ patientInfo.idType }}</span>
          </el-form-item>
          <el-form-item label="证件号">
            <span class="info-text">{{ patientInfo.idNo }}</span>
          </el-form-item>
          <el-form-item label="联系地址">
            <span class="info-text">{{ patientInfo.address }}</span>
          </el-form-item>
          <el-form-item label="档案状态">
            <el-tag type="success">正常</el-tag>
          </el-form-item>
        </el-form>

        <div class="action-bar">
          <el-button type="primary" @click="goToHealthArchive">查看健康档案</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getPatientInfoApi } from '@/api/patient'

const router = useRouter()
const loading = ref(false)
const patientInfo = ref({})

const getPatientInfo = async () => {
  loading.value = true
  try {
    const res = await getPatientInfoApi()
    patientInfo.value = res.data
  } finally {
    loading.value = false
  }
}

const goToHealthArchive = () => {
  router.push('/patient/health-archive')
}

onMounted(() => {
  getPatientInfo()
})
</script>

<style scoped>
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-light);
}
.user-avatar {
  background: var(--primary-color);
  font-size: 32px;
}
.user-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.info-form {
  max-width: 500px;
  margin: 0 auto;
}
.info-text {
  font-size: 14px;
  color: var(--text-primary);
}

.action-bar {
  text-align: center;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}
</style>
<template>
  <div class="page-container">
    <!-- 数据概览卡片 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-value">{{ todayTotal }}</div>
        <div class="stat-label">今日预约</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ pendingCount }}</div>
        <div class="stat-label">待就诊</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ completedCount }}</div>
        <div class="stat-label">已完成</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ userStore.userInfo.name }}</div>
        <div class="stat-label">{{ userStore.userInfo.title || '医生' }}</div>
      </div>
    </div>

    <div class="content-row">
      <!-- 待就诊列表 -->
      <div class="card-box flex-1">
        <div class="section-header">
          <h3 class="section-title">今日待就诊</h3>
          <el-button type="primary" link @click="$router.push('/doctor/reception')">查看全部</el-button>
        </div>
        <div v-loading="loading" class="patient-list">
          <div v-for="item in pendingList" :key="item.id" class="patient-item">
            <div class="patient-info">
              <span class="patient-name">{{ item.patientName }}</span>
              <el-tag size="small">{{ item.gender === 'MALE' ? '男' : '女' }} {{ item.age }}岁</el-tag>
            </div>
            <div class="visit-reason">主诉：{{ item.visitReason }}</div>
            <div class="item-footer">
              <span>第{{ item.queueNo }}号</span>
              <el-button type="primary" size="small" @click="goWriteRecord(item)">
                去接诊
              </el-button>
            </div>
          </div>
          <el-empty v-if="!loading && pendingList.length === 0" :image-size="80" description="暂无待就诊患者" />
        </div>
      </div>

      <!-- 快捷功能 -->
      <div class="card-box quick-card">
        <h3 class="section-title">快捷功能</h3>
        <div class="quick-grid">
          <div class="quick-item" @click="$router.push('/doctor/reception')">
            <el-icon :size="24"><Calendar /></el-icon>
            <span>接诊管理</span>
          </div>
          <div class="quick-item" @click="$router.push('/doctor/records')">
            <el-icon :size="24"><Document /></el-icon>
            <span>病历管理</span>
          </div>
          <div class="quick-item" @click="$router.push('/doctor/schedule')">
            <el-icon :size="24"><Clock /></el-icon>
            <span>我的排班</span>
          </div>
          <div class="quick-item" @click="$router.push('/doctor/record-summary')">
            <el-icon :size="24"><Cpu /></el-icon>
            <span>AI摘要</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Calendar, Document, Clock, Cpu } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { getReceptionListApi } from '@/api/appointment'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const receptionList = ref([])

const pendingList = computed(() => {
  return receptionList.value.filter(i => ['CHECKED_IN', 'PAID'].includes(i.appointmentStatus)).slice(0, 5)
})

const todayTotal = computed(() => receptionList.value.length)
const pendingCount = computed(() => receptionList.value.filter(i => ['PAID', 'CHECKED_IN'].includes(i.appointmentStatus)).length)
const completedCount = computed(() => receptionList.value.filter(i => i.appointmentStatus === 'COMPLETED').length)

const getReceptionList = async () => {
  loading.value = true
  try {
    const res = await getReceptionListApi()
    receptionList.value = res.data.records || res.data || []
  } finally {
    loading.value = false
  }
}

const goWriteRecord = (item) => {
  router.push({
    path: '/doctor/record/write',
    query: { appointmentId: item.id, patientName: item.patientName }
  })
}

onMounted(() => {
  getReceptionList()
})
</script>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  background: #fff;
  border-radius: var(--radius-base);
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--primary-color);
  margin-bottom: 6px;
}
.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.content-row {
  display: flex;
  gap: 20px;
}
.flex-1 { flex: 1; }
.quick-card { width: 320px; flex-shrink: 0; }

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.patient-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.patient-item {
  padding: 14px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
}
.patient-info {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.patient-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.visit-reason {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 10px;
}
.item-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-secondary);
  padding-top: 10px;
  border-top: 1px solid var(--border-light);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 0;
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all 0.2s;
  color: var(--text-regular);
}
.quick-item:hover {
  background: var(--bg-hover);
  color: var(--primary-color);
}
</style>
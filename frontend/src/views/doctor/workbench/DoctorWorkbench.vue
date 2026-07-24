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
          <el-button type="primary" link class="doctor-workbench-action" @click="navigateTo('/doctor/reception')">查看全部</el-button>
        </div>
        <PageState
          :loading="loading"
          :error="loadError"
          :empty="pendingList.length === 0"
          loading-text="正在加载待就诊患者..."
          empty-text="暂无待就诊患者"
          @retry="getReceptionList"
        >
          <div class="patient-list">
            <div v-for="item in pendingList" :key="item.id" class="patient-item">
              <div class="patient-info">
                <span class="patient-name">{{ patientDisplayName(item) }}</span>
                <el-tag size="small">{{ patientMeta(item) }}</el-tag>
              </div>
              <div class="item-footer">
                <span>第{{ item.queueNo }}号</span>
                <el-button
                  type="primary"
                  size="small"
                  class="doctor-workbench-action"
                  :aria-label="`为${item.patientName}去接诊`"
                  @click="goWriteRecord(item)"
                >
                  去接诊
                </el-button>
              </div>
            </div>
          </div>
        </PageState>
      </div>

      <!-- 快捷功能 -->
      <div class="card-box quick-card">
        <h3 class="section-title">快捷功能</h3>
        <div class="quick-grid">
          <button type="button" class="quick-item" @click="navigateTo('/doctor/reception')">
            <el-icon :size="24"><Calendar /></el-icon>
            <span>接诊管理</span>
          </button>
          <button type="button" class="quick-item" @click="navigateTo('/doctor/records')">
            <el-icon :size="24"><Document /></el-icon>
            <span>病历管理</span>
          </button>
          <button type="button" class="quick-item" @click="navigateTo('/doctor/schedule')">
            <el-icon :size="24"><Clock /></el-icon>
            <span>我的排班</span>
          </button>
          <button type="button" class="quick-item" @click="navigateTo('/doctor/record-summary')">
            <el-icon :size="24"><Cpu /></el-icon>
            <span>AI摘要</span>
          </button>
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
import PageState from '@/components/common/PageState.vue'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const loadError = ref('')
const receptionList = ref([])

const pendingList = computed(() => {
  return receptionList.value.filter(i => ['BOOKED', 'CHECKED_IN'].includes(i.appointmentStatus) && i.paymentStatus === 'PAID').slice(0, 5)
})

const todayTotal = computed(() => receptionList.value.length)
const pendingCount = computed(() => receptionList.value.filter(i => ['BOOKED', 'CHECKED_IN'].includes(i.appointmentStatus) && i.paymentStatus === 'PAID').length)
const completedCount = computed(() => receptionList.value.filter(i => i.appointmentStatus === 'COMPLETED').length)

const genderLabel = (gender) => gender === 'MALE' ? '男' : (gender === 'FEMALE' ? '女' : '-')

const patientDisplayName = (item) => item.patientName || item.name || item.patientNo || '未返回姓名'

const patientMeta = (item) => {
  const age = item.age == null ? '-' : `${item.age}岁`
  return `${genderLabel(item.gender)} ${age}`
}

const getReceptionList = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getReceptionListApi()
    receptionList.value = res.data.records || res.data || []
  } catch (e) {
    receptionList.value = []
    loadError.value = e?.response?.data?.message || e?.message || '待就诊患者加载失败'
  } finally {
    loading.value = false
  }
}

const goWriteRecord = (item) => {
  // 传 patientNo 供病历创建用（后端 CreateRequest.patientId 标 @NotBlank，缺则 400）
  router.push({
    path: '/doctor/record/write',
    query: {
      appointmentId: item.id,
      patientId: item.patientNo || item.patientId,
      patientName: patientDisplayName(item)
    }
  })
}

const navigateTo = (path) => {
  router.push(path)
}

onMounted(() => {
  getReceptionList()
})
</script>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  min-width: 0;
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
  align-items: flex-start;
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
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.patient-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.item-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--text-secondary);
  padding-top: 10px;
  border-top: 1px solid var(--border-light);
}

.doctor-workbench-action {
  min-width: var(--touch-target);
  min-height: var(--touch-target);
  touch-action: manipulation;
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
  justify-content: center;
  gap: 8px;
  min-height: 92px;
  padding: 16px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-base);
  background: transparent;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--text-regular);
  font: inherit;
}
.quick-item:hover,
.quick-item:focus-visible {
  background: var(--bg-hover);
  color: var(--primary-color);
  outline: none;
  border-color: rgba(64, 158, 255, .32);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, .12);
}
.quick-item :deep(.el-icon) {
  flex: 0 0 auto;
}

@media (max-width: 900px) {
  .content-row {
    flex-direction: column;
  }

  .flex-1,
  .quick-card {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .stat-card {
    padding: 16px;
  }

  .stat-value {
    font-size: 24px;
  }

  .section-header {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }

  .section-header :deep(.el-button) {
    width: 100%;
    min-height: var(--touch-target);
    margin-left: 0;
  }

  .patient-item,
  .item-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .item-footer .doctor-workbench-action {
    width: 100%;
  }
}

@media (max-width: 360px) {
  .stat-row,
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>

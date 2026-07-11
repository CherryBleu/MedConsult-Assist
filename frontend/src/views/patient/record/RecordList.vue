<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">我的病历</h2>
      </div>

      <div v-loading="loading" class="record-list">
        <div 
          v-for="item in recordList" 
          :key="item.id" 
          class="record-item"
          @click="goToDetail(item.id)"
        >
          <div class="record-header">
            <span class="record-dept">{{ item.deptName }}</span>
            <el-tag :type="getStatusType(MEDICAL_RECORD_STATUS, item.status)">
              {{ getStatusLabel(MEDICAL_RECORD_STATUS, item.status) }}
            </el-tag>
          </div>
          <div class="record-main">
            <div class="record-doctor">主治医生：{{ item.doctorName }}</div>
            <div class="record-complaint">主诉：{{ item.chiefComplaint }}</div>
            <div class="record-diagnosis">初步诊断：{{ item.initialDiagnosis }}</div>
          </div>
          <div class="record-footer">
            <span class="record-no">病历号：{{ item.recordNo }}</span>
            <span class="record-time">{{ item.createdAt }}</span>
          </div>
        </div>

        <el-empty v-if="!loading && recordList.length === 0" description="暂无病历记录" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { MEDICAL_RECORD_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { getRecordListApi } from '@/api/record'

const router = useRouter()
const loading = ref(false)
const recordList = ref([])

const getRecordList = async () => {
  loading.value = true
  try {
    const res = await getRecordListApi()
    // 后端分页返回 {records,total}；兼容 mock 返回数组
    recordList.value = res.data?.records ?? res.data ?? []
  } finally {
    loading.value = false
  }
}

const goToDetail = (id) => {
  router.push(`/patient/record/${id}`)
}

onMounted(() => {
  getRecordList()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.record-item {
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s;
}
.record-item:hover {
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.08);
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.record-dept {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.record-main {
  margin-bottom: 12px;
  line-height: 1.6;
}
.record-doctor {
  font-size: 13px;
  color: var(--text-regular);
  margin-bottom: 4px;
}
.record-complaint {
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.record-diagnosis {
  font-size: 13px;
  color: var(--primary-color);
}

.record-footer {
  display: flex;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
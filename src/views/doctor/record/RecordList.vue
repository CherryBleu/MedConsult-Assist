<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">病历管理</h2>
      </div>

      <div v-loading="loading" class="record-list">
        <div v-for="item in recordList" :key="item.id" class="record-item">
          <div class="record-left">
            <div class="record-header">
              <span class="patient-name">{{ item.patientName }}</span>
              <el-tag :type="getStatusType(MEDICAL_RECORD_STATUS, item.status)" size="small">
                {{ getStatusLabel(MEDICAL_RECORD_STATUS, item.status) }}
              </el-tag>
            </div>
            <div class="record-info">
              <span>病历号：{{ item.recordNo }}</span>
              <span>科室：{{ item.deptName }}</span>
            </div>
            <div class="record-complaint">主诉：{{ item.chiefComplaint }}</div>
          </div>
          <div class="record-right">
            <el-button size="small" type="primary" @click="viewDetail(item.id)">
              查看详情
            </el-button>
            <el-button v-if="item.status === 'DRAFT'" size="small" @click="editRecord(item)">
              继续编辑
            </el-button>
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

const getList = async () => {
  loading.value = true
  try {
    const res = await getRecordListApi()
    recordList.value = res.data
  } finally {
    loading.value = false
  }
}

const viewDetail = (id) => {
  router.push(`/doctor/record/${id}`)
}

const editRecord = (item) => {
  router.push({
    path: '/doctor/record/write',
    query: { recordId: item.id, patientName: item.patientName }
  })
}

onMounted(() => {
  getList()
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
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
}
.record-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.patient-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.record-info {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.record-complaint {
  font-size: 13px;
  color: var(--text-regular);
}
.record-right {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>
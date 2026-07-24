<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">我的病历</h2>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="recordList.length === 0"
        empty-text="暂无病历记录"
        @retry="getRecordList"
      >
        <div class="record-list" aria-label="我的病历列表">
          <button
            v-for="item in recordList"
            :key="item.id"
            type="button"
            class="record-item"
            :aria-label="`${item.recordNo || '未编号病历'}，${item.deptName || '未知科室'}，${getStatusLabel(MEDICAL_RECORD_STATUS, item.status)}，查看详情`"
            @click="goToDetail(item.id)"
          >
            <div class="record-header">
              <span class="record-dept">{{ item.deptName || '未知科室' }}</span>
              <el-tag :type="getStatusType(MEDICAL_RECORD_STATUS, item.status)">
                {{ getStatusLabel(MEDICAL_RECORD_STATUS, item.status) }}
              </el-tag>
            </div>
            <div class="record-main">
              <div class="record-doctor">主治医生：{{ item.doctorName || '-' }}</div>
              <div class="record-complaint">主诉：{{ item.chiefComplaint || '-' }}</div>
              <div class="record-diagnosis">初步诊断：{{ item.diagnosisText || '-' }}</div>
            </div>
            <div class="record-footer">
              <span class="record-no">病历号：{{ item.recordNo || '-' }}</span>
              <span class="record-time">{{ item.createdAt || '-' }}</span>
            </div>
          </button>
        </div>
      </PageState>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { MEDICAL_RECORD_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { getRecordListApi } from '@/api/record'
import PageState from '@/components/common/PageState.vue'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const recordList = ref([])

const getRecordList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getRecordListApi()
    // 后端分页返回 {records,total}；兼容 mock 返回数组
    recordList.value = res.data?.records ?? res.data ?? []
  } catch (e) {
    recordList.value = []
    errorMessage.value = e?.message || '病历列表加载失败，请重试'
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
  display: block;
  width: 100%;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  padding: 16px;
  background: rgba(255, 255, 255, .9);
  color: inherit;
  cursor: pointer;
  font: inherit;
  text-align: left;
  touch-action: manipulation;
  transition: border-color 0.2s, box-shadow 0.2s, background-color 0.2s;
}
.record-item:hover {
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.08);
}
.record-item:focus-visible {
  outline: 3px solid rgba(22, 119, 255, 0.28);
  outline-offset: 2px;
  border-color: var(--primary-color);
}
.record-item:active {
  background: rgba(22, 119, 255, 0.04);
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
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
  overflow-wrap: anywhere;
}
.record-complaint {
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 4px;
  overflow-wrap: anywhere;
}
.record-diagnosis {
  font-size: 13px;
  color: var(--primary-color);
  overflow-wrap: anywhere;
}

.record-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
  color: var(--text-secondary);
}

.record-no,
.record-time {
  min-width: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .record-item {
    min-height: var(--touch-target);
    padding: 16px;
  }

  .record-header,
  .record-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .record-main {
    line-height: 1.7;
  }
}
</style>

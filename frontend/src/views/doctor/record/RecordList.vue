<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">病历管理</h2>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="recordList.length === 0"
        loading-text="正在加载病历列表..."
        empty-text="暂无病历记录"
        @retry="getList"
      >
        <section class="record-list" aria-label="医生病历列表">
          <article
            v-for="item in recordList"
            :key="item.id || item.recordId || item.recordNo"
            class="record-item"
            data-testid="doctor-record-card"
          >
            <div class="record-main">
              <div class="record-header">
                <div>
                  <p class="patient-name">{{ patientDisplay(item) }}</p>
                  <p class="record-no">{{ recordIdentity(item) }}</p>
                </div>
                <el-tag :type="getStatusType(MEDICAL_RECORD_STATUS, item.status)" size="small">
                  {{ getStatusLabel(MEDICAL_RECORD_STATUS, item.status) }}
                </el-tag>
              </div>

              <dl class="record-info">
                <div>
                  <dt>科室</dt>
                  <dd>{{ item.deptName || '-' }}</dd>
                </div>
                <div>
                  <dt>主诉</dt>
                  <dd>{{ item.chiefComplaint || '-' }}</dd>
                </div>
              </dl>
            </div>

            <div class="record-actions">
              <el-button
                type="primary"
                plain
                class="doctor-record-action"
                :aria-label="`查看 ${recordIdentity(item)} 病历详情`"
                @click="viewDetail(item.id)"
              >
                查看详情
              </el-button>
              <el-button
                v-if="item.status === 'DRAFT'"
                plain
                class="doctor-record-action"
                :aria-label="`继续编辑 ${recordIdentity(item)} 病历`"
                @click="editRecord(item)"
              >
                继续编辑
              </el-button>
            </div>
          </article>
        </section>
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

const asRecords = (data) => data?.records ?? data?.items ?? (Array.isArray(data) ? data : [])
const getErrorMessage = (error, fallback) => error?.response?.data?.message || error?.message || fallback
const recordIdentity = (item) => item.recordNo || item.recordId || item.id || '-'
const patientDisplay = (item) => {
  if (item.patientName) return item.patientName
  if (item.patientId) return `患者 ${item.patientId}`
  return '未知患者'
}

const getList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getRecordListApi()
    recordList.value = asRecords(res.data)
  } catch (e) {
    recordList.value = []
    errorMessage.value = getErrorMessage(e, '病历列表加载失败，请重试')
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
  display: grid;
  gap: 12px;
  min-width: 0;
}

.record-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  background: rgba(255, 255, 255, .88);
}

.record-main {
  min-width: 0;
}

.record-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.record-header > div {
  min-width: 0;
}

.patient-name,
.record-no {
  margin: 0;
}

.patient-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

.record-no {
  margin-top: 4px;
  font-family: var(--font-mono, monospace);
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.record-info {
  display: grid;
  gap: 8px;
  margin: 0;
}

.record-info div {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 10px;
}

.record-info dt,
.record-info dd {
  margin: 0;
}

.record-info dt {
  color: var(--text-secondary);
}

.record-info dd {
  min-width: 0;
  color: var(--text-regular);
  overflow-wrap: anywhere;
}

.record-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.doctor-record-action {
  min-height: var(--touch-target);
  min-width: 88px;
  margin-left: 0;
  touch-action: manipulation;
}

.doctor-record-action:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

@media (max-width: 768px) {
  .record-item {
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .record-header {
    flex-direction: column;
  }

  .record-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .doctor-record-action {
    width: 100%;
  }
}
</style>

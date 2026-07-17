<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span class="page-title">接诊管理</span>
      </template>

      <div class="stat-grid">
        <div class="stat-card stat-pending">
          <div class="stat-num">{{ stats.pending }}</div>
          <div class="stat-label">今日待诊</div>
        </div>
        <div class="stat-card stat-in-progress">
          <div class="stat-num">{{ stats.inProgress }}</div>
          <div class="stat-label">就诊中</div>
        </div>
        <div class="stat-card stat-completed">
          <div class="stat-num">{{ stats.completed }}</div>
          <div class="stat-label">已完成</div>
        </div>
      </div>

      <el-radio-group v-model="activeStatus" class="status-filter" @change="handleStatusChange">
        <el-radio-button value="ALL">全部</el-radio-button>
        <el-radio-button value="PENDING">待就诊</el-radio-button>
        <el-radio-button value="IN_PROGRESS">就诊中</el-radio-button>
        <el-radio-button value="COMPLETED">已完成</el-radio-button>
        <el-radio-button value="NO_SHOW">爽约</el-radio-button>
      </el-radio-group>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="tableData.length === 0"
        empty-text="暂无接诊记录"
        @retry="getList"
      >
        <ResponsiveTable aria-label="接诊管理列表">
          <template #table>
            <el-table :data="tableData" border stripe class="reception-table">
              <el-table-column type="index" label="序号" width="60" align="center" :index="indexMethod" />
              <el-table-column label="患者信息" width="160" align="center">
                <template #default="{ row }">
                  <div class="patient-cell">
                    <div class="patient-name">{{ patientDisplayName(row) }}</div>
                    <div class="patient-no" v-if="row.patientNo && row.patientName">{{ row.patientNo }}</div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="gender" label="性别" width="70" align="center">
                <template #default="{ row }">{{ genderLabel(row.gender) }}</template>
              </el-table-column>
              <el-table-column label="年龄" width="70" align="center">
                <template #default="{ row }">{{ row.age ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="预约时段" width="180" align="center">
                <template #default="{ row }">{{ row.scheduleDate }} {{ periodLabel(row.period) }}</template>
              </el-table-column>
              <el-table-column prop="queueNo" label="队列号" width="80" align="center" />
              <el-table-column prop="fee" label="挂号费" width="90" align="center">
                <template #default="{ row }">¥{{ row.fee }}</template>
              </el-table-column>
              <el-table-column label="支付状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="getPaymentTagType(row.paymentStatus)" size="small">
                    {{ getPaymentLabel(row.paymentStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="预约状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="getStatusType(APPOINTMENT_STATUS, row.appointmentStatus)" size="small">
                    {{ getStatusLabel(APPOINTMENT_STATUS, row.appointmentStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="visitReason" label="主诉" min-width="150" show-overflow-tooltip />
              <el-table-column label="操作" width="220" align="center" fixed="right">
                <template #default="{ row }">
                  <template v-if="canStartOrMarkNoShow(row)">
                    <el-button
                      v-if="!isExpired(row)"
                      type="success"
                      size="small"
                      @click="handleStartVisit(row.id)"
                    >
                      开始就诊
                    </el-button>
                    <el-button
                      v-else
                      type="danger"
                      size="small"
                      @click="handleMarkNoShow(row.id)"
                    >
                      标记爽约
                    </el-button>
                  </template>
                  <template v-else-if="row.appointmentStatus === 'IN_PROGRESS'">
                    <el-button type="primary" size="small" @click="handleEndVisit(row.id, row)">
                      完成接诊
                    </el-button>
                    <el-button size="small" @click="goWriteRecord(row)">
                      写病历
                    </el-button>
                  </template>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template #card>
            <article
              v-for="row in tableData"
              :key="row.id || row.appointmentNo"
              class="reception-card"
              data-testid="responsive-reception-card"
            >
              <div class="reception-card__header">
                <div>
                  <p class="reception-card__name">{{ patientDisplayName(row) }}</p>
                  <p class="reception-card__meta">{{ row.patientNo || row.appointmentNo || '-' }}</p>
                </div>
                <el-tag :type="getStatusType(APPOINTMENT_STATUS, row.appointmentStatus)" size="small">
                  {{ getStatusLabel(APPOINTMENT_STATUS, row.appointmentStatus) }}
                </el-tag>
              </div>

              <dl class="reception-card__fields">
                <div>
                  <dt>性别/年龄</dt>
                  <dd>{{ genderLabel(row.gender) }} · {{ row.age ?? '-' }}岁</dd>
                </div>
                <div>
                  <dt>预约时段</dt>
                  <dd>{{ row.scheduleDate }} {{ periodLabel(row.period) }}</dd>
                </div>
                <div>
                  <dt>队列/费用</dt>
                  <dd>{{ row.queueNo || '-' }}号 · ¥{{ row.fee }}</dd>
                </div>
                <div>
                  <dt>支付状态</dt>
                  <dd>{{ getPaymentLabel(row.paymentStatus) }}</dd>
                </div>
              </dl>

              <p v-if="row.visitReason" class="reception-card__reason">主诉：{{ row.visitReason }}</p>

              <div class="reception-card__actions">
                <template v-if="canStartOrMarkNoShow(row)">
                  <el-button
                    v-if="!isExpired(row)"
                    type="success"
                    plain
                    @click="handleStartVisit(row.id)"
                  >
                    开始就诊
                  </el-button>
                  <el-button
                    v-else
                    type="danger"
                    plain
                    @click="handleMarkNoShow(row.id)"
                  >
                    标记爽约
                  </el-button>
                </template>
                <template v-else-if="row.appointmentStatus === 'IN_PROGRESS'">
                  <el-button type="primary" plain @click="handleEndVisit(row.id, row)">完成接诊</el-button>
                  <el-button plain @click="goWriteRecord(row)">写病历</el-button>
                </template>
                <span v-else class="reception-card__no-action">暂无可执行操作</span>
              </div>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>

      <el-pagination
        v-if="pagination.total > 0 && !loading && !errorMessage"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { APPOINTMENT_STATUS, getStatusLabel, getStatusType } from '@/constants'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'
import {
  getReceptionListApi,
  startVisitApi,
  endVisitApi,
  markNoShowApi
} from '@/api/appointment'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const allData = ref([])

const activeStatus = ref('ALL')

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const stats = computed(() => {
  const today = dayjs().format('YYYY-MM-DD')
  const todayList = allData.value.filter(i => i.scheduleDate === today && i.appointmentStatus !== 'CANCELLED')
  return {
    pending: todayList.filter(i => ['BOOKED', 'CHECKED_IN'].includes(i.appointmentStatus)).length,
    inProgress: todayList.filter(i => i.appointmentStatus === 'IN_PROGRESS').length,
    completed: todayList.filter(i => i.appointmentStatus === 'COMPLETED').length
  }
})

const filteredData = computed(() => {
  // 已取消的预约不展示在医生接诊列表中
  let list = allData.value.filter(i => i.appointmentStatus !== 'CANCELLED')
  if (activeStatus.value === 'ALL') return list
  if (activeStatus.value === 'PENDING') {
    return list.filter(i => ['BOOKED', 'CHECKED_IN'].includes(i.appointmentStatus))
  }
  return list.filter(i => i.appointmentStatus === activeStatus.value)
})

watch(filteredData, (list) => {
  pagination.total = list.length
  const maxPage = Math.max(1, Math.ceil(list.length / pagination.pageSize))
  if (pagination.pageNum > maxPage) {
    pagination.pageNum = maxPage
  }
})

const tableData = computed(() => {
  const start = (pagination.pageNum - 1) * pagination.pageSize
  const end = start + pagination.pageSize
  return filteredData.value.slice(start, end)
})

const getPaymentLabel = (status) => {
  const map = { PAID: '已支付', UNPAID: '待支付', REFUNDING: '退款中', REFUNDED: '已退款' }
  return map[status] || status
}

const getPaymentTagType = (status) => {
  const map = { PAID: 'success', UNPAID: 'warning', REFUNDING: 'warning', REFUNDED: 'info' }
  return map[status] || 'info'
}

const genderLabel = (gender) => gender === 'MALE' ? '男' : (gender === 'FEMALE' ? '女' : '-')

const periodLabel = (period) => {
  const map = { MORNING: '上午', AFTERNOON: '下午', EVENING: '晚上' }
  return map[period] || period || '-'
}

const patientDisplayName = (row) => row.patientName || row.name || row.patientNo || '未知'

const canStartOrMarkNoShow = (row) => ['PAID', 'CHECKED_IN'].includes(row.appointmentStatus)

const isExpired = (row) => {
  if (!canStartOrMarkNoShow(row)) return false
  return dayjs(row.scheduleDate).isBefore(dayjs(), 'day')
}

const indexMethod = (index) => {
  return (pagination.pageNum - 1) * pagination.pageSize + index + 1
}

const getList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getReceptionListApi({ pageNum: 1, pageSize: 1000 })
    allData.value = res.data.records || []
  } catch (error) {
    errorMessage.value = error?.message || '接诊列表加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const handleStatusChange = () => {
  pagination.pageNum = 1
}

const handleSizeChange = () => {
  pagination.pageNum = 1
}

const handleCurrentChange = () => {
}

const handleStartVisit = async (id) => {
  try {
    await startVisitApi(id)
    ElMessage.success('已开始就诊')
    getList()
  } catch (e) {
    ElMessage.error(e?.message || '开始就诊失败')
  }
}

const handleEndVisit = async (id, row) => {
  try {
    const action = await ElMessageBox({
      title: '完成接诊确认',
      message: '完成接诊前请确保已完成病历书写并归档。未写病历将无法在病历列表中查询到本次就诊记录。',
      confirmButtonText: '已写病历，完成接诊',
      cancelButtonText: '去写病历',
      distinguishCancelAndClose: true,
      type: 'warning',
      showClose: false
    }).catch(e => e)
    if (action === 'cancel') {
      // 用户点击"去写病历" → 跳转到写病历页
      goWriteRecord(row)
      return
    }
    await endVisitApi(id)
    ElMessage.success('接诊已完成')
    getList()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.message || '完成接诊失败')
    }
  }
}

const handleMarkNoShow = async (id) => {
  try {
    await ElMessageBox.confirm('确定将该预约标记为爽约吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await markNoShowApi(id)
    ElMessage.success('已标记为爽约')
    getList()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.message || '标记爽约失败')
    }
  }
}

const goWriteRecord = (row) => {
  // 传 patientNo 供病历创建用（后端 CreateRequest.patientId 标 @NotBlank，缺则 400）。
  // row.patientNo 来自后端预约列表（appointment.patient_no 冗余字段）。
  router.push({
    path: '/doctor/record/write',
    query: {
      appointmentId: row.id || row.appointmentId,
      patientId: row.patientNo || row.patientId,
      patientName: row.patientName || row.patientNo
    }
  })
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  color: #fff;
}

.stat-pending {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
}

.stat-in-progress {
  background: linear-gradient(135deg, #3b82f6, #60a5fa);
}

.stat-completed {
  background: linear-gradient(135deg, #10b981, #34d399);
}

.stat-num {
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
  margin-bottom: 6px;
}

.stat-label {
  font-size: 14px;
  opacity: 0.9;
}

.status-filter {
  margin-bottom: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

:deep(.status-filter .el-radio-button__inner) {
  min-height: var(--touch-target);
  border-left: 1px solid var(--el-border-color);
  border-radius: var(--radius-base);
  display: inline-flex;
  align-items: center;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.reception-table {
  width: 100%;
  margin-top: 16px;
}

.patient-cell {
  text-align: center;
}
.patient-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 2px;
}
.patient-no {
  font-size: 12px;
  color: var(--text-secondary);
}

.reception-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(59, 130, 246, .18);
  border-radius: var(--radius-lg);
  background:
    linear-gradient(145deg, rgba(239, 246, 255, .88), rgba(255, 255, 255, .96));
  box-shadow: 0 14px 32px rgba(30, 64, 175, .08);
}

.reception-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.reception-card__name,
.reception-card__meta,
.reception-card__reason {
  margin: 0;
}

.reception-card__name {
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.reception-card__meta {
  margin-top: 4px;
  font-family: var(--font-mono, monospace);
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.reception-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}

.reception-card__fields div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(59, 130, 246, .12);
}

.reception-card__fields dt,
.reception-card__fields dd {
  margin: 0;
}

.reception-card__fields dt {
  flex: 0 0 auto;
  color: var(--text-secondary);
}

.reception-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
  text-align: right;
}

.reception-card__reason {
  padding: 10px 12px;
  border-radius: var(--radius-base);
  background: rgba(255, 255, 255, .72);
  color: var(--text-secondary);
  line-height: 1.6;
}

.reception-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.reception-card__actions .el-button {
  min-height: var(--touch-target);
  margin-left: 0;
}

.reception-card__no-action {
  grid-column: 1 / -1;
  min-height: var(--touch-target);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

@media (max-width: 640px) {
  .stat-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .stat-card {
    padding: 16px;
  }

  .status-filter {
    width: 100%;
  }

  :deep(.status-filter .el-radio-button) {
    flex: 1 1 calc(50% - 8px);
  }

  :deep(.status-filter .el-radio-button__inner) {
    width: 100%;
    justify-content: center;
  }

  .pagination {
    justify-content: center;
    overflow-x: auto;
  }
}
</style>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span class="page-title">接诊管理</span>
      </template>

      <el-row :gutter="16" class="stat-row">
        <el-col :span="8">
          <div class="stat-card stat-pending">
            <div class="stat-num">{{ stats.pending }}</div>
            <div class="stat-label">今日待诊</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card stat-in-progress">
            <div class="stat-num">{{ stats.inProgress }}</div>
            <div class="stat-label">就诊中</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card stat-completed">
            <div class="stat-num">{{ stats.completed }}</div>
            <div class="stat-label">已完成</div>
          </div>
        </el-col>
      </el-row>

      <el-radio-group v-model="activeStatus" class="status-filter" @change="handleStatusChange">
        <el-radio-button label="ALL">全部</el-radio-button>
        <el-radio-button label="PENDING">待就诊</el-radio-button>
        <el-radio-button label="IN_PROGRESS">就诊中</el-radio-button>
        <el-radio-button label="COMPLETED">已完成</el-radio-button>
        <el-radio-button label="NO_SHOW">爽约</el-radio-button>
      </el-radio-group>

      <el-table v-loading="loading" :data="tableData" border stripe style="width: 100%; margin-top: 16px">
        <el-table-column type="index" label="序号" width="60" align="center" :index="indexMethod" />
        <el-table-column label="患者姓名" width="120" align="center">
          <template #default="{ row }">
            {{ row.patientName || row.patientNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="gender" label="性别" width="70" align="center">
          <template #default="{ row }">
            {{ row.gender === 'MALE' ? '男' : (row.gender === 'FEMALE' ? '女' : '-') }}
          </template>
        </el-table-column>
        <el-table-column label="年龄" width="70" align="center">
          <template #default="{ row }">
            {{ row.age ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="预约时段" width="180" align="center">
          <template #default="{ row }">
            {{ row.scheduleDate }} {{ row.period === 'MORNING' ? '上午' : '下午' }}
          </template>
        </el-table-column>
        <el-table-column prop="queueNo" label="队列号" width="80" align="center" />
        <el-table-column prop="fee" label="挂号费" width="90" align="center">
          <template #default="{ row }">
            ¥{{ row.fee }}
          </template>
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
            <template v-if="['PAID', 'CHECKED_IN'].includes(row.appointmentStatus)">
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
              <el-button type="primary" size="small" @click="handleEndVisit(row.id)">
                完成接诊
              </el-button>
              <el-button size="small" @click="goWriteRecord(row.id)">
                写病历
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
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
import {
  getReceptionListApi,
  startVisitApi,
  endVisitApi,
  markNoShowApi
} from '@/api/appointment'

const router = useRouter()
const loading = ref(false)
const allData = ref([])

const activeStatus = ref('ALL')

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const stats = computed(() => {
  const today = dayjs().format('YYYY-MM-DD')
  const todayList = allData.value.filter(i => i.scheduleDate === today)
  return {
    pending: todayList.filter(i => ['BOOKED', 'CHECKED_IN'].includes(i.appointmentStatus)).length,
    inProgress: todayList.filter(i => i.appointmentStatus === 'IN_PROGRESS').length,
    completed: todayList.filter(i => i.appointmentStatus === 'COMPLETED').length
  }
})

const filteredData = computed(() => {
  if (activeStatus.value === 'ALL') return allData.value
  if (activeStatus.value === 'PENDING') {
    return allData.value.filter(i => ['BOOKED', 'CHECKED_IN'].includes(i.appointmentStatus))
  }
  return allData.value.filter(i => i.appointmentStatus === activeStatus.value)
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
  const map = { PAID: '已支付', UNPAID: '待支付', REFUNDED: '已退款' }
  return map[status] || status
}

const getPaymentTagType = (status) => {
  const map = { PAID: 'success', UNPAID: 'warning', REFUNDED: 'info' }
  return map[status] || 'info'
}

const isExpired = (row) => {
  if (!['PAID', 'CHECKED_IN'].includes(row.appointmentStatus)) return false
  return dayjs(row.scheduleDate).isBefore(dayjs(), 'day')
}

const indexMethod = (index) => {
  return (pagination.pageNum - 1) * pagination.pageSize + index + 1
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getReceptionListApi({ pageNum: 1, pageSize: 1000 })
    allData.value = res.data.records || []
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
  } catch (e) {}
}

const handleEndVisit = async (id) => {
  try {
    await ElMessageBox.confirm('确定完成本次接诊吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })
    await endVisitApi(id)
    ElMessage.success('接诊已完成')
    getList()
  } catch (e) {}
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
  } catch (e) {}
}

const goWriteRecord = (id) => {
  router.push({ path: '/doctor/record/write', query: { appointmentId: id } })
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

.stat-row {
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
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>

<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">我的预约</h2>
        <el-button type="primary" @click="$router.push('/patient/appointment/department')">
          新增预约
        </el-button>
      </div>

      <el-tabs v-model="activeTab" class="appointment-tabs" @tab-change="fetchList">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="待就诊" name="BOOKED" />
        <el-tab-pane label="已签到" name="CHECKED_IN" />
        <el-tab-pane label="就诊中" name="IN_PROGRESS" />
        <el-tab-pane label="已完成" name="COMPLETED" />
        <el-tab-pane label="已取消" name="CANCELLED" />
      </el-tabs>

      <div v-loading="loading" class="appointment-list">
        <div v-for="item in list" :key="item.id" class="appointment-item">
          <div class="item-header">
            <div class="header-left">
              <span class="dept-name">{{ item.deptName }}</span>
              <el-tag :type="getStatusType(APPOINTMENT_STATUS, item.appointmentStatus)" size="small">
                {{ getStatusLabel(APPOINTMENT_STATUS, item.appointmentStatus) }}
              </el-tag>
              <el-tag v-if="item.paymentStatus !== 'PAID' && item.appointmentStatus !== 'CANCELLED'" type="warning" size="small">
                待支付
              </el-tag>
            </div>
            <span class="fee">挂号费：¥{{ item.fee }}</span>
          </div>
          <div class="item-body">
            <div class="doctor-info">
              <el-avatar :size="44">{{ (item.doctorName || '?').charAt(0) }}</el-avatar>
              <div>
                <div class="doctor-name">
                  {{ item.doctorName }}
                  <span class="apt-no">{{ item.appointmentNo }}</span>
                </div>
                <div class="time-row">
                  <el-icon><Calendar /></el-icon>
                  {{ item.scheduleDate }} {{ item.period === 'MORNING' ? '上午' : item.period === 'AFTERNOON' ? '下午' : '全天' }}
                  <template v-if="item.queueNo > 0">
                    &nbsp;·&nbsp;第 <strong style="color:var(--primary-color)">{{ item.queueNo }}</strong> 号
                  </template>
                </div>
                <div v-if="item.visitReason" class="reason">主诉：{{ item.visitReason }}</div>
              </div>
            </div>
            <div class="item-actions">
              <el-button size="small" @click="showDetail(item)">查看详情</el-button>
              <el-button
                v-if="item.appointmentStatus === 'BOOKED' && item.paymentStatus === 'UNPAID'"
                size="small"
                type="warning"
                @click="payAppointment(item)"
              >
                立即支付
              </el-button>
              <el-button
                v-if="item.appointmentStatus === 'BOOKED' && item.paymentStatus === 'PAID'"
                size="small"
                type="primary"
                @click="checkIn(item)"
              >
                签到
              </el-button>
              <el-button
                v-if="['BOOKED', 'CHECKED_IN'].includes(item.appointmentStatus)"
                size="small"
                type="danger"
                plain
                @click="cancelAppointment(item)"
              >
                取消预约
              </el-button>
            </div>
          </div>
        </div>

        <el-empty v-if="!loading && list.length === 0" description="暂无预约记录" />
      </div>

      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="预约详情" width="560px">
      <el-descriptions v-if="currentDetail" :column="2" border size="small">
        <el-descriptions-item label="预约编号" :span="2">{{ currentDetail.appointmentNo }}</el-descriptions-item>
        <el-descriptions-item label="科室">{{ currentDetail.deptName }}</el-descriptions-item>
        <el-descriptions-item label="医生">{{ currentDetail.doctorName }}</el-descriptions-item>
        <el-descriptions-item label="就诊日期">{{ currentDetail.scheduleDate }}</el-descriptions-item>
        <el-descriptions-item label="时段">{{ currentDetail.period === 'MORNING' ? '上午' : '下午' }}</el-descriptions-item>
        <el-descriptions-item label="序号" v-if="currentDetail.queueNo">第{{ currentDetail.queueNo }}号</el-descriptions-item>
        <el-descriptions-item label="挂号费">¥{{ currentDetail.fee }}</el-descriptions-item>
        <el-descriptions-item label="支付状态">
          <el-tag :type="currentDetail.paymentStatus === 'PAID' ? 'success' : 'warning'" size="small">
            {{ currentDetail.paymentStatus === 'PAID' ? '已支付' : currentDetail.paymentStatus === 'REFUNDED' ? '已退款' : '待支付' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="预约状态" :span="2">
          <el-tag :type="getStatusType(APPOINTMENT_STATUS, currentDetail.appointmentStatus)" size="small">
            {{ getStatusLabel(APPOINTMENT_STATUS, currentDetail.appointmentStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="主诉" :span="2">{{ currentDetail.visitReason || '无' }}</el-descriptions-item>
        <el-descriptions-item v-if="currentDetail.cancelReason" label="取消原因" :span="2">{{ currentDetail.cancelReason }}</el-descriptions-item>
        <el-descriptions-item label="预约时间" :span="2">{{ currentDetail.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Calendar } from '@element-plus/icons-vue'
import { APPOINTMENT_STATUS, getStatusLabel, getStatusType } from '@/constants'
import {
  getAppointmentListApi, cancelAppointmentApi, payAppointmentApi,
  checkInAppointmentApi, getAppointmentDetailApi
} from '@/api/appointment'

const activeTab = ref('all')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const detailVisible = ref(false)
const currentDetail = ref(null)

const fetchList = async () => {
  loading.value = true
  try {
    const params = { page: pageNum.value, pageSize: pageSize.value }
    if (activeTab.value !== 'all') params.status = activeTab.value
    const res = await getAppointmentListApi(params)
    list.value = res.data.records || res.data || []
    total.value = res.data.total || list.value.length
  } finally {
    loading.value = false
  }
}

const showDetail = async (item) => {
  try {
    const res = await getAppointmentDetailApi(item.id)
    currentDetail.value = res.data
    detailVisible.value = true
  } catch (e) {}
}

const payAppointment = (item) => {
  ElMessageBox.confirm(`将支付挂号费 ¥${item.fee}，是否继续？`, '支付确认', {
    confirmButtonText: '确认支付',
    cancelButtonText: '取消',
    type: 'info'
  }).then(async () => {
    // paymentStatus @NotBlank，paidAmount 取挂号费（后端 PaymentUpdateRequest 接受）
    await payAppointmentApi(item.id, { paidAmount: item.fee })
    ElMessage.success('支付成功')
    fetchList()
  }).catch(() => {})
}

const checkIn = (item) => {
  ElMessageBox.confirm('签到后将进入候诊队列，是否确认签到？', '签到确认', {
    confirmButtonText: '确认签到',
    cancelButtonText: '取消',
    type: 'info'
  }).then(async () => {
    await checkInAppointmentApi(item.id)
    ElMessage.success('签到成功，请等待叫号')
    fetchList()
  }).catch(() => {})
}

const cancelAppointment = (item) => {
  ElMessageBox.confirm('确定要取消该预约吗？' + (item.paymentStatus === 'PAID' ? '已支付费用将原路退回。' : ''), '提示', {
    confirmButtonText: '确定取消',
    cancelButtonText: '再想想',
    type: 'warning'
  }).then(async () => {
    await cancelAppointmentApi(item.id)
    ElMessage.success('预约已取消')
    fetchList()
  }).catch(() => {})
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.appointment-tabs { margin-bottom: 16px; }
.appointment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.appointment-item {
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  padding: 16px;
  transition: box-shadow .2s;
}
.appointment-item:hover { box-shadow: 0 2px 12px rgba(0,0,0,.08); }
.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}
.header-left { display: flex; align-items: center; gap: 8px; }
.dept-name { font-size: 16px; font-weight: 600; color: var(--text-primary); }
.fee { font-size: 14px; color: var(--warning-color, #e6a23c); font-weight: 500; }
.item-body { display: flex; justify-content: space-between; align-items: center; }
.doctor-info { display: flex; gap: 12px; align-items: flex-start; }
.doctor-name { font-size: 15px; color: var(--text-primary); margin-bottom: 4px; }
.apt-no { font-size: 12px; color: var(--text-secondary); font-weight: normal; margin-left: 8px; }
.time-row { font-size: 13px; color: var(--text-secondary); display: flex; align-items: center; gap: 4px; }
.reason { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
.item-actions { display: flex; gap: 8px; flex-shrink: 0; }
.pagination-wrap { margin-top: 20px; display: flex; justify-content: flex-end; }
</style>

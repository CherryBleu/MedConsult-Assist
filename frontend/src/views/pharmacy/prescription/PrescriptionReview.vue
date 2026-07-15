<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">处方审核</h2>
        <div class="header-actions">
          <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 160px" @change="handleFilterChange">
            <el-option label="待审方" value="PENDING_REVIEW" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已缴费" value="PAID" />
            <el-option label="已发药" value="DISPENSED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
          <el-button type="primary" @click="fetchList">刷新</el-button>
        </div>
      </div>

      <el-table :data="prescriptionList" v-loading="loading" border stripe>
        <el-table-column prop="prescriptionId" label="处方编号" width="170" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总金额" width="110" align="right">
          <template #default="{ row }">
            <span v-if="row.totalFee != null">¥ {{ Number(row.totalFee).toFixed(2) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="支付状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.paymentStatus" size="small" effect="plain" :type="getPayType(row.paymentStatus)">
              {{ getPayLabel(row.paymentStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING_REVIEW'" size="small" link type="success" @click="openReview(row, 'APPROVE')">通过</el-button>
            <el-button v-if="row.status === 'PENDING_REVIEW'" size="small" link type="danger" @click="openReview(row, 'REJECT')">驳回</el-button>
            <el-button v-if="row.status === 'APPROVED' || row.status === 'PAID'" size="small" link type="warning" @click="openDispense(row)">发药</el-button>
            <el-button v-if="row.status === 'DISPENSED'" size="small" link type="primary" @click="handleComplete(row)">完成</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </div>

    <!-- 处方详情 -->
    <el-dialog v-model="detailVisible" title="处方详情" width="720px">
      <div v-loading="detailLoading">
        <el-descriptions :column="2" border v-if="detail">
          <el-descriptions-item label="处方编号">{{ detail.prescriptionId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(detail.status)">{{ getStatusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总金额">¥ {{ detail.totalFee != null ? Number(detail.totalFee).toFixed(2) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付状态">{{ getPayLabel(detail.paymentStatus) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ getSourceLabel(detail.source) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.reviewComment" label="审方意见" :span="2">{{ detail.reviewComment }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectReason" label="驳回原因" :span="2">{{ detail.rejectReason }}</el-descriptions-item>
        </el-descriptions>

        <h4 style="margin: 16px 0 8px">处方明细</h4>
        <el-table :data="detail?.items || []" border size="small">
          <el-table-column prop="drugName" label="药品名称" min-width="140" />
          <el-table-column prop="specification" label="规格" width="110" />
          <el-table-column prop="dosage" label="用法用量" width="100" />
          <el-table-column prop="frequency" label="频次" width="100" />
          <el-table-column prop="days" label="天数" width="70" align="center" />
          <el-table-column label="数量" width="100" align="right">
            <template #default="{ row }">{{ row.quantity }} {{ row.unit }}</template>
          </el-table-column>
          <el-table-column label="小计" width="90" align="right">
            <template #default="{ row }">{{ row.subtotal != null ? '¥' + Number(row.subtotal).toFixed(2) : '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 审方对话框 -->
    <el-dialog v-model="reviewDialogVisible" :title="reviewForm.action === 'APPROVE' ? '审方通过' : '审方驳回'" width="480px">
      <el-form :model="reviewForm" label-width="90px">
        <el-form-item label="处方编号">
          <span>{{ currentPrescription?.prescriptionId }}</span>
        </el-form-item>
        <el-form-item label="审方意见">
          <el-input v-model="reviewForm.reviewComment" type="textarea" :rows="2" placeholder="审方意见（可选）" />
        </el-form-item>
        <el-form-item v-if="reviewForm.action === 'REJECT'" label="驳回原因" required>
          <el-input v-model="reviewForm.rejectReason" type="textarea" :rows="2" placeholder="请填写驳回原因（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleReview">确认</el-button>
      </template>
    </el-dialog>

    <!-- 调剂发药对话框 -->
    <el-dialog v-model="dispenseDialogVisible" title="调剂发药" width="480px">
      <el-alert title="发药将触发药品库存 FEFO 出库，请确认处方明细无误。" type="warning" :closable="false" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="处方编号">
          <span>{{ currentPrescription?.prescriptionId }}</span>
        </el-form-item>
        <el-form-item label="发药药师">
          <span>{{ userStore.userInfo?.name || '当前药师' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispenseDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="submitting" @click="handleDispense">确认发药</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPrescriptionListApi, getPrescriptionDetailApi,
  reviewPrescriptionApi, dispensePrescriptionApi, completePrescriptionApi
} from '@/api/prescription'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()

const loading = ref(false)
const prescriptionList = ref([])
const total = ref(0)
const statusFilter = ref('PENDING_REVIEW')

const pagination = reactive({ page: 1, pageSize: 10 })

const fetchList = async () => {
  loading.value = true
  try {
    const params = { page: pagination.page, pageSize: pagination.pageSize }
    if (statusFilter.value) params.status = statusFilter.value
    const res = await getPrescriptionListApi(params)
    const data = res.data
    prescriptionList.value = data.items ?? data.records ?? (Array.isArray(data) ? data : [])
    total.value = data.total ?? prescriptionList.value.length
  } finally {
    loading.value = false
  }
}

const handleFilterChange = () => {
  pagination.page = 1
  fetchList()
}

// ===== 详情 =====
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const showDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getPrescriptionDetailApi(row.prescriptionId)
    detail.value = res.data
  } finally {
    detailLoading.value = false
  }
}

// ===== 审方 =====
const reviewDialogVisible = ref(false)
const currentPrescription = ref(null)
const reviewForm = reactive({ action: 'APPROVE', reviewComment: '', rejectReason: '' })
const submitting = ref(false)

const openReview = (row, action) => {
  currentPrescription.value = row
  reviewForm.action = action
  reviewForm.reviewComment = ''
  reviewForm.rejectReason = ''
  reviewDialogVisible.value = true
}

const handleReview = async () => {
  if (reviewForm.action === 'REJECT' && !reviewForm.rejectReason?.trim()) {
    ElMessage.warning('驳回必须填写驳回原因')
    return
  }
  submitting.value = true
  try {
    // pharmacistId 取当前登录药师的关联编号（由 /auth/me 返回，JWT 全链路透传）
    await reviewPrescriptionApi(currentPrescription.value.prescriptionId, {
      action: reviewForm.action,
      pharmacistId: String(userStore.userInfo?.pharmacistId || ''),
      reviewComment: reviewForm.reviewComment || undefined,
      rejectReason: reviewForm.action === 'REJECT' ? reviewForm.rejectReason : undefined
    })
    ElMessage.success(reviewForm.action === 'APPROVE' ? '已通过审方' : '已驳回')
    reviewDialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

// ===== 调剂发药 =====
const dispenseDialogVisible = ref(false)

const openDispense = (row) => {
  currentPrescription.value = row
  dispenseDialogVisible.value = true
}

const handleDispense = async () => {
  submitting.value = true
  try {
    await dispensePrescriptionApi(currentPrescription.value.prescriptionId, {
      pharmacistId: String(userStore.userInfo?.userId || '')
    })
    ElMessage.success('发药成功')
    dispenseDialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

// ===== 完成 =====
const handleComplete = (row) => {
  ElMessageBox.confirm('确认将处方标记为已完成吗？', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'info'
  }).then(async () => {
    await completePrescriptionApi(row.prescriptionId)
    ElMessage.success('已完成')
    fetchList()
  }).catch(() => {})
}

// ===== 状态文案/配色 =====
const STATUS_META = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_REVIEW: { label: '待审方', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  PAID: { label: '已缴费', type: 'success' },
  DISPENSED: { label: '已发药', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}
const getStatusLabel = (s) => STATUS_META[s]?.label || s || '-'
const getStatusType = (s) => STATUS_META[s]?.type || ''

const PAY_META = {
  UNPAID: { label: '未支付', type: 'info' },
  PAID: { label: '已支付', type: 'success' },
  REFUNDING: { label: '退款中', type: 'warning' },
  REFUNDED: { label: '已退款', type: 'danger' }
}
const getPayLabel = (s) => PAY_META[s]?.label || (s || '-')
const getPayType = (s) => PAY_META[s]?.type || ''

const getSourceLabel = (s) => {
  const m = { OUTPATIENT: '门诊', INPATIENT: '住院', EMERGENCY: '急诊' }
  return m[s] || s || '-'
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
.header-actions {
  display: flex;
  gap: 8px;
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>

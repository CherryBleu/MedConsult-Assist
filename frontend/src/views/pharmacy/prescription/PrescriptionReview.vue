<template>
  <div class="page-container">
    <div class="card-box prescription-review-page">
      <div class="page-header">
        <div>
          <h2 class="page-title">处方发药</h2>
          <p class="page-subtitle">医生开方后进入患者缴费与药房发药流程，药房按待发药、已发药、已完成处理。</p>
        </div>
        <div class="header-actions">
          <el-select
            v-model="statusFilter"
            class="status-filter"
            placeholder="状态筛选"
            clearable
            aria-label="处方状态筛选"
            @change="handleFilterChange"
          >
            <el-option label="待缴费" value="APPROVED" />
            <el-option label="待发药" value="PAID" />
            <el-option label="已发药" value="DISPENSED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
          <el-button type="primary" class="prescription-toolbar-action" @click="fetchList">刷新</el-button>
        </div>
      </div>

      <PageState
        :loading="loading"
        :error="loadError"
        :empty="prescriptionList.length === 0"
        empty-text="暂无处方记录"
        @retry="fetchList"
      >
        <div class="prescription-table-shell">
          <ResponsiveTable aria-label="处方发药列表">
            <template #table>
              <el-table :data="prescriptionList" class="prescription-table" border stripe>
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
              <el-table-column label="操作" width="284" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button class="prescription-review-action" size="small" link type="primary" :aria-label="`查看 ${row.prescriptionId} 处方详情`" @click="showDetail(row)">详情</el-button>
                    <el-button v-if="row.status === 'PAID'" class="prescription-review-action" size="small" link type="warning" :aria-label="`发药 ${row.prescriptionId}`" @click="openDispense(row)">发药</el-button>
                    <el-button v-if="row.status === 'DISPENSED'" class="prescription-review-action" size="small" link type="primary" :aria-label="`完成 ${row.prescriptionId}`" @click="handleComplete(row)">完成</el-button>
                  </div>
                </template>
              </el-table-column>
              </el-table>
            </template>

            <template #card>
              <article
                v-for="row in prescriptionList"
                :key="row.id || row.prescriptionId"
                class="prescription-card"
                data-testid="responsive-prescription-card"
              >
                <div class="prescription-card__header">
                  <div>
                    <p class="prescription-card__title">{{ row.prescriptionId }}</p>
                    <p class="prescription-card__meta">{{ row.createdAt || '-' }}</p>
                  </div>
                  <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
                </div>
                <dl class="prescription-card__fields">
                  <div>
                    <dt>总金额</dt>
                    <dd>{{ row.totalFee != null ? `¥ ${Number(row.totalFee).toFixed(2)}` : '-' }}</dd>
                  </div>
                  <div>
                    <dt>支付状态</dt>
                    <dd>
                      <el-tag v-if="row.paymentStatus" size="small" effect="plain" :type="getPayType(row.paymentStatus)">
                        {{ getPayLabel(row.paymentStatus) }}
                      </el-tag>
                      <span v-else>-</span>
                    </dd>
                  </div>
                </dl>
                <div class="prescription-card__actions">
                  <el-button class="prescription-review-action" plain :aria-label="`查看 ${row.prescriptionId} 处方详情`" @click="showDetail(row)">详情</el-button>
                  <el-button v-if="row.status === 'PAID'" class="prescription-review-action" type="warning" plain :aria-label="`发药 ${row.prescriptionId}`" @click="openDispense(row)">发药</el-button>
                  <el-button v-if="row.status === 'DISPENSED'" class="prescription-review-action" type="primary" plain :aria-label="`完成 ${row.prescriptionId}`" @click="handleComplete(row)">完成</el-button>
                </div>
              </article>
            </template>
          </ResponsiveTable>
        </div>
      </PageState>

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
    <el-dialog v-model="detailVisible" title="处方详情" width="min(720px, calc(100vw - 32px))" top="24px" class="prescription-detail-dialog">
      <div v-loading="detailLoading">
        <div v-if="detailError" class="inline-error" role="alert">
          <span>{{ detailError }}</span>
          <el-button class="inline-error__action" type="danger" plain @click="retryDetail">重试</el-button>
        </div>
        <el-descriptions :column="descriptionColumns" border v-if="detail">
          <el-descriptions-item label="处方编号">{{ detail.prescriptionId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(detail.status)">{{ getStatusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总金额">¥ {{ detail.totalFee != null ? Number(detail.totalFee).toFixed(2) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="支付状态">{{ getPayLabel(detail.paymentStatus) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ getSourceLabel(detail.source) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="detail-section-title">处方明细</h4>
        <div class="detail-table-wrap">
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
        <div class="detail-medicine-list" aria-label="处方明细移动列表">
          <article
            v-for="item in detail?.items || []"
            :key="item.id || item.itemId || item.drugName"
            class="detail-medicine-card"
            data-testid="prescription-detail-medicine-card"
          >
            <div class="detail-medicine-card__header">
              <strong>{{ item.drugName || '-' }}</strong>
              <span>{{ item.subtotal != null ? '¥' + Number(item.subtotal).toFixed(2) : '-' }}</span>
            </div>
            <dl class="detail-medicine-card__facts">
              <div>
                <dt>规格</dt>
                <dd>{{ item.specification || '-' }}</dd>
              </div>
              <div>
                <dt>用法用量</dt>
                <dd>{{ [item.route, item.dosage].filter(Boolean).join(' / ') || '-' }}</dd>
              </div>
              <div>
                <dt>频次</dt>
                <dd>{{ item.frequency || '-' }}</dd>
              </div>
              <div>
                <dt>疗程</dt>
                <dd>{{ item.days ? `${item.days} 天` : '-' }}</dd>
              </div>
              <div>
                <dt>数量</dt>
                <dd>{{ item.quantity }} {{ item.unit || '' }}</dd>
              </div>
            </dl>
          </article>
        </div>
      </div>
    </el-dialog>

    <!-- 调剂发药对话框 -->
    <el-dialog v-model="dispenseDialogVisible" title="调剂发药" width="min(480px, calc(100vw - 32px))" top="24px" class="prescription-workflow-dialog">
      <el-alert title="发药将触发药品库存 FEFO 出库，请确认处方明细无误。" type="warning" :closable="false" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="处方编号">
          <span>{{ currentPrescription?.prescriptionId }}</span>
        </el-form-item>
        <el-form-item label="发药药师">
          <span>{{ userStore.userInfo?.name || '当前药师' }}</span>
        </el-form-item>
      </el-form>
      <div v-if="dispenseError" class="inline-error" role="alert">{{ dispenseError }}</div>
      <template #footer>
        <el-button class="prescription-dialog-action" @click="dispenseDialogVisible = false">取消</el-button>
        <el-button type="warning" class="prescription-dialog-action" :loading="submitting" @click="handleDispense">确认发药</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPrescriptionListApi, getPrescriptionDetailApi,
  dispensePrescriptionApi, completePrescriptionApi
} from '@/api/prescription'
import { useUserStore } from '@/store/modules/user'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'
import { useResponsive } from '@/composables/useResponsive'

const userStore = useUserStore()
const { isMobile } = useResponsive()

const loading = ref(false)
const prescriptionList = ref([])
const total = ref(0)
const statusFilter = ref('PAID')
const loadError = ref('')
let listRequestSeq = 0

const pagination = reactive({ page: 1, pageSize: 10 })
const descriptionColumns = computed(() => isMobile.value ? 1 : 2)
const getErrorMessage = (error, fallback) => error?.response?.data?.message || error?.message || fallback
const getPharmacistOperatorId = () => {
  const user = userStore.userInfo || {}
  const id = user.pharmacistId || user.pharmacistNo || user.userId || user.userNo || user.id || user.account
  return id == null ? '' : String(id).trim()
}

const fetchList = async () => {
  const requestSeq = ++listRequestSeq
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: pagination.page, pageSize: pagination.pageSize }
    if (statusFilter.value) params.status = statusFilter.value
    const res = await getPrescriptionListApi(params)
    if (requestSeq !== listRequestSeq) return
    const data = res.data
    prescriptionList.value = data.items ?? data.records ?? (Array.isArray(data) ? data : [])
    total.value = data.total ?? prescriptionList.value.length
  } catch (e) {
    if (requestSeq !== listRequestSeq) return
    prescriptionList.value = []
    total.value = 0
    loadError.value = e?.message || '处方列表加载失败'
  } finally {
    if (requestSeq === listRequestSeq) {
      loading.value = false
    }
  }
}

const handleFilterChange = () => {
  pagination.page = 1
  fetchList()
}

// ===== 详情 =====
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref(null)
const lastDetailId = ref('')

const showDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  lastDetailId.value = row.prescriptionId
  try {
    const res = await getPrescriptionDetailApi(row.prescriptionId)
    detail.value = res.data
  } catch (e) {
    detailError.value = getErrorMessage(e, '处方详情加载失败，请重试')
  } finally {
    detailLoading.value = false
  }
}

const retryDetail = () => {
  if (!lastDetailId.value) return
  showDetail({ prescriptionId: lastDetailId.value })
}

const currentPrescription = ref(null)
const submitting = ref(false)

// ===== 调剂发药 =====
const dispenseDialogVisible = ref(false)
const dispenseError = ref('')

const openDispense = (row) => {
  currentPrescription.value = row
  dispenseError.value = ''
  dispenseDialogVisible.value = true
}

const handleDispense = async () => {
  dispenseError.value = ''
  submitting.value = true
  try {
    const pharmacistId = getPharmacistOperatorId()
    if (!pharmacistId) {
      throw new Error('当前药师账号缺少编号，请重新登录后再试')
    }
    await dispensePrescriptionApi(currentPrescription.value.prescriptionId, {
      pharmacistId
    })
    ElMessage.success('发药成功')
    dispenseDialogVisible.value = false
    fetchList()
  } catch (e) {
    dispenseError.value = getErrorMessage(e, '发药失败，请重试')
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
  APPROVED: { label: '待缴费', type: 'warning' },
  PAID: { label: '待发药', type: 'success' },
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
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-lighter);
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.page-subtitle {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: var(--font-sm);
  line-height: 1.5;
}
.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.status-filter {
  width: 160px;
}
.prescription-review-page {
  max-width: var(--content-max);
  margin: 0 auto;
}
.prescription-toolbar-action {
  min-height: var(--touch-target);
}
.prescription-table-shell {
  min-width: 0;
  max-width: 100%;
}
.prescription-table-shell :deep(.responsive-table__desktop) {
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 4px;
  scrollbar-gutter: stable;
}
.prescription-table-shell :deep(.el-table) {
  min-width: 954px;
}
.table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.table-actions .el-button {
  margin-left: 0;
}
.prescription-review-action,
.prescription-dialog-action {
  min-width: var(--touch-target);
  min-height: var(--touch-target);
  touch-action: manipulation;
  transition: background-color var(--motion-base) ease, color var(--motion-base) ease, box-shadow var(--motion-base) ease;
}
.prescription-review-action:focus-visible,
.prescription-dialog-action:focus-visible,
.prescription-toolbar-action:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
  box-shadow: var(--focus-ring);
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
  max-width: 100%;
  overflow-x: auto;
}
.prescription-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, .84);
  box-shadow: 0 12px 30px rgba(15, 35, 95, .06);
}
.prescription-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.prescription-card__title,
.prescription-card__meta {
  margin: 0;
}
.prescription-card__title {
  overflow-wrap: anywhere;
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}
.prescription-card__meta {
  margin-top: 4px;
  font-size: var(--font-sm);
  color: var(--text-secondary);
}
.prescription-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}
.prescription-card__fields div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}
.prescription-card__fields dt,
.prescription-card__fields dd {
  margin: 0;
}
.prescription-card__fields dt {
  color: var(--text-secondary);
}
.prescription-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  text-align: right;
}
.prescription-card__actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(104px, 1fr));
  gap: 8px;
}
.prescription-card__actions .el-button {
  width: 100%;
  min-height: var(--touch-target);
  margin-left: 0;
}
.detail-section-title {
  margin: 16px 0 8px;
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}
.detail-table-wrap {
  max-width: 100%;
  overflow-x: auto;
}
.detail-medicine-list {
  display: none;
}
.detail-medicine-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: var(--surface-color);
}
.detail-medicine-card__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-primary);
}
.detail-medicine-card__header strong,
.detail-medicine-card__header span {
  overflow-wrap: anywhere;
}
.detail-medicine-card__facts {
  display: grid;
  gap: 8px;
  margin: 0;
}
.detail-medicine-card__facts div {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 10px;
}
.detail-medicine-card__facts dt,
.detail-medicine-card__facts dd {
  margin: 0;
}
.detail-medicine-card__facts dt {
  color: var(--text-secondary);
}
.detail-medicine-card__facts dd {
  min-width: 0;
  overflow-wrap: anywhere;
  text-align: right;
  color: var(--text-primary);
}
.inline-error {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
  padding: 12px;
  border: 1px solid rgba(185, 28, 28, .22);
  border-radius: var(--radius-sm);
  background: #fef2f2;
  color: var(--el-color-danger);
  font-size: var(--font-sm);
  line-height: 1.6;
}
.inline-error span {
  min-width: 0;
  flex: 1;
}
.inline-error__action {
  min-height: var(--touch-target);
}
:global(.prescription-detail-dialog),
:global(.prescription-workflow-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 48px);
  margin: 24px auto !important;
}
:global(.prescription-detail-dialog .el-dialog__body),
:global(.prescription-workflow-dialog .el-dialog__body) {
  overflow-y: auto;
}
@media (max-width: 640px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .header-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .status-filter,
  .header-actions .el-button {
    width: 100%;
    min-height: var(--touch-target);
  }

  .pagination-wrapper {
    justify-content: flex-start;
    padding-bottom: 4px;
  }

  .pagination-wrapper :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
  }

  .detail-table-wrap {
    display: none;
  }

  .detail-medicine-list {
    display: grid;
    gap: 10px;
  }

  .inline-error {
    align-items: stretch;
    flex-direction: column;
  }

  .inline-error__action {
    width: 100%;
  }

  :deep(.el-dialog) {
    display: flex;
    flex-direction: column;
    max-height: calc(100vh - 48px);
    margin: 24px auto !important;
  }

  :deep(.el-dialog__body) {
    overflow-y: auto;
  }

  :deep(.el-dialog__footer) {
    display: grid;
    gap: 8px;
  }

  :deep(.el-dialog__footer .el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>

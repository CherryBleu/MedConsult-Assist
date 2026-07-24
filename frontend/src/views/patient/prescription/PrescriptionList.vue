<template>
  <section class="page-container prescription-shell" aria-labelledby="patient-prescriptions-title">
    <div class="prescription-workspace">
      <header class="prescription-header">
        <div class="prescription-header__copy">
          <p class="prescription-eyebrow">Patient Prescriptions</p>
          <h2 id="patient-prescriptions-title" class="page-title">我的处方</h2>
          <p class="prescription-subtitle">待缴费、待取药和历史处方</p>
        </div>
        <el-button
          type="primary"
          plain
          class="refresh-action"
          :loading="loading"
          @click="fetchList"
        >
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </header>

      <section class="summary-strip" aria-label="处方费用概览">
        <div class="summary-item">
          <span class="summary-item__label">待缴费</span>
          <strong class="summary-item__value">{{ pendingPayCount }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-item__label">本页金额</span>
          <strong class="summary-item__value">¥ {{ formatMoney(pageTotalFee) }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-item__label">待取药</span>
          <strong class="summary-item__value">{{ waitingPickupCount }}</strong>
        </div>
      </section>

      <section class="list-panel" aria-labelledby="patient-prescription-list-title">
        <div class="list-panel__header">
          <h3 id="patient-prescription-list-title" class="section-title">处方记录</h3>
          <el-tabs v-model="activeTab" class="prescription-tabs" @tab-change="handleTabChange">
            <el-tab-pane
              v-for="tab in tabs"
              :key="tab.name"
              :label="tab.label"
              :name="tab.name"
            />
          </el-tabs>
        </div>

        <PageState
          :loading="loading"
          :error="loadError"
          :empty="prescriptionList.length === 0"
          empty-text="暂无处方记录"
          @retry="fetchList"
        >
          <ResponsiveTable aria-label="患者处方列表">
            <template #table>
              <el-table :data="prescriptionList" border stripe>
                <el-table-column prop="prescriptionId" label="处方编号" min-width="170" />
                <el-table-column label="状态" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getStatusType(row.status)">
                      {{ getStatusLabel(row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="支付" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain" :type="getPayType(row.paymentStatus)">
                      {{ getPayLabel(row.paymentStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="金额" width="120" align="right">
                  <template #default="{ row }">
                    ¥ {{ formatMoney(row.totalFee) }}
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="创建时间" min-width="170" />
                <el-table-column label="下一步" min-width="140">
                  <template #default="{ row }">
                    <span class="next-action">{{ getNextAction(row) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="190" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      link
                      type="primary"
                      class="patient-prescription-action"
                      @click="openDetail(row)"
                    >
                      <el-icon><View /></el-icon>
                      查看详情
                    </el-button>
                    <el-button
                      v-if="canPay(row)"
                      link
                      type="warning"
                      class="patient-prescription-action"
                      @click="openPayDialog(row)"
                    >
                      <el-icon><CreditCard /></el-icon>
                      立即缴费
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </template>

            <template #card>
              <article
                v-for="row in prescriptionList"
                :key="row.prescriptionId"
                class="prescription-card"
                data-testid="responsive-patient-prescription-card"
              >
                <div class="prescription-card__topline">
                  <div class="prescription-card__title-block">
                    <span class="prescription-card__id">{{ row.prescriptionId }}</span>
                    <span class="prescription-card__time">{{ row.createdAt || '-' }}</span>
                  </div>
                  <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
                </div>

                <dl class="prescription-card__facts">
                  <div>
                    <dt>支付</dt>
                    <dd>{{ getPayLabel(row.paymentStatus) }}</dd>
                  </div>
                  <div>
                    <dt>金额</dt>
                    <dd>¥ {{ formatMoney(row.totalFee) }}</dd>
                  </div>
                  <div>
                    <dt>下一步</dt>
                    <dd>{{ getNextAction(row) }}</dd>
                  </div>
                </dl>

                <div class="prescription-card__actions">
                  <el-button
                    plain
                    class="patient-prescription-action"
                    @click="openDetail(row)"
                  >
                    <el-icon><View /></el-icon>
                    查看详情
                  </el-button>
                  <el-button
                    v-if="canPay(row)"
                    type="warning"
                    plain
                    class="patient-prescription-action"
                    @click="openPayDialog(row)"
                  >
                    <el-icon><CreditCard /></el-icon>
                    立即缴费
                  </el-button>
                </div>
              </article>
            </template>
          </ResponsiveTable>
        </PageState>

        <div v-if="total > 0" class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next"
            @size-change="fetchList"
            @current-change="fetchList"
          />
        </div>
      </section>
    </div>

    <el-drawer
      v-model="detailVisible"
      title="处方详情"
      :size="detailDrawerSize"
      direction="rtl"
      class="prescription-detail-drawer"
    >
      <div v-loading="detailLoading" class="detail-content">
        <div v-if="detailError" class="detail-error" role="alert">
          <el-icon><Warning /></el-icon>
          <div class="detail-error__copy">
            <strong>处方详情加载失败</strong>
            <span>{{ detailError }}</span>
          </div>
          <el-button type="primary" plain @click="reloadDetail">重试</el-button>
        </div>

        <template v-else-if="detail">
          <section class="detail-section detail-summary" aria-label="处方摘要">
            <div>
              <span class="detail-label">处方编号</span>
              <strong class="detail-id">{{ detail.prescriptionId }}</strong>
            </div>
            <div class="detail-summary__tags">
              <el-tag :type="getStatusType(detail.status)">
                {{ getStatusLabel(detail.status) }}
              </el-tag>
              <el-tag effect="plain" :type="getPayType(detail.paymentStatus)">
                {{ getPayLabel(detail.paymentStatus) }}
              </el-tag>
            </div>
          </section>

          <section class="detail-section" aria-labelledby="prescription-progress-title">
            <h3 id="prescription-progress-title" class="drawer-section-title">流转状态</h3>
            <ol class="status-steps" aria-label="处方状态进度">
              <li
                v-for="step in statusSteps"
                :key="step.value"
                class="status-step"
                :class="getStepClass(step.value, detail.status)"
              >
                <span class="status-step__dot" aria-hidden="true"></span>
                <span>{{ step.label }}</span>
              </li>
            </ol>
            <p v-if="isExceptional(detail)" class="exception-note" role="alert">
              {{ detail.rejectReason || detail.cancelReason || '处方当前不可继续流转' }}
            </p>
          </section>

          <section class="detail-section" aria-labelledby="prescription-medicine-title">
            <h3 id="prescription-medicine-title" class="drawer-section-title">处方明细</h3>
            <ul class="medicine-list">
              <li v-for="item in detail.items" :key="item.id || item.drugName" class="medicine-item">
                <div class="medicine-item__main">
                  <strong>{{ item.drugName }}</strong>
                  <span>{{ item.specification || '规格未记录' }}</span>
                </div>
                <dl class="medicine-item__meta">
                  <div>
                    <dt>用法</dt>
                    <dd>{{ formatUsage(item) }}</dd>
                  </div>
                  <div>
                    <dt>数量</dt>
                    <dd>{{ item.quantity }} {{ item.unit || '' }}</dd>
                  </div>
                  <div>
                    <dt>小计</dt>
                    <dd>¥ {{ formatMoney(item.subtotal) }}</dd>
                  </div>
                </dl>
              </li>
            </ul>
          </section>

          <section class="detail-section detail-fee" aria-label="费用信息">
            <span>应付金额</span>
            <strong>¥ {{ formatMoney(detail.totalFee) }}</strong>
          </section>

          <div class="drawer-actions">
            <el-button
              v-if="canPay(detail)"
              type="warning"
              class="patient-prescription-action"
              @click="openPayDialog(detail)"
            >
              <el-icon><CreditCard /></el-icon>
              立即缴费
            </el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-dialog
      v-model="payDialogVisible"
      title="处方缴费"
      width="min(480px, calc(100vw - 32px))"
      class="prescription-pay-dialog"
      :close-on-click-modal="!paying"
    >
      <div v-if="currentPayPrescription" class="pay-dialog-body">
        <div class="pay-summary">
          <el-icon><Wallet /></el-icon>
          <div>
            <span>本次实付</span>
            <strong>¥ {{ formatMoney(currentPayPrescription.totalFee) }}</strong>
          </div>
        </div>

        <div v-if="payError" class="pay-error" role="alert">
          <el-icon><Warning /></el-icon>
          <span>{{ payError }}</span>
        </div>

        <el-form label-position="top" class="pay-form">
          <el-form-item label="处方编号">
            <span class="readonly-field">{{ currentPayPrescription.prescriptionId }}</span>
          </el-form-item>
          <el-form-item label="支付流水号">
            <el-input
              v-model="payForm.paymentNo"
              readonly
              aria-label="支付流水号"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button class="dialog-action" :disabled="paying" @click="payDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          class="dialog-action pay-submit"
          :loading="paying"
          @click="handlePay"
        >
          确认缴费
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CreditCard, Refresh, View, Wallet, Warning } from '@element-plus/icons-vue'
import { getPrescriptionDetailApi, getPrescriptionListApi, payPrescriptionApi } from '@/api/prescription'
import { useResponsive } from '@/composables/useResponsive'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const tabs = [
  { name: 'ALL', label: '全部' },
  { name: 'PENDING_PAY', label: '待缴费', status: 'APPROVED' },
  { name: 'REVIEWING', label: '历史待审', status: 'PENDING_REVIEW' },
  { name: 'READY', label: '待取药', status: 'PAID' },
  { name: 'DONE', label: '已完成', statuses: ['DISPENSED', 'COMPLETED'] },
  { name: 'EXCEPTION', label: '退回/取消', statuses: ['REJECTED', 'CANCELLED'] }
]

const STATUS_META = {
  DRAFT: { label: '草稿', type: 'info' },
  PENDING_REVIEW: { label: '历史待审', type: 'warning' },
  APPROVED: { label: '待缴费', type: 'warning' },
  REJECTED: { label: '已驳回', type: 'danger' },
  PAID: { label: '待取药', type: 'success' },
  DISPENSED: { label: '已发药', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const PAY_META = {
  UNPAID: { label: '未支付', type: 'warning' },
  PAID: { label: '已支付', type: 'success' },
  REFUNDING: { label: '退款中', type: 'warning' },
  REFUNDED: { label: '已退款', type: 'info' }
}

const statusSteps = [
  { value: 'APPROVED', label: '开方' },
  { value: 'PAID', label: '缴费' },
  { value: 'DISPENSED', label: '发药' },
  { value: 'COMPLETED', label: '完成' }
]
const stepOrder = statusSteps.map(step => step.value)

const { isMobile } = useResponsive()
const detailDrawerSize = computed(() => isMobile.value ? '100%' : '560px')
const activeTab = ref('ALL')
const loading = ref(false)
const loadError = ref('')
const prescriptionList = ref([])
const total = ref(0)
const pagination = reactive({ page: 1, pageSize: 10 })
let listRequestSeq = 0

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref(null)
const lastDetailId = ref('')

const payDialogVisible = ref(false)
const paying = ref(false)
const payError = ref('')
const currentPayPrescription = ref(null)
const payForm = reactive({ paymentNo: '' })

const currentTab = computed(() => tabs.find(tab => tab.name === activeTab.value) || tabs[0])
const pendingPayCount = computed(() => prescriptionList.value.filter(canPay).length)
const waitingPickupCount = computed(() => prescriptionList.value.filter(item => item.status === 'PAID').length)
const pageTotalFee = computed(() => prescriptionList.value.reduce((sum, item) => sum + Number(item.totalFee || 0), 0))

const fetchList = async () => {
  const requestSeq = ++listRequestSeq
  loading.value = true
  loadError.value = ''
  try {
    const params = { page: pagination.page, pageSize: pagination.pageSize }
    if (currentTab.value.status) params.status = currentTab.value.status
    const res = await getPrescriptionListApi(params)
    if (requestSeq !== listRequestSeq) return
    const data = res.data || {}
    const records = data.items ?? data.records ?? (Array.isArray(data) ? data : [])
    const filtered = applyTabFilter(records)
    prescriptionList.value = filtered
    total.value = currentTab.value.status || currentTab.value.name === 'ALL'
      ? (data.total ?? filtered.length)
      : filtered.length
  } catch (error) {
    if (requestSeq !== listRequestSeq) return
    prescriptionList.value = []
    total.value = 0
    loadError.value = error?.message || '处方列表加载失败，请重试'
  } finally {
    if (requestSeq === listRequestSeq) {
      loading.value = false
    }
  }
}

const applyTabFilter = (records) => {
  if (currentTab.value.name === 'PENDING_PAY') return records.filter(canPay)
  if (currentTab.value.status) return records
  if (currentTab.value.statuses) {
    return records.filter(item => currentTab.value.statuses.includes(item.status))
  }
  return records
}

const handleTabChange = () => {
  pagination.page = 1
  fetchList()
}

const openDetail = async (row) => {
  lastDetailId.value = row.prescriptionId
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const res = await getPrescriptionDetailApi(row.prescriptionId)
    detail.value = res.data
  } catch (error) {
    detailError.value = error?.message || '处方详情加载失败，请重试'
  } finally {
    detailLoading.value = false
  }
}

const reloadDetail = () => {
  if (!lastDetailId.value) return
  openDetail({ prescriptionId: lastDetailId.value })
}

const openPayDialog = (row) => {
  currentPayPrescription.value = row
  payForm.paymentNo = `PAY${Date.now()}`
  payError.value = ''
  payDialogVisible.value = true
}

const handlePay = async () => {
  if (!currentPayPrescription.value) return
  paying.value = true
  payError.value = ''
  try {
    const res = await payPrescriptionApi(currentPayPrescription.value.prescriptionId, {
      paidAmount: Number(currentPayPrescription.value.totalFee || 0),
      paymentNo: payForm.paymentNo
    })
    ElMessage.success('缴费成功')
    payDialogVisible.value = false
    if (detail.value?.prescriptionId === res.data?.prescriptionId) {
      detail.value = { ...detail.value, ...res.data }
    }
    await fetchList()
  } catch (error) {
    payError.value = error?.message || '缴费失败，请重试'
  } finally {
    paying.value = false
  }
}

const canPay = (row) => row?.status === 'APPROVED' && row?.paymentStatus !== 'PAID'
const isExceptional = (row) => ['REJECTED', 'CANCELLED'].includes(row?.status)

const getStatusLabel = (status) => STATUS_META[status]?.label || status || '-'
const getStatusType = (status) => STATUS_META[status]?.type || ''
const getPayLabel = (status) => PAY_META[status]?.label || status || '-'
const getPayType = (status) => PAY_META[status]?.type || ''

const getNextAction = (row) => {
  if (canPay(row)) return '缴费后取药'
  if (row.status === 'PENDING_REVIEW') return '历史处方处理中'
  if (row.status === 'PAID') return '等待药房发药'
  if (row.status === 'DISPENSED') return '核对已发药'
  if (row.status === 'COMPLETED') return '处方已完成'
  if (row.status === 'REJECTED') return '联系医生调整'
  if (row.status === 'CANCELLED') return '处方已取消'
  return '查看处方详情'
}

const getStepClass = (step, status) => {
  if (isExceptional({ status })) return 'is-muted'
  const currentIndex = stepOrder.indexOf(status)
  const stepIndex = stepOrder.indexOf(step)
  if (currentIndex === -1) return ''
  if (stepIndex < currentIndex) return 'is-complete'
  if (stepIndex === currentIndex) return 'is-current'
  return ''
}

const formatMoney = (value) => Number(value || 0).toFixed(2)
const formatUsage = (item) => {
  return [item.route, item.dosage, item.frequency, item.days ? `${item.days}天` : '']
    .filter(Boolean)
    .join(' / ') || '-'
}

onMounted(fetchList)
</script>

<style scoped>
.prescription-shell {
  --prescription-radius: 8px;
}

.prescription-workspace {
  display: grid;
  gap: 16px;
  max-width: var(--content-max);
  margin: 0 auto;
}

.prescription-header,
.summary-strip,
.list-panel {
  border: 1px solid rgba(219, 234, 254, .88);
  border-radius: var(--prescription-radius);
  background: rgba(255, 255, 255, .9);
  box-shadow: 0 14px 34px rgba(15, 35, 95, .07);
}

.prescription-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
}

.prescription-header__copy {
  min-width: 0;
}

.prescription-eyebrow,
.prescription-subtitle,
.page-title,
.section-title {
  margin: 0;
}

.prescription-eyebrow {
  margin-bottom: 4px;
  color: var(--primary-dark);
  font-size: var(--font-xs);
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.page-title {
  color: var(--text-primary);
  font-size: 22px;
  font-weight: 800;
  line-height: 1.3;
}

.prescription-subtitle {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: var(--font-sm);
}

.refresh-action,
.dialog-action {
  min-height: var(--touch-target);
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  overflow: hidden;
}

.summary-item {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 14px 18px;
  border-right: 1px solid var(--border-lighter);
}

.summary-item:last-child {
  border-right: 0;
}

.summary-item__label {
  color: var(--text-secondary);
  font-size: var(--font-sm);
}

.summary-item__value {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-size: 22px;
  line-height: 1.2;
}

.list-panel {
  padding: 18px;
}

.list-panel__header {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
}

.section-title,
.drawer-section-title {
  color: var(--text-primary);
  font-size: var(--font-base);
  font-weight: 800;
}

.prescription-tabs {
  min-width: 0;
}

.prescription-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.prescription-tabs :deep(.el-tabs__nav-wrap) {
  min-width: 0;
}

.prescription-tabs :deep(.el-tabs__item) {
  min-height: var(--touch-target);
  letter-spacing: 0;
}

.next-action {
  color: var(--text-regular);
  font-size: var(--font-sm);
}

.patient-prescription-action {
  min-height: 36px;
  margin-left: 0;
}

.patient-prescription-action + .patient-prescription-action {
  margin-left: 8px;
}

.prescription-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--prescription-radius);
  background: #ffffff;
}

.prescription-card__topline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.prescription-card__title-block {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.prescription-card__id {
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-size: var(--font-base);
  font-weight: 800;
}

.prescription-card__time {
  color: var(--text-secondary);
  font-size: var(--font-sm);
}

.prescription-card__facts,
.medicine-item__meta {
  display: grid;
  gap: 10px;
  margin: 0;
}

.prescription-card__facts div,
.medicine-item__meta div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 12px;
}

.prescription-card__facts dt,
.prescription-card__facts dd,
.medicine-item__meta dt,
.medicine-item__meta dd {
  margin: 0;
}

.prescription-card__facts dt,
.medicine-item__meta dt,
.detail-label {
  color: var(--text-secondary);
  font-size: var(--font-sm);
}

.prescription-card__facts dd,
.medicine-item__meta dd {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  text-align: right;
}

.prescription-card__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.prescription-card__actions .patient-prescription-action {
  width: 100%;
  min-height: var(--touch-target);
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  max-width: 100%;
  margin-top: 16px;
  overflow-x: auto;
}

.detail-content {
  min-height: 240px;
}

.detail-section {
  padding: 16px 0;
  border-bottom: 1px solid var(--border-lighter);
}

.detail-section:first-child {
  padding-top: 0;
}

.detail-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.detail-id {
  display: block;
  margin-top: 4px;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-size: var(--font-lg);
}

.detail-summary__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.status-steps {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}

.status-step {
  display: grid;
  gap: 6px;
  min-width: 0;
  color: var(--text-secondary);
  font-size: var(--font-xs);
  text-align: center;
}

.status-step__dot {
  width: 12px;
  height: 12px;
  margin: 0 auto;
  border: 2px solid var(--border-color);
  border-radius: 50%;
  background: #ffffff;
}

.status-step.is-complete,
.status-step.is-current {
  color: var(--primary-dark);
  font-weight: 700;
}

.status-step.is-complete .status-step__dot,
.status-step.is-current .status-step__dot {
  border-color: var(--accent-green);
  background: var(--accent-green);
}

.status-step.is-current .status-step__dot {
  box-shadow: 0 0 0 4px rgba(22, 163, 74, .14);
}

.status-step.is-muted {
  color: var(--text-placeholder);
}

.exception-note,
.detail-error,
.pay-error {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(185, 28, 28, .22);
  border-radius: var(--prescription-radius);
  background: rgba(254, 242, 242, .92);
  color: var(--danger-color);
}

.exception-note {
  margin: 12px 0 0;
}

.detail-error {
  align-items: center;
}

.detail-error__copy {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.medicine-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.medicine-item {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border-lighter);
  border-radius: var(--prescription-radius);
  background: rgba(248, 250, 252, .9);
}

.medicine-item__main {
  display: grid;
  gap: 4px;
}

.medicine-item__main strong {
  color: var(--text-primary);
}

.medicine-item__main span {
  color: var(--text-secondary);
  font-size: var(--font-sm);
}

.detail-fee {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-regular);
}

.detail-fee strong {
  color: var(--primary-dark);
  font-size: 24px;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.drawer-actions .patient-prescription-action {
  min-height: var(--touch-target);
}

.pay-dialog-body {
  display: grid;
  gap: 14px;
}

.pay-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(34, 197, 94, .22);
  border-radius: var(--prescription-radius);
  background: rgba(240, 253, 244, .92);
  color: var(--accent-green);
}

.pay-summary .el-icon {
  font-size: 22px;
}

.pay-summary span {
  display: block;
  color: var(--text-secondary);
  font-size: var(--font-sm);
}

.pay-summary strong {
  color: var(--text-primary);
  font-size: 24px;
}

.pay-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.readonly-field {
  display: inline-flex;
  align-items: center;
  min-height: var(--touch-target);
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-weight: 700;
}

.pay-submit {
  min-width: 112px;
}

@media (max-width: 640px) {
  .prescription-workspace {
    gap: 12px;
  }

  .prescription-header {
    align-items: stretch;
    flex-direction: column;
    padding: 16px;
  }

  .refresh-action {
    width: 100%;
  }

  .summary-strip {
    grid-template-columns: 1fr;
  }

  .summary-item {
    border-right: 0;
    border-bottom: 1px solid var(--border-lighter);
  }

  .summary-item:last-child {
    border-bottom: 0;
  }

  .list-panel {
    padding: 14px;
  }

  .prescription-tabs :deep(.el-tabs__nav-scroll) {
    overflow-x: auto;
  }

  .pagination-wrapper {
    justify-content: flex-start;
    padding-bottom: 4px;
  }

  .pagination-wrapper :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
  }

  .detail-summary,
  .detail-fee,
  .detail-error {
    align-items: stretch;
    flex-direction: column;
  }

  .detail-summary__tags {
    justify-content: flex-start;
  }

  .status-steps {
    grid-template-columns: repeat(5, minmax(48px, 1fr));
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .drawer-actions,
  .drawer-actions .patient-prescription-action,
  .dialog-action {
    width: 100%;
  }
}
</style>

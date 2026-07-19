<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存管理</h2>
        <div class="header-actions">
          <el-input v-model="searchKey" class="filter-control search-control" placeholder="搜索药品名称" clearable />
          <el-select v-model="statusFilter" class="filter-control" placeholder="状态筛选" clearable>
            <el-option label="正常" value="NORMAL" />
            <el-option label="库存不足" value="LOW_STOCK" />
            <el-option label="近效期" value="EXPIRED_WARNING" />
          </el-select>
          <el-button type="primary" class="stock-action" @click="getStockList">刷新</el-button>
        </div>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="filteredList.length === 0"
        loading-text="正在加载库存..."
        empty-text="暂无库存记录"
        @retry="getStockList"
      >
        <ResponsiveTable aria-label="库存管理列表">
          <template #table>
            <el-table :data="filteredList" border stripe>
              <el-table-column prop="drugName" label="药品名称" min-width="180" />
              <el-table-column prop="specification" label="规格" width="150" />
              <el-table-column prop="batchNo" label="批号" width="120" />
              <el-table-column label="库存数量" width="130">
                <template #default="{ row }">
                  <span :class="{ 'low-stock': row.status === 'LOW_STOCK' }">
                    {{ row.stockQuantity }} {{ row.unit }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="warningQuantity" label="预警值" width="90" align="center" />
              <el-table-column prop="expireDate" label="有效期至" width="120" />
              <el-table-column label="剩余天数" width="100" align="center">
                <template #default="{ row }">
                  <span :class="{ 'text-danger': getDaysLeft(row.expireDate) <= 30 }">
                    {{ getDaysLeft(row.expireDate) }} 天
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="success" link @click="openStockDialog(row, 'IN')">入库</el-button>
                  <el-button size="small" type="warning" link @click="openStockDialog(row, 'OUT')">出库</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template #card>
            <article
              v-for="row in filteredList"
              :key="row.id || row.drugId || row.batchNo"
              class="stock-card"
              data-testid="responsive-stock-card"
            >
              <div class="stock-card__header">
                <div>
                  <p class="stock-card__name">{{ row.drugName }}</p>
                  <p class="stock-card__meta">{{ row.specification || '-' }} · 批号 {{ row.batchNo || '-' }}</p>
                </div>
                <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
              </div>

              <dl class="stock-card__fields">
                <div>
                  <dt>库存数量</dt>
                  <dd :class="{ 'low-stock': row.status === 'LOW_STOCK' }">{{ row.stockQuantity }} {{ row.unit }}</dd>
                </div>
                <div>
                  <dt>预警值</dt>
                  <dd>{{ row.warningQuantity }} {{ row.unit }}</dd>
                </div>
                <div>
                  <dt>有效期至</dt>
                  <dd>{{ row.expireDate || '-' }}</dd>
                </div>
                <div>
                  <dt>剩余天数</dt>
                  <dd :class="{ 'text-danger': getDaysLeft(row.expireDate) <= 30 }">{{ getDaysLeft(row.expireDate) }} 天</dd>
                </div>
              </dl>

              <div class="stock-card__actions">
                <el-button type="success" plain @click="openStockDialog(row, 'IN')">入库</el-button>
                <el-button type="warning" plain @click="openStockDialog(row, 'OUT')">出库</el-button>
              </div>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>
    </div>

    <el-dialog v-model="stockDialogVisible" :title="operateType === 'IN' ? '药品入库' : '药品出库'" width="min(480px, calc(100vw - 32px))">
      <el-form :model="stockForm" :rules="stockRules" ref="stockFormRef" class="stock-form" label-position="top">
        <el-form-item label="药品名称">
          <span>{{ currentDrug?.drugName }}</span>
        </el-form-item>
        <el-form-item label="规格">
          <span>{{ currentDrug?.specification }}</span>
        </el-form-item>
        <el-form-item label="当前库存">
          <span>{{ currentDrug?.stockQuantity }} {{ currentDrug?.unit }}</span>
        </el-form-item>
        <el-form-item label="批号" prop="batchNo" v-if="operateType === 'IN'">
          <el-input v-model="stockForm.batchNo" placeholder="请输入批号" />
        </el-form-item>
        <el-form-item label="有效期至" prop="expireDate" v-if="operateType === 'IN'">
          <el-date-picker v-model="stockForm.expireDate" type="date" value-format="YYYY-MM-DD" placeholder="选择有效期" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="operateType === 'IN' ? '入库数量' : '出库数量'" prop="quantity">
          <el-input-number v-model="stockForm.quantity" :min="1" :max="operateType === 'OUT' ? currentDrug?.stockQuantity : 9999" />
          <span style="margin-left: 8px">{{ currentDrug?.unit }}</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="stockForm.remark" type="textarea" :rows="2" :placeholder="operateType === 'IN' ? '如：采购入库、退货入库' : '如：门诊发药、病区领药'" />
        </el-form-item>
      </el-form>
      <div v-if="dialogError" class="inline-error" role="alert">{{ dialogError }}</div>
      <template #footer>
        <el-button class="stock-action" @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" class="stock-action" :loading="submitting" @click="submitStockOperate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getStockListApi, stockInApi, stockOutApi } from '@/api/drug'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const loading = ref(false)
const errorMessage = ref('')
const stockDialogVisible = ref(false)
const operateType = ref('IN')
const submitting = ref(false)
const dialogError = ref('')
const stockList = ref([])
const currentDrug = ref(null)
const searchKey = ref('')
const statusFilter = ref('')
const stockFormRef = ref()

const stockForm = reactive({
  quantity: 1,
  batchNo: '',
  expireDate: '',
  remark: ''
})

const stockRules = {
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批号', trigger: 'blur' }],
  expireDate: [{ required: true, message: '请选择有效期', trigger: 'change' }]
}

const filteredList = computed(() => {
  let list = stockList.value
  if (searchKey.value) {
    const key = searchKey.value.toLowerCase()
    list = list.filter(i => i.drugName.toLowerCase().includes(key))
  }
  if (statusFilter.value) {
    list = list.filter(i => i.status === statusFilter.value)
  }
  return list
})

const asArray = (data) => Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])

const getDaysLeft = (dateStr) => {
  if (!dateStr) return 0
  const expire = new Date(dateStr)
  const today = new Date()
  const diff = Math.ceil((expire - today) / (1000 * 60 * 60 * 24))
  return diff > 0 ? diff : 0
}

const getStatusType = (status) => {
  const map = { NORMAL: 'success', LOW_STOCK: 'warning', EXPIRED_WARNING: 'danger' }
  return map[status] || 'info'
}
const getStatusLabel = (status) => {
  const map = { NORMAL: '正常', LOW_STOCK: '库存不足', EXPIRED_WARNING: '近效期' }
  return map[status] || status
}

const getStockList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getStockListApi()
    stockList.value = asArray(res.data)
  } catch (e) {
    stockList.value = []
    errorMessage.value = e?.message || '库存列表加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const openStockDialog = (row, type) => {
  currentDrug.value = row
  operateType.value = type
  dialogError.value = ''
  Object.assign(stockForm, { quantity: 1, batchNo: row.batchNo || '', expireDate: row.expireDate || '', remark: '' })
  stockDialogVisible.value = true
}

const submitStockOperate = async () => {
  dialogError.value = ''
  try {
    await stockFormRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    if (operateType.value === 'IN') {
      // 后端 InboundRequest 要求 batchNo + expireDate + quantity（缺则 400）
      await stockInApi(currentDrug.value.id, {
        quantity: stockForm.quantity,
        batchNo: stockForm.batchNo,
        expireDate: stockForm.expireDate,
        remark: stockForm.remark
      })
      ElMessage.success('入库成功')
    } else {
      await stockOutApi(currentDrug.value.id, stockForm.quantity, stockForm.remark)
      ElMessage.success('出库成功')
    }
    stockDialogVisible.value = false
    getStockList()
  } catch (e) {
    dialogError.value = e?.message || '库存操作失败，请重试'
    ElMessage.error(dialogError.value)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  getStockList()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  gap: 12px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.filter-control {
  width: 160px;
}
.search-control {
  width: 220px;
}
.stock-action {
  min-height: var(--touch-target);
  min-width: 76px;
}
.low-stock {
  color: var(--danger-color);
  font-weight: 600;
}
.text-danger {
  color: var(--danger-color);
  font-weight: 600;
}
.stock-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, .86);
  box-shadow: 0 12px 30px rgba(15, 35, 95, .06);
}
.stock-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.stock-card__header > div {
  min-width: 0;
}
.stock-card__name,
.stock-card__meta {
  margin: 0;
}
.stock-card__name {
  overflow-wrap: anywhere;
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}
.stock-card__meta {
  margin-top: 4px;
  font-family: var(--font-mono, monospace);
  font-size: var(--font-sm);
  color: var(--text-secondary);
}
.stock-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}
.stock-card__fields div {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}
.stock-card__fields dt,
.stock-card__fields dd {
  margin: 0;
}
.stock-card__fields dt {
  color: var(--text-secondary);
}
.stock-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
  text-align: right;
}
.stock-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.stock-card__actions .el-button {
  width: 100%;
  min-height: var(--touch-target);
  margin-left: 0;
}
.stock-form :deep(.el-form-item__label) {
  color: var(--text-primary);
  font-weight: 600;
  line-height: 1.4;
}
.stock-form :deep(.el-input-number) {
  width: 100%;
}
.inline-error {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid rgba(185, 28, 28, .22);
  border-radius: var(--radius-sm);
  background: #fef2f2;
  color: var(--el-color-danger);
  font-size: var(--font-sm);
  line-height: 1.6;
}

@media (max-width: 768px) {
  .page-header,
  .header-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-control,
  .search-control,
  .header-actions .stock-action {
    width: 100%;
  }

  .header-actions :deep(.el-input__wrapper),
  .header-actions :deep(.el-select__wrapper) {
    min-height: var(--touch-target);
  }

  :deep(.el-dialog__footer) {
    display: grid;
    gap: 8px;
  }

  :deep(.el-dialog__footer .el-button) {
    width: 100%;
    margin-left: 0;
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
}
</style>

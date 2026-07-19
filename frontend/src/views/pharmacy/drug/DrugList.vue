<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">药品目录</h2>
        <div class="header-actions">
          <el-input
            v-model="searchKey"
            class="filter-control search-control"
            placeholder="搜索药品名称/编号"
            clearable
            @keyup.enter="getDrugList"
          >
            <template #append>
              <el-button icon="Search" aria-label="搜索药品" @click="getDrugList" />
            </template>
          </el-input>
          <el-button type="primary" icon="Plus" class="drug-action" @click="openAddDialog">新增药品</el-button>
        </div>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="filteredList.length === 0"
        loading-text="正在加载药品目录..."
        empty-text="暂无药品记录"
        @retry="getDrugList"
      >
        <ResponsiveTable aria-label="药品目录列表">
          <template #table>
            <el-table :data="filteredList" border stripe>
              <el-table-column prop="drugNo" label="药品编号" width="140" />
              <el-table-column prop="name" label="药品名称" min-width="180" />
              <el-table-column prop="specification" label="规格" width="160" />
              <el-table-column prop="manufacturer" label="生产厂家" min-width="160" show-overflow-tooltip />
              <el-table-column label="库存" width="110" align="center">
                <template #default="{ row }">
                  {{ formatStock(row) }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="172" fixed="right">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button class="table-action" type="primary" link @click="handleEdit(row)">编辑</el-button>
                    <el-button class="table-action" type="danger" link @click="handleToggleStatus(row)">
                      {{ row.status === 'DISABLED' ? '启用' : '禁用' }}
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template #card>
            <article
              v-for="row in filteredList"
              :key="row.id || row.drugId || row.drugNo"
              class="drug-card"
              data-testid="responsive-drug-card"
            >
              <div class="drug-card__header">
                <div>
                  <p class="drug-card__name">{{ row.name || '-' }}</p>
                  <p class="drug-card__meta">{{ row.drugNo || '-' }} · {{ row.specification || '-' }}</p>
                </div>
                <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
              </div>

              <dl class="drug-card__fields">
                <div>
                  <dt>分类</dt>
                  <dd>{{ row.category || '-' }}</dd>
                </div>
                <div>
                  <dt>生产厂家</dt>
                  <dd>{{ row.manufacturer || '-' }}</dd>
                </div>
                <div>
                  <dt>库存</dt>
                  <dd>{{ formatStock(row) }}</dd>
                </div>
                <div>
                  <dt>单价</dt>
                  <dd>{{ formatPrice(row.price) }}</dd>
                </div>
              </dl>

              <div class="drug-card__actions">
                <el-button type="primary" plain :aria-label="`编辑${row.name || '药品'}`" @click="handleEdit(row)">
                  编辑
                </el-button>
                <el-button type="danger" plain :aria-label="`${row.status === 'DISABLED' ? '启用' : '禁用'}${row.name || '药品'}`" @click="handleToggleStatus(row)">
                  {{ row.status === 'DISABLED' ? '启用' : '禁用' }}
                </el-button>
              </div>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑药品' : '新增药品'" width="min(560px, calc(100vw - 32px))">
      <el-form :model="form" :rules="rules" ref="formRef" class="drug-form" label-position="top">
        <el-form-item label="药品编号" prop="drugNo">
          <el-input v-model="form.drugNo" placeholder="自动生成" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="药品名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入药品名称" />
        </el-form-item>
        <el-form-item label="规格" prop="specification">
          <el-input v-model="form.specification" placeholder="如：0.5g*24粒" />
        </el-form-item>
        <el-form-item label="生产厂家" prop="manufacturer">
          <el-input v-model="form.manufacturer" placeholder="请输入生产厂家" />
        </el-form-item>
        <el-form-item label="批准文号" prop="approvalNo">
          <el-input v-model="form.approvalNo" placeholder="国药准字..." />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option label="抗生素" value="抗生素" />
            <el-option label="解热镇痛" value="解热镇痛" />
            <el-option label="降压药" value="降压药" />
            <el-option label="降糖药" value="降糖药" />
            <el-option label="胃药" value="胃药" />
            <el-option label="祛痰药" value="祛痰药" />
            <el-option label="维生素" value="维生素" />
            <el-option label="中成药" value="中成药" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="单价（元）" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-select v-model="form.unit" placeholder="请选择单位" style="width: 100%">
            <el-option label="盒" value="盒" />
            <el-option label="瓶" value="瓶" />
            <el-option label="支" value="支" />
            <el-option label="袋" value="袋" />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="dialogError" class="inline-error" role="alert">{{ dialogError }}</div>
      <template #footer>
        <el-button class="drug-action" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="drug-action" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDrugListApi, addDrugApi } from '@/api/drug'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const loading = ref(false)
const errorMessage = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const dialogError = ref('')
const searchKey = ref('')
const formRef = ref()
const drugList = ref([])

const form = reactive({
  drugNo: '',
  name: '',
  specification: '',
  manufacturer: '',
  approvalNo: '',
  category: '',
  price: 0,
  unit: '盒',
  status: 'NORMAL'
})

const rules = {
  name: [{ required: true, message: '请输入药品名称', trigger: 'blur' }],
  specification: [{ required: true, message: '请输入规格', trigger: 'blur' }],
  manufacturer: [{ required: true, message: '请输入生产厂家', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入单价', trigger: 'blur' }]
}

const filteredList = computed(() => {
  if (!searchKey.value) return drugList.value
  const key = searchKey.value.toLowerCase()
  return drugList.value.filter(i =>
    String(i.name || '').toLowerCase().includes(key) ||
    String(i.drugNo || '').toLowerCase().includes(key)
  )
})

const asArray = (data) => Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
const getErrorMessage = (error, fallback) => error?.response?.data?.message || error?.message || fallback
const formatStock = (row) => {
  const quantity = row.stock ?? row.stockQuantity ?? 0
  return `${quantity}${row.unit ? ' ' + row.unit : ''}`
}
const formatPrice = (price) => {
  if (price === undefined || price === null || price === '') return '-'
  return `¥${Number(price).toFixed(2)}`
}
const getStatusType = (status) => {
  const map = { NORMAL: 'success', LOW_STOCK: 'warning', EXPIRED_WARNING: 'danger', DISABLED: 'info' }
  return map[status] || 'info'
}
const getStatusLabel = (status) => {
  const map = { NORMAL: '正常', LOW_STOCK: '库存不足', EXPIRED_WARNING: '近效期', DISABLED: '已禁用' }
  return map[status] || status
}

const getDrugList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getDrugListApi()
    drugList.value = asArray(res.data)
  } catch (e) {
    drugList.value = []
    errorMessage.value = getErrorMessage(e, '药品目录加载失败，请重试')
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  isEdit.value = false
  dialogError.value = ''
  Object.assign(form, {
    drugNo: 'DRG' + Date.now().toString().slice(-6),
    name: '', specification: '', manufacturer: '', approvalNo: '',
    category: '', price: 0, unit: '盒', status: 'NORMAL'
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogError.value = ''
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleToggleStatus = (row) => {
  row.status = row.status === 'DISABLED' ? 'NORMAL' : 'DISABLED'
  ElMessage.success(row.status === 'DISABLED' ? '已禁用' : '已启用')
}

const submitForm = async () => {
  dialogError.value = ''
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await addDrugApi(form)
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    dialogVisible.value = false
    getDrugList()
  } catch (e) {
    dialogError.value = getErrorMessage(e, '药品保存失败，请重试')
    ElMessage.error(dialogError.value)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  getDrugList()
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
  width: 180px;
}
.search-control {
  width: 240px;
}
.drug-action {
  min-height: var(--touch-target);
  min-width: 76px;
}
.table-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.table-actions .el-button {
  margin-left: 0;
}
.table-action {
  min-height: var(--touch-target);
  min-width: var(--touch-target);
}
.drug-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, .86);
  box-shadow: 0 12px 30px rgba(15, 35, 95, .06);
}
.drug-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.drug-card__header > div {
  min-width: 0;
}
.drug-card__name,
.drug-card__meta {
  margin: 0;
}
.drug-card__name {
  overflow-wrap: anywhere;
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}
.drug-card__meta {
  margin-top: 4px;
  font-family: var(--font-mono, monospace);
  font-size: var(--font-sm);
  color: var(--text-secondary);
}
.drug-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}
.drug-card__fields div {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}
.drug-card__fields dt,
.drug-card__fields dd {
  margin: 0;
}
.drug-card__fields dt {
  color: var(--text-secondary);
}
.drug-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
  text-align: right;
}
.drug-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.drug-card__actions .el-button {
  width: 100%;
  min-height: var(--touch-target);
  margin-left: 0;
}
.drug-form :deep(.el-form-item__label) {
  color: var(--text-primary);
  font-weight: 600;
  line-height: 1.4;
}
.drug-form :deep(.el-input-number) {
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
  .header-actions .drug-action {
    width: 100%;
  }

  .header-actions :deep(.el-input__wrapper) {
    min-height: var(--touch-target);
  }

  .header-actions :deep(.el-input-group__append .el-button) {
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

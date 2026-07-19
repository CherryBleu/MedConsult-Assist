<template>
  <div class="page-container">
    <div class="card-box admin-drug-page">
      <div class="page-header">
        <div class="page-heading">
          <h2 class="page-title">药品管理</h2>
          <p class="page-subtitle">共 {{ filteredList.length }} 条药品记录</p>
        </div>
        <div class="header-actions">
          <el-input
            v-model="searchKey"
            class="filter-control search-control"
            aria-label="搜索药品名称或编号"
            placeholder="搜索药品名称/编号"
            clearable
            @keyup.enter="getDrugList"
          >
            <template #append>
              <el-button :icon="Search" aria-label="搜索药品" :loading="loading" @click="getDrugList" />
            </template>
          </el-input>
          <el-button class="drug-action" :icon="Refresh" :loading="loading" @click="getDrugList">刷新</el-button>
          <el-button type="primary" class="drug-action" :icon="Plus" @click="openAddDialog">新增药品</el-button>
        </div>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="filteredList.length === 0"
        loading-text="正在加载药品列表..."
        empty-text="暂无药品记录"
        @retry="getDrugList"
      >
        <section
          class="admin-table-scroll"
          data-testid="admin-drug-table-scroll"
          aria-label="药品列表横向滚动区域"
        >
          <el-table class="admin-drug-table" :data="filteredList" border stripe>
            <el-table-column prop="drugNo" label="药品编号" width="140" />
            <el-table-column prop="name" label="药品名称" min-width="190" />
            <el-table-column prop="specification" label="规格" width="160" />
            <el-table-column prop="manufacturer" label="生产厂家" min-width="180" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="120">
              <template #default="{ row }">{{ row.category || '-' }}</template>
            </el-table-column>
            <el-table-column label="库存" width="120" align="center">
              <template #default="{ row }">{{ formatStock(row) }}</template>
            </el-table-column>
            <el-table-column label="单价" width="110" align="right">
              <template #default="{ row }">{{ formatPrice(row.price) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="getStatusType(STOCK_STATUS, row.status)" size="small">
                  {{ getStatusLabel(STOCK_STATUS, row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right" align="center">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button
                    class="table-action"
                    size="small"
                    type="primary"
                    link
                    :aria-label="`编辑${row.name || '药品'}`"
                    @click="handleEdit(row)"
                  >
                    编辑
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </PageState>
    </div>

    <el-dialog
      v-model="dialogVisible"
      class="admin-drug-dialog"
      :title="isEdit ? '编辑药品' : '新增药品'"
      width="min(620px, calc(100vw - 48px))"
      transition="admin-drug-fade"
      destroy-on-close
    >
      <div class="dialog-scroll-body" data-testid="admin-drug-dialog-body">
        <el-form ref="formRef" :model="form" :rules="rules" class="drug-form" label-position="top">
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
          <el-form-item label="分类" prop="category">
            <el-select
              v-model="form.category"
              class="category-select"
              aria-label="选择药品分类"
              placeholder="请选择分类"
              style="width: 100%"
            >
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
            <el-select v-model="form.unit" aria-label="选择药品单位" placeholder="请选择单位" style="width: 100%">
              <el-option label="盒" value="盒" />
              <el-option label="瓶" value="瓶" />
              <el-option label="支" value="支" />
              <el-option label="袋" value="袋" />
            </el-select>
          </el-form-item>
        </el-form>
        <div v-if="dialogError" class="inline-error" role="alert" aria-live="assertive">
          {{ dialogError }}
        </div>
      </div>
      <template #footer>
        <el-button class="drug-action" :disabled="submitting" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="drug-action" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { STOCK_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { getDrugListApi, addDrugApi } from '@/api/drug'
import PageState from '@/components/common/PageState.vue'

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
  return drugList.value.filter((item) =>
    String(item.name || '').toLowerCase().includes(key) ||
    String(item.drugNo || '').toLowerCase().includes(key) ||
    String(item.manufacturer || '').toLowerCase().includes(key)
  )
})

const asArray = (data) => Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])
const getErrorMessage = (error, fallback) => error?.response?.data?.message || error?.message || fallback

const formatStock = (row) => {
  const quantity = row.stock ?? row.stockQuantity
  if (quantity === undefined || quantity === null || quantity === '') return '-'
  return `${quantity}${row.unit ? ' ' + row.unit : ''}`
}

const formatPrice = (price) => {
  if (price === undefined || price === null || price === '') return '-'
  return `¥${Number(price).toFixed(2)}`
}

const resetForm = () => {
  Object.assign(form, {
    drugNo: 'DRG' + Date.now().toString().slice(-6),
    name: '',
    specification: '',
    manufacturer: '',
    category: '',
    price: 0,
    unit: '盒',
    status: 'NORMAL'
  })
}

const getDrugList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getDrugListApi()
    drugList.value = asArray(res.data)
  } catch (error) {
    drugList.value = []
    errorMessage.value = getErrorMessage(error, '药品列表加载失败，请重试')
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  isEdit.value = false
  dialogError.value = ''
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogError.value = ''
  Object.assign(form, { unit: '盒', price: 0, category: '', ...row })
  dialogVisible.value = true
}

const submitForm = async () => {
  dialogError.value = ''
  if (isEdit.value) {
    ElMessage.info('药品编辑功能暂未开放（后端无更新接口）')
    dialogVisible.value = false
    return
  }

  try {
    await formRef.value?.validate()
  } catch (error) {
    return
  }

  submitting.value = true
  try {
    await addDrugApi({ ...form })
    ElMessage.success('新增成功')
    dialogVisible.value = false
    getDrugList()
  } catch (error) {
    dialogError.value = getErrorMessage(error, '药品保存失败，请重试')
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
.admin-drug-page.card-box {
  min-width: 0;
  padding: 18px;
  border-radius: var(--radius-sm);
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.page-heading {
  min-width: 0;
}

.page-title,
.page-subtitle {
  margin: 0;
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.page-subtitle {
  margin-top: 4px;
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.filter-control {
  width: 220px;
}

.search-control {
  width: 260px;
}

.drug-action {
  min-width: 76px;
  min-height: var(--touch-target);
  transition: border-color var(--motion-base) ease, box-shadow var(--motion-base) ease, transform var(--motion-fast) ease;
}

.drug-action:focus-visible,
.table-action:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

.admin-table-scroll {
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  scrollbar-gutter: stable;
}

.admin-drug-table {
  min-width: 1120px;
}

.admin-drug-table :deep(.el-table__header th) {
  background: #f8fafc;
  color: var(--text-regular);
  font-weight: 700;
}

.admin-drug-table :deep(.el-table__row) {
  transition: background-color var(--motion-fast) ease;
}

.table-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.table-actions .el-button {
  margin-left: 0;
}

.table-action {
  min-width: var(--touch-target);
  min-height: var(--touch-target);
}

:deep(.admin-drug-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 48px);
  margin: 24px auto !important;
  overflow: hidden;
  border-radius: var(--radius-sm);
}

:deep(.admin-drug-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  padding-top: 12px;
  padding-bottom: 12px;
}

:deep(.admin-drug-dialog .el-dialog__footer) {
  border-top: 1px solid var(--border-lighter);
}

.dialog-scroll-body {
  max-height: 430px;
  max-height: min(430px, calc(100vh - 260px));
  overflow-y: auto;
  padding-right: 4px;
}

.drug-form :deep(.el-form-item) {
  margin-bottom: 14px;
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
  padding: 10px 12px;
  border: 1px solid rgba(185, 28, 28, .22);
  border-radius: var(--radius-sm);
  background: #fef2f2;
  color: var(--el-color-danger);
  font-size: var(--font-sm);
  line-height: 1.6;
}

:global(.admin-drug-fade-enter-active),
:global(.admin-drug-fade-leave-active) {
  transition: opacity var(--motion-fast) ease;
}

:global(.admin-drug-fade-enter-from),
:global(.admin-drug-fade-leave-to) {
  opacity: 0;
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

  :deep(.admin-drug-dialog .el-dialog__footer) {
    display: grid;
    gap: 8px;
  }

  :deep(.admin-drug-dialog .el-dialog__footer .el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>

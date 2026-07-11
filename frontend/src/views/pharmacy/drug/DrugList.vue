<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">药品目录</h2>
        <div class="header-actions">
          <el-input v-model="searchKey" placeholder="搜索药品名称/编号" clearable style="width: 240px" @keyup.enter="getDrugList">
            <template #append><el-button icon="Search" @click="getDrugList" /></template>
          </el-input>
          <el-button type="primary" @click="openAddDialog">新增药品</el-button>
        </div>
      </div>

      <el-table :data="filteredList" v-loading="loading" border stripe>
        <el-table-column prop="drugNo" label="药品编号" width="140" />
        <el-table-column prop="name" label="药品名称" min-width="180" />
        <el-table-column prop="specification" label="规格" width="160" />
        <el-table-column prop="manufacturer" label="生产厂家" min-width="160" show-overflow-tooltip />
        <el-table-column label="库存" width="100" align="center">
          <template #default="{ row }">
            {{ row.stock ?? row.stockQuantity ?? 0 }}{{ row.unit ? ' ' + row.unit : '' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="handleToggleStatus(row)">
              {{ row.status === 'DISABLED' ? '启用' : '禁用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑药品' : '新增药品'" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
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
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDrugListApi, addDrugApi } from '@/api/drug'

const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
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
    i.name.toLowerCase().includes(key) ||
    i.drugNo.toLowerCase().includes(key)
  )
})

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
  try {
    const res = await getDrugListApi()
    drugList.value = res.data
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  isEdit.value = false
  Object.assign(form, {
    drugNo: 'DRG' + Date.now().toString().slice(-6),
    name: '', specification: '', manufacturer: '', approvalNo: '',
    category: '', price: 0, unit: '盒', status: 'NORMAL'
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleToggleStatus = (row) => {
  row.status = row.status === 'DISABLED' ? 'NORMAL' : 'DISABLED'
  ElMessage.success(row.status === 'DISABLED' ? '已禁用' : '已启用')
}

const submitForm = async () => {
  await formRef.value.validate()
  submitting.value = true
  try {
    await addDrugApi(form)
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    dialogVisible.value = false
    getDrugList()
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
}
</style>

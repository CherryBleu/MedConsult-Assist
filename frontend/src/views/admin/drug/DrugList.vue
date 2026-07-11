<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">药品管理</h2>
        <el-button type="primary" @click="openAddDialog">新增药品</el-button>
      </div>

      <el-table :data="drugList" v-loading="loading" border stripe>
        <el-table-column prop="drugNo" label="药品编号" width="120" />
        <el-table-column prop="name" label="药品名称" width="200" />
        <el-table-column prop="specification" label="规格" width="160" />
        <el-table-column prop="manufacturer" label="生产厂家" width="180" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column label="单价" width="100">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(STOCK_STATUS, row.status)">
              {{ getStatusLabel(STOCK_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑药品' : '新增药品'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="药品编号">
          <el-input v-model="form.drugNo" />
        </el-form-item>
        <el-form-item label="药品名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="form.specification" />
        </el-form-item>
        <el-form-item label="生产厂家">
          <el-input v-model="form.manufacturer" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" />
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="form.price" :min="0" :precision="2" />
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
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { STOCK_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { getDrugListApi, addDrugApi } from '@/api/drug'

const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const drugList = ref([])

const form = reactive({
  drugNo: '',
  name: '',
  specification: '',
  manufacturer: '',
  category: '',
  price: 0
})

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
  Object.assign(form, { drugNo: '', name: '', specification: '', manufacturer: '', category: '', price: 0 })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

const submitForm = async () => {
  submitting.value = true
  try {
    if (isEdit.value) {
      // 后端暂无药品更新接口，编辑为占位提示
      ElMessage.info('药品编辑功能暂未开放（后端无更新接口）')
      dialogVisible.value = false
    } else {
      await addDrugApi(form)
      ElMessage.success('新增成功')
      dialogVisible.value = false
      getDrugList()
    }
  } finally {
    submitting.value = false
  }
}

getDrugList()
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
</style>
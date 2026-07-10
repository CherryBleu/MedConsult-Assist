<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存管理</h2>
        <div class="header-actions">
          <el-input v-model="searchKey" placeholder="搜索药品名称" clearable style="width: 220px" />
          <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 140px">
            <el-option label="正常" value="NORMAL" />
            <el-option label="库存不足" value="LOW_STOCK" />
            <el-option label="近效期" value="EXPIRED_WARNING" />
          </el-select>
          <el-button type="primary" @click="getStockList">刷新</el-button>
        </div>
      </div>

      <el-table :data="filteredList" v-loading="loading" border stripe>
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
    </div>

    <el-dialog v-model="stockDialogVisible" :title="operateType === 'IN' ? '药品入库' : '药品出库'" width="480px">
      <el-form :model="stockForm" :rules="stockRules" ref="stockFormRef" label-width="90px">
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
      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStockOperate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getStockListApi, stockInApi, stockOutApi } from '@/api/drug'

const loading = ref(false)
const stockDialogVisible = ref(false)
const operateType = ref('IN')
const submitting = ref(false)
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
  try {
    const res = await getStockListApi()
    stockList.value = res.data
  } finally {
    loading.value = false
  }
}

const openStockDialog = (row, type) => {
  currentDrug.value = row
  operateType.value = type
  Object.assign(stockForm, { quantity: 1, batchNo: row.batchNo || '', expireDate: row.expireDate || '', remark: '' })
  stockDialogVisible.value = true
}

const submitStockOperate = async () => {
  await stockFormRef.value.validate()
  submitting.value = true
  try {
    if (operateType.value === 'IN') {
      await stockInApi(currentDrug.value.id, stockForm.quantity, stockForm.remark)
      ElMessage.success('入库成功')
    } else {
      await stockOutApi(currentDrug.value.id, stockForm.quantity, stockForm.remark)
      ElMessage.success('出库成功')
    }
    stockDialogVisible.value = false
    getStockList()
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
.low-stock {
  color: var(--danger-color);
  font-weight: 600;
}
.text-danger {
  color: var(--danger-color);
  font-weight: 600;
}
</style>

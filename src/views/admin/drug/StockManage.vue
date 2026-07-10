<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存管理</h2>
      </div>

      <el-table :data="stockList" v-loading="loading" border stripe>
        <el-table-column prop="drugName" label="药品名称" width="200" />
        <el-table-column prop="specification" label="规格" width="160" />
        <el-table-column prop="batchNo" label="批号" width="120" />
        <el-table-column label="库存数量" width="120">
          <template #default="{ row }">
            <span :class="{ 'low-stock': row.status === 'LOW_STOCK' }">
              {{ row.stockQuantity }} {{ row.unit }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="warningQuantity" label="预警值" width="100" align="center" />
        <el-table-column prop="expireDate" label="有效期" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(STOCK_STATUS, row.status)">
              {{ getStatusLabel(STOCK_STATUS, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" link @click="openStockDialog(row, 'in')">入库</el-button>
            <el-button size="small" type="warning" link @click="openStockDialog(row, 'out')">出库</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 出入库弹窗 -->
    <el-dialog v-model="stockDialogVisible" :title="operateType === 'in' ? '药品入库' : '药品出库'" width="400px">
      <el-form label-width="80px">
        <el-form-item label="药品名称">
          <span>{{ currentDrug?.drugName }}</span>
        </el-form-item>
        <el-form-item label="当前库存">
          <span>{{ currentDrug?.stockQuantity }} {{ currentDrug?.unit }}</span>
        </el-form-item>
        <el-form-item label="操作数量">
          <el-input-number v-model="operateQuantity" :min="1" :max="9999" />
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
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { STOCK_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { getStockListApi, stockInApi, stockOutApi } from '@/api/drug'

const loading = ref(false)
const stockDialogVisible = ref(false)
const operateType = ref('in')
const submitting = ref(false)
const stockList = ref([])
const currentDrug = ref(null)
const operateQuantity = ref(1)

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
  operateQuantity.value = 1
  stockDialogVisible.value = true
}

const submitStockOperate = async () => {
  submitting.value = true
  try {
    if (operateType.value === 'in') {
      await stockInApi(currentDrug.value.id, operateQuantity.value)
      ElMessage.success('入库成功')
    } else {
      await stockOutApi(currentDrug.value.id, operateQuantity.value)
      ElMessage.success('出库成功')
    }
    stockDialogVisible.value = false
    getStockList()
  } finally {
    submitting.value = false
  }
}

getStockList()
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.low-stock {
  color: var(--danger-color);
  font-weight: 600;
}
</style>
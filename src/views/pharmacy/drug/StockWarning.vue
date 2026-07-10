<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存预警</h2>
        <div class="header-actions">
          <el-radio-group v-model="warningTypeFilter" size="default">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="LOW_STOCK">库存不足</el-radio-button>
            <el-radio-button value="EXPIRED_WARNING">近效期</el-radio-button>
          </el-radio-group>
          <el-tag type="danger" effect="dark">共 {{ filteredList.length }} 条预警</el-tag>
        </div>
      </div>

      <el-table :data="filteredList" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="drugName" label="药品名称" min-width="180" />
        <el-table-column prop="specification" label="规格" width="150" />
        <el-table-column prop="batchNo" label="批号" width="120" />
        <el-table-column label="预警类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.warningType === 'LOW_STOCK' ? 'warning' : 'danger'" size="small">
              {{ row.warningType === 'LOW_STOCK' ? '库存不足' : '近效期预警' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前库存" width="120" align="center">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.warningType === 'LOW_STOCK' }">
              {{ row.stockQuantity }} {{ row.unit }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="预警阈值" width="100" align="center">
          <template #default="{ row }">{{ row.warningQuantity }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column prop="expireDate" label="有效期至" width="120" align="center" />
        <el-table-column label="剩余天数" width="100" align="center">
          <template #default="{ row }">
            <el-progress
              v-if="row.warningType === 'EXPIRED_WARNING'"
              :percentage="Math.min(100, Math.round((row.daysLeft / 90) * 100))"
              :color="row.daysLeft < 30 ? '#f56c6c' : '#e6a23c'"
              :stroke-width="16"
              :text-inside="true"
              :format="() => row.daysLeft + '天'"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="success" link @click="goStockIn(row)">立即入库</el-button>
            <el-button size="small" type="primary" link @click="viewFlow(row)">查看流水</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStockWarningApi } from '@/api/drug'

const router = useRouter()
const loading = ref(false)
const warningList = ref([])
const warningTypeFilter = ref('')

const filteredList = computed(() => {
  if (!warningTypeFilter.value) return warningList.value
  return warningList.value.filter(i => i.warningType === warningTypeFilter.value)
})

const getWarningList = async () => {
  loading.value = true
  try {
    const res = await getStockWarningApi()
    warningList.value = res.data
  } finally {
    loading.value = false
  }
}

const goStockIn = () => {
  router.push('/pharmacy/stock')
}

const viewFlow = () => {
  router.push('/pharmacy/stock-flow')
}

onMounted(() => {
  getWarningList()
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
.text-danger {
  color: var(--danger-color);
  font-weight: 600;
}
</style>

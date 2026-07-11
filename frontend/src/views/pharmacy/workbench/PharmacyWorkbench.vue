<template>
  <div class="page-container">
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-value">{{ totalDrugs }}</div>
        <div class="stat-label">药品总数</div>
      </div>
      <div class="stat-card warning">
        <div class="stat-value">{{ lowStockCount }}</div>
        <div class="stat-label">库存预警</div>
      </div>
      <div class="stat-card danger">
        <div class="stat-value">{{ expireWarningCount }}</div>
        <div class="stat-label">近效期提醒</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ todayInOut }}</div>
        <div class="stat-label">今日出入库</div>
      </div>
    </div>

    <div class="content-row">
      <div class="card-box flex-1">
        <div class="section-header">
          <h3 class="section-title">待处理预警</h3>
          <el-button type="primary" link @click="$router.push('/pharmacy/stock-warning')">查看全部</el-button>
        </div>
        <div v-loading="loading" class="warning-list">
          <div v-for="item in warningList" :key="item.id" class="warning-item">
            <div class="warning-info">
              <div class="warning-header">
                <span class="drug-name">{{ item.drugName }}</span>
                <el-tag :type="item.warningType === 'LOW_STOCK' ? 'warning' : 'danger'" size="small">
                  {{ item.warningType === 'LOW_STOCK' ? '库存不足' : '近效期' }}
                </el-tag>
              </div>
              <div class="warning-detail">
                <span v-if="item.warningType === 'LOW_STOCK'">当前库存：{{ item.stockQuantity }}{{ item.unit }}，预警阈值：{{ item.warningQuantity }}{{ item.unit }}</span>
                <span v-else>有效期至：{{ item.expireDate }}，剩余 {{ item.daysLeft }} 天</span>
              </div>
            </div>
            <el-button type="primary" size="small" @click="goStockIn(item)">立即处理</el-button>
          </div>
          <el-empty v-if="!loading && warningList.length === 0" :image-size="80" description="暂无待处理预警" />
        </div>
      </div>

      <div class="card-box quick-card">
        <h3 class="section-title">快捷功能</h3>
        <div class="quick-grid">
          <div class="quick-item" @click="$router.push('/pharmacy/drug')">
            <el-icon :size="24"><FirstAidKit /></el-icon>
            <span>药品目录</span>
          </div>
          <div class="quick-item" @click="$router.push('/pharmacy/stock')">
            <el-icon :size="24"><Box /></el-icon>
            <span>库存管理</span>
          </div>
          <div class="quick-item" @click="$router.push('/pharmacy/stock-warning')">
            <el-icon :size="24"><Warning /></el-icon>
            <span>库存预警</span>
          </div>
          <div class="quick-item" @click="$router.push('/pharmacy/stock-flow')">
            <el-icon :size="24"><Document /></el-icon>
            <span>库存流水</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { FirstAidKit, Box, Warning, Document } from '@element-plus/icons-vue'
import { getStockWarningApi, getStockListApi, getDrugListApi } from '@/api/drug'

const router = useRouter()
const loading = ref(false)
const warningList = ref([])
const stockList = ref([])
const drugList = ref([])

const totalDrugs = computed(() => drugList.value.length)
const lowStockCount = computed(() => warningList.value.filter(i => i.warningType === 'LOW_STOCK').length)
const expireWarningCount = computed(() => warningList.value.filter(i => i.warningType === 'EXPIRED_WARNING').length)
const todayInOut = ref(12)

// 从分页或数组响应中取数组（后端 PageResult {items,total} 或直接数组）
const asArray = (data) => Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])

const getWarningList = async () => {
  loading.value = true
  try {
    const [warnRes, stockRes, drugRes] = await Promise.all([
      getStockWarningApi(),
      getStockListApi(),
      getDrugListApi()
    ])
    warningList.value = asArray(warnRes.data).slice(0, 5)
    stockList.value = asArray(stockRes.data)
    drugList.value = asArray(drugRes.data)
  } finally {
    loading.value = false
  }
}

const goStockIn = (item) => {
  router.push('/pharmacy/stock')
}

onMounted(() => {
  getWarningList()
})
</script>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  background: #fff;
  border-radius: var(--radius-base);
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  border-left: 4px solid var(--primary-color);
}
.stat-card.warning {
  border-left-color: #e6a23c;
}
.stat-card.danger {
  border-left-color: #f56c6c;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.stat-card.warning .stat-value { color: #e6a23c; }
.stat-card.danger .stat-value { color: #f56c6c; }
.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.content-row {
  display: flex;
  gap: 20px;
}
.flex-1 { flex: 1; }
.quick-card { width: 320px; flex-shrink: 0; }

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.warning-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.warning-item {
  padding: 14px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.warning-info {
  flex: 1;
}
.warning-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.drug-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.warning-detail {
  font-size: 13px;
  color: var(--text-secondary);
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 0;
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all 0.2s;
  color: var(--text-regular);
}
.quick-item:hover {
  background: var(--bg-hover);
  color: var(--primary-color);
}
</style>

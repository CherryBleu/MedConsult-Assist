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
          <el-button type="primary" link class="workbench-action" @click="navigateTo('/pharmacy/stock-warning')">查看全部</el-button>
        </div>
        <PageState
          :loading="loading"
          :error="loadError"
          :empty="warningList.length === 0"
          loading-text="正在加载库存预警..."
          empty-text="暂无待处理预警"
          @retry="getWarningList"
        >
          <div class="warning-list">
            <article
              v-for="item in warningList"
              :key="item.id"
              class="warning-item"
              data-testid="pharmacy-workbench-warning-item"
            >
              <div class="warning-info">
                <div class="warning-header">
                  <span class="drug-name">{{ item.drugName }}</span>
                  <el-tag :type="warningTypeTag(item.warningType)" size="small">
                    {{ warningTypeLabel(item.warningType) }}
                  </el-tag>
                </div>
                <div class="warning-detail">
                  <span v-if="item.warningType === 'LOW_STOCK'">当前库存：{{ item.stockQuantity }}{{ item.unit }}，预警阈值：{{ item.warningQuantity }}{{ item.unit }}</span>
                  <span v-else>有效期至：{{ item.expireDate }}，剩余 {{ item.daysLeft }} 天</span>
                </div>
              </div>
              <el-button
                type="primary"
                class="warning-action"
                :aria-label="`处理${item.drugName || '药品'}库存预警`"
                @click="goStockIn(item)"
              >
                立即处理
              </el-button>
            </article>
          </div>
        </PageState>
      </div>

      <div class="card-box quick-card">
        <h3 class="section-title">快捷功能</h3>
        <div class="quick-grid">
          <button type="button" class="quick-item" @click="navigateTo('/pharmacy/drug')">
            <el-icon :size="24"><FirstAidKit /></el-icon>
            <span>药品目录</span>
          </button>
          <button type="button" class="quick-item" @click="navigateTo('/pharmacy/stock')">
            <el-icon :size="24"><Box /></el-icon>
            <span>库存管理</span>
          </button>
          <button type="button" class="quick-item" @click="navigateTo('/pharmacy/stock-warning')">
            <el-icon :size="24"><Warning /></el-icon>
            <span>库存预警</span>
          </button>
          <button type="button" class="quick-item" @click="navigateTo('/pharmacy/stock-flow')">
            <el-icon :size="24"><Document /></el-icon>
            <span>库存流水</span>
          </button>
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
import PageState from '@/components/common/PageState.vue'

const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const warningList = ref([])
const stockList = ref([])
const drugList = ref([])

const totalDrugs = computed(() => drugList.value.length)
const lowStockCount = computed(() => warningList.value.filter(i => i.warningType === 'LOW_STOCK').length)
const expireWarningCount = computed(() => warningList.value.filter(i => i.warningType === 'NEAR_EXPIRY').length)
const todayInOut = ref(12)

// 从分页或数组响应中取数组（后端 PageResult {items,total} 或直接数组）
const asArray = (data) => Array.isArray(data) ? data : (data?.items ?? data?.records ?? [])

const getWarningList = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const [warnRes, stockRes, drugRes] = await Promise.all([
      getStockWarningApi(),
      getStockListApi(),
      getDrugListApi()
    ])
    warningList.value = asArray(warnRes.data).slice(0, 5)
    stockList.value = asArray(stockRes.data)
    drugList.value = asArray(drugRes.data)
  } catch (e) {
    warningList.value = []
    loadError.value = e?.message || '库存预警加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const goStockIn = (item) => {
  router.push('/pharmacy/stock')
}

const warningTypeLabel = (type) => type === 'LOW_STOCK' ? '库存不足' : '近效期'

const warningTypeTag = (type) => type === 'LOW_STOCK' ? 'warning' : 'danger'

const navigateTo = (path) => {
  router.push(path)
}

onMounted(() => {
  getWarningList()
})
</script>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.stat-card {
  min-width: 0;
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
  align-items: flex-start;
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
  gap: 12px;
}
.warning-info {
  flex: 1;
  min-width: 0;
}
.warning-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.drug-name {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.warning-detail {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.warning-action {
  min-height: var(--touch-target);
  flex: 0 0 auto;
  touch-action: manipulation;
}
.workbench-action {
  min-height: var(--touch-target);
  touch-action: manipulation;
}
.warning-action:focus-visible,
.workbench-action:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
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
  justify-content: center;
  gap: 8px;
  min-height: 92px;
  padding: 16px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-base);
  background: transparent;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--text-regular);
  font: inherit;
}
.quick-item:hover,
.quick-item:focus-visible {
  background: var(--bg-hover);
  color: var(--primary-color);
  outline: none;
  border-color: rgba(64, 158, 255, .32);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, .12);
}
.quick-item :deep(.el-icon) {
  flex: 0 0 auto;
}

@media (max-width: 900px) {
  .content-row {
    flex-direction: column;
  }

  .flex-1,
  .quick-card {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .stat-card {
    padding: 16px;
  }

  .stat-value {
    font-size: 24px;
  }

  .section-header {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }

  .section-header :deep(.el-button) {
    width: 100%;
    min-height: var(--touch-target);
    margin-left: 0;
  }

  .warning-item {
    align-items: stretch;
    flex-direction: column;
  }

  .warning-header {
    justify-content: space-between;
  }

  .warning-action {
    width: 100%;
  }
}

@media (max-width: 360px) {
  .stat-row,
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存预警</h2>
        <div class="header-actions">
          <el-radio-group
            v-model="warningTypeFilter"
            size="default"
            aria-label="库存预警类型筛选"
            data-testid="stock-warning-type-filter"
          >
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="LOW_STOCK" data-testid="stock-warning-filter-low">库存不足</el-radio-button>
            <el-radio-button value="NEAR_EXPIRY">近效期</el-radio-button>
          </el-radio-group>
          <el-tag type="danger" effect="dark">共 {{ filteredList.length }} 条预警</el-tag>
        </div>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="filteredList.length === 0"
        empty-text="暂无库存预警"
        @retry="getWarningList"
      >
        <ResponsiveTable aria-label="库存预警列表">
          <template #table>
            <section
              class="pharmacy-table-scroll"
              data-testid="pharmacy-stock-warning-table-scroll"
              aria-label="库存预警横向滚动区域"
            >
              <el-table class="pharmacy-stock-warning-table" :data="filteredList" border stripe>
              <el-table-column type="index" label="#" width="50" align="center" />
              <el-table-column prop="drugName" label="药品名称" min-width="180" />
              <el-table-column prop="specification" label="规格" width="150" />
              <el-table-column prop="batchNo" label="批号" width="120" />
              <el-table-column label="预警类型" width="120" align="center">
                <template #default="{ row }">
                  <el-tag :type="warningTypeTag(row.warningType)" size="small">
                    {{ warningTypeLabel(row.warningType) }}
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
                    v-if="row.warningType === 'NEAR_EXPIRY'"
                    :percentage="daysLeftPercent(row.daysLeft)"
                    :color="row.daysLeft < 30 ? '#f56c6c' : '#e6a23c'"
                    :stroke-width="16"
                    :text-inside="true"
                    :format="() => daysLeftText(row)"
                  />
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="176" fixed="right" align="center">
                <template #default="{ row }">
                  <div class="table-actions">
                    <el-button class="table-action" size="small" type="success" link @click="goStockIn(row)">立即入库</el-button>
                    <el-button class="table-action" size="small" type="primary" link @click="viewFlow(row)">查看流水</el-button>
                  </div>
                </template>
              </el-table-column>
              </el-table>
            </section>
          </template>

          <template #card>
            <article
              v-for="row in filteredList"
              :key="row.id || row.drugId || row.batchNo"
              class="warning-card"
              data-testid="responsive-stock-warning-card"
            >
              <div class="warning-card__header">
                <div>
                  <p class="warning-card__name">{{ row.drugName }}</p>
                  <p class="warning-card__meta">{{ row.specification || '-' }} · 批号 {{ row.batchNo || '-' }}</p>
                </div>
                <el-tag :type="warningTypeTag(row.warningType)" size="small">
                  {{ warningTypeLabel(row.warningType) }}
                </el-tag>
              </div>

              <dl class="warning-card__fields">
                <div>
                  <dt>当前库存</dt>
                  <dd :class="{ 'text-danger': row.warningType === 'LOW_STOCK' }">
                    {{ row.stockQuantity }} {{ row.unit }}
                  </dd>
                </div>
                <div>
                  <dt>预警阈值</dt>
                  <dd>{{ row.warningQuantity }} {{ row.unit }}</dd>
                </div>
                <div>
                  <dt>有效期至</dt>
                  <dd>{{ row.expireDate || '-' }}</dd>
                </div>
                <div>
                  <dt>剩余天数</dt>
                  <dd :class="{ 'text-danger': row.daysLeft < 30 }">{{ daysLeftText(row) }}</dd>
                </div>
              </dl>

              <div class="warning-card__actions">
                <el-button type="success" plain @click="goStockIn(row)">立即入库</el-button>
                <el-button type="primary" plain @click="viewFlow(row)">查看流水</el-button>
              </div>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStockWarningApi } from '@/api/drug'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const warningList = ref([])
const warningTypeFilter = ref('')

const filteredList = computed(() => {
  if (!warningTypeFilter.value) return warningList.value
  return warningList.value.filter(i => i.warningType === warningTypeFilter.value)
})

const getWarningList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getStockWarningApi()
    warningList.value = Array.isArray(res.data) ? res.data : (res.data?.items ?? res.data?.records ?? [])
  } catch (error) {
    warningList.value = []
    errorMessage.value = error?.response?.data?.message || error?.message || '库存预警加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const goStockIn = () => {
  router.push('/pharmacy/stock')
}

const viewFlow = (row) => {
  router.push({
    path: '/pharmacy/stock-flow',
    query: row?.drugId ? { drugId: row.drugId } : undefined
  })
}

const warningTypeLabel = (type) => type === 'LOW_STOCK' ? '库存不足' : '近效期预警'

const warningTypeTag = (type) => type === 'LOW_STOCK' ? 'warning' : 'danger'

const daysLeftText = (row) => Number.isFinite(Number(row.daysLeft)) ? `${row.daysLeft}天` : '-'

const daysLeftPercent = (daysLeft) => Math.min(100, Math.max(0, Math.round((Number(daysLeft || 0) / 90) * 100)))

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

.pharmacy-table-scroll {
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  scrollbar-gutter: stable;
}

.pharmacy-stock-warning-table {
  min-width: 1040px;
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

.warning-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(220, 38, 38, .18);
  border-radius: var(--radius-lg);
  background:
    linear-gradient(145deg, rgba(255, 247, 237, .9), rgba(255, 255, 255, .96));
  box-shadow: 0 14px 32px rgba(180, 83, 9, .08);
}

.warning-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.warning-card__name,
.warning-card__meta {
  margin: 0;
}

.warning-card__name {
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.warning-card__meta {
  margin-top: 4px;
  font-family: var(--font-mono, monospace);
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.warning-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}

.warning-card__fields div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(220, 38, 38, .12);
}

.warning-card__fields dt,
.warning-card__fields dd {
  margin: 0;
}

.warning-card__fields dt {
  flex: 0 0 auto;
  color: var(--text-secondary);
}

.warning-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
  text-align: right;
}

.warning-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.warning-card__actions .el-button {
  min-height: var(--touch-target);
  margin-left: 0;
}

@media (max-width: 640px) {
  .page-header,
  .header-actions {
    align-items: stretch;
    flex-direction: column;
  }

  :deep(.header-actions .el-radio-group) {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px;
  }

  :deep(.header-actions .el-radio-button__inner) {
    width: 100%;
    min-height: var(--touch-target);
    border-left: 1px solid var(--el-border-color);
    border-radius: var(--radius-base);
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
}
</style>

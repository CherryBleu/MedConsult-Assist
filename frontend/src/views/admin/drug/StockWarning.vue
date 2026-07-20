<template>
  <div class="page-container">
    <div class="card-box admin-drug-page">
      <div class="page-header">
        <div class="page-heading">
          <h2 class="page-title">库存预警</h2>
          <p class="page-subtitle">共 {{ filteredList.length }} 条预警记录</p>
        </div>
        <div class="header-actions">
          <el-radio-group
            v-model="warningTypeFilter"
            aria-label="库存预警类型筛选"
            data-testid="admin-stock-warning-type-filter"
          >
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="LOW_STOCK" data-testid="admin-stock-warning-filter-low">库存不足</el-radio-button>
            <el-radio-button value="NEAR_EXPIRY">临期预警</el-radio-button>
          </el-radio-group>
          <el-button
            class="warning-action"
            type="primary"
            plain
            :icon="Refresh"
            :loading="loading"
            :disabled="loading"
            aria-label="刷新库存预警"
            @click="getWarningList"
          >
            刷新
          </el-button>
          <el-tag type="danger" effect="dark">预警 {{ filteredList.length }}</el-tag>
        </div>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="filteredList.length === 0"
        loading-text="正在加载库存预警..."
        empty-text="暂无库存预警"
        @retry="getWarningList"
      >
        <ResponsiveTable aria-label="库存预警列表">
          <template #table>
            <section
              class="admin-table-scroll"
              data-testid="admin-stock-warning-table-scroll"
              aria-label="库存预警横向滚动区域"
            >
              <el-table class="admin-stock-warning-table" :data="filteredList" border stripe>
                <el-table-column type="index" label="#" width="56" align="center" />
                <el-table-column prop="drugName" label="药品名称" min-width="190" />
                <el-table-column prop="specification" label="规格" width="160" />
                <el-table-column prop="batchNo" label="批号" width="130">
                  <template #default="{ row }">{{ row.batchNo || '-' }}</template>
                </el-table-column>
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
                <el-table-column label="预警阈值" width="110" align="center">
                  <template #default="{ row }">{{ row.warningQuantity }} {{ row.unit }}</template>
                </el-table-column>
                <el-table-column prop="expireDate" label="有效期至" width="130" align="center">
                  <template #default="{ row }">{{ row.expireDate || '-' }}</template>
                </el-table-column>
                <el-table-column label="剩余天数" width="110" align="center">
                  <template #default="{ row }">
                    <span :class="{ 'text-danger': row.daysLeft < 30 }">{{ daysLeftText(row) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="112" fixed="right" align="center">
                  <template #default="{ row }">
                    <div class="table-actions">
                      <el-button
                        class="table-action"
                        size="small"
                        type="primary"
                        link
                        :aria-label="`查看${row.drugName || '药品'}库存流水`"
                        @click="viewFlow(row)"
                      >
                        查看流水
                      </el-button>
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
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
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
  return warningList.value.filter((item) => item.warningType === warningTypeFilter.value)
})

const getErrorMessage = (error, fallback) => error?.response?.data?.message || error?.message || fallback

const getWarningList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getStockWarningApi()
    warningList.value = Array.isArray(res.data) ? res.data : (res.data?.items ?? res.data?.records ?? [])
  } catch (error) {
    warningList.value = []
    errorMessage.value = getErrorMessage(error, '库存预警加载失败，请重试')
  } finally {
    loading.value = false
  }
}

const viewFlow = (row) => {
  router.push({
    path: '/admin/stock',
    query: row?.drugId ? { drugNo: row.drugId } : undefined
  })
}

const warningTypeLabel = (type) => type === 'LOW_STOCK' ? '库存不足' : '临期预警'

const warningTypeTag = (type) => type === 'LOW_STOCK' ? 'warning' : 'danger'

const daysLeftText = (row) => Number.isFinite(Number(row.daysLeft)) ? `${row.daysLeft} 天` : '-'

onMounted(() => {
  getWarningList()
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
  flex-wrap: wrap;
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

.warning-action {
  min-width: 76px;
  min-height: var(--touch-target);
  transition: border-color var(--motion-base) ease, box-shadow var(--motion-base) ease, transform var(--motion-fast) ease;
}

.warning-action:focus-visible,
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

.admin-stock-warning-table {
  min-width: 1120px;
}

.admin-stock-warning-table :deep(.el-table__header th) {
  background: #f8fafc;
  color: var(--text-regular);
  font-weight: 700;
}

.admin-stock-warning-table :deep(.el-table__row) {
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

.text-danger {
  color: var(--danger-color);
  font-weight: 700;
}

.warning-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(220, 38, 38, .18);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, .96);
  box-shadow: 0 10px 24px rgba(180, 83, 9, .08);
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
  grid-template-columns: 1fr;
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
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    min-height: var(--touch-target);
    border-left: 1px solid var(--el-border-color);
    border-radius: var(--radius-sm);
  }

  .header-actions .warning-action {
    width: 100%;
  }
}
</style>

<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">库存预警</h2>
        <div class="warn-count">
          <el-tag type="danger" effect="dark">共 {{ warningList.length }} 条预警</el-tag>
        </div>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="warningList.length === 0"
        empty-text="暂无库存预警"
        @retry="getWarningList"
      >
        <ResponsiveTable aria-label="库存预警列表">
          <template #table>
            <el-table :data="warningList" border stripe>
              <el-table-column prop="drugName" label="药品名称" width="200" />
              <el-table-column prop="specification" label="规格" width="160" />
              <el-table-column prop="batchNo" label="批号" width="120" />
              <el-table-column label="预警类型" width="120">
                <template #default="{ row }">
                  <el-tag :type="warningTypeTag(row.warningType)" size="small">
                    {{ warningTypeLabel(row.warningType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="当前库存" width="120">
                <template #default="{ row }">
                  <span :class="{ 'text-danger': row.warningType === 'LOW_STOCK' }">
                    {{ row.stockQuantity }} {{ row.unit }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="预警值" width="100" align="center">
                <template #default="{ row }">{{ row.warningQuantity }}</template>
              </el-table-column>
              <el-table-column prop="expireDate" label="有效期" width="120" />
              <el-table-column label="剩余天数" width="100" align="center">
                <template #default="{ row }">
                  <span :class="{ 'text-danger': row.daysLeft < 30 }">{{ daysLeftText(row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" link @click="goStockIn(row)">
                    立即入库
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template #card>
            <article
              v-for="row in warningList"
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
                  <dt>预警值</dt>
                  <dd>{{ row.warningQuantity }} {{ row.unit }}</dd>
                </div>
                <div>
                  <dt>有效期</dt>
                  <dd>{{ row.expireDate || '-' }}</dd>
                </div>
                <div>
                  <dt>剩余天数</dt>
                  <dd :class="{ 'text-danger': row.daysLeft < 30 }">{{ daysLeftText(row) }}</dd>
                </div>
              </dl>

              <div class="warning-card__actions">
                <el-button type="primary" plain @click="goStockIn(row)">立即入库</el-button>
              </div>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStockWarningApi } from '@/api/drug'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const warningList = ref([])

const getWarningList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getStockWarningApi()
    warningList.value = res.data
  } catch (error) {
    errorMessage.value = error?.message || '库存预警加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const goStockIn = (row) => {
  router.push('/admin/stock')
}

const warningTypeLabel = (type) => type === 'LOW_STOCK' ? '库存不足' : '临期预警'

const warningTypeTag = (type) => type === 'LOW_STOCK' ? 'warning' : 'danger'

const daysLeftText = (row) => Number.isFinite(Number(row.daysLeft)) ? `${row.daysLeft} 天` : '-'

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
.text-danger {
  color: var(--danger-color);
  font-weight: 600;
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
}

.warning-card__actions .el-button {
  min-height: var(--touch-target);
  margin-left: 0;
}

@media (max-width: 640px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .warn-count {
    display: flex;
  }
}
</style>

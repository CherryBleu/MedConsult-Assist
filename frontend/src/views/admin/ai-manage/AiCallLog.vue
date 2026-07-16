<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">AI调用日志</h2>
        <div class="stat-bar">
          <span>累计调用：<b>{{ total }}</b> 次</span>
          <span>本页成功：<b class="success">{{ successCount }}</b></span>
          <span>本页失败：<b class="danger">{{ failCount }}</b></span>
        </div>
      </div>

      <el-table :data="logList" v-loading="loading" border stripe>
        <el-table-column prop="logNo" label="日志编号" width="180" />
        <el-table-column prop="serviceType" label="服务类型" width="120" />
        <el-table-column prop="modelName" label="模型名称" width="180" />
        <el-table-column prop="userName" label="调用用户" width="120" />
        <el-table-column label="输入长度" width="100" align="center">
          <template #default="{ row }">{{ row.inputLength }} 字</template>
        </el-table-column>
        <el-table-column label="输出长度" width="100" align="center">
          <template #default="{ row }">{{ row.outputLength }} 字</template>
        </el-table-column>
        <el-table-column label="耗时" width="100" align="center">
          <template #default="{ row }">{{ row.costTime }} ms</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="调用时间" width="180" />
      </el-table>

      <div class="pagination-box">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getLogList"
          @current-change="getLogList"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getAiCallLogApi } from '@/api/ai-manage'

const loading = ref(false)
const logList = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 统计：成功/失败仅基于当前页样本（后端未提供全量聚合接口），
// 总数用 total（全量），避免只统计当前 10 条造成的误导。
const successCount = computed(() => logList.value.filter(i => i.status === 'SUCCESS').length)
const failCount = computed(() => logList.value.filter(i => i.status === 'FAILED').length)

const getLogList = async () => {
  loading.value = true
  try {
    const res = await getAiCallLogApi({ page: pageNum.value, pageSize: pageSize.value })
    // 后端 GET /ai/call-log 返回 PageResult（{records,total,...}），不是数组。
    // 直接赋 res.data 会导致 logList.value.filter 报 "not a function"（render 崩溃）。
    // request 拦截器已补 records 别名（items→records），这里统一取 records 数组。
    const data = res.data
    logList.value = Array.isArray(data) ? data : (data?.records ?? data?.items ?? [])
    if (data && typeof data.total === 'number') total.value = data.total
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getLogList()
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
.stat-bar {
  display: flex;
  gap: 20px;
  font-size: 14px;
  color: var(--text-regular);
}
.stat-bar b {
  color: var(--text-primary);
  font-size: 16px;
}
.stat-bar .success { color: #52c41a; }
.stat-bar .danger { color: #ff4d4f; }
.pagination-box {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
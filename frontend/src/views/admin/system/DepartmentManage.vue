<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">科室管理</h2>
      </div>

      <el-table :data="deptList" v-loading="loading" border stripe>
        <el-table-column prop="name" label="科室名称" width="140" />
        <el-table-column prop="description" label="科室描述" />
        <el-table-column prop="location" label="位置" width="140" />
        <el-table-column prop="doctorCount" label="医生数量" width="100" align="center" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { getDeptListApi } from '@/api/system'

const loading = ref(false)
const deptList = ref([])

const getDeptList = async () => {
  loading.value = true
  try {
    const res = await getDeptListApi()
    deptList.value = res.data
  } finally {
    loading.value = false
  }
}

getDeptList()
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
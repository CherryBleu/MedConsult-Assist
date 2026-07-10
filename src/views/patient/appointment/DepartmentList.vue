<template>
  <div class="page-container">
    <div class="card-box">
      <h2 class="page-title">选择科室</h2>
      
      <div v-loading="loading" class="dept-category">
        <div 
          v-for="item in departmentList" 
          :key="item.id" 
          class="dept-card"
          @click="selectDepartment(item)"
        >
          <div class="dept-card-header">
            <span class="dept-card-name">{{ item.name }}</span>
            <el-tag size="small" v-if="item.hot">热门</el-tag>
          </div>
          <p class="dept-card-desc">{{ item.description }}</p>
          <div class="dept-card-footer">
            <span>{{ item.location }}</span>
            <el-button type="primary" link>去挂号 →</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDepartmentListApi } from '@/api/department'

const router = useRouter()
const loading = ref(false)
const departmentList = ref([])

// 获取科室列表
const getDepartmentList = async () => {
  loading.value = true
  try {
    const res = await getDepartmentListApi()
    departmentList.value = res.data
  } finally {
    loading.value = false
  }
}

const selectDepartment = (dept) => {
  router.push({
    path: '/patient/appointment/doctor',
    query: { deptId: dept.id, deptName: dept.name }
  })
}

onMounted(() => {
  getDepartmentList()
})
</script>

<style scoped>
.page-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 20px;
  color: var(--text-primary);
}

.dept-category {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.dept-card {
  padding: 20px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all 0.2s;
}
.dept-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 4px 12px rgba(22, 119, 255, 0.1);
  transform: translateY(-2px);
}

.dept-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.dept-card-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.dept-card-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 12px;
  min-height: 40px;
}
.dept-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-secondary);
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}
</style>
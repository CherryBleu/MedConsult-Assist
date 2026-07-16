<template>
  <div class="page-container">
    <!-- 智能分诊区域 -->
    <div class="card-box triage-section">
      <div class="triage-header" @click="triageExpanded = !triageExpanded">
        <div class="triage-title-row">
          <el-icon :size="24" color="#1677ff"><Cpu /></el-icon>
          <span class="triage-title">智能分诊</span>
          <span class="triage-subtitle">不知道挂哪个科？输入症状，AI帮您推荐</span>
        </div>
        <el-icon :size="20" :class="{ expanded: triageExpanded }"><ArrowDown /></el-icon>
      </div>

      <div v-show="triageExpanded" class="triage-body">
        <div v-if="!triageResult" class="input-section">
          <el-input
            v-model="symptoms"
            type="textarea"
            :rows="3"
            placeholder="请描述您的症状，例如：咳嗽、咳痰、发热3天..."
            maxlength="200"
            show-word-limit
          />
          <div class="duration-row">
            <span class="duration-label">症状持续时间：</span>
            <el-radio-group v-model="duration" size="small">
              <el-radio value="1天以内">1天以内</el-radio>
              <el-radio value="1-3天">1-3天</el-radio>
              <el-radio value="3-7天">3-7天</el-radio>
              <el-radio value="7天以上">7天以上</el-radio>
            </el-radio-group>
            <el-button type="primary" :loading="triageLoading" @click="handleTriage">
              开始分诊
            </el-button>
          </div>
          <div class="tip-row">
            <el-icon><InfoFilled /></el-icon>
            <span>AI分诊结果仅供参考，不能替代医生诊断，急重症请立即就医。</span>
          </div>
        </div>

        <div v-else class="result-section">
          <div class="result-header">
            <span class="result-label">分诊结果：</span>
            <span v-if="triageResult.emergencyRecommended" class="emergency-tag">建议急诊</span>
            <el-button link type="primary" size="small" @click="resetTriage">重新分诊</el-button>
          </div>
          <div class="dept-recommend">
            <div
              v-for="(item, index) in triageResult.recommendations"
              :key="index"
              class="dept-item"
            >
              <span class="dept-rank">{{ index + 1 }}</span>
              <span class="dept-name">{{ item.departmentName }}</span>
              <span class="dept-reason">{{ item.reason }}</span>
              <el-button type="primary" size="small" @click="goToDeptFromTriage(item.departmentId)">
                去挂号
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="card-box">
      <h2 class="page-title">选择科室</h2>
      
      <div v-loading="loading" class="dept-category">
        <div 
          v-for="item in departmentList" 
          :key="item.id" 
          class="dept-card interactive-card"
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
import { ElMessage } from 'element-plus'
import { Cpu, ArrowDown, InfoFilled } from '@element-plus/icons-vue'
import { getDepartmentListApi } from '@/api/department'
import { triageApi } from '@/api/ai'

const router = useRouter()
const loading = ref(false)
const departmentList = ref([])

// 智能分诊相关状态
const triageExpanded = ref(false)
const symptoms = ref('')
const duration = ref('1-3天')
const triageLoading = ref(false)
const triageResult = ref(null)

const handleTriage = async () => {
  if (!symptoms.value.trim()) {
    ElMessage.warning('请描述您的症状')
    return
  }
  triageLoading.value = true
  try {
    const res = await triageApi({ symptoms: symptoms.value, duration: duration.value })
    triageResult.value = res.data
  } finally {
    triageLoading.value = false
  }
}

const resetTriage = () => {
  triageResult.value = null
  symptoms.value = ''
}

const goToDeptFromTriage = (deptId) => {
  // 找到对应科室名称
  const dept = departmentList.value.find(d => d.id === deptId)
  router.push({
    path: '/patient/appointment/doctor',
    query: { deptId, deptName: dept?.name || '' }
  })
}

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

/* 智能分诊区域 */
.triage-section {
  margin-bottom: 16px;
  padding: 0;
  overflow: hidden;
}
.triage-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  cursor: pointer;
  user-select: none;
  background: linear-gradient(135deg, #e6f4ff 0%, #f0f7ff 100%);
}
.triage-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.triage-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.triage-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
  margin-left: 4px;
}
.triage-header .el-icon:last-child {
  transition: transform 0.3s;
  color: var(--text-secondary);
}
.triage-header .el-icon.expanded:last-child {
  transform: rotate(180deg);
}
.triage-body {
  padding: 20px;
  border-top: 1px solid var(--border-light);
}
.duration-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.duration-label {
  font-size: 14px;
  color: var(--text-primary);
  flex-shrink: 0;
}
.tip-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  background: #fff7e6;
  border-radius: var(--radius-base);
  font-size: 12px;
  color: #d46b08;
  margin-top: 16px;
}

/* 分诊结果 */
.result-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.result-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.emergency-tag {
  padding: 2px 8px;
  background: #fff1f0;
  color: #ff4d4f;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}
.dept-recommend {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.dept-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  background: #fafafa;
}
.dept-rank {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--primary-color);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.dept-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  min-width: 100px;
}
.dept-reason {
  flex: 1;
  font-size: 12px;
  color: var(--text-secondary);
}

.dept-category {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.dept-card {
  padding: 20px;
  border: 1px solid var(--border-lighter);
  border-radius: 18px;
  cursor: pointer;
  background: linear-gradient(180deg, rgba(255,255,255,.94), rgba(248,251,255,.90));
}
.dept-card:hover {
  border-color: rgba(22, 119, 255, .42);
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
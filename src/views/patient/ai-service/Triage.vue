<template>
  <div class="page-container">
    <div class="card-box triage-card">
      <div class="triage-header">
        <el-icon :size="32" color="#1677ff"><Cpu /></el-icon>
        <div>
          <h2 class="title">智能分诊</h2>
          <p class="desc">输入您的症状，AI为您推荐就诊科室</p>
        </div>
      </div>

      <div v-if="!result" class="input-section">
        <el-form label-width="0">
          <el-form-item label="">
            <el-input
              v-model="symptoms"
              type="textarea"
              :rows="4"
              placeholder="请描述您的症状，例如：咳嗽、咳痰、发热3天..."
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="">
            <el-radio-group v-model="duration">
              <el-radio value="1天以内">1天以内</el-radio>
              <el-radio value="1-3天">1-3天</el-radio>
              <el-radio value="3-7天">3-7天</el-radio>
              <el-radio value="7天以上">7天以上</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="">
            <el-button 
              type="primary" 
              size="large" 
              class="submit-btn"
              :loading="submitting"
              @click="handleTriage"
            >
              开始分诊
            </el-button>
          </el-form-item>
        </el-form>

        <div class="tip-box">
          <el-icon><InfoFilled /></el-icon>
          <span>AI分诊结果仅供参考，不能替代医生诊断，急重症请立即就医。</span>
        </div>
      </div>

      <!-- 分诊结果 -->
      <div v-else class="result-section">
        <div class="result-header">
          <h3>分诊结果</h3>
          <el-button link type="primary" @click="reset">重新分诊</el-button>
        </div>

        <div class="risk-bar" :class="result.riskLevel">
          <span>风险等级：{{ riskLabel }}</span>
          <el-tag v-if="result.emergencyRecommended" type="danger">建议急诊</el-tag>
        </div>

        <div class="dept-recommend">
          <h4>推荐就诊科室</h4>
          <div 
            v-for="(item, index) in result.recommendations" 
            :key="index"
            class="dept-item"
          >
            <div class="dept-rank">{{ index + 1 }}</div>
            <div class="dept-info">
              <div class="dept-name">{{ item.departmentName }}</div>
              <div class="dept-reason">{{ item.reason }}</div>
            </div>
            <div class="confidence">
              <div class="confidence-text">置信度 {{ item.confidence }}%</div>
              <el-progress :percentage="item.confidence" :show-text="false" />
            </div>
            <el-button type="primary" size="small" @click="goToDept(item.departmentId)">
              去挂号
            </el-button>
          </div>
        </div>

        <div class="citation-box">
          <div class="citation-title">参考疾病知识</div>
          <el-tag v-for="(item, index) in result.citations" :key="index" effect="plain">
            {{ item }}
          </el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Cpu, InfoFilled } from '@element-plus/icons-vue'
import { triageApi } from '@/api/ai'

const router = useRouter()

const symptoms = ref('')
const duration = ref('1-3天')
const submitting = ref(false)
const result = ref(null)

const riskLabel = computed(() => {
  const map = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '危重' }
  return map[result.value?.riskLevel] || '低风险'
})

const handleTriage = async () => {
  if (!symptoms.value.trim()) {
    ElMessage.warning('请描述您的症状')
    return
  }

  submitting.value = true
  try {
    const res = await triageApi({
      symptoms: symptoms.value,
      duration: duration.value
    })
    result.value = res.data
  } finally {
    submitting.value = false
  }
}

const reset = () => {
  result.value = null
  symptoms.value = ''
}

const goToDept = (deptId) => {
  router.push({
    path: '/patient/appointment/doctor',
    query: { deptId }
  })
}
</script>

<style scoped>
.triage-card {
  max-width: 700px;
  margin: 0 auto;
}

.triage-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-light);
}
.title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}
.desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
}

.submit-btn {
  width: 100%;
}

.tip-box {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  background: #fff7e6;
  border-radius: var(--radius-base);
  font-size: 12px;
  color: #d46b08;
  margin-top: 20px;
}

/* 结果样式 */
.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.result-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.risk-bar {
  padding: 12px 16px;
  border-radius: var(--radius-base);
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.risk-bar.LOW { background: #f6ffed; color: #52c41a; }
.risk-bar.MEDIUM { background: #fff7e6; color: #faad14; }
.risk-bar.HIGH { background: #fff1f0; color: #ff4d4f; }
.risk-bar.CRITICAL { background: #fff1f0; color: #cf1322; }

.dept-recommend h4 {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 12px;
}
.dept-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  margin-bottom: 12px;
}
.dept-rank {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--primary-color);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}
.dept-info {
  flex: 1;
}
.dept-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}
.dept-reason {
  font-size: 12px;
  color: var(--text-secondary);
}
.confidence {
  width: 120px;
  flex-shrink: 0;
}
.confidence-text {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.citation-box {
  margin-top: 20px;
  padding: 16px;
  background: var(--bg-page);
  border-radius: var(--radius-base);
}
.citation-title {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}
</style>
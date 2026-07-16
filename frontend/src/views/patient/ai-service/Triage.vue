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
          <el-form-item label="症状持续时间">
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

        <div class="risk-bar" :class="riskLevel">
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
              <div class="confidence-text">置信度 {{ Math.round((item.confidence || 0) * 100) }}%</div>
              <el-progress :percentage="Math.round((item.confidence || 0) * 100)" :show-text="false" />
            </div>
            <el-button type="primary" size="small" @click="goToDept(item.departmentId)">
              去挂号
            </el-button>
          </div>
          <div v-if="!result.recommendations || result.recommendations.length === 0" class="empty-tip">
            暂无推荐科室，建议前往全科门诊
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// keep-alive include 按组件 name 匹配，必须显式声明 name（与路由 name 一致）
defineOptions({ name: 'Triage' })

import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Cpu, InfoFilled } from '@element-plus/icons-vue'
import { useTriageStore } from '@/store/modules/triage'

const router = useRouter()
const triageStore = useTriageStore()

// 症状/持续时长/请求态/结果全部上提到 Pinia store：组件卸载（路由切走）后状态不丢，
// 切回来时直接读 store 即可恢复"正在加载"或"已有分诊结果"。请求的 Promise
// 也由 store action 持有，组件销毁不影响请求继续。
// storeToRefs 返回可写 ref，v-model 双向绑定直接落到 store 状态上。
const { symptoms, duration, submitting, result } = storeToRefs(triageStore)

// 后端 TriageResponse 无 riskLevel 字段，根据 emergencyRecommended + 最高置信度推导
const riskLevel = computed(() => {
  if (!result.value) return 'LOW'
  if (result.value.emergencyRecommended) return 'HIGH'
  const maxConf = Math.max(...(result.value.recommendations || []).map(r => r.confidence || 0))
  if (maxConf >= 0.8) return 'LOW'
  if (maxConf >= 0.5) return 'MEDIUM'
  return 'MEDIUM'
})

const riskLabel = computed(() => {
  const map = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '危重' }
  return map[riskLevel.value] || '低风险'
})

// 触发分诊：仅做输入校验 + 调 store action，真正的请求/结果写入全在 store.triage 内完成。
const handleTriage = async () => {
  if (!symptoms.value.trim()) {
    ElMessage.warning('请描述您的症状')
    return
  }
  await triageStore.triage()
}

const reset = () => {
  triageStore.reset()
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

.empty-tip {
  padding: 20px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
}
</style>

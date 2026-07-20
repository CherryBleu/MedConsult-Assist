<template>
  <div class="page-container">
    <!-- 智能分诊区域 -->
    <div class="card-box triage-section">
      <button
        type="button"
        class="triage-header"
        :aria-expanded="triageExpanded"
        aria-controls="department-triage-panel"
        @click="triageExpanded = !triageExpanded"
      >
        <div class="triage-title-row">
          <el-icon :size="24" color="#1677ff"><Cpu /></el-icon>
          <span class="triage-title">智能分诊</span>
          <span class="triage-subtitle">不知道挂哪个科？输入症状，AI帮您推荐</span>
        </div>
        <el-icon :size="20" :class="{ expanded: triageExpanded }"><ArrowDown /></el-icon>
      </button>

      <div id="department-triage-panel" v-show="triageExpanded" class="triage-body">
        <div v-if="!triageResult" class="input-section">
          <div class="triage-intake">
            <div class="triage-intake__header">
              <div>
                <span class="triage-intake__eyebrow">先确认症状持续时间</span>
                <h3 class="triage-intake__title">分诊会优先参考持续时长</h3>
              </div>
              <el-button type="primary" :loading="triageLoading" class="triage-cta" @click="handleTriage">
                开始分诊
              </el-button>
            </div>
            <el-radio-group v-model="duration" size="small" class="duration-group">
              <el-radio
                v-for="item in durationOptions"
                :key="item.value"
                :value="item.value"
                border
                class="duration-option"
              >
                <span class="duration-option__label">{{ item.label }}</span>
                <span class="duration-option__hint">{{ item.hint }}</span>
              </el-radio>
            </el-radio-group>
            <el-input
              v-model="symptoms"
              type="textarea"
              :rows="3"
              class="triage-input"
              placeholder="请描述您的症状，例如：咳嗽、咳痰、发热3天..."
              maxlength="200"
              show-word-limit
            />
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
      
      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="departmentList.length === 0"
        empty-text="暂无可预约科室"
        @retry="getDepartmentList"
      >
        <div class="dept-category" aria-label="可预约科室">
          <button
            v-for="item in departmentList"
            :key="item.id"
            type="button"
            class="dept-card interactive-card"
            :aria-label="`${item.name}，${item.description || '暂无简介'}，${item.location || '院区待确认'}，去挂号`"
            @click="selectDepartment(item)"
          >
            <div class="dept-card-header">
              <span class="dept-card-name">{{ item.name }}</span>
              <el-tag size="small" v-if="item.hot">热门</el-tag>
            </div>
            <p class="dept-card-desc">{{ item.description || '暂无科室简介' }}</p>
            <div class="dept-card-footer">
              <span>{{ item.location || '院区待确认' }}</span>
              <span class="dept-card-cta">去挂号</span>
            </div>
          </button>
        </div>
      </PageState>
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
import { DEFAULT_TRIAGE_DURATION, TRIAGE_DURATION_OPTIONS } from '@/store/modules/triage'
import PageState from '@/components/common/PageState.vue'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const departmentList = ref([])
const durationOptions = TRIAGE_DURATION_OPTIONS

// 智能分诊相关状态
const triageExpanded = ref(false)
const symptoms = ref('')
const duration = ref(DEFAULT_TRIAGE_DURATION)
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
  errorMessage.value = ''
  try {
    const res = await getDepartmentListApi()
    departmentList.value = Array.isArray(res.data) ? res.data.filter(item => item.enabled !== 0) : []
  } catch (error) {
    departmentList.value = []
    errorMessage.value = error?.message || '科室列表加载失败，请重试'
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
  width: 100%;
  min-height: 56px;
  padding: 16px 20px;
  border: 0;
  cursor: pointer;
  font: inherit;
  text-align: left;
  user-select: none;
  background: linear-gradient(135deg, #e6f4ff 0%, #f0f7ff 100%);
  color: inherit;
}
.triage-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex-wrap: wrap;
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
.triage-intake {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(22, 119, 255, .16);
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(240, 247, 255, .86), rgba(255, 255, 255, .96));
}
.triage-intake__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.triage-intake__eyebrow {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--primary-color);
}
.triage-intake__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.triage-cta {
  min-height: 44px;
  min-width: 112px;
}
.duration-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.duration-option {
  margin-right: 0;
  width: 100%;
}
:deep(.duration-option.el-radio.is-bordered) {
  display: flex;
  align-items: flex-start;
  width: 100%;
  height: auto;
  min-height: 68px;
  margin-right: 0;
  padding: 12px 14px;
  border-radius: 8px;
  border-color: rgba(22, 119, 255, .18);
  background: rgba(255, 255, 255, .96);
}
:deep(.duration-option.el-radio.is-bordered.is-checked) {
  border-color: var(--primary-color);
  background: rgba(230, 244, 255, .9);
}
:deep(.duration-option .el-radio__label) {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 10px;
  white-space: normal;
}
.duration-option__label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
}
.duration-option__hint {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.4;
}
.triage-input :deep(textarea) {
  min-height: 108px;
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
  display: grid;
  grid-template-columns: auto minmax(0, 120px) minmax(0, 1fr) auto;
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
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.dept-card {
  display: flex;
  min-height: 164px;
  width: 100%;
  flex-direction: column;
  justify-content: space-between;
  padding: 20px;
  border: 1px solid var(--border-lighter);
  border-radius: var(--radius-base);
  cursor: pointer;
  font: inherit;
  text-align: left;
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
  gap: 12px;
  font-size: 12px;
  color: var(--text-secondary);
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}
.dept-card-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--touch-target);
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(2, 132, 199, .1);
  color: var(--primary-dark);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .triage-header {
    align-items: flex-start;
    gap: 12px;
    padding: 14px 16px;
  }

  .triage-title-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .triage-subtitle {
    margin-left: 0;
    line-height: 1.5;
  }

  .triage-intake__header {
    align-items: stretch;
    flex-direction: column;
  }

  .duration-group {
    grid-template-columns: 1fr;
  }

  .triage-cta {
    width: 100%;
  }

  .dept-category {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .dept-item {
    grid-template-columns: 1fr;
  }

  .dept-card {
    min-height: 148px;
    padding: 16px;
  }

  .dept-card-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .dept-card-cta {
    width: 100%;
  }
}
</style>

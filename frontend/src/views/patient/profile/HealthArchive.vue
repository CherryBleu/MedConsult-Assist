<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <el-button @click="$router.back()" :icon="ArrowLeft">返回</el-button>
        <h2 class="page-title">健康档案</h2>
      </div>

      <div v-loading="loading" class="archive-content">
        <!-- 未关联患者档案时给出明确提示，而非静默显示空表 -->
        <el-alert
          v-if="!hasPatientId"
          type="warning"
          :closable="false"
          show-icon
          title="当前账号未关联患者档案"
          description="请先前往「个人中心」完善个人档案后再查看健康档案。"
        />
        <template v-else>
        <!-- 过敏史 -->
        <div class="archive-section">
          <div class="section-header">
            <el-icon :size="20" color="#ff4d4f"><Warning /></el-icon>
            <h3 class="section-title">过敏史</h3>
          </div>
          <div class="section-content">
            <el-tag v-for="(item, index) in allergyList" :key="index" type="danger" effect="light">
              {{ item }}
            </el-tag>
            <span v-if="!allergyList.length" class="empty-text">暂无记录</span>
          </div>
        </div>

        <!-- 既往病史 -->
        <div class="archive-section">
          <div class="section-header">
            <el-icon :size="20" color="#faad14"><Document /></el-icon>
            <h3 class="section-title">既往病史</h3>
          </div>
          <div class="section-content text-content">
            {{ formatList(archive.pastMedicalHistory) }}
          </div>
        </div>

        <!-- 家族病史 -->
        <div class="archive-section">
          <div class="section-header">
            <el-icon :size="20" color="#1677ff"><UserFilled /></el-icon>
            <h3 class="section-title">家族病史</h3>
          </div>
          <div class="section-content text-content">
            {{ formatList(archive.familyHistory) }}
          </div>
        </div>

        <!-- 紧急联系人 -->
        <div class="archive-section">
          <div class="section-header">
            <el-icon :size="20" color="#52c41a"><Phone /></el-icon>
            <h3 class="section-title">紧急联系人</h3>
          </div>
          <div class="section-content contact-info">
            <div v-if="archive.emergencyContact">
              <div>姓名：{{ archive.emergencyContact.name }}</div>
              <div>关系：{{ archive.emergencyContact.relation }}</div>
              <div>电话：{{ archive.emergencyContact.phone }}</div>
            </div>
            <span v-else class="empty-text">暂无记录</span>
          </div>
        </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ArrowLeft, Warning, Document, UserFilled, Phone } from '@element-plus/icons-vue'
import { getHealthArchiveApi } from '@/api/patient'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const loading = ref(false)
const archive = ref({})

// 是否已关联患者档案（patientId 为 null → 未关联，提示先建档）
const hasPatientId = computed(() => !!userStore.userInfo?.patientId)

const allergyList = computed(() => {
  const a = archive.value.allergies
  if (!a) return []
  // 后端 allergies 是数组；兼容 mock 字符串
  if (Array.isArray(a)) return a.filter(i => i && String(i).trim())
  if (typeof a === 'string') return a.split(/[,、]/).filter(i => i.trim())
  return []
})

// 数组转顿号分隔字符串展示（后端 pastMedicalHistory/familyHistory 是 List<String>）
const formatList = (v) => {
  if (!v) return '暂无记录'
  if (Array.isArray(v)) return v.length ? v.join('、') : '暂无记录'
  return v || '暂无记录'
}

const getHealthArchive = async () => {
  // patientId 必填：从登录态 userInfo 取，避免 URL 拼成 /patients/undefined
  const patientId = userStore.userInfo?.patientId
  if (!patientId) {
    archive.value = {}
    return
  }
  loading.value = true
  try {
    const res = await getHealthArchiveApi(patientId)
    archive.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getHealthArchive()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.archive-section {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-light);
}
.archive-section:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.section-content {
  padding-left: 28px;
  line-height: 1.8;
}
.text-content {
  font-size: 14px;
  color: var(--text-regular);
}
.empty-text {
  color: var(--text-secondary);
  font-size: 14px;
}
.contact-info {
  font-size: 14px;
  color: var(--text-regular);
  line-height: 2;
}
</style>
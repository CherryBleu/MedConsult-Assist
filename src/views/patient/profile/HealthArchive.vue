<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <el-button @click="$router.back()" :icon="ArrowLeft">返回</el-button>
        <h2 class="page-title">健康档案</h2>
      </div>

      <div v-loading="loading" class="archive-content">
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
            {{ archive.pastMedicalHistory || '暂无记录' }}
          </div>
        </div>

        <!-- 家族病史 -->
        <div class="archive-section">
          <div class="section-header">
            <el-icon :size="20" color="#1677ff"><UserFilled /></el-icon>
            <h3 class="section-title">家族病史</h3>
          </div>
          <div class="section-content text-content">
            {{ archive.familyHistory || '暂无记录' }}
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
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ArrowLeft, Warning, Document, UserFilled, Phone } from '@element-plus/icons-vue'
import { getHealthArchiveApi } from '@/api/patient'

const loading = ref(false)
const archive = ref({})

const allergyList = computed(() => {
  if (!archive.value.allergies) return []
  return archive.value.allergies.split(/[,、]/).filter(i => i.trim())
})

const getHealthArchive = async () => {
  loading.value = true
  try {
    const res = await getHealthArchiveApi()
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
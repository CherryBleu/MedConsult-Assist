<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <el-button @click="$router.back()" :icon="ArrowLeft">返回</el-button>
        <h2 class="page-title">病历详情</h2>
      </div>

      <div v-loading="loading" class="detail-content">
        <div class="detail-section">
          <h3 class="section-title">基本信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="病历号">{{ detail.recordNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="就诊科室">{{ detail.deptName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="主治医生">{{ detail.doctorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="就诊时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="病历状态">
              <el-tag :type="getStatusType(MEDICAL_RECORD_STATUS, detail.status)">
                {{ getStatusLabel(MEDICAL_RECORD_STATUS, detail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="归档时间">{{ detail.archivedAt || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <h3 class="section-title">诊疗信息</h3>
          <div class="info-block">
            <div class="info-label">主诉</div>
            <div class="info-content">{{ detail.chiefComplaint || '-' }}</div>
          </div>
          <div class="info-block">
            <div class="info-label">现病史</div>
            <div class="info-content">{{ detail.presentIllness || '-' }}</div>
          </div>
          <div class="info-block">
            <div class="info-label">既往史</div>
            <div class="info-content">{{ detail.pastHistory || '-' }}</div>
          </div>
          <div class="info-block">
            <div class="info-label">体格检查</div>
            <div class="info-content">{{ detail.physicalExam || '-' }}</div>
          </div>
          <div class="info-block">
            <div class="info-label">诊断</div>
            <div class="info-content diagnosis">{{ detail.diagnosisText || '-' }}</div>
          </div>
        </div>

        <div class="detail-section">
          <h3 class="section-title">处方信息</h3>
          <el-table :data="detail.prescriptions || []" border stripe>
            <el-table-column prop="name" label="药品名称" />
            <el-table-column prop="specification" label="规格" />
            <el-table-column prop="dosage" label="用法用量" />
            <el-table-column prop="quantity" label="数量" />
          </el-table>
        </div>

        <div class="detail-section">
          <h3 class="section-title">医嘱</h3>
          <div class="advice-block">{{ detail.doctorAdvice || '-' }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { MEDICAL_RECORD_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { getRecordDetailApi } from '@/api/record'

const route = useRoute()
const loading = ref(false)
const detail = ref({})

const getDetail = async () => {
  const id = route.params.id
  if (!id) return

  loading.value = true
  try {
    const res = await getRecordDetailApi(id)
    detail.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getDetail()
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

.detail-section {
  margin-bottom: 24px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
  padding-left: 8px;
  border-left: 3px solid var(--primary-color);
}

.info-block {
  margin-bottom: 12px;
  line-height: 1.6;
}
.info-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}
.info-content {
  font-size: 14px;
  color: var(--text-primary);
}
.diagnosis {
  color: var(--primary-color);
  font-weight: 500;
}

.advice-block {
  padding: 16px;
  background: #f0f7ff;
  border-radius: var(--radius-base);
  line-height: 1.8;
  color: var(--text-primary);
  font-size: 14px;
}
</style>

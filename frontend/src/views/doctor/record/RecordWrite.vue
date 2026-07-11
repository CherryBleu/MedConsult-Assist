<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <el-button @click="$router.back()" :icon="ArrowLeft">返回</el-button>
        <h2 class="page-title">书写电子病历</h2>
        <div class="header-right">
          <el-tag :type="getStatusType(MEDICAL_RECORD_STATUS, form.status)">
            {{ getStatusLabel(MEDICAL_RECORD_STATUS, form.status) }}
          </el-tag>
        </div>
      </div>

      <div class="patient-bar">
        <span>患者：{{ patientName }}</span>
        <span>就诊时间：{{ currentDate }}</span>
      </div>

      <el-form :model="form" label-width="100px" class="record-form">
        <el-form-item label="主诉" required>
          <el-input
            v-model="form.chiefComplaint"
            type="textarea"
            :rows="2"
            placeholder="请输入主诉"
          />
        </el-form-item>

        <el-form-item label="现病史">
          <el-input
            v-model="form.presentIllness"
            type="textarea"
            :rows="3"
            placeholder="请输入现病史"
          />
        </el-form-item>

        <el-form-item label="既往史">
          <el-input
            v-model="form.pastHistory"
            type="textarea"
            :rows="2"
            placeholder="请输入既往史"
          />
        </el-form-item>

        <el-form-item label="体格检查">
          <el-input
            v-model="form.physicalExam"
            type="textarea"
            :rows="2"
            placeholder="请输入体格检查结果"
          />
        </el-form-item>

        <el-form-item label="初步诊断">
          <el-input
            v-model="form.initialDiagnosis"
            type="textarea"
            :rows="2"
            placeholder="请输入初步诊断"
          />
        </el-form-item>

        <el-form-item label="处方信息">
          <el-input
            v-model="form.prescriptionsText"
            type="textarea"
            :rows="3"
            placeholder="请输入处方药品及用法"
          />
        </el-form-item>

        <el-form-item label="医嘱">
          <el-input
            v-model="form.doctorAdvice"
            type="textarea"
            :rows="2"
            placeholder="请输入医嘱"
          />
        </el-form-item>
      </el-form>

      <div class="form-footer">
        <el-button @click="$router.back()">取消</el-button>
        <el-button :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" :loading="archiving" @click="archiveRecord">
          确认归档
        </el-button>
        <el-button type="success" :loading="aiLoading" @click="generateSummary">
          AI生成摘要
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { MEDICAL_RECORD_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { createRecordApi, archiveRecordApi } from '@/api/record'
import { generateSummaryByTextApi } from '@/api/ai'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const patientName = ref('')
const saving = ref(false)
const archiving = ref(false)
const aiLoading = ref(false)
const currentDate = ref(dayjs().format('YYYY-MM-DD HH:mm'))

const form = reactive({
  appointmentId: '',
  chiefComplaint: '',
  presentIllness: '',
  pastHistory: '',
  physicalExam: '',
  initialDiagnosis: '',
  prescriptionsText: '',
  doctorAdvice: '',
  status: 'DRAFT'
})

// 组装后端 CreateRequest 期望的载荷：patientId/doctorId 必填，initialDiagnosis 是数组
const buildPayload = () => {
  // patientId 优先取路由参数（从接诊页带入），否则用测试患者档案
  const patientId = route.query.patientId || userStore.userInfo?.patientId
  const doctorId = route.query.doctorId || userStore.userInfo?.doctorId
  return {
    patientId: String(patientId || ''),
    doctorId: String(doctorId || ''),
    appointmentId: form.appointmentId || undefined,
    chiefComplaint: form.chiefComplaint,
    presentIllness: form.presentIllness || undefined,
    pastHistory: form.pastHistory || undefined,
    physicalExam: form.physicalExam || undefined,
    // 后端 initialDiagnosis 是 List<String>；空串不发送避免 Jackson 空串→数组报错
    initialDiagnosis: form.initialDiagnosis?.trim()
      ? form.initialDiagnosis.split(/[,，；;\n]/).map(s => s.trim()).filter(Boolean)
      : undefined,
    prescriptionsText: form.prescriptionsText || undefined,
    doctorAdvice: form.doctorAdvice || undefined
  }
}

const saveDraft = async () => {
  if (!form.chiefComplaint.trim()) {
    ElMessage.warning('请填写主诉')
    return
  }
  saving.value = true
  try {
    await createRecordApi(buildPayload())
    ElMessage.success('草稿已保存')
  } finally {
    saving.value = false
  }
}

const archiveRecord = async () => {
  if (!form.chiefComplaint.trim() || !form.initialDiagnosis.trim()) {
    ElMessage.warning('请完善主诉和诊断信息')
    return
  }
  try {
    await ElMessageBox.confirm('归档后病历不可直接修改，确认归档吗？', '提示', {
      confirmButtonText: '确认归档',
      cancelButtonText: '再编辑',
      type: 'warning'
    })
    archiving.value = true
    // 归档前必须先拿到真实病历 id：新建病历（无 recordId）先 create 再 archive，
    // 不能用 Date.now() 当 recordId（后端必然 404/参数错误）。
    let recordId = route.query.recordId
    if (!recordId) {
      const created = await createRecordApi(buildPayload())
      recordId = created?.data?.id || created?.data?.recordId || created?.data?.recordNo
      if (!recordId) {
        ElMessage.error('保存病历失败，无法归档')
        return
      }
    }
    await archiveRecordApi(recordId)
    form.status = 'ARCHIVED'
    ElMessage.success('病历已归档')
    router.back()
  } catch (err) {
    if (err !== 'cancel') console.error(err)
  } finally {
    archiving.value = false
  }
}

const generateSummary = async () => {
  if (!form.chiefComplaint.trim()) {
    ElMessage.warning('请先填写主诉内容')
    return
  }
  aiLoading.value = true
  try {
    const text = `主诉：${form.chiefComplaint}\n现病史：${form.presentIllness}\n既往史：${form.pastHistory}\n体格检查：${form.physicalExam}`
    const res = await generateSummaryByTextApi(text)
    const content = res.data.summaryContent
    form.initialDiagnosis = content.diagnosis
    form.doctorAdvice = content.advice
    ElMessage.success('AI摘要已填充，可继续编辑')
  } finally {
    aiLoading.value = false
  }
}

onMounted(() => {
  patientName.value = route.query.patientName || '患者'
  form.appointmentId = route.query.appointmentId || ''
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.page-title {
  flex: 1;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.header-right {
  flex-shrink: 0;
}

.patient-bar {
  padding: 12px 16px;
  background: var(--bg-page);
  border-radius: var(--radius-base);
  margin-bottom: 20px;
  display: flex;
  gap: 24px;
  font-size: 14px;
  color: var(--text-regular);
}

.record-form {
  max-width: 900px;
  margin: 0 auto;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
  margin-top: 20px;
}
</style>
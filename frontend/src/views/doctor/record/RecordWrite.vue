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

        <el-form-item label="医嘱">
          <el-input
            v-model="form.doctorAdvice"
            type="textarea"
            :rows="2"
            placeholder="请输入医嘱"
          />
        </el-form-item>
      </el-form>

      <!-- 处方明细：独立成表流转（POST /prescriptions），不再塞病历 JSON 文本。
           每行：药品选择 + 用量/频次/途径/天数/数量 + 删除。 -->
      <div class="prescription-section">
        <div class="section-header">
          <h3 class="section-title">处方明细</h3>
          <el-button type="primary" plain size="small" :icon="Plus" @click="addItem">
            添加药品
          </el-button>
        </div>
        <el-table :data="form.prescriptionItems" border size="small" empty-text="暂无药品，点击右上角添加">
          <el-table-column label="药品" min-width="220">
            <template #default="{ row, $index }">
              <DrugSelect
                :initial-drug-name="row.drugName"
                @select="(drug) => onDrugSelected($index, drug)"
                @clear="onDrugCleared($index)"
              />
            </template>
          </el-table-column>
          <el-table-column label="规格" width="120">
            <template #default="{ row }">
              <span>{{ row.specification || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="用法用量" width="110">
            <template #default="{ row }">
              <el-input v-model="row.dosage" placeholder="如 30mg" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="频次" width="120">
            <template #default="{ row }">
              <el-input v-model="row.frequency" placeholder="如 每日一次" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="途径" width="90">
            <template #default="{ row }">
              <el-input v-model="row.route" placeholder="如 口服" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="天数" width="80">
            <template #default="{ row }">
              <el-input-number
                v-model="row.days"
                :min="1"
                :controls="false"
                size="small"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="数量" width="90">
            <template #default="{ row }">
              <el-input v-model="row.quantity" placeholder="如 7" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="70">
            <template #default="{ row }">
              <span>{{ row.unit || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="prescription-tip">
          处方提交后将进入药师审方队列（草稿→待审）。本批不计金额。
        </div>
      </div>

      <div class="form-footer">
        <el-button @click="$router.back()">取消</el-button>
        <el-button :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" :loading="archiving" @click="archiveRecord">
          确认归档
        </el-button>
        <!-- 开方与归档解耦：独立按钮，先保存病历再提交处方进药师审方 -->
        <el-button type="success" :loading="prescribing" @click="submitPrescription">
          提交处方
        </el-button>
        <el-button :loading="aiLoading" @click="generateSummary">
          AI生成摘要
        </el-button>
        <el-button type="warning" :loading="medAnalysisLoading" @click="openMedAnalysis">
          AI用药分析
        </el-button>
      </div>
    </div>

    <!-- 用药分析抽屉 -->
    <el-drawer
      v-model="medAnalysisVisible"
      title="AI用药安全分析"
      size="600px"
      :close-on-click-modal="false"
    >
      <div v-if="medAnalysisResult" class="med-analysis-content">
        <!-- 整体风险 -->
        <div class="risk-overview" :class="medRiskLevel.toLowerCase()">
          <div class="risk-label">整体风险等级</div>
          <div class="risk-value">{{ medRiskLabel }}</div>
        </div>

        <!-- 禁忌风险 -->
        <div class="analysis-block">
          <h3 class="block-title">禁忌风险</h3>
          <div v-if="!medAnalysisResult.contraindicationRisks || medAnalysisResult.contraindicationRisks.length === 0" class="safe-tip">
            <el-icon color="#52c41a"><CircleCheck /></el-icon>
            <span>未检测到禁忌风险</span>
          </div>
          <div v-else class="risk-list">
            <div v-for="(item, index) in medAnalysisResult.contraindicationRisks" :key="index" class="risk-item danger">
              {{ typeof item === 'string' ? item : (item.description || item.risk || '—') }}
            </div>
          </div>
        </div>

        <!-- 药物相互作用 -->
        <div class="analysis-block">
          <h3 class="block-title">药物相互作用</h3>
          <div v-if="!medAnalysisResult.interactionRisks || medAnalysisResult.interactionRisks.length === 0" class="safe-tip">
            <el-icon color="#52c41a"><CircleCheck /></el-icon>
            <span>未检测到相互作用风险</span>
          </div>
          <div v-else class="interaction-list">
            <div
              v-for="(item, index) in medAnalysisResult.interactionRisks"
              :key="index"
              class="interaction-item"
              :class="(item.level || 'LOW').toLowerCase()"
            >
              <div class="drug-pair">{{ item.drugA || item.drug_a || '?' }} + {{ item.drugB || item.drug_b || '?' }}</div>
              <div class="risk-level">风险等级：{{ item.level === 'LOW' ? '低' : item.level === 'MEDIUM' ? '中' : item.level === 'HIGH' ? '高' : (item.level || '低') }}</div>
              <div class="risk-desc">{{ item.desc || item.description || item.effect || '' }}</div>
            </div>
          </div>
        </div>

        <!-- 用药提醒 -->
        <div class="analysis-block">
          <h3 class="block-title">用药提醒</h3>
          <div v-if="!medAnalysisResult.reminders || medAnalysisResult.reminders.length === 0" class="safe-tip">
            <el-icon color="#52c41a"><CircleCheck /></el-icon>
            <span>暂无用药提醒</span>
          </div>
          <ul v-else class="reminder-list">
            <li v-for="(item, index) in medAnalysisResult.reminders" :key="index">
              {{ typeof item === 'string' ? item : ((item.drugName ? item.drugName + '：' : '') + (item.reminder || item.content || item.message || '—')) }}
            </li>
          </ul>
        </div>
      </div>
      <el-empty v-else description="正在分析，请稍候..." />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, CircleCheck, Plus } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { MEDICAL_RECORD_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { createRecordApi, updateRecordApi, archiveRecordApi, getRecordDetailApi } from '@/api/record'
import { createPrescriptionApi, submitPrescriptionApi } from '@/api/prescription'
import { generateSummaryByTextApi, medicationAnalysisApi } from '@/api/ai'
import { useUserStore } from '@/store/modules/user'
import DrugSelect from './components/DrugSelect.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const patientName = ref('')
const saving = ref(false)
// 当前编辑的病历 id：从路由带入（继续编辑草稿）或首次 create 后回填。
// 保存草稿时据此判断走 update（PUT）而非重复 create（POST），避免草稿越存越多。
// 开方（POST /prescriptions）的 recordId 必填，也复用此 id。
const currentRecordId = ref(route.query.recordId || null)
const archiving = ref(false)
const aiLoading = ref(false)
// 开方提交 loading（独立于病历保存/归档）
const prescribing = ref(false)
const currentDate = ref(dayjs().format('YYYY-MM-DD HH:mm'))

// 用药分析相关
const medAnalysisVisible = ref(false)
const medAnalysisLoading = ref(false)
const medAnalysisResult = ref(null)

const medRiskLevel = computed(() => {
  return medAnalysisResult.value?.overallRiskLevel || 'LOW'
})
const medRiskLabel = computed(() => {
  const map = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' }
  return map[medRiskLevel.value] || '低风险'
})

const form = reactive({
  appointmentId: '',
  chiefComplaint: '',
  presentIllness: '',
  pastHistory: '',
  physicalExam: '',
  initialDiagnosis: '',
  // 处方明细行（独立提交到 POST /prescriptions，不再随病历文本落库）。
  // 每项字段对齐后端 ItemRequest：drugNo?/drugName/specification?/dosage?/frequency?/
  // route?/days(>=1)/quantity(>0)/unit?/unitPrice?(本批不传)
  prescriptionItems: [],
  doctorAdvice: '',
  status: 'DRAFT'
})

// 新增一条空白药品明细行（默认天数 1，数量留空让医生填）
const addItem = () => {
  form.prescriptionItems.push({
    drugNo: '',
    drugName: '',
    specification: '',
    dosage: '',
    frequency: '',
    route: '',
    days: 1,
    quantity: '',
    unit: ''
  })
}

const removeItem = (index) => {
  form.prescriptionItems.splice(index, 1)
}

// DrugSelect 选中药品 → 回填该行药品快照字段
const onDrugSelected = (index, drug) => {
  const row = form.prescriptionItems[index]
  if (!row) return
  row.drugNo = drug.drugNo ?? drug.drugId ?? ''
  row.drugName = drug.name ?? drug.genericName ?? drug.drugName ?? ''
  row.specification = drug.specification ?? ''
  row.unit = drug.unit ?? ''
}

// 清空选择 → 重置该行药品字段（用量等保留）
const onDrugCleared = (index) => {
  const row = form.prescriptionItems[index]
  if (!row) return
  row.drugNo = ''
  row.drugName = ''
  row.specification = ''
  row.unit = ''
}

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
    // 已有病历 id（继续编辑草稿 / 本会话首次保存后）走 update；否则 create
    if (currentRecordId.value) {
      await updateRecordApi(currentRecordId.value, buildPayload())
    } else {
      const created = await createRecordApi(buildPayload())
      const newId = created?.data?.id || created?.data?.recordId || created?.data?.recordNo
      if (newId) currentRecordId.value = newId
    }
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
    // 归档前必须先拿到真实病历 id：复用 currentRecordId（继续编辑/已保存草稿），
    // 否则新建病历后回填，不能用 Date.now() 当 recordId（后端必然 404/参数错误）。
    let recordId = currentRecordId.value
    if (!recordId) {
      const created = await createRecordApi(buildPayload())
      recordId = created?.data?.id || created?.data?.recordId || created?.data?.recordNo
      if (!recordId) {
        ElMessage.error('保存病历失败，无法归档')
        return
      }
      currentRecordId.value = recordId
    }
    await archiveRecordApi(recordId, {
      // 后端 ArchiveRequest.confirmBy 标了 @NotBlank（医生编号 doctor_no）
      confirmBy: String(userStore.userInfo?.doctorId || route.query.doctorId || ''),
      confirmNote: '医生确认归档'
    })
    form.status = 'ARCHIVED'
    ElMessage.success('病历已归档')
    router.back()
  } catch (err) {
    if (err !== 'cancel') console.error(err)
  } finally {
    archiving.value = false
  }
}

// 提交处方：开方(DRAFT) → 自动 submit → 待审(PENDING_REVIEW)。
// 开方与归档解耦：互不依赖，可独立操作。开方需先有病历 recordId（后端 @NotBlank）。
const submitPrescription = async () => {
  // 1. 必须先有病历：复用归档同款"先建后回填"逻辑，保证 recordId 真实可用
  if (!currentRecordId.value) {
    if (!form.chiefComplaint.trim()) {
      ElMessage.warning('请先填写主诉并保存病历，再开方')
      return
    }
    try {
      const created = await createRecordApi(buildPayload())
      const newId = created?.data?.id || created?.data?.recordId || created?.data?.recordNo
      if (!newId) {
        ElMessage.error('保存病历失败，无法开方')
        return
      }
      currentRecordId.value = newId
    } catch (e) {
      return
    }
  }

  // 2. 校验明细行：非空 + 每行 drugName/days/quantity 合法
  if (!form.prescriptionItems.length) {
    ElMessage.warning('请至少添加一条药品明细')
    return
  }
  for (const [i, row] of form.prescriptionItems.entries()) {
    if (!row.drugName?.trim()) {
      ElMessage.warning(`第 ${i + 1} 行未选择药品`)
      return
    }
    if (!row.days || row.days < 1) {
      ElMessage.warning(`第 ${i + 1} 行天数至少为 1`)
      return
    }
    const qty = Number(row.quantity)
    if (!row.quantity || isNaN(qty) || qty <= 0) {
      ElMessage.warning(`第 ${i + 1} 行数量必须大于 0`)
      return
    }
  }

  prescribing.value = true
  try {
    // 3. 组装 CreateRequest（recordId/patientId/doctorId 复用 buildPayload 同源）
    const patientId = route.query.patientId || userStore.userInfo?.patientId
    const doctorId = route.query.doctorId || userStore.userInfo?.doctorId
    const payload = {
      recordId: String(currentRecordId.value),
      patientId: String(patientId || ''),
      doctorId: String(doctorId || ''),
      departmentId: route.query.departmentId || undefined,
      source: 'OUTPATIENT',
      // unitPrice 本批不传（drug 表无 price 列），totalFee 按 0 计
      items: form.prescriptionItems.map((r) => ({
        drugNo: r.drugNo || undefined,
        drugName: r.drugName.trim(),
        specification: r.specification || undefined,
        dosage: r.dosage || undefined,
        frequency: r.frequency || undefined,
        route: r.route || undefined,
        days: Number(r.days),
        quantity: Number(r.quantity),
        unit: r.unit || undefined
      }))
    }
    // 4. 开方（DRAFT）→ 自动提交审方（PENDING_REVIEW）
    const created = await createPrescriptionApi(payload)
    const prescriptionId = created?.data?.prescriptionId
    if (!prescriptionId) {
      ElMessage.error('开方失败：未返回处方编号')
      return
    }
    await submitPrescriptionApi(prescriptionId)
    ElMessage.success('处方已提交，等待药师审核')
    // 提交成功后清空明细行，避免重复提交
    form.prescriptionItems = []
  } catch (e) {
    // 开方成功但 submit 失败：处方已成 DRAFT，提示医生可稍后手动提交（此处不自动重试）
  } finally {
    prescribing.value = false
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
    const content = res.data?.summary || {}
    const diagnosis = Array.isArray(content.diagnosis) ? content.diagnosis.join('，') : (content.diagnosis || '')
    if (diagnosis) form.initialDiagnosis = diagnosis
    const adviceParts = [content.treatmentPlan, content.followUpAdvice].filter(Boolean)
    if (adviceParts.length) form.doctorAdvice = adviceParts.join('\n')
    ElMessage.success('AI摘要已填充，可继续编辑')
  } finally {
    aiLoading.value = false
  }
}

const openMedAnalysis = async () => {
  // 用药分析数据源改为处方明细行（处方结构化后的联动调整）。
  // 取每行 drugName 组成 [{ drugName }]；空明细提示先添加药品。
  const validItems = form.prescriptionItems.filter((r) => r.drugName?.trim())
  if (!validItems.length) {
    ElMessage.warning('请先添加处方药品')
    return
  }
  medAnalysisVisible.value = true
  medAnalysisResult.value = null
  medAnalysisLoading.value = true
  try {
    const drugs = validItems.map((r) => ({ drugName: r.drugName.trim() }))
    const payload = {
      recordId: currentRecordId.value || null,
      prescriptions: drugs,
      patientContext: null
    }
    const res = await medicationAnalysisApi(payload)
    medAnalysisResult.value = res.data
  } catch (e) {
    // 错误由拦截器统一提示
  } finally {
    medAnalysisLoading.value = false
  }
}

onMounted(async () => {
  patientName.value = route.query.patientName || '患者'
  form.appointmentId = route.query.appointmentId || ''
  // 编辑已有草稿：加载病历详情填充表单
  const recordId = route.query.recordId
  if (recordId) {
    try {
      const res = await getRecordDetailApi(recordId)
      const d = res.data
      if (d) {
        form.chiefComplaint = d.chiefComplaint || ''
        form.presentIllness = d.presentIllness || ''
        form.pastHistory = d.pastHistory || ''
        form.physicalExam = d.physicalExam || ''
        form.initialDiagnosis = Array.isArray(d.initialDiagnosis) ? d.initialDiagnosis.join('；') : (d.initialDiagnosis || '')
        form.doctorAdvice = d.doctorAdvice || ''
        // TODO 本批不回显已开处方：后端 GET /prescriptions 列表 ListItem.recordId 回传 null
        // 且不支持按 recordId 过滤，无法据此反查本病历的处方。待后端补该过滤后，在此
        // 按记录 recordId 调列表 + 详情回填 form.prescriptionItems。
      }
    } catch (e) {
      // 加载失败不阻塞新建流程
    }
  }
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

/* 处方明细区块 */
.prescription-section {
  max-width: 900px;
  margin: 8px auto 0;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.prescription-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
  margin-top: 20px;
}

/* 用药分析抽屉 */
.med-analysis-content {
  padding: 0 8px;
}
.risk-overview {
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.risk-overview.low { background: #f6ffed; border: 1px solid #b7eb8f; }
.risk-overview.medium { background: #fff7e6; border: 1px solid #ffd591; }
.risk-overview.high { background: #fff1f0; border: 1px solid #ffa39e; }
.risk-label { font-size: 14px; color: var(--text-regular); }
.risk-value { font-size: 20px; font-weight: 600; }
.low .risk-value { color: #52c41a; }
.medium .risk-value { color: #faad14; }
.high .risk-value { color: #ff4d4f; }
.analysis-block {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-light);
}
.analysis-block:last-child {
  border-bottom: none;
  margin-bottom: 0;
}
.block-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px;
}
.safe-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #52c41a;
  font-size: 14px;
}
.risk-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.risk-item.danger {
  padding: 10px 14px;
  background: #fff1f0;
  border-left: 3px solid #ff4d4f;
  border-radius: 4px;
  font-size: 14px;
  color: #cf1322;
}
.interaction-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.interaction-item {
  padding: 14px;
  border-radius: 8px;
  border-left: 4px solid;
}
.interaction-item.low {
  background: #f6ffed;
  border-left-color: #52c41a;
}
.interaction-item.medium {
  background: #fff7e6;
  border-left-color: #faad14;
}
.interaction-item.high {
  background: #fff1f0;
  border-left-color: #ff4d4f;
}
.drug-pair {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
}
.risk-level {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.risk-desc {
  font-size: 13px;
  color: var(--text-regular);
}
.reminder-list {
  margin: 0;
  padding-left: 20px;
  line-height: 2;
  font-size: 14px;
  color: var(--text-regular);
}
</style>
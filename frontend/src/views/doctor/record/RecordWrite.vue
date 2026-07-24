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

        <section class="structured-prescription" aria-labelledby="structured-prescription-title">
          <div class="section-heading">
            <div>
              <h3 id="structured-prescription-title" class="section-title">结构化处方</h3>
              <p class="section-subtitle">处方独立提交到审方流转，病历只保留诊疗记录。</p>
            </div>
            <el-button type="primary" plain class="record-action" @click="addPrescriptionItem">新增药品</el-button>
          </div>

          <article
            v-for="(item, index) in prescriptionItems"
            :key="item.key"
            class="prescription-item"
            data-testid="structured-prescription-item"
          >
            <div class="prescription-item__header">
              <strong>药品 {{ index + 1 }}</strong>
              <el-button
                type="danger"
                plain
                class="record-action"
                :disabled="prescriptionItems.length === 1"
                :aria-label="`删除第${index + 1}个药品`"
                @click="removePrescriptionItem(index)"
              >
                删除
              </el-button>
            </div>

            <div class="prescription-grid">
              <label class="field-control" :for="`rx-drug-name-${index}`">
                <span>药品名称</span>
                <el-select
                  v-model="item.drugNo"
                  class="drug-name-select"
                  filterable
                  remote
                  reserve-keyword
                  clearable
                  :remote-method="keyword => searchPrescriptionDrugs(item, keyword)"
                  :loading="item.drugLoading"
                  placeholder="输入药品名搜索药品库"
                  no-data-text="未匹配到药品库药品"
                  @focus="searchPrescriptionDrugs(item, '')"
                  @change="drugNo => handleDrugSelect(item, drugNo)"
                  @clear="clearDrugSelection(item)"
                >
                  <el-option
                    v-for="drug in item.drugOptions"
                    :key="drug.drugNo"
                    :label="drug.value"
                    :value="drug.drugNo"
                  />
                </el-select>
              </label>
              <label class="field-control" :for="`rx-spec-${index}`">
                <span>规格</span>
                <el-input :input-id="`rx-spec-${index}`" v-model="item.specification" readonly placeholder="选择药品后自动显示" />
              </label>
              <label class="field-control" :for="`rx-dosage-${index}`">
                <span>单次剂量</span>
                <el-input :input-id="`rx-dosage-${index}`" v-model="item.dosage" placeholder="单次剂量" />
              </label>
              <label class="field-control" :for="`rx-frequency-${index}`">
                <span>频次</span>
                <el-input :input-id="`rx-frequency-${index}`" v-model="item.frequency" placeholder="频次" />
              </label>
              <label class="field-control" :for="`rx-route-${index}`">
                <span>给药途径</span>
                <el-input :input-id="`rx-route-${index}`" v-model="item.route" placeholder="给药途径" />
              </label>
              <label class="field-control" :for="`rx-days-${index}`">
                <span>用药天数</span>
                <el-input
                  :input-id="`rx-days-${index}`"
                  v-model="item.days"
                  aria-label="用药天数"
                  inputmode="numeric"
                  placeholder="天数"
                />
              </label>
              <label class="field-control" :for="`rx-quantity-${index}`">
                <span>数量</span>
                <el-input
                  :input-id="`rx-quantity-${index}`"
                  v-model="item.quantity"
                  aria-label="数量"
                  inputmode="decimal"
                  placeholder="数量"
                />
              </label>
              <label class="field-control" :for="`rx-unit-${index}`">
                <span>单位</span>
                <el-input :input-id="`rx-unit-${index}`" v-model="item.unit" placeholder="单位" />
              </label>
              <label class="field-control" :for="`rx-unit-price-${index}`">
                <span>单价</span>
                <el-input
                  :input-id="`rx-unit-price-${index}`"
                  v-model="item.unitPrice"
                  aria-label="单价"
                  inputmode="decimal"
                  readonly
                  placeholder="选择药品后自动显示"
                />
              </label>
            </div>
          </article>

          <div class="prescription-summary" aria-label="处方费用汇总">
            <span>处方合计</span>
            <strong data-testid="structured-prescription-total">¥{{ structuredPrescriptionTotal.toFixed(2) }}</strong>
          </div>

          <div v-if="prescriptionError" class="inline-error" role="alert">{{ prescriptionError }}</div>
        </section>

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
        <el-button type="primary" class="record-action" :loading="prescriptionSubmitting" @click="submitPrescriptionForReview">
          开方并提交审方
        </el-button>
        <el-button type="success" :loading="aiLoading" @click="generateSummary">
          AI生成摘要
        </el-button>
        <el-button type="warning" class="record-action" :loading="medAnalysisLoading" @click="openMedAnalysis">
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
import { ArrowLeft, CircleCheck } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { MEDICAL_RECORD_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { createRecordApi, updateRecordApi, archiveRecordApi, getRecordDetailApi } from '@/api/record'
import { createPrescriptionApi, submitPrescriptionApi } from '@/api/prescription'
import { generateSummaryByTextApi, medicationAnalysisApi } from '@/api/ai'
import { getDrugListApi } from '@/api/drug'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const patientName = ref('')
const saving = ref(false)
// 当前编辑的病历 id：从路由带入（继续编辑草稿）或首次 create 后回填。
// 保存草稿时据此判断走 update（PUT）而非重复 create（POST），避免草稿越存越多。
const currentRecordId = ref(route.query.recordId || null)
const archiving = ref(false)
const aiLoading = ref(false)
const currentDate = ref(dayjs().format('YYYY-MM-DD HH:mm'))

// 用药分析相关
const medAnalysisVisible = ref(false)
const medAnalysisLoading = ref(false)
const medAnalysisResult = ref(null)
const prescriptionSubmitting = ref(false)
const prescriptionError = ref('')
const submittedPrescriptionId = ref('')

const newPrescriptionItem = () => ({
  key: Date.now() + Math.random(),
  drugNo: '',
  drugName: '',
  selectedDrugName: '',
  drugOptions: [],
  drugLoading: false,
  specification: '',
  dosage: '',
  frequency: '',
  route: '口服',
  days: '',
  quantity: '',
  unit: '盒',
  unitPrice: ''
})

const prescriptionItems = ref([newPrescriptionItem()])

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
  doctorAdvice: '',
  status: 'DRAFT'
})

const numericValue = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

const structuredPrescriptionTotal = computed(() => prescriptionItems.value.reduce((sum, item) => {
  return sum + numericValue(item.quantity) * numericValue(item.unitPrice)
}, 0))

const addPrescriptionItem = () => {
  prescriptionItems.value.push(newPrescriptionItem())
}

const removePrescriptionItem = (index) => {
  if (prescriptionItems.value.length === 1) return
  prescriptionItems.value.splice(index, 1)
}

const getDrugName = (drug) => drug?.name || drug?.drugName || drug?.genericName || ''
const getDrugNo = (drug) => String(drug?.drugNo || drug?.drugId || drug?.id || '')

const normalizeDrugOption = (drug) => {
  const name = getDrugName(drug)
  const unitPrice = drug.unitPrice ?? drug.price ?? ''
  const priceText = formatUnitPrice(unitPrice)
  return {
    ...drug,
    name,
    value: `${name}${drug.specification ? `（${drug.specification}）` : ''}${priceText === '-' ? '' : ` ¥${priceText}`}`,
    drugNo: getDrugNo(drug),
    unitPrice
  }
}

const formatUnitPrice = (value) => {
  const n = Number(value)
  return Number.isFinite(n) ? n.toFixed(2) : '-'
}

const searchPrescriptionDrugs = async (item, keyword = '') => {
  const query = keyword?.trim()
  item.drugLoading = true
  try {
    const res = await getDrugListApi({ keyword: query || undefined, page: 1, pageSize: 20 })
    const list = Array.isArray(res.data) ? res.data : []
    item.drugOptions = list
      .map(normalizeDrugOption)
      .filter(item => item.name && item.status !== 'DISABLED')
  } catch (e) {
    item.drugOptions = []
  } finally {
    item.drugLoading = false
  }
}

const clearDrugSelection = (item) => {
  item.drugNo = ''
  item.drugName = ''
  item.selectedDrugName = ''
  item.specification = ''
  item.unitPrice = ''
}

const handleDrugSelect = (item, drugNo) => {
  if (!drugNo) {
    clearDrugSelection(item)
    return
  }
  const option = item.drugOptions.find(drug => String(drug.drugNo) === String(drugNo))
  if (!option) {
    clearDrugSelection(item)
    return
  }
  item.drugNo = option.drugNo
  item.drugName = option.name
  item.selectedDrugName = option.name
  item.specification = option.specification || ''
  item.unit = option.unit || item.unit || ''
  item.unitPrice = option.unitPrice === '' || option.unitPrice == null ? '' : String(option.unitPrice)
}

const ensureSelectedDrugOption = (item) => {
  if (!item.drugNo || !item.drugName) return
  const exists = item.drugOptions.some(drug => String(drug.drugNo) === String(item.drugNo))
  if (!exists) {
    item.drugOptions.unshift(normalizeDrugOption(item))
  }
}

const getPrescriptionDraftItems = () => prescriptionItems.value
  .filter(item => item.drugName?.trim())
  .map(item => ({
    drugNo: item.drugNo || undefined,
    drugName: item.drugName.trim(),
    specification: item.specification || undefined,
    dosage: item.dosage || undefined,
    frequency: item.frequency || undefined,
    route: item.route || undefined,
    days: Number(item.days),
    quantity: Number(item.quantity),
    unit: item.unit || undefined,
    unitPrice: item.unitPrice === '' ? undefined : Number(item.unitPrice)
  }))

const validatePrescriptionItems = () => {
  const items = getPrescriptionDraftItems()
  if (items.length === 0) return '请至少填写一条处方药品'
  const missingCatalogDrug = prescriptionItems.value.find(item => item.drugName?.trim() && !item.drugNo)
  if (missingCatalogDrug) return '请选择药品库中的药品，不支持手输库外药品'
  const invalid = items.find(item => !item.drugName || !Number.isFinite(item.days) || item.days < 1 || !Number.isFinite(item.quantity) || item.quantity <= 0)
  if (invalid) return '请完善药品名称、用药天数和数量'
  return ''
}

const buildPrescriptionSnapshot = () => getPrescriptionDraftItems()
  .map(item => `${item.drugName}${item.specification ? `（${item.specification}）` : ''} ${[item.route, item.dosage, item.frequency].filter(Boolean).join(' ')} ${item.days}天 ${item.quantity}${item.unit || ''}`)
  .join('\n')

const applyPrescriptionDetail = (list) => {
  if (!Array.isArray(list) || list.length === 0) return
  prescriptionItems.value = list.map(item => {
    const nextItem = {
      ...newPrescriptionItem(),
      drugNo: getDrugNo(item),
      drugName: item.drugName || item.name || '',
      selectedDrugName: item.drugName || item.name || '',
      specification: item.specification || '',
      dosage: item.dosage || '',
      frequency: item.frequency || '',
      route: item.route || '口服',
      days: item.days || '',
      quantity: item.quantity || '',
      unit: item.unit || '盒',
      unitPrice: item.unitPrice || ''
    }
    ensureSelectedDrugOption(nextItem)
    return nextItem
  })
}

const extractRecordId = (response) => response?.data?.recordId || response?.data?.recordNo || response?.data?.id

const ensureRecordDraft = async () => {
  if (currentRecordId.value) {
    await updateRecordApi(currentRecordId.value, buildPayload())
    return currentRecordId.value
  }
  const created = await createRecordApi(buildPayload())
  const newId = extractRecordId(created)
  if (newId) {
    currentRecordId.value = newId
    return newId
  }
  throw new Error('保存病历失败，无法创建处方')
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
      const newId = extractRecordId(created)
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
      recordId = extractRecordId(created)
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

const submitPrescriptionForReview = async () => {
  prescriptionError.value = ''
  if (!form.chiefComplaint.trim() || !form.initialDiagnosis.trim()) {
    prescriptionError.value = '请先完善主诉和初步诊断'
    return
  }
  const itemError = validatePrescriptionItems()
  if (itemError) {
    prescriptionError.value = itemError
    return
  }
  prescriptionSubmitting.value = true
  try {
    const recordId = await ensureRecordDraft()
    const res = await createPrescriptionApi({
      recordId: String(recordId),
      patientId: String(route.query.patientId || userStore.userInfo?.patientId || ''),
      doctorId: String(route.query.doctorId || userStore.userInfo?.doctorId || ''),
      departmentId: String(route.query.departmentId || userStore.userInfo?.departmentId || ''),
      source: 'OUTPATIENT',
      items: getPrescriptionDraftItems()
    })
    const prescriptionId = res.data?.prescriptionId
    if (!prescriptionId) throw new Error('处方创建失败，请重试')
    await submitPrescriptionApi(prescriptionId)
    submittedPrescriptionId.value = prescriptionId
    ElMessage.success('处方已提交审方')
  } catch (e) {
    prescriptionError.value = e?.response?.data?.message || e?.message || '处方提交失败，请重试'
  } finally {
    prescriptionSubmitting.value = false
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
  const prescriptionDraftItems = getPrescriptionDraftItems()
  if (prescriptionDraftItems.length === 0) {
    ElMessage.warning('请先填写结构化处方药品')
    return
  }
  medAnalysisVisible.value = true
  medAnalysisResult.value = null
  medAnalysisLoading.value = true
  try {
    const payload = {
      recordId: currentRecordId.value || null,
      prescriptions: prescriptionDraftItems.length ? prescriptionDraftItems : [{ drugName: buildPrescriptionSnapshot() }],
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
        applyPrescriptionDetail(d.prescriptions)
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

.structured-prescription {
  display: grid;
  gap: 14px;
  max-width: 900px;
  margin: 0 auto 18px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  background: rgba(248, 250, 252, .7);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.section-title,
.section-subtitle {
  margin: 0;
}

.section-title {
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.section-subtitle {
  margin-top: 4px;
  font-size: var(--font-sm);
  line-height: 1.6;
  color: var(--text-secondary);
}

.prescription-item {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border-lighter);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
}

.prescription-item__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-primary);
}

.prescription-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.field-control {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.field-control span {
  font-size: var(--font-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.field-control :deep(.el-input__wrapper) {
  min-height: var(--touch-target);
}

.field-control :deep(.el-select),
.field-control :deep(.el-select__wrapper) {
  width: 100%;
}

.prescription-summary {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px;
  border-top: 1px solid var(--border-lighter);
  color: var(--text-regular);
}

.prescription-summary strong {
  font-size: var(--font-lg);
  color: var(--text-primary);
}

.inline-error {
  padding: 12px;
  border: 1px solid rgba(185, 28, 28, .22);
  border-radius: var(--radius-sm);
  background: #fef2f2;
  color: var(--el-color-danger);
  font-size: var(--font-sm);
  line-height: 1.6;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
  margin-top: 20px;
}

.record-action {
  min-width: var(--touch-target);
  min-height: var(--touch-target);
  touch-action: manipulation;
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

@media (max-width: 768px) {
  .page-header,
  .patient-bar,
  .section-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .page-header .el-button,
  .section-heading .record-action {
    width: 100%;
  }

  .patient-bar {
    gap: 8px;
  }

  .record-form {
    max-width: 100%;
  }

  .record-form :deep(.el-form-item) {
    display: block;
  }

  .record-form :deep(.el-form-item__label) {
    display: block;
    width: auto !important;
    margin-bottom: 6px;
    text-align: left;
    line-height: 1.5;
  }

  .record-form :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }

  .prescription-grid {
    grid-template-columns: 1fr;
  }

  .prescription-item__header {
    align-items: stretch;
    flex-direction: column;
  }

  .prescription-item__header .record-action {
    width: 100%;
  }

  .prescription-summary {
    justify-content: space-between;
  }

  .form-footer {
    display: grid;
    grid-template-columns: 1fr;
  }

  .form-footer .el-button {
    width: 100%;
    margin-left: 0;
    min-height: var(--touch-target);
  }
}
</style>

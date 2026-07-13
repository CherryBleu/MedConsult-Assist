<template>
  <div class="page-container">
    <div class="card-box">
      <h2 class="page-title">个人信息</h2>

      <!-- 场景1：未关联患者档案（历史脏账号 patient_id 为 NULL）→ 补建档 -->
      <div v-if="!hasPatientId" v-loading="loading" class="info-card">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="当前账号未关联患者档案"
          description="你的账号尚未建立患者档案，挂号、预约查询、智能问诊等功能无法使用。请补充以下信息建立档案（姓名和手机号已从注册信息同步）。"
        />
        <el-form
          ref="archiveFormRef"
          :model="archiveForm"
          :rules="archiveRules"
          label-width="100px"
          class="archive-form"
        >
          <el-form-item label="姓名">
            <span class="sync-text">{{ userStore.userInfo?.name || '—' }}（已从注册信息同步）</span>
          </el-form-item>
          <el-form-item label="手机号">
            <span class="sync-text">{{ userStore.userInfo?.phoneMasked || '—' }}（已从注册信息同步）</span>
          </el-form-item>
          <el-form-item label="身份证号" prop="idCard">
            <el-input v-model="archiveForm.idCard" placeholder="请输入身份证号" maxlength="18" />
          </el-form-item>
          <el-form-item label="性别" prop="gender">
            <el-radio-group v-model="archiveForm.gender">
              <el-radio value="MALE">男</el-radio>
              <el-radio value="FEMALE">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="出生日期" prop="birthDate">
            <el-date-picker
              v-model="archiveForm.birthDate"
              type="date"
              placeholder="选择出生日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>
        <div class="action-bar">
          <el-button type="primary" :loading="saving" @click="submitArchive">建立档案</el-button>
        </div>
      </div>

      <!-- 场景2：已关联患者档案 → 查看/编辑 -->
      <div v-else v-loading="loading" class="info-card">
        <div class="avatar-section">
          <el-avatar :size="80" class="user-avatar">
            {{ patientInfo.name?.charAt(0) || '用' }}
          </el-avatar>
          <div class="user-name">{{ patientInfo.name }}</div>
          <el-tag size="small" type="primary">{{ patientInfo.patientNo }}</el-tag>
        </div>

        <!-- 查看模式 -->
        <el-form v-if="!editing" label-width="100px" class="info-form">
          <el-form-item label="姓名">
            <span class="info-text">{{ patientInfo.name }}</span>
          </el-form-item>
          <el-form-item label="性别">
            <span class="info-text">{{ patientInfo.gender === 'MALE' ? '男' : patientInfo.gender === 'FEMALE' ? '女' : '未设置' }}</span>
          </el-form-item>
          <el-form-item label="出生日期">
            <span class="info-text">{{ patientInfo.birthDate || '未设置' }}</span>
          </el-form-item>
          <el-form-item label="手机号">
            <span class="info-text">{{ patientInfo.phone }}</span>
          </el-form-item>
          <el-form-item label="证件号">
            <span class="info-text">{{ patientInfo.idNo }}</span>
          </el-form-item>
          <el-form-item label="联系地址">
            <span class="info-text">{{ patientInfo.address || '未设置' }}</span>
          </el-form-item>
          <el-form-item label="过敏史">
            <span class="info-text">{{ formatList(patientInfo.allergies) }}</span>
          </el-form-item>
          <el-form-item label="既往病史">
            <span class="info-text">{{ formatList(patientInfo.pastMedicalHistory) }}</span>
          </el-form-item>
          <el-form-item label="档案状态">
            <el-tag type="success">正常</el-tag>
          </el-form-item>
        </el-form>

        <!-- 编辑模式：仅可编辑后端 UpdateRequest 允许的字段 -->
        <el-form v-else ref="editFormRef" :model="editForm" label-width="100px" class="info-form">
          <el-form-item label="手机号">
            <el-input v-model="editForm.phone" placeholder="如需修改请输入新手机号，留空则保持原号" maxlength="11" />
          </el-form-item>
          <el-form-item label="联系地址">
            <el-input v-model="editForm.address" placeholder="请输入联系地址" />
          </el-form-item>
          <el-form-item label="过敏史">
            <el-input
              v-model="editForm.allergiesText"
              type="textarea"
              :rows="2"
              placeholder="多个过敏源请用顿号或逗号分隔"
            />
          </el-form-item>
          <el-form-item label="既往病史">
            <el-input
              v-model="editForm.pastMedicalHistoryText"
              type="textarea"
              :rows="2"
              placeholder="多个病史请用顿号或逗号分隔"
            />
          </el-form-item>
        </el-form>

        <div class="action-bar">
          <template v-if="!editing">
            <el-button type="primary" @click="startEdit">编辑档案</el-button>
            <el-button @click="goToHealthArchive">查看健康档案</el-button>
          </template>
          <template v-else>
            <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
            <el-button @click="cancelEdit">取消</el-button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getPatientInfoApi, updatePatientInfoApi } from '@/api/patient'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const patientInfo = ref({})
const editFormRef = ref(null)
const archiveFormRef = ref(null)

// 是否已关联患者档案（patientId 为 null/空 → 未关联，需补建档）
const hasPatientId = computed(() => !!userStore.userInfo?.patientId)

// 编辑表单（对齐后端 PatientDTO.UpdateRequest 可更新字段）
const editForm = ref({
  phone: '',
  address: '',
  allergiesText: '',
  pastMedicalHistoryText: ''
})

// 补建档表单：只需补充身份证号/性别/出生日期（姓名和手机号从 sys_user 同步）
const archiveForm = ref({
  idCard: '',
  gender: '',
  birthDate: ''
})

// 身份证号校验（15位旧版或18位新版）
const validateIdCard = (rule, value, callback) => {
  if (!value || !value.trim()) {
    callback(new Error('请输入身份证号'))
    return
  }
  if (/(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/.test(value.trim())) {
    callback()
  } else {
    callback(new Error('身份证号格式不正确（须 15 或 18 位）'))
  }
}

const archiveRules = {
  idCard: [{ required: true, validator: validateIdCard, trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  birthDate: [{ required: true, message: '请选择出生日期', trigger: 'change' }]
}

// List<String> → 顿号分隔字符串（展示用）
const formatList = (v) => {
  if (!v) return '未设置'
  if (Array.isArray(v)) return v.length ? v.join('、') : '未设置'
  return v || '未设置'
}

// 顿号/逗号分隔字符串 → List<String>（编辑提交用）
const textToList = (text) => {
  if (!text || !text.trim()) return []
  return text.split(/[、,，\s]+/).map(s => s.trim()).filter(s => s)
}

const getPatientInfo = async () => {
  if (!hasPatientId.value) {
    return
  }
  const patientId = userStore.userInfo.patientId
  loading.value = true
  try {
    const res = await getPatientInfoApi(patientId)
    patientInfo.value = res.data || {}
  } finally {
    loading.value = false
  }
}

// 进入编辑模式：把 List 字段转成文本填入表单
const startEdit = () => {
  editForm.value = {
    // phone 后端返回的是脱敏值（如 139****0001），不能回填编辑框——
    // 用户若不改动直接保存会把脱敏串写回 DB 损坏真实手机号。
    // 编辑框留空，placeholder 提示"如需修改请输入新手机号"；不填则提交 null（后端保持原值）。
    phone: '',
    address: patientInfo.value.address || '',
    allergiesText: Array.isArray(patientInfo.value.allergies) ? patientInfo.value.allergies.join('、') : '',
    pastMedicalHistoryText: Array.isArray(patientInfo.value.pastMedicalHistory) ? patientInfo.value.pastMedicalHistory.join('、') : ''
  }
  editing.value = true
}

const cancelEdit = () => {
  editing.value = false
}

const saveEdit = async () => {
  const patientId = userStore.userInfo.patientId
  saving.value = true
  try {
    const payload = {
      phone: editForm.value.phone || null,
      address: editForm.value.address || null,
      allergies: textToList(editForm.value.allergiesText),
      pastMedicalHistory: textToList(editForm.value.pastMedicalHistoryText)
    }
    await updatePatientInfoApi(patientId, payload)
    ElMessage.success('档案更新成功')
    editing.value = false
    await getPatientInfo()
  } catch (e) {
    // 错误 toast 由 request 拦截器统一弹
  } finally {
    saving.value = false
  }
}

// 补建档：后端自动建档+绑定+重签JWT，返回新 token 后前端存储，无需重新登录
const submitArchive = async () => {
  if (!archiveFormRef.value) return
  await archiveFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      // 后端用 sys_user 已有的 name/phone 自动建档，前端只补充 idCard/gender/birthDate
      // 返回新的 accessToken/refreshToken（已带 patientId），存储后无需重新登录
      await userStore.bindPatient({
        idCard: archiveForm.value.idCard.trim(),
        gender: archiveForm.value.gender,
        birthDate: archiveForm.value.birthDate || null
      })
      ElMessage.success('档案建立成功，现已可使用全部功能')
      await getPatientInfo()
    } catch (e) {
      // 错误 toast 由 request 拦截器统一弹
    } finally {
      saving.value = false
    }
  })
}

const goToHealthArchive = () => {
  router.push('/patient/health-archive')
}

onMounted(() => {
  getPatientInfo()
})
</script>

<style scoped>
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}

.info-card {
  min-height: 200px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-light);
}
.user-avatar {
  background: var(--primary-color);
  font-size: 32px;
}
.user-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.info-form {
  max-width: 500px;
  margin: 0 auto;
}
.archive-form {
  max-width: 500px;
  margin: 20px auto 0;
}
.info-text {
  font-size: 14px;
  color: var(--text-primary);
}
.sync-text {
  font-size: 14px;
  color: var(--text-secondary);
}

.action-bar {
  text-align: center;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}
</style>

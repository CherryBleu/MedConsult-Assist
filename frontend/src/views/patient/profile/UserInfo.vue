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
          <el-form-item label="档案状态">
            <el-tag type="success">正常</el-tag>
          </el-form-item>
        </el-form>

        <!-- 健康档案 -->
        <div v-if="!editing" class="health-archive-section">
          <div class="section-divider">
            <span class="divider-text">健康档案</span>
          </div>

          <!-- 过敏史 -->
          <div class="archive-item">
            <div class="archive-label">
              <el-icon :size="16" color="#ff4d4f"><Warning /></el-icon>
              <span>过敏史</span>
            </div>
            <div class="archive-value">
              <el-tag v-for="(item, index) in allergyList" :key="index" type="danger" effect="light" size="small">
                {{ item }}
              </el-tag>
              <span v-if="!allergyList.length" class="empty-text">暂无记录</span>
            </div>
          </div>

          <!-- 既往病史 -->
          <div class="archive-item">
            <div class="archive-label">
              <el-icon :size="16" color="#faad14"><Document /></el-icon>
              <span>既往病史</span>
            </div>
            <div class="archive-value">{{ formatList(patientInfo.pastMedicalHistory) }}</div>
          </div>

          <!-- 家族病史 -->
          <div class="archive-item">
            <div class="archive-label">
              <el-icon :size="16" color="#1677ff"><UserFilled /></el-icon>
              <span>家族病史</span>
            </div>
            <div class="archive-value">{{ formatList(patientInfo.familyHistory) }}</div>
          </div>

          <!-- 紧急联系人 -->
          <div class="archive-item">
            <div class="archive-label">
              <el-icon :size="16" color="#52c41a"><Phone /></el-icon>
              <span>紧急联系人</span>
            </div>
            <div class="archive-value">
              <template v-if="patientInfo.emergencyContact?.name">
                姓名：{{ patientInfo.emergencyContact.name }}　关系：{{ patientInfo.emergencyContact.relation }}　电话：{{ patientInfo.emergencyContact.phone }}
              </template>
              <span v-else class="empty-text">暂无记录</span>
            </div>
          </div>
        </div>

        <!-- 编辑模式：仅可编辑后端 UpdateRequest 允许且 DetailResponse 返回的字段 -->
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
          </template>
          <template v-else>
            <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
            <el-button @click="cancelEdit">取消</el-button>
          </template>
        </div>
      </div>

      <!-- 修改密码 -->
      <div class="card-box mt-20">
        <div class="section-header">
          <el-icon><Lock /></el-icon>
          <span class="section-title">修改密码</span>
        </div>
        <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px" class="password-form">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入原密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码（至少6位）" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="passwordLoading" @click="submitChangePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Warning, Document, UserFilled, Phone, Lock } from '@element-plus/icons-vue'
import { getPatientInfoApi, updatePatientInfoApi } from '@/api/patient'
import { useUserStore } from '@/store/modules/user'
import { changePasswordApi } from '@/api/system'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const editing = ref(false)
const patientInfo = ref({})
const editFormRef = ref(null)
const archiveFormRef = ref(null)
const passwordLoading = ref(false)
const passwordFormRef = ref(null)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: (rule, value, callback) => {
      if (value !== passwordForm.newPassword) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    }, trigger: 'blur' }
  ]
}

// 是否已关联患者档案（patientId 为 null/空 → 未关联，需补建档）
const hasPatientId = computed(() => !!userStore.userInfo?.patientId)

// 过敏史列表（兼容数组和字符串）
const allergyList = computed(() => {
  const a = patientInfo.value.allergies
  if (!a) return []
  if (Array.isArray(a)) return a.filter(i => i && String(i).trim())
  if (typeof a === 'string') return a.split(/[,、]/).filter(i => i.trim())
  return []
})

// 编辑表单（仅包含后端 DetailResponse 返回且 UpdateRequest 可更新的字段）
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
    phone: '',
    address: patientInfo.value.address || '',
    allergiesText: Array.isArray(patientInfo.value.allergies) ? patientInfo.value.allergies.join('、') : (patientInfo.value.allergies || ''),
    pastMedicalHistoryText: Array.isArray(patientInfo.value.pastMedicalHistory) ? patientInfo.value.pastMedicalHistory.join('、') : (patientInfo.value.pastMedicalHistory || '')
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

const submitChangePassword = async () => {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    passwordLoading.value = true
    try {
      await changePasswordApi({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      ElMessage.success('密码修改成功')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    } catch (e) {
      // 错误提示由拦截器统一处理
    } finally {
      passwordLoading.value = false
    }
  })
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

/* 健康档案 */
.health-archive-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}
.section-divider {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
.divider-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  padding-right: 12px;
  background: #fff;
  position: relative;
  z-index: 1;
}
.section-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-light);
  margin-left: -100px;
}
.archive-item {
  display: flex;
  margin-bottom: 16px;
}
.archive-label {
  width: 100px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--text-secondary);
}
.archive-value {
  flex: 1;
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.6;
}
.archive-value .el-tag + .el-tag {
  margin-left: 8px;
}
.empty-text {
  color: var(--text-secondary);
}

.mt-20 {
  margin-top: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.password-form {
  max-width: 400px;
}
</style>

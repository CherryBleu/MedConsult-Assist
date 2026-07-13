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
          description="你的账号尚未建立患者档案，挂号、预约查询、智能问诊等功能无法使用。请完善以下信息建立档案。"
        />
        <el-form
          ref="archiveFormRef"
          :model="archiveForm"
          :rules="archiveRules"
          label-width="100px"
          class="archive-form"
        >
          <el-form-item label="姓名" prop="name">
            <el-input v-model="archiveForm.name" placeholder="请输入真实姓名" />
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
          <el-form-item label="身份证号" prop="idNo">
            <el-input v-model="archiveForm.idNo" placeholder="请输入身份证号" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="archiveForm.phone" placeholder="请输入手机号" />
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
            <el-input v-model="editForm.phone" placeholder="请输入手机号" />
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
import { getPatientInfoApi, updatePatientInfoApi, addPatientApi } from '@/api/patient'
import { bindPatientApi } from '@/api/user'
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

// 补建档表单（对齐后端 PatientDTO.CreateRequest）
const archiveForm = ref({
  name: '',
  gender: 'MALE',
  birthDate: '',
  idNo: '',
  phone: ''
})

const archiveRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  idNo: [{ required: true, message: '请输入身份证号', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
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
    // 未关联档案：预填手机号（从登录态脱敏手机号不可用，留空让用户填）
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
    phone: patientInfo.value.phone || '',
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

// 补建档：先 POST /patients 创建档案，再 POST /auth/me/bind-patient 绑定到当前用户
const submitArchive = async () => {
  if (!archiveFormRef.value) return
  await archiveFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      // 1. 创建患者档案（对齐 PatientDTO.CreateRequest）
      const createRes = await addPatientApi({
        name: archiveForm.value.name,
        gender: archiveForm.value.gender,
        birthDate: archiveForm.value.birthDate || null,
        idType: 'ID_CARD',
        idNo: archiveForm.value.idNo,
        phone: archiveForm.value.phone
      })
      const patientNo = createRes.data?.patientId || createRes.data?.patientNo
      if (!patientNo) {
        ElMessage.error('档案创建成功但未返回编号，请联系管理员')
        return
      }
      // 2. 绑定到当前登录用户
      await bindPatientApi(patientNo)
      ElMessage.success('档案建立并绑定成功，请重新登录以刷新权限')
      // 3. 刷新前端 userInfo（patientId 现在有值了）
      await userStore.getUserInfo()
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

.action-bar {
  text-align: center;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}
</style>

<template>
  <div class="page-container">
    <div class="card-box">
      <h2 class="page-title">个人中心</h2>

      <div v-loading="loading" class="info-card">
        <div class="avatar-section">
          <el-avatar :size="80" class="user-avatar">
            {{ profile.name?.charAt(0) || '医' }}
          </el-avatar>
          <div class="user-name">{{ profile.name || '医生' }}</div>
          <el-tag size="small" type="primary">{{ profile.doctorNo || '—' }}</el-tag>
        </div>

        <el-form label-width="100px" class="info-form">
          <el-form-item label="姓名">
            <span class="info-text">{{ profile.name || '—' }}</span>
          </el-form-item>
          <el-form-item label="工号">
            <span class="info-text">{{ profile.doctorNo || '—' }}</span>
          </el-form-item>
          <el-form-item label="职称">
            <span class="info-text">{{ profile.title || '—' }}</span>
          </el-form-item>
          <el-form-item label="所属科室">
            <span class="info-text">{{ profile.departmentName || '—' }}</span>
          </el-form-item>
          <el-form-item label="手机号">
            <span class="info-text">{{ profile.phoneMasked || '—' }}</span>
          </el-form-item>
          <el-form-item label="擅长">
            <span class="info-text">{{ profile.specialties || '—' }}</span>
          </el-form-item>
        </el-form>
      </div>

      <div class="card-box mt-20">
        <div class="section-header">
          <el-icon><Iphone /></el-icon>
          <span class="section-title">修改手机号</span>
        </div>
        <el-form :model="phoneForm" :rules="phoneRules" ref="phoneFormRef" label-width="100px" class="password-form">
          <el-form-item label="新手机号" prop="phone">
            <el-input
              v-model="phoneForm.phone"
              placeholder="请输入11位手机号"
              maxlength="11"
              inputmode="numeric"
              @input="phoneForm.phone = normalizePhoneInput($event)"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="phoneLoading" @click="submitChangePhone">修改手机号</el-button>
          </el-form-item>
        </el-form>
      </div>

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
            <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码（至少8位且含字母和数字）" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Iphone, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { getDoctorDetailApi } from '@/api/doctor'
import { changePasswordApi } from '@/api/system'
import { updateMyPhoneApi } from '@/api/user'

const userStore = useUserStore()
const loading = ref(false)
const phoneLoading = ref(false)
const passwordLoading = ref(false)
const phoneFormRef = ref(null)
const passwordFormRef = ref(null)

const profile = ref({})

const phoneForm = reactive({
  phone: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const normalizePhoneInput = (value) => String(value || '').replace(/\D/g, '').slice(0, 11)

const phoneRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的11位手机号', trigger: 'blur' }
  ]
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/, message: '密码须8-64位且至少含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const mergeProfile = (doctor = {}, user = userStore.userInfo || {}) => {
  const specialties = Array.isArray(doctor.specialties) ? doctor.specialties.join('、') : doctor.specialties
  profile.value = {
    ...user,
    ...doctor,
    name: doctor.name || user.name,
    doctorNo: doctor.doctorNo || doctor.id || user.doctorNo || user.doctorId,
    departmentName: doctor.departmentName || doctor.deptName || user.departmentName,
    phoneMasked: user.phoneMasked || user.phone || '',
    specialties: specialties || ''
  }
}

const loadProfile = async () => {
  loading.value = true
  try {
    const user = await userStore.getUserInfo()
    let doctor = null
    const doctorId = user.doctorId || user.doctorNo
    if (doctorId) {
      const detailRes = await getDoctorDetailApi(doctorId)
      doctor = detailRes.data
    }
    mergeProfile(doctor || {}, user)
  } catch (error) {
    mergeProfile({}, userStore.userInfo || {})
    ElMessage.error(error?.message || '医生档案加载失败')
  } finally {
    loading.value = false
  }
}

const submitChangePhone = async () => {
  if (!phoneFormRef.value) return
  const valid = await phoneFormRef.value.validate().catch(() => false)
  if (!valid) return
  phoneLoading.value = true
  try {
    const res = await updateMyPhoneApi({ phone: phoneForm.phone })
    userStore.userInfo = res.data
    mergeProfile(profile.value, res.data)
    phoneForm.phone = ''
    ElMessage.success('手机号修改成功')
  } catch (e) {
    // 错误提示由拦截器统一处理
  } finally {
    phoneLoading.value = false
  }
}

const submitChangePassword = async () => {
  if (!passwordFormRef.value) return
  const valid = await passwordFormRef.value.validate().catch(() => false)
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
}

onMounted(loadProfile)
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
.info-text {
  font-size: 14px;
  color: var(--text-primary);
  overflow-wrap: anywhere;
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

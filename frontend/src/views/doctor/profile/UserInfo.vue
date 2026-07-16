<template>
  <div class="page-container">
    <div class="card-box">
      <h2 class="page-title">个人中心</h2>

      <div class="info-card">
        <div class="avatar-section">
          <el-avatar :size="80" class="user-avatar">
            {{ userStore.userInfo.name?.charAt(0) || '医' }}
          </el-avatar>
          <div class="user-name">{{ userStore.userInfo.name || '医生' }}</div>
          <el-tag size="small" type="primary">{{ userStore.userInfo.doctorNo || '—' }}</el-tag>
        </div>

        <el-form label-width="100px" class="info-form">
          <el-form-item label="姓名">
            <span class="info-text">{{ userStore.userInfo.name || '—' }}</span>
          </el-form-item>
          <el-form-item label="工号">
            <span class="info-text">{{ userStore.userInfo.doctorNo || '—' }}</span>
          </el-form-item>
          <el-form-item label="职称">
            <span class="info-text">{{ userStore.userInfo.title || '—' }}</span>
          </el-form-item>
          <el-form-item label="所属科室">
            <span class="info-text">{{ userStore.userInfo.departmentName || '—' }}</span>
          </el-form-item>
          <el-form-item label="手机号">
            <span class="info-text">{{ userStore.userInfo.phone || '—' }}</span>
          </el-form-item>
          <el-form-item label="挂号费">
            <span class="info-text">¥{{ userStore.userInfo.registrationFee || 0 }}</span>
          </el-form-item>
        </el-form>
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
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { changePasswordApi } from '@/api/system'

const userStore = useUserStore()
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
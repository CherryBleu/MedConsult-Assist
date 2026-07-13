<template>
  <div class="register-page">
    <div class="register-box">
      <div class="register-header">
        <h2>用户注册</h2>
        <p>智慧医疗系统账号注册</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" class="register-form">
        <el-form-item label="账号" prop="account">
          <el-input v-model="form.account" placeholder="请输入账号（4-20位字母数字）" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码（8-20位，含字母和数字）" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="请再次输入密码" show-password />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="form.idCard" placeholder="请输入身份证号" maxlength="18" />
        </el-form-item>
        <el-form-item label="注册身份" prop="role">
          <el-radio-group v-model="form.role" @change="onRoleChange">
            <el-radio value="PATIENT">患者</el-radio>
            <el-radio value="DOCTOR">医生</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-alert
          v-if="form.role === 'DOCTOR'"
          type="info"
          :closable="false"
          style="margin-bottom: 18px"
          title="医生账号注册后，科室/职称/擅长等档案信息由管理员在「医生管理」中维护。"
        />

        <el-form-item>
          <el-button type="primary" size="large" style="width: 100%" :loading="submitting" @click="handleRegister">
            立即注册
          </el-button>
        </el-form-item>

        <div class="login-link">
          已有账号？<router-link to="/login">立即登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { registerApi } from '@/api/user'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)

const form = reactive({
  account: '',
  password: '',
  confirmPassword: '',
  name: '',
  phone: '',
  idCard: '',
  role: 'PATIENT'
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validateIdCard = (rule, value, callback) => {
  // PATIENT 角色注册即建档，身份证号必填；DOCTOR 选填
  if (form.role === 'PATIENT' && (!value || !value.trim())) {
    callback(new Error('患者注册请填写身份证号（用于自动建档）'))
    return
  }
  if (!value || /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/.test(value)) {
    callback()
  } else {
    callback(new Error('身份证号格式不正确'))
  }
}

const rules = {
  account: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9]{4,20}$/, message: '账号为4-20位字母或数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为8-20位', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,}$/, message: '密码须至少含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { pattern: /^[\u4e00-\u9fa5A-Za-z·.\s]{1,50}$/, message: '姓名须为中文/字母/空格/点（不含数字或特殊符号）', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  idCard: [{ validator: validateIdCard, trigger: 'blur' }],
  role: [{ required: true, message: '请选择注册身份', trigger: 'change' }]
}

// 切换角色时重新校验 idCard（PATIENT 必填，DOCTOR 选填）
const onRoleChange = () => {
  formRef.value?.validateField('idCard')
}

const handleRegister = async () => {
  try {
    await formRef.value.validate()
  } catch (e) {
    // 校验失败时 Element Plus 已在表单项下方展示错误，无需额外处理
    return
  }
  submitting.value = true
  try {
    // 后端 RegisterRequest 现接受 account/password/phone/name/role/idCard；
    // PATIENT 角色注册时 idCard 必填（后端注册即建档用）。
    // confirmPassword/departmentId/title/specialty 仍为前端辅助字段，不提交。
    const payload = {
      account: form.account,
      password: form.password,
      name: form.name,
      phone: form.phone,
      role: form.role
    }
    // PATIENT 角色补传 idCard（建档必需）；DOCTOR 不需要
    if (form.role === 'PATIENT') {
      payload.idCard = form.idCard
    }
    await registerApi(payload)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    // 错误消息已由 request 拦截器 ElMessage.error 展示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.register-page {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.register-box {
  width: 480px;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  max-height: 90vh;
  overflow-y: auto;
}
.register-header {
  text-align: center;
  margin-bottom: 30px;
}
.register-header h2 {
  font-size: 24px;
  color: var(--text-primary);
  margin: 0 0 8px;
}
.register-header p {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}
.login-link {
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
}
.login-link a {
  color: var(--primary-color);
  text-decoration: none;
  margin-left: 4px;
}
</style>

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
          <el-radio-group v-model="form.role">
            <el-radio value="PATIENT">患者</el-radio>
            <el-radio value="DOCTOR">医生</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.role === 'DOCTOR'" label="科室" prop="departmentId">
          <el-select v-model="form.departmentId" placeholder="请选择所属科室" style="width: 100%">
            <el-option v-for="dept in deptList" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'DOCTOR'" label="职称" prop="title">
          <el-select v-model="form.title" placeholder="请选择职称" style="width: 100%">
            <el-option label="主任医师" value="主任医师" />
            <el-option label="副主任医师" value="副主任医师" />
            <el-option label="主治医师" value="主治医师" />
            <el-option label="住院医师" value="住院医师" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.role === 'DOCTOR'" label="擅长" prop="specialty">
          <el-input v-model="form.specialty" type="textarea" :rows="2" placeholder="请输入擅长领域" />
        </el-form-item>

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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { registerApi } from '@/api/user'
import { getDepartmentListApi } from '@/api/department'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const deptList = ref([])

const form = reactive({
  account: '',
  password: '',
  confirmPassword: '',
  name: '',
  phone: '',
  idCard: '',
  role: 'PATIENT',
  departmentId: null,
  title: '',
  specialty: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validateIdCard = (rule, value, callback) => {
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
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  idCard: [{ validator: validateIdCard, trigger: 'blur' }],
  role: [{ required: true, message: '请选择注册身份', trigger: 'change' }],
  departmentId: [{ required: true, message: '请选择科室', trigger: 'change' }],
  title: [{ required: true, message: '请选择职称', trigger: 'change' }]
}

const getDeptList = async () => {
  try {
    const res = await getDepartmentListApi()
    deptList.value = res.data
  } catch (e) {}
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
    // 后端 RegisterRequest 只接受 account/password/phone/name/role；
    // confirmPassword/idCard/departmentId/title/specialty 为前端辅助字段，不提交。
    const payload = {
      account: form.account,
      password: form.password,
      name: form.name,
      phone: form.phone,
      role: form.role
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

onMounted(() => {
  getDeptList()
})
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

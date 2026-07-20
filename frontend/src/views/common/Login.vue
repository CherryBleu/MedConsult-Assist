<template>
  <main class="login-container" aria-labelledby="login-title">
    <!-- 左侧品牌介绍区 -->
    <section class="login-left" :class="{ 'patient-theme': selectedEntry === 'patient', 'staff-theme': selectedEntry === 'staff' }" aria-label="系统能力">
      <div class="brand-box">
        <h1 class="brand-title">智慧医疗系统</h1>
        <p class="brand-desc">智能分诊 · 电子病历 · AI辅助诊疗</p>
        <div class="brand-features">
          <div class="feature-item">
            <el-icon :size="20"><User /></el-icon>
            <span>在线预约挂号</span>
          </div>
          <div class="feature-item">
            <el-icon :size="20"><Document /></el-icon>
            <span>电子病历管理</span>
          </div>
          <div class="feature-item">
            <el-icon :size="20"><Cpu /></el-icon>
            <span>AI智能辅助</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 右侧登录表单区 -->
    <section class="login-right" aria-label="登录表单">
      <div class="login-card">
        <!-- 入口选择界面 -->
        <template v-if="!selectedEntry">
          <h2 id="login-title" class="login-title">请选择登录入口</h2>
          <div class="entry-selector">
            <button type="button" class="entry-card patient-entry" @click="selectEntry('patient')">
              <el-icon :size="48" class="entry-icon"><User /></el-icon>
              <span class="entry-name">患者入口</span>
              <span class="entry-desc">预约挂号 · 在线问诊 · 健康档案</span>
            </button>
            <button type="button" class="entry-card staff-entry" @click="selectEntry('staff')">
              <el-icon :size="48" class="entry-icon"><Suitcase /></el-icon>
              <span class="entry-name">工作人员入口</span>
              <span class="entry-desc">医生 · 管理员 · 药房管理员</span>
            </button>
          </div>
        </template>

        <!-- 登录表单界面 -->
        <template v-else>
          <button v-if="!fixedEntryMode" type="button" class="back-entry" @click="backToEntrySelect">
            <el-icon><ArrowLeft /></el-icon>
            <span>返回选择</span>
          </button>
          <h2 id="login-title" class="login-title">
            {{ selectedEntry === 'patient' ? '患者登录' : '工作人员登录' }}
          </h2>
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            class="login-form"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="account">
              <el-input
                v-model="loginForm.account"
                :placeholder="selectedEntry === 'patient' ? '请输入患者账号' : '请输入工号/账号'"
                size="large"
                :prefix-icon="User"
                autocomplete="username"
                :aria-label="selectedEntry === 'patient' ? '患者账号' : '工号或账号'"
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入登录密码"
                size="large"
                :prefix-icon="Lock"
                show-password
                autocomplete="current-password"
                aria-label="登录密码"
              />
            </el-form-item>

            <el-form-item>
              <div class="login-options">
                <el-checkbox v-model="loginForm.remember">记住账号</el-checkbox>
              </div>
            </el-form-item>

            <el-form-item>
              <el-button
                :type="selectedEntry === 'patient' ? 'primary' : 'warning'"
                size="large"
                class="login-btn"
                :loading="loading"
                @click="handleLogin"
              >
                登 录
              </el-button>
            </el-form-item>
          </el-form>

          <div class="login-tip" v-if="selectedEntry === 'patient'">
            <span>还没有账号？</span>
            <router-link to="/register" class="register-link">立即注册</router-link>
          </div>

          <div class="demo-accounts" v-if="isMockMode">
            <div class="demo-title">演示账号（密码任意6位以上）：</div>
            <div class="demo-list">
              <template v-if="selectedEntry === 'patient'">
                <button type="button" class="demo-tag" @click="fillAccount('patient')">患者/patient</button>
              </template>
              <template v-else>
                <button type="button" class="demo-tag" @click="fillAccount('doctor')">医生/doctor</button>
                <button type="button" class="demo-tag" @click="fillAccount('admin')">管理员/admin</button>
                <button type="button" class="demo-tag" @click="fillAccount('pharmacy')">药房/pharmacy</button>
              </template>
            </div>
          </div>
        </template>
      </div>
    </section>
  </main>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Document, Cpu, Suitcase, ArrowLeft } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const props = defineProps({
  fixedEntry: {
    type: String,
    default: ''
  }
})

const initialEntry = props.fixedEntry || route.meta.entry || null
const selectedEntry = ref(initialEntry)
const loginFormRef = ref(null)
const loading = ref(false)
const isMockMode = import.meta.env.VITE_USE_MOCK === 'true'
const fixedEntryMode = computed(() => Boolean(props.fixedEntry || route.meta.entry))

const loginForm = reactive({
  account: '',
  password: '',
  remember: false,
  clientType: initialEntry ? initialEntry.toUpperCase() : null      // 登录入口：'PATIENT'/'STAFF'，与后端 clientType 契约一致
})

const selectEntry = (entry) => {
  selectedEntry.value = entry
  loginForm.clientType = entry.toUpperCase()   // patient→PATIENT, staff→STAFF
}

const backToEntrySelect = () => {
  selectedEntry.value = null
  loginForm.account = ''
  loginForm.password = ''
  loginForm.clientType = null
}

const loginRules = {
  account: [
    { required: true, message: '请输入登录账号', trigger: 'blur' },
    { min: 3, max: 32, message: '账号长度在 3 到 32 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入登录密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ]
}

// 记住账号回显
onMounted(() => {
  if (route.meta.entry) {
    selectEntry(route.meta.entry)
  }
  const rememberAccount = localStorage.getItem('remember_account')
  if (rememberAccount) {
    loginForm.account = rememberAccount
    loginForm.remember = true
  }
})

const fillAccount = (account) => {
  loginForm.account = account
  // 仅开发环境预填演示密码；生产构建不含明文密码，用户需自行输入
  loginForm.password = import.meta.env.DEV ? '123456' : ''
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      await userStore.login(loginForm)
      
      // 记住账号
      if (loginForm.remember) {
        localStorage.setItem('remember_account', loginForm.account)
      } else {
        localStorage.removeItem('remember_account')
      }

      // 获取用户信息
      await userStore.getUserInfo()

      ElMessage.success('登录成功')
      
      // 跳转目标页
      const redirect = route.query.redirect || '/home'
      router.replace(redirect)
    } catch (err) {
      console.error('登录失败', err)
      ElMessage.error(err?.message || '登录失败，请检查账号或入口')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container {
  width: 100%;
  min-height: 100vh;
  min-height: 100svh;
  display: flex;
  overflow-x: hidden;
  background: linear-gradient(135deg, #f8fbfd 0%, #eef8f9 100%);
}

/* 左侧品牌区 */
.login-left {
  flex: 1;
  background: linear-gradient(135deg, var(--primary-dark) 0%, var(--primary-color) 56%, var(--accent-green) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  transition: background 0.3s ease;
}

.login-left.patient-theme {
  background: linear-gradient(135deg, var(--primary-dark) 0%, var(--primary-color) 54%, var(--accent-green) 100%);
}

.login-left.staff-theme {
  background: linear-gradient(135deg, #92400e 0%, var(--accent-amber) 52%, var(--primary-color) 100%);
}

.brand-box {
  width: min(420px, calc(100% - 48px));
}

.brand-title {
  font-size: clamp(32px, 4vw, 42px);
  font-weight: 600;
  margin-bottom: 16px;
}

.brand-desc {
  font-size: 18px;
  opacity: 0.9;
  margin-bottom: 60px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  opacity: 0.95;
}

/* 右侧登录区 */
.login-right {
  width: 520px;
  background: rgba(248, 251, 253, .94);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.login-card {
  width: min(100%, 380px);
  border-radius: 12px;
}

.login-title {
  font-size: clamp(24px, 4vw, 28px);
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 32px;
  text-align: center;
}

.login-form {
  width: 100%;
}

.login-options {
  width: 100%;
  display: flex;
  justify-content: space-between;
  font-size: 14px;
}

.login-btn {
  width: 100%;
  min-height: 44px;
  font-size: 16px;
}

.login-tip {
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: 20px;
}

.register-link {
  color: var(--primary-color);
  margin-left: 4px;
}

.demo-accounts {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}
.demo-title {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 10px;
}
.demo-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.demo-tag {
  font-size: 12px;
  min-height: 32px;
  padding: 4px 10px;
  background: #f3fafb;
  border-radius: 999px;
  color: var(--primary-color);
  cursor: pointer;
  transition: all .2s;
  user-select: none;
}
.demo-tag:hover {
  background: #e0f2fe;
}

/* 入口选择界面 */
.entry-selector {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 24px;
}

.entry-card {
  width: 100%;
  min-height: 132px;
  padding: 24px;
  border: 1px solid var(--border-light);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s ease;
  text-align: center;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbfd 100%);
  font: inherit;
}

.entry-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-hover);
}

.patient-entry:hover {
  border-color: var(--primary-color);
  background: rgba(22, 119, 255, 0.04);
}

.patient-entry:hover .entry-icon {
  color: var(--primary-color);
}

.staff-entry:hover {
  border-color: var(--accent-amber);
  background: rgba(245, 158, 11, 0.08);
}

.staff-entry:hover .entry-icon {
  color: var(--accent-amber);
}

.entry-icon {
  color: var(--text-secondary);
  margin-bottom: 12px;
  transition: color 0.25s ease;
}

.entry-name {
  display: block;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.entry-desc {
  display: block;
  font-size: 13px;
  color: var(--text-secondary);
}

/* 返回选择 */
.back-entry {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  margin-bottom: 8px;
  width: fit-content;
  min-height: 36px;
  padding: 0 6px;
  border-radius: var(--radius-sm);
  transition: color 0.2s;
}

.back-entry:hover {
  color: var(--primary-color);
  background: var(--bg-hover);
}

.entry-card:focus-visible,
.demo-tag:focus-visible,
.back-entry:focus-visible,
.register-link:focus-visible {
  box-shadow: var(--focus-ring);
}

.login-form :deep(.el-input__wrapper) {
  min-height: 44px;
  box-shadow: 0 0 0 1px rgba(203, 213, 225, .88) inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--primary-color) inset, var(--focus-ring);
}

@media (max-width: 900px) {
  .login-container {
    display: block;
    padding: 16px;
  }

  .login-left {
    min-height: 220px;
    border-radius: var(--radius-xl);
    padding: 28px 20px;
    text-align: center;
  }

  .brand-box {
    width: min(100%, 420px);
  }

  .brand-desc {
    margin-bottom: 24px;
  }

  .brand-features {
    align-items: center;
    gap: 12px;
  }

  .login-right {
    width: 100%;
    padding: 16px 0 0;
    background: transparent;
  }

  .login-card {
    width: 100%;
    max-width: 460px;
    margin: 0 auto;
    padding: 24px 18px;
    background: rgba(255, 255, 255, .94);
    border: 1px solid rgba(255, 255, 255, .72);
    box-shadow: var(--shadow-card);
    backdrop-filter: blur(12px);
  }
}

@media (max-width: 420px) {
  .login-container {
    padding: 12px;
  }

  .login-left {
    min-height: 188px;
    padding: 22px 16px;
  }

  .brand-title {
    margin-bottom: 10px;
  }

  .brand-desc {
    font-size: 15px;
    margin-bottom: 16px;
  }

  .feature-item {
    font-size: 14px;
  }

  .login-title {
    margin-bottom: 22px;
  }

  .entry-card {
    min-height: 118px;
    padding: 18px 14px;
  }
}
</style>

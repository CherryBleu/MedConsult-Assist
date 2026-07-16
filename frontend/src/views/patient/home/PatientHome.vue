<template>
  <div class="page-container">
    <!-- 顶部欢迎横幅 -->
    <div class="banner-card">
      <div class="banner-left">
        <span class="eyebrow">MEDCONSULT ASSIST</span>
        <h2>您好，{{ userStore.userInfo.name || '用户' }}</h2>
        <p>今天也可以从智能分诊、快速挂号和病历追踪开始管理健康。</p>
        <div class="hero-actions">
          <el-button type="primary" round @click="$router.push('/patient/ai-consult')">AI 问诊</el-button>
          <el-button round @click="$router.push('/patient/appointment/department')">立即挂号</el-button>
        </div>
      </div>
      <div class="banner-right">
        <div class="health-orbit">
          <el-icon :size="48" color="#fff"><FirstAidKit /></el-icon>
        </div>
      </div>
    </div>

    <!-- 快捷功能入口 -->
    <div class="card-box mb-20">
      <h3 class="section-title">常用功能</h3>
      <div class="quick-grid">
        <div class="quick-item interactive-card" @click="$router.push('/patient/appointment')">
          <div class="quick-icon icon-blue">
            <el-icon :size="28"><Calendar /></el-icon>
          </div>
          <span>预约挂号</span>
        </div>
        <div class="quick-item interactive-card" @click="$router.push('/patient/triage')">
          <div class="quick-icon icon-green">
            <el-icon :size="28"><Cpu /></el-icon>
          </div>
          <span>智能分诊</span>
        </div>
        <div class="quick-item interactive-card" @click="$router.push('/patient/records')">
          <div class="quick-icon icon-orange">
            <el-icon :size="28"><Document /></el-icon>
          </div>
          <span>我的病历</span>
        </div>
        <div class="quick-item interactive-card" @click="$router.push('/patient/profile')">
          <div class="quick-icon icon-purple">
            <el-icon :size="28"><User /></el-icon>
          </div>
          <span>个人中心</span>
        </div>
      </div>
    </div>

    <!-- 热门科室 -->
    <div class="card-box mb-20">
      <div class="section-header">
        <h3 class="section-title">热门科室</h3>
        <el-button type="primary" link @click="$router.push('/patient/appointment/department')">全部科室</el-button>
      </div>
      <div v-loading="deptLoading" class="dept-grid">
        <div 
          v-for="item in hotDepartments" 
          :key="item.id" 
          class="dept-item"
          @click="goToDoctorList(item.id)"
        >
          <div class="dept-name">{{ item.name }}</div>
          <div class="dept-desc">{{ item.description }}</div>
        </div>
      </div>
    </div>

    <!-- 推荐医生 -->
    <div class="card-box">
      <div class="section-header">
        <h3 class="section-title">推荐医生</h3>
        <el-button type="primary" link @click="$router.push('/patient/appointment/department')">查看更多</el-button>
      </div>
      <div v-loading="doctorLoading" class="doctor-list">
        <div v-for="doctor in recommendDoctors" :key="doctor.id" class="doctor-card">
          <el-avatar :size="56" class="doctor-avatar">
            {{ (doctor.name || '?').charAt(0) }}
          </el-avatar>
          <div class="doctor-info">
            <div class="doctor-name">
              {{ doctor.name }}
              <span class="doctor-title">{{ doctor.title }}</span>
            </div>
            <div class="doctor-special">擅长：{{ doctor.specialties }}</div>
          </div>
          <el-button type="primary" size="small" @click="goToSchedule(doctor.id)">
            预约
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { getDepartmentListApi } from '@/api/department'
import { getDoctorListApi } from '@/api/doctor'

const router = useRouter()
const userStore = useUserStore()

const deptLoading = ref(false)
const doctorLoading = ref(false)
const hotDepartments = ref([])
const recommendDoctors = ref([])

// 获取热门科室（取前6个）
const getHotDepartments = async () => {
  deptLoading.value = true
  try {
    const res = await getDepartmentListApi()
    hotDepartments.value = res.data.slice(0, 6)
  } finally {
    deptLoading.value = false
  }
}

// 获取推荐医生（取前3个）
const getRecommendDoctors = async () => {
  doctorLoading.value = true
  try {
    const res = await getDoctorListApi()
    recommendDoctors.value = res.data.slice(0, 3)
  } finally {
    doctorLoading.value = false
  }
}

const goToDoctorList = (deptId) => {
  const dept = hotDepartments.value.find(i => i.id === deptId)
  router.push({ 
    path: '/patient/appointment/doctor', 
    query: { deptId, deptName: dept?.name } 
  })
}

const goToSchedule = (doctorId) => {
  const doctor = recommendDoctors.value.find(i => i.id === doctorId)
  router.push({ 
    path: '/patient/appointment/schedule', 
    query: { doctorId, doctorName: doctor?.name } 
  })
}

onMounted(() => {
  getHotDepartments()
  getRecommendDoctors()
})
</script>

<style scoped>
/* 横幅 */
.banner-card {
  position: relative;
  overflow: hidden;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: 24px;
  padding: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  box-shadow: 0 24px 60px rgba(22, 119, 255, .22);
}
.banner-card::after {
  content: '';
  position: absolute;
  width: 260px;
  height: 260px;
  right: -70px;
  top: -80px;
  border-radius: 50%;
  background: rgba(255,255,255,.18);
}
.banner-left {
  position: relative;
  z-index: 1;
}
.eyebrow {
  display: inline-flex;
  margin-bottom: 10px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255,255,255,.18);
  font-size: 11px;
  letter-spacing: .12em;
}
.banner-left h2 {
  font-size: 28px;
  margin-bottom: 8px;
}
.banner-left p {
  opacity: 0.92;
  font-size: 14px;
}
.hero-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
.health-orbit {
  position: relative;
  z-index: 1;
  width: 104px;
  height: 104px;
  border-radius: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255,255,255,.18);
  box-shadow: inset 0 0 0 1px rgba(255,255,255,.28);
  backdrop-filter: blur(6px);
}

/* 板块标题 */
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

/* 快捷入口 */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 18px 0;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255,255,255,.92), rgba(248,251,255,.92));
  border: 1px solid var(--border-lighter);
}
.quick-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.icon-blue { background: #1677ff; }
.icon-green { background: #52c41a; }
.icon-orange { background: #faad14; }
.icon-purple { background: #722ed1; }

/* 科室网格 */
.dept-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.dept-item {
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all 0.2s;
}
.dept-item:hover {
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.1);
}
.dept-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.dept-desc {
  font-size: 12px;
  color: var(--text-secondary);
}

/* 医生列表 */
.doctor-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.doctor-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
}
.doctor-avatar {
  background: var(--primary-color);
  flex-shrink: 0;
}
.doctor-info {
  flex: 1;
}
.doctor-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.doctor-title {
  font-size: 12px;
  color: var(--primary-color);
  margin-left: 8px;
  font-weight: normal;
}
.doctor-special {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
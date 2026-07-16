<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo-box">
        <span v-if="!isCollapse" class="logo-text">智慧医疗系统</span>
        <span v-else class="logo-text">医疗</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#ffffff"
        class="side-menu"
      >
        <template v-for="menu in menuList" :key="menu.path">
          <el-menu-item v-if="!menu.children" :index="menu.path">
            <el-icon><component :is="menu.icon" /></el-icon>
            <template #title>{{ menu.title }}</template>
          </el-menu-item>

          <el-sub-menu v-else :index="menu.path">
            <template #title>
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.title }}</span>
            </template>
            <el-menu-item
              v-for="child in menu.children"
              :key="child.path"
              :index="child.path"
            >
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <!-- 右侧主区域 -->
    <el-container>
      <!-- 顶部导航栏 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbList" :key="item.path">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-popover
            v-model:visible="noticePopoverVisible"
            placement="bottom-end"
            :width="320"
            trigger="click"
            popper-class="notice-popover"
          >
            <template #reference>
              <div class="notice-bell" @click="handleNoticeClick">
                <el-badge :value="noticeStore.unreadCount" :hidden="noticeStore.unreadCount === 0" :max="99">
                  <el-icon :size="20"><Bell /></el-icon>
                </el-badge>
              </div>
            </template>
            <div class="notice-panel">
              <div class="notice-header">
                <span class="notice-title">通知</span>
              </div>
              <div class="notice-list">
                <div
                  v-for="notice in noticeStore.noticeList.slice(0, 5)"
                  :key="notice.id"
                  class="notice-item"
                  :class="{ unread: !notice.isRead }"
                  @click="handleMarkRead(notice)"
                >
                  <div class="notice-content">{{ notice.content }}</div>
                  <div class="notice-time">{{ notice.createTime }}</div>
                </div>
                <div v-if="noticeStore.noticeList.length === 0" class="notice-empty">暂无通知</div>
              </div>
              <div class="notice-footer">
                <span class="notice-all" @click="goToNoticeList">查看全部</span>
              </div>
            </div>
          </el-popover>
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" class="user-avatar">
                {{ userStore.userInfo.name?.charAt(0) || '用' }}
              </el-avatar>
              <span class="user-name">{{ userStore.userInfo.name || '用户' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <!-- keep-alive 缓存 AI 工具等重状态页面：避免切换标签后回来丢失已生成结果，
                 用户无需重新点击"生成"等待 LLM 调用（病历摘要/用药分析单次 LLM 耗时 10-15s）。
                 include 按路由 name 精确缓存，避免缓存列表页导致数据不刷新。 -->
            <keep-alive :include="cachedRouteNames">
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { useNoticeStore } from '@/store/modules/notice'
import { ROLE_ENUM } from '@/constants'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const noticeStore = useNoticeStore()

const isCollapse = ref(false)
const noticePopoverVisible = ref(false)

// 需要 keep-alive 缓存的路由 name 列表：
// 仅缓存"重状态"页面（AI 工具等生成结果耗时 10-15s，切走再切回不应丢失）。
// 列表/详情页不缓存——它们需要每次进入刷新最新数据。
const cachedRouteNames = ref([
  'RecordSummary',      // 病历摘要：LLM 生成结果耗时，切走再切回保留结果
  'MedicationAnalysis', // 用药分析：LLM 生成结果耗时，切走再切回保留结果
  'AiConsult',          // AI 问诊：保留多轮对话上下文
  'Triage'              // 智能分诊：保留分诊结果
])

// 当前激活菜单
const activeMenu = computed(() => route.path)

// 面包屑
const breadcrumbList = computed(() => {
  return route.matched
    .filter(item => item.meta && item.meta.title)
    .map(item => ({
      path: item.path,
      title: item.meta.title
    }))
})

// 全量菜单配置 - 图标使用官方标准字符串名称
const allMenuList = {
  [ROLE_ENUM.PATIENT.value]: [
    { path: '/patient/home', title: '首页', icon: 'House' },
    { path: '/patient/appointment', title: '预约挂号', icon: 'Calendar' },
    { path: '/patient/records', title: '我的病历', icon: 'Document' },
    {
      path: '/ai-service',
      title: 'AI服务',
      icon: 'Cpu',
      children: [
        { path: '/patient/triage', title: '智能分诊' },
        { path: '/patient/ai-consult', title: 'AI问诊' },
        { path: '/patient/ai/imaging', title: '影像检测' }
      ]
    },
    { path: '/patient/profile', title: '个人中心', icon: 'User' }
  ],
  [ROLE_ENUM.DOCTOR.value]: [
    { path: '/doctor/workbench', title: '工作台', icon: 'House' },
    { path: '/doctor/reception', title: '接诊管理', icon: 'Calendar' },
    { path: '/doctor/records', title: '病历管理', icon: 'Document' },
    { path: '/doctor/schedule', title: '我的排班', icon: 'Calendar' },
    {
      path: '/ai-tool',
      title: 'AI工具',
      icon: 'Cpu',
      children: [
        { path: '/doctor/record-summary', title: '病历摘要' },
        { path: '/doctor/medication-analysis', title: '用药分析' },
        { path: '/doctor/ai/imaging', title: '影像辅助' }
      ]
    }
  ],
  [ROLE_ENUM.HOSPITAL_ADMIN.value]: [
    {
      path: '/admin/user',
      title: '系统管理',
      icon: 'Setting',
      children: [
        { path: '/admin/user', title: '医院管理员管理' },
        { path: '/admin/pharmacy-admin', title: '药房管理员管理' },
        { path: '/admin/patient', title: '患者管理' },
        { path: '/admin/doctor', title: '医生管理' },
        { path: '/admin/department', title: '科室管理' }
      ]
    },
    {
      path: '/admin/schedule',
      title: '排班管理',
      icon: 'Calendar'
    },
    {
      path: '/admin/drug',
      title: '药品库存',
      icon: 'FirstAidKit',
      children: [
        { path: '/admin/drug', title: '药品管理' },
        { path: '/admin/stock', title: '库存管理' },
        { path: '/admin/stock-warning', title: '库存预警' }
      ]
    },
    {
      path: '/admin/ai-call-log',
      title: 'AI管理',
      icon: 'Cpu',
      children: [
        { path: '/admin/ai-call-log', title: '调用日志' },
        { path: '/admin/ai-feedback', title: '反馈管理' }
      ]
    },
    {
      path: '/admin/audit-log',
      title: '审计日志',
      icon: 'List'
    }
  ],
  [ROLE_ENUM.PHARMACY_ADMIN.value]: [
    { path: '/pharmacy/workbench', title: '工作台', icon: 'House' },
    { path: '/pharmacy/prescription-review', title: '处方审核', icon: 'Document' },
    {
      path: '/pharmacy/drug',
      title: '药品库存',
      icon: 'FirstAidKit',
      children: [
        { path: '/pharmacy/drug', title: '药品目录' },
        { path: '/pharmacy/stock', title: '库存管理' },
        { path: '/pharmacy/stock-warning', title: '库存预警' },
        { path: '/pharmacy/stock-flow', title: '库存流水' }
      ]
    }
  ]
}

// 根据角色过滤菜单
const menuList = computed(() => {
  const role = userStore.role
  return allMenuList[role] || []
})

const handleNoticeClick = async () => {
  if (noticePopoverVisible.value) return
  await noticeStore.fetchNotices({ pageNum: 1, pageSize: 5 })
}

const handleMarkRead = async (notice) => {
  if (!notice.isRead) {
    await noticeStore.markRead(notice.id)
  }
}

const goToNoticeList = () => {
  noticePopoverVisible.value = false
  router.push('/notice')
}

onMounted(() => {
  // 通知功能仅管理员可用（后端 /notifications 对非管理员返回 403），其他角色跳过加载避免报错
  if (userStore.role === ROLE_ENUM.HOSPITAL_ADMIN.value) {
    noticeStore.fetchUnreadCount()
  }
})

const handleCommand = (command) => {
  if (command === 'profile') {
    const role = userStore.role
    if (role === ROLE_ENUM.PATIENT.value) {
      router.push('/patient/profile')
    } else if (role === ROLE_ENUM.DOCTOR.value) {
      router.push('/doctor/profile')
    } else if (role === ROLE_ENUM.HOSPITAL_ADMIN.value || role === ROLE_ENUM.PHARMACY_ADMIN.value) {
      router.push('/admin/profile')
    } else {
      ElMessage.info('个人中心功能开发中')
    }
  } else if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      await userStore.logout()
      router.replace('/login')
      ElMessage.success('已退出登录')
    }).catch(() => {})
  }
}
</script>

<style scoped>
.layout-container {
  width: 100%;
  height: 100vh;
}

/* 侧边栏 */
.layout-aside {
  background-color: #001529;
  transition: width 0.2s;
  overflow: hidden;
}

.logo-box {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  border-bottom: 1px solid #ffffff1a;
}

.side-menu {
  border-right: none;
  height: calc(100vh - 60px);
}

:deep(.el-menu) {
  border-right: none;
}

/* 顶部栏 */
.layout-header {
  background: #fff;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 60px !important;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--text-regular);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.notice-bell {
  cursor: pointer;
  color: var(--text-regular);
  display: flex;
  align-items: center;
  padding: 8px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.notice-bell:hover {
  background-color: var(--bg-page);
}

.notice-panel {
  margin: -12px;
}

.notice-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
}

.notice-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.notice-list {
  max-height: 300px;
  overflow-y: auto;
}

.notice-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--border-lighter);
  transition: background-color 0.2s;
}

.notice-item:hover {
  background-color: var(--bg-page);
}

.notice-item.unread .notice-content {
  font-weight: 500;
}

.notice-content {
  font-size: 13px;
  color: var(--text-primary);
  margin-bottom: 4px;
  line-height: 1.5;
}

.notice-time {
  font-size: 12px;
  color: var(--text-secondary);
}

.notice-empty {
  padding: 40px 16px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
}

.notice-footer {
  padding: 10px 16px;
  text-align: center;
  border-top: 1px solid var(--border-light);
}

.notice-all {
  font-size: 13px;
  color: var(--color-primary);
  cursor: pointer;
}

.notice-all:hover {
  text-decoration: underline;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.user-name {
  font-size: 14px;
  color: var(--text-regular);
}

/* 主内容区 */
.layout-main {
  background: var(--bg-page);
  padding: 20px;
  overflow-y: auto;
}

/* 页面过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
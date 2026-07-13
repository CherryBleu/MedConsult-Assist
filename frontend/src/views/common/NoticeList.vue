<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">消息中心</h2>
        <el-button type="primary" plain :disabled="unreadCount === 0" @click="handleMarkAllRead">
          全部已读
        </el-button>
      </div>

      <el-tabs v-model="activeTab" class="notice-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="ALL">
          <template #label>
            <span>全部</span>
          </template>
        </el-tab-pane>
        <el-tab-pane name="UNREAD">
          <template #label>
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="tab-badge">
              未读
            </el-badge>
          </template>
        </el-tab-pane>
        <el-tab-pane label="系统通知" name="SYSTEM" />
        <el-tab-pane label="预约提醒" name="APPOINTMENT" />
        <el-tab-pane label="支付通知" name="PAYMENT" />
        <el-tab-pane label="AI服务" name="AI" />
      </el-tabs>

      <div v-loading="loading" class="notice-list">
        <div v-for="item in noticeList" :key="item.id" class="notice-item" :class="{ unread: !item.isRead }" @click="handleItemClick(item)">
          <div class="item-icon">
            <el-icon :size="24" :color="getTypeColor(item.type)">
              <component :is="getTypeIcon(item.type)" />
            </el-icon>
          </div>
          <div class="item-content">
            <div class="item-header">
              <div class="item-title">
                <span class="title-text">{{ item.title }}</span>
                <el-tag v-if="!item.isRead" size="small" type="danger" effect="light" class="unread-dot">未读</el-tag>
              </div>
              <span class="item-time">{{ item.createdAt }}</span>
            </div>
            <div class="item-body">
              <p class="content-text">{{ item.content }}</p>
            </div>
            <div class="item-footer">
              <el-tag :type="getTypeTagType(item.type)" size="small" effect="plain">
                {{ getTypeName(item.type) }}
              </el-tag>
              <div class="item-actions" @click.stop>
                <el-button v-if="!item.isRead" size="small" link type="primary" @click="handleMarkRead(item)">
                  标记已读
                </el-button>
                <el-button size="small" link type="danger" @click="handleDelete(item)">
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <el-empty v-if="!loading && noticeList.length === 0" description="暂无通知消息" />
      </div>

      <div class="pagination-wrapper" v-if="total > 0">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="通知详情" width="500px">
      <div class="notice-detail" v-if="currentNotice">
        <div class="detail-header">
          <el-icon :size="28" :color="getTypeColor(currentNotice.type)">
            <component :is="getTypeIcon(currentNotice.type)" />
          </el-icon>
          <div class="detail-title-wrap">
            <h3 class="detail-title">{{ currentNotice.title }}</h3>
            <div class="detail-meta">
              <el-tag :type="getTypeTagType(currentNotice.type)" size="small" effect="plain">
                {{ getTypeName(currentNotice.type) }}
              </el-tag>
              <span class="detail-time">{{ currentNotice.createdAt }}</span>
            </div>
          </div>
        </div>
        <div class="detail-content">
          <p>{{ currentNotice.content }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, Calendar, Wallet, Setting, Cpu } from '@element-plus/icons-vue'
import { useNoticeStore } from '@/store/modules/notice'

const noticeStore = useNoticeStore()

const activeTab = ref('ALL')
const loading = ref(false)
const noticeList = ref([])
const total = ref(0)
const unreadCount = computed(() => noticeStore.unreadCount)
const detailVisible = ref(false)
const currentNotice = ref(null)

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const typeConfig = {
  APPOINTMENT: { name: '预约提醒', icon: Calendar, color: '#1677ff', tagType: 'primary' },
  PAYMENT: { name: '支付通知', icon: Wallet, color: '#52c41a', tagType: 'success' },
  SYSTEM: { name: '系统通知', icon: Setting, color: '#909399', tagType: 'info' },
  AI: { name: 'AI服务', icon: Cpu, color: '#722ed1', tagType: 'warning' }
}

const getTypeName = (type) => typeConfig[type]?.name || '通知'
const getTypeIcon = (type) => typeConfig[type]?.icon || Bell
const getTypeColor = (type) => typeConfig[type]?.color || '#1677ff'
const getTypeTagType = (type) => typeConfig[type]?.tagType || ''

const fetchList = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      pageSize: pagination.pageSize
    }
    if (activeTab.value === 'UNREAD') {
      params.isRead = false
    } else if (activeTab.value !== 'ALL') {
      params.type = activeTab.value
    }
    const data = await noticeStore.fetchNotices(params)
    noticeList.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const fetchUnreadCount = async () => {
  await noticeStore.fetchUnreadCount()
}

const handleTabChange = () => {
  pagination.page = 1
  fetchList()
}

const handleMarkRead = async (item) => {
  await noticeStore.markRead(item.id)
  ElMessage.success('已标记为已读')
  if (activeTab.value === 'UNREAD') {
    fetchList()
  } else {
    item.isRead = true
  }
}

const handleMarkAllRead = async () => {
  await noticeStore.markAllRead()
  ElMessage.success('已全部标记为已读')
  fetchList()
}

const handleDelete = (item) => {
  ElMessageBox.confirm('确定要删除这条通知吗？', '提示', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await noticeStore.deleteNotice(item.id)
    ElMessage.success('删除成功')
    fetchList()
    fetchUnreadCount()
  }).catch(() => {})
}

const handleItemClick = (item) => {
  currentNotice.value = item
  detailVisible.value = true
  if (!item.isRead) {
    noticeStore.markRead(item.id)
    item.isRead = true
  }
}

onMounted(() => {
  fetchList()
  fetchUnreadCount()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.notice-tabs {
  margin-bottom: 16px;
}
.tab-badge :deep(.el-badge__content) {
  top: 8px;
  right: -16px;
}

.notice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 200px;
}

.notice-item {
  display: flex;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all 0.2s;
  background: var(--bg-card);
}
.notice-item:hover {
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.1);
}
.notice-item.unread {
  background: #f0f7ff;
  border-color: #bae0ff;
}
.notice-item.unread:hover {
  border-color: var(--primary-color);
}

.item-icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--bg-page);
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}
.item-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.title-text {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.unread-dot {
  transform: scale(0.8);
}
.item-time {
  font-size: 12px;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.item-body {
  margin-bottom: 12px;
}
.content-text {
  margin: 0;
  font-size: 14px;
  color: var(--text-regular);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.item-actions {
  display: flex;
  gap: 4px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

.notice-detail .detail-header {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
}
.detail-title-wrap {
  flex: 1;
}
.detail-title {
  margin: 0 0 8px 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}
.detail-time {
  font-size: 13px;
  color: var(--text-secondary);
}
.detail-content {
  font-size: 14px;
  color: var(--text-regular);
  line-height: 1.8;
}
.detail-content p {
  margin: 0;
}
</style>

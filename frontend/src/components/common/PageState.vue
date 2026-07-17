<template>
  <div class="page-state">
    <div v-if="loading" class="page-state__panel" role="status" aria-live="polite">
      <el-icon class="page-state__icon is-loading"><Loading /></el-icon>
      <p class="page-state__title">{{ loadingText }}</p>
    </div>

    <div v-else-if="error" class="page-state__panel page-state__panel--error" role="alert">
      <el-icon class="page-state__icon"><Warning /></el-icon>
      <div class="page-state__copy">
        <p class="page-state__title">{{ errorTitle }}</p>
        <p class="page-state__desc">{{ error }}</p>
      </div>
      <el-button type="primary" plain class="page-state__action" @click="$emit('retry')">
        {{ retryText }}
      </el-button>
    </div>

    <div v-else-if="empty" class="page-state__panel" role="status">
      <el-icon class="page-state__icon"><InfoFilled /></el-icon>
      <p class="page-state__title">{{ emptyText }}</p>
    </div>

    <slot v-else />
  </div>
</template>

<script setup>
import { InfoFilled, Loading, Warning } from '@element-plus/icons-vue'

defineEmits(['retry'])

defineProps({
  loading: {
    type: Boolean,
    default: false
  },
  error: {
    type: String,
    default: ''
  },
  empty: {
    type: Boolean,
    default: false
  },
  loadingText: {
    type: String,
    default: '加载中...'
  },
  errorTitle: {
    type: String,
    default: '加载失败'
  },
  emptyText: {
    type: String,
    default: '暂无数据'
  },
  retryText: {
    type: String,
    default: '重试'
  }
})
</script>

<style scoped>
.page-state {
  min-width: 0;
}

.page-state__panel {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 160px;
  padding: 24px;
  border: 1px dashed var(--border-light);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, .72);
  color: var(--text-secondary);
}

.page-state__panel--error {
  justify-content: flex-start;
  border-style: solid;
  border-color: rgba(220, 38, 38, .24);
  background: linear-gradient(135deg, rgba(254, 242, 242, .96), rgba(255, 255, 255, .9));
  color: var(--danger-color);
}

.page-state__icon {
  flex: 0 0 auto;
  font-size: 22px;
}

.page-state__copy {
  min-width: 0;
  flex: 1;
}

.page-state__title,
.page-state__desc {
  margin: 0;
}

.page-state__title {
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.page-state__desc {
  margin-top: 4px;
  font-size: var(--font-sm);
  line-height: 1.6;
  color: var(--text-secondary);
}

.page-state__action {
  min-width: var(--touch-target);
  min-height: var(--touch-target);
}

@media (max-width: 640px) {
  .page-state__panel,
  .page-state__panel--error {
    align-items: stretch;
    flex-direction: column;
  }

  .page-state__action {
    width: 100%;
  }
}
</style>

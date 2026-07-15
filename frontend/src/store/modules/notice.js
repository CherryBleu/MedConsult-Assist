import { defineStore } from 'pinia'
import {
  getNoticeListApi, getUnreadCountApi, markReadApi, markAllReadApi
} from '@/api/notice'

export const useNoticeStore = defineStore('notice', {
  state: () => ({
    noticeList: [],
    unreadCount: 0,
    loaded: false
  }),

  actions: {
    async fetchNotices(params) {
      const res = await getNoticeListApi(params)
      // 后端→前端字段映射：notificationId→id, read→isRead（content/createdAt 后端已返回，透传）
      // mock 仍用 id/isRead，?? 兜底保证两种数据源都兼容
      const records = (res.data.records || []).map(n => ({
        ...n,
        id: n.id ?? n.notificationId,
        isRead: n.isRead ?? n.read ?? false
      }))
      this.noticeList = records
      this.loaded = true
      // 返回映射后的数据，供 view 直接使用
      return { ...res.data, records }
    },

    async fetchUnreadCount() {
      const res = await getUnreadCountApi()
      // 确保 unreadCount 始终为数字（防止后端返回对象导致 [object Object] 显示）
      this.unreadCount = typeof res.data === 'number' ? res.data : 0
      return this.unreadCount
    },

    async markRead(id) {
      const res = await markReadApi(id)
      const notice = this.noticeList.find(n => n.id === Number(id))
      if (notice && !notice.isRead) {
        notice.isRead = true
        this.unreadCount = Math.max(0, this.unreadCount - 1)
      }
      return res
    },

    async markAllRead() {
      // 后端无批量已读端点：对当前列表里的未读项逐条 PATCH 兜底
      const unreadIds = this.noticeList.filter(n => !n.isRead).map(n => n.id)
      const res = await markAllReadApi(unreadIds)
      this.noticeList.forEach(n => { n.isRead = true })
      this.unreadCount = 0
      return res
    }
  }
})

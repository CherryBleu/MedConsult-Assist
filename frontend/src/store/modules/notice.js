import { defineStore } from 'pinia'
import {
  getNoticeListApi, getUnreadCountApi, markReadApi, markAllReadApi
} from '@/api/notice'

const normalizeNotice = (notice) => ({
  ...notice,
  id: notice.id ?? notice.notificationId,
  isRead: notice.isRead ?? notice.read ?? false
})

const sameNoticeId = (left, right) => String(left) === String(right)

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
      const records = (res.data.records || []).map(normalizeNotice)
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
      const notice = this.noticeList.find(n => sameNoticeId(n.id, id))
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
    },

    upsertRealtimeNotice(rawNotice) {
      if (!rawNotice) return
      const notice = normalizeNotice(rawNotice)
      if (!notice.id) return

      const index = this.noticeList.findIndex(item => sameNoticeId(item.id, notice.id))
      if (index === -1) {
        this.noticeList.unshift(notice)
        if (!notice.isRead) {
          this.unreadCount += 1
        }
      } else {
        const previous = this.noticeList[index]
        const wasRead = previous.isRead
        const next = { ...previous, ...notice }
        this.noticeList.splice(index, 1, next)
        if (wasRead && !next.isRead) {
          this.unreadCount += 1
        } else if (!wasRead && next.isRead) {
          this.unreadCount = Math.max(0, this.unreadCount - 1)
        }
      }
      this.loaded = true
    },

    applyRealtimeRead(id) {
      if (!id) return
      const notice = this.noticeList.find(item => sameNoticeId(item.id, id))
      if (notice && !notice.isRead) {
        notice.isRead = true
        this.unreadCount = Math.max(0, this.unreadCount - 1)
      }
    }
  }
})

import { defineStore } from 'pinia'
import {
  getNoticeListApi, getUnreadCountApi, markReadApi, markAllReadApi, deleteNoticeApi
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
      this.noticeList = res.data.records
      this.loaded = true
      return res.data
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
    },

    async deleteNotice(id) {
      const res = await deleteNoticeApi(id)
      const index = this.noticeList.findIndex(n => n.id === Number(id))
      if (index > -1) {
        const notice = this.noticeList[index]
        if (!notice.isRead) {
          this.unreadCount = Math.max(0, this.unreadCount - 1)
        }
        this.noticeList.splice(index, 1)
      }
      return res
    }
  }
})

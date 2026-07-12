import { defineStore } from 'pinia'
import { triageApi } from '@/api/ai'

/**
 * 智能分诊状态。
 *
 * <p>目的：把 Triage.vue 的输入（symptoms/duration）与请求态（submitting）、
 * 结果（result）从组件内 ref 上提到 Pinia store。组件卸载（路由切走）后：
 *   - 已填写的症状不会被清空；
 *   - 正在进行的分诊请求由 store action 持有 Promise，完成后把 result 写回 store；
 *   - 切回来时若 submitting=true 仍显示"加载中"，若 result 有值仍能看到上次分诊结果。
 *
 * <p>风格对齐 aiChat.js / user.js：Options API（state + actions）。
 */
export const useTriageStore = defineStore('triage', {
  state: () => ({
    // 症状文本（用户在文本框输入）
    symptoms: '',
    // 持续时间单选项
    duration: '1-3天',
    // 是否正在请求分诊接口
    submitting: false,
    // 分诊结果（后端 TriageResponse），null 表示尚未分诊
    result: null
  }),

  actions: {
    // 设置症状文本
    setSymptoms(val) {
      this.symptoms = val
    },

    // 设置持续时间
    setDuration(val) {
      this.duration = val
    },

    // 执行分诊：请求逻辑搬进 store action，Promise 由 store 持有。
    // 即使组件卸载，请求也会继续，完成后 result 写回 store；组件重新挂载时
    // 通过 storeToRefs 读取 submitting/result 即可恢复展示。
    // 返回 true/false 表示是否成功（供组件决定是否切到结果视图）。
    async triage() {
      const text = String(this.symptoms || '').trim()
      if (!text) return false
      if (this.submitting) return false

      this.submitting = true
      try {
        // 后端 TriageRequest.symptoms 是 List<String>（@NotEmpty），前端按逗号/顿号拆成数组
        const symptomList = text.split(/[,，、\n]/).map(s => s.trim()).filter(Boolean)
        const res = await triageApi({
          symptoms: symptomList,
          duration: this.duration
        })
        this.result = res.data
        return true
      } finally {
        this.submitting = false
      }
    },

    // 重置：清空结果与输入，回到输入视图
    reset() {
      this.result = null
      this.symptoms = ''
    }
  }
})

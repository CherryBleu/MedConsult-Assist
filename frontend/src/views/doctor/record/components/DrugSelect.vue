<template>
  <!-- 药品远程搜索选择器（开方时选药）。
       基于 el-select filterable remote + getDrugListApi(keyword)。
       选中后 emit('select', 药品对象) 回填明细行的 drugNo/drugName/specification/unit。
       库存为 0 的药品置灰但仍可选（开方不强校验库存，发药时 FEFO 才校验）。 -->
  <el-select
    v-model="selectedLabel"
    filterable
    remote
    clearable
    reserve-keyword
    :remote-method="search"
    :loading="loading"
    placeholder="输入药品名搜索"
    style="width: 100%"
    @change="onChange"
    @clear="onClear"
  >
    <el-option
      v-for="d in options"
      :key="d.drugNo"
      :label="buildLabel(d)"
      :value="d.drugNo"
    >
      <span style="float: left">{{ d.name }}</span>
      <span style="float: right; color: var(--el-text-color-secondary); font-size: 12px">
        {{ d.specification }} · 库存 {{ d.stockQuantity ?? d.stock ?? 0 }}
      </span>
    </el-option>
  </el-select>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getDrugListApi } from '@/api/drug'

const props = defineProps({
  // 初始药品名（编辑回填场景用；本批开方页未做回显，预留）
  initialDrugName: { type: String, default: '' }
})
const emit = defineEmits(['select', 'clear'])

const selectedLabel = ref('')
const options = ref([])
const loading = ref(false)
let debounceTimer = null

// 选项展示文本：药名（规格）
const buildLabel = (d) => {
  const spec = d.specification ? `（${d.specification}）` : ''
  return `${d.name ?? d.genericName ?? d.drugName}${spec}`
}

// 远程搜索（300ms 防抖，避免每次按键都打后端）
const search = (keyword) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(async () => {
    const kw = (keyword || '').trim()
    if (!kw) {
      options.value = []
      return
    }
    loading.value = true
    try {
      const res = await getDrugListApi({ keyword: kw, page: 1, pageSize: 20 })
      options.value = Array.isArray(res.data) ? res.data : []
    } catch (e) {
      // 错误由请求拦截器统一提示
      options.value = []
    } finally {
      loading.value = false
    }
  }, 300)
}

// 选中某药：把整条药品对象回传父组件
const onChange = (drugNo) => {
  const drug = options.value.find((d) => d.drugNo === drugNo)
  if (drug) {
    emit('select', drug)
  }
}

const onClear = () => {
  emit('clear')
}

// 父组件回填初始药名时同步显示
watch(
  () => props.initialDrugName,
  (v) => {
    if (v) selectedLabel.value = v
  },
  { immediate: true }
)
</script>

<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <div class="header-left">
          <el-icon :size="28" color="#1677ff"><PictureFilled /></el-icon>
          <div>
            <h2 class="title">医学影像异常检测</h2>
            <p class="desc">上传CT/X光/MRI等医学影像，AI辅助检测异常区域</p>
          </div>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="main-tabs">
        <el-tab-pane label="新建检测" name="new">
          <div class="upload-section">
            <el-form class="imaging-form" label-position="top">
              <el-form-item label="影像类型">
                <el-select v-model="imagingForm.imagingType" class="field-control" placeholder="请选择影像类型" aria-label="影像类型">
                  <el-option label="CT" value="CT" />
                  <el-option label="X线" value="X线" />
                  <el-option label="MRI" value="MRI" />
                  <el-option label="超声" value="超声" />
                </el-select>
              </el-form-item>
              <el-form-item label="检查部位">
                <el-select v-model="imagingForm.bodyPart" class="field-control" placeholder="请选择检查部位" aria-label="检查部位">
                  <el-option label="胸部" value="胸部" />
                  <el-option label="腹部" value="腹部" />
                  <el-option label="头颅" value="头颅" />
                  <el-option label="脊柱" value="脊柱" />
                  <el-option label="四肢" value="四肢" />
                </el-select>
              </el-form-item>
            </el-form>

            <el-upload
              class="image-uploader"
              drag
              action="#"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleFileChange"
              accept="image/*"
            >
              <div v-if="!imageUrl" class="upload-placeholder">
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="el-upload__text">将图片拖到此处，或<em>点击上传</em></div>
                <div class="upload-tip">支持 JPG、PNG、DICOM 格式，单张不超过 10MB</div>
              </div>
              <div v-else class="image-preview-wrapper">
                <img ref="previewImg" :src="imageUrl" class="preview-image" :alt="buildImagingAlt('待提交的医学影像预览')" @load="onImageLoad" />
                <canvas ref="annotationCanvas" class="annotation-canvas" aria-hidden="true"></canvas>
                <div class="image-overlay">
                  <el-button type="danger" size="small" circle class="image-remove-action" aria-label="移除影像" @click.stop="removeImage">
                    <el-icon><Close /></el-icon>
                  </el-button>
                </div>
              </div>
            </el-upload>

            <div class="submit-section">
              <el-button
                type="primary"
                size="large"
                class="patient-imaging-action"
                :loading="submitting"
                :disabled="!imageUrl || !imagingForm.imagingType || !imagingForm.bodyPart"
                @click="submitDetection"
              >
                {{ submitting ? '检测中...' : '提交检测' }}
              </el-button>
            </div>

            <div v-if="submitError" class="inline-error" role="alert">{{ submitError }}</div>

            <div v-if="detecting" class="progress-section" role="status" aria-live="polite">
              <el-progress :percentage="progress" :status="progressStatus" />
              <p class="progress-text">{{ progressText }}</p>
            </div>
          </div>

          <div v-if="detectionResult" class="result-section">
            <div class="result-header">
              <h3>检测结果</h3>
              <el-tag :type="detectionResult.hasAbnormal ? 'danger' : 'success'" size="large">
                {{ detectionResult.hasAbnormal ? '检测到异常' : '未见明显异常' }}
              </el-tag>
            </div>

            <div class="result-content">
              <div class="result-image-wrapper">
                <img :src="imageUrl" class="result-image" :alt="buildImagingAlt('影像检测结果原图与异常区域标注', detectionResult)" @load="drawAnnotations" />
                <canvas ref="resultCanvas" class="result-canvas" aria-hidden="true"></canvas>
                <div v-if="detectionResult.regions && detectionResult.regions.length" class="legend">
                  <div v-for="(region, idx) in detectionResult.regions" :key="idx" class="legend-item">
                    <span class="legend-color" :style="{ background: getRegionColor(idx) }"></span>
                    <span>{{ region.label }} ({{ region.confidence }}%)</span>
                  </div>
                </div>
              </div>

              <div class="result-info">
                <el-descriptions :column="1" border>
                  <el-descriptions-item label="影像类型">{{ detectionResult.imagingType }}</el-descriptions-item>
                  <el-descriptions-item label="检查部位">{{ detectionResult.bodyPart }}</el-descriptions-item>
                  <el-descriptions-item label="检测模型">{{ detectionResult.modelName }}</el-descriptions-item>
                  <el-descriptions-item label="置信度">
                    <el-progress :percentage="detectionResult.confidence" :stroke-width="8" />
                  </el-descriptions-item>
                  <el-descriptions-item label="检测状态">
                    <el-tag v-if="detectionResult.reviewStatus === 'REVIEWED'" type="success" size="small">已审核</el-tag>
                    <el-tag v-else type="warning" size="small">待医生审核</el-tag>
                  </el-descriptions-item>
                </el-descriptions>

                <div v-if="detectionResult.regions && detectionResult.regions.length" class="region-text-list" aria-label="异常区域文字说明">
                  <h4>异常区域文字说明</h4>
                  <ul>
                    <li v-for="(region, idx) in detectionResult.regions" :key="idx">
                      {{ getRegionDescription(region) }}
                    </li>
                  </ul>
                </div>

                <div class="findings-box">
                  <h4>AI影像所见</h4>
                  <p>{{ detectionResult.aiFindings }}</p>
                </div>

                <div class="diagnosis-box">
                  <h4>AI诊断建议</h4>
                  <p class="diagnosis-text">{{ detectionResult.aiDiagnosis }}</p>
                </div>

                <div v-if="detectionResult.suggestions && detectionResult.suggestions.length" class="suggestions-box">
                  <h4>后续建议</h4>
                  <ul>
                    <li v-for="(sug, idx) in detectionResult.suggestions" :key="idx">{{ sug }}</li>
                  </ul>
                </div>

                <div v-if="detectionResult.reviewStatus === 'REVIEWED'" class="review-box">
                  <h4>医生审核意见</h4>
                  <el-tag :type="getReviewTagType(detectionResult.reviewResult)" size="small" style="margin-bottom: 8px">
                    {{ getReviewResultText(detectionResult.reviewResult) }}
                  </el-tag>
                  <p>{{ detectionResult.reviewComment }}</p>
                  <p v-if="detectionResult.reviewedBy || detectionResult.reviewedAt" class="review-meta">
                    审核医生：{{ detectionResult.reviewedBy || '-' }} | {{ detectionResult.reviewedAt || '-' }}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="历史记录" name="history">
          <PageState
            :loading="historyLoading"
            :error="historyError"
            :empty="historyList.length === 0"
            loading-text="正在加载影像检测历史..."
            empty-text="暂无影像检测历史"
            @retry="getHistoryList"
          >
            <ResponsiveTable aria-label="影像检测历史记录">
              <template #table>
                <el-table :data="historyList" stripe style="width: 100%">
                  <el-table-column prop="taskNo" label="检测编号" width="180" />
                  <el-table-column prop="imagingType" label="影像类型" width="100" />
                  <el-table-column prop="bodyPart" label="检查部位" width="100" />
                  <el-table-column label="检测结果" width="120">
                    <template #default="{ row }">
                      <el-tag :type="row.hasAbnormal ? 'danger' : 'success'" size="small">
                        {{ row.hasAbnormal ? '异常' : '正常' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="置信度" width="120">
                    <template #default="{ row }">
                      <el-progress :percentage="row.confidence" :stroke-width="6" />
                    </template>
                  </el-table-column>
                  <el-table-column label="审核状态" width="100">
                    <template #default="{ row }">
                      <el-tag :type="row.reviewStatus === 'REVIEWED' ? 'success' : 'warning'" size="small">
                        {{ row.reviewStatus === 'REVIEWED' ? '已审核' : '待审核' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="createdAt" label="检测时间" width="180" />
                  <el-table-column label="操作" width="120">
                    <template #default="{ row }">
                      <el-button type="primary" link size="small" class="patient-imaging-action" :aria-label="`查看 ${row.taskNo} 检测详情`" @click="viewHistory(row)">查看详情</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </template>

              <template #card>
                <article
                  v-for="row in historyList"
                  :key="row.detectionId || row.taskId || row.taskNo"
                  class="history-card"
                  data-testid="responsive-patient-imaging-card"
                >
                  <div class="history-card__header">
                    <div>
                      <p class="history-card__title">{{ row.taskNo || row.taskId }}</p>
                      <p class="history-card__meta">{{ row.createdAt || '-' }}</p>
                    </div>
                    <span class="status-chip" :class="row.reviewStatus === 'REVIEWED' ? 'success' : 'warning'">
                      {{ row.reviewStatus === 'REVIEWED' ? '已审核' : '待审核' }}
                    </span>
                  </div>
                  <dl class="history-card__fields">
                    <div>
                      <dt>类型</dt>
                      <dd>{{ row.imagingType || '-' }} {{ row.bodyPart || '' }}</dd>
                    </div>
                    <div>
                      <dt>AI结果</dt>
                      <dd>
                        <span class="status-chip" :class="row.hasAbnormal ? 'danger' : 'success'">
                          {{ row.hasAbnormal ? '异常' : '正常' }}
                        </span>
                      </dd>
                    </div>
                    <div>
                      <dt>置信度</dt>
                      <dd>{{ Number(row.confidence || 0) }}%</dd>
                    </div>
                  </dl>
                  <el-button type="primary" plain class="patient-imaging-action" @click="viewHistory(row)">查看详情</el-button>
                </article>
              </template>
            </ResponsiveTable>
          </PageState>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { PictureFilled, UploadFilled, Close } from '@element-plus/icons-vue'
import { submitImagingDetectionApi, getImagingResultApi, getImagingHistoryListApi, uploadImageFileApi } from '@/api/ai'
import { useUserStore } from '@/store/modules/user'
import { getToken } from '@/utils/auth'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const userStore = useUserStore()

const activeTab = ref('new')
const imageUrl = ref('')
const imageFile = ref(null)
const submitting = ref(false)
const detecting = ref(false)
const progress = ref(0)
const progressStatus = ref('')
const progressText = ref('')
const detectionResult = ref(null)
const currentTaskId = ref('')
const pollTimer = ref(null)
const historyList = ref([])
const historyLoading = ref(false)
const historyError = ref('')
const submitError = ref('')

const previewImg = ref(null)
const annotationCanvas = ref(null)
const resultCanvas = ref(null)

const imagingForm = reactive({
  imagingType: 'CT',
  bodyPart: '胸部'
})

const regionColors = ['#ff4d4f', '#faad14', '#52c41a', '#1890ff', '#722ed1']

const getRegionColor = (idx) => regionColors[idx % regionColors.length]

const buildImagingAlt = (context, result = null) => {
  const source = result || detectionResult.value || {}
  const type = source.imagingType || imagingForm.imagingType || '医学影像'
  const part = source.bodyPart || imagingForm.bodyPart || '检查部位'
  if (source.hasAbnormal === true) return `${context}：${type}${part}，AI提示存在异常区域`
  if (source.hasAbnormal === false) return `${context}：${type}${part}，AI未提示明显异常`
  return `${context}：${type}${part}`
}

const getRegionDescription = (region) => {
  const confidence = Number(region.confidence || 0)
  const hasRegionBox = [region.x, region.y, region.width, region.height].every(value => Number.isFinite(Number(value)))
  const regionBox = hasRegionBox ? `，区域 x${region.x} y${region.y} 宽${region.width} 高${region.height}` : ''
  return `${region.label || '异常区域'}，置信度 ${confidence}%${regionBox}`
}

const handleFileChange = (file) => {
  submitError.value = ''
  const isImage = file.raw.type.startsWith('image/')
  const isLt10M = file.raw.size / 1024 / 1024 < 10

  if (!isImage) {
    submitError.value = '请上传图片文件'
    ElMessage.error('请上传图片文件')
    return
  }
  if (!isLt10M) {
    submitError.value = '图片大小不能超过 10MB'
    ElMessage.error('图片大小不能超过 10MB')
    return
  }

  imageFile.value = file.raw
  imageUrl.value = URL.createObjectURL(file.raw)
  detectionResult.value = null
}

const removeImage = () => {
  imageUrl.value = ''
  imageFile.value = null
  detectionResult.value = null
  submitError.value = ''
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
  detecting.value = false
  progress.value = 0
}

const onImageLoad = () => {
  nextTick(() => {
    drawCanvas(previewImg.value, annotationCanvas.value, [])
  })
}

const drawCanvas = (img, canvas, regions) => {
  if (!img || !canvas) return
  const rect = img.getBoundingClientRect()
  canvas.width = rect.width
  canvas.height = rect.height
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  if (!regions || !regions.length) return

  const scaleX = rect.width / img.naturalWidth
  const scaleY = rect.height / img.naturalHeight

  regions.forEach((region, idx) => {
    const x = region.x * scaleX
    const y = region.y * scaleY
    const w = region.width * scaleX
    const h = region.height * scaleY

    ctx.strokeStyle = getRegionColor(idx)
    ctx.lineWidth = 2
    ctx.strokeRect(x, y, w, h)

    ctx.fillStyle = getRegionColor(idx) + 'cc'
    const label = `${region.label} ${region.confidence}%`
    ctx.font = '12px sans-serif'
    const textWidth = ctx.measureText(label).width
    ctx.fillRect(x, y - 18, textWidth + 8, 18)
    ctx.fillStyle = '#fff'
    ctx.fillText(label, x + 4, y - 5)
  })
}

const drawAnnotations = () => {
  nextTick(() => {
    const resultImgs = document.querySelectorAll('.result-image')
    const resultCanvases = document.querySelectorAll('.result-canvas')
    if (resultImgs.length > 0 && resultCanvases.length > 0) {
      drawCanvas(resultImgs[resultImgs.length - 1], resultCanvases[resultCanvases.length - 1], detectionResult.value?.regions || [])
    }
  })
}

const submitDetection = async () => {
  submitError.value = ''
  if (!imageFile.value) {
    submitError.value = '请先上传影像图片'
    ElMessage.warning('请先上传影像图片')
    return
  }

  submitting.value = true
  detecting.value = true
  progress.value = 0
  progressStatus.value = ''
  progressText.value = '正在上传影像文件...'

  try {
    // 1. 上传图片到 MinIO，后续检测仅引用服务端返回的 fileId。
    const uploadRes = await uploadImageFileApi(imageFile.value)
    const fileId = uploadRes.data?.fileId
    if (!fileId) {
      throw new Error('文件上传失败：未返回 fileId')
    }

    // 2. 提交检测任务（后端 DTO: imageType + fileIds List）
    progressText.value = '正在提交检测任务...'
    const res = await submitImagingDetectionApi({
      imageType: imagingForm.imagingType,
      fileIds: [fileId],
      patientId: userStore.userInfo?.patientId
    })
    currentTaskId.value = res.data.detectionId || res.data.taskId
    ElMessage.success('检测任务已提交，正在处理中...')
    startPolling()
  } catch (e) {
    submitError.value = e?.response?.data?.message || e?.message || '提交失败，请重试'
    ElMessage.error(submitError.value)
    detecting.value = false
  } finally {
    submitting.value = false
  }
}

const startPolling = () => {
  progress.value = 20
  progressText.value = '任务排队中...'

  let pollCount = 0
  // 最大轮询次数：约 60 次 × 1.5s = 90s（与 request.js 超时对齐），超时自动停止避免页面假死 + 持续打接口
  const MAX_POLL_COUNT = 60
  pollTimer.value = setInterval(async () => {
    // 退出登录后 token 被清除，立即停止轮询避免 401 死循环
    if (!getToken()) {
      clearInterval(pollTimer.value)
      pollTimer.value = null
      detecting.value = false
      return
    }
    pollCount++
    // 超过最大次数仍未完成，停止轮询并提示
    if (pollCount > MAX_POLL_COUNT) {
      clearInterval(pollTimer.value)
      pollTimer.value = null
      detecting.value = false
      progressStatus.value = 'exception'
      progressText.value = '检测超时，请稍后在历史记录中查看结果'
      submitError.value = progressText.value
      ElMessage.warning('检测超时，请稍后在历史记录中查看结果')
      getHistoryList()
      return
    }
    try {
      const res = await getImagingResultApi(currentTaskId.value)
      const data = res.data
      progress.value = Math.min(20 + pollCount * 15, 90)

      if (data.status === 'PENDING') {
        progressText.value = '任务排队中，请稍候...'
      } else if (data.status === 'PROCESSING') {
        progressText.value = 'AI正在分析影像，请稍候...'
      } else if (data.status === 'COMPLETED') {
        clearInterval(pollTimer.value)
        pollTimer.value = null
        progress.value = 100
        progressStatus.value = 'success'
        progressText.value = '检测完成！'
        detectionResult.value = {
          ...data,
          imagingType: data.imagingType || imagingForm.imagingType,
          bodyPart: data.bodyPart || imagingForm.bodyPart,
          modelName: data.modelName || '当前影像模型'
        }
        detecting.value = false
        ElMessage.success('检测完成')
        nextTick(() => {
          drawAnnotations()
        })
      } else if (data.status === 'FAILED') {
        // AI 任务失败：停止轮询并提示，避免对 FAILED 任务永久轮询
        clearInterval(pollTimer.value)
        pollTimer.value = null
        detecting.value = false
        progressStatus.value = 'exception'
        progressText.value = data.failReason || data.errorMessage || '检测失败，请重试'
        submitError.value = progressText.value
        ElMessage.error(submitError.value)
      }
    } catch (e) {
      clearInterval(pollTimer.value)
      pollTimer.value = null
      detecting.value = false
      submitError.value = e?.response?.data?.message || e?.message || '获取检测结果失败'
      ElMessage.error(submitError.value)
    }
  }, 1500)
}

const getReviewResultText = (result) => {
  const map = {
    CONFIRM: '确认结果', CONFIRMED: '确认结果',
    CORRECT: '已修正', CORRECTED: '已修正',
    REJECT: '驳回', REJECTED: '驳回'
  }
  return map[result] || '待审核'
}

const getReviewTagType = (result) => {
  const map = {
    CONFIRM: 'success', CONFIRMED: 'success',
    CORRECT: 'warning', CORRECTED: 'warning',
    REJECT: 'danger', REJECTED: 'danger'
  }
  return map[result] || 'info'
}

const getHistoryList = async () => {
  historyLoading.value = true
  historyError.value = ''
  try {
    // 后端按 patientId 过滤；患者端传当前用户 patientId
    const patientId = userStore.userInfo?.patientId
    const res = await getImagingHistoryListApi(patientId || null)
    historyList.value = Array.isArray(res.data) ? res.data : (res.data?.items ?? res.data?.records ?? [])
  } catch (e) {
    historyList.value = []
    historyError.value = e?.response?.data?.message || e?.message || '影像检测历史加载失败'
  } finally {
    historyLoading.value = false
  }
}

const viewHistory = async (row) => {
  activeTab.value = 'new'
  currentTaskId.value = row.detectionId || row.taskId
  imagingForm.imagingType = row.imagingType
  imagingForm.bodyPart = row.bodyPart
  progress.value = 100
  progressStatus.value = 'success'
  detecting.value = false
  submitting.value = false

  try {
    const res = await getImagingResultApi(row.detectionId || row.taskId)
    detectionResult.value = {
      ...res.data,
      imagingType: res.data?.imagingType || row.imagingType || imagingForm.imagingType,
      bodyPart: res.data?.bodyPart || row.bodyPart || imagingForm.bodyPart,
      modelName: res.data?.modelName || row.modelName || '当前影像模型'
    }
    // 还原历史影像原图：用上传时记录的 URL，不替换为第三方域名
    imageUrl.value = row.imageUrl || row.originalImageUrl || ''
    nextTick(() => {
      setTimeout(() => drawAnnotations(), 300)
    })
  } catch (e) {
    submitError.value = e?.response?.data?.message || e?.message || '获取详情失败，请重试'
    ElMessage.error(submitError.value)
  }
}

onMounted(() => {
  getHistoryList()
})

// 组件卸载清理轮询定时器，避免离开页面后持续发请求（内存/请求泄漏）
onUnmounted(() => {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 20px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-light);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}
.desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
}

.main-tabs {
  margin-top: 0;
}

.upload-section {
  max-width: 760px;
}

.imaging-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 16px;
  margin-bottom: 18px;
  border: 1px solid rgba(2, 132, 199, .1);
  border-radius: var(--radius-base);
  background: var(--bg-page);
}

.imaging-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.imaging-form :deep(.el-form-item__label) {
  color: var(--text-primary);
  font-weight: 600;
  line-height: 1.4;
}

.field-control {
  width: 100%;
}

.patient-imaging-action,
.image-remove-action {
  min-width: var(--touch-target);
  min-height: var(--touch-target);
  touch-action: manipulation;
}

.image-uploader {
  width: 100%;
}
.image-uploader :deep(.el-upload-dragger) {
  width: 100%;
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.upload-placeholder {
  text-align: center;
}
.upload-icon {
  font-size: 56px;
  color: #c0c4cc;
  margin-bottom: 16px;
}
.upload-tip {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 8px;
}
.image-preview-wrapper {
  position: relative;
  width: 100%;
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
}
.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
.annotation-canvas {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}
.image-overlay {
  position: absolute;
  top: 12px;
  right: 12px;
}

.submit-section {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
.submit-section .patient-imaging-action {
  width: 200px;
}

.inline-error {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid rgba(185, 28, 28, .22);
  border-radius: var(--radius-sm);
  background: #fef2f2;
  color: var(--el-color-danger);
  font-size: var(--font-sm);
  line-height: 1.6;
}

.progress-section {
  margin-top: 24px;
  padding: 20px;
  background: var(--bg-page);
  border-radius: var(--radius-base);
}
.progress-text {
  text-align: center;
  margin-top: 12px;
  font-size: 14px;
  color: var(--text-secondary);
}

.result-section {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border-light);
}
.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.result-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}
.result-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.result-image-wrapper {
  position: relative;
  background: #000;
  border-radius: var(--radius-base);
  overflow: hidden;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.result-image {
  max-width: 100%;
  max-height: 400px;
  object-fit: contain;
}
.result-canvas {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}
.legend {
  position: absolute;
  bottom: 12px;
  left: 12px;
  background: rgba(0,0,0,0.7);
  padding: 8px 12px;
  border-radius: 4px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #fff;
  margin: 4px 0;
}
.legend-color {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.result-info {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.findings-box,
.diagnosis-box,
.suggestions-box,
.region-text-list,
.review-box {
  padding: 16px;
  background: var(--bg-page);
  border-radius: var(--radius-base);
}
.findings-box h4,
.diagnosis-box h4,
.suggestions-box h4,
.region-text-list h4,
.review-box h4 {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 8px;
  color: var(--text-primary);
}
.findings-box p,
.diagnosis-box p,
.review-box p {
  font-size: 13px;
  color: var(--text-regular);
  line-height: 1.8;
  margin: 0;
}
.diagnosis-text {
  color: var(--primary-color) !important;
  font-weight: 500;
}
.suggestions-box ul {
  margin: 0;
  padding-left: 20px;
}
.suggestions-box li,
.region-text-list li {
  font-size: 13px;
  color: var(--text-regular);
  line-height: 2;
}
.region-text-list ul {
  margin: 0;
  padding-left: 20px;
}
.review-box {
  background: #f6ffed;
}
.review-meta {
  font-size: 12px !important;
  color: var(--text-secondary) !important;
  margin-top: 8px !important;
}

.history-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  background: var(--bg-card);
}

.history-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.history-card__header > div {
  min-width: 0;
}

.history-card__title,
.history-card__meta {
  margin: 0;
}

.history-card__title {
  overflow-wrap: anywhere;
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.history-card__meta {
  margin-top: 4px;
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.history-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}

.history-card__fields div {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}

.history-card__fields dt,
.history-card__fields dd {
  margin: 0;
}

.history-card__fields dt {
  color: var(--text-secondary);
}

.history-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  text-align: right;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
  white-space: nowrap;
}

.status-chip.success {
  color: #15803d;
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.status-chip.warning {
  color: #92400e;
  background: #fffbeb;
  border-color: #fde68a;
}

.status-chip.danger {
  color: #b91c1c;
  background: #fef2f2;
  border-color: #fecaca;
}

.history-card__header .status-chip,
.history-card__fields .status-chip {
  flex: 0 0 auto;
}

@media (max-width: 768px) {
  .page-header,
  .header-left,
  .result-header,
  .submit-section {
    align-items: stretch;
    flex-direction: column;
  }

  .header-left {
    gap: 10px;
  }

  .title {
    font-size: 18px;
  }

  .imaging-form,
  .result-content {
    grid-template-columns: 1fr;
  }

  .image-uploader :deep(.el-upload-dragger),
  .image-preview-wrapper {
    min-height: 240px;
    height: auto;
  }

  .result-image-wrapper {
    min-height: 240px;
  }

  .legend {
    right: 12px;
    max-width: calc(100% - 24px);
  }

  .submit-section .patient-imaging-action,
  .history-card .patient-imaging-action {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .image-uploader :deep(.el-upload-dragger),
  .image-preview-wrapper,
  .result-image-wrapper {
    min-height: 210px;
  }

  .imaging-form,
  .findings-box,
  .diagnosis-box,
  .suggestions-box,
  .region-text-list,
  .review-box,
  .history-card {
    padding: 14px;
  }
}
</style>

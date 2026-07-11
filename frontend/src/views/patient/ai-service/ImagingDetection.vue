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
            <el-form label-width="100px">
              <el-form-item label="影像类型">
                <el-select v-model="imagingForm.imagingType" placeholder="请选择影像类型" style="width: 200px">
                  <el-option label="CT" value="CT" />
                  <el-option label="X线" value="X线" />
                  <el-option label="MRI" value="MRI" />
                  <el-option label="超声" value="超声" />
                </el-select>
              </el-form-item>
              <el-form-item label="检查部位">
                <el-select v-model="imagingForm.bodyPart" placeholder="请选择检查部位" style="width: 200px">
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
                <img ref="previewImg" :src="imageUrl" class="preview-image" @load="onImageLoad" />
                <canvas ref="annotationCanvas" class="annotation-canvas"></canvas>
                <div class="image-overlay">
                  <el-button type="danger" size="small" circle @click.stop="removeImage">
                    <el-icon><Close /></el-icon>
                  </el-button>
                </div>
              </div>
            </el-upload>

            <div class="submit-section">
              <el-button
                type="primary"
                size="large"
                :loading="submitting"
                :disabled="!imageUrl || !imagingForm.imagingType || !imagingForm.bodyPart"
                @click="submitDetection"
              >
                {{ submitting ? '检测中...' : '提交检测' }}
              </el-button>
            </div>

            <div v-if="detecting" class="progress-section">
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
                <img :src="imageUrl" class="result-image" @load="drawAnnotations" />
                <canvas ref="resultCanvas" class="result-canvas"></canvas>
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
                  <p>{{ detectionResult.doctorOpinion }}</p>
                  <p class="review-meta">审核医生：{{ detectionResult.reviewedBy }} | {{ detectionResult.reviewedAt }}</p>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="历史记录" name="history">
          <el-table :data="historyList" v-loading="historyLoading" stripe style="width: 100%">
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
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="viewHistory(row)">查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { PictureFilled, UploadFilled, Close } from '@element-plus/icons-vue'
import { submitImagingDetectionApi, getImagingResultApi, getImagingHistoryListApi } from '@/api/ai'

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

const previewImg = ref(null)
const annotationCanvas = ref(null)
const resultCanvas = ref(null)

const imagingForm = reactive({
  imagingType: 'CT',
  bodyPart: '胸部'
})

const regionColors = ['#ff4d4f', '#faad14', '#52c41a', '#1890ff', '#722ed1']

const getRegionColor = (idx) => regionColors[idx % regionColors.length]

const handleFileChange = (file) => {
  const isImage = file.raw.type.startsWith('image/')
  const isLt10M = file.raw.size / 1024 / 1024 < 10

  if (!isImage) {
    ElMessage.error('请上传图片文件')
    return
  }
  if (!isLt10M) {
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
  if (!imageFile.value) {
    ElMessage.warning('请先上传影像图片')
    return
  }

  submitting.value = true
  detecting.value = true
  progress.value = 0
  progressStatus.value = ''
  progressText.value = '正在提交检测任务...'

  try {
    const res = await submitImagingDetectionApi({
      imagingType: imagingForm.imagingType,
      bodyPart: imagingForm.bodyPart,
      imageUrl: imageUrl.value,
      fileName: imageFile.value.name
    })
    currentTaskId.value = res.data.taskId
    ElMessage.success('检测任务已提交，正在处理中...')
    startPolling()
  } catch (e) {
    ElMessage.error('提交失败，请重试')
    detecting.value = false
  } finally {
    submitting.value = false
  }
}

const startPolling = () => {
  progress.value = 20
  progressText.value = '任务排队中...'

  let pollCount = 0
  pollTimer.value = setInterval(async () => {
    pollCount++
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
        detectionResult.value = data
        detecting.value = false
        ElMessage.success('检测完成')
        nextTick(() => {
          drawAnnotations()
        })
      }
    } catch (e) {
      clearInterval(pollTimer.value)
      pollTimer.value = null
      detecting.value = false
      ElMessage.error('获取检测结果失败')
    }
  }, 1500)
}

const getReviewResultText = (result) => {
  const map = { CONFIRM: '确认结果', CORRECT: '已修正', REJECT: '驳回' }
  return map[result] || '待审核'
}

const getReviewTagType = (result) => {
  const map = { CONFIRM: 'success', CORRECT: 'warning', REJECT: 'danger' }
  return map[result] || 'info'
}

const getHistoryList = async () => {
  historyLoading.value = true
  try {
    const res = await getImagingHistoryListApi('patient')
    historyList.value = res.data
  } finally {
    historyLoading.value = false
  }
}

const viewHistory = async (row) => {
  activeTab.value = 'new'
  currentTaskId.value = row.taskId
  imagingForm.imagingType = row.imagingType
  imagingForm.bodyPart = row.bodyPart
  progress.value = 100
  progressStatus.value = 'success'
  detecting.value = false
  submitting.value = false

  try {
    const res = await getImagingResultApi(row.taskId)
    detectionResult.value = res.data
    // 还原历史影像原图：用上传时记录的 URL，不替换为第三方域名
    imageUrl.value = row.imageUrl || row.originalImageUrl || ''
    nextTick(() => {
      setTimeout(() => drawAnnotations(), 300)
    })
  } catch (e) {
    ElMessage.error('获取详情失败')
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
  max-width: 700px;
}

.image-uploader {
  width: 100%;
}
.image-uploader :deep(.el-upload-dragger) {
  width: 100%;
  height: 320px;
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
  text-align: center;
}
.submit-section .el-button {
  width: 200px;
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
.review-box {
  padding: 16px;
  background: var(--bg-page);
  border-radius: var(--radius-base);
}
.findings-box h4,
.diagnosis-box h4,
.suggestions-box h4,
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
.suggestions-box li {
  font-size: 13px;
  color: var(--text-regular);
  line-height: 2;
}
.review-box {
  background: #f6ffed;
}
.review-meta {
  font-size: 12px !important;
  color: var(--text-secondary) !important;
  margin-top: 8px !important;
}
</style>

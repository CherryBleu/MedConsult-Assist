<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <div class="header-left">
          <el-icon :size="28" color="#1677ff"><Monitor /></el-icon>
          <div>
            <h2 class="title">影像AI辅助诊断</h2>
            <p class="desc">上传医学影像，AI辅助检测异常区域，医生审核确认</p>
          </div>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="main-tabs">
        <el-tab-pane label="检测任务列表" name="list">
          <div class="filter-bar">
            <el-radio-group v-model="filterStatus" @change="getTaskList">
              <el-radio-button value="all">全部</el-radio-button>
              <el-radio-button value="PENDING">待审核</el-radio-button>
              <el-radio-button value="REVIEWED">已审核</el-radio-button>
            </el-radio-group>
            <el-button type="primary" @click="activeTab = 'detect'">
              <el-icon><Plus /></el-icon>新建检测
            </el-button>
          </div>

          <el-table :data="filteredList" v-loading="listLoading" stripe @row-click="viewTask">
            <el-table-column prop="taskNo" label="检测编号" width="170" />
            <el-table-column prop="patientName" label="患者姓名" width="100" />
            <el-table-column prop="imagingType" label="影像类型" width="90" />
            <el-table-column prop="bodyPart" label="检查部位" width="90" />
            <el-table-column label="AI检测结果" width="120">
              <template #default="{ row }">
                <el-tag :type="row.hasAbnormal ? 'danger' : 'success'" size="small">
                  {{ row.hasAbnormal ? '检出异常' : '未见异常' }}
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
            <el-table-column prop="createdAt" label="提交时间" width="170" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click.stop="viewTask(row)">查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="新建检测" name="detect">
          <div class="upload-section">
            <el-form label-width="100px">
              <el-form-item label="患者姓名">
                <el-input v-model="imagingForm.patientName" placeholder="请输入患者姓名" style="width: 200px" />
              </el-form-item>
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
              <el-button @click="activeTab = 'list'">返回列表</el-button>
              <el-button
                type="primary"
                size="large"
                :loading="submitting"
                :disabled="!imageUrl || !imagingForm.imagingType || !imagingForm.bodyPart"
                @click="submitDetection"
              >
                {{ submitting ? '检测中...' : '提交AI检测' }}
              </el-button>
            </div>

            <div v-if="detecting" class="progress-section">
              <el-progress :percentage="progress" :status="progressStatus" />
              <p class="progress-text">{{ progressText }}</p>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="检测详情" name="detail" v-if="currentTask">
          <div class="detail-header">
            <div>
              <h3>检测详情 - {{ currentTask.taskNo }}</h3>
              <p class="detail-meta">患者：{{ currentTask.patientName || '未知' }} | {{ currentTask.imagingType }} {{ currentTask.bodyPart }} | 提交时间：{{ currentTask.createdAt }}</p>
            </div>
            <el-button @click="activeTab = 'list'">返回列表</el-button>
          </div>

          <div class="detail-content">
            <div class="detail-image-section">
              <div class="image-wrapper">
                <img :src="imageUrl" class="detail-image" @load="drawDetailAnnotations" />
                <canvas ref="detailCanvas" class="detail-canvas"></canvas>
                <div v-if="currentTask.regions && currentTask.regions.length" class="legend">
                  <div v-for="(region, idx) in currentTask.regions" :key="idx" class="legend-item">
                    <span class="legend-color" :style="{ background: getRegionColor(idx) }"></span>
                    <span>{{ region.label }} ({{ region.confidence }}%)</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="detail-info-section">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="检测编号">{{ currentTask.taskNo }}</el-descriptions-item>
                <el-descriptions-item label="检测模型">{{ currentTask.modelName }}</el-descriptions-item>
                <el-descriptions-item label="影像类型">{{ currentTask.imagingType }}</el-descriptions-item>
                <el-descriptions-item label="检查部位">{{ currentTask.bodyPart }}</el-descriptions-item>
                <el-descriptions-item label="AI置信度" :span="2">
                  <el-progress :percentage="currentTask.confidence" :stroke-width="8" />
                </el-descriptions-item>
                <el-descriptions-item label="AI检测结论" :span="2">
                  <el-tag :type="currentTask.hasAbnormal ? 'danger' : 'success'" size="small">
                    {{ currentTask.hasAbnormal ? '检测到异常' : '未见明显异常' }}
                  </el-tag>
                </el-descriptions-item>
              </el-descriptions>

              <div class="findings-box">
                <h4>AI影像所见</h4>
                <p>{{ currentTask.aiFindings }}</p>
              </div>

              <div class="diagnosis-box">
                <h4>AI诊断建议</h4>
                <p class="diagnosis-text">{{ currentTask.aiDiagnosis }}</p>
              </div>

              <div v-if="currentTask.suggestions && currentTask.suggestions.length" class="suggestions-box">
                <h4>AI后续建议</h4>
                <ul>
                  <li v-for="(sug, idx) in currentTask.suggestions" :key="idx">{{ sug }}</li>
                </ul>
              </div>

              <div v-if="currentTask.reviewStatus === 'REVIEWED'" class="reviewed-box">
                <h4>审核结果</h4>
                <el-tag :type="getReviewTagType(currentTask.reviewResult)" style="margin-bottom: 12px">
                  {{ getReviewResultText(currentTask.reviewResult) }}
                </el-tag>
                <p><strong>医生意见：</strong>{{ currentTask.doctorOpinion }}</p>
                <p class="review-meta">审核医生：{{ currentTask.reviewedBy }} | {{ currentTask.reviewedAt }}</p>
              </div>

              <div v-else class="review-section">
                <h4>医生审核</h4>
                <el-form label-width="100px">
                  <el-form-item label="审核结果">
                    <el-radio-group v-model="reviewForm.reviewResult">
                      <el-radio value="CONFIRM">
                        <el-tag type="success" size="small">确认</el-tag>
                        同意AI检测结果
                      </el-radio>
                      <el-radio value="CORRECT">
                        <el-tag type="warning" size="small">修正</el-tag>
                        修正AI检测结果
                      </el-radio>
                      <el-radio value="REJECT">
                        <el-tag type="danger" size="small">驳回</el-tag>
                        AI检测不准确
                      </el-radio>
                    </el-radio-group>
                  </el-form-item>

                  <el-form-item v-if="reviewForm.reviewResult === 'CORRECT'" label="修正诊断">
                    <el-input
                      v-model="reviewForm.correctedDiagnosis"
                      type="textarea"
                      :rows="3"
                      placeholder="请输入修正后的诊断意见..."
                    />
                  </el-form-item>

                  <el-form-item label="医生意见">
                    <el-input
                      v-model="reviewForm.doctorOpinion"
                      type="textarea"
                      :rows="4"
                      placeholder="请输入您的审核意见..."
                    />
                  </el-form-item>

                  <el-form-item>
                    <el-button type="primary" :loading="reviewing" @click="submitReview">
                      提交审核
                    </el-button>
                    <el-button @click="resetReviewForm">重置</el-button>
                  </el-form-item>
                </el-form>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor, UploadFilled, Close, Plus } from '@element-plus/icons-vue'
import { submitImagingDetectionApi, getImagingResultApi, reviewImagingDetectionApi, getImagingHistoryListApi } from '@/api/ai'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()

const activeTab = ref('list')
const filterStatus = ref('all')
const taskList = ref([])
const listLoading = ref(false)
const currentTask = ref(null)

const imageUrl = ref('')
const imageFile = ref(null)
const submitting = ref(false)
const detecting = ref(false)
const progress = ref(0)
const progressStatus = ref('')
const progressText = ref('')
const currentTaskId = ref('')
const pollTimer = ref(null)
const reviewing = ref(false)

const previewImg = ref(null)
const annotationCanvas = ref(null)
const detailCanvas = ref(null)

const imagingForm = reactive({
  patientName: '',
  imagingType: 'CT',
  bodyPart: '胸部'
})

const reviewForm = reactive({
  reviewResult: 'CONFIRM',
  correctedDiagnosis: '',
  doctorOpinion: ''
})

const regionColors = ['#ff4d4f', '#faad14', '#52c41a', '#1890ff', '#722ed1']

const getRegionColor = (idx) => regionColors[idx % regionColors.length]

const filteredList = computed(() => {
  if (filterStatus.value === 'all') return taskList.value
  return taskList.value.filter(item => item.reviewStatus === filterStatus.value)
})

const getTaskList = async () => {
  listLoading.value = true
  try {
    const res = await getImagingHistoryListApi('doctor')
    taskList.value = res.data
  } finally {
    listLoading.value = false
  }
}

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
  currentTask.value = null
}

const removeImage = () => {
  imageUrl.value = ''
  imageFile.value = null
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
    ctx.setLineDash([])
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

const drawDetailAnnotations = () => {
  nextTick(() => {
    const imgs = document.querySelectorAll('.detail-image')
    const canvases = document.querySelectorAll('.detail-canvas')
    if (imgs.length > 0 && canvases.length > 0) {
      drawCanvas(imgs[imgs.length - 1], canvases[canvases.length - 1], currentTask.value?.regions || [])
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
      patientName: imagingForm.patientName,
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
        data.patientName = imagingForm.patientName
        currentTask.value = data
        detecting.value = false
        ElMessage.success('检测完成，请审核结果')
        activeTab.value = 'detail'
        nextTick(() => {
          setTimeout(() => drawDetailAnnotations(), 300)
        })
        getTaskList()
      }
    } catch (e) {
      clearInterval(pollTimer.value)
      pollTimer.value = null
      detecting.value = false
      ElMessage.error('获取检测结果失败')
    }
  }, 1500)
}

const viewTask = async (row) => {
  currentTaskId.value = row.taskId
  listLoading.value = true
  try {
    const res = await getImagingResultApi(row.taskId)
    currentTask.value = { ...res.data, patientName: row.patientName }
    // 还原任务影像原图：用上传时记录的 URL，不替换为第三方域名
    imageUrl.value = row.imageUrl || row.originalImageUrl || ''
    imagingForm.patientName = row.patientName || ''
    imagingForm.imagingType = row.imagingType
    imagingForm.bodyPart = row.bodyPart
    resetReviewForm()
    activeTab.value = 'detail'
    nextTick(() => {
      setTimeout(() => drawDetailAnnotations(), 300)
    })
  } catch (e) {
    ElMessage.error('获取详情失败')
  } finally {
    listLoading.value = false
  }
}

const resetReviewForm = () => {
  reviewForm.reviewResult = 'CONFIRM'
  reviewForm.correctedDiagnosis = ''
  reviewForm.doctorOpinion = ''
}

const submitReview = async () => {
  if (!reviewForm.doctorOpinion.trim()) {
    ElMessage.warning('请填写医生意见')
    return
  }
  if (reviewForm.reviewResult === 'CORRECT' && !reviewForm.correctedDiagnosis.trim()) {
    ElMessage.warning('请填写修正后的诊断')
    return
  }

  await ElMessageBox.confirm('确认提交审核结果？', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning'
  })

  reviewing.value = true
  try {
    await reviewImagingDetectionApi(currentTaskId.value, {
      reviewResult: reviewForm.reviewResult,
      correctedDiagnosis: reviewForm.reviewResult === 'CORRECT' ? reviewForm.correctedDiagnosis : '',
      doctorOpinion: reviewForm.doctorOpinion
    })
    ElMessage.success('审核提交成功')
    currentTask.value.reviewStatus = 'REVIEWED'
    currentTask.value.reviewResult = reviewForm.reviewResult
    currentTask.value.doctorOpinion = reviewForm.doctorOpinion
    currentTask.value.reviewedBy = userStore.userInfo?.name || userStore.userInfo?.username || '当前医生'
    currentTask.value.reviewedAt = new Date().toLocaleString()
    if (reviewForm.reviewResult === 'CORRECT') {
      currentTask.value.aiDiagnosis = reviewForm.correctedDiagnosis
    }
    getTaskList()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('审核提交失败')
    }
  } finally {
    reviewing.value = false
  }
}

const getReviewResultText = (result) => {
  const map = { CONFIRM: '医生已确认结果', CORRECT: '医生已修正结果', REJECT: '医生已驳回' }
  return map[result] || '待审核'
}

const getReviewTagType = (result) => {
  const map = { CONFIRM: 'success', CORRECT: 'warning', REJECT: 'danger' }
  return map[result] || 'info'
}

onMounted(() => {
  getTaskList()
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

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
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
  display: flex;
  justify-content: center;
  gap: 12px;
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

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-light);
}
.detail-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px;
}
.detail-meta {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
}

.detail-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.detail-image-section .image-wrapper {
  position: relative;
  background: #000;
  border-radius: var(--radius-base);
  overflow: hidden;
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.detail-image {
  max-width: 100%;
  max-height: 500px;
  object-fit: contain;
}
.detail-canvas {
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

.detail-info-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.findings-box,
.diagnosis-box,
.suggestions-box,
.review-section,
.reviewed-box {
  padding: 16px;
  background: var(--bg-page);
  border-radius: var(--radius-base);
}
.findings-box h4,
.diagnosis-box h4,
.suggestions-box h4,
.review-section h4,
.reviewed-box h4 {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--text-primary);
}
.findings-box p,
.diagnosis-box p,
.reviewed-box p {
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
.review-section {
  background: #e6f7ff;
}
.reviewed-box {
  background: #f6ffed;
}
.review-meta {
  font-size: 12px !important;
  color: var(--text-secondary) !important;
  margin-top: 8px !important;
}
</style>

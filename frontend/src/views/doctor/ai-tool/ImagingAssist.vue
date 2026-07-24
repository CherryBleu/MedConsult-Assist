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
            <el-radio-group v-model="filterStatus" class="status-filter" @change="getTaskList" aria-label="审核状态筛选">
              <el-radio-button value="all">全部</el-radio-button>
              <el-radio-button value="PENDING">待审核</el-radio-button>
              <el-radio-button value="REVIEWED">已审核</el-radio-button>
            </el-radio-group>
            <el-button type="primary" class="imaging-action" @click="activeTab = 'detect'">
              <el-icon><Plus /></el-icon>新建检测
            </el-button>
          </div>

          <PageState
            :loading="listLoading"
            :error="listError"
            :empty="filteredList.length === 0"
            loading-text="正在加载影像检测任务..."
            empty-text="暂无影像检测任务"
            @retry="getTaskList"
          >
            <ResponsiveTable aria-label="影像检测任务列表">
              <template #table>
                <el-table :data="filteredList" stripe @row-click="viewTask">
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
                      <el-progress :percentage="Number(row.confidence || 0)" :stroke-width="6" />
                    </template>
                  </el-table-column>
                  <el-table-column label="审核状态" width="100">
                    <template #default="{ row }">
                      <el-tag :type="row.reviewStatus === 'REVIEWED' ? 'success' : 'warning'" size="small">
                        {{ row.reviewStatus === 'REVIEWED' ? '已审核' : '待审核' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="createdAt" label="提交时间" min-width="170" />
                  <el-table-column label="操作" width="120" fixed="right">
                    <template #default="{ row }">
                      <el-button type="primary" link size="small" @click.stop="viewTask(row)">查看详情</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </template>

              <template #card>
                <article
                  v-for="row in filteredList"
                  :key="row.detectionId || row.taskId || row.taskNo"
                  class="imaging-card"
                  data-testid="responsive-imaging-card"
                >
                  <div class="imaging-card__header">
                    <div>
                      <p class="imaging-card__title">{{ row.taskNo || row.taskId }}</p>
                      <p class="imaging-card__meta">{{ row.createdAt || '-' }}</p>
                    </div>
                    <span class="status-chip" :class="row.reviewStatus === 'REVIEWED' ? 'success' : 'warning'">
                      {{ row.reviewStatus === 'REVIEWED' ? '已审核' : '待审核' }}
                    </span>
                  </div>
                  <dl class="imaging-card__fields">
                    <div>
                      <dt>患者</dt>
                      <dd>{{ row.patientName || '未知' }}</dd>
                    </div>
                    <div>
                      <dt>类型</dt>
                      <dd>{{ row.imagingType || '-' }} {{ row.bodyPart || '' }}</dd>
                    </div>
                    <div>
                      <dt>AI结果</dt>
                      <dd>
                        <span class="status-chip" :class="row.hasAbnormal ? 'danger' : 'success'">
                          {{ row.hasAbnormal ? '检出异常' : '未见异常' }}
                        </span>
                      </dd>
                    </div>
                    <div>
                      <dt>置信度</dt>
                      <dd>{{ Number(row.confidence || 0) }}%</dd>
                    </div>
                  </dl>
                  <el-button type="primary" plain class="imaging-action" @click="viewTask(row)">查看详情</el-button>
                </article>
              </template>
            </ResponsiveTable>
          </PageState>
        </el-tab-pane>

        <el-tab-pane label="新建检测" name="detect">
          <div class="upload-section">
            <el-form class="imaging-form" label-position="top">
              <el-form-item label="患者姓名">
                <el-input v-model="imagingForm.patientName" class="field-control" placeholder="请输入患者姓名" aria-label="患者姓名" />
              </el-form-item>
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
                <img ref="previewImg" :src="imageUrl" class="preview-image" alt="待提交的医学影像预览" @load="onImageLoad" />
                <canvas ref="annotationCanvas" class="annotation-canvas" aria-hidden="true"></canvas>
                <div class="image-overlay">
                  <el-button type="danger" size="small" circle class="image-remove-action" aria-label="移除影像" @click.stop="removeImage">
                    <el-icon><Close /></el-icon>
                  </el-button>
                </div>
              </div>
            </el-upload>

            <div class="submit-section">
              <el-button class="imaging-action" @click="activeTab = 'list'">返回列表</el-button>
              <el-button
                type="primary"
                size="large"
                class="imaging-action"
                :loading="submitting"
                :disabled="!imageUrl || !imagingForm.imagingType || !imagingForm.bodyPart"
                @click="submitDetection"
              >
                {{ submitting ? '检测中...' : '提交AI检测' }}
              </el-button>
            </div>

            <div v-if="submitError" class="inline-error" role="alert">{{ submitError }}</div>

            <div v-if="detecting" class="progress-section" role="status" aria-live="polite">
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
            <el-button class="imaging-action" @click="activeTab = 'list'">返回列表</el-button>
          </div>

          <div class="detail-content">
            <div class="detail-image-section">
              <div class="image-wrapper">
                <img :src="imageUrl" class="detail-image" alt="影像检测原图与异常区域标注" @load="drawDetailAnnotations" />
                <canvas ref="detailCanvas" class="detail-canvas" aria-hidden="true"></canvas>
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
                <p><strong>医生意见：</strong>{{ currentTask.reviewComment }}</p>
                <p v-if="currentTask.reviewedBy || currentTask.reviewedAt" class="review-meta">
                  审核医生：{{ currentTask.reviewedBy || '-' }} | {{ currentTask.reviewedAt || '-' }}
                </p>
              </div>

              <div v-else class="review-section">
                <h4>医生审核</h4>
                <el-form class="review-form" label-position="top">
                  <el-form-item label="审核结果">
                    <el-radio-group v-model="reviewForm.reviewResult" class="review-options">
                      <el-radio value="CONFIRMED">
                        <el-tag type="success" size="small">确认</el-tag>
                        同意AI检测结果
                      </el-radio>
                      <el-radio value="CORRECTED">
                        <el-tag type="warning" size="small">修正</el-tag>
                        修正AI检测结果
                      </el-radio>
                      <el-radio value="REJECTED">
                        <el-tag type="danger" size="small">驳回</el-tag>
                        AI检测不准确
                      </el-radio>
                    </el-radio-group>
                  </el-form-item>

                  <el-form-item v-if="reviewForm.reviewResult === 'CORRECTED'" label="修正诊断">
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
                    <div v-if="reviewError" class="inline-error review-error" role="alert">{{ reviewError }}</div>
                    <el-button type="primary" class="imaging-action" :loading="reviewing" @click="submitReview">
                      提交审核
                    </el-button>
                    <el-button class="imaging-action" @click="resetReviewForm">重置</el-button>
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
import { submitImagingDetectionApi, getImagingResultApi, reviewImagingDetectionApi, getImagingHistoryListApi, uploadImageFileApi } from '@/api/ai'
import { useUserStore } from '@/store/modules/user'
import { getToken } from '@/utils/auth'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const userStore = useUserStore()

const activeTab = ref('list')
const filterStatus = ref('all')
const taskList = ref([])
const listLoading = ref(false)
const listError = ref('')
const currentTask = ref(null)
const submitError = ref('')
const reviewError = ref('')

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
  reviewResult: 'CONFIRMED',
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
  listError.value = ''
  try {
    // 后端 GET /ai/imaging-detection/list?patientId=xxx（不传 patientId 返回全部）
    const res = await getImagingHistoryListApi(null)
    taskList.value = Array.isArray(res.data) ? res.data : (res.data?.items ?? res.data?.records ?? [])
  } catch (e) {
    taskList.value = []
    listError.value = e?.response?.data?.message || e?.message || '影像检测任务加载失败'
  } finally {
    listLoading.value = false
  }
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
    // 注意：imagingForm.patientName 是医生手输的患者姓名（非患者主键 ID），
    // 不能作为 patientId 提交（后端 numericId 会把它解析成脏数据落库）。
    // 医生端影像辅助不强绑定患者档案，patientId 留空。
    progressText.value = '正在提交检测任务...'
    const res = await submitImagingDetectionApi({
      imageType: imagingForm.imagingType,
      fileIds: [fileId]
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
    // 退出登录后立即停止轮询
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
      getTaskList()
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
        data.patientName = imagingForm.patientName
        currentTask.value = {
          ...data,
          imagingType: data.imagingType || imagingForm.imagingType,
          bodyPart: data.bodyPart || imagingForm.bodyPart,
          modelName: data.modelName || '当前影像模型'
        }
        detecting.value = false
        ElMessage.success('检测完成，请审核结果')
        activeTab.value = 'detail'
        nextTick(() => {
          setTimeout(() => drawDetailAnnotations(), 300)
        })
        getTaskList()
      } else if (data.status === 'FAILED') {
        // 检测失败：停止轮询，提示用户（之前缺失此分支导致页面假死 + 永久轮询）
        clearInterval(pollTimer.value)
        pollTimer.value = null
        detecting.value = false
        progressStatus.value = 'exception'
        progressText.value = '检测失败：' + (data.failReason || 'AI 服务异常，请重试')
        submitError.value = progressText.value
        ElMessage.error('检测失败，请重试或联系管理员')
        getTaskList()
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

const viewTask = async (row) => {
  currentTaskId.value = row.detectionId || row.taskId
  listLoading.value = true
  try {
    const res = await getImagingResultApi(row.detectionId || row.taskId)
    currentTask.value = {
      ...res.data,
      patientName: row.patientName,
      imagingType: res.data?.imagingType || row.imagingType || imagingForm.imagingType,
      bodyPart: res.data?.bodyPart || row.bodyPart || imagingForm.bodyPart,
      modelName: res.data?.modelName || row.modelName || '当前影像模型'
    }
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
  reviewError.value = ''
  reviewForm.reviewResult = 'CONFIRMED'
  reviewForm.correctedDiagnosis = ''
  reviewForm.doctorOpinion = ''
}

const submitReview = async () => {
  reviewError.value = ''
  if (!reviewForm.doctorOpinion.trim()) {
    reviewError.value = '请填写医生意见'
    ElMessage.warning('请填写医生意见')
    return
  }
  if (reviewForm.reviewResult === 'CORRECTED' && !reviewForm.correctedDiagnosis.trim()) {
    reviewError.value = '请填写修正后的诊断'
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
    // reviewer 身份由后端从 JWT 推导，客户端不能指定。
    const reviewRes = await reviewImagingDetectionApi(currentTaskId.value, {
      reviewResult: reviewForm.reviewResult,
      reviewComment: reviewForm.doctorOpinion + (reviewForm.reviewResult === 'CORRECTED' ? ' 修正诊断: ' + reviewForm.correctedDiagnosis : '')
    })
    ElMessage.success('审核提交成功')
    Object.assign(currentTask.value, reviewRes.data || {})
    if (reviewForm.reviewResult === 'CORRECTED') {
      currentTask.value.aiDiagnosis = reviewForm.correctedDiagnosis
    }
    getTaskList()
  } catch (e) {
    if (e !== 'cancel') {
      reviewError.value = e?.response?.data?.message || e?.message || '审核提交失败'
      ElMessage.error(reviewError.value)
    }
  } finally {
    reviewing.value = false
  }
}

const getReviewResultText = (result) => {
  const map = {
    CONFIRM: '医生已确认结果', CONFIRMED: '医生已确认结果',
    CORRECT: '医生已修正结果', CORRECTED: '医生已修正结果',
    REJECT: '医生已驳回', REJECTED: '医生已驳回'
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
  gap: 12px;
  margin-bottom: 20px;
}

.status-filter {
  min-width: 0;
}

.imaging-action,
.image-remove-action {
  min-height: var(--touch-target);
  min-width: var(--touch-target);
  touch-action: manipulation;
}

.imaging-action {
  min-width: 88px;
}

.imaging-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, .84);
  box-shadow: 0 12px 30px rgba(15, 35, 95, .06);
}

.imaging-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.imaging-card__header > div {
  min-width: 0;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
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

.imaging-card__header .status-chip,
.imaging-card__fields .status-chip {
  flex: 0 0 auto;
}

.imaging-card__title,
.imaging-card__meta {
  margin: 0;
}

.imaging-card__title {
  overflow-wrap: anywhere;
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.imaging-card__meta {
  margin-top: 4px;
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.imaging-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}

.imaging-card__fields div {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}

.imaging-card__fields dt,
.imaging-card__fields dd {
  margin: 0;
}

.imaging-card__fields dt {
  color: var(--text-secondary);
}

.imaging-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  text-align: right;
}

.upload-section {
  max-width: 760px;
}

.imaging-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.imaging-form :deep(.el-form-item__label),
.review-form :deep(.el-form-item__label) {
  color: var(--text-primary);
  font-weight: 600;
  line-height: 1.4;
}

.field-control {
  width: 100%;
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
  text-align: center;
  display: flex;
  justify-content: center;
  gap: 12px;
}

.submit-section :deep(.el-button + .el-button),
.review-section :deep(.el-button + .el-button) {
  margin-left: 0;
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

.review-error {
  width: 100%;
  margin: 0 0 12px;
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
  line-height: 1.6;
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
  min-height: 360px;
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

.review-form :deep(.el-form-item:last-child .el-form-item__content) {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.review-options {
  display: grid;
  gap: 10px;
}

.review-options :deep(.el-radio) {
  min-height: var(--touch-target);
  align-items: center;
  margin-right: 0;
  white-space: normal;
}

.reviewed-box {
  background: #f6ffed;
}
.review-meta {
  font-size: 12px !important;
  color: var(--text-secondary) !important;
  margin-top: 8px !important;
}

@media (max-width: 768px) {
  .page-header,
  .header-left,
  .filter-bar,
  .detail-header,
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

  .status-filter,
  .status-filter :deep(.el-radio-button),
  .status-filter :deep(.el-radio-button__inner),
  .filter-bar .imaging-action,
  .submit-section .imaging-action,
  .detail-header .imaging-action,
  .review-form .imaging-action {
    width: 100%;
  }

  .status-filter {
    display: grid;
    grid-template-columns: 1fr;
  }

  .status-filter :deep(.el-radio-button__inner) {
    min-height: var(--touch-target);
    line-height: 28px;
  }

  .imaging-form,
  .detail-content {
    grid-template-columns: 1fr;
  }

  .image-uploader :deep(.el-upload-dragger),
  .image-preview-wrapper {
    min-height: 240px;
    height: auto;
  }

  .detail-image-section .image-wrapper {
    min-height: 240px;
  }

  .detail-info-section :deep(.el-descriptions__body) {
    overflow-x: auto;
  }

  .legend {
    right: 12px;
    max-width: calc(100% - 24px);
  }
}

@media (max-width: 480px) {
  .image-uploader :deep(.el-upload-dragger),
  .image-preview-wrapper,
  .detail-image-section .image-wrapper {
    min-height: 210px;
  }

  .imaging-form,
  .findings-box,
  .diagnosis-box,
  .suggestions-box,
  .review-section,
  .reviewed-box {
    padding: 14px;
  }
}
</style>

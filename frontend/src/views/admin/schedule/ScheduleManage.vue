<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">排班管理</h2>
        <div class="header-actions">
          <el-button type="success" class="admin-schedule-action" @click="openBatchDialog">批量排班</el-button>
          <el-button type="primary" class="admin-schedule-action" @click="openAddDialog">新增排班</el-button>
        </div>
      </div>

      <el-form :model="queryParams" inline class="search-form">
        <el-form-item label="科室">
          <el-select v-model="queryParams.departmentId" class="schedule-filter-control" placeholder="请选择科室" clearable @change="handleDeptChange">
            <el-option v-for="dept in deptList" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="医生">
          <el-select v-model="queryParams.doctorId" class="schedule-filter-control" placeholder="请选择医生" clearable>
            <el-option v-for="doc in doctorList" :key="doc.id" :label="doc.name" :value="doc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            class="schedule-date-filter"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" class="schedule-status-filter" placeholder="请选择状态" clearable>
            <el-option label="可预约" value="AVAILABLE" />
            <el-option label="已约满" value="FULL" />
            <el-option label="已停诊" value="SUSPENDED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="admin-schedule-action" @click="handleSearch">搜索</el-button>
          <el-button class="admin-schedule-action" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="tableData.length === 0"
        loading-text="正在加载排班..."
        empty-text="暂无排班数据"
        @retry="getList"
      >
        <ResponsiveTable aria-label="排班管理列表">
          <template #table>
            <el-table :data="tableData" border stripe>
              <el-table-column prop="scheduleNo" label="排班编号" width="120" />
              <el-table-column prop="doctorName" label="医生姓名" width="100" />
              <el-table-column prop="deptName" label="科室" width="120" />
              <el-table-column prop="scheduleDate" label="排班日期" width="120" />
              <el-table-column label="时段" width="80">
                <template #default="{ row }">{{ periodLabel(row.period) }}</template>
              </el-table-column>
              <el-table-column label="起止时间" width="130">
                <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
              </el-table-column>
              <el-table-column prop="totalQuota" label="总号源" width="80" align="center" />
              <el-table-column label="已预约" width="80" align="center">
                <template #default="{ row }">
                  <span :class="{ 'text-danger': row.bookedQuota >= row.totalQuota }">{{ row.bookedQuota }}</span>
                </template>
              </el-table-column>
              <el-table-column label="挂号费" width="90">
                <template #default="{ row }">¥{{ row.registrationFee }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="scheduleStatusType(row.status)" size="small">
                    {{ scheduleStatusLabel(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <!-- #17：后端 PUT /schedules/{id} 全量更新已实现，编辑排班字段生效 -->
                  <el-button
                    size="small"
                    type="primary"
                    link
                    class="admin-schedule-action"
                    :aria-label="`编辑 ${row.scheduleDate} ${row.doctorName} 排班`"
                    @click="handleEdit(row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-if="row.status !== 'SUSPENDED'"
                    size="small"
                    type="warning"
                    link
                    class="admin-schedule-action"
                    :aria-label="`停诊 ${row.scheduleDate} ${row.doctorName} 排班`"
                    @click="handleToggleStatus(row, 'SUSPENDED')"
                  >
                    停诊
                  </el-button>
                  <el-button
                    v-else
                    size="small"
                    type="success"
                    link
                    class="admin-schedule-action"
                    :aria-label="`恢复 ${row.scheduleDate} ${row.doctorName} 排班`"
                    @click="handleToggleStatus(row, 'AVAILABLE')"
                  >
                    恢复
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template #card>
            <article
              v-for="row in tableData"
              :key="row.id || row.scheduleNo"
              class="schedule-card"
              data-testid="responsive-admin-schedule-card"
            >
              <div class="schedule-card__header">
                <div>
                  <p class="schedule-card__title">{{ row.doctorName || '-' }}</p>
                  <p class="schedule-card__meta">{{ row.deptName || '-' }} · {{ row.scheduleNo || '-' }}</p>
                </div>
                <el-tag :type="scheduleStatusType(row.status)" size="small">
                  {{ scheduleStatusLabel(row.status) }}
                </el-tag>
              </div>

              <dl class="schedule-card__fields">
                <div>
                  <dt>日期</dt>
                  <dd>{{ row.scheduleDate || '-' }}</dd>
                </div>
                <div>
                  <dt>时段</dt>
                  <dd>{{ periodLabel(row.period) }} {{ row.startTime }} - {{ row.endTime }}</dd>
                </div>
                <div>
                  <dt>号源</dt>
                  <dd>
                    <span :class="{ 'text-danger': row.bookedQuota >= row.totalQuota }">
                      {{ row.bookedQuota }}/{{ row.totalQuota }}
                    </span>
                    <span class="schedule-card__sub">剩余 {{ remainingQuota(row) }}</span>
                  </dd>
                </div>
                <div>
                  <dt>挂号费</dt>
                  <dd>¥{{ row.registrationFee || 0 }}</dd>
                </div>
              </dl>

              <div class="schedule-card__actions">
                <el-button
                  type="primary"
                  plain
                  class="admin-schedule-action"
                  :aria-label="`编辑 ${row.scheduleDate} ${row.doctorName} 排班`"
                  @click="handleEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="row.status !== 'SUSPENDED'"
                  type="warning"
                  plain
                  class="admin-schedule-action"
                  :aria-label="`停诊 ${row.scheduleDate} ${row.doctorName} 排班`"
                  @click="handleToggleStatus(row, 'SUSPENDED')"
                >
                  停诊
                </el-button>
                <el-button
                  v-else
                  type="success"
                  plain
                  class="admin-schedule-action"
                  :aria-label="`恢复 ${row.scheduleDate} ${row.doctorName} 排班`"
                  @click="handleToggleStatus(row, 'AVAILABLE')"
                >
                  恢复
                </el-button>
              </div>
            </article>
          </template>
        </ResponsiveTable>

        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList"
          @current-change="getList"
          class="pagination"
        />
      </PageState>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑排班' : '新增排班'" width="min(560px, calc(100vw - 32px))">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="医生" prop="doctorId">
          <!-- 编辑模式禁用：后端 PUT /schedules/{id} 医生/科室不可变(#17)，改了不落库 -->
          <el-select v-model="form.doctorId" placeholder="请选择医生" style="width: 100%" :disabled="isEdit" @change="handleDoctorChange">
            <el-option v-for="doc in allDoctors" :key="doc.id" :label="doc.deptName + ' - ' + doc.name + ' (' + doc.title + ')'" :value="doc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排班日期" prop="scheduleDate">
          <el-date-picker v-model="form.scheduleDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 100%" :disabled-date="disabledDate" />
        </el-form-item>
        <el-form-item label="时段" prop="period">
          <el-radio-group v-model="form.period">
            <el-radio value="MORNING">上午</el-radio>
            <el-radio value="AFTERNOON">下午</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="起止时间" required>
          <div class="time-range">
            <el-time-picker
              v-model="form.startTime"
              placeholder="开始时间"
              value-format="HH:mm"
              format="HH:mm"
              style="width: 200px"
            />
            <span class="time-sep">-</span>
            <el-time-picker
              v-model="form.endTime"
              placeholder="结束时间"
              value-format="HH:mm"
              format="HH:mm"
              style="width: 200px"
            />
          </div>
        </el-form-item>
        <el-form-item label="总号源数" prop="totalQuota">
          <el-input-number v-model="form.totalQuota" :min="1" :max="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="挂号费" prop="registrationFee">
          <el-input-number v-model="form.registrationFee" :min="0" :precision="0" style="width: 200px" />
          <span class="fee-unit">元</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="admin-schedule-action" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="admin-schedule-action" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量排班弹窗（#16：循环调用 createScheduleApi 生成多天排班，不改后端） -->
    <el-dialog v-model="batchDialogVisible" title="批量排班" width="min(560px, calc(100vw - 32px))">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
        选择医生 + 日期范围 + 时段，系统将自动为范围内的每一天生成排班
      </el-alert>
      <el-form :model="batchForm" label-width="100px">
        <el-form-item label="医生" required>
          <el-select v-model="batchForm.doctorId" placeholder="请选择医生" style="width: 100%" @change="handleBatchDoctorChange">
            <el-option v-for="doc in allDoctors" :key="doc.id" :label="doc.deptName + ' - ' + doc.name + ' (' + doc.title + ')'" :value="doc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围" required>
          <el-date-picker
            v-model="batchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            :disabled-date="disabledDate"
          />
        </el-form-item>
        <el-form-item label="时段" required>
          <el-checkbox-group v-model="batchForm.periods">
            <el-checkbox value="MORNING">上午</el-checkbox>
            <el-checkbox value="AFTERNOON">下午</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="每周">
          <el-checkbox-group v-model="batchForm.weekdays">
            <el-checkbox v-for="w in weekdayOptions" :key="w.value" :value="w.value">{{ w.label }}</el-checkbox>
          </el-checkbox-group>
          <div class="form-tip">不勾选则包含范围内每一天</div>
        </el-form-item>
        <el-form-item label="总号源数">
          <el-input-number v-model="batchForm.totalQuota" :min="1" :max="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="挂号费">
          <el-input-number v-model="batchForm.registrationFee" :min="0" :precision="0" style="width: 200px" />
          <span class="fee-unit">元</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <div v-if="batchProgress.visible" class="batch-progress">
          <el-progress
            :percentage="batchProgress.total ? Math.round(batchProgress.done * 100 / batchProgress.total) : 0"
            :status="batchProgress.fail > 0 ? 'warning' : 'success'"
          />
          <div class="form-tip">
            进度 {{ batchProgress.done }}/{{ batchProgress.total }}（成功 {{ batchProgress.success }}，失败 {{ batchProgress.fail }}）
          </div>
        </div>
        <el-button class="admin-schedule-action" :disabled="batchSubmitting" @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" class="admin-schedule-action" :loading="batchSubmitting" @click="submitBatch">
          生成排班
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getScheduleManageListApi, createScheduleApi, updateScheduleApi, toggleScheduleStatusApi } from '@/api/appointment'
import { getDepartmentListApi } from '@/api/department'
import { getDoctorListApi } from '@/api/doctor'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'
import dayjs from 'dayjs'

const loading = ref(false)
const errorMessage = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
// 编辑模式标识（#17：后端 PUT /schedules/{id} 全量更新接口落地后，排班字段编辑生效）
const isEdit = ref(false)
const currentId = ref(null)
const tableData = ref([])
const total = ref(0)
const deptList = ref([])
const doctorList = ref([])
const allDoctors = ref([])
const dateRange = ref([])
const formRef = ref(null)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  departmentId: '',
  doctorId: '',
  status: ''
})

const form = reactive({
  doctorId: '',
  scheduleDate: '',
  period: 'MORNING',
  startTime: '08:00',
  endTime: '12:00',
  totalQuota: 20,
  registrationFee: 50
})

const rules = {
  doctorId: [{ required: true, message: '请选择医生', trigger: 'change' }],
  scheduleDate: [{ required: true, message: '请选择排班日期', trigger: 'change' }],
  period: [{ required: true, message: '请选择时段', trigger: 'change' }],
  totalQuota: [{ required: true, message: '请输入总号源数', trigger: 'blur' }],
  registrationFee: [{ required: true, message: '请输入挂号费', trigger: 'blur' }]
}

const disabledDate = (time) => {
  return time.getTime() < dayjs().startOf('day').valueOf()
}

const periodLabel = (period) => {
  const map = { MORNING: '上午', AFTERNOON: '下午', EVENING: '夜间' }
  return map[period] || period || '-'
}

const scheduleStatusLabel = (status) => {
  const map = { AVAILABLE: '可预约', FULL: '已约满', SUSPENDED: '已停诊', STOPPED: '已停诊' }
  return map[status] || status || '-'
}

const scheduleStatusType = (status) => {
  const map = { AVAILABLE: 'success', FULL: 'warning', SUSPENDED: 'danger', STOPPED: 'danger' }
  return map[status] || 'info'
}

const remainingQuota = (row) => {
  if (Number.isFinite(Number(row.remainingQuota))) return Number(row.remainingQuota)
  return Number(row.totalQuota || 0) - Number(row.bookedQuota || 0)
}

const getList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const params = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      // 后端 ScheduleController 接收 dateFrom/dateTo（非 startDate/endDate）
      params.dateFrom = dateRange.value[0]
      params.dateTo = dateRange.value[1]
    }
    const res = await getScheduleManageListApi(params)
    tableData.value = res.data.records
    total.value = res.data.total
  } catch (e) {
    tableData.value = []
    total.value = 0
    errorMessage.value = e?.response?.data?.message || e?.message || '排班列表加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const getDeptList = async () => {
  const res = await getDepartmentListApi()
  deptList.value = res.data
}

const getAllDoctors = async () => {
  const res = await getDoctorListApi()
  allDoctors.value = res.data
}

const handleDeptChange = async (val) => {
  queryParams.doctorId = ''
  if (val) {
    const res = await getDoctorListApi(val)
    doctorList.value = res.data
  } else {
    doctorList.value = allDoctors.value
  }
}

const handleDoctorChange = (val) => {
  const doctor = allDoctors.value.find(d => d.id === val)
  if (doctor) {
    // 映射后的医生对象用 fee 字段（mapDoctor 中 registrationFee → fee）
    form.registrationFee = doctor.registrationFee ?? doctor.fee ?? 50
  }
}

const handleSearch = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.departmentId = ''
  queryParams.doctorId = ''
  queryParams.status = ''
  dateRange.value = []
  queryParams.pageNum = 1
  doctorList.value = allDoctors.value
  getList()
}

const openAddDialog = () => {
  isEdit.value = false
  currentId.value = null
  Object.assign(form, {
    doctorId: '',
    scheduleDate: '',
    period: 'MORNING',
    startTime: '08:00',
    endTime: '12:00',
    totalQuota: 20,
    registrationFee: 50
  })
  dialogVisible.value = true
}

// #17：编辑排班，回填列表行字段（需后端 GET /schedules ListItem 返回 doctorId/departmentId 等字段）
const handleEdit = (row) => {
  isEdit.value = true
  currentId.value = row.id
  Object.assign(form, {
    doctorId: row.doctorId,
    scheduleDate: row.scheduleDate,
    period: row.period,
    startTime: row.startTime,
    endTime: row.endTime,
    totalQuota: row.totalQuota,
    registrationFee: row.registrationFee
  })
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateScheduleApi({ id: currentId.value, ...form })
      ElMessage.success('更新成功')
    } else {
      await createScheduleApi(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitting.value = false
  }
}

const handleToggleStatus = (row, status) => {
  const text = status === 'SUSPENDED' ? '停诊' : '恢复'
  ElMessageBox.confirm(`确定要${text}该排班吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await toggleScheduleStatusApi(row.id, status)
    ElMessage.success(`${text}成功`)
    getList()
  }).catch(() => {})
}

// ===== #16：批量排班（前端循环调用 createScheduleApi） =====
const batchDialogVisible = ref(false)
const batchSubmitting = ref(false)
// 批量排班进度（#16：180 条串行最坏 10s+，并发 8 + 进度展示，避免管理员干等）
const batchProgress = reactive({ visible: false, done: 0, total: 0, success: 0, fail: 0 })
const BATCH_CONCURRENCY = 8
const weekdayOptions = [
  { value: 1, label: '周一' }, { value: 2, label: '周二' }, { value: 3, label: '周三' },
  { value: 4, label: '周四' }, { value: 5, label: '周五' }, { value: 6, label: '周六' },
  { value: 0, label: '周日' }
]
const batchForm = reactive({
  doctorId: '',
  dateRange: [],
  periods: ['MORNING', 'AFTERNOON'],
  weekdays: [],
  totalQuota: 20,
  registrationFee: 50
})

const handleBatchDoctorChange = (val) => {
  const doctor = allDoctors.value.find(d => d.id === val)
  if (doctor) {
    batchForm.registrationFee = doctor.registrationFee ?? doctor.fee ?? 50
  }
}

const openBatchDialog = () => {
  batchForm.doctorId = ''
  batchForm.dateRange = []
  batchForm.periods = ['MORNING', 'AFTERNOON']
  batchForm.weekdays = []
  batchForm.totalQuota = 20
  batchForm.registrationFee = 50
  batchDialogVisible.value = true
}

const submitBatch = async () => {
  if (!batchForm.doctorId) {
    ElMessage.warning('请选择医生')
    return
  }
  if (!batchForm.dateRange || batchForm.dateRange.length !== 2) {
    ElMessage.warning('请选择日期范围')
    return
  }
  if (batchForm.periods.length === 0) {
    ElMessage.warning('请至少选择一个时段')
    return
  }

  // 展开日期范围 + 时段，生成所有排班请求
  const start = dayjs(batchForm.dateRange[0])
  const end = dayjs(batchForm.dateRange[1])
  const dayCount = end.diff(start, 'day') + 1
  if (dayCount > 90) {
    ElMessage.warning('单次批量排班不超过 90 天，请缩小日期范围')
    return
  }
  const tasks = []
  let cursor = start
  while (cursor.isBefore(end) || cursor.isSame(end, 'day')) {
    // 按星期过滤（未勾选则全部包含）
    if (batchForm.weekdays.length === 0 || batchForm.weekdays.includes(cursor.day())) {
      for (const period of batchForm.periods) {
        tasks.push({
          doctorId: batchForm.doctorId,
          scheduleDate: cursor.format('YYYY-MM-DD'),
          period,
          startTime: period === 'MORNING' ? '08:00' : '14:00',
          endTime: period === 'MORNING' ? '12:00' : '17:00',
          totalQuota: batchForm.totalQuota,
          registrationFee: batchForm.registrationFee
        })
      }
    }
    cursor = cursor.add(1, 'day')
  }

  if (tasks.length === 0) {
    ElMessage.warning('所选条件下无排班需要生成')
    return
  }

  batchSubmitting.value = true
  batchProgress.visible = true
  batchProgress.done = 0
  batchProgress.total = tasks.length
  batchProgress.success = 0
  batchProgress.fail = 0
  try {
    // 并发池（并发上限 8）：避免 180 条串行最坏 10s+，又不会打爆后端连接池
    const queue = [...tasks]
    const run = async () => {
      while (queue.length) {
        const task = queue.shift()
        try {
          await createScheduleApi(task)
          batchProgress.success++
        } catch {
          batchProgress.fail++
        }
        batchProgress.done++
      }
    }
    await Promise.all(
      Array.from({ length: Math.min(BATCH_CONCURRENCY, tasks.length) }, () => run())
    )
    if (batchProgress.fail === 0) {
      ElMessage.success(`批量排班完成，共生成 ${batchProgress.success} 条排班`)
    } else {
      ElMessage.warning(`生成 ${batchProgress.success} 条成功，${batchProgress.fail} 条失败（可能日期重复）`)
    }
    batchDialogVisible.value = false
    getList()
  } finally {
    batchSubmitting.value = false
    batchProgress.visible = false
  }
}

onMounted(() => {
  getDeptList()
  getAllDoctors().then(() => {
    doctorList.value = allDoctors.value
    getList()
  })
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.search-form {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.search-form :deep(.el-form-item) {
  margin-right: 0;
  margin-bottom: 0;
}

.search-form :deep(.el-form-item__content) {
  min-width: 0;
}

.schedule-filter-control {
  width: 160px;
}

.schedule-date-filter {
  width: 260px;
}

.schedule-status-filter {
  width: 120px;
}

.admin-schedule-action {
  min-width: var(--touch-target);
  min-height: var(--touch-target);
  touch-action: manipulation;
}

.header-actions .admin-schedule-action,
.search-form .admin-schedule-action {
  min-width: 88px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.text-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}
.time-range {
  display: flex;
  align-items: center;
  gap: 10px;
}
.time-sep {
  color: var(--el-text-color-regular);
}
.fee-unit {
  margin-left: 10px;
  color: var(--el-text-color-regular);
}
.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-top: 4px;
}
.batch-progress {
  margin-bottom: 12px;
}

.schedule-card {
  display: grid;
  gap: 14px;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 12px 28px rgba(15, 35, 95, .06);
}

.schedule-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.schedule-card__header > div {
  min-width: 0;
}

.schedule-card__title,
.schedule-card__meta {
  margin: 0;
}

.schedule-card__title {
  overflow-wrap: anywhere;
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.schedule-card__meta {
  margin-top: 4px;
  font-family: var(--font-mono, monospace);
  font-size: var(--font-sm);
  color: var(--text-secondary);
  overflow-wrap: anywhere;
}

.schedule-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}

.schedule-card__fields div {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}

.schedule-card__fields dt,
.schedule-card__fields dd {
  margin: 0;
}

.schedule-card__fields dt {
  color: var(--text-secondary);
}

.schedule-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
  text-align: right;
}

.schedule-card__sub {
  display: block;
  margin-top: 2px;
  font-size: var(--font-xs);
  color: var(--text-secondary);
}

.schedule-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.schedule-card__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.schedule-card__actions .admin-schedule-action {
  width: 100%;
}

@media (max-width: 768px) {
  .page-header,
  .header-actions,
  .search-form {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions .admin-schedule-action,
  .search-form .admin-schedule-action,
  .schedule-filter-control,
  .schedule-date-filter,
  .schedule-status-filter {
    width: 100%;
  }

  .search-form :deep(.el-form-item__label) {
    justify-content: flex-start;
    min-width: 68px;
  }

  .search-form :deep(.el-input__wrapper),
  .search-form :deep(.el-select__wrapper),
  .search-form :deep(.el-date-editor) {
    min-height: var(--touch-target);
  }

  .pagination {
    justify-content: center;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .time-range {
    align-items: stretch;
    flex-direction: column;
  }

  .time-range :deep(.el-date-editor.el-input) {
    width: 100% !important;
  }

  .time-sep {
    display: none;
  }

  :deep(.el-dialog) {
    display: flex;
    flex-direction: column;
    max-height: calc(100vh - 48px);
    margin: 24px auto !important;
  }

  :deep(.el-dialog__body) {
    overflow-y: auto;
  }

  :deep(.el-dialog__footer) {
    display: grid;
    gap: 8px;
  }

  :deep(.el-dialog__footer .el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>

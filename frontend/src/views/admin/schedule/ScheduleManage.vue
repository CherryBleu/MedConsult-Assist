<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">排班管理</h2>
        <div>
          <el-button type="success" @click="openBatchDialog">批量排班</el-button>
          <el-button type="primary" @click="openAddDialog">新增排班</el-button>
        </div>
      </div>

      <el-form :model="queryParams" inline class="search-form">
        <el-form-item label="科室">
          <el-select v-model="queryParams.departmentId" placeholder="请选择科室" clearable style="width: 160px" @change="handleDeptChange">
            <el-option v-for="dept in deptList" :key="dept.id" :label="dept.name" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="医生">
          <el-select v-model="queryParams.doctorId" placeholder="请选择医生" clearable style="width: 160px">
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
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="可预约" value="AVAILABLE" />
            <el-option label="已约满" value="FULL" />
            <el-option label="已停诊" value="STOPPED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="scheduleNo" label="排班编号" width="120" />
        <el-table-column prop="doctorName" label="医生姓名" width="100" />
        <el-table-column prop="deptName" label="科室" width="120" />
        <el-table-column prop="scheduleDate" label="排班日期" width="120" />
        <el-table-column label="时段" width="80">
          <template #default="{ row }">{{ row.period === 'MORNING' ? '上午' : '下午' }}</template>
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
            <el-tag v-if="row.status === 'AVAILABLE'" type="success" size="small">可预约</el-tag>
            <el-tag v-else-if="row.status === 'FULL'" type="warning" size="small">已约满</el-tag>
            <el-tag v-else type="danger" size="small">已停诊</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 'STOPPED'"
              size="small"
              type="warning"
              link
              @click="handleToggleStatus(row, 'STOPPED')"
            >停诊</el-button>
            <el-button
              v-else
              size="small"
              type="success"
              link
              @click="handleToggleStatus(row, 'AVAILABLE')"
            >恢复</el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

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
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑排班' : '新增排班'" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="医生" prop="doctorId">
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
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量排班弹窗（#16：循环调用 createScheduleApi 生成多天排班，不改后端） -->
    <el-dialog v-model="batchDialogVisible" title="批量排班" width="560px">
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
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitBatch">
          生成排班
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getScheduleManageListApi, createScheduleApi, updateScheduleApi, deleteScheduleApi, toggleScheduleStatusApi } from '@/api/appointment'
import { getDepartmentListApi } from '@/api/department'
import { getDoctorListApi } from '@/api/doctor'
import dayjs from 'dayjs'

const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const tableData = ref([])
const total = ref(0)
const deptList = ref([])
const doctorList = ref([])
const allDoctors = ref([])
const dateRange = ref([])
const formRef = ref(null)
const currentId = ref(null)

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

const getList = async () => {
  loading.value = true
  try {
    const params = { ...queryParams }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await getScheduleManageListApi(params)
    tableData.value = res.data.records
    total.value = res.data.total
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
  const text = status === 'STOPPED' ? '停诊' : '恢复'
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
  let successCount = 0
  let failCount = 0
  try {
    for (const task of tasks) {
      try {
        await createScheduleApi(task)
        successCount++
      } catch {
        failCount++
      }
    }
    if (failCount === 0) {
      ElMessage.success(`批量排班完成，共生成 ${successCount} 条排班`)
    } else {
      ElMessage.warning(`生成 ${successCount} 条成功，${failCount} 条失败（可能日期重复）`)
    }
    batchDialogVisible.value = false
    getList()
  } finally {
    batchSubmitting.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除该排班吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteScheduleApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => {})
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
.search-form {
  margin-bottom: 20px;
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
</style>

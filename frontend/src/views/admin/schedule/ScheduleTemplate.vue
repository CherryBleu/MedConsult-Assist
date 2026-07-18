<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">排班模板</h2>
        <div>
          <el-button type="success" @click="openApplyDialog">一键生成排班</el-button>
          <el-button type="primary" @click="openAddDialog">新增模板</el-button>
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
        <el-form-item label="启用状态">
          <el-select v-model="queryParams.enabled" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="true" />
            <el-option label="停用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="templateNo" label="模板编号" width="130" />
        <el-table-column prop="doctorName" label="医生" width="100" />
        <el-table-column prop="deptName" label="科室" width="120" />
        <el-table-column label="周几" width="80">
          <template #default="{ row }">{{ dayOfWeekText(row.dayOfWeek) }}</template>
        </el-table-column>
        <el-table-column label="时段" width="90">
          <template #default="{ row }">{{ periodText(row.period) }}</template>
        </el-table-column>
        <el-table-column label="起止时间" width="130">
          <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
        </el-table-column>
        <el-table-column prop="totalQuota" label="号源" width="70" align="center" />
        <el-table-column label="挂号费" width="90">
          <template #default="{ row }">¥{{ row.registrationFee }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.enabled" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
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

    <!-- 新增/编辑模板弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑模板' : '新增模板'" width="560px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="医生" prop="doctorId">
          <el-select v-model="form.doctorId" placeholder="请选择医生" style="width: 100%" :disabled="isEdit" @change="handleDoctorChange">
            <el-option v-for="doc in allDoctors" :key="doc.id" :label="doc.deptName + ' - ' + doc.name + ' (' + (doc.title || '') + ')'" :value="doc.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="周几" prop="dayOfWeek">
          <el-select v-model="form.dayOfWeek" placeholder="请选择周几" style="width: 100%">
            <el-option v-for="d in 7" :key="d" :label="dayOfWeekText(d)" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="时段" prop="period">
          <el-radio-group v-model="form.period">
            <el-radio value="MORNING">上午</el-radio>
            <el-radio value="AFTERNOON">下午</el-radio>
            <el-radio value="EVENING">晚上</el-radio>
            <el-radio value="FULL_DAY">全天</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="起止时间">
          <div class="time-range">
            <el-time-picker v-model="form.startTime" placeholder="开始时间" value-format="HH:mm" format="HH:mm" style="width: 200px" />
            <span class="time-sep">-</span>
            <el-time-picker v-model="form.endTime" placeholder="结束时间" value-format="HH:mm" format="HH:mm" style="width: 200px" />
          </div>
        </el-form-item>
        <el-form-item label="总号源数" prop="totalQuota">
          <el-input-number v-model="form.totalQuota" :min="1" :max="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="挂号费" prop="registrationFee">
          <el-input-number v-model="form.registrationFee" :min="0" :precision="0" style="width: 200px" />
          <span class="fee-unit">元</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 一键生成弹窗 -->
    <el-dialog v-model="applyDialogVisible" title="一键生成排班" width="480px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
        按启用模板的"周几出诊规律"，从起始日期起批量生成 N 周的实际排班。已存在的排班会自动跳过（不重复生成）。
      </el-alert>
      <el-form :model="applyForm" label-width="100px">
        <el-form-item label="起始日期">
          <el-date-picker v-model="applyForm.startDate" type="date" placeholder="选择起始日期" value-format="YYYY-MM-DD" style="width: 100%" :disabled-date="disabledDate" />
        </el-form-item>
        <el-form-item label="生成周数">
          <el-input-number v-model="applyForm.weeks" :min="1" :max="8" style="width: 200px" />
          <span class="fee-unit">周（1-8）</span>
        </el-form-item>
        <el-form-item label="限定医生">
          <el-select v-model="applyForm.doctorId" placeholder="不填则处理全部启用模板" clearable style="width: 100%">
            <el-option v-for="doc in allDoctors" :key="doc.id" :label="doc.deptName + ' - ' + doc.name" :value="doc.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="success" :loading="applying" @click="submitApply">确认生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTemplateListApi, createTemplateApi, updateTemplateApi, deleteTemplateApi, applyScheduleTemplateApi } from '@/api/scheduleTemplate'
import { getDepartmentListApi } from '@/api/department'
import { getDoctorListApi } from '@/api/doctor'
import dayjs from 'dayjs'

const loading = ref(false)
const dialogVisible = ref(false)
const applyDialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const applying = ref(false)
const tableData = ref([])
const total = ref(0)
const deptList = ref([])
const doctorList = ref([])
const allDoctors = ref([])
const formRef = ref(null)
const currentId = ref(null)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  departmentId: '',
  doctorId: '',
  enabled: ''
})

const form = reactive({
  doctorId: '',
  dayOfWeek: 1,
  period: 'MORNING',
  startTime: '08:00',
  endTime: '12:00',
  totalQuota: 20,
  registrationFee: 50,
  enabled: true
})

const applyForm = reactive({
  startDate: '',
  weeks: 2,
  doctorId: ''
})

const rules = {
  doctorId: [{ required: true, message: '请选择医生', trigger: 'change' }],
  dayOfWeek: [{ required: true, message: '请选择周几', trigger: 'change' }],
  period: [{ required: true, message: '请选择时段', trigger: 'change' }],
  totalQuota: [{ required: true, message: '请输入总号源数', trigger: 'blur' }]
}

const dayOfWeekText = (d) => {
  const map = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }
  return map[d] || d
}
const periodText = (p) => ({ MORNING: '上午', AFTERNOON: '下午', EVENING: '晚上', FULL_DAY: '全天' }[p] || p)

const disabledDate = (time) => time.getTime() < dayjs().startOf('day').valueOf()

const getList = async () => {
  loading.value = true
  try {
    const params = { ...queryParams }
    if (params.enabled === '') delete params.enabled
    const res = await getTemplateListApi(params)
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
  queryParams.enabled = ''
  queryParams.pageNum = 1
  doctorList.value = allDoctors.value
  getList()
}

const openAddDialog = () => {
  isEdit.value = false
  currentId.value = null
  Object.assign(form, {
    doctorId: '',
    dayOfWeek: 1,
    period: 'MORNING',
    startTime: '08:00',
    endTime: '12:00',
    totalQuota: 20,
    registrationFee: 50,
    enabled: true
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  currentId.value = row.id
  Object.assign(form, {
    doctorId: row.doctorId,
    dayOfWeek: row.dayOfWeek,
    period: row.period,
    startTime: row.startTime,
    endTime: row.endTime,
    totalQuota: row.totalQuota,
    registrationFee: row.registrationFee,
    enabled: !!row.enabled
  })
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload = {
      doctorId: form.doctorId,
      dayOfWeek: form.dayOfWeek,
      period: form.period,
      startTime: form.startTime,
      endTime: form.endTime,
      totalQuota: form.totalQuota,
      registrationFee: form.registrationFee,
      enabled: form.enabled ? 1 : 0
    }
    if (isEdit.value) {
      // 编辑不改医生，payload 不带 doctorId（后端忽略）
      const { doctorId, ...updatePayload } = payload
      await updateTemplateApi(currentId.value, updatePayload)
      ElMessage.success('更新成功')
    } else {
      await createTemplateApi(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除该模板吗？（模板：${row.doctorName} ${dayOfWeekText(row.dayOfWeek)} ${periodText(row.period)}）`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteTemplateApi(row.id)
    ElMessage.success('删除成功')
    getList()
  }).catch(() => {})
}

const openApplyDialog = () => {
  applyForm.startDate = dayjs().add(1, 'day').format('YYYY-MM-DD')
  applyForm.weeks = 2
  applyForm.doctorId = ''
  applyDialogVisible.value = true
}

const submitApply = async () => {
  if (!applyForm.startDate) {
    ElMessage.warning('请选择起始日期')
    return
  }
  applying.value = true
  try {
    const payload = {
      startDate: applyForm.startDate,
      weeks: applyForm.weeks
    }
    if (applyForm.doctorId) payload.doctorId = applyForm.doctorId
    const res = await applyScheduleTemplateApi(payload)
    const d = res.data || {}
    ElMessage.success(`生成完成：新增 ${d.generated ?? 0} 条，跳过 ${d.skipped ?? 0} 条已存在`)
    applyDialogVisible.value = false
  } finally {
    applying.value = false
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
.search-form {
  margin-bottom: 20px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
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
</style>

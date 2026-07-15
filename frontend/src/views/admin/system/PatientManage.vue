<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <div class="search-area">
          <el-input
            v-model="searchKeyword"
            placeholder="姓名/手机号/患者编号"
            style="width: 280px"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table :data="patientList" v-loading="loading" border stripe>
        <el-table-column prop="patientNo" label="患者编号" width="160" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column label="性别" width="80">
          <template #default="{ row }">{{ row.gender === 'MALE' ? '男' : '女' }}</template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="80" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="idCard" label="身份证号" width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ row.status === 'ACTIVE' ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleView(row)">查看</el-button>
            <el-button
              size="small"
              :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
              link
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </div>

    <el-dialog v-model="addDialogVisible" title="新增患者" width="520px">
      <el-form :model="addForm" :rules="addFormRules" ref="addFormRef" label-width="100px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="addForm.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="addForm.gender">
            <el-radio value="MALE">男</el-radio>
            <el-radio value="FEMALE">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄" prop="age">
          <el-input-number v-model="addForm.age" :min="0" :max="150" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="addForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="addForm.idCard" placeholder="请输入身份证号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAddForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" title="患者详情" width="650px">
      <div v-if="detailData" class="detail-content">
        <el-descriptions title="基本信息" :column="2" border>
          <el-descriptions-item label="患者编号">{{ detailData.patientNo }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ detailData.name }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ detailData.gender === 'MALE' ? '男' : '女' }}</el-descriptions-item>
          <el-descriptions-item label="年龄">{{ detailData.age }}岁</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detailData.phone }}</el-descriptions-item>
          <el-descriptions-item label="身份证号">{{ detailData.idCard }}</el-descriptions-item>
          <el-descriptions-item label="出生日期">{{ detailData.birthDate }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailData.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
              {{ detailData.status === 'ACTIVE' ? '正常' : '禁用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="注册时间" :span="2">{{ detailData.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ detailData.address }}</el-descriptions-item>
        </el-descriptions>

        <el-descriptions title="健康档案" :column="1" border class="mt-20" style="margin-top: 20px">
          <el-descriptions-item label="过敏史">{{ detailData.allergies }}</el-descriptions-item>
          <el-descriptions-item label="既往病史">{{ detailData.pastMedicalHistory }}</el-descriptions-item>
          <el-descriptions-item label="家族病史">{{ detailData.familyHistory }}</el-descriptions-item>
          <el-descriptions-item label="紧急联系人">
            {{ detailData.emergencyContact.name }}（{{ detailData.emergencyContact.relation }}）- {{ detailData.emergencyContact.phone }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button type="primary" @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminPatientListApi, getPatientDetailApi, updatePatientStatusApi, addPatientApi } from '@/api/patient'

const loading = ref(false)
const submitting = ref(false)
const searchKeyword = ref('')
const patientList = ref([])
const addDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const detailData = ref(null)
const addFormRef = ref(null)

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const addForm = reactive({
  name: '',
  gender: 'MALE',
  age: null,
  phone: '',
  idCard: ''
})

const addFormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  age: [{ required: true, message: '请输入年龄', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  idCard: [
    { required: true, message: '请输入身份证号', trigger: 'blur' },
    { pattern: /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/, message: '身份证号格式不正确', trigger: 'blur' }
  ]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getAdminPatientListApi({
      keyword: searchKeyword.value,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    patientList.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  getList()
}

const handleReset = () => {
  searchKeyword.value = ''
  pagination.pageNum = 1
  getList()
}

const openAddDialog = () => {
  Object.assign(addForm, {
    name: '',
    gender: 'MALE',
    age: null,
    phone: '',
    idCard: ''
  })
  addDialogVisible.value = true
}

const submitAddForm = async () => {
  if (!addFormRef.value) return
  await addFormRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        await addPatientApi(addForm)
        ElMessage.success('新增成功')
        addDialogVisible.value = false
        getList()
      } finally {
        submitting.value = false
      }
    }
  })
}

const handleView = async (row) => {
  const res = await getPatientDetailApi(row.id)
  detailData.value = res.data
  detailDialogVisible.value = true
}

const handleToggleStatus = (row) => {
  const newStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const actionText = newStatus === 'ACTIVE' ? '启用' : '禁用'
  ElMessageBox.confirm(`确定要${actionText}患者「${row.name}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await updatePatientStatusApi(row.id, newStatus)
    ElMessage.success(`${actionText}成功`)
    getList()
  }).catch(() => {})
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.search-area {
  display: flex;
  gap: 10px;
  align-items: center;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
.detail-content {
  max-height: 60vh;
  overflow-y: auto;
}
</style>

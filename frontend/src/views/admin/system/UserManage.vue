<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">用户管理</h2>
        <el-button type="primary" @click="openAddDialog">新增用户</el-button>
      </div>

      <el-table :data="userList" v-loading="loading" border stripe>
        <el-table-column prop="userNo" label="用户编号" width="120" />
        <el-table-column prop="account" label="账号" width="140" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag>{{ getRoleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
              {{ row.status === 'ACTIVE' ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="账号">
          <el-input v-model="form.account" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="患者" value="PATIENT" />
            <el-option label="医生" value="DOCTOR" />
            <el-option label="药房管理员" value="PHARMACY_ADMIN" />
            <el-option label="医院管理员" value="HOSPITAL_ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">正常</el-radio>
            <el-radio value="DISABLED">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRoleLabel } from '@/constants'
import { getUserListApi, addUserApi, updateUserApi, deleteUserApi } from '@/api/system'

const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const userList = ref([])
const currentId = ref(null)

const form = reactive({
  account: '',
  name: '',
  phone: '',
  role: 'PATIENT',
  status: 'ACTIVE'
})

const getUserList = async () => {
  loading.value = true
  try {
    const res = await getUserListApi()
    // 后端 GET /auth/users 返回 PageResult {page,pageSize,total,items}，不是数组。
    // 直接赋 res.data 会让 el-table :data 拿到对象而非数组。
    // request 拦截器统一补了 records 别名（items↔records），这里两者都兼容。
    userList.value = res.data?.items ?? res.data?.records ?? []
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  isEdit.value = false
  currentId.value = null
  Object.assign(form, { account: '', name: '', phone: '', role: 'PATIENT', status: 'ACTIVE' })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  currentId.value = row.id
  // 只赋值表单字段，避免 Object.assign(form, row) 把 row 的 id/userNo/createdAt 等
  // 残留进 form——否则先编辑再新增时，脏字段会随 addUserApi 提交到 /auth/register 造成数据污染。
  Object.assign(form, {
    account: row.account ?? '',
    name: row.name ?? '',
    phone: row.phone ?? '',
    role: row.role ?? 'PATIENT',
    status: row.status ?? 'ACTIVE'
  })
  dialogVisible.value = true
}

const submitForm = async () => {
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateUserApi(currentId.value, form)
      ElMessage.success('更新成功')
    } else {
      await addUserApi(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getUserList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除用户「${row.name}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteUserApi(row.id)
    ElMessage.success('删除成功')
    getUserList()
  }).catch(() => {})
}

getUserList()
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}
</style>
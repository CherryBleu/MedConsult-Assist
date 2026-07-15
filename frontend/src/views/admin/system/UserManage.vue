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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="账号" prop="account">
          <el-input v-model="form.account" placeholder="4-32位字母/数字/下划线" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="默认 Med@123456，用户登录后可修改" />
          <div class="form-tip">不填写则使用默认密码 Med@123456</div>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="中国大陆 11 位手机号（选填）" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%">
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

const DEFAULT_PASSWORD = 'Med@123456'

const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const userList = ref([])
const currentId = ref(null)
const formRef = ref(null)

const form = reactive({
  account: '',
  name: '',
  password: '',
  phone: '',
  role: 'DOCTOR',
  status: 'ACTIVE'
})

// 表单校验规则（#19：手机号未作校验 + #20：新增用户接口错误的根因之一是前端无校验）
const rules = {
  account: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{4,32}$/, message: '账号须 4-32 位字母/数字/下划线', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { pattern: /^[\u4e00-\u9fa5A-Za-z·.\s]{1,50}$/, message: '姓名须 1-50 位中文/字母/空格/点', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式非法（须 11 位 1[3-9] 开头）', trigger: 'blur' }
  ]
}

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
  Object.assign(form, { account: '', name: '', password: '', phone: '', role: 'DOCTOR', status: 'ACTIVE' })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  currentId.value = row.id
  Object.assign(form, {
    account: row.account ?? '',
    name: row.name ?? '',
    password: '',
    phone: row.phone ?? '',
    role: row.role ?? 'DOCTOR',
    status: row.status ?? 'ACTIVE'
  })
  dialogVisible.value = true
}

const submitForm = async () => {
  // 表单校验（#19：原表单完全无校验，提交非法数据直接被后端拒绝）
  if (formRef.value) {
    await formRef.value.validate()
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateUserApi(currentId.value, form)
      ElMessage.success('更新成功')
    } else {
      // #19/#20：后端 /auth/register 要求 password 必填，前端不填则用默认密码
      // 管理类角色（PHARMACY_ADMIN/HOSPITAL_ADMIN）后端禁止自助注册——提示引导
      if (['PHARMACY_ADMIN', 'HOSPITAL_ADMIN'].includes(form.role)) {
        ElMessage.warning(`${getRoleLabel(form.role)} 角色需由系统管理员通过数据库或脚本创建，暂不支持在此新增`)
        return
      }
      const submitData = { ...form }
      if (!submitData.password) {
        submitData.password = DEFAULT_PASSWORD
      }
      await addUserApi(submitData)
      ElMessage.success(`新增成功，初始密码：${submitData.password}，请通知用户登录后修改`)
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
.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-top: 4px;
}
</style>
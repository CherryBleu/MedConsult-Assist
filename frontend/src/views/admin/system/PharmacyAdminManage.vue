<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">药房管理员管理</h2>
        <el-button type="primary" @click="openAddDialog">新增药房管理员</el-button>
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
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增药房管理员" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="账号" prop="account">
          <el-input v-model="form.account" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入11位手机号" />
        </el-form-item>
        <el-form-item label="默认密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入初始密码" />
          <div class="form-tip">
            <el-alert type="info" :closable="false" show-icon :title="''" class="tip-alert">
              <template #default>设置初始密码后，用户首次登录可自行修改</template>
            </el-alert>
          </div>
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
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRoleLabel } from '@/constants'
import { getUserListApi, addUserApi, deleteUserApi } from '@/api/system'

const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const allUserList = ref([])
const formRef = ref(null)

const userList = computed(() => {
  return allUserList.value.filter(i => i.role === 'PHARMACY_ADMIN')
})

const form = reactive({
  account: '',
  name: '',
  phone: '',
  password: '',
  role: 'PHARMACY_ADMIN',
  status: 'ACTIVE'
})

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^\d{11}$/, message: '请输入有效的11位手机号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/, message: '密码须8-64位且至少含字母和数字', trigger: 'blur' }
  ]
}

const getUserList = async () => {
  loading.value = true
  try {
    const res = await getUserListApi()
    allUserList.value = res.data?.items ?? res.data?.records ?? []
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  Object.assign(form, { account: '', name: '', phone: '', password: '', role: 'PHARMACY_ADMIN', status: 'ACTIVE' })
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    await addUserApi(form)
    ElMessage.success('新增成功')
    dialogVisible.value = false
    getUserList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除药房管理员「${row.name}」吗？`, '提示', {
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
  margin-top: 8px;
}
.tip-alert {
  padding: 8px 12px;
}
</style>

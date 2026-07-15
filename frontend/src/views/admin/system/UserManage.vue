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
        <!-- 编辑/删除操作已移除：后端无 PUT/DELETE /auth/users/{id}，docs §2.1 也未定义，
             假成功占位会误导管理员。如需调整用户状态/角色请走数据库或后续补接口。 -->
      </el-table>
    </div>

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增用户" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="账号" prop="account">
          <el-input v-model="form.account" placeholder="4-32位字母/数字/下划线" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="默认 Med@123456，用户登录后可修改" />
          <div class="form-tip">不填写则使用默认密码 Med@123456</div>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="医生" value="DOCTOR" />
            <el-option disabled label="药房管理员（需脚本创建）" value="PHARMACY_ADMIN" />
            <el-option disabled label="医院管理员（需脚本创建）" value="HOSPITAL_ADMIN" />
          </el-select>
          <div class="form-tip">患者请引导自助注册；管理类角色需由系统管理员通过数据库/脚本创建</div>
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
import { ElMessage } from 'element-plus'
import { getRoleLabel } from '@/constants'
import { getUserListApi, addUserApi } from '@/api/system'

const DEFAULT_PASSWORD = 'Med@123456'

const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const userList = ref([])
const formRef = ref(null)

const form = reactive({
  account: '',
  name: '',
  password: '',
  role: 'DOCTOR',
  status: 'ACTIVE'
})

// 表单校验规则（#19：新增用户接口错误的根因之一是前端无校验）
const rules = {
  account: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{4,32}$/, message: '账号须 4-32 位字母/数字/下划线', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { pattern: /^[\u4e00-\u9fa5A-Za-z·.\s]{1,50}$/, message: '姓名须 1-50 位中文/字母/空格/点', trigger: 'blur' }
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
  Object.assign(form, { account: '', name: '', password: '', role: 'DOCTOR', status: 'ACTIVE' })
  dialogVisible.value = true
}

const submitForm = async () => {
  // 表单校验（#19：原表单完全无校验，提交非法数据直接被后端拒绝）
  if (formRef.value) {
    await formRef.value.validate()
  }
  submitting.value = true
  try {
    // #19/#20：后端 /auth/register 要求 password 必填，前端不填则用默认密码
    const submitData = { ...form }
    if (!submitData.password) {
      submitData.password = DEFAULT_PASSWORD
    }
    await addUserApi(submitData)
    ElMessage.success(`新增成功，初始密码：${submitData.password}，请通知用户登录后修改`)
    dialogVisible.value = false
    getUserList()
  } finally {
    submitting.value = false
  }
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
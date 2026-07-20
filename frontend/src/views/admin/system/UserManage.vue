<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">医院管理员管理</h2>
        <el-button type="primary" @click="openAddDialog">新增医院管理员</el-button>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="userList.length === 0"
        empty-text="暂无医院管理员"
        @retry="getUserList"
      >
        <ResponsiveTable aria-label="医院管理员列表">
          <template #table>
            <div
              class="admin-user-table-shell"
              data-testid="admin-user-table-shell"
              aria-label="医院管理员宽表横向滚动区域"
              tabindex="0"
            >
              <el-table :data="userList" border stripe class="admin-user-table">
                <el-table-column prop="userNo" label="用户编号" width="150" />
                <el-table-column prop="account" label="账号" width="180" />
                <el-table-column prop="name" label="姓名" width="160" />
                <el-table-column prop="phone" label="手机号" width="170" />
                <el-table-column label="角色" width="160">
                  <template #default="{ row }">
                    <el-tag>{{ getRoleLabel(row.role) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="120">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
                      {{ row.status === 'ACTIVE' ? '正常' : '禁用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="创建时间" min-width="220" />
                <el-table-column label="操作" width="160" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      size="small"
                      type="danger"
                      link
                      class="admin-user-action"
                      :aria-label="`删除医院管理员 ${row.name}`"
                      @click="handleDelete(row)"
                    >
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>

          <template #card>
            <article
              v-for="row in userList"
              :key="row.id"
              class="user-card"
              data-testid="responsive-user-card"
            >
              <div class="user-card__header">
                <div>
                  <p class="user-card__name">{{ row.name }}</p>
                  <p class="user-card__meta">{{ row.account }} · {{ row.userNo }}</p>
                </div>
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
                  {{ row.status === 'ACTIVE' ? '正常' : '禁用' }}
                </el-tag>
              </div>
              <dl class="user-card__fields">
                <div>
                  <dt>角色</dt>
                  <dd>{{ getRoleLabel(row.role) }}</dd>
                </div>
                <div>
                  <dt>手机号</dt>
                  <dd>{{ row.phone }}</dd>
                </div>
                <div>
                  <dt>创建时间</dt>
                  <dd>{{ row.createdAt }}</dd>
                </div>
              </dl>
              <el-button class="admin-user-action user-card__action" type="danger" plain @click="handleDelete(row)">
                删除
              </el-button>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>
    </div>

    <!-- 新增弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增医院管理员" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="账号" prop="account">
          <el-input v-model="form.account" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="请输入11位手机号"
            maxlength="11"
            inputmode="numeric"
            @input="form.phone = normalizePhoneInput($event)"
          />
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
          <!-- 新建账号后端固定 ACTIVE(createUser 硬编码)，不提供禁用选项避免「改了不生效」误导 -->
          <el-tag type="success" size="small">正常（新建账号默认启用，如需禁用待用户管理接口补充）</el-tag>
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
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const loading = ref(false)
const errorMessage = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const allUserList = ref([])
const formRef = ref(null)

const userList = computed(() => {
  return allUserList.value.filter(i => i.role === 'HOSPITAL_ADMIN')
})

const form = reactive({
  account: '',
  name: '',
  phone: '',
  password: '',
  role: 'HOSPITAL_ADMIN',
  status: 'ACTIVE'
})

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的11位手机号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/, message: '密码须8-64位且至少含字母和数字', trigger: 'blur' }
  ]
}

const normalizePhoneInput = (value) => String(value || '').replace(/\D/g, '').slice(0, 11)

const getUserList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getUserListApi()
    allUserList.value = Array.isArray(res.data) ? res.data : (res.data?.items ?? res.data?.records ?? [])
  } catch (error) {
    errorMessage.value = error?.message || '用户列表加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  Object.assign(form, { account: '', name: '', phone: '', password: '', role: 'HOSPITAL_ADMIN', status: 'ACTIVE' })
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

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除医院管理员「${row.name}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }
  try {
    await deleteUserApi(row.id)
    ElMessage.success('删除成功')
    getUserList()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
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

.admin-user-action {
  min-height: var(--touch-target);
  min-width: 88px;
  touch-action: manipulation;
}

.admin-user-table-shell {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior-x: contain;
  padding-bottom: 4px;
}

.admin-user-table-shell:focus-visible {
  outline: 2px solid rgba(2, 132, 199, .18);
  outline-offset: 2px;
  box-shadow: var(--focus-ring);
}

.admin-user-table {
  min-width: 1360px;
}
.form-tip {
  margin-top: 8px;
}
.tip-alert {
  padding: 8px 12px;
}

.user-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  background: rgba(255, 255, 255, .84);
  box-shadow: 0 12px 30px rgba(15, 35, 95, .06);
}

.user-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.user-card__name,
.user-card__meta {
  margin: 0;
}

.user-card__name {
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.user-card__meta {
  margin-top: 4px;
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.user-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}

.user-card__fields div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}

.user-card__fields dt,
.user-card__fields dd {
  margin: 0;
}

.user-card__fields dt {
  color: var(--text-secondary);
}

.user-card__fields dd {
  color: var(--text-primary);
  text-align: right;
}

.user-card__action {
  min-height: var(--touch-target);
}

@media (max-width: 640px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .page-header .el-button {
    min-height: var(--touch-target);
  }
}
</style>

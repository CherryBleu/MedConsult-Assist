<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">药房管理员管理</h2>
        <div class="pharmacy-admin-toolbar" role="search" aria-label="药房管理员筛选">
          <el-input
            v-model="searchKeyword"
            class="pharmacy-admin-search"
            placeholder="搜索账号、姓名或手机号"
            clearable
          />
          <el-button type="primary" class="pharmacy-admin-action" @click="openAddDialog">新增药房管理员</el-button>
        </div>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="userList.length === 0"
        loading-text="正在加载药房管理员列表..."
        empty-text="暂无药房管理员"
        @retry="getUserList"
      >
        <ResponsiveTable aria-label="药房管理员列表">
          <template #table>
            <div
              class="admin-pharmacy-admin-table-shell"
              data-testid="admin-pharmacy-admin-table-shell"
              aria-label="药房管理员宽表横向滚动区域"
              tabindex="0"
            >
              <el-table :data="userList" border stripe class="admin-pharmacy-admin-table">
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
                      class="pharmacy-admin-action"
                      :aria-label="`删除药房管理员 ${row.name}`"
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
              :key="row.id || row.userNo"
              class="pharmacy-admin-card"
              data-testid="responsive-pharmacy-admin-card"
            >
              <div class="pharmacy-admin-card__header">
                <div>
                  <p class="pharmacy-admin-card__name">{{ row.name }}</p>
                  <p class="pharmacy-admin-card__meta">{{ row.account }} · {{ row.userNo }}</p>
                </div>
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'ACTIVE' ? '正常' : '禁用' }}
                </el-tag>
              </div>
              <dl class="pharmacy-admin-card__fields">
                <div>
                  <dt>角色</dt>
                  <dd>{{ getRoleLabel(row.role) }}</dd>
                </div>
                <div>
                  <dt>手机号</dt>
                  <dd>{{ row.phone || '-' }}</dd>
                </div>
                <div>
                  <dt>创建时间</dt>
                  <dd>{{ row.createdAt || '-' }}</dd>
                </div>
              </dl>
              <el-button
                type="danger"
                plain
                class="pharmacy-admin-action pharmacy-admin-card__action"
                :aria-label="`删除药房管理员 ${row.name}`"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>
    </div>

    <!-- 新增弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      title="新增药房管理员"
      width="min(520px, calc(100vw - 32px))"
      custom-class="admin-pharmacy-admin-dialog"
      body-class="admin-pharmacy-admin-dialog-body"
    >
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
        <el-button class="pharmacy-admin-action" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="pharmacy-admin-action" :loading="submitting" @click="submitForm">确定</el-button>
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
const searchKeyword = ref('')
const formRef = ref(null)

const userList = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  return allUserList.value
    .filter(i => i.role === 'PHARMACY_ADMIN')
    .filter(i => {
      if (!keyword) return true
      return [i.account, i.name, i.phone, i.userNo]
        .filter(Boolean)
        .some(value => String(value).toLowerCase().includes(keyword))
    })
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
  errorMessage.value = ''
  try {
    const res = await getUserListApi()
    allUserList.value = Array.isArray(res.data) ? res.data : (res.data?.items ?? res.data?.records ?? [])
  } catch (e) {
    allUserList.value = []
    errorMessage.value = e?.message || '药房管理员列表加载失败，请重试'
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
  } catch (e) {
    ElMessage.error(e?.message || '新增失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除药房管理员「${row.name}」吗？`, '提示', {
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
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.pharmacy-admin-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
}

.pharmacy-admin-search {
  width: 260px;
  max-width: min(260px, 100%);
  border-radius: var(--radius-base);
  transition: box-shadow .18s ease, outline-color .18s ease;
}

.pharmacy-admin-search:focus-within {
  outline: 2px solid rgba(2, 132, 199, .2);
  outline-offset: 2px;
  box-shadow: var(--focus-ring);
}

.pharmacy-admin-action {
  min-height: var(--touch-target);
  min-width: 88px;
  touch-action: manipulation;
  transition: background-color .18s ease, border-color .18s ease, color .18s ease, box-shadow .18s ease;
}

.admin-pharmacy-admin-table-shell {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior-x: contain;
  padding-bottom: 4px;
}

.admin-pharmacy-admin-table-shell:focus-visible {
  outline: 2px solid rgba(2, 132, 199, .18);
  outline-offset: 2px;
  box-shadow: var(--focus-ring);
}

.admin-pharmacy-admin-table {
  min-width: 1360px;
}

.pharmacy-admin-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 12px 28px rgba(15, 35, 95, .06);
}

.pharmacy-admin-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.pharmacy-admin-card__header > div {
  min-width: 0;
}

.pharmacy-admin-card__name,
.pharmacy-admin-card__meta {
  margin: 0;
}

.pharmacy-admin-card__name {
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.pharmacy-admin-card__meta {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: var(--font-sm);
  overflow-wrap: anywhere;
}

.pharmacy-admin-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}

.pharmacy-admin-card__fields div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}

.pharmacy-admin-card__fields dt,
.pharmacy-admin-card__fields dd {
  margin: 0;
}

.pharmacy-admin-card__fields dt {
  color: var(--text-secondary);
}

.pharmacy-admin-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
  text-align: right;
}

.pharmacy-admin-card__action {
  width: 100%;
}

.form-tip {
  margin-top: 8px;
}
.tip-alert {
  padding: 8px 12px;
}

@media (max-width: 640px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .pharmacy-admin-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .pharmacy-admin-search,
  .pharmacy-admin-toolbar .pharmacy-admin-action {
    width: 100%;
    max-width: 100%;
  }
}

:global(.admin-pharmacy-admin-dialog) {
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 72px);
  margin: 24px auto !important;
}

:global(.admin-pharmacy-admin-dialog-body) {
  flex: 1 1 auto;
  min-height: 0;
  max-height: min(42vh, 360px) !important;
  overflow-y: auto !important;
}

:global(.admin-pharmacy-admin-dialog .el-dialog__footer) {
  flex: 0 0 auto;
}
</style>

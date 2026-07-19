<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">科室管理</h2>
        <el-button type="primary" class="department-manage-action" @click="openAddDialog">新增科室</el-button>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="deptList.length === 0"
        loading-text="正在加载科室列表..."
        empty-text="暂无科室数据"
        @retry="getDeptList"
      >
        <div
          class="department-table-shell"
          data-testid="admin-department-table-shell"
          aria-label="科室管理列表"
        >
          <el-table :data="deptList" border stripe class="department-table">
            <el-table-column prop="departmentNo" label="科室编号" width="160" />
            <el-table-column prop="name" label="科室名称" width="140" />
            <el-table-column prop="description" label="科室描述" min-width="220" show-overflow-tooltip />
            <el-table-column prop="location" label="位置" width="140" />
            <el-table-column prop="doctorCount" label="医生数量" width="100" align="center" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button
                  size="small"
                  type="primary"
                  link
                  class="department-manage-action"
                  :aria-label="`编辑科室 ${row.name}`"
                  @click="handleEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  link
                  class="department-manage-action"
                  :aria-label="`删除科室 ${row.name}`"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </PageState>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑科室' : '新增科室'"
      width="min(500px, calc(100vw - 32px))"
      class="department-dialog"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="科室编号" v-if="isEdit">
          <el-input v-model="form.departmentNo" disabled />
        </el-form-item>
        <el-form-item label="科室名称">
          <el-input v-model="form.name" placeholder="请输入科室名称" />
        </el-form-item>
        <el-form-item label="科室描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="位置">
          <el-input v-model="form.location" placeholder="请输入位置" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="department-manage-action" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="department-manage-action" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDeptListApi, addDeptApi, updateDeptApi, deleteDeptApi } from '@/api/system'
import PageState from '@/components/common/PageState.vue'

const loading = ref(false)
const errorMessage = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const deptList = ref([])
const currentId = ref(null)

const form = reactive({
  departmentNo: '',
  name: '',
  description: '',
  location: '',
  enabled: 1
})

const getDeptList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getDeptListApi()
    deptList.value = Array.isArray(res.data) ? res.data : (res.data?.items ?? res.data?.records ?? [])
  } catch (e) {
    deptList.value = []
    errorMessage.value = e?.message || '科室列表加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const openAddDialog = () => {
  isEdit.value = false
  currentId.value = null
  Object.assign(form, { departmentNo: '', name: '', description: '', location: '', enabled: 1 })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  currentId.value = row.id
  Object.assign(form, row)
  dialogVisible.value = true
}

const submitForm = async () => {
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateDeptApi(currentId.value, form)
      ElMessage.success('更新成功')
    } else {
      await addDeptApi(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getDeptList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除科室「${row.name}」吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteDeptApi(row.id)
    ElMessage.success('删除成功')
    getDeptList()
  }).catch(() => {})
}

getDeptList()
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
.department-manage-action {
  min-height: 32px;
  min-width: 88px;
}
.department-table-shell {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior-x: contain;
}
.department-table {
  min-width: 960px;
}
:deep(.department-dialog) {
  max-width: calc(100vw - 32px);
}
:deep(.department-dialog .el-dialog__body) {
  max-height: min(58vh, 520px);
  overflow-y: auto;
}
</style>

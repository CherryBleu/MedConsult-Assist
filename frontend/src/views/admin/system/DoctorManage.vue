<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">医生管理</h2>
        <el-button type="primary" class="doctor-manage-action" @click="openAddDialog">新增医生</el-button>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="doctorList.length === 0"
        loading-text="正在加载医生列表..."
        empty-text="暂无医生数据"
        @retry="getDoctorList"
      >
        <ResponsiveTable aria-label="医生管理列表">
          <template #table>
            <div
              class="admin-doctor-table-shell"
              data-testid="admin-doctor-table-shell"
              aria-label="医生管理宽表横向滚动区域"
              tabindex="0"
            >
              <el-table :data="doctorList" border stripe class="admin-doctor-table">
                <el-table-column prop="doctorNo" label="工号" width="120" />
                <el-table-column prop="name" label="姓名" width="100" />
                <el-table-column label="性别" width="80">
                  <template #default="{ row }">{{ row.gender === 'MALE' ? '男' : '女' }}</template>
                </el-table-column>
                <el-table-column prop="title" label="职称" width="100" />
                <el-table-column prop="departmentName" label="所属科室" width="140" />
                <el-table-column prop="specialties" label="擅长" min-width="180" show-overflow-tooltip />
                <el-table-column prop="phone" label="联系电话" width="130" />
                <el-table-column label="挂号费" width="100">
                  <template #default="{ row }">¥{{ row.registrationFee }}</template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
                      {{ row.status === 'ACTIVE' ? '在职' : '停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200" fixed="right">
                  <template #default="{ row }">
                    <div class="table-actions">
                      <el-button size="small" type="primary" link class="doctor-manage-action" :aria-label="`编辑医生 ${row.name}`" @click="handleEdit(row)">编辑</el-button>
                      <el-button size="small" type="danger" link class="doctor-manage-action" :aria-label="`删除医生 ${row.name}`" @click="handleDelete(row)">删除</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>

          <template #card>
            <article
              v-for="row in doctorList"
              :key="row.id || row.doctorNo"
              class="doctor-card"
              data-testid="responsive-admin-doctor-card"
            >
              <div class="doctor-card__header">
                <div>
                  <p class="doctor-card__name">{{ row.name }}</p>
                  <p class="doctor-card__meta">{{ row.doctorNo || '-' }} · {{ row.title || '-' }}</p>
                </div>
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'ACTIVE' ? '在职' : '停用' }}
                </el-tag>
              </div>

              <dl class="doctor-card__fields">
                <div>
                  <dt>科室</dt>
                  <dd>{{ row.departmentName || '-' }}</dd>
                </div>
                <div>
                  <dt>擅长</dt>
                  <dd>{{ row.specialties || '-' }}</dd>
                </div>
                <div>
                  <dt>电话</dt>
                  <dd>{{ row.phone || '-' }}</dd>
                </div>
                <div>
                  <dt>挂号费</dt>
                  <dd>¥{{ row.registrationFee || 0 }}</dd>
                </div>
              </dl>

              <div class="doctor-card__actions">
                <el-button type="primary" plain class="doctor-manage-action" :aria-label="`编辑医生 ${row.name}`" @click="handleEdit(row)">编辑</el-button>
                <el-button type="danger" plain class="doctor-manage-action" :aria-label="`删除医生 ${row.name}`" @click="handleDelete(row)">删除</el-button>
              </div>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑医生' : '新增医生'"
      width="min(520px, calc(100vw - 32px))"
      class="admin-doctor-dialog"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="工号" prop="doctorNo">
          <el-input v-model="form.doctorNo" placeholder="请输入医生工号" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender" :disabled="isEdit">
            <el-radio value="MALE">男</el-radio>
            <el-radio value="FEMALE">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="职称">
          <el-select v-model="form.title" placeholder="请选择职称" style="width: 100%">
            <el-option label="主任医师" value="主任医师" />
            <el-option label="副主任医师" value="副主任医师" />
            <el-option label="主治医师" value="主治医师" />
            <el-option label="住院医师" value="住院医师" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属科室">
          <el-select v-model="form.departmentId" placeholder="请选择科室" style="width: 100%">
            <el-option 
              v-for="dept in deptList" 
              :key="dept.id" 
              :label="dept.name" 
              :value="dept.id" 
            />
          </el-select>
        </el-form-item>
        <el-form-item label="擅长领域">
          <el-input v-model="form.specialties" type="textarea" :rows="2" placeholder="请输入擅长领域" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="请输入11位手机号"
            maxlength="11"
            inputmode="numeric"
            @input="form.phone = normalizePhoneInput($event)"
          />
        </el-form-item>
        <el-form-item label="挂号费">
          <el-input-number v-model="form.registrationFee" :min="0" :precision="0" />
        </el-form-item>
        <el-form-item label="默认密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" placeholder="请输入初始密码" />
          <div class="form-tip">
            <el-alert type="info" :closable="false" show-icon :title="''" class="tip-alert">
              <template #default>设置初始密码后，用户首次登录可自行修改</template>
            </el-alert>
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ACTIVE">在职</el-radio>
            <el-radio value="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="doctor-manage-action" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="doctor-manage-action" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDoctorListApi, addDoctorApi, updateDoctorApi, deleteDoctorApi } from '@/api/system'
import { getDepartmentListApi } from '@/api/department'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const loading = ref(false)
const errorMessage = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const doctorList = ref([])
const deptList = ref([])
const currentId = ref(null)
const formRef = ref(null)

const form = reactive({
  doctorNo: '',
  name: '',
  gender: 'MALE',
  title: '主治医师',
  departmentId: '',
  specialties: '',
  phone: '',
  password: '',
  registrationFee: 20,
  status: 'ACTIVE'
})

const rules = {
  doctorNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
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

const getDoctorList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getDoctorListApi()
    doctorList.value = res.data
  } catch (e) {
    doctorList.value = []
    errorMessage.value = e?.message || '医生列表加载失败，请重试'
  } finally {
    loading.value = false
  }
}

const getDeptList = async () => {
  const res = await getDepartmentListApi()
  deptList.value = res.data
}

const openAddDialog = () => {
  isEdit.value = false
  currentId.value = null
  Object.assign(form, {
    doctorNo: '', name: '', gender: 'MALE', title: '主治医师',
    departmentId: '', specialties: '', phone: '', password: '', registrationFee: 20, status: 'ACTIVE'
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  currentId.value = row.id
  Object.assign(form, {
    doctorNo: row.doctorNo, name: row.name, gender: row.gender, title: row.title,
    departmentId: row.departmentId, specialties: row.specialties, phone: row.phone, 
    password: '', registrationFee: row.registrationFee, status: row.status
  })
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateDoctorApi(currentId.value, form)
      ElMessage.success('更新成功')
    } else {
      await addDoctorApi(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getDoctorList()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除医生「${row.name}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }
  try {
    await deleteDoctorApi(row.id)
    ElMessage.success('删除成功')
    getDoctorList()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(() => {
  getDoctorList()
  getDeptList()
})
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

.doctor-manage-action {
  min-height: var(--touch-target);
  min-width: 88px;
  touch-action: manipulation;
}

.table-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
  gap: 8px;
  white-space: nowrap;
}

.table-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.admin-doctor-table-shell {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior-x: contain;
  padding-bottom: 4px;
}

.admin-doctor-table {
  min-width: 1260px;
}

.admin-doctor-table-shell:focus-visible {
  outline: 2px solid rgba(2, 132, 199, .18);
  outline-offset: 2px;
  box-shadow: var(--focus-ring);
}

.doctor-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 12px 28px rgba(15, 35, 95, .06);
}

.doctor-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.doctor-card__header > div {
  min-width: 0;
}

.doctor-card__name,
.doctor-card__meta {
  margin: 0;
}

.doctor-card__name {
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.doctor-card__meta {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: var(--font-sm);
  overflow-wrap: anywhere;
}

.doctor-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}

.doctor-card__fields div {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-lighter);
}

.doctor-card__fields dt,
.doctor-card__fields dd {
  margin: 0;
}

.doctor-card__fields dt {
  color: var(--text-secondary);
}

.doctor-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
  text-align: right;
}

.doctor-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.doctor-card__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.doctor-card__actions .doctor-manage-action {
  width: 100%;
}

@media (max-width: 640px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .page-header .doctor-manage-action {
    width: 100%;
  }
}

.form-tip {
  margin-top: 8px;
}
.tip-alert {
  padding: 8px 12px;
}

:global(.admin-doctor-dialog) {
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 48px);
  margin: 24px auto !important;
}

:global(.admin-doctor-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

:global(.admin-doctor-dialog .el-dialog__footer) {
  flex: 0 0 auto;
}
</style>

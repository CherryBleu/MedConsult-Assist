<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <div class="search-area">
          <el-input
            v-model="searchKeyword"
            placeholder="姓名/手机号/患者编号"
            class="search-input"
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

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="patientList.length === 0"
        empty-text="暂无患者档案"
        @retry="getList"
      >
        <ResponsiveTable aria-label="患者管理列表">
          <template #table>
            <el-table :data="patientList" border stripe>
              <el-table-column prop="patientNo" label="患者编号" width="160" />
              <el-table-column prop="name" label="姓名" width="100" />
              <el-table-column label="性别" width="80">
                <template #default="{ row }">{{ genderLabel(row.gender) }}</template>
              </el-table-column>
              <el-table-column prop="age" label="年龄" width="80" />
              <el-table-column prop="phone" label="手机号" width="130" />
              <el-table-column prop="idCard" label="身份证号" width="180" show-overflow-tooltip />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="patientStatusType(row.status)" size="small">
                    {{ patientStatusLabel(row.status) }}
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
          </template>

          <template #card>
            <article
              v-for="row in patientList"
              :key="row.id || row.patientNo"
              class="patient-card"
              data-testid="responsive-patient-card"
            >
              <div class="patient-card__header">
                <div>
                  <p class="patient-card__name">{{ row.name }}</p>
                  <p class="patient-card__meta">{{ row.patientNo }}</p>
                </div>
                <el-tag :type="patientStatusType(row.status)" size="small">
                  {{ patientStatusLabel(row.status) }}
                </el-tag>
              </div>

              <dl class="patient-card__fields">
                <div>
                  <dt>性别/年龄</dt>
                  <dd>{{ genderLabel(row.gender) }} · {{ row.age || '-' }}岁</dd>
                </div>
                <div>
                  <dt>手机号</dt>
                  <dd>{{ row.phone || '-' }}</dd>
                </div>
                <div>
                  <dt>身份证号</dt>
                  <dd>{{ row.idCard || '-' }}</dd>
                </div>
                <div>
                  <dt>注册时间</dt>
                  <dd>{{ row.createdAt || '-' }}</dd>
                </div>
              </dl>

              <div class="patient-card__actions">
                <el-button type="primary" plain @click="handleView(row)">查看详情</el-button>
                <el-button
                  :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
                  plain
                  @click="handleToggleStatus(row)"
                >
                  {{ row.status === 'ACTIVE' ? '禁用患者' : '启用患者' }}
                </el-button>
              </div>
            </article>
          </template>
        </ResponsiveTable>
      </PageState>

      <div v-if="pagination.total > 0 && !loading && !errorMessage" class="pagination-wrapper">
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

    <el-dialog v-model="detailDialogVisible" title="患者详情" width="min(650px, 92vw)">
      <div v-if="detailData" class="detail-content">
        <el-descriptions title="基本信息" :column="1" border>
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
import { getAdminPatientListApi, getPatientDetailApi, updatePatientStatusApi } from '@/api/patient'
import PageState from '@/components/common/PageState.vue'
import ResponsiveTable from '@/components/common/ResponsiveTable.vue'

const loading = ref(false)
const errorMessage = ref('')
const searchKeyword = ref('')
const patientList = ref([])
const detailDialogVisible = ref(false)
const detailData = ref(null)

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const getList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await getAdminPatientListApi({
      keyword: searchKeyword.value,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    const records = res.data?.records ?? res.data?.items ?? []
    patientList.value = records
    pagination.total = res.data?.total ?? records.length
  } catch (error) {
    errorMessage.value = error?.message || '患者列表加载失败，请重试'
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

const handleView = async (row) => {
  try {
    const res = await getPatientDetailApi(row.id)
    detailData.value = res.data
    detailDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error?.message || '患者详情加载失败')
  }
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

const genderLabel = (gender) => gender === 'MALE' ? '男' : '女'

const patientStatusLabel = (status) => status === 'ACTIVE' ? '正常' : '禁用'

const patientStatusType = (status) => status === 'ACTIVE' ? 'success' : 'danger'

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
.search-input {
  width: min(280px, 100%);
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

.patient-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid rgba(8, 145, 178, .18);
  border-radius: var(--radius-lg);
  background:
    linear-gradient(145deg, rgba(236, 254, 255, .86), rgba(255, 255, 255, .96));
  box-shadow: 0 14px 32px rgba(15, 76, 117, .08);
}

.patient-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.patient-card__name,
.patient-card__meta {
  margin: 0;
}

.patient-card__name {
  font-size: var(--font-base);
  font-weight: 700;
  color: var(--text-primary);
}

.patient-card__meta {
  margin-top: 4px;
  font-family: var(--font-mono, monospace);
  font-size: var(--font-sm);
  color: var(--text-secondary);
}

.patient-card__fields {
  display: grid;
  gap: 10px;
  margin: 0;
}

.patient-card__fields div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(8, 145, 178, .12);
}

.patient-card__fields dt,
.patient-card__fields dd {
  margin: 0;
}

.patient-card__fields dt {
  flex: 0 0 auto;
  color: var(--text-secondary);
}

.patient-card__fields dd {
  min-width: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
  text-align: right;
}

.patient-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.patient-card__actions .el-button {
  min-height: var(--touch-target);
  margin-left: 0;
}

@media (max-width: 640px) {
  .page-header,
  .search-area {
    align-items: stretch;
    flex-direction: column;
  }

  .search-input {
    width: 100%;
  }

  .search-area .el-button {
    min-height: var(--touch-target);
    margin-left: 0;
  }

  .pagination-wrapper {
    justify-content: center;
    overflow-x: auto;
  }

  .detail-content {
    max-height: 68vh;
  }
}
</style>

<template>
  <div class="page-container">
    <div class="card-box confirm-card">
      <h2 class="page-title">预约信息确认</h2>

      <el-form label-width="80px" class="info-form">
        <el-form-item label="就诊科室">
          <span class="info-text">{{ formData.deptName }}</span>
        </el-form-item>
        <el-form-item label="就诊医生">
          <span class="info-text">{{ formData.doctorName }}</span>
        </el-form-item>
        <el-form-item label="就诊日期">
          <span class="info-text">{{ formData.date }}</span>
        </el-form-item>
        <el-form-item label="就诊时段">
          <span class="info-text">{{ formData.time }}</span>
        </el-form-item>
        <el-form-item label="挂号费用">
          <span class="fee-text">¥{{ formData.fee }}</span>
        </el-form-item>
        <el-form-item label="就诊原因">
          <el-input
            v-model="visitReason"
            type="textarea"
            :rows="3"
            placeholder="请简要描述您的症状或就诊原因"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <div class="confirm-footer">
        <el-button @click="$router.back()">返回修改</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAppointment">
          确认预约并支付
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createAppointmentApi } from '@/api/appointment'

const route = useRoute()
const router = useRouter()

const submitting = ref(false)
const visitReason = ref('')

const formData = reactive({
  scheduleId: '',
  doctorId: '',
  doctorName: '',
  deptName: '',
  date: '',
  time: '',
  fee: 0
})

onMounted(() => {
  Object.assign(formData, route.query)
})

const submitAppointment = async () => {
  if (!visitReason.value.trim()) {
    ElMessage.warning('请填写就诊原因')
    return
  }

  submitting.value = true
  try {
    await createAppointmentApi({
      scheduleId: formData.scheduleId,
      doctorId: formData.doctorId,
      visitReason: visitReason.value
    })
    ElMessage.success('预约成功，请按时就诊')
    router.replace('/patient/appointment')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.confirm-card {
  max-width: 600px;
  margin: 0 auto;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 24px;
  text-align: center;
}
.info-form {
  margin-bottom: 24px;
}
.info-text {
  font-size: 15px;
  color: var(--text-primary);
}
.fee-text {
  font-size: 18px;
  font-weight: 600;
  color: var(--danger-color);
}
.confirm-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}
</style>
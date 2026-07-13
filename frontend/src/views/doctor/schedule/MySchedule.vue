<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">我的排班</h2>
      </div>

      <!-- 日期切换 -->
      <div class="date-tabs">
        <div 
          v-for="(day, index) in dateList" 
          :key="index"
          class="date-tab"
          :class="{ active: selectedDate === day.date }"
          @click="selectedDate = day.date"
        >
          <div class="week">{{ day.week }}</div>
          <div class="day">{{ day.day }}</div>
        </div>
      </div>

      <div v-loading="loading" class="schedule-list">
        <div v-for="item in daySchedule" :key="item.id" class="schedule-item">
          <div class="period-tag" :class="item.period.toLowerCase()">
            {{ item.period === 'MORNING' ? '上午' : '下午' }}
          </div>
          <div class="schedule-info">
            <div class="time">{{ item.startTime }} - {{ item.endTime }}</div>
            <div class="quota">
              总号源：{{ item.totalQuota }} | 
              已预约：{{ item.bookedQuota }} | 
              剩余：{{ item.totalQuota - item.bookedQuota }}
            </div>
          </div>
          <el-tag :type="getStatusType(SCHEDULE_STATUS, item.status)">
            {{ getStatusLabel(SCHEDULE_STATUS, item.status) }}
          </el-tag>
          <div class="fee">挂号费 ¥{{ item.registrationFee }}</div>
        </div>

        <el-empty v-if="!loading && daySchedule.length === 0" description="当日无排班" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import dayjs from 'dayjs'
import { SCHEDULE_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { getScheduleListApi } from '@/api/appointment'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()

const loading = ref(false)
const selectedDate = ref('')
const scheduleList = ref([])

const dateList = computed(() => {
  const list = []
  const weekMap = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  for (let i = 0; i < 7; i++) {
    const date = dayjs().add(i, 'day')
    list.push({
      date: date.format('YYYY-MM-DD'),
      day: date.format('MM/DD'),
      week: i === 0 ? '今天' : weekMap[date.day()]
    })
  }
  return list
})

const daySchedule = computed(() => {
  return scheduleList.value.filter(i => i.scheduleDate === selectedDate.value)
})

const getSchedule = async () => {
  loading.value = true
  try {
    // 只查本人排班（userInfo.doctorId 是主键 id 串，后端 available 支持主键/doctor_no 双查）
    const doctorId = userStore.userInfo?.doctorId
    const res = await getScheduleListApi(doctorId)
    scheduleList.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  selectedDate.value = dateList.value[0].date
  getSchedule()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.date-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  overflow-x: auto;
  padding-bottom: 8px;
}
.date-tab {
  flex-shrink: 0;
  width: 70px;
  padding: 10px 0;
  text-align: center;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all 0.2s;
}
.date-tab.active {
  border-color: var(--primary-color);
  background: var(--primary-color);
  color: #fff;
}
.week { font-size: 12px; margin-bottom: 2px; }
.day { font-size: 14px; font-weight: 600; }

.schedule-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.schedule-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
}
.period-tag {
  width: 60px;
  padding: 4px 0;
  text-align: center;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.period-tag.morning { background: #e6f4ff; color: #1677ff; }
.period-tag.afternoon { background: #fff7e6; color: #faad14; }

.schedule-info {
  flex: 1;
}
.time {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.quota {
  font-size: 12px;
  color: var(--text-secondary);
}
.fee {
  font-size: 14px;
  font-weight: 600;
  color: var(--danger-color);
  flex-shrink: 0;
}
</style>
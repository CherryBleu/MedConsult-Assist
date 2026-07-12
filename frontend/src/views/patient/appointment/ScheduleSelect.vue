<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <el-button @click="$router.back()" :icon="ArrowLeft">返回</el-button>
        <h2 class="page-title">选择就诊时间</h2>
      </div>

      <!-- 医生信息 -->
      <div class="doctor-info-bar">
        <el-avatar :size="48" class="doctor-avatar">{{ (doctorInfo.name || '?').charAt(0) }}</el-avatar>
        <div>
          <div class="doctor-name">
            {{ doctorInfo.name }}
            <el-tag size="small">{{ doctorInfo.title }}</el-tag>
          </div>
          <div class="doctor-dept">{{ deptName }}</div>
        </div>
      </div>

      <!-- 日期选择 -->
      <div class="date-scroll">
        <div 
          v-for="(day, index) in dateList" 
          :key="index"
          class="date-item"
          :class="{ active: selectedDate === day.date }"
          @click="selectDate(day.date)"
        >
          <div class="date-week">{{ day.week }}</div>
          <div class="date-day">{{ day.day }}</div>
        </div>
      </div>

      <!-- 时段列表 -->
      <div v-loading="loading" class="period-list">
        <h3 class="period-title">上午</h3>
        <div class="period-grid">
          <div 
            v-for="item in morningList" 
            :key="item.id"
            class="period-item"
            :class="item.statusClass"
            @click="selectPeriod(item)"
          >
            <div class="period-time">{{ item.startTime }} - {{ item.endTime }}</div>
            <div class="period-quota">
              <span v-if="item.statusClass === 'available'">剩余{{ item.remain }}号</span>
              <span v-else-if="item.statusClass === 'full'">号满</span>
              <span v-else>停诊</span>
            </div>
          </div>
          <el-empty v-if="morningList.length === 0" :image-size="60" description="暂无上午排班" />
        </div>

        <h3 class="period-title">下午</h3>
        <div class="period-grid">
          <div 
            v-for="item in afternoonList" 
            :key="item.id"
            class="period-item"
            :class="item.statusClass"
            @click="selectPeriod(item)"
          >
            <div class="period-time">{{ item.startTime }} - {{ item.endTime }}</div>
            <div class="period-quota">
              <span v-if="item.statusClass === 'available'">剩余{{ item.remain }}号</span>
              <span v-else-if="item.statusClass === 'full'">号满</span>
              <span v-else>停诊</span>
            </div>
          </div>
          <el-empty v-if="afternoonList.length === 0" :image-size="60" description="暂无下午排班" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { getScheduleListApi } from '@/api/appointment'
import { SCHEDULE_STATUS } from '@/constants'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const deptName = ref('')
const doctorInfo = ref({})
const selectedDate = ref('')
const scheduleList = ref([])

// 生成近7天日期
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

// 按日期过滤 + 上午分组
const morningList = computed(() => {
  return scheduleList.value
    .filter(i => i.scheduleDate === selectedDate.value && i.period === 'MORNING')
    .map(item => ({
      ...item,
      remain: item.totalQuota - item.bookedQuota,
      statusClass: item.status === SCHEDULE_STATUS.AVAILABLE.value
        ? 'available'
        : item.status === SCHEDULE_STATUS.FULL.value
          ? 'full'
          : 'suspended'
    }))
})

// 按日期过滤 + 下午分组
const afternoonList = computed(() => {
  return scheduleList.value
    .filter(i => i.scheduleDate === selectedDate.value && i.period === 'AFTERNOON')
    .map(item => ({
      ...item,
      remain: item.totalQuota - item.bookedQuota,
      statusClass: item.status === SCHEDULE_STATUS.AVAILABLE.value
        ? 'available'
        : item.status === SCHEDULE_STATUS.FULL.value
          ? 'full'
          : 'suspended'
    }))
})

// 获取排班列表
const getScheduleList = async () => {
  const doctorId = route.query.doctorId
  if (!doctorId) return

  loading.value = true
  try {
    const res = await getScheduleListApi(doctorId)
    scheduleList.value = res.data
  } finally {
    loading.value = false
  }
}

const selectDate = (date) => {
  selectedDate.value = date
}

const selectPeriod = (period) => {
  if (period.statusClass !== 'available') return
  
  router.push({
    path: '/patient/appointment/confirm',
    query: {
      scheduleId: period.id,
      doctorId: doctorInfo.value.id,
      doctorName: doctorInfo.value.name,
      deptName: deptName.value,
      date: selectedDate.value,
      time: `${period.startTime}-${period.endTime}`,
      fee: period.registrationFee
    }
  })
}

onMounted(() => {
  deptName.value = route.query.deptName || ''
  doctorInfo.value = {
    id: route.query.doctorId,
    name: route.query.doctorName || '医生',
    title: ''
  }
  selectedDate.value = dateList.value[0].date
  getScheduleList()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.doctor-info-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 16px;
  background: var(--bg-page);
  border-radius: var(--radius-base);
  margin-bottom: 20px;
}
.doctor-avatar {
  background: var(--primary-color);
}
.doctor-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.doctor-dept {
  font-size: 13px;
  color: var(--text-secondary);
}

/* 日期滚动条 */
.date-scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 8px;
  margin-bottom: 24px;
}
.date-item {
  flex-shrink: 0;
  width: 70px;
  padding: 12px 0;
  text-align: center;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: all 0.2s;
}
.date-item.active {
  border-color: var(--primary-color);
  background: var(--primary-color);
  color: #fff;
}
.date-week {
  font-size: 12px;
  margin-bottom: 4px;
}
.date-day {
  font-size: 15px;
  font-weight: 600;
}

.period-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 16px 0 12px;
}
.period-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.period-item {
  padding: 14px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}
.period-item.available:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}
.period-item.full {
  background: #f5f5f5;
  color: var(--text-secondary);
  cursor: not-allowed;
}
.period-item.suspended {
  background: #fff1f0;
  color: var(--danger-color);
  cursor: not-allowed;
}
.period-time {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}
.period-quota {
  font-size: 12px;
}
</style>
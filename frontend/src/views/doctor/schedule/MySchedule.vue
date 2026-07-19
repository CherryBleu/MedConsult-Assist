<template>
  <div class="page-container">
    <div class="card-box">
      <div class="page-header">
        <h2 class="page-title">我的排班</h2>
      </div>

      <div class="date-tabs" role="list" aria-label="排班日期">
        <button
          v-for="day in dateList"
          :key="day.date"
          type="button"
          class="date-tab"
          :class="{ active: selectedDate === day.date }"
          :aria-pressed="selectedDate === day.date"
          @click="selectedDate = day.date"
        >
          <span class="week">{{ day.week }}</span>
          <span class="day">{{ day.day }}</span>
        </button>
      </div>

      <PageState
        :loading="loading"
        :error="errorMessage"
        :empty="daySchedule.length === 0"
        loading-text="正在加载排班..."
        empty-text="当日无排班"
        @retry="getSchedule"
      >
        <section class="schedule-list" aria-label="当日排班列表">
          <article
            v-for="item in daySchedule"
            :key="`${item.id}-${item.scheduleDate}-${item.period}`"
            class="schedule-item"
            data-testid="doctor-schedule-card"
          >
            <div class="period-tag" :class="periodClass(item.period)">
              {{ periodLabel(item.period) }}
            </div>
            <div class="schedule-info">
              <p class="time">{{ item.startTime }} - {{ item.endTime }}</p>
              <p class="quota">
                总号源：{{ item.totalQuota }} | 已预约：{{ item.bookedQuota }} |
                剩余：{{ remainingQuota(item) }}
              </p>
            </div>
            <el-tag class="status-tag" :type="getStatusType(SCHEDULE_STATUS, item.status)">
              {{ getStatusLabel(SCHEDULE_STATUS, item.status) }}
            </el-tag>
            <div class="fee">挂号费 ¥{{ item.registrationFee }}</div>
          </article>
        </section>
      </PageState>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import dayjs from 'dayjs'
import { SCHEDULE_STATUS, getStatusLabel, getStatusType } from '@/constants'
import { getScheduleListApi } from '@/api/appointment'
import { useUserStore } from '@/store/modules/user'
import PageState from '@/components/common/PageState.vue'

const userStore = useUserStore()

const loading = ref(false)
const errorMessage = ref('')
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

const remainingQuota = (item) => {
  if (Number.isFinite(Number(item.remainingQuota))) return Number(item.remainingQuota)
  return Number(item.totalQuota || 0) - Number(item.bookedQuota || 0)
}

const periodLabel = (period) => {
  const map = { MORNING: '上午', AFTERNOON: '下午', EVENING: '夜间' }
  return map[period] || period || '-'
}

const periodClass = (period) => String(period || '').toLowerCase()

const getSchedule = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const doctorId = userStore.userInfo?.doctorId
    const res = await getScheduleListApi(doctorId)
    scheduleList.value = Array.isArray(res.data) ? res.data : (res.data?.items ?? res.data?.records ?? [])
  } catch (e) {
    scheduleList.value = []
    errorMessage.value = e?.response?.data?.message || e?.message || '排班加载失败，请重试'
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
  min-width: 0;
  margin-bottom: 20px;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  padding-bottom: 8px;
}

.date-tab {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 76px;
  min-height: 56px;
  padding: 8px 4px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  background: #fff;
  color: var(--text-secondary);
  cursor: pointer;
  touch-action: manipulation;
  transition: background .18s ease, border-color .18s ease, color .18s ease;
}

.date-tab.active {
  border-color: var(--primary-color);
  background: var(--primary-color);
  color: #fff;
}

.date-tab:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

.week {
  font-size: 12px;
  line-height: 1.4;
}

.day {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}

.schedule-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.schedule-item {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 16px;
  min-width: 0;
  padding: 16px 20px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  background: rgba(255, 255, 255, .88);
}

.period-tag {
  width: 60px;
  padding: 6px 0;
  text-align: center;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.period-tag.morning {
  background: #e6f4ff;
  color: #1677ff;
}

.period-tag.afternoon {
  background: #fff7e6;
  color: #b45309;
}

.period-tag.evening {
  background: #eef2ff;
  color: #4f46e5;
}

.schedule-info {
  min-width: 0;
}

.time,
.quota {
  margin: 0;
}

.time {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.quota {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
  overflow-wrap: anywhere;
}

.status-tag,
.fee {
  justify-self: end;
}

.fee {
  font-size: 14px;
  font-weight: 600;
  color: var(--danger-color);
  white-space: nowrap;
}

@media (max-width: 768px) {
  .schedule-item {
    grid-template-columns: 1fr;
    align-items: stretch;
    gap: 12px;
    padding: 16px;
  }

  .period-tag,
  .status-tag,
  .fee {
    justify-self: start;
  }

  .period-tag {
    min-width: 60px;
  }
}
</style>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { CalendarDays, RefreshCw, Star } from 'lucide-vue-next'
import { getWeekday } from '@/api/home'
import { formatScore, todayWeekday, weekdayName } from '@/utils/format'

const groups = ref([])
const loading = ref(true)
const error = ref('')
const selected = ref(todayWeekday())

const days = computed(() =>
  [1, 2, 3, 4, 5, 6, 7].map((weekday) => {
    const group = groups.value.find((item) => Number(item.weekday) === weekday)
    return {
      weekday,
      label: weekdayName(weekday),
      videos: Array.isArray(group?.videos) ? group.videos : []
    }
  })
)

const current = computed(() => days.value.find((d) => d.weekday === selected.value) || days.value[0])

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getWeekday()
    groups.value = Array.isArray(data?.records) ? data.records : []
    selected.value = todayWeekday()
  } catch (err) {
    groups.value = []
    error.value = err.message || '追番周表加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="schedule-page">
    <div class="container">
      <header class="page-head">
        <h1 class="page-title">追番周表</h1>
        <p class="page-sub">
          <CalendarDays :size="13" />
          一周新番更新时间表
        </p>
      </header>

      <div class="day-tabs">
        <button
          v-for="day in days"
          :key="day.weekday"
          type="button"
          class="day-tab"
          :class="{ 'is-active': day.weekday === selected }"
          @click="selected = day.weekday"
        >
          <span class="day-tab__label">{{ day.label }}</span>
          <span class="day-tab__count">{{ day.videos.length }}</span>
        </button>
      </div>

      <div v-if="loading" class="video-grid">
        <div v-for="n in 8" :key="n" class="video-card video-card--skeleton">
          <span class="skeleton skeleton-cover"></span>
          <div class="video-card__body">
            <span class="skeleton skeleton-line"></span>
            <span class="skeleton skeleton-line skeleton-line--sm"></span>
          </div>
        </div>
      </div>

      <div v-else-if="error" class="state-box">
        <p class="state-box__title">追番周表加载失败</p>
        <p class="state-box__hint">{{ error }}</p>
        <button type="button" class="btn-ghost" @click="load">
          <RefreshCw :size="14" /> 重试
        </button>
      </div>

      <div v-else-if="current.videos.length" class="video-grid">
        <RouterLink
          v-for="video in current.videos"
          :key="video.video_id"
          :to="`/anime/${video.video_id}`"
          class="video-card"
        >
          <div class="video-card__cover">
            <img
              :src="video.cover_url || '/images/cover-placeholder.svg'"
              :alt="video.title"
              loading="lazy"
              @error="(event) => (event.target.src = '/images/cover-placeholder.svg')"
            />
            <span v-if="formatScore(video.score)" class="badge-score">
              <Star :size="11" />
              {{ formatScore(video.score) }}
            </span>
          </div>
          <div class="video-card__body">
            <h3 class="video-card__title">{{ video.title }}</h3>
            <p class="video-card__meta">
              <span class="video-card__remark">{{ video.remarks || '连载中' }}</span>
            </p>
          </div>
        </RouterLink>
      </div>

      <div v-else class="state-box">
        <p class="state-box__title">{{ current.label }}暂无更新</p>
        <p class="state-box__hint">切换到其他星期看看。</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.schedule-page {
  padding-top: calc(var(--header-height) + 28px);
  padding-bottom: 48px;
}

.page-head {
  margin-bottom: 20px;
}

.page-title {
  font-size: 26px;
  font-weight: 700;
}

.page-sub {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

.day-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 22px;
}

.day-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 68px;
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid hsl(var(--border));
  background-color: hsl(var(--card));
  transition: border-color 0.2s ease, background-color 0.2s ease;

  &:hover {
    border-color: hsl(var(--brand) / 0.5);
  }

  &.is-active {
    background-color: hsl(var(--brand));
    border-color: hsl(var(--brand));

    .day-tab__label {
      color: #fff;
      font-weight: 600;
    }

    .day-tab__count {
      color: rgba(255, 255, 255, 0.75);
    }
  }
}

.day-tab__label {
  font-size: 14px;
  color: hsl(var(--foreground));
}

.day-tab__count {
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;

  @media (min-width: 640px) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  @media (min-width: 1024px) {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: var(--grid-gap);
  }
}

.video-card {
  display: flex;
  gap: 12px;
  padding: 10px;
  border-radius: 12px;
  background-color: hsl(var(--card));
  border: 1px solid transparent;
  transition: border-color 0.2s ease, transform 0.2s ease;

  &:hover {
    border-color: hsl(var(--brand) / 0.4);
    transform: translateY(-2px);

    .video-card__title {
      color: hsl(var(--brand));
    }
  }
}

.video-card__cover {
  position: relative;
  flex-shrink: 0;
  width: 72px;
  height: 96px;
  border-radius: 8px;
  overflow: hidden;
  background-color: hsl(var(--muted));

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.badge-score {
  position: absolute;
  top: 6px;
  right: 6px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background-color: rgba(0, 0, 0, 0.68);
  color: hsl(var(--score));
  backdrop-filter: blur(4px);
}

.video-card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.video-card__title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  color: hsl(var(--foreground));
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.2s ease;
}

.video-card__meta {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.video-card__remark {
  color: hsl(var(--update));
}

.video-card--skeleton {
  pointer-events: none;
}

.skeleton-cover {
  width: 72px;
  height: 96px;
  border-radius: 8px;
}

.skeleton-line {
  display: block;
  width: 100%;
  height: 14px;
  border-radius: 6px;

  &--sm {
    width: 60%;
  }
}

@media (max-width: 480px) {
  .video-grid {
    grid-template-columns: 1fr;
  }
}
</style>

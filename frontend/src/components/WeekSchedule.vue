<script setup>
import { computed, ref, watch } from 'vue'
import { CalendarDays } from 'lucide-vue-next'
import { todayWeekday, weekdayName } from '@/utils/format'

const props = defineProps({
  data: { type: Array, default: () => [] }
})

const selected = ref(todayWeekday())

const days = computed(() =>
  [1, 2, 3, 4, 5, 6, 7].map((weekday) => {
    const group = props.data.find((item) => Number(item.weekday) === weekday)
    return {
      weekday,
      label: weekdayName(weekday),
      videos: Array.isArray(group?.videos) ? group.videos : []
    }
  })
)

const current = computed(() => days.value.find((day) => day.weekday === selected.value) || days.value[0])

watch(
  () => props.data,
  () => {
    selected.value = todayWeekday()
  }
)
</script>

<template>
  <section id="schedule" class="schedule surface">
    <header class="head">
      <h2 class="section-title">追番周表</h2>
      <span class="head-note">
        <CalendarDays :size="14" />
        每周更新
      </span>
    </header>

    <div class="tabs">
      <button
        v-for="day in days"
        :key="day.weekday"
        type="button"
        class="tab"
        :class="{ 'is-active': day.weekday === selected }"
        :aria-pressed="day.weekday === selected"
        @click="selected = day.weekday"
      >
        {{ day.label }}
      </button>
    </div>

    <div v-if="current.videos.length" class="list">
      <RouterLink
        v-for="video in current.videos"
        :key="video.video_id"
        :to="`/anime/${video.video_id}`"
        class="row"
      >
        <img
          :src="video.cover_url || '/images/cover-placeholder.svg'"
          :alt="video.title"
          loading="lazy"
          class="thumb"
          @error="(event) => (event.target.src = '/images/cover-placeholder.svg')"
        />
        <div class="row-text">
          <p class="row-title">{{ video.title }}</p>
          <p class="row-sub">
            <span class="row-remark">{{ video.remarks || '连载中' }}</span>
            <span v-if="video.score" class="row-score">{{ video.score }} 分</span>
          </p>
        </div>
      </RouterLink>
    </div>
    <div v-else class="empty">
      <p>这一天暂无更新的番剧</p>
    </div>
  </section>
</template>

<style scoped lang="scss">
.schedule {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 14px 16px 16px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.head-note {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 10px;
}

.tab {
  height: 26px;
  padding: 0 10px;
  border-radius: 6px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  transition: background-color 0.2s ease, color 0.2s ease;

  &:hover {
    color: hsl(var(--foreground));
  }

  &.is-active {
    background-color: hsl(var(--background));
    color: hsl(var(--foreground));
    font-weight: 600;
    box-shadow: inset 0 0 0 1px hsl(var(--brand) / 0.35);
  }
}

.list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 16px;
  max-height: 268px;
  overflow-y: auto;
  padding-right: 4px;
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 5px 4px;
  border-radius: 8px;
  transition: background-color 0.2s ease;

  &:hover {
    background-color: hsl(var(--background) / 0.5);

    .row-title {
      color: hsl(var(--brand));
    }
  }
}

.thumb {
  flex-shrink: 0;
  width: 42px;
  height: 56px;
  border-radius: 6px;
  object-fit: cover;
  background-color: hsl(var(--muted));
}

.row-text {
  min-width: 0;
}

.row-title {
  font-size: 13px;
  line-height: 1.4;
  color: hsl(var(--foreground));
  transition: color 0.2s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-sub {
  display: flex;
  gap: 7px;
  margin-top: 4px;
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
  min-width: 0;
}

.row-remark {
  color: hsl(var(--update));
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-score {
  color: hsl(var(--score));
  white-space: nowrap;
}

.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

@media (max-width: 560px) {
  .list {
    grid-template-columns: 1fr;
  }
}
</style>

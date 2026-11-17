<script setup>
import { onMounted, ref } from 'vue'
import { Flame, RefreshCw, Star } from 'lucide-vue-next'
import { getRankVideos, getRanks } from '@/api/home'
import { formatCount, formatScore } from '@/utils/format'

const ranks = ref([])
const activeId = ref(null)
const videos = ref([])
const loading = ref(true)
const error = ref('')
let requestId = 0

async function loadVideos(id) {
  const current = ++requestId
  loading.value = true
  error.value = ''
  try {
    const data = await getRankVideos(id)
    if (current !== requestId) return
    videos.value = Array.isArray(data?.records) ? data.records : []
  } catch (err) {
    if (current !== requestId) return
    videos.value = []
    error.value = err.message || '排行榜加载失败'
  } finally {
    if (current === requestId) loading.value = false
  }
}

async function loadRanks() {
  loading.value = true
  error.value = ''
  try {
    const data = await getRanks()
    ranks.value = Array.isArray(data?.records) ? data.records : []
    if (ranks.value.length) {
      activeId.value = ranks.value[0].id
      await loadVideos(activeId.value)
    } else {
      videos.value = []
      loading.value = false
    }
  } catch (err) {
    error.value = err.message || '排行榜加载失败'
    loading.value = false
  }
}

function selectRank(id) {
  if (id === activeId.value) return
  activeId.value = id
  loadVideos(id)
}

function retry() {
  if (activeId.value) loadVideos(activeId.value)
  else loadRanks()
}

onMounted(loadRanks)
</script>

<template>
  <div class="rank-page">
    <div class="container">
      <header class="page-head">
        <h1 class="page-title">排行榜</h1>
        <p class="page-sub">
          <Flame :size="13" />
          每周更新的热门番剧榜单
        </p>
      </header>

      <div v-if="ranks.length" class="rank-tabs">
        <button
          v-for="rank in ranks"
          :key="rank.id"
          type="button"
          class="rank-tab"
          :class="{ 'is-active': rank.id === activeId }"
          @click="selectRank(rank.id)"
        >
          {{ rank.name }}
          <span class="rank-count">{{ rank.video_count }}</span>
        </button>
      </div>

      <div v-if="loading" class="rank-list">
        <div v-for="n in 10" :key="n" class="rank-row rank-row--skeleton">
          <span class="skeleton skeleton-num"></span>
          <span class="skeleton skeleton-cover"></span>
          <div class="skeleton-lines">
            <span class="skeleton skeleton-line-lg"></span>
            <span class="skeleton skeleton-line-sm"></span>
          </div>
        </div>
      </div>

      <div v-else-if="error" class="state-box">
        <p class="state-box__title">排行榜加载失败</p>
        <p class="state-box__hint">{{ error }}</p>
        <button type="button" class="btn-ghost" @click="retry">
          <RefreshCw :size="14" /> 重试
        </button>
      </div>

      <div v-else-if="videos.length" class="rank-list">
        <RouterLink
          v-for="(video, index) in videos"
          :key="video.video_id"
          :to="`/anime/${video.video_id}`"
          class="rank-row"
        >
          <span class="rank-num" :class="`is-${Math.min(index + 1, 3)}`">{{ index + 1 }}</span>
          <div class="rank-cover">
            <img
              :src="video.cover_url || '/images/cover-placeholder.svg'"
              :alt="video.title"
              loading="lazy"
              @error="(event) => (event.target.src = '/images/cover-placeholder.svg')"
            />
          </div>
          <div class="rank-info">
            <h3 class="rank-title">{{ video.title }}</h3>
            <p class="rank-meta">
              <span v-if="video.area">{{ video.area }}</span>
              <span v-if="video.year">{{ video.year }}</span>
              <span v-if="video.version">{{ video.version }}</span>
            </p>
            <p v-if="video.remarks" class="rank-remark">{{ video.remarks }}</p>
          </div>
          <div class="rank-side">
            <span v-if="formatScore(video.score)" class="rank-score">
              <Star :size="12" />
              {{ formatScore(video.score) }}
            </span>
            <span class="rank-hits">{{ formatCount(video.hits) }} 次播放</span>
          </div>
        </RouterLink>
      </div>

      <div v-else class="state-box">
        <p class="state-box__title">该榜单暂无数据</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.rank-page {
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

.rank-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

.rank-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid hsl(var(--border));
  background-color: hsl(var(--card));
  font-size: 14px;
  color: hsl(var(--muted-foreground));
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;

  &:hover {
    color: hsl(var(--foreground));
    border-color: hsl(var(--brand) / 0.5);
  }

  &.is-active {
    background-color: hsl(var(--brand));
    border-color: hsl(var(--brand));
    color: #fff;
    font-weight: 600;

    .rank-count {
      background-color: rgba(255, 255, 255, 0.22);
      color: #fff;
    }
  }
}

.rank-count {
  padding: 1px 7px;
  border-radius: 999px;
  background-color: hsl(var(--muted));
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rank-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-radius: 12px;
  background-color: hsl(var(--card));
  border: 1px solid transparent;
  transition: border-color 0.2s ease, transform 0.2s ease;

  &:hover {
    border-color: hsl(var(--brand) / 0.4);
    transform: translateX(2px);

    .rank-title {
      color: hsl(var(--brand));
    }
  }
}

.rank-num {
  flex-shrink: 0;
  width: 30px;
  font-size: 20px;
  font-weight: 800;
  text-align: center;
  color: hsl(var(--muted-foreground));

  &.is-1 { color: #f5b83d; }
  &.is-2 { color: #b9c2cc; }
  &.is-3 { color: #cf9463; }
}

.rank-cover {
  flex-shrink: 0;
  width: 52px;
  height: 70px;
  border-radius: 7px;
  overflow: hidden;
  background-color: hsl(var(--muted));

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.rank-info {
  flex: 1;
  min-width: 0;
}

.rank-title {
  font-size: 15px;
  font-weight: 600;
  color: hsl(var(--foreground));
  transition: color 0.2s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-meta {
  display: flex;
  gap: 10px;
  margin-top: 5px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}

.rank-remark {
  margin-top: 3px;
  font-size: 12px;
  color: hsl(var(--update));
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-side {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.rank-score {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  font-weight: 700;
  color: hsl(var(--score));
}

.rank-hits {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.rank-row--skeleton {
  pointer-events: none;
}

.skeleton-num {
  width: 30px;
  height: 24px;
  border-radius: 6px;
}

.skeleton-cover {
  width: 52px;
  height: 70px;
  border-radius: 7px;
}

.skeleton-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-line-lg {
  width: 46%;
  height: 16px;
  border-radius: 6px;
}

.skeleton-line-sm {
  width: 30%;
  height: 12px;
  border-radius: 6px;
}

@media (max-width: 640px) {
  .rank-row {
    gap: 10px;
    padding: 10px 12px;
  }

  .rank-num {
    width: 24px;
    font-size: 17px;
  }

  .rank-side {
    display: none;
  }
}
</style>

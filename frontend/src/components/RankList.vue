<script setup>
import { onMounted, ref } from 'vue'
import { Flame, RefreshCw } from 'lucide-vue-next'
import { getRankVideos, getRanks } from '@/api/home'
import { formatCount } from '@/utils/format'

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
    videos.value = Array.isArray(data?.list) ? data.list : []
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
    ranks.value = Array.isArray(data?.list) ? data.list : []
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
  <section id="ranking" class="ranking surface">
    <header class="head">
      <h2 class="section-title">排行榜</h2>
      <span class="head-note"><Flame :size="14" /> 热门榜单</span>
    </header>

    <div v-if="ranks.length" class="tabs">
      <button
        v-for="rank in ranks"
        :key="rank.id"
        type="button"
        class="tab"
        :class="{ 'is-active': rank.id === activeId }"
        :aria-pressed="rank.id === activeId"
        @click="selectRank(rank.id)"
      >
        {{ rank.name }}
      </button>
    </div>

    <div v-if="loading" class="list">
      <div v-for="n in 8" :key="n" class="row row-skeleton">
        <span class="skeleton skeleton-rank"></span>
        <span class="skeleton skeleton-thumb"></span>
        <span class="skeleton skeleton-line"></span>
      </div>
    </div>

    <div v-else-if="error" class="empty">
      <p>{{ error }}</p>
      <button type="button" class="btn-ghost" @click="retry">
        <RefreshCw :size="14" /> 重试
      </button>
    </div>

    <div v-else-if="videos.length" class="list">
      <RouterLink
        v-for="(video, i) in videos"
        :key="video.video_id"
        :to="`/anime/${video.video_id}`"
        class="row"
      >
        <span class="rank-num" :class="{ 'is-top': i < 3 }">{{ i + 1 }}</span>
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
            <span v-if="video.score" class="row-score">{{ video.score }} 分</span>
            <span>{{ formatCount(video.hits) }} 次播放</span>
          </p>
        </div>
      </RouterLink>
    </div>

    <div v-else class="empty">
      <p>该榜单暂无数据</p>
    </div>
  </section>
</template>

<style scoped lang="scss">
.ranking {
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
  gap: 4px;
  margin-bottom: 10px;
}

.tab {
  height: 26px;
  padding: 0 12px;
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
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 268px;
  overflow-y: auto;
  padding-right: 4px;
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
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

.rank-num {
  flex-shrink: 0;
  width: 18px;
  font-size: 13px;
  font-weight: 700;
  text-align: center;
  color: hsl(var(--muted-foreground));

  &.is-top {
    color: hsl(var(--brand));
  }
}

.thumb {
  flex-shrink: 0;
  width: 36px;
  height: 48px;
  border-radius: 5px;
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
  gap: 8px;
  margin-top: 3px;
  font-size: 11.5px;
  color: hsl(var(--muted-foreground));
}

.row-score {
  color: hsl(var(--score));
}

.row-skeleton {
  pointer-events: none;
}

.skeleton-rank,
.skeleton-thumb,
.skeleton-line {
  display: block;
  border-radius: 5px;
}

.skeleton-rank {
  width: 18px;
  height: 18px;
}

.skeleton-thumb {
  width: 36px;
  height: 48px;
}

.skeleton-line {
  flex: 1;
  height: 14px;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 180px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}
</style>

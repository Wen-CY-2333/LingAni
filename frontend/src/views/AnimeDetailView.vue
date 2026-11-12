<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Heart, ListVideo, Loader2, Play, RefreshCw } from 'lucide-vue-next'
import { getRecommendations, getSections, getVideoDetail } from '@/api/anime'
import { addFavorite, checkFavorite, deleteFavorite, getVideoHistory } from '@/api/backend'
import { isLoggedIn } from '@/stores/auth'
import { formatCount, formatScore, plainDescription, remarkText } from '@/utils/format'
import { showError, showSuccess } from '@/utils/toast'
import EpisodeList from '@/components/EpisodeList.vue'
import RecommendationList from '@/components/RecommendationList.vue'
import CommentSection from '@/components/CommentSection.vue'

const route = useRoute()
const router = useRouter()

const video = ref(null)
const sections = ref([])
const recommendations = ref([])
const loading = ref(true)
const error = ref('')
const expanded = ref(false)
const favorited = ref(false)
const favoriteId = ref(null)
const favoriteLoading = ref(false)
const favoriteBusy = ref(false)
const watchedHistory = ref({})
let requestId = 0

const videoId = computed(() => route.params.id)

const score = computed(() => formatScore(video.value?.score))
const tags = computed(() => {
  const list = Array.isArray(video.value?.tags) ? video.value.tags : []
  const categories = Array.isArray(video.value?.categories) ? video.value.categories : []
  return [...categories, ...list].filter((tag, i, arr) => typeof tag === 'string' && arr.indexOf(tag) === i).slice(0, 12)
})

const description = computed(() => plainDescription(video.value?.description))
const needExpand = computed(() => description.value.length > 180)

const firstSection = computed(() => sections.value[0] || null)
const playLink = computed(() =>
  firstSection.value ? `/anime/${videoId.value}/play/${firstSection.value.id}` : `/anime/${videoId.value}`
)

const groupTitle = computed(() => video.value?.play_from?.[0]?.title || 'CYC_Main')
const remark = computed(() => remarkText(video.value?.remarks))

async function loadDetail() {
  const current = ++requestId
  loading.value = true
  error.value = ''
  video.value = null
  sections.value = []
  recommendations.value = []
  expanded.value = false
  favorited.value = false
  favoriteId.value = null
  watchedHistory.value = {}

  try {
    const detail = await getVideoDetail(videoId.value)
    if (current !== requestId) return
    video.value = detail
    loadFavorite(current)
    loadWatchedHistory(current)
  } catch (err) {
    if (current !== requestId) return
    error.value = err.message || '番剧详情加载失败'
    loading.value = false
    return
  }

  const [sectionResult, recommendResult] = await Promise.allSettled([
    getSections(videoId.value),
    getRecommendations(videoId.value, 12)
  ])

  if (current !== requestId) return

  sections.value = sectionResult.status === 'fulfilled' && Array.isArray(sectionResult.value?.list)
    ? sectionResult.value.list
    : []
  recommendations.value = recommendResult.status === 'fulfilled' && Array.isArray(recommendResult.value?.list)
    ? recommendResult.value.list
    : []
  loading.value = false
}

watch(videoId, loadDetail, { immediate: true })

async function loadFavorite(current) {
  if (!isLoggedIn.value) return
  favoriteLoading.value = true
  try {
    const data = await checkFavorite({ videoId: videoId.value })
    if (current !== requestId) return
    favorited.value = Boolean(data?.favorited)
    favoriteId.value = data?.id || null
  } catch {
    // 追番状态失败不影响详情页浏览，切换追番时会再次提示
  } finally {
    if (current === requestId) favoriteLoading.value = false
  }
}

async function loadWatchedHistory(current) {
  if (!isLoggedIn.value) return
  try {
    const list = await getVideoHistory(videoId.value)
    if (current !== requestId) return
    const map = {}
    ;(Array.isArray(list) ? list : []).forEach((item) => {
      map[String(item.sectionId)] = item
    })
    watchedHistory.value = map
  } catch {
    // 观看记录只是辅助标记，失败时不影响详情页浏览
  }
}

async function toggleFavorite() {
  if (!isLoggedIn.value) {
    showError(new Error('请先登录后再追番'))
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (favoriteBusy.value || favoriteLoading.value) return

  favoriteBusy.value = true
  try {
    if (favorited.value && favoriteId.value) {
      await deleteFavorite(favoriteId.value)
      favorited.value = false
      favoriteId.value = null
      showSuccess('已取消追番')
    } else {
      const data = await addFavorite({
        videoId: videoId.value,
        animeTitle: video.value?.title || '',
        animeCover: video.value?.cover_url || ''
      })
      favorited.value = true
      favoriteId.value = data?.id || null
      showSuccess('已加入追番')
    }
  } catch (err) {
    showError(err, favorited.value ? '取消追番失败' : '追番失败')
  } finally {
    favoriteBusy.value = false
  }
}

function scrollToEpisodes() {
  document.getElementById('episodes')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="detail">
    <div v-if="loading" class="container detail-body">
      <div class="hero">
        <span class="skeleton hero-cover"></span>
        <div class="hero-info">
          <span class="skeleton line line-short"></span>
          <span class="skeleton line line-title"></span>
          <span class="skeleton line line-sub"></span>
          <span class="skeleton line line-mid"></span>
          <span class="skeleton line line-mid"></span>
        </div>
      </div>
      <div class="block skeleton skeleton-block"></div>
    </div>

    <div v-else-if="error" class="container detail-body">
      <div class="state-box">
        <p class="state-box__title">番剧详情加载失败</p>
        <p class="state-box__hint">{{ error }}</p>
        <div class="state-box__actions">
          <button type="button" class="btn-ghost" @click="loadDetail">
            <RefreshCw :size="14" /> 重试
          </button>
          <RouterLink to="/" class="btn-ghost">返回首页</RouterLink>
        </div>
      </div>
    </div>

    <div v-else-if="video" class="container detail-body">
      <div class="hero">
        <img
          :src="video.cover_url || '/images/cover-placeholder.svg'"
          :alt="video.title"
          class="hero-cover"
          @error="(event) => (event.target.src = '/images/cover-placeholder.svg')"
        />
        <div class="hero-info">
          <p class="eyebrow">番剧详情</p>
          <h1 class="title">{{ video.title }}</h1>
          <p v-if="video.subtitle" class="subtitle">{{ video.subtitle }}</p>

          <div v-if="tags.length" class="tags">
            <span v-for="tag in tags" :key="tag" class="chip">{{ tag }}</span>
          </div>

          <div class="meta-row">
            <span v-if="score" class="score-pill">
              <span class="score-dot"></span>
              评分
              <strong>{{ score }}</strong>
            </span>
            <span v-if="remark" class="meta-text">{{ remark }}</span>
            <span class="meta-text">{{ formatCount(video.hits) }} 次播放</span>
          </div>

          <div class="actions">
            <RouterLink :to="playLink" class="btn-brand" :class="{ 'is-disabled': !firstSection }">
              <Play :size="15" />
              {{ firstSection ? '开始观看' : '暂无章节' }}
            </RouterLink>
            <button
              type="button"
              class="btn-ghost favorite-btn"
              :class="{ 'is-active': favorited }"
              :disabled="favoriteBusy || favoriteLoading"
              @click="toggleFavorite"
            >
              <Loader2 v-if="favoriteBusy || favoriteLoading" :size="15" class="spin" />
              <Heart v-else :size="15" :fill="favorited ? 'currentColor' : 'none'" />
              {{ favorited ? '已追番' : '加入追番' }}
            </button>
            <button type="button" class="btn-ghost" @click="scrollToEpisodes">
              <ListVideo :size="15" />
              查看选集
            </button>
          </div>
        </div>
      </div>

      <section class="block">
        <div class="section-head">
          <h2 class="section-title">简介</h2>
        </div>
        <p v-if="description" class="description" :class="{ 'is-collapsed': !expanded && needExpand }">
          {{ description }}
        </p>
        <p v-else class="description muted">暂无简介</p>
        <button v-if="needExpand" type="button" class="expand-btn" @click="expanded = !expanded">
          {{ expanded ? '收起' : '展开' }}
        </button>
      </section>

      <section id="episodes" class="block">
        <EpisodeList
          :sections="sections"
          :video-id="videoId"
          section-id=""
          :group-title="groupTitle"
          :history="watchedHistory"
        />
      </section>

      <section class="block">
        <CommentSection :video-id="videoId" />
      </section>

      <section class="block">
        <RecommendationList :videos="recommendations" :exclude-id="videoId" />
      </section>
    </div>

    <div v-else class="container detail-body">
      <div class="state-box">
        <p class="state-box__title">番剧不存在</p>
        <p class="state-box__hint">该番剧可能已下架或 ID 不正确。</p>
        <div class="state-box__actions">
          <RouterLink to="/" class="btn-ghost">返回首页</RouterLink>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.detail-body {
  padding-top: 32px;
  padding-bottom: 12px;
}

.hero {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 26px;
  align-items: start;

  @media (max-width: 640px) {
    grid-template-columns: 130px minmax(0, 1fr);
    gap: 16px;
  }
}

.hero-cover {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  border-radius: 8px;
  background-color: hsl(var(--muted));
}

.hero-info {
  min-width: 0;
  padding-top: 2px;
}

.eyebrow {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 2px;
  color: hsl(var(--brand));
}

.title {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.3;
  color: hsl(var(--foreground));

  @media (max-width: 640px) {
    font-size: 20px;
  }
}

.subtitle {
  margin-top: 8px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  margin-top: 16px;
}

.score-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background-color: hsl(var(--muted));
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));

  strong {
    color: hsl(var(--brand));
    font-size: 14px;
  }

  svg {
    color: hsl(var(--score));
  }
}

.score-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: hsl(var(--brand));
}

.meta-text {
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 22px;
}

.btn-brand.is-disabled {
  opacity: 0.5;
  pointer-events: none;
}

.favorite-btn.is-active {
  border-color: hsl(var(--brand) / 0.55);
  color: hsl(var(--brand));
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.block {
  margin-top: 36px;
  padding-top: 28px;
  border-top: 1px solid hsl(var(--border));
}

.description {
  font-size: 14px;
  line-height: 1.85;
  color: hsl(var(--muted-foreground));
  white-space: pre-line;

  &.is-collapsed {
    display: -webkit-box;
    -webkit-line-clamp: 4;
    line-clamp: 4;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &.muted {
    color: hsl(var(--muted-foreground) / 0.7);
  }
}

.expand-btn {
  margin-top: 10px;
  font-size: 13px;
  color: hsl(var(--brand));

  &:hover {
    text-decoration: underline;
  }
}

.hero-cover.skeleton {
  height: 267px;
}

.line {
  display: block;
  height: 16px;
  margin-bottom: 12px;
  border-radius: 6px;
}

.line-short {
  width: 60px;
  height: 12px;
}

.line-title {
  width: 45%;
  height: 26px;
}

.line-sub {
  width: 70%;
}

.line-mid {
  width: 55%;
}

.skeleton-block {
  display: block;
  height: 140px;
  margin-top: 36px;
  border-radius: 12px;
}
</style>

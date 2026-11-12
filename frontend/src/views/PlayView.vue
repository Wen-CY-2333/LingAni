<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft, Loader2, MessageSquarePlus, RefreshCw, Send, Star, Trash2 } from 'lucide-vue-next'
import { getRecommendations, getSections, getVideoDetail } from '@/api/anime'
import { getPlayUrl } from '@/api/section'
import { deleteDanmaku, getDanmaku, getVideoHistory, saveHistory, sendDanmaku } from '@/api/backend'
import { authState, isLoggedIn } from '@/stores/auth'
import { toDanmakuItems, hexToInt } from '@/utils/danmaku'
import { formatCount, formatScore, plainDescription, remarkText } from '@/utils/format'
import { formatDuration } from '@/utils/datetime'
import { showError, showSuccess } from '@/utils/toast'
import VideoPlayer from '@/components/VideoPlayer.vue'
import EpisodeList from '@/components/EpisodeList.vue'
import RecommendationList from '@/components/RecommendationList.vue'
import CommentSection from '@/components/CommentSection.vue'

const route = useRoute()
const router = useRouter()

const video = ref(null)
const sections = ref([])
const recommendations = ref([])
const loadingVideo = ref(true)
const videoError = ref('')

const playUrl = ref('')
const loadingUrl = ref(true)
const urlError = ref('')
const playerKey = ref(0)

const danmakuRaw = ref([])
const danmakuError = ref('')
const danmakuItems = computed(() => toDanmakuItems(danmakuRaw.value))
const danmakuText = ref('')
const danmakuColor = ref('#ffffff')
const danmakuMode = ref(0)
const sendingDanmaku = ref(false)
const showMyDanmaku = ref(false)

const colorOptions = ['#ffffff', '#ff6b81', '#ffd166', '#4dd4ff', '#a78bfa']
const modeOptions = [
  { value: 0, label: '滚动' },
  { value: 1, label: '顶部' },
  { value: 2, label: '底部' }
]

const playback = ref({ time: 0, duration: 0 })
const progressSnapshot = ref({ sectionId: '', episodeTitle: '', time: 0, duration: 0 })
let historyTimer = 0
let historySaving = false

const videoId = computed(() => route.params.videoId)
const sectionId = computed(() => route.params.sectionId)
const sectionHistory = ref({})
const historyLoaded = ref(false)

// 续播位置：优先使用 URL 上的 ?t=，否则取当前章节的历史进度
const resumeTime = computed(() => {
  const fromQuery = Number(route.query.t)
  if (Number.isFinite(fromQuery) && fromQuery > 0) return fromQuery

  const record = sectionHistory.value[String(sectionId.value)]
  if (!record) return 0
  const duration = Number(record.duration) || 0
  const progress = Number(record.progress) || 0
  // 已经看完的章节从头播放
  if (progress < 1 || (duration > 0 && progress + 5 >= duration)) return 0
  return progress
})

const currentSection = computed(
  () => sections.value.find((item) => String(item.id) === String(sectionId.value)) || null
)
const groupTitle = computed(() => video.value?.play_from?.[0]?.title || 'CYC_Main')
const score = computed(() => formatScore(video.value?.score))
const remark = computed(() => remarkText(video.value?.remarks))
const description = computed(() => plainDescription(video.value?.description))
const myDanmaku = computed(() => {
  if (!isLoggedIn.value) return []
  const uid = authState.user?.id
  return danmakuRaw.value
    .filter((item) => item.userId === uid)
    .slice()
    .sort((a, b) => Number(a.time) - Number(b.time))
})

let videoSeq = 0
let sectionSeq = 0
let historySeq = 0

async function loadHistory() {
  const current = ++historySeq
  sectionHistory.value = {}
  historyLoaded.value = false
  if (!isLoggedIn.value) {
    historyLoaded.value = true
    return
  }
  try {
    const list = await getVideoHistory(videoId.value)
    if (current !== historySeq) return
    const map = {}
    ;(Array.isArray(list) ? list : []).forEach((item) => {
      map[String(item.sectionId)] = item
    })
    sectionHistory.value = map
  } catch {
    // 续播信息加载失败时按从头播放处理
  } finally {
    if (current === historySeq) historyLoaded.value = true
  }
}

async function loadVideo() {
  const current = ++videoSeq
  loadingVideo.value = true
  videoError.value = ''
  video.value = null
  sections.value = []
  recommendations.value = []

  const [detailResult, sectionResult, recommendResult] = await Promise.allSettled([
    getVideoDetail(videoId.value),
    getSections(videoId.value),
    getRecommendations(videoId.value, 12)
  ])

  if (current !== videoSeq) return

  if (detailResult.status === 'fulfilled') {
    video.value = detailResult.value
  } else {
    videoError.value = detailResult.reason?.message || '番剧信息加载失败'
  }

  sections.value = sectionResult.status === 'fulfilled' && Array.isArray(sectionResult.value?.list)
    ? sectionResult.value.list
    : []
  recommendations.value = recommendResult.status === 'fulfilled' && Array.isArray(recommendResult.value?.list)
    ? recommendResult.value.list
    : []
  loadingVideo.value = false
}

async function loadSection() {
  const current = ++sectionSeq
  playUrl.value = ''
  urlError.value = ''
  danmakuError.value = ''
  danmakuRaw.value = []
  loadingUrl.value = true

  const sid = sectionId.value
  const [urlResult, danmakuResult] = await Promise.allSettled([getPlayUrl(sid), getDanmaku(sid)])

  if (current !== sectionSeq) return

  if (urlResult.status === 'fulfilled' && urlResult.value?.url) {
    playUrl.value = urlResult.value.url
  } else {
    urlError.value = urlResult.status === 'fulfilled'
      ? '接口未返回可用的视频直链'
      : urlResult.reason?.message || '视频直链获取失败'
  }

  if (danmakuResult.status === 'fulfilled') {
    danmakuRaw.value = Array.isArray(danmakuResult.value) ? danmakuResult.value : []
  } else {
    danmakuError.value = '弹幕加载失败，不影响视频播放'
  }

  loadingUrl.value = false
}

watch(
  videoId,
  () => {
    loadVideo()
    loadHistory()
  },
  { immediate: true }
)

watch(
  sectionId,
  (newId, oldId) => {
    if (oldId) {
      flushHistory()
    }
    playback.value = { time: 0, duration: 0 }
    progressSnapshot.value = { videoId: '', sectionId: '', episodeTitle: '', time: 0, duration: 0 }
    loadSection()
  },
  { immediate: true }
)

function onProgress(data) {
  playback.value = {
    time: Number(data?.time) || 0,
    duration: Number(data?.duration) || 0
  }
  progressSnapshot.value = {
    // 记录当时的番剧 ID：卸载时路由参数可能已变成新页面，不能用 route.params 取值
    videoId: String(videoId.value || ''),
    sectionId: String(sectionId.value || ''),
    episodeTitle: currentSection.value?.title || '',
    time: playback.value.time,
    duration: playback.value.duration
  }
}

async function persistHistory(snapshot) {
  if (!isLoggedIn.value || !video.value || !snapshot?.sectionId || !snapshot?.videoId) return
  if (snapshot.time < 1 || snapshot.duration <= 1) return
  if (historySaving) return

  historySaving = true
  try {
    const saved = {
      progress: Math.floor(snapshot.time),
      duration: Math.floor(snapshot.duration)
    }
    await saveHistory({
      videoId: String(snapshot.videoId),
      sectionId: String(snapshot.sectionId),
      animeTitle: video.value.title || '',
      animeCover: video.value.cover_url || '',
      episodeTitle: snapshot.episodeTitle || '',
      progress: saved.progress,
      duration: saved.duration
    })
    // 同步本地缓存，切回同一集时能续播，选集按钮进度也会实时更新
    sectionHistory.value = {
      ...sectionHistory.value,
      [String(snapshot.sectionId)]: saved
    }
  } catch {
    // 进度保存属于后台行为，失败时不打断播放，也不覆盖正在观看的内容
  } finally {
    historySaving = false
  }
}

function flushHistory() {
  const snapshot = { ...progressSnapshot.value }
  if (!snapshot.sectionId) return
  persistHistory(snapshot)
}

function onPlaybackEnded() {
  flushHistory()
}

function retryPlayUrl() {
  playerKey.value += 1
  loadSection()
}

async function submitDanmaku() {
  if (!isLoggedIn.value) {
    showError(new Error('请先登录后再发送弹幕'))
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  const text = danmakuText.value.trim()
  if (!text) {
    showError(new Error('弹幕内容不能为空'))
    return
  }
  if (text.length > 200) {
    showError(new Error('弹幕最多 200 个字符'))
    return
  }

  sendingDanmaku.value = true
  try {
    const data = await sendDanmaku({
      sectionId: String(sectionId.value),
      time: Number((playback.value.time || 0).toFixed(2)),
      text,
      color: hexToInt(danmakuColor.value),
      mode: Number(danmakuMode.value)
    })
    danmakuRaw.value = [...danmakuRaw.value, data]
    danmakuText.value = ''
    showSuccess('弹幕已发送')
  } catch (error) {
    showError(error, '弹幕发送失败')
  } finally {
    sendingDanmaku.value = false
  }
}

async function removeMyDanmaku(item) {
  if (!window.confirm('确定删除这条弹幕吗？')) return
  try {
    await deleteDanmaku(item.id)
    danmakuRaw.value = danmakuRaw.value.filter((danmaku) => danmaku.id !== item.id)
    showSuccess('弹幕已删除')
  } catch (error) {
    showError(error, '弹幕删除失败')
  }
}

function modeLabel(mode) {
  return modeOptions.find((item) => item.value === Number(mode))?.label || '滚动'
}

function onPageHide() {
  flushHistory()
}

onMounted(() => {
  historyTimer = window.setInterval(() => {
    if (progressSnapshot.value.sectionId && playback.value.time > 1) {
      persistHistory({ ...progressSnapshot.value })
    }
  }, 15000)
  window.addEventListener('pagehide', onPageHide)
})

onBeforeUnmount(() => {
  if (historyTimer) window.clearInterval(historyTimer)
  window.removeEventListener('pagehide', onPageHide)
  flushHistory()
})
</script>

<template>
  <div class="play">
    <div class="container play-body">
      <nav class="breadcrumb" aria-label="面包屑">
        <RouterLink :to="`/anime/${videoId}`" class="back-link">
          <ChevronLeft :size="15" />
          {{ video?.title || '返回详情' }}
        </RouterLink>
        <span class="separator">/</span>
        <span>{{ currentSection?.title || `第 ${sectionId} 集` }}</span>
      </nav>

      <h1 class="play-title">
        {{ video?.title || '番剧播放' }}
        <span v-if="currentSection" class="play-episode">{{ currentSection.title }}</span>
      </h1>

      <div class="play-grid">
        <div class="play-main">
          <div class="player-shell">
            <VideoPlayer
              v-if="playUrl && historyLoaded"
              :key="playerKey"
              :url="playUrl"
              :poster="video?.cover_url || ''"
              :title="`${video?.title || ''} ${currentSection?.title || ''}`.trim()"
              :danmaku="danmakuItems"
              :initial-time="resumeTime"
              @retry="retryPlayUrl"
              @progress="onProgress"
              @ended="onPlaybackEnded"
            />

            <div v-else-if="loadingUrl || !historyLoaded" class="player-state">
              <span class="spinner"></span>
              <p>正在获取第 {{ currentSection?.title || sectionId }} 的播放地址…</p>
            </div>

            <div v-else class="player-state player-state--error">
              <p class="state-title">播放地址获取失败</p>
              <p class="state-hint">{{ urlError }}</p>
              <button type="button" class="btn-brand" @click="retryPlayUrl">
                <RefreshCw :size="14" /> 重新获取
              </button>
            </div>
          </div>

          <p v-if="danmakuError" class="danmaku-hint">{{ danmakuError }}</p>

          <!-- 弹幕发送与个人弹幕管理 -->
          <section class="danmaku-tools surface">
            <form v-if="isLoggedIn" class="danmaku-form" @submit.prevent="submitDanmaku">
              <div class="danmaku-input-row">
                <MessageSquarePlus :size="16" class="input-icon" />
                <input
                  v-model="danmakuText"
                  type="text"
                  maxlength="200"
                  placeholder="发一条友善的弹幕…"
                  autocomplete="off"
                />
                <span class="danmaku-time">{{ formatDuration(playback.time) }}</span>
                <button type="submit" class="btn-brand danmaku-send" :disabled="sendingDanmaku || !danmakuText.trim()">
                  <Loader2 v-if="sendingDanmaku" :size="14" class="spin" />
                  <Send v-else :size="14" />
                  发送
                </button>
              </div>

              <div class="danmaku-options">
                <span class="option-label">颜色</span>
                <button
                  v-for="color in colorOptions"
                  :key="color"
                  type="button"
                  class="color-dot"
                  :class="{ 'is-active': danmakuColor === color }"
                  :style="{ backgroundColor: color }"
                  :aria-label="`选择颜色 ${color}`"
                  @click="danmakuColor = color"
                ></button>
                <span class="option-label">模式</span>
                <button
                  v-for="mode in modeOptions"
                  :key="mode.value"
                  type="button"
                  class="mode-btn"
                  :class="{ 'is-active': danmakuMode === mode.value }"
                  @click="danmakuMode = mode.value"
                >
                  {{ mode.label }}
                </button>
                <span class="danmaku-count">{{ danmakuText.length }}/200</span>
                <button
                  v-if="myDanmaku.length"
                  type="button"
                  class="my-toggle"
                  @click="showMyDanmaku = !showMyDanmaku"
                >
                  {{ showMyDanmaku ? '收起我的弹幕' : `我的弹幕（${myDanmaku.length}）` }}
                </button>
              </div>
            </form>

            <RouterLink
              v-else
              class="danmaku-login"
              :to="{ path: '/login', query: { redirect: route.fullPath } }"
            >
              <MessageSquarePlus :size="16" />
              <span>登录后即可发送弹幕、管理自己的弹幕</span>
              <span class="danmaku-login__action">去登录</span>
            </RouterLink>

            <div v-if="isLoggedIn && showMyDanmaku" class="my-danmaku">
              <p v-if="!myDanmaku.length" class="my-empty">当前章节还没有你发送的弹幕。</p>
              <ul v-else class="my-list">
                <li v-for="item in myDanmaku" :key="item.id" class="my-item">
                  <span class="my-time">{{ formatDuration(item.time) }}</span>
                  <span class="my-mode">{{ modeLabel(item.mode) }}</span>
                  <span class="my-text">{{ item.text }}</span>
                  <button type="button" class="my-delete" @click="removeMyDanmaku(item)">
                    <Trash2 :size="13" /> 删除
                  </button>
                </li>
              </ul>
            </div>
          </section>

          <div class="meta-bar">
            <span v-if="score" class="meta-score">
              <Star :size="13" />
              {{ score }} 分
            </span>
            <span v-if="remark" class="meta-item">{{ remark }}</span>
            <span v-if="video?.total" class="meta-item">共 {{ video.total }} 集</span>
            <span class="meta-item">{{ formatCount(video?.hits) }} 次播放</span>
            <span class="meta-item meta-danmaku">弹幕 {{ danmakuRaw.length }} 条</span>
          </div>

          <section v-if="description" class="info-card surface">
            <h2 class="section-title">简介</h2>
            <p class="info-text">{{ description }}</p>
          </section>

          <CommentSection :key="`comments-${videoId}`" class="play-comments" :video-id="videoId" />
        </div>

        <aside class="play-side surface">
          <div v-if="loadingVideo && !sections.length" class="side-loading">
            <span class="skeleton side-line"></span>
            <span class="skeleton side-line"></span>
            <span class="skeleton side-line"></span>
          </div>
          <EpisodeList
            v-else
            dense
            :sections="sections"
            :video-id="videoId"
            :section-id="sectionId"
            :group-title="groupTitle"
            :history="sectionHistory"
          />
        </aside>
      </div>

      <RecommendationList
        v-if="recommendations.length"
        class="play-recommend"
        :videos="recommendations"
        :exclude-id="videoId"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.play-body {
  padding-top: 22px;
  padding-bottom: 12px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: hsl(var(--muted-foreground));
  transition: color 0.2s ease;

  &:hover {
    color: hsl(var(--brand));
  }
}

.separator {
  color: hsl(var(--border));
}

.play-title {
  margin-top: 10px;
  font-size: 20px;
  font-weight: 700;
  color: hsl(var(--foreground));
}

.play-episode {
  margin-left: 8px;
  font-size: 14px;
  font-weight: 500;
  color: hsl(var(--brand));
}

.play-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 20px;
  margin-top: 16px;
  align-items: start;

  @media (max-width: 1100px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.play-main {
  min-width: 0;
}

.player-shell {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  background-color: #050505;
}

.player-state {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  text-align: center;
}

.player-state--error {
  .state-title {
    font-size: 15px;
    font-weight: 600;
    color: hsl(var(--foreground));
  }

  .state-hint {
    max-width: 520px;
    line-height: 1.7;
  }
}

.spinner {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.16);
  border-top-color: hsl(var(--brand));
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.spin {
  animation: spin 0.9s linear infinite;
}

.danmaku-hint {
  margin-top: 8px;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

/* 弹幕发送区 */
.danmaku-tools {
  margin-top: 12px;
  padding: 12px 14px;
}

.danmaku-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.danmaku-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 6px 0 12px;
  border-radius: 8px;
  border: 1px solid hsl(var(--border));
  background-color: hsl(var(--muted) / 0.55);
}

.input-icon {
  color: hsl(var(--muted-foreground));
  flex-shrink: 0;
}

.danmaku-input-row input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: 0;
  background: transparent;
  outline: 0;
  color: hsl(var(--foreground));
  font-size: 13.5px;

  &::placeholder {
    color: hsl(var(--muted-foreground));
  }
}

.danmaku-time {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  font-variant-numeric: tabular-nums;
}

.danmaku-send {
  height: 30px;
  padding: 0 14px;
  font-size: 13px;
  flex-shrink: 0;
}

.danmaku-options {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.option-label {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.color-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid transparent;
  box-shadow: 0 0 0 1px hsl(var(--border));
  transition: transform 0.2s ease, border-color 0.2s ease;

  &.is-active {
    border-color: hsl(var(--foreground));
    transform: scale(1.1);
  }
}

.mode-btn {
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid hsl(var(--border));
  color: hsl(var(--muted-foreground));
  font-size: 12px;

  &.is-active {
    border-color: hsl(var(--brand));
    color: hsl(var(--brand));
    background-color: hsl(var(--brand) / 0.08);
  }
}

.danmaku-count {
  margin-left: auto;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.my-toggle {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  text-decoration: underline;
  text-underline-offset: 3px;

  &:hover {
    color: hsl(var(--brand));
  }
}

.danmaku-login {
  display: flex;
  align-items: center;
  gap: 8px;
  color: hsl(var(--muted-foreground));
  font-size: 13px;

  &:hover {
    color: hsl(var(--foreground));
  }
}

.danmaku-login__action {
  margin-left: auto;
  color: hsl(var(--brand));
  font-weight: 600;
}

.my-danmaku {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed hsl(var(--border));
}

.my-empty {
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}

.my-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 220px;
  overflow-y: auto;
}

.my-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
}

.my-time {
  color: hsl(var(--brand));
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.my-mode {
  font-size: 11px;
  color: hsl(var(--muted-foreground));
  border: 1px solid hsl(var(--border));
  border-radius: 4px;
  padding: 1px 5px;
  flex-shrink: 0;
}

.my-text {
  flex: 1;
  min-width: 0;
  color: hsl(var(--foreground) / 0.9);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-delete {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: hsl(var(--muted-foreground));
  flex-shrink: 0;

  &:hover {
    color: hsl(var(--brand));
  }
}

.meta-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  background-color: hsl(var(--card));
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}

.meta-score {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: hsl(var(--score));
  font-weight: 600;

  svg {
    fill: hsl(var(--score));
  }
}

.meta-danmaku {
  margin-left: auto;
}

.info-card {
  margin-top: 16px;
  padding: 18px 20px 20px;
}

.info-text {
  margin-top: 12px;
  font-size: 13.5px;
  line-height: 1.85;
  color: hsl(var(--muted-foreground));
  white-space: pre-line;
  display: -webkit-box;
  -webkit-line-clamp: 6;
  line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.play-comments {
  margin-top: 16px;
}

.play-side {
  position: sticky;
  top: calc(var(--header-height) + 16px);
  padding: 16px;
}

.side-loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-line {
  display: block;
  height: 42px;
  border-radius: 6px;
}

.play-recommend {
  margin-top: 40px;
  padding-top: 28px;
  border-top: 1px solid hsl(var(--border));
}

@media (max-width: 1100px) {
  .play-side {
    position: static;
  }

  .meta-danmaku {
    margin-left: 0;
  }
}

@media (max-width: 640px) {
  .danmaku-input-row {
    flex-wrap: wrap;
    height: auto;
    padding: 8px 10px;
  }

  .danmaku-input-row input {
    flex-basis: calc(100% - 26px);
    height: 32px;
  }

  .danmaku-time,
  .danmaku-send {
    margin-left: auto;
  }

  .danmaku-count {
    margin-left: 0;
  }
}
</style>

<script setup>
import { onMounted, ref } from 'vue'
import { History, Loader2, Play, RefreshCw, Trash2 } from 'lucide-vue-next'
import { clearHistory, deleteHistory, getHistory } from '@/api/backend'
import { formatDateTime, formatDuration, progressPercent } from '@/utils/datetime'
import { showError, showSuccess } from '@/utils/toast'
import Pagination from '@/components/Pagination.vue'

const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(true)
const error = ref('')
const deletingId = ref(null)
const clearing = ref(false)

async function load(current = page.value) {
  loading.value = true
  error.value = ''
  try {
    const data = await getHistory({ current, size: pageSize })
    records.value = Array.isArray(data?.records) ? data.records : []
    total.value = Number(data?.total) || 0
    page.value = current
  } catch (err) {
    error.value = err.message || '播放历史加载失败'
  } finally {
    loading.value = false
  }
}

function continueLink(item) {
  const t = Number(item.progress) > 0 ? `?t=${Math.floor(Number(item.progress))}` : ''
  return `/anime/${item.videoId}/play/${item.sectionId}${t}`
}

async function remove(item) {
  if (!window.confirm('确定删除这条播放记录吗？')) return
  deletingId.value = item.id
  try {
    await deleteHistory(item.id)
    showSuccess('已删除播放记录')
    const nextPage = records.value.length === 1 && page.value > 1 ? page.value - 1 : page.value
    await load(nextPage)
  } catch (err) {
    showError(err, '删除播放记录失败')
  } finally {
    deletingId.value = null
  }
}

async function clearAll() {
  if (!window.confirm('确定清空全部播放历史吗？此操作不可恢复。')) return
  clearing.value = true
  try {
    await clearHistory()
    showSuccess('已清空播放历史')
    page.value = 1
    await load(1)
  } catch (err) {
    showError(err, '清空播放历史失败')
  } finally {
    clearing.value = false
  }
}

function changePage(next) {
  load(next)
}

onMounted(() => load(1))
</script>

<template>
  <div class="history-page container">
    <header class="page-head">
      <div>
        <p class="eyebrow">个人中心</p>
        <h1 class="page-title"><History :size="18" /> 播放历史</h1>
        <p class="page-hint">共 {{ total }} 条记录</p>
      </div>
      <button
        v-if="records.length"
        type="button"
        class="btn-ghost is-danger"
        :disabled="clearing"
        @click="clearAll"
      >
        <Loader2 v-if="clearing" :size="14" class="spin" />
        <Trash2 v-else :size="14" />
        清空历史
      </button>
    </header>

    <div v-if="loading" class="state-box">
      <span class="spinner"></span>
      <p>正在加载播放历史…</p>
    </div>

    <div v-else-if="error" class="state-box">
      <p class="state-box__title">播放历史加载失败</p>
      <p class="state-box__hint">{{ error }}</p>
      <div class="state-box__actions">
        <button type="button" class="btn-ghost" @click="load(page)">
          <RefreshCw :size="14" /> 重试
        </button>
      </div>
    </div>

    <div v-else-if="!records.length" class="state-box">
      <p class="state-box__title">还没有播放记录</p>
      <p class="state-box__hint">登录状态下观看视频，进度会自动保存在这里。</p>
      <div class="state-box__actions">
        <RouterLink to="/" class="btn-brand">去首页看看</RouterLink>
      </div>
    </div>

    <template v-else>
      <ul class="history-list">
        <li v-for="item in records" :key="item.id" class="history-item surface">
          <RouterLink :to="continueLink(item)" class="history-cover-link">
            <img
              :src="item.animeCover || '/images/cover-placeholder.svg'"
              :alt="item.animeTitle || '动画封面'"
              class="history-cover"
              @error="(event) => (event.target.src = '/images/cover-placeholder.svg')"
            />
            <span class="cover-play"><Play :size="16" /></span>
          </RouterLink>

          <div class="history-body">
            <RouterLink :to="continueLink(item)" class="history-title">
              {{ item.animeTitle || '未命名动画' }}
            </RouterLink>
            <p class="history-episode">{{ item.episodeTitle || `章节 ${item.sectionId}` }}</p>

            <div class="progress-row">
              <div class="progress-track">
                <span
                  class="progress-fill"
                  :style="{ width: `${progressPercent(item.progress, item.duration)}%` }"
                ></span>
              </div>
              <span class="progress-text">
                {{ formatDuration(item.progress) }} / {{ formatDuration(item.duration) }}
              </span>
            </div>

            <p class="history-meta">最近播放 {{ formatDateTime(item.lastPlayedAt) }}</p>
          </div>

          <div class="history-actions">
            <RouterLink :to="continueLink(item)" class="btn-brand">
              <Play :size="14" /> 继续观看
            </RouterLink>
            <button
              type="button"
              class="btn-ghost is-danger"
              :disabled="deletingId === item.id"
              @click="remove(item)"
            >
              <Loader2 v-if="deletingId === item.id" :size="13" class="spin" />
              <Trash2 v-else :size="13" />
              删除
            </button>
          </div>
        </li>
      </ul>

      <Pagination :page="page" :page-size="pageSize" :total="total" @change="changePage" />
    </template>
  </div>
</template>

<style scoped lang="scss">
.history-page {
  padding-top: 32px;
  padding-bottom: 12px;
}

.state-box {
  margin-top: 24px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-bottom: 18px;
  border-bottom: 1px solid hsl(var(--border));
}

.eyebrow {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 2px;
  color: hsl(var(--brand));
}

.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 24px;
  font-weight: 700;
  color: hsl(var(--foreground));

  svg {
    color: hsl(var(--brand));
  }
}

.page-hint {
  margin-top: 6px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

.is-danger:hover {
  border-color: hsl(var(--brand) / 0.6);
  color: hsl(var(--brand));
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 24px;
}

.history-item {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 12px 16px;

  @media (max-width: 720px) {
    grid-template-columns: 78px minmax(0, 1fr);
    align-items: start;
  }
}

.history-cover-link {
  position: relative;
  border-radius: 8px;
  overflow: hidden;

  @media (max-width: 720px) {
    grid-row: span 2;
  }
}

.history-cover {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  background-color: hsl(var(--muted));
}

.cover-play {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(0, 0, 0, 0.42);
  color: #fff;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.history-cover-link:hover .cover-play {
  opacity: 1;
}

.history-body {
  min-width: 0;
}

.history-title {
  font-size: 15px;
  font-weight: 600;
  color: hsl(var(--foreground));
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  &:hover {
    color: hsl(var(--brand));
  }
}

.history-episode {
  margin-top: 4px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

.progress-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.progress-track {
  flex: 1;
  max-width: 320px;
  height: 4px;
  border-radius: 2px;
  background-color: hsl(var(--border));
  overflow: hidden;
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: 2px;
  background-color: hsl(var(--brand));
}

.progress-text {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  white-space: nowrap;
}

.history-meta {
  margin-top: 6px;
  font-size: 12px;
  color: hsl(var(--muted-foreground) / 0.85);
}

.history-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;

  .btn-brand,
  .btn-ghost {
    height: 34px;
    padding: 0 14px;
    font-size: 13px;
    white-space: nowrap;
  }

  @media (max-width: 720px) {
    grid-column: 1 / -1;
    flex-direction: row;
    justify-content: flex-start;
  }
}

.spinner {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.16);
  border-top-color: hsl(var(--brand));
  animation: spin 0.9s linear infinite;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

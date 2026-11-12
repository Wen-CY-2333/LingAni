<script setup>
import { onMounted, ref } from 'vue'
import { Heart, Loader2, RefreshCw, Trash2 } from 'lucide-vue-next'
import { deleteFavorite, getFavorites } from '@/api/backend'
import { formatDateTime } from '@/utils/datetime'
import { showError, showSuccess } from '@/utils/toast'
import Pagination from '@/components/Pagination.vue'

const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const loading = ref(true)
const error = ref('')
const deletingId = ref(null)

async function load(current = page.value) {
  loading.value = true
  error.value = ''
  try {
    const data = await getFavorites({ current, size: pageSize })
    records.value = Array.isArray(data?.records) ? data.records : []
    total.value = Number(data?.total) || 0
    page.value = current
  } catch (err) {
    error.value = err.message || '追番列表加载失败'
  } finally {
    loading.value = false
  }
}

function detailLink(item) {
  return item.videoId ? `/anime/${item.videoId}` : '/'
}

async function remove(item) {
  if (!window.confirm(`确定取消追番「${item.animeTitle || '该动画'}」吗？`)) return
  deletingId.value = item.id
  try {
    await deleteFavorite(item.id)
    showSuccess('已取消追番')
    const nextPage = records.value.length === 1 && page.value > 1 ? page.value - 1 : page.value
    await load(nextPage)
  } catch (err) {
    showError(err, '取消追番失败')
  } finally {
    deletingId.value = null
  }
}

function changePage(next) {
  load(next)
}

onMounted(() => load(1))
</script>

<template>
  <div class="favorites-page container">
    <header class="page-head">
      <div>
        <p class="eyebrow">个人中心</p>
        <h1 class="page-title"><Heart :size="18" /> 我的追番</h1>
        <p class="page-hint">共 {{ total }} 部动画</p>
      </div>
      <RouterLink to="/" class="btn-ghost">继续发现动画</RouterLink>
    </header>

    <div v-if="loading" class="state-box">
      <span class="spinner"></span>
      <p>正在加载追番列表…</p>
    </div>

    <div v-else-if="error" class="state-box">
      <p class="state-box__title">追番列表加载失败</p>
      <p class="state-box__hint">{{ error }}</p>
      <div class="state-box__actions">
        <button type="button" class="btn-ghost" @click="load(page)">
          <RefreshCw :size="14" /> 重试
        </button>
      </div>
    </div>

    <div v-else-if="!records.length" class="state-box">
      <p class="state-box__title">还没有追番</p>
      <p class="state-box__hint">在动画详情页点击「加入追番」，就能在这里随时找到它。</p>
      <div class="state-box__actions">
        <RouterLink to="/" class="btn-brand">去首页看看</RouterLink>
      </div>
    </div>

    <template v-else>
      <div class="favorite-grid">
        <article v-for="item in records" :key="item.id" class="favorite-card surface">
          <RouterLink :to="detailLink(item)" class="cover-link">
            <img
              :src="item.animeCover || '/images/cover-placeholder.svg'"
              :alt="item.animeTitle || '动画封面'"
              class="cover"
              @error="(event) => (event.target.src = '/images/cover-placeholder.svg')"
            />
          </RouterLink>
          <div class="card-body">
            <RouterLink :to="detailLink(item)" class="card-title">{{ item.animeTitle || '未命名动画' }}</RouterLink>
            <p class="card-meta">收藏于 {{ formatDateTime(item.createdAt) }}</p>
            <div class="card-actions">
              <RouterLink :to="detailLink(item)" class="btn-ghost">查看详情</RouterLink>
              <button type="button" class="btn-ghost is-danger" :disabled="deletingId === item.id" @click="remove(item)">
                <Loader2 v-if="deletingId === item.id" :size="13" class="spin" />
                <Trash2 v-else :size="13" />
                取消追番
              </button>
            </div>
          </div>
        </article>
      </div>

      <Pagination :page="page" :page-size="pageSize" :total="total" @change="changePage" />
    </template>
  </div>
</template>

<style scoped lang="scss">
.favorites-page {
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

.favorite-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 18px;
  margin-top: 24px;
}

.favorite-card {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.cover-link {
  display: block;
}

.cover {
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
  background-color: hsl(var(--muted));
  transition: transform 0.25s ease;
}

.favorite-card:hover .cover {
  transform: scale(1.03);
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px 14px;
}

.card-title {
  font-size: 14.5px;
  font-weight: 600;
  color: hsl(var(--foreground));
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;

  &:hover {
    color: hsl(var(--brand));
  }
}

.card-meta {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;

  .btn-ghost {
    flex: 1 1 auto;
    height: 32px;
    padding: 0 10px;
    font-size: 12.5px;
    white-space: nowrap;
  }

  .is-danger {
    color: hsl(var(--muted-foreground));

    &:hover {
      border-color: hsl(var(--brand) / 0.6);
      background-color: hsl(var(--brand) / 0.1);
      color: hsl(var(--brand));
    }
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

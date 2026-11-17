<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RefreshCw, Search } from 'lucide-vue-next'
import { searchVideos } from '@/api/video'
import AnimeGrid from '@/components/AnimeGrid.vue'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 24

const keyword = ref(String(route.query.q || '').trim())
const inputValue = ref(keyword.value)
const page = ref(Math.max(1, Number(route.query.page) || 1))

const videos = ref([])
const pager = ref({ page: 1, page_size: PAGE_SIZE, total: 0 })
const loading = ref(false)
const error = ref('')
const searched = ref(false)
let requestId = 0

const totalPages = computed(() => Math.ceil(pager.value.total / pager.value.page_size))

function syncQuery() {
  const query = {}
  if (keyword.value) query.q = keyword.value
  if (page.value > 1) query.page = String(page.value)
  router.replace({ path: '/search', query })
}

async function load() {
  if (!keyword.value) {
    videos.value = []
    pager.value = { page: 1, page_size: PAGE_SIZE, total: 0 }
    searched.value = false
    return
  }
  const current = ++requestId
  loading.value = true
  error.value = ''
  searched.value = true
  try {
    const data = await searchVideos(keyword.value, { page: page.value, pageSize: PAGE_SIZE })
    if (current !== requestId) return
    videos.value = Array.isArray(data?.records) ? data.records : []
    pager.value = {
      page: data?.current ?? page.value,
      page_size: data?.size ?? PAGE_SIZE,
      total: data?.total ?? 0
    }
  } catch (err) {
    if (current !== requestId) return
    videos.value = []
    pager.value = { page: 1, page_size: PAGE_SIZE, total: 0 }
    error.value = err.message || '搜索失败'
  } finally {
    if (current === requestId) loading.value = false
  }
}

function submit() {
  const q = inputValue.value.trim()
  if (!q || q === keyword.value) {
    inputValue.value = keyword.value
    return
  }
  keyword.value = q
  page.value = 1
  syncQuery()
  load()
}

function onPageChange(p) {
  page.value = p
  syncQuery()
  load()
}

watch(
  () => route.query,
  (query) => {
    const q = String(query.q || '').trim()
    const p = Math.max(1, Number(query.page) || 1)
    if (q !== keyword.value || p !== page.value) {
      keyword.value = q
      inputValue.value = q
      page.value = p
      load()
    }
  }
)

load()
</script>

<template>
  <div class="search-page">
    <div class="container">
      <header class="page-head">
        <h1 class="page-title">搜索番剧</h1>
        <p class="page-sub">输入番剧名称、别名或关键字</p>
      </header>

      <form class="search-bar" role="search" @submit.prevent="submit">
        <Search :size="17" class="search-bar__icon" />
        <input
          v-model="inputValue"
          type="search"
          class="search-bar__input"
          placeholder="搜索番剧…"
          autocomplete="off"
          enterkeyhint="search"
        />
        <button type="submit" class="search-bar__btn">搜索</button>
      </form>

      <div v-if="loading" class="anime-grid skeleton-grid">
        <span v-for="i in 12" :key="i" class="skeleton skeleton-card"></span>
      </div>

      <div v-else-if="error" class="state-box">
        <p class="state-box__title">搜索失败</p>
        <p class="state-box__hint">{{ error }}</p>
        <button type="button" class="btn-ghost" @click="load">
          <RefreshCw :size="14" /> 重试
        </button>
      </div>

      <template v-else-if="searched">
        <div v-if="videos.length" class="result-meta">
          「{{ keyword }}」共找到 {{ pager.total }} 部番剧 · 第 {{ pager.page }} / {{ totalPages }} 页
        </div>
        <AnimeGrid v-if="videos.length" :videos="videos" />
        <div v-else class="state-box">
          <p class="state-box__title">没有找到「{{ keyword }}」相关的番剧</p>
          <p class="state-box__hint">换个关键词试试，例如番剧别名或译名。</p>
        </div>

        <Pagination
          v-if="videos.length"
          :page="pager.page"
          :page-size="pager.page_size"
          :total="pager.total"
          @change="onPageChange"
        />
      </template>

      <div v-else class="search-placeholder">
        <Search :size="34" />
        <p>输入关键词开始搜索</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.search-page {
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
  margin-top: 6px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 640px;
  height: 46px;
  padding: 0 8px 0 16px;
  margin-bottom: 26px;
  border-radius: 999px;
  border: 1px solid hsl(var(--border));
  background-color: hsl(var(--card));
  transition: border-color 0.2s ease;

  &:focus-within {
    border-color: hsl(var(--brand) / 0.6);
  }
}

.search-bar__icon {
  flex-shrink: 0;
  color: hsl(var(--muted-foreground));
}

.search-bar__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  background: transparent;
  border: 0;
  outline: 0;
  font-size: 14.5px;
  color: hsl(var(--foreground));

  &::placeholder {
    color: hsl(var(--muted-foreground));
  }

  &::-webkit-search-cancel-button {
    display: none;
  }
}

.search-bar__btn {
  flex-shrink: 0;
  height: 34px;
  padding: 0 20px;
  border-radius: 999px;
  background-color: hsl(var(--brand));
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  transition: background-color 0.2s ease;

  &:hover {
    background-color: hsl(var(--brand-strong));
  }
}

.result-meta {
  margin-bottom: 14px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}

.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;

  @media (min-width: 640px) {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  @media (min-width: 1024px) {
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: var(--grid-gap);
  }
}

.skeleton-card {
  display: block;
  aspect-ratio: 3 / 4;
  border-radius: 10px;
}

.search-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 72px 0;
  color: hsl(var(--muted-foreground));
  font-size: 13.5px;
}
</style>

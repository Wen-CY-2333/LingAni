<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RefreshCw, SlidersHorizontal } from 'lucide-vue-next'
import { getVideos } from '@/api/video'
import AnimeGrid from '@/components/AnimeGrid.vue'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const router = useRouter()

const ZONE_OPTIONS = [
  { value: 1, label: 'TV番组' },
  { value: 2, label: '剧场版' }
]

const YEAR_OPTIONS = [
  { value: '', label: '全部年份' },
  ...Array.from({ length: 2026 - 2000 + 1 }, (_, i) => {
    const year = 2026 - i
    return { value: String(year), label: `${year}年` }
  })
]

const TAG_OPTIONS = [
  { value: '', label: '全部题材' },
  { value: '热血', label: '热血' },
  { value: '奇幻', label: '奇幻' },
  { value: '战斗', label: '战斗' },
  { value: '搞笑', label: '搞笑' },
  { value: '日常', label: '日常' },
  { value: '校园', label: '校园' },
  { value: '恋爱', label: '恋爱' },
  { value: '科幻', label: '科幻' },
  { value: '治愈', label: '治愈' },
  { value: '百合', label: '百合' },
  { value: '后宫', label: '后宫' },
  { value: '异世界', label: '异世界' },
  { value: '冒险', label: '冒险' },
  { value: '漫画改', label: '漫画改' },
  { value: '原创', label: '原创' },
  { value: '小说改', label: '小说改' },
  { value: '游戏改', label: '游戏改' },
  { value: '续作', label: '续作' }
]

const PAGE_SIZE = 24

const filters = reactive({
  zoneId: Number(route.query.zone) || 1,
  year: route.query.year ? String(route.query.year) : '',
  tag: route.query.tag ? String(route.query.tag) : '',
  page: Math.max(1, Number(route.query.page) || 1)
})

const videos = ref([])
const pager = ref({ page: 1, page_size: PAGE_SIZE, total: 0 })
const loading = ref(true)
const error = ref('')
let requestId = 0

const totalPages = computed(() => Math.ceil(pager.value.total / pager.value.page_size))
const hasFilters = computed(() => Boolean(filters.year || filters.tag))

function syncQuery() {
  const query = {}
  if (filters.zoneId !== 1) query.zone = String(filters.zoneId)
  if (filters.year) query.year = filters.year
  if (filters.tag) query.tag = filters.tag
  if (filters.page > 1) query.page = String(filters.page)
  router.replace({ path: '/category', query })
}

async function load() {
  const current = ++requestId
  loading.value = true
  error.value = ''
  try {
    const data = await getVideos({
      zoneId: filters.zoneId,
      year: filters.year || undefined,
      tag: filters.tag || undefined,
      page: filters.page,
      pageSize: PAGE_SIZE
    })
    if (current !== requestId) return
    videos.value = Array.isArray(data?.list) ? data.list : []
    pager.value = data?.pager ?? { page: filters.page, page_size: PAGE_SIZE, total: 0 }
  } catch (err) {
    if (current !== requestId) return
    videos.value = []
    pager.value = { page: 1, page_size: PAGE_SIZE, total: 0 }
    error.value = err.message || '分类数据加载失败'
  } finally {
    if (current === requestId) loading.value = false
  }
}

function applyFilters(resetPage = true) {
  if (resetPage) filters.page = 1
  syncQuery()
  load()
}

function setZone(value) {
  if (value === filters.zoneId) return
  filters.zoneId = value
  applyFilters()
}

function setYear(value) {
  if (value === filters.year) return
  filters.year = value
  applyFilters()
}

function setTag(value) {
  if (value === filters.tag) return
  filters.tag = value
  applyFilters()
}

function clearFilters() {
  filters.year = ''
  filters.tag = ''
  applyFilters()
}

function onPageChange(page) {
  filters.page = page
  applyFilters(false)
}

watch(
  () => route.query,
  (query) => {
    const zoneId = Number(query.zone) || 1
    const year = query.year ? String(query.year) : ''
    const tag = query.tag ? String(query.tag) : ''
    const page = Math.max(1, Number(query.page) || 1)
    if (
      zoneId !== filters.zoneId ||
      year !== filters.year ||
      tag !== filters.tag ||
      page !== filters.page
    ) {
      filters.zoneId = zoneId
      filters.year = year
      filters.tag = tag
      filters.page = page
      load()
    }
  }
)

load()
</script>

<template>
  <div class="category-page">
    <div class="container">
      <header class="page-head">
        <h1 class="page-title">番剧分类</h1>
        <p class="page-sub">按分区、年份与题材浏览全部番剧</p>
      </header>

      <div class="filters surface">
        <div class="filter-row">
          <span class="filter-label">分区</span>
          <div class="filter-options">
            <button
              v-for="opt in ZONE_OPTIONS"
              :key="opt.value"
              type="button"
              class="filter-chip"
              :class="{ 'is-active': filters.zoneId === opt.value }"
              @click="setZone(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <div class="filter-row">
          <span class="filter-label">年份</span>
          <div class="filter-options">
            <button
              v-for="opt in YEAR_OPTIONS"
              :key="opt.value || 'all'"
              type="button"
              class="filter-chip"
              :class="{ 'is-active': filters.year === opt.value }"
              @click="setYear(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <div class="filter-row">
          <span class="filter-label">题材</span>
          <div class="filter-options">
            <button
              v-for="opt in TAG_OPTIONS"
              :key="opt.value || 'all'"
              type="button"
              class="filter-chip"
              :class="{ 'is-active': filters.tag === opt.value }"
              @click="setTag(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>

        <div v-if="hasFilters" class="filter-actions">
          <button type="button" class="btn-ghost" @click="clearFilters">
            <SlidersHorizontal :size="13" /> 清空筛选
          </button>
        </div>
      </div>

      <div v-if="loading" class="anime-grid skeleton-grid">
        <span v-for="i in 12" :key="i" class="skeleton skeleton-card"></span>
      </div>

      <div v-else-if="error" class="state-box">
        <p class="state-box__title">分类加载失败</p>
        <p class="state-box__hint">{{ error }}</p>
        <button type="button" class="btn-ghost" @click="load">
          <RefreshCw :size="14" /> 重试
        </button>
      </div>

      <template v-else>
        <div v-if="videos.length" class="result-meta">
          共 {{ pager.total }} 部番剧 · 第 {{ pager.page }} / {{ totalPages }} 页
        </div>
        <AnimeGrid v-if="videos.length" :videos="videos" />
        <div v-else class="state-box">
          <p class="state-box__title">没有符合条件的番剧</p>
          <p class="state-box__hint">试试清空筛选条件，或切换到其他分区。</p>
          <button v-if="hasFilters" type="button" class="btn-ghost" @click="clearFilters">
            清空筛选
          </button>
        </div>

        <Pagination
          v-if="videos.length"
          :page="pager.page"
          :page-size="pager.page_size"
          :total="pager.total"
          @change="onPageChange"
        />
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.category-page {
  padding-top: calc(var(--header-height) + 28px);
  padding-bottom: 48px;
}

.page-head {
  margin-bottom: 22px;
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

.filters {
  padding: 16px 18px;
  margin-bottom: 26px;
}

.filter-row {
  display: flex;
  gap: 12px;
  padding: 7px 0;

  & + .filter-row {
    border-top: 1px dashed hsl(var(--border) / 0.55);
  }
}

.filter-label {
  flex-shrink: 0;
  width: 34px;
  padding-top: 5px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-chip {
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
  transition: background-color 0.2s ease, color 0.2s ease;

  &:hover {
    color: hsl(var(--foreground));
  }

  &.is-active {
    background-color: hsl(var(--brand));
    color: #fff;
    font-weight: 600;
  }
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 10px;
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

@media (max-width: 640px) {
  .page-title {
    font-size: 22px;
  }

  .filter-row {
    flex-direction: column;
    gap: 8px;
  }

  .filter-label {
    padding-top: 0;
  }
}
</style>

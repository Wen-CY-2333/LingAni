<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getAdminFavorites, getAdminHistory } from '@/api/backend'
import { formatDateTime, formatDuration } from '@/utils/datetime'
import { showError } from '@/utils/toast'
import Pagination from '@/components/Pagination.vue'

const activeTab = ref('favorites')

const favoriteState = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
  keyword: '',
  loading: false,
  records: []
})

const historyState = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
  keyword: '',
  loading: false,
  records: []
})

async function loadFavorites() {
  favoriteState.loading = true
  try {
    const data = await getAdminFavorites({
      current: favoriteState.page,
      size: favoriteState.pageSize,
      keyword: favoriteState.keyword.trim() || undefined
    })
    favoriteState.records = data?.records || []
    favoriteState.total = data?.total || 0
  } catch (error) {
    showError(error.message)
    favoriteState.records = []
    favoriteState.total = 0
  } finally {
    favoriteState.loading = false
  }
}

async function loadHistory() {
  historyState.loading = true
  try {
    const data = await getAdminHistory({
      current: historyState.page,
      size: historyState.pageSize,
      keyword: historyState.keyword.trim() || undefined
    })
    historyState.records = data?.records || []
    historyState.total = data?.total || 0
  } catch (error) {
    showError(error.message)
    historyState.records = []
    historyState.total = 0
  } finally {
    historyState.loading = false
  }
}

function searchFavorites() {
  favoriteState.page = 1
  loadFavorites()
}

function searchHistory() {
  historyState.page = 1
  loadHistory()
}

const totalHint = computed(() => (activeTab.value === 'favorites' ? favoriteState.total : historyState.total))

onMounted(() => {
  loadFavorites()
  loadHistory()
})
</script>

<template>
  <header class="admin-head">
    <div>
      <h1 class="admin-title">追番与历史</h1>
      <p class="admin-sub">共 {{ totalHint }} 条记录，此页仅用于查看，不影响用户数据。</p>
    </div>
  </header>

  <div class="tabs">
    <button
      type="button"
      class="tab"
      :class="{ 'is-active': activeTab === 'favorites' }"
      @click="activeTab = 'favorites'"
    >
      追番记录
    </button>
    <button
      type="button"
      class="tab"
      :class="{ 'is-active': activeTab === 'history' }"
      @click="activeTab = 'history'"
    >
      播放历史
    </button>
  </div>

  <template v-if="activeTab === 'favorites'">
    <div class="toolbar">
      <input
        v-model="favoriteState.keyword"
        class="search-input"
        type="search"
        placeholder="搜索动画标题"
        @keyup.enter="searchFavorites"
      />
      <button type="button" class="admin-btn" @click="searchFavorites">搜索</button>
      <button type="button" class="admin-btn" :disabled="favoriteState.loading" @click="loadFavorites">
        {{ favoriteState.loading ? '加载中…' : '刷新' }}
      </button>
    </div>

    <div class="admin-card">
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>用户</th>
              <th>动画 ID</th>
              <th>动画标题</th>
              <th>追番时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in favoriteState.records" :key="row.id">
              <td>{{ row.id }}</td>
              <td>{{ row.username || `用户 ${row.userId}` }}</td>
              <td>{{ row.videoId }}</td>
              <td class="td-ellipsis" :title="row.animeTitle">{{ row.animeTitle }}</td>
              <td>{{ formatDateTime(row.createdAt) }}</td>
            </tr>
            <tr v-if="!favoriteState.records.length">
              <td colspan="5">
                <p class="empty-state">{{ favoriteState.loading ? '加载中…' : '暂无追番记录' }}</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        :page="favoriteState.page"
        :page-size="favoriteState.pageSize"
        :total="favoriteState.total"
        @change="(next) => { favoriteState.page = next; loadFavorites() }"
      />
    </div>
  </template>

  <template v-else>
    <div class="toolbar">
      <input
        v-model="historyState.keyword"
        class="search-input"
        type="search"
        placeholder="搜索动画或章节标题"
        @keyup.enter="searchHistory"
      />
      <button type="button" class="admin-btn" @click="searchHistory">搜索</button>
      <button type="button" class="admin-btn" :disabled="historyState.loading" @click="loadHistory">
        {{ historyState.loading ? '加载中…' : '刷新' }}
      </button>
    </div>

    <div class="admin-card">
      <div class="admin-table-wrap">
        <table class="admin-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>用户</th>
              <th>动画 ID</th>
              <th>章节</th>
              <th>进度 / 时长</th>
              <th>最近播放</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in historyState.records" :key="row.id">
              <td>{{ row.id }}</td>
              <td>{{ row.username || `用户 ${row.userId}` }}</td>
              <td>{{ row.videoId }}</td>
              <td class="td-ellipsis" :title="row.episodeTitle">{{ row.episodeTitle || row.sectionId }}</td>
              <td>{{ formatDuration(row.progress) }} / {{ formatDuration(row.duration) }}</td>
              <td>{{ formatDateTime(row.lastPlayedAt) }}</td>
            </tr>
            <tr v-if="!historyState.records.length">
              <td colspan="6">
                <p class="empty-state">{{ historyState.loading ? '加载中…' : '暂无播放历史' }}</p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <Pagination
        :page="historyState.page"
        :page-size="historyState.pageSize"
        :total="historyState.total"
        @change="(next) => { historyState.page = next; loadHistory() }"
      />
    </div>
  </template>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getAdminStats } from '@/api/backend'
import { showError } from '@/utils/toast'

const stats = ref(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    stats.value = await getAdminStats()
  } catch (error) {
    showError(error.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)

const cards = computed(() => {
  if (!stats.value) return []
  return [
    { label: '用户总数', value: stats.value.users, hint: '含被禁用的账号' },
    { label: '评论总数', value: stats.value.comments, hint: '不含已删除评论' },
    { label: '弹幕总数', value: stats.value.danmaku, hint: '不含已删除弹幕' },
    { label: '追番总数', value: stats.value.favorites, hint: '全站追番记录' },
    { label: '历史总数', value: stats.value.history, hint: '全站播放进度记录' }
  ]
})
</script>

<template>
  <header class="admin-head">
    <div>
      <h1 class="admin-title">数据总览</h1>
      <p class="admin-sub">灵漫后端当前数据量，数字随前台操作实时变化。</p>
    </div>
    <button type="button" class="admin-btn" :disabled="loading" @click="load">
      {{ loading ? '加载中…' : '刷新数据' }}
    </button>
  </header>

  <section v-if="cards.length" class="stat-grid">
    <div v-for="card in cards" :key="card.label" class="stat-card">
      <p class="stat-label">{{ card.label }}</p>
      <p class="stat-value">{{ card.value }}</p>
      <p class="stat-hint">{{ card.hint }}</p>
    </div>
  </section>

  <div v-else-if="loading" class="admin-card">
    <p class="empty-state">正在加载统计数据…</p>
  </div>

  <div v-else class="admin-card">
    <p class="empty-state">统计数据加载失败，请点击右上角重试。</p>
  </div>
</template>

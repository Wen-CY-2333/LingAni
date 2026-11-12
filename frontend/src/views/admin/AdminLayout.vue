<script setup>
import { useRoute } from 'vue-router'
import { ArrowLeft, LayoutDashboard, ListVideo, MessageSquareText, Users } from 'lucide-vue-next'

const route = useRoute()

const links = [
  { path: '/admin', label: '数据总览', icon: LayoutDashboard },
  { path: '/admin/users', label: '用户管理', icon: Users },
  { path: '/admin/content', label: '内容审核', icon: MessageSquareText },
  { path: '/admin/records', label: '追番与历史', icon: ListVideo }
]

function isActive(path) {
  return route.path === path
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-side">
      <div class="admin-brand">灵漫后台</div>
      <nav class="side-nav" aria-label="后台导航">
        <RouterLink
          v-for="link in links"
          :key="link.path"
          :to="link.path"
          class="side-link"
          :class="{ 'is-active': isActive(link.path) }"
        >
          <component :is="link.icon" :size="16" />
          <span>{{ link.label }}</span>
        </RouterLink>
      </nav>
      <RouterLink to="/" class="admin-back">
        <ArrowLeft :size="15" /> 返回前台
      </RouterLink>
    </aside>

    <main class="admin-main">
      <div class="admin-page">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style lang="scss">
@use '@/assets/styles/admin.scss';
</style>

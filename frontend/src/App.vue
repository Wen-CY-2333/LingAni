<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import AppFooter from './components/AppFooter.vue'
import AppToast from './components/AppToast.vue'
import { getMe } from '@/api/backend'
import { isLoggedIn, setUser } from '@/stores/auth'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

// 登录/注册页只保留表单卡片，隐藏站点导航与页脚
const isAuthPage = computed(() => route.path === '/login' || route.path === '/register')
const isAdminPage = computed(() => route.path.startsWith('/admin'))

function handleUnauthorized() {
  showToast('登录状态已失效，请重新登录', 'error')
  if (route.meta.requiresAuth) {
    router.replace({ path: '/login', query: { redirect: route.fullPath } })
  }
}

// 已登录时刷新一次用户信息，确保角色、昵称、头像与后端一致
async function refreshUser() {
  if (!isLoggedIn.value) return
  try {
    setUser(await getMe())
  } catch {
    // 401 由请求拦截器统一处理，这里不重复提示
  }
}

onMounted(() => {
  window.addEventListener('lingani:unauthorized', handleUnauthorized)
  refreshUser()
})

onBeforeUnmount(() => {
  window.removeEventListener('lingani:unauthorized', handleUnauthorized)
})
</script>

<template>
  <AppHeader v-if="!isAuthPage && !isAdminPage" />
  <main class="page" :class="{ 'auth-layout': isAuthPage, 'admin-layout': isAdminPage }">
    <div class="page-wrap" :class="{ 'is-admin': isAdminPage }">
      <RouterView />
    </div>
  </main>
  <AppFooter v-if="!isAuthPage && !isAdminPage" />
  <AppToast />
</template>

<style scoped lang="scss">
.page-wrap {
  width: 100%;
  max-width: 1600px;
  margin: 0 auto;
}

.page.auth-layout {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding-top: 0;
}

.page.admin-layout {
  padding-top: 0;
}

.page-wrap.is-admin {
  max-width: none;
}
</style>

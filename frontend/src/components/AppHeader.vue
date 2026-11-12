<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronDown, Heart, History, LogOut, Search, ShieldCheck, UserRound } from 'lucide-vue-next'
import { authState, clearAuth, isLoggedIn } from '@/stores/auth'
import { logout as logoutRequest } from '@/api/backend'
import { showToast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const scrolled = ref(false)
const searchInput = ref('')
const menuOpen = ref(false)
const userAreaRef = ref(null)
const avatarFailed = ref(false)

const isHome = computed(() => route.path === '/')
const solidHeader = computed(() => !isHome.value || scrolled.value)

const nickname = computed(() => authState.user?.nickname || authState.user?.username || '')
const avatar = computed(() => authState.user?.avatar || '')
const initial = computed(() => (nickname.value ? nickname.value.slice(0, 1).toUpperCase() : '灵'))
const isAdmin = computed(() => authState.user?.role === 1)

watch(avatar, () => {
  avatarFailed.value = false
})

function isActive(path) {
  return route.path === path
}

function submitSearch() {
  const q = searchInput.value.trim()
  if (!q) return
  if (route.path === '/search' && String(route.query.q || '') === q) {
    searchInput.value = ''
    return
  }
  router.push({ path: '/search', query: { q } })
}

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function closeMenu() {
  menuOpen.value = false
}

function onDocumentClick(event) {
  if (!userAreaRef.value) return
  if (!userAreaRef.value.contains(event.target)) {
    closeMenu()
  }
}

async function handleLogout() {
  closeMenu()
  try {
    await logoutRequest()
  } catch {
    // 即使后端退出失败，也必须清理本地登录状态
  } finally {
    clearAuth()
    showToast('已退出登录', 'success')
    if (route.path.startsWith('/user')) {
      router.push('/')
    }
  }
}

// 跟随路由同步搜索框：离开搜索页后清空，回到搜索页时回填当前关键词
watch(
  () => [route.path, route.query.q],
  () => {
    searchInput.value = route.path === '/search' ? String(route.query.q || '') : ''
    closeMenu()
  },
  { immediate: true }
)

function onScroll() {
  scrolled.value = window.scrollY > 4
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('click', onDocumentClick)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('click', onDocumentClick)
})
</script>

<template>
  <header class="app-header" :class="{ 'is-solid': solidHeader }">
    <div class="container header-inner">
      <RouterLink to="/" class="brand" aria-label="灵漫首页">
        <span class="brand-text">灵漫</span>
      </RouterLink>

      <nav class="nav" aria-label="主导航">
        <RouterLink to="/" class="nav-link" :class="{ 'is-active': isHome }">首页</RouterLink>
        <RouterLink to="/category" class="nav-link" :class="{ 'is-active': isActive('/category') }">分类</RouterLink>
        <RouterLink to="/rank" class="nav-link" :class="{ 'is-active': isActive('/rank') }">排行榜</RouterLink>
        <RouterLink to="/schedule" class="nav-link" :class="{ 'is-active': isActive('/schedule') }">追番周表</RouterLink>
      </nav>

      <div class="header-actions">
        <form class="search-box" role="search" @submit.prevent="submitSearch">
          <Search :size="15" />
          <input
            v-model="searchInput"
            type="search"
            class="search-input"
            placeholder="搜索番剧…"
            autocomplete="off"
            enterkeyhint="search"
          />
        </form>

        <div v-if="isLoggedIn" ref="userAreaRef" class="user-area">
          <button
            type="button"
            class="user-trigger"
            :aria-expanded="menuOpen"
            aria-haspopup="menu"
            @click="toggleMenu"
          >
            <img
              v-if="avatar && !avatarFailed"
              :src="avatar"
              :alt="nickname"
              class="avatar-img"
              referrerpolicy="no-referrer"
              @error="avatarFailed = true"
            />
            <span v-else class="avatar-fallback">{{ initial }}</span>
            <span class="user-name">{{ nickname }}</span>
            <ChevronDown :size="14" class="chevron" :class="{ 'is-open': menuOpen }" />
          </button>

          <div v-if="menuOpen" class="user-menu" role="menu">
            <RouterLink to="/user" class="menu-item" role="menuitem" @click="closeMenu">
              <UserRound :size="15" /> 个人中心
            </RouterLink>
            <RouterLink to="/user/favorites" class="menu-item" role="menuitem" @click="closeMenu">
              <Heart :size="15" /> 我的追番
            </RouterLink>
            <RouterLink to="/user/history" class="menu-item" role="menuitem" @click="closeMenu">
              <History :size="15" /> 播放历史
            </RouterLink>
            <RouterLink v-if="isAdmin" to="/admin" class="menu-item" role="menuitem" @click="closeMenu">
              <ShieldCheck :size="15" /> 管理后台
            </RouterLink>
            <button type="button" class="menu-item is-danger" role="menuitem" @click="handleLogout">
              <LogOut :size="15" /> 退出登录
            </button>
          </div>
        </div>

        <div v-else class="guest-actions">
          <RouterLink to="/login" class="guest-link">登录</RouterLink>
          <RouterLink to="/register" class="guest-link is-primary">注册</RouterLink>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped lang="scss">
.app-header {
  position: fixed;
  inset: 0 0 auto;
  z-index: 50;
  height: var(--header-height);
  background-color: transparent;
  border-bottom: 1px solid transparent;
  transition: background-color 0.3s ease, border-color 0.3s ease, backdrop-filter 0.3s ease;

  /* 位于 Banner 上方时的顶部渐变遮罩 */
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(
      to bottom,
      rgba(0, 0, 0, 0.68) 0%,
      rgba(0, 0, 0, 0.36) 45%,
      rgba(0, 0, 0, 0) 100%
    );
    pointer-events: none;
    transition: opacity 0.3s ease;
  }

  &.is-solid {
    background-color: rgba(18, 18, 18, 0.95);
    border-bottom-color: hsl(var(--border) / 0.6);
    backdrop-filter: blur(8px);

    &::before {
      opacity: 0;
    }
  }
}

.header-inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 28px;
  height: 100%;
}

.brand {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.brand-text {
  font-family: 'LingAni Display', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 25px;
  font-weight: 400;
  line-height: 1;
  letter-spacing: 2px;
  color: hsl(var(--brand));
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.35);
  white-space: nowrap;
}

.nav {
  display: flex;
  align-items: center;
  gap: 22px;
}

.nav-link {
  position: relative;
  font-size: 14.5px;
  color: hsl(var(--muted-foreground));
  transition: color 0.2s ease;
  white-space: nowrap;
  cursor: pointer;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);

  &:hover {
    color: hsl(var(--foreground));
  }

  &.is-active {
    color: hsl(var(--foreground));
    font-weight: 600;

    &::after {
      content: '';
      position: absolute;
      left: 50%;
      bottom: -19px;
      width: 22px;
      height: 2px;
      border-radius: 2px;
      transform: translateX(-50%);
      background-color: hsl(var(--brand));
    }
  }
}

.header-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 280px;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background-color: rgba(18, 18, 18, 0.55);
  backdrop-filter: blur(6px);
  color: hsl(var(--muted-foreground));
  font-size: 13px;
}

.search-input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: 0;
  outline: 0;
  font-size: 13px;
  color: hsl(var(--foreground));

  &::placeholder {
    color: hsl(var(--muted-foreground));
  }

  &::-webkit-search-cancel-button {
    display: none;
  }
}

.user-area {
  position: relative;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 10px 0 6px;
  border-radius: 999px;
  border: 1px solid transparent;
  background-color: rgba(255, 255, 255, 0.04);
  color: hsl(var(--foreground));
  font-size: 13px;
  transition: border-color 0.2s ease, background-color 0.2s ease;

  &:hover {
    border-color: hsl(var(--brand) / 0.45);
    background-color: rgba(255, 255, 255, 0.08);
  }
}

.avatar-img,
.avatar-fallback {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  flex-shrink: 0;
}

.avatar-img {
  object-fit: cover;
  background-color: hsl(var(--muted));
}

.avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: hsl(var(--brand));
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.user-name {
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chevron {
  color: hsl(var(--muted-foreground));
  transition: transform 0.2s ease;

  &.is-open {
    transform: rotate(180deg);
  }
}

.user-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 168px;
  padding: 6px;
  border-radius: 10px;
  border: 1px solid hsl(var(--border));
  background-color: rgba(24, 24, 27, 0.97);
  backdrop-filter: blur(10px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.45);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border-radius: 7px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
  transition: color 0.2s ease, background-color 0.2s ease;

  &:hover {
    color: hsl(var(--foreground));
    background-color: rgba(255, 255, 255, 0.07);
  }

  &.is-danger:hover {
    color: hsl(var(--brand));
  }
}

.guest-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.guest-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 16px;
  border-radius: 999px;
  font-size: 13.5px;
  color: hsl(var(--foreground));
  border: 1px solid hsl(var(--border));
  background-color: rgba(255, 255, 255, 0.04);
  transition: border-color 0.2s ease, color 0.2s ease;

  &:hover {
    border-color: hsl(var(--brand) / 0.6);
    color: hsl(var(--brand));
  }

  &.is-primary {
    border-color: transparent;
    background-color: hsl(var(--brand));
    color: #fff;

    &:hover {
      color: #fff;
      filter: brightness(1.06);
    }
  }
}

@media (max-width: 900px) {
  .search-box {
    display: none;
  }

  .nav {
    gap: 16px;
    overflow-x: auto;
  }
}

@media (max-width: 560px) {
  .brand-text {
    font-size: 21px;
  }

  .nav {
    gap: 12px;
  }

  .nav-link {
    font-size: 13px;
  }

  .user-name {
    display: none;
  }

  .header-inner {
    gap: 14px;
  }
}
</style>

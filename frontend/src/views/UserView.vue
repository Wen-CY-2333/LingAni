<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Heart, History, KeyRound, Loader2, Save, UserRound } from 'lucide-vue-next'
import { getMe, updatePassword, updateProfile } from '@/api/backend'
import { authState, clearAuth, setUser } from '@/stores/auth'
import { formatDateTime } from '@/utils/datetime'
import { showError, showSuccess } from '@/utils/toast'

const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const user = computed(() => authState.user)

const profile = ref({ nickname: '', avatar: '' })
const savingProfile = ref(false)
const avatarFailed = ref(false)

const password = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const savingPassword = ref(false)
const passwordError = ref('')

const initial = computed(() => (user.value?.nickname || user.value?.username || '灵').slice(0, 1).toUpperCase())
const joinedAt = computed(() => formatDateTime(user.value?.createdAt))

function applyUser(data) {
  setUser(data)
  profile.value.nickname = data.nickname || ''
  profile.value.avatar = data.avatar || ''
  avatarFailed.value = false
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await getMe()
    applyUser(data)
  } catch (error) {
    loadError.value = error.message || '用户信息加载失败'
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!profile.value.nickname.trim()) {
    showError(new Error('昵称不能为空'))
    return
  }
  savingProfile.value = true
  try {
    const data = await updateProfile({
      nickname: profile.value.nickname.trim(),
      avatar: profile.value.avatar.trim()
    })
    applyUser(data)
    showSuccess('个人资料已更新')
  } catch (error) {
    showError(error, '资料保存失败')
  } finally {
    savingProfile.value = false
  }
}

async function changePassword() {
  passwordError.value = ''
  if (!password.value.oldPassword || !password.value.newPassword || !password.value.confirmPassword) {
    passwordError.value = '请填写完整的密码信息'
    return
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{8,32}$/.test(password.value.newPassword)) {
    passwordError.value = '新密码需为 8-32 位，且同时包含字母和数字'
    return
  }
  if (password.value.newPassword !== password.value.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }

  savingPassword.value = true
  try {
    await updatePassword(password.value)
    clearAuth()
    showSuccess('密码已修改，请重新登录')
    router.push('/login')
  } catch (error) {
    passwordError.value = error.message || '密码修改失败'
    showError(error, '密码修改失败')
  } finally {
    savingPassword.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="user-page container">
    <div v-if="loading" class="state-box">
      <span class="spinner"></span>
      <p>正在加载用户信息…</p>
    </div>

    <div v-else-if="loadError" class="state-box">
      <p class="state-box__title">用户信息加载失败</p>
      <p class="state-box__hint">{{ loadError }}</p>
      <div class="state-box__actions">
        <button type="button" class="btn-ghost" @click="load">
          <UserRound :size="14" /> 重试
        </button>
        <RouterLink to="/" class="btn-ghost">返回首页</RouterLink>
      </div>
    </div>

    <template v-else>
      <section class="profile-head surface">
        <div class="profile-main">
          <img
            v-if="user?.avatar && !avatarFailed"
            :src="user.avatar"
            :alt="user.nickname"
            class="profile-avatar"
            referrerpolicy="no-referrer"
            @error="avatarFailed = true"
          />
          <span v-else class="profile-avatar is-fallback">{{ initial }}</span>
          <div class="profile-info">
            <p class="eyebrow">个人中心</p>
            <h1 class="profile-name">{{ user?.nickname }}</h1>
            <p class="profile-username">@{{ user?.username }}</p>
            <p class="profile-joined">注册于 {{ joinedAt }}</p>
          </div>
        </div>
        <div class="profile-links">
          <RouterLink to="/user/favorites" class="btn-ghost">
            <Heart :size="14" /> 我的追番
          </RouterLink>
          <RouterLink to="/user/history" class="btn-ghost">
            <History :size="14" /> 播放历史
          </RouterLink>
        </div>
      </section>

      <div class="user-grid">
        <section class="panel surface">
          <div class="panel-head">
            <h2 class="section-title">修改资料</h2>
          </div>
          <form class="panel-form" @submit.prevent="saveProfile">
            <label class="field">
              <span class="field-label">昵称</span>
              <input v-model="profile.nickname" type="text" maxlength="50" placeholder="请输入昵称" />
            </label>
            <label class="field">
              <span class="field-label">头像地址</span>
              <input v-model="profile.avatar" type="text" maxlength="500" placeholder="https://…" />
            </label>
            <button type="submit" class="btn-brand" :disabled="savingProfile">
              <Loader2 v-if="savingProfile" :size="14" class="spin" />
              <Save v-else :size="14" />
              {{ savingProfile ? '保存中…' : '保存资料' }}
            </button>
          </form>
        </section>

        <section class="panel surface">
          <div class="panel-head">
            <h2 class="section-title">修改密码</h2>
          </div>
          <form class="panel-form" @submit.prevent="changePassword">
            <label class="field">
              <span class="field-label">原密码</span>
              <input v-model="password.oldPassword" type="password" autocomplete="current-password" maxlength="72" />
            </label>
            <label class="field">
              <span class="field-label">新密码</span>
              <input
                v-model="password.newPassword"
                type="password"
                autocomplete="new-password"
                placeholder="8-32 位，包含字母和数字"
                maxlength="32"
              />
            </label>
            <label class="field">
              <span class="field-label">确认新密码</span>
              <input
                v-model="password.confirmPassword"
                type="password"
                autocomplete="new-password"
                maxlength="32"
              />
            </label>
            <p v-if="passwordError" class="form-error">{{ passwordError }}</p>
            <button type="submit" class="btn-ghost" :disabled="savingPassword">
              <Loader2 v-if="savingPassword" :size="14" class="spin" />
              <KeyRound v-else :size="14" />
              {{ savingPassword ? '提交中…' : '修改密码' }}
            </button>
          </form>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.user-page {
  padding-top: 32px;
  padding-bottom: 12px;
}

.profile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 28px;
  flex-wrap: wrap;
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 18px;
}

.profile-avatar {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  object-fit: cover;
  background-color: hsl(var(--muted));
}

.profile-avatar.is-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background-color: hsl(var(--brand));
  color: #fff;
  font-size: 26px;
  font-weight: 700;
}

.eyebrow {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 2px;
  color: hsl(var(--brand));
}

.profile-name {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 700;
  color: hsl(var(--foreground));
}

.profile-username,
.profile-joined {
  margin-top: 4px;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}

.profile-links {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.user-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 24px;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

.panel {
  padding: 22px 24px;
}

.panel-head {
  margin-bottom: 16px;
}

.panel-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.field-label {
  font-size: 13px;
  color: hsl(var(--muted-foreground));
}

.field input {
  height: 40px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid hsl(var(--border));
  background-color: hsl(var(--muted) / 0.7);
  color: hsl(var(--foreground));
  outline: 0;
  font-size: 14px;

  &:focus {
    border-color: hsl(var(--brand) / 0.7);
  }
}

.form-error {
  font-size: 12.5px;
  color: hsl(var(--brand));
  line-height: 1.6;
}

.panel-form .btn-brand,
.panel-form .btn-ghost {
  align-self: flex-start;
  margin-top: 2px;
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

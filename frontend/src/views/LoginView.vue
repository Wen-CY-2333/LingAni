<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loader2, LogIn } from 'lucide-vue-next'
import { login } from '@/api/backend'
import { setAuth } from '@/stores/auth'
import { showError, showSuccess } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const form = ref({ username: '', password: '' })
const loading = ref(false)
const errorText = ref('')

async function submit() {
  errorText.value = ''
  if (!form.value.username.trim() || !form.value.password) {
    errorText.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  try {
    const data = await login({
      username: form.value.username.trim(),
      password: form.value.password
    })
    setAuth(data.token, data.user)
    showSuccess('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.replace(redirect || '/')
  } catch (error) {
    errorText.value = error.message || '登录失败'
    showError(error, '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page container">
    <div class="auth-card surface">
      <header class="auth-head">
        <p class="eyebrow">灵漫 LingAni</p>
        <h1 class="auth-title">欢迎回来</h1>
        <p class="auth-hint">登录后可保存播放历史、追番、发表评论和发送弹幕。</p>
      </header>

      <form class="auth-form" @submit.prevent="submit">
        <label class="field">
          <span class="field-label">用户名</span>
          <input v-model="form.username" type="text" autocomplete="username" placeholder="请输入用户名" maxlength="50" />
        </label>

        <label class="field">
          <span class="field-label">密码</span>
          <input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            maxlength="72"
          />
        </label>

        <p v-if="errorText" class="form-error">{{ errorText }}</p>

        <button type="submit" class="btn-brand submit-btn" :disabled="loading">
          <Loader2 v-if="loading" :size="15" class="spin" />
          <LogIn v-else :size="15" />
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>

      <p class="auth-switch">
        还没有账号？
        <RouterLink to="/register">立即注册</RouterLink>
      </p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-page {
  display: flex;
  justify-content: center;
  padding-top: 32px;
  padding-bottom: 32px;
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 32px 30px 26px;
}

.eyebrow {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 2px;
  color: hsl(var(--brand));
}

.auth-title {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
  color: hsl(var(--foreground));
}

.auth-hint {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: hsl(var(--muted-foreground));
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 24px;
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
  height: 42px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid hsl(var(--border));
  background-color: hsl(var(--muted) / 0.7);
  color: hsl(var(--foreground));
  outline: 0;
  font-size: 14px;
  transition: border-color 0.2s ease;

  &:focus {
    border-color: hsl(var(--brand) / 0.7);
  }
}

.form-error {
  font-size: 12.5px;
  color: hsl(var(--brand));
  line-height: 1.6;
}

.submit-btn {
  width: 100%;
  height: 42px;
  margin-top: 4px;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.auth-switch {
  margin-top: 20px;
  font-size: 13px;
  color: hsl(var(--muted-foreground));
  text-align: center;

  a {
    color: hsl(var(--brand));
    font-weight: 600;

    &:hover {
      text-decoration: underline;
    }
  }
}
</style>

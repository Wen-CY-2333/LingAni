<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Loader2, UserPlus } from 'lucide-vue-next'
import { register } from '@/api/backend'
import { showError, showSuccess } from '@/utils/toast'

const router = useRouter()

const form = ref({ username: '', nickname: '', password: '', confirmPassword: '' })
const loading = ref(false)
const errorText = ref('')

function validate() {
  const username = form.value.username.trim()
  if (!/^[A-Za-z][A-Za-z0-9_]{2,19}$/.test(username)) {
    return '用户名需为 3-20 位，以字母开头，可包含字母、数字和下划线'
  }
  if (!/^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{8,32}$/.test(form.value.password)) {
    return '密码需为 8-32 位，且同时包含字母和数字'
  }
  if (form.value.password !== form.value.confirmPassword) {
    return '两次输入的密码不一致'
  }
  return ''
}

async function submit() {
  errorText.value = validate()
  if (errorText.value) return

  loading.value = true
  try {
    await register({
      username: form.value.username.trim(),
      nickname: form.value.nickname.trim(),
      password: form.value.password,
      confirmPassword: form.value.confirmPassword
    })
    showSuccess('注册成功，请登录')
    router.push('/login')
  } catch (error) {
    errorText.value = error.message || '注册失败'
    showError(error, '注册失败')
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
        <h1 class="auth-title">创建账号</h1>
        <p class="auth-hint">用户名注册后不可修改，昵称可在个人中心随时调整。</p>
      </header>

      <form class="auth-form" @submit.prevent="submit">
        <label class="field">
          <span class="field-label">用户名</span>
          <input
            v-model="form.username"
            type="text"
            autocomplete="username"
            placeholder="3-20 位，字母开头"
            maxlength="20"
          />
        </label>

        <label class="field">
          <span class="field-label">昵称（可选）</span>
          <input v-model="form.nickname" type="text" placeholder="未填写时使用用户名" maxlength="50" />
        </label>

        <label class="field">
          <span class="field-label">密码</span>
          <input
            v-model="form.password"
            type="password"
            autocomplete="new-password"
            placeholder="8-32 位，包含字母和数字"
            maxlength="32"
          />
        </label>

        <label class="field">
          <span class="field-label">确认密码</span>
          <input
            v-model="form.confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="请再次输入密码"
            maxlength="32"
          />
        </label>

        <p v-if="errorText" class="form-error">{{ errorText }}</p>

        <button type="submit" class="btn-brand submit-btn" :disabled="loading">
          <Loader2 v-if="loading" :size="15" class="spin" />
          <UserPlus v-else :size="15" />
          {{ loading ? '注册中…' : '注册' }}
        </button>
      </form>

      <p class="auth-switch">
        已有账号？
        <RouterLink to="/login">返回登录</RouterLink>
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

<script setup>
import { onMounted, ref } from 'vue'
import { getAdminUsers, updateAdminUserStatus } from '@/api/backend'
import { authState } from '@/stores/auth'
import { formatDateTime } from '@/utils/datetime'
import { showError, showSuccess } from '@/utils/toast'
import Pagination from '@/components/Pagination.vue'

const pageSize = 10
const page = ref(1)
const total = ref(0)
const records = ref([])
const keyword = ref('')
const loading = ref(false)
const busyId = ref(null)

async function load() {
  loading.value = true
  try {
    const data = await getAdminUsers({
      current: page.value,
      size: pageSize,
      keyword: keyword.value.trim() || undefined
    })
    records.value = data?.records || []
    total.value = data?.total || 0
  } catch (error) {
    showError(error.message)
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function changePage(next) {
  page.value = next
  load()
}

async function toggleStatus(user) {
  busyId.value = user.id
  const next = user.status === 1 ? 0 : 1
  try {
    await updateAdminUserStatus(user.id, next)
    user.status = next
    showSuccess(next === 1 ? '已启用该账号' : '已禁用该账号，其登录状态立即失效')
  } catch (error) {
    showError(error.message)
  } finally {
    busyId.value = null
  }
}

function isSelf(user) {
  return authState.user?.id === user.id
}

onMounted(load)
</script>

<template>
  <header class="admin-head">
    <div>
      <h1 class="admin-title">用户管理</h1>
      <p class="admin-sub">共 {{ total }} 个账号，禁用后该用户无法登录，旧 Token 立即失效。</p>
    </div>
  </header>

  <div class="toolbar">
    <input
      v-model="keyword"
      class="search-input"
      type="search"
      placeholder="搜索用户名或昵称"
      @keyup.enter="search"
    />
    <button type="button" class="admin-btn" @click="search">搜索</button>
    <button type="button" class="admin-btn" :disabled="loading" @click="load">
      {{ loading ? '加载中…' : '刷新' }}
    </button>
  </div>

  <div class="admin-card">
    <div class="admin-table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>昵称</th>
            <th>角色</th>
            <th>状态</th>
            <th>注册时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in records" :key="user.id">
            <td>{{ user.id }}</td>
            <td>{{ user.username }}</td>
            <td>{{ user.nickname || '-' }}</td>
            <td>
              <span class="badge" :class="user.role === 1 ? 'badge--admin' : 'badge--muted'">
                {{ user.role === 1 ? '管理员' : '普通用户' }}
              </span>
            </td>
            <td>
              <span class="badge" :class="user.status === 1 ? 'badge--ok' : 'badge--off'">
                {{ user.status === 1 ? '正常' : '已禁用' }}
              </span>
            </td>
            <td>{{ formatDateTime(user.createdAt) }}</td>
            <td>
              <div class="row-actions">
                <button
                  type="button"
                  class="act-btn"
                  :class="{ 'is-danger': user.status === 1 }"
                  :disabled="busyId === user.id || isSelf(user)"
                  :title="isSelf(user) ? '不能修改自己的账号状态' : ''"
                  @click="toggleStatus(user)"
                >
                  {{ user.status === 1 ? '禁用' : '启用' }}
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="!records.length">
            <td colspan="7">
              <p class="empty-state">{{ loading ? '加载中…' : '暂无用户数据' }}</p>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination :page="page" :page-size="pageSize" :total="total" @change="changePage" />
  </div>
</template>

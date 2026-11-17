import axios from 'axios'
import { clearAuth, getToken } from '@/stores/auth'

// 留空时使用同源地址，由 Vite 代理转发到 Spring Boot
export const BACKEND_BASE_URL = (import.meta.env.VITE_BACKEND_BASE_URL || '').replace(/\/+$/, '')

const backend = axios.create({
  baseURL: BACKEND_BASE_URL,
  timeout: 15000,
  headers: {
    Accept: 'application/json'
  }
})

backend.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

backend.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data
      const error = new Error(body.msg || '请求失败')
      error.code = body.code
      if (body.code === 401) {
        handleUnauthorized()
      }
      return Promise.reject(error)
    }
    return body
  },
  (error) => {
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时，请稍后重试'))
    }
    if (!error.response) {
      return Promise.reject(new Error('网络请求失败，请检查后端服务是否已启动'))
    }

    const status = error.response.status
    const message = error.response.data?.msg
    if (status === 401) {
      handleUnauthorized()
      return Promise.reject(new Error(message || '登录状态已失效，请重新登录'))
    }
    if (status === 403) {
      return Promise.reject(new Error(message || '没有权限执行该操作'))
    }
    if (status === 404) {
      return Promise.reject(new Error(message || '请求的内容不存在'))
    }
    if (status === 429) {
      return Promise.reject(new Error(message || '操作过于频繁，请稍后再试'))
    }
    if (status >= 500) {
      return Promise.reject(new Error(message || '服务器暂时不可用，请稍后重试'))
    }
    return Promise.reject(new Error(message || `请求失败（HTTP ${status}）`))
  }
)

let unauthorizedNotified = false

function handleUnauthorized() {
  clearAuth()
  if (unauthorizedNotified) return
  unauthorizedNotified = true
  window.dispatchEvent(new CustomEvent('lingani:unauthorized'))
  window.setTimeout(() => {
    unauthorizedNotified = false
  }, 3000)
}

/* ---------------- 用户 ---------------- */
export function register(payload) {
  return backend.post('/api/auth/register', payload)
}

export function login(payload) {
  return backend.post('/api/auth/login', payload)
}

export function logout() {
  return backend.post('/api/auth/logout')
}

export function getMe() {
  return backend.get('/api/user/me')
}

export function updateProfile(payload) {
  return backend.put('/api/user/profile', payload)
}

export function updatePassword(payload) {
  return backend.put('/api/user/password', payload)
}

/* ---------------- 播放历史 ---------------- */
export function saveHistory(payload) {
  return backend.post('/api/history', payload)
}

export function getHistory(params = {}) {
  return backend.get('/api/history', { params })
}

export function getVideoHistory(videoId) {
  return backend.get(`/api/history/video/${encodeURIComponent(videoId)}`)
}

export function deleteHistory(id) {
  return backend.delete(`/api/history/${id}`)
}

export function clearHistory() {
  return backend.delete('/api/history')
}

/* ---------------- 追番 ---------------- */
export function addFavorite(payload) {
  return backend.post('/api/favorites', payload)
}

export function getFavorites(params = {}) {
  return backend.get('/api/favorites', { params })
}

export function checkFavorite(params = {}) {
  return backend.get('/api/favorites/check', { params })
}

export function deleteFavorite(id) {
  return backend.delete(`/api/favorites/${id}`)
}

/* ---------------- 评论 ---------------- */
export function getComments(params = {}) {
  return backend.get('/api/comments', { params })
}

export function createComment(payload) {
  return backend.post('/api/comments', payload)
}

export function updateComment(id, payload) {
  return backend.put(`/api/comments/${id}`, payload)
}

export function deleteComment(id) {
  return backend.delete(`/api/comments/${id}`)
}

/* ---------------- 弹幕 ---------------- */
export function getDanmaku(sectionId) {
  return backend.get('/api/danmaku', { params: { sectionId } })
}

export function sendDanmaku(payload) {
  return backend.post('/api/danmaku', payload)
}

export function deleteDanmaku(id) {
  return backend.delete(`/api/danmaku/${id}`)
}

/* ---------------- 站内通知 ---------------- */
export function getNotifications(params = {}) {
  return backend.get('/api/notifications', { params })
}

export function getUnreadNotificationCount() {
  return backend.get('/api/notifications/unread-count')
}

export function markNotificationRead(id) {
  return backend.post(`/api/notifications/${id}/read`)
}

export function markAllNotificationsRead() {
  return backend.post('/api/notifications/read-all')
}

/* ---------------- 管理后台 ---------------- */
export function getAdminStats() {
  return backend.get('/api/admin/stats')
}

export function getAdminUsers(params = {}) {
  return backend.get('/api/admin/users', { params })
}

export function updateAdminUserStatus(id, status) {
  return backend.put(`/api/admin/users/${id}/status`, { status })
}

export function getAdminComments(params = {}) {
  return backend.get('/api/admin/comments', { params })
}

export function updateAdminCommentStatus(id, status) {
  return backend.put(`/api/admin/comments/${id}/status`, { status })
}

export function deleteAdminComment(id) {
  return backend.delete(`/api/admin/comments/${id}`)
}

export function getAdminDanmaku(params = {}) {
  return backend.get('/api/admin/danmaku', { params })
}

export function updateAdminDanmakuStatus(id, status) {
  return backend.put(`/api/admin/danmaku/${id}/status`, { status })
}

export function deleteAdminDanmaku(id) {
  return backend.delete(`/api/admin/danmaku/${id}`)
}

export function getAdminFavorites(params = {}) {
  return backend.get('/api/admin/favorites', { params })
}

export function getAdminHistory(params = {}) {
  return backend.get('/api/admin/history', { params })
}

export default backend

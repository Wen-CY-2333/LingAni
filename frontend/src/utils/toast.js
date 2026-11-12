import { reactive } from 'vue'

let seq = 0

export const toasts = reactive([])

export function showToast(message, type = 'info', duration = 3000) {
  if (!message) return
  const id = `toast-${++seq}`
  toasts.push({ id, message: String(message), type })
  window.setTimeout(() => {
    const index = toasts.findIndex((item) => item.id === id)
    if (index > -1) toasts.splice(index, 1)
  }, duration)
}

export function showError(error, fallback = '操作失败') {
  showToast(error?.message || fallback, 'error')
}

export function showSuccess(message) {
  showToast(message, 'success')
}

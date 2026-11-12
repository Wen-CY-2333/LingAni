/**
 * 将后端弹幕数据转换为 artplayer-plugin-danmuku 需要的格式。
 * 后端字段：{ id, userId, text, time(秒), color(十进制整数), mode(0滚动/1顶部/2底部) }
 * 插件字段：{ text, time(秒), color(CSS 颜色), mode(0滚动/1顶部/2底部) }
 */

const MODE_MAP = {
  normal: 0,
  scroll: 0,
  top: 1,
  bottom: 2
}

export function intToHex(value) {
  if (typeof value === 'string' && value.startsWith('#')) return value
  const num = Number(value)
  if (!Number.isFinite(num) || num < 0) return '#ffffff'
  const clamped = Math.min(Math.max(Math.floor(num), 0), 0xffffff)
  return `#${clamped.toString(16).padStart(6, '0')}`
}

export function hexToInt(value) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return Math.min(Math.max(Math.floor(value), 0), 0xffffff)
  }
  if (typeof value !== 'string') return 0xffffff
  const hex = value.replace('#', '').trim()
  if (!/^[0-9a-fA-F]{6}$/.test(hex)) return 0xffffff
  return parseInt(hex, 16)
}

function normalizeMode(mode) {
  if (typeof mode === 'string') {
    return MODE_MAP[mode] ?? 0
  }
  const num = Number(mode)
  if (!Number.isFinite(num)) return 0
  return Math.min(Math.max(Math.floor(num), 0), 2)
}

function normalizeTime(item) {
  const time = Number(item.time ?? item.time_point)
  return Number.isFinite(time) && time >= 0 ? time : null
}

export function toDanmakuItems(items) {
  if (!Array.isArray(items)) return []

  return items
    .map((item) => {
      if (!item) return null
      const text = typeof item.text === 'string' ? item.text.trim() : ''
      if (!text) return null
      const time = normalizeTime(item)
      if (time === null) return null
      return {
        text,
        time,
        color: intToHex(item.color),
        mode: normalizeMode(item.mode),
        border: false
      }
    })
    .filter(Boolean)
    .sort((a, b) => a.time - b.time)
}

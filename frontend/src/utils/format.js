/** 接口中的 weekday：1=周一 … 7=周日 */
export const WEEKDAY_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

export function weekdayName(weekday) {
  return WEEKDAY_NAMES[Number(weekday) - 1] || ''
}

/** 当前星期（1=周一 … 7=周日） */
export function todayWeekday() {
  const day = new Date().getDay()
  return day === 0 ? 7 : day
}

/** 播放量等数字的简写：1.6万 / 3.2亿 */
export function formatCount(value) {
  const num = Number(value)
  if (!Number.isFinite(num) || num < 0) return ''
  if (num >= 100000000) return `${(num / 100000000).toFixed(1)}亿`
  if (num >= 10000) return `${(num / 10000).toFixed(1)}万`
  return String(num)
}

/** 评分展示：保留一位小数，0 分视为暂无评分 */
export function formatScore(score) {
  const num = Number(score)
  if (!Number.isFinite(num) || num <= 0) return ''
  return num.toFixed(1)
}

/** 更新信息，例如 "04|周日16:05后" */
export function remarkText(remarks) {
  return typeof remarks === 'string' ? remarks.trim() : ''
}

/** 去掉简介中的日文原文部分，只保留中文简介 */
export function plainDescription(description) {
  if (typeof description !== 'string') return ''
  return description.split('[简介原文]')[0].trim()
}

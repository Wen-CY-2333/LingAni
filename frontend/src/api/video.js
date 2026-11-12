import backend from './backend'

/** 分类列表：zoneId 必填（1=TV番组，2=剧场版），支持 year / tag 筛选与分页 */
export function getVideos({ zoneId = 1, year, tag, page = 1, pageSize = 24 } = {}) {
  const params = { zoneId, page, pageSize }
  if (year) params.year = year
  if (tag) params.tag = tag
  return backend.get('/api/cycani/videos', { params })
}

/** 搜索番剧 */
export function searchVideos(q, { page = 1, pageSize = 24 } = {}) {
  return backend.get('/api/cycani/videos/search', {
    params: { q, page, pageSize }
  })
}

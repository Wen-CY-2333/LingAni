import backend from './backend'

/** 动漫详情 */
export function getVideoDetail(videoId) {
  return backend.get(`/api/cycani/videos/${videoId}`)
}

/** 章节列表 */
export function getSections(videoId, { page = 1, pageSize = 48 } = {}) {
  return backend.get(`/api/cycani/videos/${videoId}/sections`, {
    params: {
      playerCode: 'cychub',
      page,
      pageSize
    }
  })
}

/** 相关推荐 */
export function getRecommendations(videoId, limit = 12) {
  return backend.get(`/api/cycani/videos/${videoId}/recommendations`, { params: { limit } })
}

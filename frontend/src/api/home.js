import backend from './backend'

/** 首页 Banner 轮播 */
export function getBanners() {
  return backend.get('/api/cycani/adverts', { params: { position: 'banner' } })
}

/** 首页推荐（按 TV番组 / 剧场版 分组返回） */
export function getRecommend() {
  return backend.get('/api/cycani/index/recommend')
}

/** 追番周表 */
export function getWeekday() {
  return backend.get('/api/cycani/index/weekday')
}

/** 排行榜分类 */
export function getRanks() {
  return backend.get('/api/cycani/ranks')
}

/** 指定榜单的视频列表 */
export function getRankVideos(rankId) {
  return backend.get(`/api/cycani/ranks/${rankId}/videos`)
}

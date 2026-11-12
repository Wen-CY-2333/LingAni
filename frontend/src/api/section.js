import backend from './backend'

/**
 * 获取章节视频直链：经由灵漫后端代理播放源接口。
 */
export function getPlayUrl(sectionId) {
  return backend.get(`/api/cycani/play/${sectionId}`)
}

import { ref } from 'vue'

/** 当前页面上下文（如播放页的 videoId/sectionId/progress），仅用于让小灵理解用户所在位置。 */
export const agentPageContext = ref(null)

export function setAgentPageContext(context) {
  agentPageContext.value = context
}

export function clearAgentPageContext() {
  agentPageContext.value = null
}

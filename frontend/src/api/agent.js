import backend from '@/api/backend'
import { getToken } from '@/stores/auth'

// Python Agent 服务地址，与后端保持一致：默认同源，由 Vite 代理 /agent-api 转发到 8000
export const AGENT_BASE_URL = (
  import.meta.env.VITE_AGENT_BASE_URL || '/agent-api'
).replace(/\/+$/, '')

/**
 * 用用户 JWT 向 Spring Boot 换取一次性的服务间身份上下文。
 * 复用后端 axios 实例，自动携带 Token 并沿用统一的错误处理。
 */
export async function createAgentContext() {
  if (!getToken()) throw new Error('请先登录后再使用小灵')

  const data = await backend.post('/api/internal/agent/context')
  if (!data?.token) throw new Error('获取 AI 身份上下文失败，请重新登录后再试')
  return data.token
}

function parseEvent(raw) {
  let event = 'message'
  const dataLines = []
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (!dataLines.length) return null
  try {
    return { event, data: JSON.parse(dataLines.join('\n')) }
  } catch {
    return null
  }
}

/**
 * 调用 POST /api/agent/chat/stream，逐事件回调。
 * 返回的 Promise 在流结束（done / error）后 resolve。
 */
export async function streamAgentChat({ message, threadId, context, signal, onEvent }) {
  const contextToken = await createAgentContext()
  const response = await fetch(`${AGENT_BASE_URL}/api/agent/chat/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${contextToken}`
    },
    body: JSON.stringify({ message, thread_id: threadId || null, context: context || undefined }),
    signal
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.msg || `AI 服务请求失败（HTTP ${response.status}）`)
  }
  if (!response.body) throw new Error('当前浏览器不支持流式响应')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    let index = buffer.indexOf('\n\n')
    while (index >= 0) {
      const raw = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      const parsed = parseEvent(raw)
      if (parsed) {
        onEvent?.(parsed.event, parsed.data)
        if (parsed.event === 'done' || parsed.event === 'error') {
          await reader.cancel().catch(() => {})
          return
        }
      }
      index = buffer.indexOf('\n\n')
    }
  }
}

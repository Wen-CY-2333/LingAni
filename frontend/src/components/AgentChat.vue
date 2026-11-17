<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { RotateCcw, Send, X } from 'lucide-vue-next'
import Live2DAvatar from '@/components/Live2DAvatar.vue'
import { ensureMascotPosition, mascotDisplaySize, mascotPosition, mascotSettings } from '@/stores/mascot'
import { isLoggedIn } from '@/stores/auth'
import { agentPageContext } from '@/stores/agentContext'
import { streamAgentChat } from '@/api/agent'
import { getNotifications, getUnreadNotificationCount, markNotificationRead } from '@/api/backend'

const TOOL_LABELS = {
  search_anime: '搜索动漫',
  get_anime_detail: '查询动漫详情',
  get_anime_sections: '查询章节',
  get_anime_recommendations: '查找相似推荐',
  get_home_recommend: '获取首页推荐',
  get_weekday_schedule: '查询追番周表',
  get_rank_list: '获取排行榜',
  get_rank_videos: '查询榜单内容',
  get_user_history: '读取观看历史',
  get_user_progress: '读取观看进度',
  get_user_favorites: '读取追番列表',
  add_favorite: '添加追番',
  check_favorite: '检查追番状态'
}

const QUICK_PROMPTS = ['本周有什么新番更新？', '推荐几部高分动漫', '现在有哪些排行榜？', '我最近看到哪了？']
const PLAY_PROMPTS = ['给我前情提要', '这一集有什么看点', '我播到哪了']
const DETAIL_PROMPTS = ['这部讲什么？', '有什么看点？', '值得追吗？']

const open = ref(false)
const input = ref('')
const messages = ref([])
const loading = ref(false)
const toolStatus = ref('')
const threadId = ref(null)
const listRef = ref(null)
const inputRef = ref(null)
const router = useRouter()
const live2dReady = ref(true)

/** 聊天框统一尺寸，所有页面一致。 */
const PANEL_WIDTH = 384
const PANEL_HEIGHT = 568
/** 聊天框与看板娘的水平间距。 */
const PANEL_GAP = 16
const draggingMascot = ref(false)

ensureMascotPosition()

/** 聊天框紧贴看板娘、对齐角色上半身，拖动看板娘时一起移动。 */
const panelStyle = computed(() => {
  const width = Math.min(PANEL_WIDTH, window.innerWidth - 24)
  const height = Math.min(PANEL_HEIGHT, window.innerHeight - 24)
  const mascot = mascotDisplaySize()
  const rightSideLeft = mascotPosition.left + mascot.width + PANEL_GAP
  const leftSideLeft = mascotPosition.left - width - PANEL_GAP
  // 看板娘在屏幕左半边时聊天框靠右，右半边时靠左；首选一侧放不下再换另一侧
  const preferRight = mascotPosition.left + mascot.width / 2 < window.innerWidth / 2
  const fitsRight = rightSideLeft + width <= window.innerWidth - 12
  const fitsLeft = leftSideLeft >= 12
  let left = preferRight
    ? (fitsRight ? rightSideLeft : leftSideLeft)
    : (fitsLeft ? leftSideLeft : rightSideLeft)
  left += mascotSettings.panelOffsetX
  left = Math.min(Math.max(12, left), Math.max(12, window.innerWidth - width - 12))
  const top = Math.min(
    Math.max(12, mascotPosition.top + mascotSettings.panelOffsetY),
    Math.max(12, window.innerHeight - height - 12)
  )
  return { left: `${left}px`, top: `${top}px`, right: 'auto', bottom: 'auto' }
})

let hideTimer = null
/** 输入法组词中（拼音尚未上屏），此时不能被鼠标移开打断。 */
const composing = ref(false)

function cancelHide() {
  if (hideTimer) {
    clearTimeout(hideTimer)
    hideTimer = null
  }
}

function showPanel() {
  cancelHide()
  open.value = true
}

/** 正在输入（有草稿或输入法组词）时不自动隐藏，发送后恢复离开即隐藏。 */
function isTyping() {
  return composing.value || input.value.trim().length > 0
}

function scheduleHide() {
  if (draggingMascot.value) return
  if (isTyping()) return
  cancelHide()
  // 留一点缓冲，方便鼠标从看板娘平滑移到聊天框
  hideTimer = setTimeout(() => {
    if (isTyping()) {
      hideTimer = null
      return
    }
    open.value = false
    hideTimer = null
  }, 300)
}

function onCompositionStart() {
  composing.value = true
  cancelHide()
}

function onCompositionEnd() {
  composing.value = false
}

function onMascotHover(hovered) {
  if (hovered) showPanel()
  else scheduleHide()
}

function onMascotDragging(dragging) {
  draggingMascot.value = dragging
  if (dragging) showPanel()
}

/** 看板娘拖动位置写入共享状态，设置页也能读取。 */
function onMascotMove(position) {
  mascotPosition.left = position.left
  mascotPosition.top = position.top
}

let controller = null

/** 播放页、详情页都属于陪伴场景，聊天框切换为对应的快捷提问。 */
const companionPage = computed(() => (agentPageContext.value?.current_video_id ? agentPageContext.value.page || 'play' : ''))
const quickPrompts = computed(() => {
  if (companionPage.value === 'detail') return DETAIL_PROMPTS
  if (companionPage.value === 'play') return PLAY_PROMPTS
  return QUICK_PROMPTS
})

const canSend = computed(() => input.value.trim().length > 0 && !loading.value)

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function worksOf(item) {
  return (item.works || []).filter((work) => work?.videoId && work?.title)
}

/** 把文本节点里出现的真实番名替换成可跳转的彩色链接，不动其他 HTML。 */
function linkifyAnimeTitles(root, works) {
  const idByTitle = new Map(works.map((work) => [work.title, String(work.videoId)]))
  const alternatives = [...idByTitle.keys()].sort((a, b) => b.length - a.length).map(escapeRegExp)
  const pattern = new RegExp(alternatives.join('|'), 'g')
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  const nodes = []
  while (walker.nextNode()) nodes.push(walker.currentNode)

  for (const node of nodes) {
    if (node.parentElement?.closest('a')) continue
    const text = node.nodeValue || ''
    pattern.lastIndex = 0
    if (!pattern.test(text)) continue
    pattern.lastIndex = 0

    const fragment = document.createDocumentFragment()
    let cursor = 0
    let match
    // 书名号、引号紧贴番名时，一并纳入链接范围
    const quotePairs = { '《': '》', '「': '」', '『': '』', '“': '”' }
    while ((match = pattern.exec(text)) !== null) {
      let start = match.index
      let end = match.index + match[0].length
      const closeQuote = quotePairs[text[start - 1]]
      if (closeQuote && text[end] === closeQuote) {
        start -= 1
        end += 1
      }
      if (start > cursor) fragment.appendChild(document.createTextNode(text.slice(cursor, start)))
      const link = document.createElement('a')
      link.className = 'anime-link'
      link.dataset.animeId = idByTitle.get(match[0])
      link.textContent = text.slice(start, end)
      fragment.appendChild(link)
      cursor = end
    }
    if (cursor < text.length) fragment.appendChild(document.createTextNode(text.slice(cursor)))
    node.parentNode?.replaceChild(fragment, node)
  }
}

/** 回复按 Markdown 渲染，再把其中出现的真实番名变成可点击链接。 */
function renderReply(item) {
  const html = DOMPurify.sanitize(marked.parse(item.content || ''))
  const works = worksOf(item)
  if (!works.length) return html
  const doc = new DOMParser().parseFromString(html, 'text/html')
  linkifyAnimeTitles(doc.body, works)
  return doc.body.innerHTML
}

/** reply 中没有原样出现的作品，补一行链接，避免漏掉可跳转入口。 */
function orphanWorks(item) {
  return worksOf(item).filter((work) => !(item.content || '').includes(work.title))
}

function actionPath(action) {
  return action?.type === 'navigate' && typeof action.path === 'string' ? action.path : ''
}

/** 只放行站内真实存在的路由，避免跳到不存在的地址后被重定向回首页。 */
function safePath(path) {
  if (!path) return ''
  const normalized = path.trim()
  return /^\/(search(\?[^#]*)?|rank|schedule|category|user\/(history|favorites)|anime\/\d+(\/play\/\d+)?)$/.test(normalized) ? normalized : ''
}

/** 回复结束后自动跳转到第一个合法跳转动作，已在同一路由则不重复跳转。 */
function autoNavigate(actions) {
  const target = (actions || [])
    .map(actionPath)
    .map(safePath)
    .find(Boolean)
  if (!target) return
  const route = router.currentRoute.value
  if (target === route.path || target === route.fullPath) return
  // 自动跳转保持聊天框打开，方便用户继续追问；不收起面板
  router.push(target).catch(() => {})
}

/** 手动点击番名链接跳转后收起面板，避免挡住新页面内容。 */
function go(path) {
  if (!path) return
  open.value = false
  cancelHide()
  router.push(path).catch(() => {})
}

function openAnime(event) {
  const chip = event.target.closest('[data-anime-id]')
  if (!chip) return
  event.preventDefault()
  go(`/anime/${chip.getAttribute('data-anime-id')}`)
}

watch(
  messages,
  async () => {
    await nextTick()
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  },
  { deep: true }
)

watch(open, async (value) => {
  if (!value) return
  await nextTick()
  inputRef.value?.focus()
})

function togglePanel() {
  open.value = !open.value
}

function resetChat() {
  messages.value = []
  threadId.value = null
  controller?.abort()
}

function usePrompt(text) {
  if (loading.value) return
  input.value = text
  send()
}

function onKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return

  input.value = ''
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '', error: false, works: [], actions: [] })

  const reply = messages.value[messages.value.length - 1]
  loading.value = true
  toolStatus.value = ''
  controller = new AbortController()

  try {
    await streamAgentChat({
      message: text,
      threadId: threadId.value,
      context: agentPageContext.value,
      signal: controller.signal,
      onEvent(event, data) {
        if (event === 'meta') {
          threadId.value = data?.thread_id || threadId.value
        } else if (event === 'token') {
          toolStatus.value = ''
          reply.content += data?.text || ''
        } else if (event === 'tool') {
          toolStatus.value = `正在${TOOL_LABELS[data?.name] || '查询资料'}…`
        } else if (event === 'done') {
          toolStatus.value = ''
          if (data?.thread_id) threadId.value = data.thread_id
          reply.works = Array.isArray(data?.works) ? data.works : []
          reply.actions = Array.isArray(data?.actions) ? data.actions : []
          autoNavigate(reply.actions)
        } else if (event === 'error') {
          toolStatus.value = ''
          reply.error = true
          reply.content = data?.msg || '小灵暂时无法回答，请稍后再试'
        }
      }
    })
  } catch (error) {
    if (error?.name !== 'AbortError') {
      reply.error = true
      reply.content = error?.message || '网络异常，请稍后再试'
    }
  } finally {
    loading.value = false
    toolStatus.value = ''
    controller = null
  }
}

/* ---------------- 通知气泡 ---------------- */
const mascotNotice = ref(null)
const dismissedNoticeId = ref(null)
let noticeTimer = null

const noticeStyle = computed(() => {
  const width = Math.min(280, window.innerWidth - 24)
  return { left: 'auto', right: '24px', top: 'calc(var(--header-height) + 12px)', width: `${width}px` }
})

async function loadMascotNotice() {
  if (!isLoggedIn.value) {
    mascotNotice.value = null
    return
  }
  try {
    const count = Number(await getUnreadNotificationCount()) || 0
    if (count <= 0) {
      mascotNotice.value = null
      return
    }
    const data = await getNotifications({ current: 1, size: 1 })
    const item = data?.records?.[0] || null
    mascotNotice.value = item && item.id !== dismissedNoticeId.value ? item : null
  } catch {
    mascotNotice.value = null
  }
}

async function handleMascotNoticeClick() {
  const item = mascotNotice.value
  if (!item) return
  try {
    await markNotificationRead(item.id)
    window.dispatchEvent(new CustomEvent('lingani:notifications-refresh'))
  } catch {
    // 标记失败也允许跳转
  }
  mascotNotice.value = null
  if (item.videoId) {
    router.push(`/anime/${item.videoId}`)
  }
}

function dismissMascotNotice() {
  dismissedNoticeId.value = mascotNotice.value?.id || null
  mascotNotice.value = null
}

onMounted(() => {
  loadMascotNotice()
  noticeTimer = window.setInterval(loadMascotNotice, 60_000)
  window.addEventListener('lingani:notifications-refresh', loadMascotNotice)
})

onBeforeUnmount(() => {
  controller?.abort()
  cancelHide()
  window.clearInterval(noticeTimer)
  window.removeEventListener('lingani:notifications-refresh', loadMascotNotice)
})
</script>

<template>
  <div class="agent">
    <Transition name="agent-panel">
      <section
        v-if="open"
        class="agent-panel"
        :style="panelStyle"
        @mouseenter="cancelHide"
        @mouseleave="scheduleHide"
      >
        <header class="agent-header">
          <div class="agent-title">
            <strong>小灵</strong>
          </div>
          <button class="agent-reset" type="button" aria-label="重置会话" @click="resetChat">
            <RotateCcw :size="16" />
          </button>
        </header>

        <div ref="listRef" class="agent-messages">
          <template v-if="!messages.length">
            <div class="agent-welcome">
              <p v-if="companionPage === 'detail'">我在呢，想了解这部作品的看点，还是让我帮你判断值不值得追？</p>
              <p v-else-if="companionPage === 'play'">我在呢，想让我帮你回忆前情，还是说说这集的看点？</p>
              <p v-else>你好，我是小灵。可以帮你找番、看详情、查推荐与排行榜，也能根据你的观看历史和追番来推荐番剧。</p>
              <div class="agent-prompts">
                <button v-for="item in quickPrompts" :key="item" type="button" @click="usePrompt(item)">
                  {{ item }}
                </button>
              </div>
            </div>
          </template>

          <div
            v-for="(item, index) in messages"
            :key="index"
            class="agent-bubble"
            :class="[item.role, { 'is-error': item.error }]"
          >
            <template v-if="item.role === 'user'">{{ item.content }}</template>
            <template v-else>
              <div class="agent-reply" v-html="renderReply(item)" @click="openAnime" />
              <div v-if="orphanWorks(item).length" class="agent-works">
                <a
                  v-for="work in orphanWorks(item)"
                  :key="work.videoId"
                  class="anime-link"
                  role="button"
                  :data-anime-id="work.videoId"
                  @click.prevent="go(`/anime/${work.videoId}`)"
                >
                  {{ work.title }}
                </a>
              </div>
            </template>
          </div>

          <div v-if="toolStatus" class="agent-status">{{ toolStatus }}</div>
          <div v-else-if="loading && !toolStatus" class="agent-status">小灵正在思考…</div>
        </div>

        <div v-if="!isLoggedIn" class="agent-login">
          <p>登录后即可使用小灵。</p>
          <RouterLink to="/login" @click="togglePanel">去登录</RouterLink>
        </div>

        <footer v-else class="agent-input">
          <textarea
            ref="inputRef"
            v-model="input"
            rows="1"
            maxlength="4000"
            placeholder="问问小灵，比如：推荐几部热血番"
            @keydown="onKeydown"
            @compositionstart="onCompositionStart"
            @compositionend="onCompositionEnd"
            @focus="cancelHide"
          />
          <button type="button" :disabled="!canSend" aria-label="发送" @click="send">
            <Send :size="17" />
          </button>
        </footer>
      </section>
    </Transition>

    <Transition name="notice-pop">
      <div v-if="mascotNotice" class="mascot-notice" :style="noticeStyle">
        <span class="mascot-notice-text" @click="handleMascotNoticeClick">{{ mascotNotice.content }}</span>
        <button type="button" class="mascot-notice-close" aria-label="关闭提醒" @click="dismissMascotNotice">
          <X :size="12" />
        </button>
      </div>
    </Transition>

    <Live2DAvatar
      v-if="live2dReady"
      :position="mascotPosition"
      @update:position="onMascotMove"
      @activate="showPanel"
      @hover="onMascotHover"
      @dragging="onMascotDragging"
      @unavailable="live2dReady = false"
    />
  </div>
</template>

<style scoped lang="scss">
.agent {
  position: relative;
  z-index: 110;
}

.mascot-notice {
  position: fixed;
  z-index: 108;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 16px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 18px;
  background-color: rgba(18, 18, 18, 0.55);
  backdrop-filter: blur(6px);
  color: hsl(var(--foreground));
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.35);
}

.mascot-notice-text {
  flex: 1;
  font-size: 13px;
  line-height: 1.6;
  color: hsl(var(--foreground));
  cursor: pointer;
}

.mascot-notice-close {
  display: inline-flex;
  color: hsl(var(--muted-foreground));
  transition: color 0.2s ease;

  &:hover {
    color: hsl(var(--foreground));
  }
}

.notice-pop-enter-active,
.notice-pop-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.notice-pop-enter-from,
.notice-pop-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.agent-panel {
  position: fixed;
  right: 28px;
  bottom: 96px;
  display: flex;
  flex-direction: column;
  width: min(384px, calc(100vw - 24px));
  height: min(568px, calc(100vh - 24px));
  overflow: hidden;
  border: 1px solid hsl(var(--border));
  border-radius: 20px;
  background: hsl(var(--card) / 0.98);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(14px);
}

.agent-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid hsl(var(--border));
}

.agent-title {
  display: flex;
  flex-direction: column;
  line-height: 1.3;

  strong {
    font-size: 15px;
  }
}

.agent-reset {
  margin-left: auto;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: hsl(var(--muted-foreground));
  cursor: pointer;

  &:hover {
    background: hsl(var(--muted));
    color: hsl(var(--foreground));
  }
}

.agent-messages {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  overflow-y: auto;
}

.agent-welcome {
  display: flex;
  flex-direction: column;
  gap: 14px;
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  line-height: 1.7;
}

.agent-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  button {
    padding: 6px 11px;
    border: 1px solid hsl(var(--border));
    border-radius: 999px;
    background: hsl(var(--muted) / 0.6);
    color: hsl(var(--foreground));
    font-size: 12px;
    cursor: pointer;

    &:hover {
      border-color: hsl(var(--brand) / 0.6);
      color: hsl(var(--brand));
    }
  }
}

.agent-bubble {
  max-width: 86%;
  padding: 9px 13px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;

  &.user {
    align-self: flex-end;
    border-bottom-right-radius: 4px;
    background: hsl(var(--brand));
    color: #221014;
    white-space: pre-wrap;
  }

  &.assistant {
    align-self: flex-start;
    border-bottom-left-radius: 4px;
    background: hsl(var(--muted));
    color: hsl(var(--foreground));
  }

  &.is-error {
    border: 1px solid hsl(var(--brand) / 0.5);
    background: hsl(var(--brand-subtle));
    color: hsl(var(--brand));
  }
}

.agent-reply {
  word-break: break-word;

  :deep(p) {
    margin: 0 0 6px;
  }

  :deep(p:last-child) {
    margin-bottom: 0;
  }

  :deep(ul),
  :deep(ol) {
    margin: 4px 0 6px;
    padding-left: 18px;
  }

  :deep(li) {
    margin: 2px 0;
  }

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 8px 0 4px;
    font-size: 14px;
    font-weight: 600;
  }

  :deep(strong) {
    font-weight: 600;
  }

  :deep(code) {
    padding: 1px 4px;
    border-radius: 4px;
    background: hsl(var(--muted));
    font-size: 12px;
  }

  :deep(blockquote) {
    margin: 4px 0;
    padding-left: 8px;
    border-left: 2px solid hsl(var(--border));
    color: hsl(var(--muted-foreground));
  }
}

.agent-works {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}

.anime-link,
.agent-reply :deep(.anime-link) {
  color: hsl(var(--brand));
  text-decoration: none;
  cursor: pointer;

  &:hover {
    color: hsl(var(--brand) / 0.8);
  }
}

.agent-status {
  align-self: flex-start;
  padding: 4px 10px;
  border-radius: 999px;
  background: hsl(var(--muted) / 0.7);
  color: hsl(var(--muted-foreground));
  font-size: 11px;
  animation: agent-pulse 1.4s ease-in-out infinite;
}

.agent-login {
  padding: 16px;
  border-top: 1px solid hsl(var(--border));
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  line-height: 1.7;

  a {
    display: inline-block;
    margin-top: 10px;
    padding: 7px 16px;
    border-radius: 999px;
    background: hsl(var(--brand));
    color: #221014;
    font-weight: 600;
    text-decoration: none;
  }
}

.agent-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid hsl(var(--border));

  textarea {
    flex: 1;
    max-height: 110px;
    min-height: 38px;
    padding: 9px 12px;
    border: 1px solid hsl(var(--input));
    border-radius: 12px;
    background: hsl(var(--muted) / 0.5);
    color: hsl(var(--foreground));
    font-size: 13px;
    line-height: 1.5;
    resize: none;
    outline: none;

    &:focus {
      border-color: hsl(var(--brand) / 0.7);
    }
  }

  button {
    display: grid;
    place-items: center;
    width: 38px;
    height: 38px;
    border: 0;
    border-radius: 12px;
    background: hsl(var(--brand));
    color: #221014;
    cursor: pointer;

    &:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
  }
}

.agent-panel-enter-active,
.agent-panel-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.agent-panel-enter-from,
.agent-panel-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}

@keyframes agent-pulse {
  0%,
  100% {
    opacity: 0.6;
  }
  50% {
    opacity: 1;
  }
}

@media (max-width: 640px) {
  .agent-panel {
    right: 16px;
    bottom: 84px;
    width: calc(100vw - 24px);
    height: calc(100dvh - 24px);
  }
}
</style>

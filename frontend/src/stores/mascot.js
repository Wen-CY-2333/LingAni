import { reactive } from 'vue'

const STORAGE_KEY = 'lingani:mascot'

/** 自定义模型地址的选项标识。 */
export const CUSTOM_MODEL_ID = 'custom'

/** 内置 Live2D 模型列表，新增模型资源时在这里补充即可。 */
export const MASCOT_MODELS = [
  { id: 'rem', name: '雷姆（rem）', url: '/live2d/model/rem/model.json' }
]

export const MASCOT_LIMITS = {
  modelScale: { min: 0.5, max: 2, step: 0.05 },
  panelOffsetX: { min: -200, max: 400, step: 10 },
  panelOffsetY: { min: -600, max: 600, step: 10 }
}

/** 聊天框相对看板娘的展示方位由看板娘位置自动决定，不再提供手动设置。 */

const DEFAULT_MODEL = MASCOT_MODELS[0]

const DEFAULTS = {
  modelId: DEFAULT_MODEL.id,
  customModelUrl: '',
  canvasWidth: 340,
  canvasHeight: 750,
  modelScale: 1,
  panelOffsetX: 0,
  panelOffsetY: 96,
  defaultPosition: null
}

function clampNumber(value, range, fallback, precision = 0) {
  const num = Number(value)
  if (!Number.isFinite(num)) return fallback
  const clamped = Math.min(range.max, Math.max(range.min, num))
  const factor = 10 ** precision
  return Math.round(clamped * factor) / factor
}

function normalize(raw) {
  const source = raw && typeof raw === 'object' ? raw : {}
  const validModel =
    source.modelId === CUSTOM_MODEL_ID || MASCOT_MODELS.some((item) => item.id === source.modelId)
  const position = source.defaultPosition
  const hasPosition =
    position && Number.isFinite(Number(position.left)) && Number.isFinite(Number(position.top))

  return {
    modelId: validModel ? source.modelId : DEFAULTS.modelId,
    customModelUrl: typeof source.customModelUrl === 'string' ? source.customModelUrl.trim() : '',
    canvasWidth: clampNumber(source.canvasWidth, { min: 200, max: 600 }, DEFAULTS.canvasWidth),
    canvasHeight: clampNumber(source.canvasHeight, { min: 400, max: 1000 }, DEFAULTS.canvasHeight),
    modelScale: clampNumber(source.modelScale, MASCOT_LIMITS.modelScale, DEFAULTS.modelScale, 2),
    panelOffsetX: clampNumber(source.panelOffsetX, MASCOT_LIMITS.panelOffsetX, DEFAULTS.panelOffsetX),
    panelOffsetY: clampNumber(source.panelOffsetY, MASCOT_LIMITS.panelOffsetY, DEFAULTS.panelOffsetY),
    defaultPosition: hasPosition
      ? { left: Math.round(Number(position.left)), top: Math.round(Number(position.top)) }
      : null
  }
}

function readStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/** 全局看板娘配置，修改后 Live2D 组件会实时响应。 */
export const mascotSettings = reactive(normalize(readStorage()))

/** 看板娘当前位置（左上角坐标），拖动与设置页共享。 */
export const mascotPosition = reactive({ left: 0, top: 0, ready: false })

/** 当前生效的模型地址。 */
export function mascotModelUrl(settings = mascotSettings) {
  if (settings.modelId === CUSTOM_MODEL_ID && settings.customModelUrl) {
    return settings.customModelUrl
  }
  const builtin = MASCOT_MODELS.find((item) => item.id === settings.modelId)
  return builtin?.url || DEFAULT_MODEL.url
}

/** 看板娘实际显示尺寸 = 画布尺寸 × 模型缩放。 */
export function mascotDisplaySize(settings = mascotSettings) {
  return {
    width: Math.round(settings.canvasWidth * settings.modelScale),
    height: Math.round(settings.canvasHeight * settings.modelScale)
  }
}

/** 贴右侧、垂直居中的默认位置。 */
export function autoMascotPosition(settings = mascotSettings) {
  const { width } = mascotDisplaySize(settings)
  return {
    left: Math.max(0, window.innerWidth - width),
    top: Math.max(0, window.innerHeight / 2 - 50)
  }
}

/** 解析配置中的默认位置，未配置时回落到自动位置。 */
export function defaultMascotPosition(settings = mascotSettings) {
  const { width } = mascotDisplaySize(settings)
  const base = settings.defaultPosition || autoMascotPosition(settings)
  return {
    left: Math.round(Math.min(Math.max(0, Number(base.left) || 0), Math.max(0, window.innerWidth - width))),
    top: Math.round(Math.min(Math.max(0, Number(base.top) || 0), Math.max(0, window.innerHeight - 80)))
  }
}

export function applyDefaultMascotPosition() {
  const position = defaultMascotPosition()
  mascotPosition.left = position.left
  mascotPosition.top = position.top
}

/** 首次挂载时初始化看板娘位置，之后保留用户拖动结果。 */
export function ensureMascotPosition() {
  if (mascotPosition.ready) return
  applyDefaultMascotPosition()
  mascotPosition.ready = true
}

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(mascotSettings))
  } catch {
    // 存储不可用时仅在当前会话生效
  }
}

export function saveMascotSettings(next) {
  Object.assign(mascotSettings, normalize(next))
  persist()
  applyDefaultMascotPosition()
}

export function resetMascotSettings() {
  Object.assign(mascotSettings, normalize(DEFAULTS))
  persist()
  applyDefaultMascotPosition()
}

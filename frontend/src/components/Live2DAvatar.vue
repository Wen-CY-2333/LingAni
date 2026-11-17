<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { mascotDisplaySize, mascotModelUrl, mascotSettings } from '@/stores/mascot'

const props = defineProps({
  position: { type: Object, required: true }
})
const emit = defineEmits(['activate', 'unavailable', 'hover', 'dragging', 'update:position'])

const host = ref(null)
let loaded = false
let tapReady = false
let disposed = false
let initializing = false
let drag = null
let reloadTimer = null

const modelUrl = computed(() => mascotModelUrl())
const displaySize = computed(() => mascotDisplaySize())

const entryStyle = computed(() => ({
  left: `${props.position.left}px`,
  top: `${props.position.top}px`,
  width: `${displaySize.value.width}px`,
  height: `${displaySize.value.height}px`
}))

/** 同一个运行时脚本只加载一次；脚本已插入但尚未执行完时，等待它的 load 事件。 */
const scriptPromises = new Map()

function loadScript(src) {
  if (scriptPromises.has(src)) return scriptPromises.get(src)

  const promise = new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[data-live2d="${src}"]`)
    if (existing) {
      if (typeof window.loadlive2d === 'function') {
        resolve()
        return
      }
      existing.addEventListener('load', () => resolve(), { once: true })
      existing.addEventListener('error', () => reject(new Error('Live2D 运行时加载失败')), { once: true })
      return
    }
    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.dataset.live2d = src
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Live2D 运行时加载失败'))
    document.head.appendChild(script)
  })

  scriptPromises.set(src, promise)
  return promise
}

async function initLive2D() {
  if (initializing) return
  initializing = true
  try {
    await loadScript('/live2d/js/live2d.js')
    if (disposed) return
    if (typeof window.loadlive2d !== 'function') {
      throw new Error('Live2D 运行时未就绪')
    }
    // 先确认模型可达，避免运行时加载失败后留下空模型
    const response = await fetch(modelUrl.value, { cache: 'force-cache' })
    if (!response.ok) throw new Error('Live2D 模型加载失败')
    if (disposed) return
    window.loadlive2d('live2d-canvas', modelUrl.value)
    loaded = true
    markTapReady()
  } catch (error) {
    console.warn('[Live2D] 初始化失败', error)
    emit('unavailable')
  } finally {
    initializing = false
  }
}

/** 模型解析是异步的，未就绪时派发点击会让运行时对空模型调用 startMotion 报错 */
function markTapReady() {
  tapReady = false
  setTimeout(() => {
    if (!disposed) tapReady = true
  }, 500)
}

onMounted(() => {
  // 捕获阶段，确保早于运行时挂在 window 上的冒泡监听器执行
  window.addEventListener('mousedown', blockMascotMousedown, true)
  initLive2D()
})

// 模型或画布尺寸变化后重新加载，保证渲染比例与命中区域正确
watch(
  () => [modelUrl.value, mascotSettings.canvasWidth, mascotSettings.canvasHeight],
  async () => {
    if (disposed || !loaded) return
    await nextTick()
    clearTimeout(reloadTimer)
    reloadTimer = setTimeout(async () => {
      if (disposed || typeof window.loadlive2d !== 'function') return
      try {
        const response = await fetch(modelUrl.value, { cache: 'force-cache' })
        if (!response.ok || disposed) return
        window.loadlive2d('live2d-canvas', modelUrl.value)
        markTapReady()
      } catch (error) {
        console.warn('[Live2D] 重新加载失败', error)
      }
    }, 150)
  }
)

function onPointerDown(event) {
  if (event.button !== 0) return
  drag = {
    x: event.clientX,
    y: event.clientY,
    left: props.position.left,
    top: props.position.top,
    moved: false
  }
  host.value?.setPointerCapture(event.pointerId)
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp, { once: true })
}

function onPointerMove(event) {
  if (!drag) return
  const dx = event.clientX - drag.x
  const dy = event.clientY - drag.y
  if (!drag.moved && Math.abs(dx) + Math.abs(dy) < 5) return
  if (!drag.moved) {
    drag.moved = true
    emit('dragging', true)
    emit('hover', true)
  }
  emit('update:position', {
    left: drag.left + dx,
    top: drag.top + dy
  })
}

function onPointerUp(event) {
  window.removeEventListener('pointermove', onPointerMove)
  const moved = drag?.moved
  drag = null
  if (moved) {
    emit('dragging', false)
    return
  }
  playTapMotion(event)
  emit('activate')
}

/** Live2D 运行时在 window 上监听 mousedown 播放点击动作。
 *  在捕获阶段拦截看板娘区域的原生 mousedown，避免拖动起手被当作点击；
 *  只有确认是点击（未拖动）时才补发一次 mousedown 触发动作。 */
function blockMascotMousedown(event) {
  if (host.value && host.value.contains(event.target)) {
    event.stopImmediatePropagation()
  }
}

/** 确认是点击（未拖动）后补发一次 mousedown，让运行时按原逻辑播放点击动作。 */
function playTapMotion(event) {
  if (!loaded || !tapReady) return
  window.dispatchEvent(
    new MouseEvent('mousedown', {
      bubbles: true,
      button: 0,
      buttons: 1,
      clientX: event.clientX,
      clientY: event.clientY
    })
  )
}

onBeforeUnmount(() => {
  disposed = true
  tapReady = false
  clearTimeout(reloadTimer)
  window.removeEventListener('mousedown', blockMascotMousedown, true)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  // 不再调用 loadlive2d(canvas, '')：空地址会在运行时留下未初始化的模型，
  // 之后任意 mousedown 都会触发 startMotion 空指针报错
})
</script>

<template>
  <div
    ref="host"
    class="live2d-entry"
    :style="entryStyle"
    title="和小灵聊聊"
    @pointerdown="onPointerDown"
    @mouseenter="emit('hover', true)"
    @mouseleave="emit('hover', false)"
  >
    <canvas
      id="live2d-canvas"
      class="live2d-canvas"
      :width="mascotSettings.canvasWidth"
      :height="mascotSettings.canvasHeight"
    />
  </div>
</template>

<style scoped>
.live2d-entry {
  position: fixed;
  z-index: 110;
  cursor: grab;
  user-select: none;
  touch-action: none;
}

.live2d-entry:active {
  cursor: grabbing;
}

.live2d-canvas {
  display: block;
  width: 100%;
  height: 100%;
}
</style>

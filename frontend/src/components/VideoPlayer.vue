<script setup>
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import Artplayer from 'artplayer'
import artplayerPluginDanmuku from 'artplayer-plugin-danmuku'
import { AlertTriangle, MessageSquareOff, MessageSquare } from 'lucide-vue-next'

const props = defineProps({
  url: { type: String, required: true },
  poster: { type: String, default: '' },
  title: { type: String, default: '' },
  danmaku: { type: Array, default: () => [] },
  initialTime: { type: Number, default: 0 }
})

const emit = defineEmits(['retry', 'progress', 'ended'])

const containerRef = ref(null)
const art = shallowRef(null)
const errorText = ref('')
const danmakuOff = ref(false)
const ready = ref(false)

function danmakuOption(list) {
  return {
    danmuku: list,
    speed: 5,
    margin: [12, 12],
    opacity: 0.92,
    fontSize: 22,
    antiOverlap: true,
    synchronousPlayback: false,
    emitter: false,
    visible: true,
    heatmap: false
  }
}

function plugin() {
  return art.value?.plugins?.artplayerPluginDanmuku || null
}

function init() {
  if (!containerRef.value || !props.url) return

  const instance = new Artplayer({
    container: containerRef.value,
    url: props.url,
    poster: props.poster,
    title: props.title,
    volume: 0.6,
    autoplay: false,
    autoSize: false,
    setting: true,
    playbackRate: true,
    fullscreen: true,
    fullscreenWeb: true,
    pip: true,
    airplay: true,
    hotkey: true,
    miniProgress: true,
    theme: '#f0787f',
    plugins: [artplayerPluginDanmuku(danmakuOption(props.danmaku))]
  })

  instance.on('ready', () => {
    ready.value = true
    errorText.value = ''
    if (props.initialTime > 0) {
      const duration = Number(instance.duration)
      const maxTime = Number.isFinite(duration) && duration > 0 ? Math.max(0, duration - 2) : props.initialTime
      instance.currentTime = Math.min(props.initialTime, maxTime)
    }
  })

  instance.on('video:timeupdate', () => {
    emit('progress', {
      time: Number(instance.currentTime) || 0,
      duration: Number(instance.duration) || 0
    })
  })

  instance.on('video:error', () => {
    errorText.value = '视频加载失败，可能是直链已过期或网络异常。'
  })

  instance.on('video:ended', () => {
    emit('ended')
  })

  art.value = instance
}

function toggleDanmaku() {
  const instance = plugin()
  if (!instance) return
  danmakuOff.value = !danmakuOff.value
  if (danmakuOff.value) instance.hide()
  else instance.show()
}

function retry() {
  errorText.value = ''
  emit('retry')
}

watch(
  () => props.url,
  (url, oldUrl) => {
    if (!url || url === oldUrl) return
    errorText.value = ''
    if (art.value) {
      art.value.url = url
    }
  }
)

watch(
  () => props.danmaku,
  (list) => {
    const instance = plugin()
    if (!instance) return
    // config 只更新配置，load() 不带参数才会清空旧队列并按新配置重新载入
    instance.config({ danmuku: list })
    instance.load().catch(() => {})
    if (danmakuOff.value) instance.hide()
  },
  { deep: false }
)

onMounted(init)

onBeforeUnmount(() => {
  if (art.value) {
    art.value.destroy(false)
    art.value = null
  }
})
</script>

<template>
  <div class="video-player">
    <div ref="containerRef" class="art-container"></div>

    <button type="button" class="danmaku-toggle" :title="danmakuOff ? '开启弹幕' : '关闭弹幕'" @click="toggleDanmaku">
      <MessageSquare v-if="!danmakuOff" :size="15" />
      <MessageSquareOff v-else :size="15" />
      <span>{{ danmakuOff ? '弹幕已关' : '弹幕' }}</span>
    </button>

    <div v-if="!ready && !errorText" class="player-loading">
      <span class="spinner"></span>
      <span>播放器加载中…</span>
    </div>

    <div v-if="errorText" class="player-error">
      <AlertTriangle :size="28" />
      <p class="error-title">{{ errorText }}</p>
      <p class="error-hint">播放地址由接口实时签发，可能已经过期。</p>
      <button type="button" class="btn-brand" @click="retry">重新获取播放地址</button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.video-player {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: 8px;
  background-color: #000;
}

.art-container {
  position: relative;
  z-index: 0;
  width: 100%;
  height: 100%;
}

.danmaku-toggle {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background-color: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12px;
  opacity: 0.85;
  transition: opacity 0.2s ease, background-color 0.2s ease;

  &:hover {
    opacity: 1;
    background-color: hsl(var(--brand));
  }
}

.player-loading {
  position: absolute;
  inset: 0;
  z-index: 4;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background-color: #050505;
  color: hsl(var(--muted-foreground));
  font-size: 13px;
}

.spinner {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.16);
  border-top-color: hsl(var(--brand));
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.player-error {
  position: absolute;
  inset: 0;
  z-index: 6;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  background-color: rgba(5, 5, 5, 0.92);
  color: hsl(var(--muted-foreground));
  text-align: center;

  svg {
    color: hsl(var(--brand));
  }
}

.error-title {
  max-width: 460px;
  font-size: 14px;
  color: hsl(var(--foreground));
}

.error-hint {
  font-size: 12px;
}
</style>

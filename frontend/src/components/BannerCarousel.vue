<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronLeft, ChevronRight, Play, Star } from 'lucide-vue-next'
import { getSections, getVideoDetail } from '@/api/anime'
import { formatScore } from '@/utils/format'

const props = defineProps({
  banners: { type: Array, default: () => [] }
})

const router = useRouter()
const details = ref({})
const index = ref(0)
const failedIds = ref([])
const watching = ref(false)
let timer = null

const slides = computed(() =>
  props.banners.map((banner) => ({
    ...banner,
    detail: details.value[banner.action_value] || null
  }))
)

const active = computed(() => slides.value[index.value] || null)

const chips = computed(() => {
  const detail = active.value?.detail
  if (!detail) return []
  const list = [detail.version, detail.area, detail.year ? `${detail.year}年` : '']
  const tags = Array.isArray(detail.tags) ? detail.tags : []
  tags.forEach((tag) => {
    if (list.length < 5 && typeof tag === 'string' && tag.length <= 5) list.push(tag)
  })
  return [...new Set(list.filter(Boolean))]
})

const activeScore = computed(() => formatScore(active.value?.detail?.score))

function select(nextIndex) {
  if (!slides.value.length) return
  index.value = (nextIndex + slides.value.length) % slides.value.length
}

function prev() {
  select(index.value - 1)
}

function next() {
  select(index.value + 1)
}

function pause() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function resume() {
  pause()
  if (slides.value.length > 1) {
    timer = setInterval(() => select(index.value + 1), 6000)
  }
}

function markFailed(id) {
  if (!failedIds.value.includes(id)) failedIds.value.push(id)
}

async function goWatch(banner) {
  if (!banner || watching.value) return
  watching.value = true
  try {
    if (banner.action_type === 'video' || /^\d+$/.test(String(banner.action_value))) {
      const data = await getSections(banner.action_value)
      const first = data?.records?.[0]
      if (first) {
        router.push(`/anime/${banner.action_value}/play/${first.id}`)
        return
      }
      router.push(`/anime/${banner.action_value}`)
    } else if (String(banner.action_value || '').startsWith('http')) {
      window.open(banner.action_value, '_blank', 'noopener')
    }
  } catch {
    router.push(`/anime/${banner.action_value}`)
  } finally {
    watching.value = false
  }
}

async function loadDetails() {
  const ids = props.banners
    .map((banner) => banner.action_value)
    .filter((id) => /^\d+$/.test(String(id || '')))
  const unique = [...new Set(ids)]

  await Promise.allSettled(
    unique.map(async (id) => {
      const detail = await getVideoDetail(id)
      if (detail) details.value = { ...details.value, [id]: detail }
    })
  )
}

watch(
  () => props.banners,
  (banners) => {
    index.value = 0
    if (banners.length) {
      loadDetails()
      resume()
    } else {
      pause()
    }
  },
  { immediate: true }
)

onMounted(resume)
onBeforeUnmount(pause)
</script>

<template>
  <section class="banner" @mouseenter="pause" @mouseleave="resume">
    <div class="slides">
      <div
        v-for="(slide, i) in slides"
        :key="slide.id"
        class="slide"
        :class="{ 'is-active': i === index }"
      >
        <img
          v-if="!failedIds.includes(slide.id)"
          :src="slide.content"
          :alt="slide.name"
          class="slide-image"
          @error="markFailed(slide.id)"
        />
        <div v-else class="slide-fallback">{{ slide.name }}</div>
      </div>
    </div>

    <div class="banner-mask"></div>

    <div class="container banner-content">
      <div class="banner-info">
        <div v-if="activeScore" class="score-pill">
          <Star :size="12" />
          <span>评分 {{ activeScore }}</span>
        </div>
        <h1 class="banner-title">{{ active?.detail?.title || active?.name }}</h1>
        <p v-if="active?.detail?.subtitle" class="banner-subtitle">{{ active.detail.subtitle }}</p>
        <div v-if="chips.length" class="banner-chips">
          <span v-for="chip in chips" :key="chip" class="chip">{{ chip }}</span>
        </div>
        <div class="banner-actions">
          <button type="button" class="btn-brand" :disabled="watching" @click="goWatch(active)">
            <Play :size="15" />
            {{ watching ? '正在获取集数…' : '立即观看' }}
          </button>
        </div>
      </div>
    </div>

    <button v-if="slides.length > 1" type="button" class="arrow arrow-prev" aria-label="上一张" @click="prev">
      <ChevronLeft :size="20" />
    </button>
    <button v-if="slides.length > 1" type="button" class="arrow arrow-next" aria-label="下一张" @click="next">
      <ChevronRight :size="20" />
    </button>

    <div v-if="slides.length > 1" class="dots">
      <button
        v-for="(slide, i) in slides"
        :key="`dot-${slide.id}`"
        type="button"
        class="dot"
        :class="{ 'is-active': i === index }"
        :aria-label="`切换到第 ${i + 1} 张`"
        @click="select(i)"
      ></button>
    </div>
  </section>
</template>

<style scoped lang="scss">
.banner {
  position: relative;
  width: 100%;
  height: clamp(320px, 56vw, 820px);
  overflow: hidden;
  background-color: hsl(var(--muted));

  @media (max-width: 640px) {
    height: auto;
    aspect-ratio: 3 / 4;
    max-height: 78vh;
  }
}

.slides,
.slide,
.slide-image,
.banner-mask {
  position: absolute;
  inset: 0;
}

.slide {
  opacity: 0;
  transition: opacity 0.7s ease;

  &.is-active {
    opacity: 1;
  }
}

.slide-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.slide-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: hsl(var(--muted-foreground));
  font-size: 14px;
  background: linear-gradient(140deg, #1c1c1c, #101010);
}

.banner-mask {
  background:
    linear-gradient(to top, rgba(10, 10, 10, 0.94) 0%, rgba(10, 10, 10, 0.62) 34%, rgba(10, 10, 10, 0.12) 68%, transparent 100%),
    linear-gradient(to right, rgba(10, 10, 10, 0.52), transparent 55%);
  pointer-events: none;
}

.banner-content {
  position: absolute;
  inset: auto 0 0;
  padding-bottom: 52px;
  pointer-events: none;
}

.banner-info {
  max-width: 660px;
  pointer-events: auto;
}

.score-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 10px;
  margin-bottom: 12px;
  border-radius: 999px;
  background-color: rgba(0, 0, 0, 0.55);
  border: 1px solid hsl(var(--border));
  color: hsl(var(--score));
  font-size: 12.5px;
  font-weight: 600;

  svg {
    fill: hsl(var(--score));
    color: hsl(var(--score));
  }
}

.banner-title {
  font-size: clamp(22px, 2.2vw, 32px);
  font-weight: 800;
  line-height: 1.25;
  color: #fff;
  text-shadow: 0 2px 18px rgba(0, 0, 0, 0.5);
}

.banner-subtitle {
  margin-top: 8px;
  font-size: 13.5px;
  color: rgba(255, 255, 255, 0.72);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.banner-actions {
  margin-top: 18px;
}

.arrow {
  position: absolute;
  top: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  transform: translateY(-50%);
  background-color: rgba(0, 0, 0, 0.45);
  color: #fff;
  opacity: 0;
  transition: opacity 0.2s ease, background-color 0.2s ease;

  &:hover {
    background-color: hsl(var(--brand));
  }
}

.arrow-prev {
  left: 18px;
}

.arrow-next {
  right: 18px;
}

.banner:hover .arrow {
  opacity: 1;
}

.dots {
  position: absolute;
  left: 50%;
  bottom: 22px;
  display: flex;
  gap: 8px;
  transform: translateX(-50%);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.35);
  transition: width 0.2s ease, background-color 0.2s ease;

  &.is-active {
    width: 18px;
    border-radius: 4px;
    background-color: hsl(var(--brand));
  }
}

@media (max-width: 640px) {
  .banner-content {
    padding-bottom: 44px;
  }

  .banner-subtitle {
    display: none;
  }
}
</style>

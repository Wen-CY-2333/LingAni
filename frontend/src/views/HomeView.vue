<script setup>
import { computed, onMounted, ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import { getBanners, getRecommend, getWeekday } from '@/api/home'
import BannerCarousel from '@/components/BannerCarousel.vue'
import WeekSchedule from '@/components/WeekSchedule.vue'
import RankList from '@/components/RankList.vue'
import AnimeGrid from '@/components/AnimeGrid.vue'

const banners = ref([])
const groups = ref([])
const weekday = ref([])
const bannersLoading = ref(true)
const recommendLoading = ref(true)
const weekdayLoading = ref(true)
const bannersError = ref('')
const recommendError = ref('')
const weekdayError = ref('')

const visibleGroups = computed(() =>
  groups.value
    .map((group) => ({
      ...group,
      videos: Array.isArray(group.videos) ? group.videos.slice(0, 18) : []
    }))
    .filter((group) => group.videos.length)
)

async function loadBanners() {
  bannersLoading.value = true
  bannersError.value = ''
  try {
    const data = await getBanners()
    banners.value = Array.isArray(data?.list) ? data.list : []
  } catch (err) {
    banners.value = []
    bannersError.value = err.message || 'Banner 加载失败'
  } finally {
    bannersLoading.value = false
  }
}

async function loadRecommend() {
  recommendLoading.value = true
  recommendError.value = ''
  try {
    const data = await getRecommend()
    groups.value = Array.isArray(data?.list) ? data.list : []
  } catch (err) {
    groups.value = []
    recommendError.value = err.message || '推荐内容加载失败'
  } finally {
    recommendLoading.value = false
  }
}

async function loadWeekday() {
  weekdayLoading.value = true
  weekdayError.value = ''
  try {
    const data = await getWeekday()
    weekday.value = Array.isArray(data?.list) ? data.list : []
  } catch (err) {
    weekday.value = []
    weekdayError.value = err.message || '追番周表加载失败'
  } finally {
    weekdayLoading.value = false
  }
}

onMounted(() => {
  loadBanners()
  loadRecommend()
  loadWeekday()
})
</script>

<template>
  <div class="home">
    <div class="home-hero">
      <div v-if="bannersLoading" class="banner-skeleton skeleton"></div>
      <BannerCarousel v-else-if="banners.length" :banners="banners" />
      <div v-else class="banner-empty">
        <div class="state-box">
          <p class="state-box__title">首页 Banner 暂无内容</p>
          <p class="state-box__hint">{{ bannersError || '接口未返回可用的 Banner 数据。' }}</p>
          <button type="button" class="btn-ghost" @click="loadBanners">
            <RefreshCw :size="14" /> 重新加载
          </button>
        </div>
      </div>
    </div>

    <div class="container home-body">
      <div class="top-grid">
        <div v-if="weekdayLoading" class="placeholder-card skeleton"></div>
        <WeekSchedule v-else-if="weekday.length" :data="weekday" />
        <div v-else class="state-box">
          <p class="state-box__title">追番周表加载失败</p>
          <p class="state-box__hint">{{ weekdayError }}</p>
          <button type="button" class="btn-ghost" @click="loadWeekday">
            <RefreshCw :size="14" /> 重试
          </button>
        </div>

        <RankList />
      </div>

      <template v-if="recommendLoading">
        <section v-for="n in 2" :key="n" class="recommend-block">
          <div class="section-head">
            <span class="skeleton skeleton-title"></span>
          </div>
          <div class="anime-grid skeleton-grid">
            <span v-for="i in 6" :key="i" class="skeleton skeleton-card"></span>
          </div>
        </section>
      </template>

      <template v-else-if="visibleGroups.length">
        <section v-for="group in visibleGroups" :key="group.id" class="recommend-block">
          <div class="section-head">
            <h2 class="section-title section-title--lg">{{ group.name }}</h2>
          </div>
          <AnimeGrid :videos="group.videos" />
        </section>
      </template>

      <div v-else class="state-box recommend-empty">
        <p class="state-box__title">推荐内容加载失败</p>
        <p class="state-box__hint">{{ recommendError }}</p>
        <button type="button" class="btn-ghost" @click="loadRecommend">
          <RefreshCw :size="14" /> 重试
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.home {
  /* 让首页 Banner 延伸到固定顶栏下方，配合顶栏渐变 */
  margin-top: calc(-1 * var(--header-height));
}

/* Banner 不参与 wrap 宽度限制，通栏铺满视口 */
.home-hero {
  margin-left: calc(50% - 50vw);
  margin-right: calc(50% - 50vw);
}

.banner-skeleton {
  height: clamp(320px, 56vw, 820px);

  @media (max-width: 640px) {
    height: 62vh;
  }
}

.banner-empty {
  padding: 24px var(--container-padding) 0;
}

.home-body {
  padding-top: 32px;
  padding-bottom: 12px;
}

.top-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--grid-gap);

  @media (min-width: 1024px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.placeholder-card {
  min-height: 306px;
  border-radius: 12px;
}

.recommend-block {
  margin-top: 42px;
}

.skeleton-title {
  display: block;
  width: 120px;
  height: 22px;
  border-radius: 6px;
}

.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;

  @media (min-width: 640px) {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  @media (min-width: 1024px) {
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: var(--grid-gap);
  }
}

.skeleton-card {
  display: block;
  aspect-ratio: 3 / 4;
  border-radius: 10px;
}

.recommend-empty {
  margin-top: 42px;
}
</style>

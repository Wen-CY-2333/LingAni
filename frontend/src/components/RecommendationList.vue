<script setup>
import { computed } from 'vue'
import AnimeGrid from './AnimeGrid.vue'

const props = defineProps({
  videos: { type: Array, default: () => [] },
  excludeId: { type: [String, Number], default: '' }
})

const list = computed(() =>
  (Array.isArray(props.videos) ? props.videos : []).filter((video) => {
    const id = video.video_id ?? video.id
    return String(id) !== String(props.excludeId)
  })
)
</script>

<template>
  <section class="recommendations">
    <div class="section-head">
      <h2 class="section-title">相关推荐</h2>
    </div>
    <AnimeGrid v-if="list.length" :videos="list" />
    <div v-else class="state-box">
      <p>暂无相关推荐</p>
    </div>
  </section>
</template>

<style scoped lang="scss">
.recommendations {
  min-width: 0;
}
</style>

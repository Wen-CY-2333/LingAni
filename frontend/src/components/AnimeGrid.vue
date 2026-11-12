<script setup>
import AnimeCard from './AnimeCard.vue'

defineProps({
  videos: { type: Array, default: () => [] }
})
</script>

<template>
  <div class="anime-grid">
    <AnimeCard v-for="video in videos" :key="video.video_id ?? video.id" :video="video" />
  </div>
</template>

<style scoped lang="scss">
.anime-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

@media (min-width: 640px) {
  .anime-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .anime-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: var(--grid-gap);
  }
}
</style>

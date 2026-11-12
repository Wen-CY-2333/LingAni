<script setup>
import { computed, ref } from 'vue'
import { Star } from 'lucide-vue-next'
import { formatScore, remarkText } from '@/utils/format'

const props = defineProps({
  video: { type: Object, required: true },
  rank: { type: Number, default: 0 }
})

const PLACEHOLDER = '/images/cover-placeholder.svg'
const failed = ref(false)

const videoId = computed(() => props.video.video_id ?? props.video.id)
const cover = computed(() => (failed.value || !props.video.cover_url ? PLACEHOLDER : props.video.cover_url))
const score = computed(() => formatScore(props.video.score))
const remark = computed(() => remarkText(props.video.remarks))

function onError() {
  failed.value = true
}
</script>

<template>
  <RouterLink :to="`/anime/${videoId}`" class="anime-card">
    <div class="cover">
      <img :src="cover" :alt="video.title" loading="lazy" decoding="async" @error="onError" />
      <span v-if="score" class="badge badge-score">
        <Star :size="11" />
        {{ score }}
      </span>
      <span v-if="remark" class="badge badge-remark">{{ remark }}</span>
      <span v-if="rank" class="badge badge-rank" :class="{ 'is-top': rank <= 3 }">{{ rank }}</span>
    </div>
    <div class="card-body">
      <h3 class="card-title">{{ video.title }}</h3>
      <p v-if="video.area || video.year" class="card-meta">
        {{ [video.area, video.year].filter(Boolean).join(' · ') }}
      </p>
    </div>
  </RouterLink>
</template>

<style scoped lang="scss">
.anime-card {
  display: block;
  min-width: 0;
  border-radius: 10px;
  overflow: hidden;
  background-color: hsl(var(--card));
  transition: transform 0.22s ease, box-shadow 0.22s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 24px rgba(0, 0, 0, 0.45);

    .cover img {
      transform: scale(1.05);
    }

    .card-title {
      color: hsl(var(--brand));
    }
  }
}

.cover {
  position: relative;
  aspect-ratio: 3 / 4;
  overflow: hidden;
  background-color: hsl(var(--muted));

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.35s ease;
  }
}

.badge {
  position: absolute;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  font-size: 11.5px;
  line-height: 1;
  background-color: rgba(0, 0, 0, 0.68);
  color: #dfe0e2;
  backdrop-filter: blur(4px);
  white-space: nowrap;
}

.badge-score {
  top: 8px;
  right: 8px;
  color: hsl(var(--score));
  font-weight: 600;
}

.badge-remark {
  bottom: 8px;
  right: 8px;
  max-width: calc(100% - 16px);
  overflow: hidden;
  text-overflow: ellipsis;
  color: hsl(var(--update));
}

.badge-rank {
  top: 8px;
  left: 8px;
  min-width: 24px;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  background-color: rgba(0, 0, 0, 0.72);

  &.is-top {
    background-color: hsl(var(--brand));
  }
}

.card-body {
  padding: 8px 10px 11px;
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  color: hsl(var(--foreground));
  transition: color 0.2s ease;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  margin-top: 4px;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>

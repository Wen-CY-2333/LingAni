<script setup>
import { computed, ref } from 'vue'
import { ArrowDownUp } from 'lucide-vue-next'

const props = defineProps({
  sections: { type: Array, default: () => [] },
  videoId: { type: [String, Number], required: true },
  sectionId: { type: [String, Number], default: '' },
  groupTitle: { type: String, default: '' },
  // 已观看记录，key 为 sectionId，值为 { progress, duration }
  history: { type: Object, default: () => ({}) },
  dense: { type: Boolean, default: false }
})

const ascending = ref(true)

const ordered = computed(() => {
  const list = Array.isArray(props.sections) ? [...props.sections] : []
  return ascending.value ? list : list.reverse()
})

function recordOf(section) {
  return props.history[String(section.id)] || null
}

function progressPercent(section) {
  const record = recordOf(section)
  const duration = Number(record?.duration) || 0
  const progress = Number(record?.progress) || 0
  if (duration <= 0 || progress <= 0) return 0
  return Math.min(100, Math.round((progress / duration) * 100))
}

function playTarget(section) {
  const record = recordOf(section)
  const duration = Number(record?.duration) || 0
  const progress = Number(record?.progress) || 0
  // 已经看完的章节从头播放，其余章节跳转到上次进度
  const finished = duration > 0 && progress + 5 >= duration
  if (progress > 1 && !finished) {
    return { path: `/anime/${props.videoId}/play/${section.id}`, query: { t: Math.floor(progress) } }
  }
  return `/anime/${props.videoId}/play/${section.id}`
}
</script>

<template>
  <section class="episode-list" :class="{ 'is-dense': dense }">
    <div class="head">
      <h2 class="section-title">选集</h2>
      <button
        v-if="sections.length > 1"
        type="button"
        class="sort-btn"
        :title="ascending ? '当前为正序，点击切换为倒序' : '当前为倒序，点击切换为正序'"
        @click="ascending = !ascending"
      >
        <ArrowDownUp :size="11" />
        {{ ascending ? '正序' : '倒序' }}
      </button>
      <span class="count">共 {{ sections.length }} 话</span>
    </div>

    <div v-if="ordered.length" class="grid">
      <RouterLink
        v-for="section in ordered"
        :key="section.id"
        :to="playTarget(section)"
        class="ep"
        :class="{
          'is-active': String(section.id) === String(sectionId),
          'is-watched': Boolean(recordOf(section))
        }"
        :title="recordOf(section) ? '已观看，点击继续播放' : section.title"
      >
        {{ section.title }}
        <span
          v-if="progressPercent(section)"
          class="ep-progress"
          :style="{ width: `${progressPercent(section)}%` }"
        ></span>
      </RouterLink>
    </div>
    <p v-else class="empty">暂无可用章节</p>
  </section>
</template>

<style scoped lang="scss">
.episode-list {
  min-width: 0;
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.count {
  align-self: flex-end;
  margin-left: auto;
  font-size: 12.5px;
  color: hsl(var(--muted-foreground));
}

.sort-btn {
  align-self: flex-end;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 24px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid hsl(var(--border));
  font-size: 11px;
  color: hsl(var(--muted-foreground));
  transition: color 0.2s ease, border-color 0.2s ease;

  &:hover {
    color: hsl(var(--foreground));
    border-color: hsl(var(--brand));
  }
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(78px, 1fr));
  gap: 8px;
  max-height: 330px;
  overflow-y: auto;
  padding-right: 4px;
}

.ep {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 42px;
  padding: 0 8px;
  border-radius: 6.5px;
  border: 1px solid hsl(var(--border));
  font-size: 13px;
  color: hsl(var(--foreground));
  transition: border-color 0.2s ease, background-color 0.2s ease, color 0.2s ease;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;

  &:hover {
    border-color: hsl(var(--brand));
    color: hsl(var(--brand));
  }

  // 看过的章节：品牌色描边 + 淡底，底部显示观看进度
  &.is-watched:not(.is-active) {
    border-color: hsl(var(--brand) / 0.5);
    background-color: hsl(var(--brand) / 0.09);
    color: hsl(var(--brand));
  }

  &.is-active {
    border-color: transparent;
    background-color: hsl(var(--brand));
    color: #fff;
    font-weight: 600;
  }
}

.ep-progress {
  position: absolute;
  left: 0;
  bottom: 0;
  height: 2px;
  background-color: hsl(var(--brand) / 0.85);
  pointer-events: none;
}

.ep.is-active .ep-progress {
  background-color: rgba(255, 255, 255, 0.85);
}

.is-dense .grid {
  grid-template-columns: repeat(auto-fill, minmax(68px, 1fr));
  max-height: 360px;
}

.is-dense .ep {
  height: 38px;
  font-size: 12.5px;
}

.empty {
  font-size: 13px;
  color: hsl(var(--muted-foreground));
  padding: 10px 0;
}
</style>

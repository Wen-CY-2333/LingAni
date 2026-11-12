<script setup>
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = defineProps({
  page: { type: Number, required: true },
  pageSize: { type: Number, required: true },
  total: { type: Number, required: true }
})

const emit = defineEmits(['change'])

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

const pages = computed(() => {
  const count = pageCount.value
  const current = props.page
  const items = []
  const push = (v) => items.push(v)

  push(1)
  const windowSize = 2
  const start = Math.max(2, current - windowSize)
  const end = Math.min(count - 1, current + windowSize)
  if (start > 2) push('…')
  for (let i = start; i <= end; i += 1) push(i)
  if (end < count - 1) push('…')
  if (count > 1) push(count)
  return items
})

function go(p) {
  if (p === '…') return
  if (p < 1 || p > pageCount.value || p === props.page) return
  emit('change', p)
}
</script>

<template>
  <nav v-if="pageCount > 1" class="pagination" aria-label="分页">
    <button
      type="button"
      class="pg-btn"
      :disabled="page <= 1"
      aria-label="上一页"
      @click="go(page - 1)"
    >
      <ChevronLeft :size="15" />
    </button>
    <button
      v-for="(p, i) in pages"
      :key="`${p}-${i}`"
      type="button"
      class="pg-btn"
      :class="{ 'is-active': p === page, 'is-ellipsis': p === '…' }"
      :disabled="p === '…'"
      @click="go(p)"
    >
      {{ p }}
    </button>
    <button
      type="button"
      class="pg-btn"
      :disabled="page >= pageCount"
      aria-label="下一页"
      @click="go(page + 1)"
    >
      <ChevronRight :size="15" />
    </button>
  </nav>
</template>

<style scoped lang="scss">
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 32px;
}

.pg-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 34px;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid hsl(var(--border));
  background-color: hsl(var(--card));
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;

  &:hover:not(:disabled) {
    color: hsl(var(--foreground));
    border-color: hsl(var(--brand) / 0.5);
  }

  &.is-active {
    background-color: hsl(var(--brand));
    border-color: hsl(var(--brand));
    color: #fff;
    font-weight: 600;
  }

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }

  &.is-ellipsis {
    border-color: transparent;
    background-color: transparent;
  }
}
</style>

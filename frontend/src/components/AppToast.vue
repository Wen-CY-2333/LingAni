<script setup>
import { toasts } from '@/utils/toast'
</script>

<template>
  <TransitionGroup name="toast" tag="div" class="toast-stack">
    <div v-for="item in toasts" :key="item.id" class="toast" :class="`is-${item.type}`">
      {{ item.message }}
    </div>
  </TransitionGroup>
</template>

<style scoped lang="scss">
.toast-stack {
  position: fixed;
  top: calc(var(--header-height) + 16px);
  left: 50%;
  z-index: 120;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  transform: translateX(-50%);
  pointer-events: none;
}

.toast {
  max-width: min(520px, 88vw);
  padding: 10px 18px;
  border-radius: 999px;
  border: 1px solid hsl(var(--border));
  background-color: rgba(24, 24, 27, 0.94);
  backdrop-filter: blur(8px);
  color: hsl(var(--foreground));
  font-size: 13px;
  line-height: 1.6;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);

  &.is-error {
    border-color: hsl(var(--brand) / 0.55);
    color: #ffd9dc;
  }

  &.is-success {
    border-color: hsl(145 55% 45% / 0.55);
    color: #c9f7d8;
  }
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>

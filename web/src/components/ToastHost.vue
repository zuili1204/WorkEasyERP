<script setup lang="ts">
import { toasts, toast } from '../stores/toast'
import Icon from './Icon.vue'

const ICONS: Record<string, string> = {
  success: 'check-circle',
  error: 'alert-triangle',
  warn: 'alert-triangle',
  info: 'message',
}
</script>

<template>
  <div class="toast-host" role="status" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="t in toasts.items" :key="t.id" class="toast" :class="t.type">
        <Icon :name="ICONS[t.type]" :size="17" />
        <span class="txt">{{ t.text }}</span>
        <button class="close" aria-label="关闭" @click="toast.dismiss(t.id)">
          <Icon name="x" :size="14" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: 18px;
  right: 20px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.toast {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 260px;
  max-width: 420px;
  padding: 11px 13px;
  border-radius: var(--radius-sm);
  background: var(--surface);
  border: 1px solid var(--border);
  border-left: 4px solid var(--blue);
  box-shadow: var(--shadow-lg);
  font-size: 13.5px;
  color: var(--text);
}

.toast.success { border-left-color: var(--accent); }
.toast.success svg { color: var(--accent); }
.toast.error { border-left-color: var(--danger); }
.toast.error svg { color: var(--danger); }
.toast.warn { border-left-color: var(--warn); }
.toast.warn svg { color: var(--warn); }
.toast.info svg { color: var(--blue); }

.txt { flex: 1; line-height: 1.5; word-break: break-word; }

.close {
  border: 0;
  background: transparent;
  color: var(--text-4);
  cursor: pointer;
  display: flex;
  padding: 3px;
  border-radius: 6px;
  transition: 0.15s var(--ease);
}
.close:hover { background: var(--slate-light); color: var(--text-2); }

.toast-enter-active,
.toast-leave-active { transition: all 0.24s var(--ease); }
.toast-enter-from { opacity: 0; transform: translateX(18px) scale(0.98); }
.toast-leave-to { opacity: 0; transform: translateX(18px) scale(0.98); }

@media (prefers-reduced-motion: reduce) {
  .toast-enter-active,
  .toast-leave-active { transition: none; }
}
</style>

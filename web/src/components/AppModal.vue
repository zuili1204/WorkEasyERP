<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import Icon from './Icon.vue'

/**
 * 统一弹窗：收口 28 个视图里各抄一份的 .mask/.modal 样式。
 * 同一产品原先存在 520/560/700 三档宽度、两种垂直留白、两种 body padding，
 * 这里统一为 sm/md/lg 三档并带最大高度滚动，Teleport 到 body 规避层级冲突。
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    icon?: string
    /** sm=520 / md=560 / lg=700 */
    size?: 'sm' | 'md' | 'lg'
    closeOnMask?: boolean
  }>(),
  { size: 'sm', closeOnMask: true },
)

const emit = defineEmits<{ (e: 'close'): void }>()

const WIDTH = { sm: 520, md: 560, lg: 700 } as const

const panel = ref<HTMLElement | null>(null)
const lastFocus = ref<HTMLElement | null>(null)

const panelWidth = computed(() => `${WIDTH[props.size]}px`)

function close() {
  emit('close')
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    close()
    return
  }
  if (e.key !== 'Tab' || !panel.value) return
  // 焦点陷阱：保证键盘操作不会跑到遮罩之外
  const nodes = Array.from(
    panel.value.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
    ),
  ).filter((n) => !n.hasAttribute('disabled'))
  if (nodes.length === 0) return
  const first = nodes[0]
  const last = nodes[nodes.length - 1]
  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault()
    last.focus()
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault()
    first.focus()
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      lastFocus.value = document.activeElement as HTMLElement | null
      window.addEventListener('keydown', onKey)
      await nextTick()
      panel.value
        ?.querySelector<HTMLElement>('input, select, textarea, button')
        ?.focus()
    } else {
      window.removeEventListener('keydown', onKey)
      lastFocus.value?.focus?.()
    }
  },
)

onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="mask" @click.self="closeOnMask && close()">
      <div
        ref="panel"
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
        :style="{ width: panelWidth }"
      >
        <div class="modal-head">
          <h3>
            <Icon v-if="icon" :name="icon" :size="18" />
            {{ title }}
          </h3>
          <span class="x" title="关闭" @click="close"><Icon name="x" :size="20" /></span>
        </div>

        <div class="modal-body">
          <slot />
        </div>

        <div class="modal-foot">
          <slot name="footer">
            <button class="btn" @click="close">取消</button>
          </slot>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center;
  z-index: var(--z-mask); padding: 60px 20px;
}
.modal {
  background: #fff; border-radius: var(--radius-lg); max-width: 100%;
  box-shadow: var(--shadow-lg); display: flex; flex-direction: column;
  max-height: calc(100vh - 120px);
}
.modal-head {
  padding: 18px 24px; border-bottom: 1px solid var(--border-2);
  display: flex; justify-content: space-between; align-items: center; flex-shrink: 0;
}
.modal-head h3 { margin: 0; font-size: var(--fs-lg); font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: var(--sp-6); overflow: auto; flex: 1; }
.modal-foot {
  padding: 15px 24px; border-top: 1px solid var(--border-2);
  display: flex; justify-content: flex-end; gap: 10px; flex-shrink: 0;
}
@media (max-width: 900px) {
  .mask { padding: 20px 12px; }
  .modal { width: 100% !important; }
}
</style>

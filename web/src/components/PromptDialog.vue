<script setup lang="ts">
import { ref, watch } from 'vue'
import Icon from './Icon.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    label?: string
    placeholder?: string
    required?: boolean
    confirmText?: string
  }>(),
  { title: '请输入', label: '', placeholder: '', required: false, confirmText: '确定' },
)

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'confirm', v: string): void
}>()

const value = ref('')
const error = ref('')

watch(
  () => props.open,
  (o) => {
    if (o) {
      value.value = ''
      error.value = ''
    }
  },
)

function close() {
  emit('update:open', false)
}

function confirm() {
  if (props.required && !value.value.trim()) {
    error.value = '此项为必填'
    return
  }
  emit('confirm', value.value.trim())
  close()
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') close()
}
</script>

<template>
  <div v-if="open" class="mask" @click.self="close" @keydown="onKey">
    <div class="modal" role="dialog" aria-modal="true">
      <div class="modal-head">
        <h3><Icon name="edit" :size="18" /> {{ title }}</h3>
        <span class="x" aria-label="关闭" @click="close"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <label v-if="label">{{ label }}</label>
        <input
          v-model="value"
          class="input"
          :class="{ invalid: error }"
          :placeholder="placeholder"
          @keyup.enter="confirm"
        />
        <p v-if="error" class="err">{{ error }}</p>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="close">取消</button>
        <button class="btn btn-primary" @click="confirm">{{ confirmText }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(3px);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  z-index: 100;
  padding: 120px 20px;
}
.modal {
  background: #fff;
  border-radius: var(--radius-lg);
  width: 420px;
  max-width: 100%;
  box-shadow: var(--shadow-lg);
}
.modal-head {
  padding: 16px 22px;
  border-bottom: 1px solid var(--border-2);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.modal-head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  display: flex;
  align-items: center;
  gap: 9px;
}
.modal-head .x {
  cursor: pointer;
  color: var(--text-3);
  display: flex;
  padding: 5px;
  border-radius: 8px;
  transition: background 0.15s var(--ease), color 0.15s var(--ease);
}
.modal-head .x:hover {
  background: var(--danger-light);
  color: var(--danger);
}
.modal-body {
  padding: 22px;
}
.modal-body label {
  display: block;
  font-size: 12.5px;
  color: var(--text-2);
  margin-bottom: 6px;
  font-weight: 600;
}
.modal-body .input {
  width: 100%;
}
.modal-body .input.invalid {
  border-color: var(--danger);
}
.modal-body .input.invalid:focus {
  box-shadow: 0 0 0 3px var(--danger-light);
}
.err {
  background: var(--danger-light);
  color: #b91c1c;
  padding: 8px 11px;
  border-radius: var(--radius-sm);
  font-size: 12.5px;
  margin: 10px 0 0;
}
.modal-foot {
  padding: 14px 22px;
  border-top: 1px solid var(--border-2);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

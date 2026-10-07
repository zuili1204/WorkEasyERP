import { reactive } from 'vue'

export type ToastType = 'success' | 'error' | 'warn' | 'info'

export interface ToastItem {
  id: number
  type: ToastType
  text: string
}

const state = reactive<{ items: ToastItem[] }>({ items: [] })

let seq = 0

function push(type: ToastType, text: string, ms: number) {
  // 同一文案短时间内重复出现时只保留一条，避免刷屏
  const dup = state.items.find((t) => t.text === text && t.type === type)
  if (dup) return
  const id = ++seq
  state.items.push({ id, type, text })
  window.setTimeout(() => dismiss(id), ms)
}

function dismiss(id: number) {
  const i = state.items.findIndex((t) => t.id === id)
  if (i >= 0) state.items.splice(i, 1)
}

export const toasts = state

export const toast = {
  success: (text: string) => push('success', text, 2400),
  error: (text: string) => push('error', text, 3800),
  warn: (text: string) => push('warn', text, 3000),
  info: (text: string) => push('info', text, 2400),
  dismiss,
}

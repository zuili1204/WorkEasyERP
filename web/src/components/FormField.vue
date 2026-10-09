<script setup lang="ts">
/**
 * 统一表单字段容器（#24）。
 * 原先 label / 必填星号 / 提示 / 错误文案及其样式在
 * EntityCreateView、LeaveCreateView、EmployeeCreateView 与各弹窗里各写一份，
 * 这里收敛为容器组件，控件本身通过插槽传入，保持灵活性。
 */
defineProps<{
  label: string
  /** 是否必填（显示红色星号） */
  required?: boolean
  /** 校验错误文案，存在时控件描边变红 */
  error?: string
  /** 字段说明，无错误时展示 */
  hint?: string
}>()
</script>

<template>
  <div class="field" :class="{ invalid: !!error }">
    <label>
      {{ label }}
      <b v-if="required" class="req">*</b>
    </label>

    <slot />

    <span v-if="hint && !error" class="hint-inline">{{ hint }}</span>
    <span v-if="error" class="err-tip">{{ error }}</span>
  </div>
</template>

<style scoped>
.field { margin-bottom: 14px; }
.field label {
  display: block; font-size: 12.5px; color: var(--text-2);
  margin-bottom: 6px; font-weight: 600;
}
.req { color: var(--danger); font-weight: 700; }
.hint-inline { display: block; margin-top: 5px; font-size: 11.5px; color: var(--text-3); }
.err-tip { display: block; margin-top: 5px; font-size: 11.5px; color: var(--danger); }
/* 控件由父组件传入，需用 :deep 才能命中 */
.field.invalid :deep(.input) {
  border-color: var(--danger);
  box-shadow: 0 0 0 3px var(--danger-light);
}
</style>

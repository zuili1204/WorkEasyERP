<script setup lang="ts">
/**
 * 统一翻页：原先 LeaveView / AttendanceView / ExpenseView 等逐字复制了同一份翻页模板，
 * 这里抽成组件统一管理边界禁用与页码展示。
 */
const props = defineProps<{ page: number; pages: number }>()
const emit = defineEmits<{ (e: 'go', page: number): void }>()

function go(p: number) {
  if (p < 1 || p > props.pages) return
  emit('go', p)
}
</script>

<template>
  <div class="pager">
    <span class="pbtn" :class="{ dis: page <= 1 }" role="button" @click="go(page - 1)">‹ 上一页</span>
    <span>第 {{ page }} / {{ pages }} 页</span>
    <span class="pbtn" :class="{ dis: page >= pages }" role="button" @click="go(page + 1)">下一页 ›</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string }>()

/** 状态码 → 展示文案（唯一口径，与数据库文档 §16.4 对齐） */
const MAP: Record<string, { text: string; cls: string }> = {
  active: { text: '在职', cls: 'badge-green' },
  probation: { text: '试用', cls: 'badge-orange' },
  resigned: { text: '离职', cls: 'badge-gray' },
  normal: { text: '正常', cls: 'badge-green' },
  deleted: { text: '已删除', cls: 'badge-gray' },
}

const m = computed(() => MAP[props.status] ?? { text: props.status || '-', cls: 'badge-gray' })
</script>

<template>
  <span class="badge" :class="m.cls">{{ m.text }}</span>
</template>

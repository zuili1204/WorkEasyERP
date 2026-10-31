<script setup lang="ts">
import Icon from './Icon.vue'

withDefaults(
  defineProps<{
    icon?: string
    title?: string
    desc?: string
    compact?: boolean
  }>(),
  { icon: 'inbox', title: '暂无数据', desc: '', compact: false },
)
</script>

<template>
  <div class="empty" :class="{ compact }">
    <div class="ico"><Icon :name="icon" :size="compact ? 34 : 46" /></div>
    <div class="t">{{ title }}</div>
    <div v-if="desc" class="d">{{ desc }}</div>
    <slot />
  </div>
</template>

<style scoped>
.empty {
  padding: 52px 20px;
  text-align: center;
  color: var(--text-3);
}
.empty.compact {
  padding: 28px 20px;
}
.ico {
  width: 58px; height: 58px; margin: 0 auto 12px;
  display: flex; align-items: center; justify-content: center;
  border: 1px dashed var(--border); border-radius: var(--radius-sm);
  background: #FCFBF7;
  color: var(--text-4); opacity: .7;
}
.t {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-2);
}
.d {
  font-size: 12.5px;
  margin-top: 4px;
  color: var(--text-3);
}
.empty :slotted(button) {
  margin-top: 14px;
}
</style>

<script setup lang="ts">
withDefaults(defineProps<{ text?: string }>(), { text: '加载中…' })
</script>

<template>
  <div class="loading">
    <span class="spinner" aria-hidden="true" />
    <span class="txt">{{ text }}</span>
  </div>
</template>

<style scoped>
.loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 20px;
  color: var(--text-3);
  font-size: 13.5px;
}
.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--border);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
.txt {
  font-family: var(--font-mono);
  font-size: 11.5px;
  letter-spacing: .08em;
  font-weight: 500;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 1.4s;
  }
}
</style>

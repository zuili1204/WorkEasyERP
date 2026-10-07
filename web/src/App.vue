<template>
  <router-view v-slot="{ Component }">
    <Transition name="view-fade" mode="out-in">
      <component :is="Component" />
    </Transition>
  </router-view>
  <ToastHost />
</template>

<script setup lang="ts">
import ToastHost from './components/ToastHost.vue'
</script>

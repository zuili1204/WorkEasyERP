<script setup lang="ts">
import { computed } from 'vue'
import AppPager from './AppPager.vue'
import EmptyState from './EmptyState.vue'
import LoadingState from './LoadingState.vue'

/**
 * 列表外壳：统一「表头 + 空态 + 加载态 + 分页」四件套。
 *
 * 各视图此前重复手写的是同一套结构（.table-wrap 内放 table.list，下面挂 loading/empty，
 * 再另起 .pager 逐字复制翻页模板）。这里只收口这四件公共部分，
 * 表头与数据行仍由使用方通过插槽提供，以保留各视图的表格差异（徽章列、操作列、权限隐藏列）。
 *
 * 用法：
 *   <TableShell :rows="data.list" :loading="loading" :page="data.page" :pages="totalPages" @go="go">
 *     <template #head><tr>...th...</tr></template>
 *     <tr v-for="r in data.list" :key="r.id">...</tr>
 *     <template #empty><button>清空搜索</button></template>
 *   </TableShell>
 */
const props = withDefaults(
  defineProps<{
    /** 当前页数据行，仅用于判定空态；行渲染交给默认插槽 */
    rows?: unknown[]
    loading?: boolean
    loadingText?: string
    emptyIcon?: string
    emptyTitle?: string
    emptyDesc?: string
    /** 传了 pages(>0) 才渲染翻页器 */
    page?: number
    pages?: number
  }>(),
  {
    rows: () => [],
    loading: false,
    loadingText: '加载中…',
    emptyIcon: 'inbox',
    emptyTitle: '暂无数据',
    emptyDesc: '',
    page: 1,
    pages: 0,
  },
)

const emit = defineEmits<{ (e: 'go', page: number): void }>()

/** 加载中优先展示加载态，避免首屏闪烁「暂无数据」 */
const showEmpty = computed(() => !props.loading && (props.rows?.length ?? 0) === 0)

function onGo(p: number) {
  emit('go', p)
}
</script>

<template>
  <div class="table-wrap">
    <table class="list">
      <thead>
        <slot name="head" />
      </thead>
      <tbody>
        <slot />
      </tbody>
    </table>

    <LoadingState v-if="loading" :text="loadingText" />
    <EmptyState
      v-else-if="showEmpty"
      :icon="emptyIcon"
      :title="emptyTitle"
      :desc="emptyDesc"
    >
      <slot name="empty" />
    </EmptyState>
  </div>

  <AppPager v-if="pages > 0" :page="page" :pages="pages" @go="onGo" />
</template>

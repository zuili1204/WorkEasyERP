<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'

const SIZE = 10

const state = reactive({ q: '', module: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.auditLogs(state.q || undefined, state.module || undefined, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

let timer: number | undefined
function onSearch() {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => {
    state.page = 1
    load()
  }, 250)
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function time(v: string | null) {
  return (v ?? '').replace('T', ' ').slice(0, 19)
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="list" :size="17" /> 操作日志</h3>
      <span class="count">记录关键写操作：谁、何时、改了什么</span>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索操作人 / 模块 / 动作" @input="onSearch" />
        </div>
        <input v-model="state.module" class="input" style="width: 150px" placeholder="模块过滤 如 leave" @input="onSearch" />
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <TableShell
        :rows="data.list"
        :loading="loading"
        :page="data.page"
        :pages="totalPages"
        :empty-icon="state.q ? 'search' : 'inbox'"
        :empty-title="state.q ? `未找到与「${state.q}」匹配的日志` : '暂无操作日志'"
        @go="go"
      >
        <template #head>
          <tr>
            <th>时间</th>
            <th>操作人</th>
            <th>模块</th>
            <th>动作</th>
            <th>对象表</th>
            <th>记录 ID</th>
          </tr>
        </template>

        <tr v-for="(r, i) in data.list" :key="i">
          <td>{{ time(r.created_at) }}</td>
          <td>{{ r.user_name || '—' }}</td>
          <td><span class="badge badge-blue">{{ r.module }}</span></td>
          <td>{{ r.action }}</td>
          <td>{{ r.target_table || '—' }}</td>
          <td class="mono">{{ (r.target_id || '').slice(0, 8) }}</td>
        </tr>
      </TableShell>
    </div>
  </div>
</template>

<style scoped>
.mono { font-family: ui-monospace, Consolas, monospace; font-size: 12px; color: var(--text-3); }
</style>

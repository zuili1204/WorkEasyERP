<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type DepartmentRow, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import { toast } from '../stores/toast'

const PAGE_SIZE = 8

const state = reactive({ q: '', sort: '', page: 1 })
const data = ref<PageResult<DepartmentRow>>({ list: [], total: 0, page: 1, size: PAGE_SIZE })
const loading = ref(false)
// 新增部门已迁移至独立页 /new/department（见 utils/entityForms.ts）

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / PAGE_SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.departments({
      q: state.q || undefined,
      page: state.page,
      size: PAGE_SIZE,
      sort: state.sort || undefined,
    })
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    toast.error(err.response?.data?.msg || '部门列表加载失败')
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

function toggleSort(key: string) {
  if (state.sort.startsWith(key)) {
    state.sort = state.sort.includes('desc') ? `${key},asc` : `${key},desc`
  } else {
    state.sort = `${key},asc`
  }
  load()
}

function sortIcon(key: string) {
  if (!state.sort.startsWith(key)) return 'arrow-up-down'
  return state.sort.includes('desc') ? 'chevron-down' : 'chevron-up'
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="building" :size="17" /> 部门管理</h3>
      <router-link class="btn btn-primary btn-sm" to="/new/department">
        <Icon name="plus" :size="14" /> 新增部门
      </router-link>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索部门" @input="onSearch" />
        </div>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th class="sortable" :class="{ on: state.sort.startsWith('name') }" @click="toggleSort('name')">
                部门<span class="sico"><Icon :name="sortIcon('name')" :size="12" /></span>
              </th>
              <th>主管</th>
              <th class="sortable" :class="{ on: state.sort.startsWith('count') }" @click="toggleSort('count')">
                人数<span class="sico"><Icon :name="sortIcon('count')" :size="12" /></span>
              </th>
              <th class="nosort">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in data.list" :key="r.id">
              <td>{{ r.name }}</td>
              <td>{{ r.managerName || '—' }}</td>
              <td>{{ r.count }}</td>
              <td><span class="badge badge-green">正常</span></td>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon :name="state.q ? 'search' : 'inbox'" :size="46" />
          <div>{{ state.q ? `未找到与「${state.q}」匹配的数据` : '暂无部门数据' }}</div>
        </div>
      </div>

      <div class="pager">
        <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
        <span>第 {{ data.page }} / {{ totalPages }} 页</span>
        <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
      </div>
    </div>
  </div>

</template>

<style scoped>
.nosort { cursor: default; }
</style>

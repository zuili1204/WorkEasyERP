<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type EmployeeRow, type PageResult } from '../api'
import { auth } from '../stores/auth'
import Icon from '../components/Icon.vue'
import StatusBadge from '../components/StatusBadge.vue'

const PAGE_SIZE = 8

/** 默认数据范围取自角色 data_scope（跨域收窄由后端 ScopeResolver 兜底） */
function defaultScope(): string {
  const s = auth.user?.dataScope
  if (s === 'all') return 'all'
  if (s === 'dept') return 'dept'
  return 'mine'
}

const SCOPES: Array<[string, string]> = [
  ['mine', '我的'],
  ['dept', '本部门'],
  ['all', '全部'],
]

const state = reactive({ q: '', scope: defaultScope(), sort: '', page: 1 })
const data = ref<PageResult<EmployeeRow>>({ list: [], total: 0, page: 1, size: PAGE_SIZE })
const loading = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / PAGE_SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.employees({
      q: state.q || undefined,
      scope: state.scope,
      page: state.page,
      size: PAGE_SIZE,
      sort: state.sort || undefined,
    })
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

function setScope(s: string) {
  state.scope = s
  state.page = 1
  load()
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

function clearSearch() {
  state.q = ''
  state.page = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="user" :size="17" /> 员工档案</h3>
      <span class="domain-tag">数据范围由角色决定，可在下方切换</span>
    </div>

    <div class="card-body">
      <!-- 列表页五件套：搜索 / 范围 / 排序 / 分页 / 空状态 -->
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索姓名 / 工号 / 岗位" @input="onSearch" />
        </div>

        <button class="btn btn-sm btn-ghost" @click="load">
          <Icon name="refresh" :size="15" /> 刷新
        </button>

        <div class="spacer" />

        <div class="seg">
          <button
            v-for="s in SCOPES"
            :key="s[0]"
            class="seg-btn"
            :class="{ active: state.scope === s[0] }"
            @click="setScope(s[0])"
          >
            {{ s[1] }}
          </button>
        </div>

        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th class="sortable" :class="{ on: state.sort.startsWith('employeeNo') }" @click="toggleSort('employeeNo')">
                工号<span class="sico"><Icon :name="sortIcon('employeeNo')" :size="12" /></span>
              </th>
              <th class="sortable" :class="{ on: state.sort.startsWith('realName') }" @click="toggleSort('realName')">
                姓名<span class="sico"><Icon :name="sortIcon('realName')" :size="12" /></span>
              </th>
              <th>部门</th>
              <th>岗位</th>
              <th class="sortable" :class="{ on: state.sort.startsWith('status') }" @click="toggleSort('status')">
                状态<span class="sico"><Icon :name="sortIcon('status')" :size="12" /></span>
              </th>
              <th class="nosort">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in data.list" :key="r.id">
              <td>{{ r.employeeNo }}</td>
              <td>{{ r.realName }}</td>
              <td>{{ r.deptName || '—' }}</td>
              <td>{{ r.position || '—' }}</td>
              <td><StatusBadge :status="r.status" /></td>
              <td class="op"><a>查看</a><a>编辑</a></td>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon :name="state.q ? 'search' : 'inbox'" :size="46" />
          <div>
            {{ state.q ? `未找到与「${state.q}」匹配的数据` : '当前数据范围内暂无数据' }}
          </div>
          <button v-if="state.q" class="btn btn-sm" style="margin-top: 14px" @click="clearSearch">
            <Icon name="x" :size="14" /> 清空搜索
          </button>
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

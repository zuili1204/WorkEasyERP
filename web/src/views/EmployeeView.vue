<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, type EmployeeRow, type PageResult } from '../api'
import { auth } from '../stores/auth'
import Icon from '../components/Icon.vue'
import AppModal from '../components/AppModal.vue'
import AppPager from '../components/AppPager.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { toast } from '../stores/toast'

const router = useRouter()
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
const detail = ref<EmployeeRow | null>(null)

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
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    toast.error(err.response?.data?.msg || '员工列表加载失败')
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

/** 原先这里是两个无效的 <a>（点了没反应），改为「查看详情」弹窗；后端暂未提供更新接口，故不暴露编辑入口 */
function openDetail(r: EmployeeRow) {
  detail.value = r
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="user" :size="17" /> 员工档案</h3>
      <div class="head-actions">
        <span class="domain-tag">数据范围由角色决定，可在下方切换</span>
        <button class="btn btn-primary btn-sm" @click="router.push('/employees/new')">
          <Icon name="plus" :size="14" /> 新建员工
        </button>
      </div>
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
              <td class="op"><a @click="openDetail(r)">查看详情</a></td>
            </tr>
          </tbody>
        </table>

        <div v-if="loading" class="empty sm"><Icon name="inbox" :size="30" /> 加载中…</div>
        <div v-else-if="data.total === 0" class="empty">
          <Icon :name="state.q ? 'search' : 'inbox'" :size="46" />
          <div>
            {{ state.q ? `未找到与「${state.q}」匹配的数据` : '当前数据范围内暂无数据' }}
          </div>
          <button v-if="state.q" class="btn btn-sm" style="margin-top: 14px" @click="clearSearch">
            <Icon name="x" :size="14" /> 清空搜索
          </button>
        </div>
      </div>

      <AppPager :page="data.page" :pages="totalPages" @go="go" />
    </div>
  </div>

  <AppModal :open="!!detail" title="员工详情" icon="user" @close="detail = null">
    <div v-if="detail" class="detail">
      <div class="detail-row"><span>工号</span><b>{{ detail.employeeNo || '—' }}</b></div>
      <div class="detail-row"><span>姓名</span><b>{{ detail.realName }}</b></div>
      <div class="detail-row"><span>部门</span><b>{{ detail.deptName || '—' }}</b></div>
      <div class="detail-row"><span>岗位</span><b>{{ detail.position || '—' }}</b></div>
      <div class="detail-row"><span>状态</span><b><StatusBadge :status="detail.status" /></b></div>
    </div>
    <template #footer>
      <button class="btn" @click="detail = null">关闭</button>
    </template>
  </AppModal>
</template>

<style scoped>
.nosort { cursor: default; }
.head-actions { display: flex; align-items: center; gap: 10px; }
.detail-row {
  display: flex; justify-content: space-between; gap: 16px;
  padding: 9px 0; border-bottom: 1px solid var(--border-2); font-size: 13.5px;
}
.detail-row span { color: var(--text-3); }
.detail-row:last-child { border-bottom: 0; }
</style>

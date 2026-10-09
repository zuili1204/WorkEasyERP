<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'

const SIZE = 8
const route = useRoute()
const bizType = computed(() => String(route.params.bizType || 'overtime'))

/** 四类假勤的表单元数据：字段 → (key, 标签, 控件类型) */
const META: Record<
  string,
  {
    title: string
    icon: string
    cols: Array<[string, string]>
  }
> = {
  overtime: {
    title: '加班申请',
    icon: 'clock',
    cols: [
      ['order_no', '单号'],
      ['employee_name', '申请人'],
      ['apply_date', '日期'],
      ['duration', '时长'],
      ['status', '状态'],
    ],
  },
  appeal: {
    title: '补卡申诉',
    icon: 'edit',
    cols: [
      ['employee_name', '申请人'],
      ['work_date', '工作日'],
      ['punch_type', '类型'],
      ['status', '状态'],
    ],
  },
  outing: {
    title: '外出申请',
    icon: 'map-pin',
    cols: [
      ['order_no', '单号'],
      ['employee_name', '申请人'],
      ['destination', '目的地'],
      ['status', '状态'],
    ],
  },
  trip: {
    title: '出差申请',
    icon: 'plane',
    cols: [
      ['order_no', '单号'],
      ['employee_name', '申请人'],
      ['destination', '目的地'],
      ['start_date', '出发'],
      ['status', '状态'],
    ],
  },
}

const STATUS_MAP: Record<string, { text: string; cls: string }> = {
  pending: { text: '审批中', cls: 'badge-orange' },
  approved: { text: '已通过', cls: 'badge-green' },
  rejected: { text: '已驳回', cls: 'badge-red' },
}

const meta = computed(() => META[bizType.value] ?? META.overtime)
const state = reactive({ q: '', scope: 'mine', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
// 四类假勤发起已迁移至独立页 /new/oa/<bizType>（见 utils/entityForms.ts）

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.oaList(bizType.value, state.q || undefined, state.scope, state.page, SIZE)
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

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function cell(row: Record<string, string | null>, key: string) {
  if (key === 'status') return ''
  const v = row[key]
  if (v == null) return '—'
  return String(v).replace('T', ' ').slice(0, 16)
}

watch(bizType, () => {
  state.page = 1
  state.q = ''
  load()
})

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon :name="meta.icon" :size="17" /> {{ meta.title }}</h3>
      <router-link class="btn btn-primary btn-sm" :to="`/new/oa/${bizType}`">
        <Icon name="plus" :size="14" /> 发起申请
      </router-link>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索单号 / 姓名" @input="onSearch" />
        </div>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <div class="seg">
          <button class="seg-btn" :class="{ active: state.scope === 'mine' }" @click="setScope('mine')">我的</button>
          <button class="seg-btn" :class="{ active: state.scope === 'dept' }" @click="setScope('dept')">本部门</button>
          <button class="seg-btn" :class="{ active: state.scope === 'all' }" @click="setScope('all')">全部</button>
        </div>
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th v-for="c in meta.cols" :key="c[0]">{{ c[1] }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <template v-for="c in meta.cols" :key="c[0]">
                <td v-if="c[0] === 'status'">
                  <span class="badge" :class="(STATUS_MAP[r.status ?? ''] ?? { cls: 'badge-gray' }).cls">
                    {{ (STATUS_MAP[r.status ?? ''] ?? { text: r.status ?? '-' }).text }}
                  </span>
                </td>
                <td v-else>{{ cell(r, c[0]) }}</td>
              </template>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon :name="state.q ? 'search' : 'inbox'" :size="46" />
          <div>{{ state.q ? `未找到与「${state.q}」匹配的数据` : '暂无申请记录' }}</div>
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

</style>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'
import { toast } from '../stores/toast'

const SIZE = 8
const route = useRoute()
const kind = computed(() => String(route.params.kind || 'regular'))

const META: Record<
  string,
  { title: string; icon: string; cols: Array<[string, string]>; approval: boolean }
> = {
  regular: {
    title: '转正',
    icon: 'award',
    approval: true,
    cols: [
      ['employee_name', '员工'],
      ['probation_end', '试用期止'],
      ['regular_date', '转正日期'],
      ['status', '状态'],
    ],
  },
  transfer: {
    title: '调岗',
    icon: 'refresh',
    approval: true,
    cols: [
      ['employee_name', '员工'],
      ['from_position', '原岗位'],
      ['to_position', '新岗位'],
      ['effective_date', '生效日'],
      ['status', '状态'],
    ],
  },
  promo: {
    title: '晋升',
    icon: 'arrow-up',
    approval: true,
    cols: [
      ['employee_name', '员工'],
      ['from_position', '原职位'],
      ['to_position', '新职位'],
      ['effective_date', '生效日'],
      ['status', '状态'],
    ],
  },
  dimission: {
    title: '离职 / 辞退',
    icon: 'user-minus',
    approval: true,
    cols: [
      ['employee_name', '员工'],
      ['type', '类型'],
      ['last_work_date', '最后工作日'],
      ['status', '状态'],
    ],
  },
  entry: {
    title: '入职办理',
    icon: 'user-plus',
    approval: false,
    cols: [
      ['apply_no', '单号'],
      ['candidate_name', '候选人'],
      ['expected_position', '期望岗位'],
      ['expected_entry_date', '预计入职'],
      ['status', '状态'],
    ],
  },
  contract: {
    title: '劳动合同',
    icon: 'file-text',
    approval: false,
    cols: [
      ['contract_no', '合同号'],
      ['employee_name', '员工'],
      ['type', '类型'],
      ['start_date', '起始'],
      ['end_date', '到期'],
      ['status', '状态'],
    ],
  },
}

const STATUS_MAP: Record<string, { text: string; cls: string }> = {
  pending: { text: '待处理', cls: 'badge-orange' },
  approved: { text: '已通过', cls: 'badge-green' },
  rejected: { text: '已驳回', cls: 'badge-red' },
  confirmed: { text: '已确认', cls: 'badge-green' },
  canceled: { text: '已取消', cls: 'badge-gray' },
  active: { text: '生效中', cls: 'badge-green' },
  expired: { text: '已到期', cls: 'badge-gray' },
  terminated: { text: '已终止', cls: 'badge-red' },
}

const TYPE_MAP: Record<string, string> = {
  resign: '主动离职',
  terminate: '辞退',
  retire: '退休',
  fixed: '固定期',
  nonfixed: '无固定期',
}

const meta = computed(() => META[kind.value] ?? META.regular)
const state = reactive({ q: '', scope: 'mine', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
// 六类人事事件发起已迁移至独立页 /new/hr/<kind>（见 utils/entityForms.ts）

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.hrList(kind.value, state.q || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

async function confirm(row: Record<string, string | null>) {
  try {
    await api.confirmEntry(String(row.id))
    load()
    toast.success('已确认入职，员工档案已建立')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    toast.error(err.response?.data?.msg || '确认入职失败')
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function cell(row: Record<string, string | null>, key: string) {
  if (key === 'type') return TYPE_MAP[String(row[key] ?? '')] ?? String(row[key] ?? '—')
  const v = row[key]
  if (v == null) return '—'
  return String(v).replace('T', ' ').slice(0, 10)
}

watch(kind, () => {
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
      <router-link class="btn btn-primary btn-sm" :to="`/new/hr/${kind}`">
        <Icon name="plus" :size="14" /> {{ kind === 'entry' ? '登记候选人' : kind === 'contract' ? '新增合同' : '发起申请' }}
      </router-link>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索" @input="load" />
        </div>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <div v-if="kind !== 'entry'" class="seg">
          <button class="seg-btn" :class="{ active: state.scope === 'mine' }" @click="state.scope = 'mine'; load()">我的</button>
          <button class="seg-btn" :class="{ active: state.scope === 'dept' }" @click="state.scope = 'dept'; load()">本部门</button>
          <button class="seg-btn" :class="{ active: state.scope === 'all' }" @click="state.scope = 'all'; load()">全部</button>
        </div>
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th v-for="c in meta.cols" :key="c[0]">{{ c[1] }}</th>
              <th class="nosort">操作</th>
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
              <td class="op">
                <a v-if="kind === 'entry' && r.status === 'pending'" @click="confirm(r)">确认入职</a>
                <span v-else style="color: var(--text-4)">—</span>
              </td>
            </tr>
          </tbody>
        </table>

        <EmptyState v-if="!loading && data.total === 0" icon="inbox" title="暂无记录" desc="点击右上角按钮发起新的申请" />
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

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
    fields: Array<[string, string, string]>
    cols: Array<[string, string]>
  }
> = {
  overtime: {
    title: '加班申请',
    icon: 'clock',
    fields: [
      ['applyDate', '日期', 'date'],
      ['startAt', '开始时间', 'datetime-local'],
      ['endAt', '结束时间', 'datetime-local'],
      ['duration', '时长(h)', 'number'],
      ['reason', '事由', 'textarea'],
    ],
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
    fields: [
      ['workDate', '工作日', 'date'],
      ['punchType', '打卡类型', 'select:in/out'],
      ['appealReason', '申诉原因', 'textarea'],
    ],
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
    fields: [
      ['startAt', '开始时间', 'datetime-local'],
      ['endAt', '结束时间', 'datetime-local'],
      ['destination', '目的地', 'text'],
      ['reason', '事由', 'textarea'],
    ],
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
    fields: [
      ['startDate', '出发日期', 'date'],
      ['endDate', '返回日期', 'date'],
      ['destination', '目的地', 'text'],
      ['reason', '事由', 'textarea'],
    ],
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
const showModal = ref(false)
const form = reactive<Record<string, string>>({})
const saving = ref(false)

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

function openModal() {
  meta.value.fields.forEach(([k]) => (form[k] = ''))
  showModal.value = true
}

async function submit() {
  saving.value = true
  try {
    const payload: Record<string, unknown> = {}
    meta.value.fields.forEach(([k, , type]) => {
      const v = form[k]
      if (!v) return
      payload[k] = type === 'number' ? Number(v) : v
    })
    await api.oaSubmit(bizType.value, payload)
    showModal.value = false
    load()
  } finally {
    saving.value = false
  }
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

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

watch(bizType, () => {
  state.page = 1
  state.q = ''
  load()
})

onMounted(() => {
  load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon :name="meta.icon" :size="17" /> {{ meta.title }}</h3>
      <button class="btn btn-primary btn-sm" @click="openModal">
        <Icon name="plus" :size="14" /> 发起申请
      </button>
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

  <div v-if="showModal" class="mask" @click.self="showModal = false">
    <div class="modal">
      <div class="modal-head">
        <h3><Icon :name="meta.icon" :size="18" /> {{ meta.title }}</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div v-for="f in meta.fields" :key="f[0]" class="field">
          <label>{{ f[1] }}</label>
          <select v-if="f[2].startsWith('select:')" v-model="form[f[0]]" class="input">
            <option v-for="o in f[2].split(':')[1].split('/')" :key="o" :value="o">{{ o }}</option>
          </select>
          <textarea v-else-if="f[2] === 'textarea'" v-model="form[f[0]]" rows="3" class="input"></textarea>
          <input v-else v-model="form[f[0]]" :type="f[2]" class="input" />
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <Icon name="check" :size="15" /> 提交审批
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.field textarea { resize: vertical; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 80px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 520px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

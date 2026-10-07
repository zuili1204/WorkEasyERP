<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'

const SIZE = 8
const route = useRoute()
const kind = computed(() => String(route.params.kind || 'regular'))

const META: Record<
  string,
  { title: string; icon: string; fields: Array<[string, string, string]>; cols: Array<[string, string]>; approval: boolean }
> = {
  regular: {
    title: '转正',
    icon: 'award',
    approval: true,
    fields: [
      ['probationEnd', '试用期止', 'date'],
      ['regularDate', '转正日期', 'date'],
      ['evaluation', '评估意见', 'textarea'],
    ],
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
    fields: [
      ['fromDeptId', '原部门', 'dept'],
      ['toDeptId', '新部门', 'dept'],
      ['fromPosition', '原岗位', 'text'],
      ['toPosition', '新岗位', 'text'],
      ['effectiveDate', '生效日', 'date'],
      ['reason', '原因', 'textarea'],
    ],
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
    fields: [
      ['fromPosition', '原职位', 'text'],
      ['toPosition', '新职位', 'text'],
      ['fromLevel', '原职级', 'text'],
      ['toLevel', '新职级', 'text'],
      ['effectiveDate', '生效日', 'date'],
      ['reason', '原因', 'textarea'],
    ],
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
    fields: [
      ['type', '类型', 'select:resign/terminate/retire'],
      ['lastWorkDate', '最后工作日', 'date'],
      ['handoverTo', '交接人', 'text'],
      ['reason', '原因', 'textarea'],
    ],
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
    fields: [
      ['candidateName', '候选人 *', 'text'],
      ['phone', '手机号', 'text'],
      ['expectedDeptId', '期望部门', 'dept'],
      ['expectedPosition', '期望岗位', 'text'],
      ['expectedEntryDate', '预计入职', 'date'],
      ['sourceChannel', '招聘渠道', 'text'],
    ],
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
    fields: [
      ['contractNo', '合同号 *', 'text'],
      ['type', '类型', 'select:fixed/nonfixed'],
      ['startDate', '起始日', 'date'],
      ['endDate', '到期日', 'date'],
      ['signDate', '签订日', 'date'],
      ['remark', '备注', 'textarea'],
    ],
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
const showModal = ref(false)
const form = reactive<Record<string, string>>({})
const saving = ref(false)
const depts = ref<{ id: string; name: string }[]>([])

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.hrList(kind.value, state.q || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

async function loadDepts() {
  const r = await api.departments({ page: 1, size: 50 })
  depts.value = r.list.map((d) => ({ id: d.id, name: d.name }))
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
      payload[k] = v
    })
    await api.hrSubmit(kind.value, payload)
    showModal.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function confirm(row: Record<string, string | null>) {
  await api.confirmEntry(String(row.id))
  load()
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

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

watch(kind, () => {
  state.page = 1
  state.q = ''
  load()
})

onMounted(async () => {
  await loadDepts()
  load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon :name="meta.icon" :size="17" /> {{ meta.title }}</h3>
      <button class="btn btn-primary btn-sm" @click="openModal">
        <Icon name="plus" :size="14" /> {{ kind === 'entry' ? '登记候选人' : kind === 'contract' ? '新增合同' : '发起申请' }}
      </button>
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

  <div v-if="showModal" class="mask" @click.self="showModal = false">
    <div class="modal">
      <div class="modal-head">
        <h3><Icon :name="meta.icon" :size="18" /> {{ meta.title }}</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div v-for="f in meta.fields" :key="f[0]" class="field">
          <label>{{ f[1] }}</label>
          <select v-if="f[2] === 'dept'" v-model="form[f[0]]" class="input">
            <option v-for="d in depts" :key="d.id" :value="d.id">{{ d.name }}</option>
          </select>
          <select v-else-if="f[2].startsWith('select:')" v-model="form[f[0]]" class="input">
            <option v-for="o in f[2].split(':')[1].split('/')" :key="o" :value="o">
              {{ ({ resign: '主动离职', terminate: '辞退', retire: '退休', fixed: '固定期', nonfixed: '无固定期' } as Record<string, string>)[o] || o }}
            </option>
          </select>
          <textarea v-else-if="f[2] === 'textarea'" v-model="form[f[0]]" rows="3" class="input"></textarea>
          <input v-else v-model="form[f[0]]" :type="f[2]" class="input" />
        </div>
        <p v-if="meta.approval" class="tip">提交后将进入审批流程（主管 → HR），通过后自动更新员工档案</p>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <Icon name="check" :size="15" /> {{ meta.approval ? '提交审批' : '保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nosort { cursor: default; }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.field textarea { resize: vertical; }
.tip { font-size: 12px; color: var(--text-3); margin: 4px 0 0; }
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

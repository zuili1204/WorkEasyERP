<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type LeaveRow, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import AttachmentPanel from '../components/AttachmentPanel.vue'

const SIZE = 8

const TYPES: Array<[string, string]> = [
  ['annual', '年假'],
  ['sick', '病假'],
  ['personal', '事假'],
  ['marriage', '婚假'],
  ['maternity', '产假'],
]

const STATUS_MAP: Record<string, { text: string; cls: string }> = {
  pending: { text: '审批中', cls: 'badge-orange' },
  approved: { text: '已通过', cls: 'badge-green' },
  rejected: { text: '已驳回', cls: 'badge-red' },
  canceled: { text: '已撤销', cls: 'badge-gray' },
}

const state = reactive({ q: '', scope: 'mine', page: 1 })
const data = ref<PageResult<LeaveRow>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const showModal = ref(false)
const showAtt = ref(false)
const attId = ref('')
const attNo = ref('')

function openAttachments(r: LeaveRow) {
  attId.value = String(r.id)
  attNo.value = String(r.orderNo ?? '')
  showAtt.value = true
}
/** create=新提交；resubmit=驳回/撤销后重新提交 */
const mode = ref<'create' | 'resubmit'>('create')
const editId = ref('')
const form = reactive({ leaveType: 'annual', startAt: '', endAt: '', duration: 1, reason: '' })
const saving = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.leaves({ q: state.q || undefined, scope: state.scope, page: state.page, size: SIZE })
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

function openCreate() {
  mode.value = 'create'
  editId.value = ''
  form.leaveType = 'annual'
  form.startAt = ''
  form.endAt = ''
  form.duration = 1
  form.reason = ''
  showModal.value = true
}

function openResubmit(row: LeaveRow) {
  mode.value = 'resubmit'
  editId.value = row.id
  form.leaveType = row.leaveType ?? 'annual'
  form.duration = Number(row.duration ?? 1)
  form.startAt = (row.startAt ?? '').slice(0, 10)
  form.endAt = (row.endAt ?? '').slice(0, 10)
  form.reason = row.reason ?? ''
  showModal.value = true
}

async function submit() {
  saving.value = true
  try {
    const body = {
      leaveType: form.leaveType,
      startAt: form.startAt ? `${form.startAt}T00:00:00Z` : undefined,
      endAt: form.endAt ? `${form.endAt}T00:00:00Z` : undefined,
      duration: Number(form.duration),
      reason: form.reason || undefined,
    }
    if (mode.value === 'resubmit') await api.resubmitLeave(editId.value, body)
    else await api.submitLeave(body)
    showModal.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function cancel(row: LeaveRow) {
  await api.cancelLeave(row.id)
  load()
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    showModal.value = false
    showAtt.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="calendar" :size="17" /> 请假申请</h3>
      <button class="btn btn-primary btn-sm" @click="openCreate">
        <Icon name="plus" :size="14" /> 发起请假
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
              <th>单号</th>
              <th>申请人</th>
              <th>类型</th>
              <th>起止</th>
              <th>天数</th>
              <th>状态</th>
              <th class="nosort">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in data.list" :key="r.id">
              <td>{{ r.orderNo }}</td>
              <td>{{ r.employeeName }}</td>
              <td>{{ TYPES.find((t) => t[0] === r.leaveType)?.[1] ?? r.leaveType }}</td>
              <td>{{ (r.startAt || '').slice(0, 10) }} ~ {{ (r.endAt || '').slice(0, 10) }}</td>
              <td>{{ r.duration }}</td>
              <td>
                <span class="badge" :class="(STATUS_MAP[r.status] ?? { cls: 'badge-gray' }).cls">
                  {{ (STATUS_MAP[r.status] ?? { text: r.status }).text }}
                </span>
              </td>
              <td class="op">
                <a @click="openAttachments(r)">附件</a>
                <a v-if="r.status === 'rejected' || r.status === 'canceled'" @click="openResubmit(r)">重新提交</a>
                <a v-if="r.status === 'pending'" style="color: var(--danger)" @click="cancel(r)">撤销</a>
                <span v-if="r.status === 'approved'" style="color: var(--text-4)">—</span>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon :name="state.q ? 'search' : 'inbox'" :size="46" />
          <div>{{ state.q ? `未找到与「${state.q}」匹配的数据` : '暂无请假记录' }}</div>
        </div>
      </div>

      <div class="pager">
        <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
        <span>第 {{ data.page }} / {{ totalPages }} 页</span>
        <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
      </div>
    </div>
  </div>

  <!-- 附件（病假证明等） -->
  <div v-if="showAtt" class="mask" @click.self="showAtt = false">
    <div class="modal">
      <div class="modal-head">
        <h3><Icon name="paperclip" :size="18" /> 附件 · {{ attNo }}</h3>
        <span class="x" @click="showAtt = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <AttachmentPanel biz-type="leave" :biz-id="attId" />
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showAtt = false">关闭</button>
      </div>
    </div>
  </div>

  <div v-if="showModal" class="mask" @click.self="showModal = false">
    <div class="modal">
      <div class="modal-head">
        <h3><Icon name="calendar" :size="18" /> {{ mode === 'resubmit' ? '重新提交请假' : '发起请假' }}</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="grid">
          <div class="field">
            <label>请假类型</label>
            <select v-model="form.leaveType" class="input">
              <option v-for="t in TYPES" :key="t[0]" :value="t[0]">{{ t[1] }}</option>
            </select>
          </div>
          <div class="field">
            <label>天数</label>
            <input v-model.number="form.duration" type="number" min="0.5" step="0.5" class="input" />
          </div>
          <div class="field">
            <label>开始日期</label>
            <input v-model="form.startAt" type="date" class="input" />
          </div>
          <div class="field">
            <label>结束日期</label>
            <input v-model="form.endAt" type="date" class="input" />
          </div>
        </div>
        <div class="field">
          <label>事由</label>
          <textarea v-model="form.reason" rows="3" class="input" placeholder="选填"></textarea>
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <Icon name="check" :size="15" /> {{ mode === 'resubmit' ? '重新提交审批' : '提交审批' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nosort { cursor: default; }
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
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

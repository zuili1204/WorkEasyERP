<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import { auth } from '../stores/auth'
import { toast } from '../stores/toast'
import Icon from '../components/Icon.vue'

const SIZE = 8
const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

const showModal = ref(false)
const form = reactive({ category: 'travel', amount: 0, happenDate: '', reason: '' })
const saving = ref(false)
const errorMsg = ref('')

const CATS = [
  ['travel', '差旅'], ['office', '办公'], ['meal', '餐饮'], ['transport', '交通'],
  ['entertain', '招待'], ['train', '培训'], ['other', '其他'],
]
const STATUS: Record<string, { text: string; cls: string }> = {
  draft: { text: '草稿', cls: 'badge-gray' },
  approving: { text: '审批中', cls: 'badge-blue' },
  approved: { text: '已通过', cls: 'badge-green' },
  rejected: { text: '已驳回', cls: 'badge-red' },
  paid: { text: '已付款', cls: 'badge-green' },
  void: { text: '已作废', cls: 'badge-gray' },
}

async function load() {
  loading.value = true
  try {
    data.value = await api.expenses(state.q || undefined, state.status || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

function openModal() {
  errorMsg.value = ''
  form.amount = 0
  form.reason = ''
  form.happenDate = ''
  showModal.value = true
}

async function submit() {
  saving.value = true
  errorMsg.value = ''
  try {
    await api.createExpense({
      category: form.category,
      amount: Number(form.amount),
      happenDate: form.happenDate || undefined,
      reason: form.reason || undefined,
    })
    showModal.value = false
    state.page = 1
    await load()
    toast.success('报销已登记，记得提交审批')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '保存失败'
  } finally {
    saving.value = false
  }
}

async function act(id: string, kind: 'submit' | 'pay') {
  errorMsg.value = ''
  try {
    if (kind === 'submit') await api.submitExpense(id)
    else await api.payExpense(id)
    await load()
    toast.success(kind === 'submit' ? '已提交审批（主管 → 财务）' : '已标记为已付款')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '操作失败'
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function money(v: string | null) {
  const n = Number(v ?? 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

onMounted(async () => {
  state.scope = auth.user?.roles?.includes('boss') ? 'all' : 'dept'
  await load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="receipt" :size="17" /> 报销</h3>
      <button class="btn btn-primary btn-sm" @click="openModal"><Icon name="plus" :size="14" /> 发起报销</button>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="seg">
          <button class="seg-btn" :class="{ active: state.scope === 'mine' }" @click="state.scope = 'mine'; state.page = 1; load()">我的</button>
          <button class="seg-btn" :class="{ active: state.scope === 'dept' }" @click="state.scope = 'dept'; state.page = 1; load()">本部门</button>
          <button class="seg-btn" :class="{ active: state.scope === 'all' }" @click="state.scope = 'all'; state.page = 1; load()">全部</button>
        </div>
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索单号 / 申请人 / 事由" @input="load" />
        </div>
        <select v-model="state.status" class="input" style="width: 120px" @change="state.page = 1; load()">
          <option value="">全部状态</option>
          <option value="draft">草稿</option>
          <option value="approving">审批中</option>
          <option value="approved">已通过</option>
          <option value="rejected">已驳回</option>
          <option value="paid">已付款</option>
        </select>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <p v-if="errorMsg" class="err">{{ errorMsg }}</p>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th>单号</th><th>申请人</th><th>部门</th><th>类别</th>
              <th>金额</th><th>发生日期</th><th>事由</th><th>状态</th><th class="nosort">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.no }}</td>
              <td>{{ r.employee_name }}</td>
              <td>{{ r.dept_name }}</td>
              <td>{{ CATS.find((c) => c[0] === r.category)?.[1] ?? r.category }}</td>
              <td><b>{{ money(r.amount) }}</b></td>
              <td>{{ r.happen_date }}</td>
              <td>{{ r.reason || '—' }}</td>
              <td><span class="badge" :class="(STATUS[r.status ?? ''] ?? { cls: 'badge-gray' }).cls">{{ (STATUS[r.status ?? ''] ?? { text: r.status }).text }}</span></td>
              <td class="op">
                <a v-if="r.status === 'draft' || r.status === 'rejected'" @click="act(String(r.id), 'submit')">提交审批</a>
                <a v-else-if="r.status === 'approved'" @click="act(String(r.id), 'pay')">标记付款</a>
                <span v-else style="color: var(--text-4)">—</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!loading && data.total === 0" class="empty">
          <Icon name="inbox" :size="46" /><div>暂无报销记录</div>
        </div>
      </div>

      <div class="pager">
        <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
        <span>第 {{ data.page }} / {{ totalPages }} 页</span>
        <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
      </div>
      <p class="tip">流程：发起 → 主管审批 → 财务审批 → 标记付款</p>
    </div>
  </div>

  <div v-if="showModal" class="mask" @click.self="showModal = false">
    <div class="modal">
      <div class="modal-head">
        <h3><Icon name="receipt" :size="18" /> 发起报销</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="grid">
          <div class="field">
            <label>类别</label>
            <select v-model="form.category" class="input">
              <option v-for="c in CATS" :key="c[0]" :value="c[0]">{{ c[1] }}</option>
            </select>
          </div>
          <div class="field">
            <label>金额 *</label>
            <input v-model.number="form.amount" type="number" min="0" class="input" />
          </div>
          <div class="field">
            <label>发生日期</label>
            <input v-model="form.happenDate" type="date" class="input" />
          </div>
          <div class="field">
            <label>事由</label>
            <input v-model="form.reason" class="input" />
          </div>
        </div>
        <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">保存</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.field { margin-bottom: 12px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 5px; font-weight: 600; }
.nosort { cursor: default; }
.tip { font-size: 12px; color: var(--text-3); margin: 10px 0 0; }
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; }
.mask { position: fixed; inset: 0; background: rgba(15,23,42,.45); backdrop-filter: blur(3px); display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 60px 20px; }
.modal { background: #fff; border-radius: var(--radius-lg); width: 560px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 20px 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

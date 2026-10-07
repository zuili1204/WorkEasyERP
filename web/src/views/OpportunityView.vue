<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import PromptDialog from '../components/PromptDialog.vue'
import EmptyState from '../components/EmptyState.vue'

const SIZE = 8
const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

const customers = ref<{ id: string; name: string }[]>([])
const showModal = ref(false)
const form = reactive({ name: '', customerId: '', stage: 'contact', amount: 0, expectCloseDate: '' })
const saving = ref(false)
const errorMsg = ref('')
const notice = ref('')
const showLostDialog = ref(false)
const lostRow = ref<Record<string, string | null> | null>(null)

const STAGES = [
  ['contact', '初步接触'], ['quote', '报价'], ['solution', '方案'],
  ['negotiation', '商务谈判'], ['won', '赢单'], ['lost', '输单'],
]
const STATUS: Record<string, { text: string; cls: string }> = {
  open: { text: '进行中', cls: 'badge-blue' },
  won: { text: '赢单', cls: 'badge-green' },
  lost: { text: '输单', cls: 'badge-red' },
}

async function load() {
  loading.value = true
  try {
    data.value = await api.opportunities(state.q || undefined, state.status || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

async function loadCustomers() {
  const r = await api.customers(undefined, 'all', undefined, 1, 50)
  customers.value = r.list.map((x) => ({ id: String(x.id), name: String(x.name ?? '') }))
  if (!form.customerId && customers.value.length) form.customerId = customers.value[0].id
}

async function submit() {
  saving.value = true
  errorMsg.value = ''
  try {
    await api.createOpportunity({
      name: form.name.trim(),
      customerId: form.customerId || undefined,
      stage: form.stage,
      amount: Number(form.amount),
      expectCloseDate: form.expectCloseDate || undefined,
    })
    showModal.value = false
    form.name = ''
    form.amount = 0
    state.page = 1
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '保存失败'
  } finally {
    saving.value = false
  }
}

async function advance(row: Record<string, string | null>, stage: string) {
  errorMsg.value = ''
  notice.value = ''
  if (stage === 'lost') {
    lostRow.value = row
    showLostDialog.value = true
    return
  }
  await updateStage(row, stage, undefined)
}

async function updateStage(row: Record<string, string | null>, stage: string, reason: string | undefined) {
  try {
    await api.updateOppStage(String(row.id), stage, reason)
    notice.value = stage === 'won' ? '已标记赢单，可转为合同' : `阶段已更新为「${STAGES.find((s) => s[0] === stage)?.[1]}」`
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '操作失败'
  }
}

function onLostConfirm(reason: string) {
  if (lostRow.value) void updateStage(lostRow.value, 'lost', reason)
}

async function toContract(row: Record<string, string | null>) {
  errorMsg.value = ''
  try {
    const r = await api.convertOpportunity(String(row.id))
    notice.value = `已生成合同 ${r.contractNo}`
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '转化失败'
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function money(v: string | null) {
  return Number(v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

onMounted(async () => {
  await loadCustomers()
  await load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="trending-up" :size="17" /> 商机管理</h3>
      <button class="btn btn-primary btn-sm" @click="showModal = true"><Icon name="plus" :size="14" /> 新增商机</button>
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
          <input v-model="state.q" class="input" placeholder="搜索商机" @input="load" />
        </div>
        <select v-model="state.status" class="input" style="width: 120px" @change="state.page = 1; load()">
          <option value="">全部状态</option>
          <option value="open">进行中</option>
          <option value="won">赢单</option>
          <option value="lost">输单</option>
        </select>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
      <p v-if="notice" class="ok">{{ notice }}</p>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr><th>编号</th><th>商机</th><th>客户</th><th>阶段</th><th>预计金额</th><th>预计成交</th><th>状态</th><th class="nosort">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.no }}</td>
              <td>{{ r.name }}</td>
              <td>{{ r.customer_name || '—' }}</td>
              <td>{{ STAGES.find((s) => s[0] === r.stage)?.[1] ?? r.stage }}</td>
              <td><b>{{ money(r.amount) }}</b></td>
              <td>{{ r.expect_close_date || '—' }}</td>
              <td><span class="badge" :class="(STATUS[r.status ?? ''] ?? { cls: 'badge-gray' }).cls">{{ (STATUS[r.status ?? ''] ?? { text: r.status }).text }}</span></td>
              <td class="op">
                <template v-if="r.status === 'open'">
                  <a @click="advance(r, 'negotiation')">推进谈判</a>
                  <a @click="advance(r, 'won')">赢单</a>
                  <a style="color: var(--danger)" @click="advance(r, 'lost')">输单</a>
                </template>
                <a v-else-if="r.status === 'won'" @click="toContract(r)">转合同</a>
                <span v-else style="color: var(--text-4)">—</span>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-if="!loading && data.total === 0" icon="target" title="暂无商机" desc="点击右上角「新增商机」开始记录客户跟进" />
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
        <h3><Icon name="trending-up" :size="18" /> 新增商机</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="field">
          <label>商机名称 *</label>
          <input v-model="form.name" class="input" />
        </div>
        <div class="grid">
          <div class="field">
            <label>客户</label>
            <select v-model="form.customerId" class="input">
              <option v-for="c in customers" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="field">
            <label>阶段</label>
            <select v-model="form.stage" class="input">
              <option v-for="s in STAGES.slice(0, 4)" :key="s[0]" :value="s[0]">{{ s[1] }}</option>
            </select>
          </div>
          <div class="field">
            <label>预计金额</label>
            <input v-model.number="form.amount" type="number" min="0" class="input" />
          </div>
          <div class="field">
            <label>预计成交日</label>
            <input v-model="form.expectCloseDate" type="date" class="input" />
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

  <PromptDialog
    :open="showLostDialog"
    title="标记输单"
    label="请填写输单原因"
    placeholder="如：价格偏高 / 竞争对手中标 / 客户预算取消…"
    required
    confirm-text="确认输单"
    @update:open="showLostDialog = $event"
    @confirm="onLostConfirm"
  />
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.field { margin-bottom: 12px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 5px; font-weight: 600; }
.nosort { cursor: default; }
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
.ok { background: var(--accent-light); color: #15803d; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
.mask { position: fixed; inset: 0; background: rgba(15,23,42,.45); backdrop-filter: blur(3px); display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 60px 20px; }
.modal { background: #fff; border-radius: var(--radius-lg); width: 600px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 20px 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

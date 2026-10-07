<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import { toast } from '../stores/toast'

const SIZE = 8
const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

const suppliers = ref<{ id: string; name: string }[]>([])
const products = ref<{ id: string; name: string; sku: string }[]>([])
const showModal = ref(false)
const form = reactive({ supplierId: '', expectDate: '', reason: '' })
const lines = ref<{ productId: string; qty: number }[]>([{ productId: '', qty: 1 }])
const saving = ref(false)
const errorMsg = ref('')
const notice = ref('')

const STATUS: Record<string, { text: string; cls: string }> = {
  draft: { text: '草稿', cls: 'badge-gray' },
  approving: { text: '审批中', cls: 'badge-blue' },
  approved: { text: '已通过', cls: 'badge-green' },
  rejected: { text: '已驳回', cls: 'badge-red' },
  converted: { text: '已转订单', cls: 'badge-green' },
  void: { text: '已作废', cls: 'badge-gray' },
}

async function load() {
  loading.value = true
  try {
    data.value = await api.purchaseRequests(state.q || undefined, state.status || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

async function loadRefs() {
  const s = await api.baseList('supplier', undefined, 1, 50)
  suppliers.value = s.list.map((x) => ({ id: String(x.id), name: String(x.name ?? '') }))
  const p = await api.baseList('product', undefined, 1, 50)
  products.value = p.list.map((x) => ({
    id: String(x.id), name: String(x.name ?? ''), sku: String(x.sku ?? ''),
  }))
  if (!form.supplierId && suppliers.value.length) form.supplierId = suppliers.value[0].id
  if (!lines.value[0].productId && products.value.length) lines.value[0].productId = products.value[0].id
}

function addLine() {
  lines.value.push({ productId: products.value[0]?.id ?? '', qty: 1 })
}

function removeLine(i: number) {
  if (lines.value.length === 1) return
  lines.value.splice(i, 1)
}

async function submit() {
  saving.value = true
  errorMsg.value = ''
  try {
    await api.createPurchaseRequest({
      supplierId: form.supplierId || undefined,
      expectDate: form.expectDate || undefined,
      reason: form.reason || undefined,
      items: lines.value.map((l) => ({ productId: l.productId, qty: Number(l.qty) })),
    })
    showModal.value = false
    form.reason = ''
    lines.value = [{ productId: products.value[0]?.id ?? '', qty: 1 }]
    state.page = 1
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '保存失败'
  } finally {
    saving.value = false
  }
}

async function act(row: Record<string, string | null>, kind: 'submit' | 'order') {
  errorMsg.value = ''
  notice.value = ''
  try {
    if (kind === 'submit') {
      await api.submitPurchaseRequest(String(row.id))
      notice.value = '已提交审批（主管 → 财务）'
    } else {
      const r = await api.purchaseRequestToOrder(String(row.id))
      notice.value = `已生成采购订单 ${r.orderNo}（草稿，需再提交订单审批）`
      toast.success(`已转采购订单 ${r.orderNo}`)
    }
    await load()
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

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

onMounted(async () => {
  await loadRefs()
  await load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="shopping-cart" :size="17" /> 采购申请</h3>
      <button class="btn btn-primary btn-sm" @click="showModal = true"><Icon name="plus" :size="14" /> 起草申请</button>
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
          <input v-model="state.q" class="input" placeholder="搜索单号 / 供应商 / 事由" @input="load" />
        </div>
        <select v-model="state.status" class="input" style="width: 120px" @change="state.page = 1; load()">
          <option value="">全部状态</option>
          <option value="draft">草稿</option>
          <option value="approving">审批中</option>
          <option value="approved">已通过</option>
          <option value="rejected">已驳回</option>
          <option value="converted">已转订单</option>
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
            <tr><th>单号</th><th>申请人</th><th>供应商</th><th>数量</th><th>期望日期</th><th>事由</th><th>状态</th><th class="nosort">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.no }}</td>
              <td>{{ r.applicant_name }}</td>
              <td>{{ r.supplier_name || '—' }}</td>
              <td>{{ r.total_qty }}</td>
              <td>{{ r.expect_date || '—' }}</td>
              <td>{{ r.reason || '—' }}</td>
              <td><span class="badge" :class="(STATUS[r.status ?? ''] ?? { cls: 'badge-gray' }).cls">{{ (STATUS[r.status ?? ''] ?? { text: r.status }).text }}</span></td>
              <td class="op">
                <a v-if="r.status === 'draft' || r.status === 'rejected'" @click="act(r, 'submit')">提交审批</a>
                <a v-else-if="r.status === 'approved'" @click="act(r, 'order')">转采购订单</a>
                <span v-else style="color: var(--text-4)">—</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!loading && data.total === 0" class="empty"><Icon name="inbox" :size="46" /><div>暂无采购申请</div></div>
      </div>

      <div class="pager">
        <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
        <span>第 {{ data.page }} / {{ totalPages }} 页</span>
        <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
      </div>
    </div>
  </div>

  <div v-if="showModal" class="mask" @click.self="showModal = false">
    <div class="modal wide">
      <div class="modal-head">
        <h3><Icon name="shopping-cart" :size="18" /> 起草采购申请</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="grid">
          <div class="field">
            <label>供应商</label>
            <select v-model="form.supplierId" class="input">
              <option v-for="s in suppliers" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="field">
            <label>期望到货日</label>
            <input v-model="form.expectDate" type="date" class="input" />
          </div>
        </div>
        <div class="field">
          <label>申请事由</label>
          <input v-model="form.reason" class="input" />
        </div>

        <div class="lines">
          <div class="lhead">
            <b>申请明细</b>
            <button class="btn btn-sm btn-ghost" @click="addLine"><Icon name="plus" :size="14" /> 加行</button>
          </div>
          <div v-for="(l, i) in lines" :key="i" class="line">
            <select v-model="l.productId" class="input" style="flex: 2">
              <option v-for="p in products" :key="p.id" :value="p.id">{{ p.sku }} · {{ p.name }}</option>
            </select>
            <input v-model.number="l.qty" type="number" min="1" class="input" style="width: 100px" />
            <button class="rm" :disabled="lines.length === 1" @click="removeLine(i)"><Icon name="x" :size="15" /></button>
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
.lines { border: 1px solid var(--border-2); border-radius: var(--radius-sm); padding: 12px; margin-top: 6px; }
.lhead { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; font-size: 13px; }
.line { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
.rm { border: 0; background: transparent; color: var(--text-3); cursor: pointer; display: flex; padding: 4px; }
.rm:hover:not(:disabled) { color: var(--danger); }
.rm:disabled { opacity: .3; cursor: not-allowed; }
.nosort { cursor: default; }
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-top: 10px; }
.ok { background: var(--accent-light); color: #15803d; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
.mask { position: fixed; inset: 0; background: rgba(15,23,42,.45); backdrop-filter: blur(3px); display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 60px 20px; }
.modal { background: #fff; border-radius: var(--radius-lg); width: 560px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal.wide { width: 700px; }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 20px 24px; max-height: 62vh; overflow: auto; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

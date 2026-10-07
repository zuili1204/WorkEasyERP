<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'

const SIZE = 8
const TABS = [
  ['sales', '销售退货'],
  ['purchase', '采购退货'],
]

const kind = ref('sales')
const isSales = computed(() => kind.value === 'sales')
const state = reactive({ q: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)

const parties = ref<{ id: string; name: string }[]>([])
const products = ref<{ id: string; name: string; sku: string; salePrice: string | null }[]>([])
const warehouses = ref<{ id: string; name: string }[]>([])

const showModal = ref(false)
const form = reactive({ partyId: '', warehouseId: '', reason: '' })
const lines = ref<{ productId: string; qty: number; price: number }[]>([])
const saving = ref(false)
const errorMsg = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = isSales.value
      ? await api.salesReturns(state.q || undefined, state.page, SIZE)
      : await api.purchaseReturns(state.q || undefined, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

async function loadRefs() {
  const p = await api.baseList('product', undefined, 1, 50)
  products.value = p.list.map((x) => ({
    id: String(x.id), name: String(x.name ?? ''), sku: String(x.sku ?? ''), salePrice: x.sale_price,
  }))
  const w = await api.baseList('warehouse', undefined, 1, 50)
  warehouses.value = w.list.map((x) => ({ id: String(x.id), name: String(x.name ?? '') }))
  if (!form.warehouseId && warehouses.value.length) form.warehouseId = warehouses.value[0].id
  await loadParties()
}

async function loadParties() {
  if (isSales.value) {
    const r = await api.customers(undefined, 'all', undefined, 1, 50)
    parties.value = r.list.map((x) => ({ id: String(x.id), name: String(x.name ?? '') }))
  } else {
    const r = await api.baseList('supplier', undefined, 1, 50)
    parties.value = r.list.map((x) => ({ id: String(x.id), name: String(x.name ?? '') }))
  }
  if (!form.partyId && parties.value.length) form.partyId = parties.value[0].id
}

function openModal() {
  errorMsg.value = ''
  form.reason = ''
  lines.value = [{
    productId: products.value[0]?.id ?? '',
    qty: 1,
    price: Number(isSales.value ? (products.value[0]?.salePrice ?? 0) : 0),
  }]
  showModal.value = true
}

async function submit() {
  if (!form.partyId || lines.value.length === 0) return
  saving.value = true
  errorMsg.value = ''
  try {
    const items = lines.value.map((l) => ({
      productId: l.productId, qty: Number(l.qty), price: Number(l.price),
    }))
    if (isSales.value) {
      await api.createSalesReturn({
        customerId: form.partyId, warehouseId: form.warehouseId, reason: form.reason || undefined, items,
      })
    } else {
      await api.createPurchaseReturn({
        supplierId: form.partyId, warehouseId: form.warehouseId, remark: form.reason || undefined, items,
      })
    }
    showModal.value = false
    state.page = 1
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '保存失败'
  } finally {
    saving.value = false
  }
}

async function audit(row: Record<string, string | null>) {
  errorMsg.value = ''
  try {
    if (isSales.value) await api.auditSalesReturn(String(row.id))
    else await api.auditPurchaseReturn(String(row.id))
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '审核失败'
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function statusBadge(s: string | null) {
  if (s === 'done') return { text: '已完成', cls: 'badge-green' }
  if (s === 'void') return { text: '已作废', cls: 'badge-gray' }
  return { text: '草稿', cls: 'badge-orange' }
}

function money(v: string | null | undefined) {
  if (v == null) return '—'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

watch(kind, () => {
  state.page = 1
  state.q = ''
  form.partyId = ''
  loadParties()
  load()
})

onMounted(async () => {
  await loadRefs()
  await load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="rotate-ccw" :size="17" /> 退货管理</h3>
      <button class="btn btn-primary btn-sm" @click="openModal">
        <Icon name="plus" :size="14" /> 新建{{ isSales ? '销售退货' : '采购退货' }}
      </button>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="seg">
          <button v-for="t in TABS" :key="t[0]" class="seg-btn" :class="{ active: kind === t[0] }" @click="kind = t[0]">
            {{ t[1] }}
          </button>
        </div>
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索单号 / 往来单位" @input="load" />
        </div>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <p v-if="errorMsg" class="err">{{ errorMsg }}</p>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th>单号</th>
              <th>{{ isSales ? '客户' : '供应商' }}</th>
              <th v-if="isSales">数量</th>
              <th>金额</th>
              <th>{{ isSales ? '已红冲应收' : '已红冲应付' }}</th>
              <th>状态</th>
              <th class="nosort">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.return_no }}</td>
              <td>{{ isSales ? r.customer_name : r.supplier_name }}</td>
              <td v-if="isSales">{{ Number(r.total_qty ?? 0).toFixed(3) }}</td>
              <td>{{ money(r.total_amount) }}</td>
              <td>{{ money(isSales ? r.ar_offset_amount : r.ap_offset_amount) }}</td>
              <td><span class="badge" :class="statusBadge(r.status).cls">{{ statusBadge(r.status).text }}</span></td>
              <td class="op">
                <a v-if="r.status === 'draft'" style="color: var(--accent)" @click="audit(r)">审核</a>
                <span v-else style="color: var(--text-4)">—</span>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon name="inbox" :size="46" />
          <div>暂无退货单</div>
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
        <h3><Icon name="rotate-ccw" :size="18" /> 新建{{ isSales ? '销售退货' : '采购退货' }}</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="grid">
          <div class="field">
            <label>{{ isSales ? '客户' : '供应商' }} *</label>
            <select v-model="form.partyId" class="input">
              <option v-for="p in parties" :key="p.id" :value="p.id">{{ p.name }}</option>
            </select>
          </div>
          <div class="field">
            <label>仓库 *</label>
            <select v-model="form.warehouseId" class="input">
              <option v-for="w in warehouses" :key="w.id" :value="w.id">{{ w.name }}</option>
            </select>
          </div>
        </div>
        <div class="field">
          <label>{{ isSales ? '退货原因' : '备注' }}</label>
          <input v-model="form.reason" class="input" />
        </div>
        <div class="lines-head">
          <b>退货明细</b>
          <button class="btn btn-sm" @click="lines.push({ productId: products[0]?.id ?? '', qty: 1, price: 0 })">
            <Icon name="plus" :size="13" /> 增行
          </button>
        </div>
        <table class="lines">
          <thead><tr><th>商品</th><th style="width: 92px">数量</th><th style="width: 108px">单价</th></tr></thead>
          <tbody>
            <tr v-for="(l, i) in lines" :key="i">
              <td>
                <select v-model="l.productId" class="input sm">
                  <option v-for="p in products" :key="p.id" :value="p.id">{{ p.sku }} · {{ p.name }}</option>
                </select>
              </td>
              <td><input v-model.number="l.qty" type="number" min="1" class="input sm" /></td>
              <td>
                <input v-model.number="l.price" type="number" min="0" class="input sm"
                       :disabled="!isSales" title="采购退货按当前加权成本自动计价" />
              </td>
            </tr>
          </tbody>
        </table>
        <p class="tip">
          {{ isSales
            ? '审核后：按当前加权成本回库 + 红冲该客户应收（到期日早优先）+ 释放授信'
            : '审核后：扣减库存 + 红冲该供应商应付（金额按当前加权成本自动计算）' }}
        </p>
        <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <Icon name="check" :size="15" /> 创建退货单
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
.lines-head { display: flex; justify-content: space-between; align-items: center; margin: 6px 0 8px; font-size: 13px; }
.lines { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.lines th { text-align: left; color: var(--text-3); font-weight: 600; padding-bottom: 4px; }
.lines td { padding: 3px 4px 3px 0; }
.input.sm { padding: 6px 8px; font-size: 12.5px; }
.tip { font-size: 12px; color: var(--text-3); margin: 10px 0 0; }
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin: 10px 0 0; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 60px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 640px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

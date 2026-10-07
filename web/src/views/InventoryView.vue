<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import { auth } from '../stores/auth'

const SIZE = 8
const TABS = [
  ['stock', '库存现量'],
  ['txn', '出入库流水'],
]

const tab = ref('stock')
const state = reactive({ q: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)

const products = ref<Record<string, string | null>[]>([])
const warehouses = ref<Record<string, string | null>[]>([])
const showModal = ref(false)
const mode = ref<'in' | 'out'>('in')
const form = reactive({ productId: '', warehouseId: '', qty: 1, price: 0, remark: '' })
const saving = ref(false)
const errorMsg = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = tab.value === 'stock'
      ? await api.stock(state.q || undefined, state.page, SIZE)
      : await api.invTxns(state.q || undefined, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

async function loadRefs() {
  const p = await api.baseList('product', undefined, 1, 50)
  products.value = p.list
  const w = await api.baseList('warehouse', undefined, 1, 50)
  warehouses.value = w.list
  if (!form.productId && p.list.length) form.productId = String(p.list[0].id)
  if (!form.warehouseId && w.list.length) form.warehouseId = String(w.list[0].id)
}

function openMove(m: 'in' | 'out') {
  mode.value = m
  errorMsg.value = ''
  form.qty = 1
  form.price = 0
  form.remark = ''
  showModal.value = true
}

async function submitMove() {
  saving.value = true
  errorMsg.value = ''
  try {
    const body = {
      productId: form.productId,
      warehouseId: form.warehouseId,
      qty: Number(form.qty),
      price: mode.value === 'in' ? Number(form.price) : undefined,
      remark: form.remark || undefined,
      refType: mode.value === 'in' ? 'purchase' : 'sales',
    }
    if (mode.value === 'in') await api.inbound(body)
    else await api.outbound(body)
    showModal.value = false
    state.page = 1
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '操作失败'
  } finally {
    saving.value = false
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function num(v: string | null | undefined, d = 2) {
  if (v == null) return '—'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: d })
}

function txnBadge(t: string | null) {
  if (t === 'in') return { text: '入库', cls: 'badge-green' }
  if (t === 'out') return { text: '出库', cls: 'badge-blue' }
  return { text: t || '—', cls: 'badge-gray' }
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

async function exportXlsx() {
  const blob = await api.exportFile('inventory')
  api.saveBlob(blob, `inventory-${new Date().toISOString().slice(0, 10)}.xlsx`)
}

watch(tab, () => {
  state.page = 1
  state.q = ''
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
      <h3><Icon name="package" :size="17" /> 库存管理</h3>
      <div class="head-actions">
        <button class="btn btn-sm" @click="openMove('in')"><Icon name="plus" :size="14" /> 入库</button>
        <button class="btn btn-sm btn-primary" @click="openMove('out')">
          <Icon name="truck" :size="14" /> 出库
        </button>
      </div>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="seg">
          <button
            v-for="t in TABS"
            :key="t[0]"
            class="seg-btn"
            :class="{ active: tab === t[0] }"
            @click="tab = t[0]"
          >{{ t[1] }}</button>
        </div>
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" :placeholder="tab === 'stock' ? '搜索商品 / SKU' : '搜索商品 / 流水号'" @input="load" />
        </div>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <button class="btn btn-sm" @click="exportXlsx"><Icon name="download" :size="15" /> 导出 Excel</button>
        <div class="spacer" />
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <!-- 库存现量 -->
      <div v-if="tab === 'stock'" class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th>SKU</th>
              <th>商品</th>
              <th>仓库</th>
              <th>现量</th>
              <th>可用</th>
              <th v-if="!auth.hidden('avg_cost')">加权成本</th>
              <th v-if="!auth.hidden('last_cost')">最近成本</th>
              <th v-if="!auth.hidden('stock_amount')">库存金额</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.sku }}</td>
              <td>{{ r.product_name }}</td>
              <td>{{ r.warehouse_name }}</td>
              <td>{{ num(r.qty, 3) }} {{ r.unit || '' }}</td>
              <td>{{ num(r.available_qty, 3) }}</td>
              <td v-if="!auth.hidden('avg_cost')">{{ num(r.avg_cost, 4) }}</td>
              <td v-if="!auth.hidden('last_cost')">{{ num(r.last_cost, 4) }}</td>
              <td v-if="!auth.hidden('stock_amount')"><b>{{ num(r.stock_amount) }}</b></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 出入库流水 -->
      <div v-else class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th>流水号</th>
              <th>类型</th>
              <th>商品</th>
              <th>数量</th>
              <th>单价</th>
              <th>金额</th>
              <th>结存</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.txn_no }}</td>
              <td>
                <span class="badge" :class="txnBadge(r.txn_type).cls">{{ txnBadge(r.txn_type).text }}</span>
              </td>
              <td>{{ r.product_name }}</td>
              <td :style="Number(r.qty) < 0 ? 'color: var(--danger)' : 'color: var(--accent)'">
                {{ num(r.qty, 3) }}
              </td>
              <td>{{ num(r.price, 4) }}</td>
              <td>{{ num(r.amount) }}</td>
              <td>{{ num(r.balance_qty, 3) }}</td>
              <td>{{ (r.created_at || '').replace('T', ' ').slice(0, 16) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="!loading && data.total === 0" class="empty">
        <Icon name="inbox" :size="46" />
        <div>{{ tab === 'stock' ? '暂无库存，请先做入库' : '暂无出入库流水' }}</div>
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
        <h3><Icon :name="mode === 'in' ? 'plus' : 'truck'" :size="18" /> {{ mode === 'in' ? '入库' : '出库' }}</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="field">
          <label>商品 *</label>
          <select v-model="form.productId" class="input">
            <option v-for="p in products" :key="String(p.id)" :value="String(p.id)">
              {{ p.sku }} · {{ p.name }}
            </option>
          </select>
        </div>
        <div class="field">
          <label>仓库 *</label>
          <select v-model="form.warehouseId" class="input">
            <option v-for="w in warehouses" :key="String(w.id)" :value="String(w.id)">{{ w.name }}</option>
          </select>
        </div>
        <div class="grid">
          <div class="field">
            <label>数量 *</label>
            <input v-model.number="form.qty" type="number" min="1" class="input" />
          </div>
          <div v-if="mode === 'in'" class="field">
            <label>入库单价 *</label>
            <input v-model.number="form.price" type="number" min="0" class="input" />
          </div>
        </div>
        <div class="field">
          <label>备注</label>
          <input v-model="form.remark" class="input" />
        </div>
        <p class="tip">
          {{ mode === 'in'
            ? '入库按移动加权平均更新成本：(原数量×原均价 + 入库量×入库价) ÷ 新数量'
            : '出库按当前加权成本计价并扣减数量，库存不足将拒绝' }}
        </p>
        <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submitMove">
          <Icon name="check" :size="15" /> 确认{{ mode === 'in' ? '入库' : '出库' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head-actions { display: flex; gap: 8px; }
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.tip { font-size: 12px; color: var(--text-3); margin: 6px 0 0; }
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin-top: 12px; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 80px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 500px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

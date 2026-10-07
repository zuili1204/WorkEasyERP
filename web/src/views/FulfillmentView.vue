<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'

const SIZE = 8
const route = useRoute()
/** inbound=采购入库；outbound=销售出库 */
const type = computed(() => (route.path.includes('inbounds') ? 'inbound' : 'outbound'))
const isIn = computed(() => type.value === 'inbound')

const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)

const parties = ref<{ id: string; name: string }[]>([])
const warehouses = ref<{ id: string; name: string }[]>([])
const products = ref<{ id: string; name: string; sku: string; salePrice: string | null }[]>([])

const showModal = ref(false)
const form = reactive({ partyId: '', warehouseId: '', remark: '' })
const lines = ref<{ productId: string; qty: number; price: number; taxRate: number }[]>([])
const saving = ref(false)
const errorMsg = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

const title = computed(() => (isIn.value ? '采购入库' : '销售出库'))

async function load() {
  loading.value = true
  try {
    data.value = isIn.value
      ? await api.inbounds(state.q || undefined, state.status || undefined, state.page, SIZE)
      : await api.outbounds(state.q || undefined, state.status || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

async function loadRefs() {
  const w = await api.baseList('warehouse', undefined, 1, 50)
  warehouses.value = w.list.map((x) => ({ id: String(x.id), name: String(x.name ?? '') }))
  const p = await api.baseList('product', undefined, 1, 50)
  products.value = p.list.map((x) => ({
    id: String(x.id),
    name: String(x.name ?? ''),
    sku: String(x.sku ?? ''),
    salePrice: x.sale_price,
  }))
  await loadParties()
  if (!form.warehouseId && warehouses.value.length) form.warehouseId = warehouses.value[0].id
}

async function loadParties() {
  if (isIn.value) {
    const r = await api.baseList('supplier', undefined, 1, 50)
    parties.value = r.list.map((x) => ({ id: String(x.id), name: String(x.name ?? '') }))
  } else {
    const r = await api.customers(undefined, 'all', undefined, 1, 50)
    parties.value = r.list.map((x) => ({ id: String(x.id), name: String(x.name ?? '') }))
  }
  if (!form.partyId && parties.value.length) form.partyId = parties.value[0].id
}

function openModal() {
  errorMsg.value = ''
  form.remark = ''
  lines.value = [{ productId: products.value[0]?.id ?? '', qty: 10, price: Number(products.value[0]?.salePrice ?? 0), taxRate: 0.13 }]
  showModal.value = true
}

function addLine() {
  lines.value.push({
    productId: products.value[0]?.id ?? '',
    qty: 1,
    price: Number(products.value[0]?.salePrice ?? 0),
    taxRate: 0.13,
  })
}

function removeLine(i: number) {
  lines.value.splice(i, 1)
}

const total = computed(() =>
  lines.value.reduce((s, l) => s + Number(l.qty || 0) * Number(l.price || 0) * (1 + Number(l.taxRate || 0)), 0),
)

async function submit() {
  if (!form.partyId || !form.warehouseId || lines.value.length === 0) return
  saving.value = true
  errorMsg.value = ''
  try {
    const items = lines.value.map((l) => ({
      productId: l.productId,
      qty: Number(l.qty),
      price: Number(l.price),
      taxRate: Number(l.taxRate),
    }))
    if (isIn.value) {
      await api.createInbound({
        supplierId: form.partyId,
        warehouseId: form.warehouseId,
        remark: form.remark || undefined,
        items,
      })
    } else {
      await api.createOutbound({
        customerId: form.partyId,
        warehouseId: form.warehouseId,
        remark: form.remark || undefined,
        items,
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
    if (isIn.value) await api.auditInbound(String(row.id))
    else await api.auditOutbound(String(row.id))
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
  if (s === 'draft') return { text: '草稿', cls: 'badge-orange' }
  if (s === 'void') return { text: '已作废', cls: 'badge-gray' }
  return { text: s || '—', cls: 'badge-blue' }
}

function money(v: string | null | undefined) {
  if (v == null) return '—'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

watch(type, () => {
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
      <h3><Icon :name="isIn ? 'package' : 'truck'" :size="17" /> {{ title }}</h3>
      <button class="btn btn-primary btn-sm" @click="openModal">
        <Icon name="plus" :size="14" /> 新建{{ title }}单
      </button>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索单号 / 往来单位" @input="load" />
        </div>
        <select v-model="state.status" class="input" style="width: 120px" @change="load">
          <option value="">全部状态</option>
          <option value="draft">草稿</option>
          <option value="done">已完成</option>
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
              <th>单号</th>
              <th>{{ isIn ? '供应商' : '客户' }}</th>
              <th>仓库</th>
              <th>数量</th>
              <th>价税合计</th>
              <th>状态</th>
              <th class="nosort">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ isIn ? r.inbound_no : r.outbound_no }}</td>
              <td>{{ isIn ? r.supplier_name : r.customer_name }}</td>
              <td>{{ r.warehouse_name }}</td>
              <td>{{ Number(r.total_qty ?? 0).toFixed(3) }}</td>
              <td>{{ money(r.total_amount) }}</td>
              <td>
                <span class="badge" :class="statusBadge(r.status).cls">{{ statusBadge(r.status).text }}</span>
              </td>
              <td class="op">
                <a v-if="r.status === 'draft'" style="color: var(--accent)" @click="audit(r)">审核</a>
                <span v-else style="color: var(--text-4)">—</span>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon name="inbox" :size="46" />
          <div>暂无{{ title }}单</div>
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
        <h3><Icon :name="isIn ? 'package' : 'truck'" :size="18" /> 新建{{ title }}单</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="grid">
          <div class="field">
            <label>{{ isIn ? '供应商' : '客户' }} *</label>
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

        <div class="lines-head">
          <b>明细行</b>
          <button class="btn btn-sm" @click="addLine"><Icon name="plus" :size="13" /> 增行</button>
        </div>
        <table class="lines">
          <thead>
            <tr><th>商品</th><th style="width: 92px">数量</th><th style="width: 108px">单价</th><th style="width: 84px">税率</th><th style="width: 40px"></th></tr>
          </thead>
          <tbody>
            <tr v-for="(l, i) in lines" :key="i">
              <td>
                <select v-model="l.productId" class="input sm">
                  <option v-for="p in products" :key="p.id" :value="p.id">{{ p.sku }} · {{ p.name }}</option>
                </select>
              </td>
              <td><input v-model.number="l.qty" type="number" min="1" class="input sm" /></td>
              <td><input v-model.number="l.price" type="number" min="0" class="input sm" /></td>
              <td><input v-model.number="l.taxRate" type="number" step="0.01" class="input sm" /></td>
              <td><a style="color: var(--danger)" @click="removeLine(i)">删</a></td>
            </tr>
          </tbody>
        </table>
        <p class="total">价税合计：<b>{{ total.toFixed(2) }}</b></p>
        <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
        <p class="tip">
          审核时{{ isIn ? '按移动加权更新库存成本并生成应付' : '按加权成本扣减库存、校验授信并生成应收' }}
        </p>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <Icon name="check" :size="15" /> 保存草稿
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
.total { text-align: right; font-size: 13px; color: var(--text-2); margin: 10px 0 0; }
.tip { font-size: 12px; color: var(--text-3); margin: 10px 0 0; }
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin: 10px 0 0; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 60px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 680px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

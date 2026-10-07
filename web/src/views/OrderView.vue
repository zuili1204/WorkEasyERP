<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useRoute } from 'vue-router'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import { auth } from '../stores/auth'
import { toast } from '../stores/toast'

const SIZE = 8
const route = useRoute()
const router = useRouter()
/** purchase=采购订单；sales=销售订单 */
const kind = computed(() => (route.path.includes('/orders/purchase') ? 'purchase' : 'sales'))
const isPo = computed(() => kind.value === 'purchase')

const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)

const parties = ref<{ id: string; name: string }[]>([])
const products = ref<{ id: string; name: string; sku: string; salePrice: string | null }[]>([])
const warehouses = ref<{ id: string; name: string }[]>([])

const showModal = ref(false)
const form = reactive({ partyId: '', warehouseId: '', creditDays: 30, remark: '' })
const lines = ref<{ productId: string; qty: number; price: number; taxRate: number }[]>([])
const saving = ref(false)
const errorMsg = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))
const title = computed(() => (isPo.value ? '采购订单' : '销售订单'))

async function load() {
  loading.value = true
  try {
    data.value = isPo.value
      ? await api.poList(state.q || undefined, state.status || undefined, state.page, SIZE)
      : await api.soList(state.q || undefined, state.status || undefined, state.scope, state.page, SIZE)
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
  if (isPo.value) {
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
  lines.value = [{
    productId: products.value[0]?.id ?? '',
    qty: 10,
    price: Number(isPo.value ? 10 : (products.value[0]?.salePrice ?? 0)),
    taxRate: 0.13,
  }]
  showModal.value = true
}

function addLine() {
  lines.value.push({ productId: products.value[0]?.id ?? '', qty: 1, price: 0, taxRate: 0.13 })
}

function removeLine(i: number) {
  lines.value.splice(i, 1)
}

const totalNet = computed(() => lines.value.reduce((s, l) => s + Number(l.qty || 0) * Number(l.price || 0), 0))
const totalTax = computed(() => lines.value.reduce((s, l) => s + Number(l.qty || 0) * Number(l.price || 0) * Number(l.taxRate || 0), 0))

async function submit() {
  if (!form.partyId || lines.value.length === 0) return
  saving.value = true
  errorMsg.value = ''
  try {
    const body = {
      partyId: form.partyId,
      warehouseId: form.warehouseId || undefined,
      creditDays: Number(form.creditDays),
      remark: form.remark || undefined,
      items: lines.value.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price), taxRate: Number(l.taxRate),
      })),
    }
    if (isPo.value) await api.createPo(body)
    else await api.createSo(body)
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

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function statusBadge(s: string | null) {
  const map: Record<string, { text: string; cls: string }> = {
    draft: { text: '草稿', cls: 'badge-gray' },
    approving: { text: '审批中', cls: 'badge-blue' },
    approved: { text: '已审批', cls: 'badge-green' },
    rejected: { text: '已驳回', cls: 'badge-red' },
    partial: { text: '部分执行', cls: 'badge-orange' },
    received: { text: '已收货', cls: 'badge-green' },
    shipped: { text: '已发货', cls: 'badge-green' },
    closed: { text: '已关闭', cls: 'badge-gray' },
    void: { text: '已作废', cls: 'badge-gray' },
  }
  return map[s ?? ''] ?? { text: s ?? '—', cls: 'badge-gray' }
}

function printOrder(row: Record<string, string | null>) {
  router.push(`/print/${isPo.value ? 'purchase_order' : 'sales_order'}/${String(row.id)}`)
}

/** 提交审批：草稿或已驳回可提交，通过后才能收发货 */
async function submitApproval(row: Record<string, string | null>) {
  errorMsg.value = ''
  try {
    if (isPo.value) await api.submitPo(String(row.id))
    else await api.submitSo(String(row.id))
    await load()
    toast.success('已提交审批，审批通过后才能收发货')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '提交失败'
  }
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
      <h3><Icon :name="isPo ? 'shopping-cart' : 'briefcase'" :size="17" /> {{ title }}</h3>
      <button class="btn btn-primary btn-sm" @click="openModal">
        <Icon name="plus" :size="14" /> 新建{{ title }}
      </button>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索单号 / 往来单位" @input="load" />
        </div>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div v-if="!isPo" class="seg">
          <button class="seg-btn" :class="{ active: state.scope === 'mine' }" @click="state.scope = 'mine'; load()">我的</button>
          <button class="seg-btn" :class="{ active: state.scope === 'dept' }" @click="state.scope = 'dept'; load()">本部门</button>
          <button class="seg-btn" :class="{ active: state.scope === 'all' }" @click="state.scope = 'all'; load()">全部</button>
        </div>
        <div class="spacer" />
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <p v-if="errorMsg" class="err">{{ errorMsg }}</p>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th>订单号</th>
              <th>{{ isPo ? '供应商' : '客户' }}</th>
              <th>数量</th>
              <th>不含税</th>
              <th>税额</th>
              <th>价税合计</th>
              <th v-if="!isPo && !auth.hidden('total_profit')">毛利</th>
              <th>状态</th>
              <th class="nosort">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.order_no }}</td>
              <td>{{ r.party_name }}</td>
              <td>{{ Number(r.total_qty ?? 0).toFixed(3) }}</td>
              <td>{{ money(r.total_net) }}</td>
              <td>{{ money(r.total_tax) }}</td>
              <td><b>{{ money(r.total_amount) }}</b></td>
              <td v-if="!isPo && !auth.hidden('total_profit')" :style="Number(r.total_profit ?? 0) < 0 ? 'color: var(--danger)' : ''">
                {{ money(r.total_profit) }}
              </td>
              <td><span class="badge" :class="statusBadge(r.status).cls">{{ statusBadge(r.status).text }}</span></td>
              <td class="op">
                <a v-if="r.status === 'draft' || r.status === 'rejected'" @click="submitApproval(r)">提交审批</a>
                <a @click="printOrder(r)">打印</a>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon name="inbox" :size="46" />
          <div>暂无{{ title }}</div>
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
        <h3><Icon :name="isPo ? 'shopping-cart' : 'briefcase'" :size="18" /> 新建{{ title }}</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="grid">
          <div class="field">
            <label>{{ isPo ? '供应商' : '客户' }} *</label>
            <select v-model="form.partyId" class="input">
              <option v-for="p in parties" :key="p.id" :value="p.id">{{ p.name }}</option>
            </select>
          </div>
          <div class="field">
            <label>账期（天）</label>
            <input v-model.number="form.creditDays" type="number" class="input" />
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
        <p class="total">不含税 {{ totalNet.toFixed(2) }} ＋ 税额 {{ totalTax.toFixed(2) }} ＝ <b>{{ (totalNet + totalTax).toFixed(2) }}</b></p>
        <p v-if="!isPo" class="tip">销售订单会按当前加权成本冻结成本价，据此计算毛利</p>
        <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <Icon name="check" :size="15" /> 创建订单
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.lines-head { display: flex; justify-content: space-between; align-items: center; margin: 6px 0 8px; font-size: 13px; }
.lines { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.lines th { text-align: left; color: var(--text-3); font-weight: 600; padding-bottom: 4px; }
.lines td { padding: 3px 4px 3px 0; }
.input.sm { padding: 6px 8px; font-size: 12.5px; }
.total { text-align: right; font-size: 13px; color: var(--text-2); margin: 10px 0 0; }
.tip { font-size: 12px; color: var(--text-3); margin: 8px 0 0; }
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

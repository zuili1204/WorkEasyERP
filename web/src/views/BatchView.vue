<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'

const SIZE = 8
const state = reactive({ q: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const expiring = ref<Record<string, string | null>[]>([])

const products = ref<{ id: string; name: string; sku: string }[]>([])
const warehouses = ref<{ id: string; name: string }[]>([])

const showModal = ref(false)
const form = reactive({
  productId: '',
  warehouseId: '',
  qty: 10,
  costPrice: 0,
  batchNo: '',
  locationCode: '',
  expireDate: '',
})
const saving = ref(false)
const errorMsg = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.batches(state.q || undefined, state.page, SIZE)
    expiring.value = await api.expiringBatches()
  } finally {
    loading.value = false
  }
}

async function loadRefs() {
  const p = await api.baseList('product', undefined, 1, 50)
  products.value = p.list.map((x) => ({
    id: String(x.id), name: String(x.name ?? ''), sku: String(x.sku ?? ''),
  }))
  const w = await api.baseList('warehouse', undefined, 1, 50)
  warehouses.value = w.list.map((x) => ({ id: String(x.id), name: String(x.name ?? '') }))
  if (!form.productId && products.value.length) form.productId = products.value[0].id
  if (!form.warehouseId && warehouses.value.length) form.warehouseId = warehouses.value[0].id
}

function openModal() {
  errorMsg.value = ''
  form.batchNo = ''
  form.locationCode = ''
  form.expireDate = ''
  showModal.value = true
}

async function submit() {
  saving.value = true
  errorMsg.value = ''
  try {
    await api.createBatch({
      productId: form.productId,
      warehouseId: form.warehouseId,
      qty: Number(form.qty),
      costPrice: Number(form.costPrice),
      batchNo: form.batchNo || undefined,
      locationCode: form.locationCode || undefined,
      expireDate: form.expireDate || undefined,
    })
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

function daysLeft(v: string | null | undefined) {
  const d = Number(v)
  if (isNaN(d)) return '—'
  if (d < 0) return `已过期 ${-d} 天`
  return `${d} 天后到期`
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
  <div>
    <div v-if="expiring.length" class="alert-bar">
      <Icon name="alert-triangle" :size="16" />
      <span><b>{{ expiring.length }}</b> 个批次 30 天内到期或已过期：</span>
      <span v-for="(b, i) in expiring.slice(0, 3)" :key="i" class="chip">
        {{ b.batch_no }}（{{ daysLeft(b.days_left) }}）
      </span>
    </div>

    <div class="card">
      <div class="card-head">
        <h3><Icon name="box" :size="17" /> 批次 / 库位</h3>
        <button class="btn btn-primary btn-sm" @click="openModal">
          <Icon name="plus" :size="14" /> 登记批次
        </button>
      </div>

      <div class="card-body">
        <div class="toolbar">
          <div class="search-box">
            <Icon name="search" :size="15" />
            <input v-model="state.q" class="input" placeholder="搜索批次号 / 商品 / 库位" @input="load" />
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
                <th>批次号</th>
                <th>商品</th>
                <th>仓库</th>
                <th>库位</th>
                <th>数量</th>
                <th>成本</th>
                <th>入库日</th>
                <th>到期日</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in data.list" :key="i">
                <td>{{ r.batch_no }}</td>
                <td>{{ r.sku }} · {{ r.product_name }}</td>
                <td>{{ r.warehouse_name }}</td>
                <td>{{ r.location_code || '—' }}</td>
                <td>{{ r.qty }}</td>
                <td>{{ r.cost_price }}</td>
                <td>{{ r.inbound_date }}</td>
                <td>{{ r.expire_date || '—' }}</td>
              </tr>
            </tbody>
          </table>

          <div v-if="!loading && data.total === 0" class="empty">
            <Icon name="inbox" :size="46" />
            <div>暂无批次记录</div>
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
          <h3><Icon name="box" :size="18" /> 登记批次</h3>
          <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
        </div>
        <div class="modal-body">
          <div class="grid">
            <div class="field">
              <label>商品 *</label>
              <select v-model="form.productId" class="input">
                <option v-for="p in products" :key="p.id" :value="p.id">{{ p.sku }} · {{ p.name }}</option>
              </select>
            </div>
            <div class="field">
              <label>仓库 *</label>
              <select v-model="form.warehouseId" class="input">
                <option v-for="w in warehouses" :key="w.id" :value="w.id">{{ w.name }}</option>
              </select>
            </div>
            <div class="field">
              <label>数量 *</label>
              <input v-model.number="form.qty" type="number" min="1" class="input" />
            </div>
            <div class="field">
              <label>成本单价</label>
              <input v-model.number="form.costPrice" type="number" min="0" class="input" />
            </div>
            <div class="field">
              <label>批次号</label>
              <input v-model="form.batchNo" class="input" placeholder="留空自动生成" />
            </div>
            <div class="field">
              <label>库位编码</label>
              <input v-model="form.locationCode" class="input" placeholder="如 A-01-03" />
            </div>
            <div class="field">
              <label>到期日</label>
              <input v-model="form.expireDate" type="date" class="input" />
            </div>
          </div>
          <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
        </div>
        <div class="modal-foot">
          <button class="btn" @click="showModal = false">取消</button>
          <button class="btn btn-primary" :disabled="saving" @click="submit">
            <Icon name="check" :size="15" /> 保存
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.alert-bar {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  background: var(--warn-light); color: #b45309; border: 1px solid #fde68a;
  border-radius: var(--radius); padding: 11px 16px; margin-bottom: 14px; font-size: 13px;
}
.chip { background: #fff; border-radius: 6px; padding: 2px 8px; font-size: 12px; }
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin: 10px 0 0; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 60px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 620px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

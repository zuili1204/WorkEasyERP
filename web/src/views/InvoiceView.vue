<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import AttachmentPanel from '../components/AttachmentPanel.vue'

const SIZE = 8
const TABS = [
  ['sales', '销项发票'],
  ['purchase', '进项发票'],
  ['reconP', '采购对账'],
  ['reconS', '销售对账'],
]

const tab = ref('sales')
const state = reactive({ q: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const recon = ref<Record<string, string | null>[]>([])
const loading = ref(false)

const showModal = ref(false)
const showAtt = ref(false)
const attId = ref('')
const attNo = ref('')

function openAttachments(r: Record<string, string | null>) {
  attId.value = String(r.id)
  attNo.value = String(r.invoice_no ?? '')
  showAtt.value = true
}
const form = reactive({ type: 'sales', partyName: '', partyTaxNo: '', amount: 0, taxRate: 0.13 })
const saving = ref(false)
const errorMsg = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))
const isRecon = computed(() => tab.value === 'reconP' || tab.value === 'reconS')
const netPreview = computed(() => Number(form.amount || 0) / (1 + Number(form.taxRate || 0)))

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    if (tab.value === 'reconP') recon.value = await api.reconPurchase()
    else if (tab.value === 'reconS') recon.value = await api.reconSales()
    else data.value = await api.invoices(tab.value, state.q || undefined, state.page, SIZE)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '加载失败'
  } finally {
    loading.value = false
  }
}

function openModal() {
  errorMsg.value = ''
  form.type = tab.value === 'purchase' ? 'purchase' : 'sales'
  form.partyName = ''
  form.partyTaxNo = ''
  form.amount = 0
  showModal.value = true
}

async function submit() {
  if (!form.partyName.trim() || form.amount <= 0) return
  saving.value = true
  errorMsg.value = ''
  try {
    await api.createInvoice({
      type: form.type,
      partyName: form.partyName.trim(),
      partyTaxNo: form.partyTaxNo || undefined,
      amount: Number(form.amount),
      taxRate: Number(form.taxRate),
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

function money(v: string | null | undefined) {
  if (v == null) return '—'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    showModal.value = false
    showAtt.value = false
  }
}

watch(tab, () => {
  state.page = 1
  state.q = ''
  load()
})

onMounted(() => {
  load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="receipt" :size="17" /> 发票与对账</h3>
      <button v-if="!isRecon" class="btn btn-primary btn-sm" @click="openModal">
        <Icon name="plus" :size="14" /> 登记发票
      </button>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="seg">
          <button v-for="t in TABS" :key="t[0]" class="seg-btn" :class="{ active: tab === t[0] }" @click="tab = t[0]">
            {{ t[1] }}
          </button>
        </div>
        <div v-if="!isRecon" class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索票号 / 购方" @input="load" />
        </div>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <span class="count">共 {{ isRecon ? recon.length : data.total }} 条</span>
      </div>

      <p v-if="errorMsg" class="err">{{ errorMsg }}</p>

      <!-- 发票列表 -->
      <div v-if="!isRecon" class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th>票号</th>
              <th>类型</th>
              <th>购方</th>
              <th>不含税</th>
              <th>税额</th>
              <th>价税合计</th>
              <th>开票日期</th>
              <th>状态</th>
              <th class="nosort">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.invoice_no }}</td>
              <td>
                <span class="badge" :class="r.type === 'sales' ? 'badge-blue' : 'badge-orange'">
                  {{ r.type === 'sales' ? '销项' : '进项' }}
                </span>
              </td>
              <td>{{ r.buyer_name }}</td>
              <td>{{ money(r.net_amount) }}</td>
              <td>{{ money(r.tax_amount) }}</td>
              <td><b>{{ money(r.amount) }}</b></td>
              <td>{{ r.issued_at }}</td>
              <td>
                <span class="badge" :class="r.status === 'issued' ? 'badge-green' : 'badge-gray'">
                  {{ r.status === 'issued' ? '已开具' : r.status }}
                </span>
              </td>
              <td class="op"><a @click="openAttachments(r)">附件</a></td>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon name="inbox" :size="46" />
          <div>暂无发票记录</div>
        </div>

        <div class="pager">
          <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
          <span>第 {{ data.page }} / {{ totalPages }} 页</span>
          <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
        </div>
      </div>

      <!-- 对账 -->
      <div v-else class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th>{{ tab === 'reconP' ? '供应商' : '客户' }}</th>
              <th>订单额</th>
              <th>{{ tab === 'reconP' ? '入库额' : '出库额' }}</th>
              <th>{{ tab === 'reconP' ? '应付' : '应收' }}</th>
              <th>{{ tab === 'reconP' ? '已付' : '已收' }}</th>
              <th>余额</th>
              <th v-if="tab === 'reconS'">授信额度</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in recon" :key="i">
              <td>{{ tab === 'reconP' ? r.supplier_name : r.customer_name }}</td>
              <td>{{ money(r.order_amount) }}</td>
              <td>{{ money(tab === 'reconP' ? r.inbound_amount : r.outbound_amount) }}</td>
              <td>{{ money(tab === 'reconP' ? r.ap_amount : r.ar_amount) }}</td>
              <td>{{ money(tab === 'reconP' ? r.paid_amount : r.received_amount) }}</td>
              <td><b>{{ money(tab === 'reconP' ? r.ap_remain : r.ar_remain) }}</b></td>
              <td v-if="tab === 'reconS'">{{ money(r.credit_limit) }}</td>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && recon.length === 0" class="empty">
          <Icon name="inbox" :size="46" />
          <div>暂无对账数据</div>
        </div>
      </div>
    </div>
  </div>

  <!-- 发票扫描件 -->
  <div v-if="showAtt" class="mask" @click.self="showAtt = false">
    <div class="modal">
      <div class="modal-head">
        <h3><Icon name="paperclip" :size="18" /> 附件 · {{ attNo }}</h3>
        <span class="x" @click="showAtt = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <AttachmentPanel biz-type="invoice" :biz-id="attId" />
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showAtt = false">关闭</button>
      </div>
    </div>
  </div>

  <div v-if="showModal" class="mask" @click.self="showModal = false">
    <div class="modal">
      <div class="modal-head">
        <h3><Icon name="receipt" :size="18" /> 登记{{ form.type === 'sales' ? '销项' : '进项' }}发票</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="grid">
          <div class="field">
            <label>购方名称 *</label>
            <input v-model="form.partyName" class="input" />
          </div>
          <div class="field">
            <label>购方税号</label>
            <input v-model="form.partyTaxNo" class="input" />
          </div>
          <div class="field">
            <label>价税合计 *</label>
            <input v-model.number="form.amount" type="number" min="0" class="input" />
          </div>
          <div class="field">
            <label>税率</label>
            <input v-model.number="form.taxRate" type="number" step="0.01" class="input" />
          </div>
        </div>
        <p class="tip">不含税 {{ netPreview.toFixed(2) }}，税额 {{ (Number(form.amount || 0) - netPreview).toFixed(2) }}（由价税合计与税率反算）</p>
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
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.tip { font-size: 12px; color: var(--text-3); margin: 8px 0 0; }
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin: 10px 0 0; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 80px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 560px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

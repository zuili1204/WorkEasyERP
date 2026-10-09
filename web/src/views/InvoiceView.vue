<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'
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

const showAtt = ref(false)
// 登记发票已迁移至独立页 /new/invoice/sales｜purchase（见 utils/entityForms.ts）
const attId = ref('')
const attNo = ref('')

function openAttachments(r: Record<string, string | null>) {
  attId.value = String(r.id)
  attNo.value = String(r.invoice_no ?? '')
  showAtt.value = true
}
const errorMsg = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))
const isRecon = computed(() => tab.value === 'reconP' || tab.value === 'reconS')

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
  if (e.key === 'Escape') showAtt.value = false
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
      <router-link v-if="!isRecon" class="btn btn-primary btn-sm" :to="tab === 'purchase' ? '/new/invoice/purchase' : '/new/invoice/sales'">
        <Icon name="plus" :size="14" /> 登记发票
      </router-link>
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
      <TableShell
        v-if="!isRecon"
        :rows="data.list"
        :loading="loading"
        :page="data.page"
        :pages="totalPages"
        empty-title="暂无发票记录"
        @go="go"
      >
        <template #head>
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
        </template>

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
      </TableShell>

      <!-- 对账 -->
      <TableShell v-else :rows="recon" :loading="loading" empty-title="暂无对账数据">
        <template #head>
          <tr>
            <th>{{ tab === 'reconP' ? '供应商' : '客户' }}</th>
            <th>订单额</th>
            <th>{{ tab === 'reconP' ? '入库额' : '出库额' }}</th>
            <th>{{ tab === 'reconP' ? '应付' : '应收' }}</th>
            <th>{{ tab === 'reconP' ? '已付' : '已收' }}</th>
            <th>余额</th>
            <th v-if="tab === 'reconS'">授信额度</th>
          </tr>
        </template>

        <tr v-for="(r, i) in recon" :key="i">
          <td>{{ tab === 'reconP' ? r.supplier_name : r.customer_name }}</td>
          <td>{{ money(r.order_amount) }}</td>
          <td>{{ money(tab === 'reconP' ? r.inbound_amount : r.outbound_amount) }}</td>
          <td>{{ money(tab === 'reconP' ? r.ap_amount : r.ar_amount) }}</td>
          <td>{{ money(tab === 'reconP' ? r.paid_amount : r.received_amount) }}</td>
          <td><b>{{ money(tab === 'reconP' ? r.ap_remain : r.ar_remain) }}</b></td>
          <td v-if="tab === 'reconS'">{{ money(r.credit_limit) }}</td>
        </tr>
      </TableShell>
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

</template>

<style scoped>
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

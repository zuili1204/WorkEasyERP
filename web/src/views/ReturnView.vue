<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'
import { toast } from '../stores/toast'

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

const errorMsg = ref('')
// 新建退货单已迁移至独立页 /new/return/sales｜purchase（见 utils/entityForms.ts）

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

async function audit(row: Record<string, string | null>) {
  errorMsg.value = ''
  try {
    if (isSales.value) await api.auditSalesReturn(String(row.id))
    else await api.auditPurchaseReturn(String(row.id))
    await load()
    toast.success(isSales.value ? '退货已审核，已回库并红冲应收' : '退货已审核，已扣减库存并红冲应付')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    const msg = err.response?.data?.msg || '审核失败'
    errorMsg.value = msg
    toast.error(msg)
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

watch(kind, () => {
  state.page = 1
  state.q = ''
  load()
})

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="rotate-ccw" :size="17" /> 退货管理</h3>
      <router-link class="btn btn-primary btn-sm" :to="isSales ? '/new/return/sales' : '/new/return/purchase'">
        <Icon name="plus" :size="14" /> 新建{{ isSales ? '销售退货' : '采购退货' }}
      </router-link>
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

      <TableShell
        :rows="data.list"
        :loading="loading"
        :page="data.page"
        :pages="totalPages"
        empty-title="暂无退货单"
        @go="go"
      >
        <template #head>
          <tr>
            <th>单号</th>
            <th>{{ isSales ? '客户' : '供应商' }}</th>
            <th v-if="isSales">数量</th>
            <th>金额</th>
            <th>{{ isSales ? '已红冲应收' : '已红冲应付' }}</th>
            <th>状态</th>
            <th class="nosort">操作</th>
          </tr>
        </template>

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
      </TableShell>
    </div>
  </div>

</template>

<style scoped>
.nosort { cursor: default; }
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin: 10px 0 0; }
</style>

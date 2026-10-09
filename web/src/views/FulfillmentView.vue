<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'
import { toast } from '../stores/toast'

const SIZE = 8
const route = useRoute()
/** inbound=采购入库；outbound=销售出库 */
const type = computed(() => (route.path.includes('inbounds') ? 'inbound' : 'outbound'))
const isIn = computed(() => type.value === 'inbound')

const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)

const errorMsg = ref('')
// 新建出入库单已迁移至独立页 /new/fulfillment/inbound｜outbound（见 utils/entityForms.ts）

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

async function audit(row: Record<string, string | null>) {
  errorMsg.value = ''
  try {
    if (isIn.value) await api.auditInbound(String(row.id))
    else await api.auditOutbound(String(row.id))
    await load()
    toast.success(isIn.value ? '入库已审核，成本已更新并生成应付' : '出库已审核，库存已扣减并生成应收')
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
  if (s === 'draft') return { text: '草稿', cls: 'badge-orange' }
  if (s === 'void') return { text: '已作废', cls: 'badge-gray' }
  return { text: s || '—', cls: 'badge-blue' }
}

function money(v: string | null | undefined) {
  if (v == null) return '—'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

watch(type, () => {
  state.page = 1
  state.q = ''
  load()
})

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon :name="isIn ? 'package' : 'truck'" :size="17" /> {{ title }}</h3>
      <router-link class="btn btn-primary btn-sm" :to="isIn ? '/new/fulfillment/inbound' : '/new/fulfillment/outbound'">
        <Icon name="plus" :size="14" /> 新建{{ title }}单
      </router-link>
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

      <TableShell
        :rows="data.list"
        :loading="loading"
        :page="data.page"
        :pages="totalPages"
        :empty-title="`暂无${title}单`"
        @go="go"
      >
        <template #head>
          <tr>
            <th>单号</th>
            <th>{{ isIn ? '供应商' : '客户' }}</th>
            <th>仓库</th>
            <th>数量</th>
            <th>价税合计</th>
            <th>状态</th>
            <th class="nosort">操作</th>
          </tr>
        </template>

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
      </TableShell>
    </div>
  </div>

</template>

<style scoped>
.nosort { cursor: default; }
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin: 10px 0 0; }
</style>

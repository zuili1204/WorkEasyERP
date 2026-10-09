<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'
import { auth } from '../stores/auth'
import { toast } from '../stores/toast'

const SIZE = 8
const TABS = [
  ['stock', '库存现量'],
  ['txn', '出入库流水'],
]

const tab = ref('stock')
const state = reactive({ q: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)

// 手工入/出库已迁移至独立页 /new/inventory/in｜out（见 utils/entityForms.ts）

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

async function exportXlsx() {
  try {
    const blob = await api.exportFile('inventory')
    api.saveBlob(blob, `inventory-${new Date().toISOString().slice(0, 10)}.xlsx`)
    toast.success('库存 Excel 已导出')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    toast.error(err.response?.data?.msg || '导出失败')
  }
}

watch(tab, () => {
  state.page = 1
  state.q = ''
  load()
})

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="package" :size="17" /> 库存管理</h3>
      <div class="head-actions">
        <router-link class="btn btn-sm" to="/new/inventory/in"><Icon name="plus" :size="14" /> 入库</router-link>
        <router-link class="btn btn-sm btn-primary" to="/new/inventory/out">
          <Icon name="truck" :size="14" /> 出库
        </router-link>
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

      <TableShell
        :rows="data.list"
        :loading="loading"
        :page="data.page"
        :pages="totalPages"
        :empty-title="tab === 'stock' ? '暂无库存，请先做入库' : '暂无出入库流水'"
        @go="go"
      >
        <template #head>
          <!-- 库存现量 -->
          <tr v-if="tab === 'stock'">
            <th>SKU</th>
            <th>商品</th>
            <th>仓库</th>
            <th>现量</th>
            <th>可用</th>
            <th v-if="!auth.hidden('avg_cost')">加权成本</th>
            <th v-if="!auth.hidden('last_cost')">最近成本</th>
            <th v-if="!auth.hidden('stock_amount')">库存金额</th>
          </tr>
          <!-- 出入库流水 -->
          <tr v-else>
            <th>流水号</th>
            <th>类型</th>
            <th>商品</th>
            <th>数量</th>
            <th>单价</th>
            <th>金额</th>
            <th>结存</th>
            <th>时间</th>
          </tr>
        </template>

        <template v-if="tab === 'stock'">
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
        </template>
        <template v-else>
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
        </template>
      </TableShell>
    </div>
  </div>

</template>

<style scoped>
.head-actions { display: flex; gap: 8px; }

</style>

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

const errorMsg = ref('')
// 新建采购/销售订单已迁移至独立页 /new/order/purchase｜sales（见 utils/entityForms.ts）

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
      <h3><Icon :name="isPo ? 'shopping-cart' : 'briefcase'" :size="17" /> {{ title }}</h3>
      <router-link class="btn btn-primary btn-sm" :to="isPo ? '/new/order/purchase' : '/new/order/sales'">
        <Icon name="plus" :size="14" /> 新建{{ title }}
      </router-link>
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

</template>

<style scoped>
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin: 10px 0 0; }
</style>

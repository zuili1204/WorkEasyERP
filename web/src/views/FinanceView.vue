<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'

const SIZE = 8
const TABS = [
  ['ar', '应收台账'],
  ['ap', '应付台账'],
  ['pay', '收付款记录'],
]

const tab = ref('ar')
const state = reactive({ q: '', status: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)

const errorMsg = ref('')
// 登记收/付款已迁移至独立页 /new/finance/receive｜pay（见 utils/entityForms.ts）

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    if (tab.value === 'ar') data.value = await api.arList(state.q || undefined, state.status || undefined, state.page, SIZE)
    else if (tab.value === 'ap') data.value = await api.apList(state.q || undefined, state.status || undefined, state.page, SIZE)
    else data.value = await api.paymentList(state.page, SIZE)
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
  if (s === 'open') return { text: '未结', cls: 'badge-orange' }
  if (s === 'partial') return { text: '部分', cls: 'badge-blue' }
  if (s === 'closed') return { text: '已结', cls: 'badge-green' }
  return { text: s || '—', cls: 'badge-gray' }
}

function money(v: string | null | undefined) {
  if (v == null) return '—'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

/** 汇总当前页的余额，便于快速核对 */
const sumRemain = computed(() =>
  data.value.list.reduce((s, r) => s + Number(r.remain_amount ?? 0), 0),
)

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
      <h3><Icon name="wallet" :size="17" /> 应收应付</h3>
      <router-link v-if="tab !== 'pay'" class="btn btn-primary btn-sm" :to="tab === 'ar' ? '/new/finance/receive' : '/new/finance/pay'">
        <Icon name="check-circle" :size="14" /> {{ tab === 'ar' ? '登记收款' : '登记付款' }}
      </router-link>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="seg">
          <button v-for="t in TABS" :key="t[0]" class="seg-btn" :class="{ active: tab === t[0] }" @click="tab = t[0]">
            {{ t[1] }}
          </button>
        </div>
        <div v-if="tab !== 'pay'" class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索单号 / 往来单位" @input="load" />
        </div>
        <select v-if="tab !== 'pay'" v-model="state.status" class="input" style="width: 120px" @change="load">
          <option value="">全部状态</option>
          <option value="open">未结</option>
          <option value="partial">部分</option>
          <option value="closed">已结</option>
        </select>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <span v-if="tab !== 'pay'" class="count">本页余额 {{ money(String(sumRemain)) }}</span>
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <p v-if="errorMsg" class="err">{{ errorMsg }}</p>

      <div class="table-wrap">
        <!-- 台账 -->
        <table v-if="tab !== 'pay'" class="list">
          <thead>
            <tr>
              <th>单号</th>
              <th>往来单位</th>
              <th>发生额</th>
              <th>已核销</th>
              <th>余额</th>
              <th>到期日</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.biz_no }}</td>
              <td>{{ r.party_name }}</td>
              <td>{{ money(r.amount) }}</td>
              <td>{{ money(r.settled_amount) }}</td>
              <td><b>{{ money(r.remain_amount) }}</b></td>
              <td>{{ r.due_date || '—' }}</td>
              <td><span class="badge" :class="statusBadge(r.status).cls">{{ statusBadge(r.status).text }}</span></td>
            </tr>
          </tbody>
        </table>

        <!-- 收付款记录 -->
        <table v-else class="list">
          <thead>
            <tr>
              <th>单号</th>
              <th>类型</th>
              <th>往来单位</th>
              <th>金额</th>
              <th>方式</th>
              <th>日期</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.payment_no }}</td>
              <td>
                <span class="badge" :class="r.type === 'receive' ? 'badge-green' : 'badge-orange'">
                  {{ r.type === 'receive' ? '收款' : '付款' }}
                </span>
              </td>
              <td>{{ r.counterparty_name }}</td>
              <td>{{ money(r.amount) }}</td>
              <td>{{ r.pay_method }}</td>
              <td>{{ r.pay_date }}</td>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon name="inbox" :size="46" />
          <div>暂无记录</div>
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
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 80px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 520px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

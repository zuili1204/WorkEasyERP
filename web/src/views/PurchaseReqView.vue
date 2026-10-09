<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'
import { toast } from '../stores/toast'

const SIZE = 8
const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

const errorMsg = ref('')
// 起草采购申请已迁移至独立页 /new/purchase-request（见 utils/entityForms.ts）
const notice = ref('')

const STATUS: Record<string, { text: string; cls: string }> = {
  draft: { text: '草稿', cls: 'badge-gray' },
  approving: { text: '审批中', cls: 'badge-blue' },
  approved: { text: '已通过', cls: 'badge-green' },
  rejected: { text: '已驳回', cls: 'badge-red' },
  converted: { text: '已转订单', cls: 'badge-green' },
  void: { text: '已作废', cls: 'badge-gray' },
}

async function load() {
  loading.value = true
  try {
    data.value = await api.purchaseRequests(state.q || undefined, state.status || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

async function act(row: Record<string, string | null>, kind: 'submit' | 'order') {
  errorMsg.value = ''
  notice.value = ''
  try {
    if (kind === 'submit') {
      await api.submitPurchaseRequest(String(row.id))
      notice.value = '已提交审批（主管 → 财务）'
      toast.success('已提交审批（主管 → 财务）')
    } else {
      const r = await api.purchaseRequestToOrder(String(row.id))
      notice.value = `已生成采购订单 ${r.orderNo}（草稿，需再提交订单审批）`
      toast.success(`已转采购订单 ${r.orderNo}`)
    }
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    const msg = err.response?.data?.msg || '操作失败'
    errorMsg.value = msg
    toast.error(msg)
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="shopping-cart" :size="17" /> 采购申请</h3>
      <router-link class="btn btn-primary btn-sm" to="/new/purchase-request"><Icon name="plus" :size="14" /> 起草申请</router-link>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="seg">
          <button class="seg-btn" :class="{ active: state.scope === 'mine' }" @click="state.scope = 'mine'; state.page = 1; load()">我的</button>
          <button class="seg-btn" :class="{ active: state.scope === 'dept' }" @click="state.scope = 'dept'; state.page = 1; load()">本部门</button>
          <button class="seg-btn" :class="{ active: state.scope === 'all' }" @click="state.scope = 'all'; state.page = 1; load()">全部</button>
        </div>
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索单号 / 供应商 / 事由" @input="load" />
        </div>
        <select v-model="state.status" class="input" style="width: 120px" @change="state.page = 1; load()">
          <option value="">全部状态</option>
          <option value="draft">草稿</option>
          <option value="approving">审批中</option>
          <option value="approved">已通过</option>
          <option value="rejected">已驳回</option>
          <option value="converted">已转订单</option>
        </select>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
      <p v-if="notice" class="ok">{{ notice }}</p>

      <TableShell
        :rows="data.list"
        :loading="loading"
        :page="data.page"
        :pages="totalPages"
        empty-title="暂无采购申请"
        @go="go"
      >
        <template #head>
          <tr><th>单号</th><th>申请人</th><th>供应商</th><th>数量</th><th>期望日期</th><th>事由</th><th>状态</th><th class="nosort">操作</th></tr>
        </template>

        <tr v-for="(r, i) in data.list" :key="i">
          <td>{{ r.no }}</td>
          <td>{{ r.applicant_name }}</td>
          <td>{{ r.supplier_name || '—' }}</td>
          <td>{{ r.total_qty }}</td>
          <td>{{ r.expect_date || '—' }}</td>
          <td>{{ r.reason || '—' }}</td>
          <td><span class="badge" :class="(STATUS[r.status ?? ''] ?? { cls: 'badge-gray' }).cls">{{ (STATUS[r.status ?? ''] ?? { text: r.status }).text }}</span></td>
          <td class="op">
            <a v-if="r.status === 'draft' || r.status === 'rejected'" @click="act(r, 'submit')">提交审批</a>
            <a v-else-if="r.status === 'approved'" @click="act(r, 'order')">转采购订单</a>
            <span v-else style="color: var(--text-4)">—</span>
          </td>
        </tr>
      </TableShell>
    </div>
  </div>

</template>

<style scoped>
.nosort { cursor: default; }
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-top: 10px; }
.ok { background: var(--accent-light); color: #15803d; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
</style>

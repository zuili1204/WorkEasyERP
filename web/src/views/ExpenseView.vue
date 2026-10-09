<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import { auth } from '../stores/auth'
import { toast } from '../stores/toast'
import Icon from '../components/Icon.vue'

const SIZE = 8
const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

const errorMsg = ref('')

const CATS = [
  ['travel', '差旅'], ['office', '办公'], ['meal', '餐饮'], ['transport', '交通'],
  ['entertain', '招待'], ['train', '培训'], ['other', '其他'],
]
const STATUS: Record<string, { text: string; cls: string }> = {
  draft: { text: '草稿', cls: 'badge-gray' },
  approving: { text: '审批中', cls: 'badge-blue' },
  approved: { text: '已通过', cls: 'badge-green' },
  rejected: { text: '已驳回', cls: 'badge-red' },
  paid: { text: '已付款', cls: 'badge-green' },
  void: { text: '已作废', cls: 'badge-gray' },
}

async function load() {
  loading.value = true
  try {
    data.value = await api.expenses(state.q || undefined, state.status || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

// 新建报销已迁移至独立页 /new/expense（见 utils/entityForms.ts）

async function act(id: string, kind: 'submit' | 'pay') {
  errorMsg.value = ''
  try {
    if (kind === 'submit') await api.submitExpense(id)
    else await api.payExpense(id)
    await load()
    toast.success(kind === 'submit' ? '已提交审批（主管 → 财务）' : '已标记为已付款')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '操作失败'
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function money(v: string | null) {
  const n = Number(v ?? 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(async () => {
  state.scope = auth.user?.roles?.includes('boss') ? 'all' : 'dept'
  await load()
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="receipt" :size="17" /> 报销</h3>
      <router-link class="btn btn-primary btn-sm" to="/new/expense"><Icon name="plus" :size="14" /> 发起报销</router-link>
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
          <input v-model="state.q" class="input" placeholder="搜索单号 / 申请人 / 事由" @input="load" />
        </div>
        <select v-model="state.status" class="input" style="width: 120px" @change="state.page = 1; load()">
          <option value="">全部状态</option>
          <option value="draft">草稿</option>
          <option value="approving">审批中</option>
          <option value="approved">已通过</option>
          <option value="rejected">已驳回</option>
          <option value="paid">已付款</option>
        </select>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <p v-if="errorMsg" class="err">{{ errorMsg }}</p>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th>单号</th><th>申请人</th><th>部门</th><th>类别</th>
              <th>金额</th><th>发生日期</th><th>事由</th><th>状态</th><th class="nosort">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.no }}</td>
              <td>{{ r.employee_name }}</td>
              <td>{{ r.dept_name }}</td>
              <td>{{ CATS.find((c) => c[0] === r.category)?.[1] ?? r.category }}</td>
              <td><b>{{ money(r.amount) }}</b></td>
              <td>{{ r.happen_date }}</td>
              <td>{{ r.reason || '—' }}</td>
              <td><span class="badge" :class="(STATUS[r.status ?? ''] ?? { cls: 'badge-gray' }).cls">{{ (STATUS[r.status ?? ''] ?? { text: r.status }).text }}</span></td>
              <td class="op">
                <a v-if="r.status === 'draft' || r.status === 'rejected'" @click="act(String(r.id), 'submit')">提交审批</a>
                <a v-else-if="r.status === 'approved'" @click="act(String(r.id), 'pay')">标记付款</a>
                <span v-else style="color: var(--text-4)">—</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!loading && data.total === 0" class="empty">
          <Icon name="inbox" :size="46" /><div>暂无报销记录</div>
        </div>
      </div>

      <div class="pager">
        <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
        <span>第 {{ data.page }} / {{ totalPages }} 页</span>
        <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
      </div>
      <p class="tip">流程：发起 → 主管审批 → 财务审批 → 标记付款</p>
    </div>
  </div>

</template>

<style scoped>
.nosort { cursor: default; }
.tip { font-size: 12px; color: var(--text-3); margin: 10px 0 0; }
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; }
</style>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import PromptDialog from '../components/PromptDialog.vue'
import TableShell from '../components/TableShell.vue'
import { toast } from '../stores/toast'

const SIZE = 8
const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

const errorMsg = ref('')
const notice = ref('')
const showLostDialog = ref(false)
const lostRow = ref<Record<string, string | null> | null>(null)

const STAGES = [
  ['contact', '初步接触'], ['quote', '报价'], ['solution', '方案'],
  ['negotiation', '商务谈判'], ['won', '赢单'], ['lost', '输单'],
]
const STATUS: Record<string, { text: string; cls: string }> = {
  open: { text: '进行中', cls: 'badge-blue' },
  won: { text: '赢单', cls: 'badge-green' },
  lost: { text: '输单', cls: 'badge-red' },
}

async function load() {
  loading.value = true
  try {
    data.value = await api.opportunities(state.q || undefined, state.status || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

// 新增商机已迁移至独立页 /new/opportunity（见 utils/entityForms.ts）

async function advance(row: Record<string, string | null>, stage: string) {
  errorMsg.value = ''
  notice.value = ''
  if (stage === 'lost') {
    lostRow.value = row
    showLostDialog.value = true
    return
  }
  await updateStage(row, stage, undefined)
}

async function updateStage(row: Record<string, string | null>, stage: string, reason: string | undefined) {
  try {
    await api.updateOppStage(String(row.id), stage, reason)
    notice.value = stage === 'won' ? '已标记赢单，可转为合同' : `阶段已更新为「${STAGES.find((s) => s[0] === stage)?.[1]}」`
    await load()
    toast.success(stage === 'won' ? '已标记赢单' : '阶段已更新')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    const msg = err.response?.data?.msg || '操作失败'
    errorMsg.value = msg
    toast.error(msg)
  }
}

function onLostConfirm(reason: string) {
  if (lostRow.value) void updateStage(lostRow.value, 'lost', reason)
}

async function toContract(row: Record<string, string | null>) {
  errorMsg.value = ''
  try {
    const r = await api.convertOpportunity(String(row.id))
    notice.value = `已生成合同 ${r.contractNo}`
    await load()
    toast.success(`已生成合同 ${r.contractNo}`)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    const msg = err.response?.data?.msg || '转化失败'
    errorMsg.value = msg
    toast.error(msg)
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function money(v: string | null) {
  return Number(v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="trending-up" :size="17" /> 商机管理</h3>
      <router-link class="btn btn-primary btn-sm" to="/new/opportunity"><Icon name="plus" :size="14" /> 新增商机</router-link>
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
          <input v-model="state.q" class="input" placeholder="搜索商机" @input="load" />
        </div>
        <select v-model="state.status" class="input" style="width: 120px" @change="state.page = 1; load()">
          <option value="">全部状态</option>
          <option value="open">进行中</option>
          <option value="won">赢单</option>
          <option value="lost">输单</option>
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
        empty-icon="target"
        empty-title="暂无商机"
        empty-desc="点击右上角「新增商机」开始记录客户跟进"
        @go="go"
      >
        <template #head>
          <tr><th>编号</th><th>商机</th><th>客户</th><th>阶段</th><th>预计金额</th><th>预计成交</th><th>状态</th><th class="nosort">操作</th></tr>
        </template>

        <tr v-for="(r, i) in data.list" :key="i">
          <td>{{ r.no }}</td>
          <td>{{ r.name }}</td>
          <td>{{ r.customer_name || '—' }}</td>
          <td>{{ STAGES.find((s) => s[0] === r.stage)?.[1] ?? r.stage }}</td>
          <td><b>{{ money(r.amount) }}</b></td>
          <td>{{ r.expect_close_date || '—' }}</td>
          <td><span class="badge" :class="(STATUS[r.status ?? ''] ?? { cls: 'badge-gray' }).cls">{{ (STATUS[r.status ?? ''] ?? { text: r.status }).text }}</span></td>
          <td class="op">
            <template v-if="r.status === 'open'">
              <a @click="advance(r, 'negotiation')">推进谈判</a>
              <a @click="advance(r, 'won')">赢单</a>
              <a style="color: var(--danger)" @click="advance(r, 'lost')">输单</a>
            </template>
            <a v-else-if="r.status === 'won'" @click="toContract(r)">转合同</a>
            <span v-else style="color: var(--text-4)">—</span>
          </td>
        </tr>
      </TableShell>
    </div>
  </div>

  <PromptDialog
    :open="showLostDialog"
    title="标记输单"
    label="请填写输单原因"
    placeholder="如：价格偏高 / 竞争对手中标 / 客户预算取消…"
    required
    confirm-text="确认输单"
    @update:open="showLostDialog = $event"
    @confirm="onLostConfirm"
  />
</template>

<style scoped>
.nosort { cursor: default; }
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
.ok { background: var(--accent-light); color: #15803d; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
</style>

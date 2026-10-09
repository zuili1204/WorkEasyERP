<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'
import AttachmentPanel from '../components/AttachmentPanel.vue'

const SIZE = 8
const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

const showAtt = ref(false)
const attId = ref('')
const attNo = ref('')
const errorMsg = ref('')
// 登记合同已迁移至独立页 /new/contract（见 utils/entityForms.ts）

const STATUS: Record<string, { text: string; cls: string }> = {
  draft: { text: '草稿', cls: 'badge-gray' },
  effective: { text: '生效中', cls: 'badge-green' },
  expiring: { text: '即将到期', cls: 'badge-orange' },
  expired: { text: '已到期', cls: 'badge-gray' },
  terminated: { text: '已终止', cls: 'badge-red' },
}

async function load() {
  loading.value = true
  try {
    data.value = await api.contracts(state.q || undefined, state.status || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

function openAttachments(r: Record<string, string | null>) {
  attId.value = String(r.id)
  attNo.value = String(r.no ?? '')
  showAtt.value = true
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function money(v: string | null) {
  return Number(v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function daysLeft(v: string | null) {
  if (!v) return '—'
  const d = Math.ceil((new Date(v).getTime() - Date.now()) / 86400000)
  return d < 0 ? `已过期 ${-d} 天` : `${d} 天`
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showAtt.value = false
}

onMounted(async () => {
  await load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div>
    <div class="card">
      <div class="card-head">
        <h3><Icon name="file-text" :size="17" /> 合同管理</h3>
        <router-link class="btn btn-primary btn-sm" to="/new/contract"><Icon name="plus" :size="14" /> 登记合同</router-link>
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
            <input v-model="state.q" class="input" placeholder="搜索合同" @input="load" />
          </div>
          <select v-model="state.status" class="input" style="width: 120px" @change="state.page = 1; load()">
            <option value="">全部状态</option>
            <option value="effective">生效中</option>
            <option value="expiring">即将到期</option>
            <option value="expired">已到期</option>
            <option value="terminated">已终止</option>
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
          empty-icon="file-text"
          empty-title="暂无合同"
          empty-desc="点击右上角「登记合同」，或由商机赢单后自动转化生成"
          @go="go"
        >
          <template #head>
            <tr><th>合同号</th><th>名称</th><th>客户</th><th>金额</th><th>签订日</th><th>到期日</th><th>剩余</th><th>状态</th><th class="nosort">操作</th></tr>
          </template>

          <tr v-for="(r, i) in data.list" :key="i">
            <td>{{ r.no }}</td>
            <td>{{ r.name }}</td>
            <td>{{ r.customer_name || '—' }}</td>
            <td><b>{{ money(r.amount) }}</b></td>
            <td>{{ r.sign_date }}</td>
            <td>{{ r.end_date || '—' }}</td>
            <td>{{ daysLeft(r.end_date) }}</td>
            <td><span class="badge" :class="(STATUS[r.status ?? ''] ?? { cls: 'badge-gray' }).cls">{{ (STATUS[r.status ?? ''] ?? { text: r.status }).text }}</span></td>
            <td class="op"><a @click="openAttachments(r)">附件</a></td>
          </tr>
        </TableShell>
      </div>
    </div>

    <div v-if="showAtt" class="mask" @click.self="showAtt = false">
      <div class="modal">
        <div class="modal-head">
          <h3><Icon name="paperclip" :size="18" /> 合同附件 · {{ attNo }}</h3>
          <span class="x" @click="showAtt = false"><Icon name="x" :size="20" /></span>
        </div>
        <div class="modal-body">
          <AttachmentPanel biz-type="contract" :biz-id="attId" />
        </div>
        <div class="modal-foot">
          <button class="btn" @click="showAtt = false">关闭</button>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.nosort { cursor: default; }
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; }
.mask { position: fixed; inset: 0; background: rgba(15,23,42,.45); backdrop-filter: blur(3px); display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 60px 20px; }
.modal { background: #fff; border-radius: var(--radius-lg); width: 600px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 20px 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import EmptyState from '../components/EmptyState.vue'
import { toast } from '../stores/toast'

const SIZE = 8
const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

const errorMsg = ref('')
const notice = ref('')

const SRC: Record<string, string> = {
  web: '官网', referral: '转介绍', exhibition: '展会', call: '电话', other: '其他',
}
const STATUS: Record<string, { text: string; cls: string }> = {
  following: { text: '跟进中', cls: 'badge-orange' },
  converted: { text: '已转化', cls: 'badge-green' },
  dropped: { text: '已放弃', cls: 'badge-gray' },
}

async function load() {
  loading.value = true
  try {
    data.value = await api.leads(state.q || undefined, state.status || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

// 新建线索已迁移至独立页 /new/lead（见 utils/entityForms.ts）

/** 线索转客户：生成客户档案并回写线索状态 */
async function convert(row: Record<string, string | null>) {
  errorMsg.value = ''
  notice.value = ''
  try {
    const r = await api.convertLead(String(row.id), 'C', 0, 0)
    notice.value = `已转为客户 ${r.customerCode}，可在客户管理中完善授信与账期`
    await load()
    toast.success(`已转为客户 ${r.customerCode}`)
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

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="target" :size="17" /> 线索管理</h3>
      <router-link class="btn btn-primary btn-sm" to="/new/lead"><Icon name="plus" :size="14" /> 新增线索</router-link>
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
          <input v-model="state.q" class="input" placeholder="搜索线索" @input="load" />
        </div>
        <select v-model="state.status" class="input" style="width: 120px" @change="state.page = 1; load()">
          <option value="">全部状态</option>
          <option value="following">跟进中</option>
          <option value="converted">已转化</option>
          <option value="dropped">已放弃</option>
        </select>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
      <p v-if="notice" class="ok">{{ notice }}</p>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr><th>编号</th><th>线索名称</th><th>来源</th><th>联系人</th><th>电话</th><th>状态</th><th class="nosort">操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.no }}</td>
              <td>{{ r.name }}</td>
              <td>{{ SRC[r.source ?? ''] ?? r.source }}</td>
              <td>{{ r.contact_name || '—' }}</td>
              <td>{{ r.contact_phone || '—' }}</td>
              <td><span class="badge" :class="(STATUS[r.status ?? ''] ?? { cls: 'badge-gray' }).cls">{{ (STATUS[r.status ?? ''] ?? { text: r.status }).text }}</span></td>
              <td class="op">
                <a v-if="r.status === 'following'" @click="convert(r)">转客户</a>
                <span v-else style="color: var(--text-4)">—</span>
              </td>
            </tr>
          </tbody>
        </table>
        <EmptyState
          v-if="!loading && data.total === 0"
          icon="target"
          title="暂无线索"
          desc="点击右上角「新增线索」登记，转化后自动生成客户档案"
        />
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
.nosort { cursor: default; }
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
.ok { background: var(--accent-light); color: #15803d; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
</style>

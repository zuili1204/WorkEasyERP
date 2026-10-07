<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'

const SIZE = 8
const state = reactive({ q: '', status: '', scope: 'all', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

const showModal = ref(false)
const form = reactive({ name: '', source: 'web', contactName: '', contactPhone: '' })
const saving = ref(false)
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

async function submit() {
  saving.value = true
  errorMsg.value = ''
  try {
    await api.createLead({
      name: form.name.trim(),
      source: form.source,
      contactName: form.contactName || undefined,
      contactPhone: form.contactPhone || undefined,
    })
    showModal.value = false
    form.name = ''
    form.contactName = ''
    form.contactPhone = ''
    state.page = 1
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '保存失败'
  } finally {
    saving.value = false
  }
}

/** 线索转客户：生成客户档案并回写线索状态 */
async function convert(row: Record<string, string | null>) {
  errorMsg.value = ''
  notice.value = ''
  try {
    const r = await api.convertLead(String(row.id), 'C', 0, 0)
    notice.value = `已转为客户 ${r.customerCode}，可在客户管理中完善授信与账期`
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '转化失败'
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

onMounted(async () => {
  await load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="target" :size="17" /> 线索管理</h3>
      <button class="btn btn-primary btn-sm" @click="showModal = true"><Icon name="plus" :size="14" /> 新增线索</button>
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
        <div v-if="!loading && data.total === 0" class="empty"><Icon name="inbox" :size="46" /><div>暂无线索</div></div>
      </div>

      <div class="pager">
        <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
        <span>第 {{ data.page }} / {{ totalPages }} 页</span>
        <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
      </div>
    </div>
  </div>

  <div v-if="showModal" class="mask" @click.self="showModal = false">
    <div class="modal">
      <div class="modal-head">
        <h3><Icon name="target" :size="18" /> 新增线索</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="field">
          <label>线索名称 *</label>
          <input v-model="form.name" class="input" placeholder="公司或联系人名称" />
        </div>
        <div class="grid">
          <div class="field">
            <label>来源</label>
            <select v-model="form.source" class="input">
              <option value="web">官网</option><option value="referral">转介绍</option>
              <option value="exhibition">展会</option><option value="call">电话</option><option value="other">其他</option>
            </select>
          </div>
          <div class="field">
            <label>联系人</label>
            <input v-model="form.contactName" class="input" />
          </div>
          <div class="field">
            <label>联系电话</label>
            <input v-model="form.contactPhone" class="input" />
          </div>
        </div>
        <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">保存</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.field { margin-bottom: 12px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 5px; font-weight: 600; }
.nosort { cursor: default; }
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
.ok { background: var(--accent-light); color: #15803d; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
.mask { position: fixed; inset: 0; background: rgba(15,23,42,.45); backdrop-filter: blur(3px); display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 60px 20px; }
.modal { background: #fff; border-radius: var(--radius-lg); width: 600px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 20px 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

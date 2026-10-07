<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'

const SIZE = 8

const state = reactive({ q: '', scope: 'mine', level: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const showModal = ref(false)
const form = reactive({
  name: '',
  level: 'B',
  contactName: '',
  contactPhone: '',
  address: '',
  creditLimit: 0,
  paymentTerms: 30,
  remark: '',
})
const saving = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.customers(
      state.q || undefined,
      state.scope,
      state.level || undefined,
      state.page,
      SIZE,
    )
  } finally {
    loading.value = false
  }
}

let timer: number | undefined
function onSearch() {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => {
    state.page = 1
    load()
  }, 250)
}

function setScope(s: string) {
  state.scope = s
  state.page = 1
  load()
}

async function submit() {
  if (!form.name.trim()) return
  saving.value = true
  try {
    await api.createCustomer({
      name: form.name.trim(),
      level: form.level,
      contactName: form.contactName || undefined,
      contactPhone: form.contactPhone || undefined,
      address: form.address || undefined,
      creditLimit: Number(form.creditLimit),
      paymentTerms: Number(form.paymentTerms),
      remark: form.remark || undefined,
    })
    showModal.value = false
    form.name = ''
    form.contactName = ''
    form.contactPhone = ''
    form.address = ''
    form.remark = ''
    state.page = 1
    load()
  } finally {
    saving.value = false
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function money(v: string | null | undefined) {
  if (v == null) return '—'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

async function exportXlsx() {
  const blob = await api.exportFile('customers')
  api.saveBlob(blob, `customers-${new Date().toISOString().slice(0, 10)}.xlsx`)
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="building" :size="17" /> 客户管理</h3>
      <button class="btn btn-primary btn-sm" @click="showModal = true">
        <Icon name="plus" :size="14" /> 新增客户
      </button>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索名称 / 编号 / 联系人" @input="onSearch" />
        </div>
        <select v-model="state.level" class="input" style="width: 110px" @change="load">
          <option value="">全部等级</option>
          <option value="A">A 级</option>
          <option value="B">B 级</option>
          <option value="C">C 级</option>
        </select>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <button class="btn btn-sm" @click="exportXlsx"><Icon name="download" :size="15" /> 导出 Excel</button>
        <div class="spacer" />
        <div class="seg">
          <button class="seg-btn" :class="{ active: state.scope === 'mine' }" @click="setScope('mine')">我的</button>
          <button class="seg-btn" :class="{ active: state.scope === 'dept' }" @click="setScope('dept')">本部门</button>
          <button class="seg-btn" :class="{ active: state.scope === 'all' }" @click="setScope('all')">全部</button>
        </div>
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th>编号</th>
              <th>客户名称</th>
              <th>等级</th>
              <th>联系人</th>
              <th>电话</th>
              <th>授信额度</th>
              <th>已用</th>
              <th>账期</th>
              <th>归属销售</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <td>{{ r.code }}</td>
              <td>{{ r.name }}</td>
              <td>
                <span
                  class="badge"
                  :class="r.level === 'A' ? 'badge-green' : r.level === 'B' ? 'badge-blue' : 'badge-gray'"
                >{{ r.level || '—' }}</span>
              </td>
              <td>{{ r.contact_name || '—' }}</td>
              <td>{{ r.contact_phone || '—' }}</td>
              <td>{{ money(r.credit_limit) }}</td>
              <td :style="Number(r.credit_used ?? 0) > Number(r.credit_limit ?? 0) ? 'color: var(--danger)' : ''">
                {{ money(r.credit_used) }}
              </td>
              <td>{{ r.payment_terms }} 天</td>
              <td>{{ r.owner_name || '—' }}</td>
              <td>
                <span class="badge" :class="r.status === 'active' ? 'badge-green' : 'badge-gray'">
                  {{ r.status === 'active' ? '正常' : r.status }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon :name="state.q ? 'search' : 'inbox'" :size="46" />
          <div>
            {{ state.q ? `未找到与「${state.q}」匹配的客户` : '当前数据范围内暂无客户' }}
          </div>
        </div>
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
        <h3><Icon name="building" :size="18" /> 新增客户</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="grid">
          <div class="field">
            <label>客户名称 *</label>
            <input v-model="form.name" class="input" placeholder="如：宏达贸易" />
          </div>
          <div class="field">
            <label>等级</label>
            <select v-model="form.level" class="input">
              <option value="A">A</option>
              <option value="B">B</option>
              <option value="C">C</option>
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
          <div class="field">
            <label>授信额度</label>
            <input v-model.number="form.creditLimit" type="number" class="input" />
          </div>
          <div class="field">
            <label>账期（天）</label>
            <input v-model.number="form.paymentTerms" type="number" class="input" />
          </div>
        </div>
        <div class="field">
          <label>地址</label>
          <input v-model="form.address" class="input" />
        </div>
        <div class="field">
          <label>备注</label>
          <textarea v-model="form.remark" rows="2" class="input"></textarea>
        </div>
        <p class="tip">归属销售默认为当前登录员工，部门随之自动带入</p>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <Icon name="check" :size="15" /> 保存
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.field textarea { resize: vertical; }
.tip { font-size: 12px; color: var(--text-3); margin: 0; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 80px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 560px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

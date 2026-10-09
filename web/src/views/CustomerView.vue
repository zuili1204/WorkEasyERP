<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'
import { toast } from '../stores/toast'

const SIZE = 8

const state = reactive({ q: '', scope: 'mine', level: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
// 新增客户已迁移至独立页 /new/customer（见 utils/entityForms.ts）

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

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function money(v: string | null | undefined) {
  if (v == null) return '—'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

async function exportXlsx() {
  try {
    const blob = await api.exportFile('customers')
    api.saveBlob(blob, `customers-${new Date().toISOString().slice(0, 10)}.xlsx`)
    toast.success('客户 Excel 已导出')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    toast.error(err.response?.data?.msg || '导出失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="building" :size="17" /> 客户管理</h3>
      <router-link class="btn btn-primary btn-sm" to="/new/customer">
        <Icon name="plus" :size="14" /> 新增客户
      </router-link>
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

      <TableShell
        :rows="data.list"
        :loading="loading"
        :page="data.page"
        :pages="totalPages"
        :empty-icon="state.q ? 'search' : 'building'"
        :empty-title="state.q ? '未找到匹配的客户' : '暂无客户'"
        :empty-desc="state.q ? `没有与「${state.q}」匹配的客户，换个关键词试试` : '当前数据范围内暂无客户，点击右上角「新增客户」开始录入'"
        @go="go"
      >
        <template #head>
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
        </template>

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
      </TableShell>
    </div>
  </div>

</template>

<style scoped>

</style>

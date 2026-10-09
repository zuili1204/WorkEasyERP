<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'
import { auth } from '../stores/auth'
import { toast } from '../stores/toast'

const SIZE = 8

const state = reactive({ period: '', scope: 'mine', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

// 生成薪资已迁移至独立页 /new/payroll（见 utils/entityForms.ts）

async function load() {
  loading.value = true
  try {
    data.value = await api.payrollList(state.period || undefined, state.scope, state.page, SIZE)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    toast.error(err.response?.data?.msg || '薪资列表加载失败')
  } finally {
    loading.value = false
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function money(v: string | null | undefined) {
  if (v == null) return '—'
  const n = Number(v)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="wallet" :size="17" /> 薪资核算</h3>
      <router-link class="btn btn-primary btn-sm" to="/new/payroll">
        <Icon name="plus" :size="14" /> 生成薪资
      </router-link>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <input v-model="state.period" class="input" style="width: 140px" placeholder="期间 2026-10" @input="load" />
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <div class="seg">
          <button class="seg-btn" :class="{ active: state.scope === 'mine' }" @click="state.scope = 'mine'; load()">我的</button>
          <button class="seg-btn" :class="{ active: state.scope === 'dept' }" @click="state.scope = 'dept'; load()">本部门</button>
          <button class="seg-btn" :class="{ active: state.scope === 'all' }" @click="state.scope = 'all'; load()">全部</button>
        </div>
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <TableShell
        :rows="data.list"
        :loading="loading"
        :page="data.page"
        :pages="totalPages"
        empty-title="暂无薪资记录"
        @go="go"
      >
        <template #head>
          <tr>
            <th>期间</th>
            <th>员工</th>
            <th v-if="!auth.hidden('net_pay')">基本工资</th>
            <th v-if="!auth.hidden('net_pay')">奖金</th>
            <th v-if="!auth.hidden('net_pay')">津贴</th>
            <th v-if="!auth.hidden('net_pay')">扣款</th>
            <th v-if="!auth.hidden('net_pay')">社保</th>
            <th v-if="!auth.hidden('net_pay')">个税</th>
            <th v-if="!auth.hidden('net_pay')">实发</th>
            <th>状态</th>
          </tr>
        </template>

        <tr v-for="(r, i) in data.list" :key="i">
          <td>{{ r.period }}</td>
          <td>{{ r.employee_name }}</td>
          <template v-if="!auth.hidden('net_pay')">
            <td>{{ money(r.base_salary) }}</td>
            <td>{{ money(r.bonus) }}</td>
            <td>{{ money(r.allowance) }}</td>
            <td>{{ money(r.deduction) }}</td>
            <td>{{ money(r.social_security) }}</td>
            <td>{{ money(r.tax) }}</td>
            <td><b>{{ money(r.net_pay) }}</b></td>
          </template>
          <td>
            <span class="badge" :class="r.status === 'paid' ? 'badge-green' : 'badge-gray'">
              {{ r.status === 'paid' ? '已发放' : '草稿' }}
            </span>
          </td>
        </tr>
      </TableShell>
    </div>
  </div>

</template>

<style scoped>

</style>

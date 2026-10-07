<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import { auth } from '../stores/auth'

const SIZE = 8

const state = reactive({ period: '', scope: 'mine', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const showModal = ref(false)
const form = reactive({
  period: new Date().toISOString().slice(0, 7),
  baseSalary: 8000,
  bonus: 0,
  allowance: 500,
  deduction: 0,
  socialSecurity: 800,
  tax: 300,
})

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

// 实发由服务端计算；此处仅用于界面预览
const previewNet = computed(
  () =>
    Number(form.baseSalary || 0) + Number(form.bonus || 0) + Number(form.allowance || 0) -
    Number(form.deduction || 0) - Number(form.socialSecurity || 0) - Number(form.tax || 0),
)

async function load() {
  loading.value = true
  try {
    data.value = await api.payrollList(state.period || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

async function generate() {
  await api.payrollGenerate({
    period: form.period,
    baseSalary: Number(form.baseSalary),
    bonus: Number(form.bonus),
    allowance: Number(form.allowance),
    deduction: Number(form.deduction),
    socialSecurity: Number(form.socialSecurity),
    tax: Number(form.tax),
  })
  showModal.value = false
  load()
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
      <button class="btn btn-primary btn-sm" @click="showModal = true">
        <Icon name="plus" :size="14" /> 生成薪资
      </button>
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

      <div class="table-wrap">
        <table class="list">
          <thead>
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
          </thead>
          <tbody>
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
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon name="inbox" :size="46" />
          <div>暂无薪资记录</div>
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
        <h3><Icon name="wallet" :size="18" /> 生成薪资</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="grid">
          <div class="field">
            <label>期间</label>
            <input v-model="form.period" class="input" placeholder="2026-10" />
          </div>
          <div class="field">
            <label>基本工资</label>
            <input v-model.number="form.baseSalary" type="number" class="input" />
          </div>
          <div class="field">
            <label>奖金</label>
            <input v-model.number="form.bonus" type="number" class="input" />
          </div>
          <div class="field">
            <label>津贴</label>
            <input v-model.number="form.allowance" type="number" class="input" />
          </div>
          <div class="field">
            <label>扣款</label>
            <input v-model.number="form.deduction" type="number" class="input" />
          </div>
          <div class="field">
            <label>社保</label>
            <input v-model.number="form.socialSecurity" type="number" class="input" />
          </div>
          <div class="field">
            <label>个税</label>
            <input v-model.number="form.tax" type="number" class="input" />
          </div>
        </div>
        <p class="tip">实发预览：<b>{{ previewNet.toFixed(2) }}</b>（以服务端计算为准）</p>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" @click="generate"><Icon name="check" :size="15" /> 生成</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.field { margin-bottom: 12px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.tip { font-size: 12.5px; color: var(--text-3); margin: 6px 0 0; }
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

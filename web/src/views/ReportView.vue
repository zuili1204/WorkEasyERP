<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type ReportResult } from '../api'
import Icon from '../components/Icon.vue'

const list = ref<Record<string, string | null>[]>([])
const modules = ref<string[]>([])
const result = ref<ReportResult | null>(null)
const loading = ref(false)

const showModal = ref(false)
const form = ref({ name: '', module: 'sales', frequency: 'none' })
const saving = ref(false)
const errorMsg = ref('')

const MODULE_TEXT: Record<string, string> = {
  sales: '销售', purchase: '采购', inventory: '库存', finance: '财务', hr: '人事',
}

const total = ref(0)
const page = ref(1)
const SIZE = 8
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / SIZE)))

async function load() {
  loading.value = true
  try {
    const r = await api.reports(undefined, page.value, SIZE)
    list.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}

async function run(id: string) {
  errorMsg.value = ''
  try {
    result.value = await api.runReport(id)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '运行失败'
  }
}

async function preview(module: string) {
  errorMsg.value = ''
  try {
    result.value = await api.previewReport(module)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '预览失败'
  }
}

async function remove(id: string) {
  try {
    await api.deleteReport(id)
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '删除失败'
  }
}

async function submit() {
  saving.value = true
  errorMsg.value = ''
  try {
    await api.createReport({ name: form.value.name.trim(), module: form.value.module, frequency: form.value.frequency })
    showModal.value = false
    form.value.name = ''
    page.value = 1
    await load()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '保存失败'
  } finally {
    saving.value = false
  }
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

onMounted(async () => {
  modules.value = await api.reportModules()
  await load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="grid2">
    <div class="card">
      <div class="card-head">
        <h3><Icon name="bar-chart" :size="17" /> 报表定义</h3>
        <button class="btn btn-primary btn-sm" @click="showModal = true"><Icon name="plus" :size="14" /> 新建报表</button>
      </div>
      <div class="card-body">
        <p v-if="errorMsg" class="err">{{ errorMsg }}</p>

        <div class="rows">
          <div v-for="r in list" :key="String(r.id)" class="row">
            <Icon name="file-text" :size="16" />
            <div class="meta">
              <div class="nm">{{ r.name }}</div>
              <div class="sub">
                {{ MODULE_TEXT[r.module ?? ''] ?? r.module }} ·
                {{ r.frequency === 'none' ? '按需' : r.frequency }} ·
                {{ r.last_generated_at ? '上次生成 ' + String(r.last_generated_at).slice(0, 16).replace('T', ' ') : '未运行' }}
              </div>
            </div>
            <a @click="run(String(r.id))">运行</a>
            <a class="rm" @click="remove(String(r.id))">删除</a>
          </div>
          <div v-if="!loading && list.length === 0" class="empty sm">
            <Icon name="inbox" :size="34" /><div>暂无报表定义</div>
          </div>
        </div>

        <div class="pager">
          <span class="pbtn" :class="{ dis: page <= 1 }" @click="page--; load()">‹ 上一页</span>
          <span>第 {{ page }} / {{ totalPages }} 页</span>
          <span class="pbtn" :class="{ dis: page >= totalPages }" @click="page++; load()">下一页 ›</span>
        </div>

        <div class="quick">
          <b>快速预览</b>
          <button v-for="m in modules" :key="m" class="btn btn-sm btn-ghost" @click="preview(m)">
            {{ MODULE_TEXT[m] ?? m }}
          </button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3><Icon name="table" :size="17" /> 运行结果</h3>
        <span v-if="result" class="count">{{ result.rows.length }} 行</span>
      </div>
      <div class="card-body">
        <div v-if="!result" class="empty sm">
          <Icon name="bar-chart" :size="38" />
          <div>选择报表运行，或用左侧快速预览</div>
        </div>
        <template v-else>
          <div class="rt">{{ result.name }}（{{ MODULE_TEXT[result.module] ?? result.module }}）</div>
          <div class="table-wrap">
            <table class="list">
              <thead>
                <tr><th v-for="(c, i) in result.columns" :key="i">{{ c }}</th></tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in result.rows" :key="ri">
                  <td v-for="(cell, ci) in row" :key="ci">{{ cell ?? '—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>
    </div>
  </div>

  <div v-if="showModal" class="mask" @click.self="showModal = false">
    <div class="modal">
      <div class="modal-head">
        <h3><Icon name="plus" :size="18" /> 新建报表</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="field">
          <label>报表名称 *</label>
          <input v-model="form.name" class="input" placeholder="如 客户销售额排行" />
        </div>
        <div class="grid">
          <div class="field">
            <label>数据模块</label>
            <select v-model="form.module" class="input">
              <option v-for="m in modules" :key="m" :value="m">{{ MODULE_TEXT[m] ?? m }}</option>
            </select>
          </div>
          <div class="field">
            <label>生成频率</label>
            <select v-model="form.frequency" class="input">
              <option value="none">按需</option>
              <option value="daily">每日</option>
              <option value="weekly">每周</option>
              <option value="monthly">每月</option>
            </select>
          </div>
        </div>
        <p class="tip">报表模板按模块内置（销售/采购/库存/财务/人事），运行后结果可导出 Excel</p>
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
.grid2 { display: grid; grid-template-columns: 380px 1fr; gap: 16px; align-items: start; }
.rows { display: flex; flex-direction: column; gap: 8px; }
.row { display: flex; align-items: center; gap: 10px; border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 9px 11px; }
.meta { flex: 1; min-width: 0; }
.nm { font-size: 13px; font-weight: 600; }
.sub { font-size: 11.5px; color: var(--text-3); margin-top: 2px; }
.row a { font-size: 12.5px; color: var(--primary); }
.row a.rm { color: var(--danger); }
.quick { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--border-2); font-size: 12.5px; }
.rt { font-size: 13.5px; font-weight: 700; margin-bottom: 10px; }
.table-wrap { max-height: 420px; overflow: auto; }
.field { margin-bottom: 12px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 5px; font-weight: 600; }
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.tip { font-size: 12px; color: var(--text-3); margin: 0; }
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
.empty.sm { padding: 26px 0; }
.empty.sm svg { opacity: .35; }
.mask { position: fixed; inset: 0; background: rgba(15,23,42,.45); backdrop-filter: blur(3px); display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 60px 20px; }
.modal { background: #fff; border-radius: var(--radius-lg); width: 520px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 20px 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

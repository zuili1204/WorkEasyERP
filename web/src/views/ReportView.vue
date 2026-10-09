<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type ReportResult } from '../api'
import Icon from '../components/Icon.vue'
import { toast } from '../stores/toast'

const list = ref<Record<string, string | null>[]>([])
const modules = ref<string[]>([])
const result = ref<ReportResult | null>(null)
const loading = ref(false)

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
    toast.success('报表运行完成')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    const msg = err.response?.data?.msg || '运行失败'
    errorMsg.value = msg
    toast.error(msg)
  }
}

async function preview(module: string) {
  errorMsg.value = ''
  try {
    result.value = await api.previewReport(module)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    const msg = err.response?.data?.msg || '预览失败'
    errorMsg.value = msg
    toast.error(msg)
  }
}

async function remove(id: string) {
  try {
    await api.deleteReport(id)
    await load()
    toast.success('报表已删除')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    const msg = err.response?.data?.msg || '删除失败'
    errorMsg.value = msg
    toast.error(msg)
  }
}

// 新建报表已迁移至独立页 /new/report（见 utils/entityForms.ts）

onMounted(async () => {
  modules.value = await api.reportModules()
  await load()
})
</script>

<template>
  <div class="grid2">
    <div class="card">
      <div class="card-head">
        <h3><Icon name="bar-chart" :size="17" /> 报表定义</h3>
        <router-link class="btn btn-primary btn-sm" to="/new/report"><Icon name="plus" :size="14" /> 新建报表</router-link>
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
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; margin-bottom: 10px; }
.empty.sm { padding: 26px 0; }
.empty.sm svg { opacity: .35; }
</style>

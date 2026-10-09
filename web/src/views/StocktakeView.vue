<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'
import { toast } from '../stores/toast'

const SIZE = 8
const state = reactive({ q: '', status: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)

// 新建盘点单已迁移至独立页 /new/stocktake（见 utils/entityForms.ts）

const showItems = ref(false)
const currentId = ref('')
const currentNo = ref('')
const items = ref<Record<string, string | null>[]>([])
const drafts = ref<Record<string, string>>({})
const saving = ref(false)
const errorMsg = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.stocktakes(state.q || undefined, state.status || undefined, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

async function openItems(row: Record<string, string | null>) {
  errorMsg.value = ''
  currentId.value = String(row.id)
  currentNo.value = String(row.no ?? '')
  items.value = await api.stocktakeItems(currentId.value)
  drafts.value = {}
  items.value.forEach((it) => {
    drafts.value[String(it.product_id)] = it.actual_qty ?? ''
  })
  showItems.value = true
}

function diffOf(it: Record<string, string | null>) {
  const actual = drafts.value[String(it.product_id)]
  if (actual === '' || actual === undefined) return 0
  return Number(actual) - Number(it.book_qty ?? 0)
}

async function saveActual() {
  saving.value = true
  errorMsg.value = ''
  try {
    const lines = items.value.map((it) => ({
      productId: String(it.product_id),
      actualQty: Number(drafts.value[String(it.product_id)] ?? 0),
    }))
    await api.inputStocktake(currentId.value, lines)
    await load()
    items.value = await api.stocktakeItems(currentId.value)
    toast.success('实盘数量已保存')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '保存失败'
  } finally {
    saving.value = false
  }
}

async function audit() {
  saving.value = true
  errorMsg.value = ''
  try {
    await api.auditStocktake(currentId.value)
    showItems.value = false
    state.page = 1
    await load()
    toast.success('审核完成，已按差异生成库存调整流水')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '审核失败'
  } finally {
    saving.value = false
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function statusBadge(s: string | null) {
  if (s === 'adjusted') return { text: '已调整', cls: 'badge-green' }
  if (s === 'counting') return { text: '盘点中', cls: 'badge-blue' }
  if (s === 'void') return { text: '已作废', cls: 'badge-gray' }
  return { text: '新建', cls: 'badge-orange' }
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showItems.value = false
}

onMounted(async () => {
  await load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="check-square" :size="17" /> 库存盘点</h3>
      <router-link class="btn btn-primary btn-sm" to="/new/stocktake">
        <Icon name="plus" :size="14" /> 新建盘点单
      </router-link>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索盘点单号" @input="load" />
        </div>
        <select v-model="state.status" class="input" style="width: 120px" @change="load">
          <option value="">全部状态</option>
          <option value="draft">新建</option>
          <option value="counting">盘点中</option>
          <option value="adjusted">已调整</option>
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
        empty-title="暂无盘点单"
        @go="go"
      >
        <template #head>
          <tr>
            <th>单号</th>
            <th>仓库</th>
            <th>盘点日期</th>
            <th>差异合计</th>
            <th>状态</th>
            <th class="nosort">操作</th>
          </tr>
        </template>

        <tr v-for="(r, i) in data.list" :key="i">
          <td>{{ r.no }}</td>
          <td>{{ r.warehouse_name }}</td>
          <td>{{ r.take_date }}</td>
          <td :style="Number(r.diff_qty ?? 0) === 0 ? '' : (Number(r.diff_qty) > 0 ? 'color: var(--accent)' : 'color: var(--danger)')">
            {{ r.diff_qty }}
          </td>
          <td><span class="badge" :class="statusBadge(r.status).cls">{{ statusBadge(r.status).text }}</span></td>
          <td class="op">
            <a @click="openItems(r)">{{ r.status === 'adjusted' ? '查看明细' : '录入实盘' }}</a>
          </td>
        </tr>
      </TableShell>
    </div>
  </div>

  <!-- 明细录入 -->
  <div v-if="showItems" class="mask" @click.self="showItems = false">
    <div class="modal wide">
      <div class="modal-head">
        <h3><Icon name="list" :size="18" /> {{ currentNo }} 明细</h3>
        <span class="x" @click="showItems = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <table class="list">
          <thead>
            <tr><th>商品</th><th>账面</th><th>实盘</th><th>差异</th></tr>
          </thead>
          <tbody>
            <tr v-for="(it, i) in items" :key="i">
              <td>{{ it.product_name }}</td>
              <td>{{ it.book_qty }} {{ it.unit || '' }}</td>
              <td>
                <input
                  v-model="drafts[String(it.product_id)]"
                  type="number"
                  class="input sm"
                  style="width: 110px"
                />
              </td>
              <td :style="diffOf(it) === 0 ? '' : (diffOf(it) > 0 ? 'color: var(--accent)' : 'color: var(--danger)')">
                {{ diffOf(it) }}
              </td>
            </tr>
          </tbody>
        </table>
        <p class="tip">审核后按差异生成库存调整流水（正=盘盈入库，负=盘亏出库）</p>
        <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showItems = false">关闭</button>
        <button class="btn" :disabled="saving" @click="saveActual"><Icon name="check" :size="15" /> 保存实盘</button>
        <button class="btn btn-primary" :disabled="saving" @click="audit">
          <Icon name="check-circle" :size="15" /> 审核并调整库存
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nosort { cursor: default; }
.input.sm { padding: 6px 8px; font-size: 12.5px; }
.tip { font-size: 12px; color: var(--text-3); margin: 10px 0 0; }
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin: 10px 0 0; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 60px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 520px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal.wide { width: 760px; }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 20px 24px; max-height: 60vh; overflow: auto; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

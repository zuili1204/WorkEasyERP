<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'

const SIZE = 8

const META: Record<string, { title: string; icon: string; fields: Array<[string, string, string]>; cols: Array<[string, string]> }> = {
  category: {
    title: '商品分类',
    icon: 'box',
    fields: [['code', '编码', 'text'], ['name', '名称 *', 'text'], ['sortNo', '排序', 'number']],
    cols: [['code', '编码'], ['name', '名称'], ['sort_no', '排序']],
  },
  product: {
    title: '商品 / SKU',
    icon: 'package',
    fields: [
      ['sku', 'SKU', 'text'],
      ['name', '名称 *', 'text'],
      ['spec', '规格', 'text'],
      ['unit', '单位', 'text'],
      ['salePrice', '售价', 'number'],
      ['taxRate', '税率', 'number'],
    ],
    cols: [['sku', 'SKU'], ['name', '名称'], ['spec', '规格'], ['unit', '单位'], ['sale_price', '售价'], ['tax_rate', '税率']],
  },
  supplier: {
    title: '供应商',
    icon: 'truck',
    fields: [
      ['code', '编码', 'text'],
      ['name', '名称 *', 'text'],
      ['contactName', '联系人', 'text'],
      ['contactPhone', '联系电话', 'text'],
      ['paymentTerms', '账期(天)', 'number'],
    ],
    cols: [['code', '编码'], ['name', '名称'], ['contact_name', '联系人'], ['contact_phone', '电话'], ['payment_terms', '账期']],
  },
  warehouse: {
    title: '仓库',
    icon: 'building',
    fields: [['code', '编码', 'text'], ['name', '名称 *', 'text'], ['location', '位置', 'text'], ['type', '类型', 'select:normal/bonded']],
    cols: [['code', '编码'], ['name', '名称'], ['location', '位置'], ['type', '类型'], ['status', '状态']],
  },
  currency: {
    title: '汇率',
    icon: 'wallet',
    fields: [
      ['currencyFrom', '源币种 *', 'text'],
      ['currencyTo', '目标币种', 'text'],
      ['rate', '汇率 *', 'number'],
      ['rateDate', '日期 *', 'date'],
    ],
    cols: [['currency_from', '源'], ['currency_to', '目标'], ['rate', '汇率'], ['rate_date', '日期']],
  },
}

const overview = ref<Record<string, string | null>[]>([])
const kind = ref<string>('category')
const state = reactive({ q: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const showModal = ref(false)
const form = reactive<Record<string, string>>({})
const saving = ref(false)

const meta = computed(() => META[kind.value] ?? META.category)
const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function loadOverview() {
  overview.value = await api.baseOverview()
}

async function load() {
  loading.value = true
  try {
    data.value = await api.baseList(kind.value, state.q || undefined, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

function openModal() {
  meta.value.fields.forEach(([k]) => (form[k] = ''))
  showModal.value = true
}

async function submit() {
  saving.value = true
  try {
    const payload: Record<string, unknown> = {}
    meta.value.fields.forEach(([k, , type]) => {
      const v = form[k]
      if (!v) return
      payload[k] = type === 'number' ? Number(v) : v
    })
    await api.baseCreate(kind.value, payload)
    showModal.value = false
    state.page = 1
    await load()
    await loadOverview()
  } finally {
    saving.value = false
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

watch(kind, () => {
  state.page = 1
  state.q = ''
  load()
})

onMounted(async () => {
  await loadOverview()
  await load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div>
    <div class="cards">
      <div
        v-for="o in overview"
        :key="String(o.kind)"
        class="tile"
        :class="{ active: kind === String(o.kind) }"
        @click="kind = String(o.kind)"
      >
        <div class="ic"><Icon :name="(META[String(o.kind)] ?? META.category).icon" :size="20" /></div>
        <div class="t">{{ o.title }}</div>
        <div class="n">{{ o.count }}</div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3><Icon :name="meta.icon" :size="17" /> {{ meta.title }}</h3>
        <button class="btn btn-primary btn-sm" @click="openModal">
          <Icon name="plus" :size="14" /> 新增
        </button>
      </div>

      <div class="card-body">
        <div class="toolbar">
          <div class="search-box">
            <Icon name="search" :size="15" />
            <input v-model="state.q" class="input" placeholder="搜索" @input="load" />
          </div>
          <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
          <div class="spacer" />
          <span class="count">共 {{ data.total }} 条</span>
        </div>

        <div class="table-wrap">
          <table class="list">
            <thead>
              <tr><th v-for="c in meta.cols" :key="c[0]">{{ c[1] }}</th></tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in data.list" :key="i">
                <td v-for="c in meta.cols" :key="c[0]">{{ r[c[0]] ?? '—' }}</td>
              </tr>
            </tbody>
          </table>

          <div v-if="!loading && data.total === 0" class="empty">
            <Icon name="inbox" :size="46" />
            <div>暂无数据，可点击右上角新增</div>
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
          <h3><Icon :name="meta.icon" :size="18" /> 新增{{ meta.title }}</h3>
          <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
        </div>
        <div class="modal-body">
          <div v-for="f in meta.fields" :key="f[0]" class="field">
            <label>{{ f[1] }}</label>
            <select v-if="f[2].startsWith('select:')" v-model="form[f[0]]" class="input">
              <option v-for="o in f[2].split(':')[1].split('/')" :key="o" :value="o">{{ o }}</option>
            </select>
            <input v-else v-model="form[f[0]]" :type="f[2]" class="input" />
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn" @click="showModal = false">取消</button>
          <button class="btn btn-primary" :disabled="saving" @click="submit">
            <Icon name="check" :size="15" /> 保存
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 12px; margin-bottom: 16px; }
.tile {
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
  padding: 14px 16px; cursor: pointer; transition: .15s var(--ease); box-shadow: var(--shadow-sm);
}
.tile:hover { border-color: var(--primary); transform: translateY(-2px); }
.tile.active { border-color: var(--primary); background: var(--primary-light); }
.tile .ic { color: var(--primary); margin-bottom: 8px; }
.tile .t { font-size: 13px; color: var(--text-2); }
.tile .n { font-size: 22px; font-weight: 800; color: var(--text); margin-top: 2px; }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 80px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 480px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

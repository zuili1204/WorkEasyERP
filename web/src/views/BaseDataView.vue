<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'

const SIZE = 8

const META: Record<string, { title: string; icon: string; cols: Array<[string, string]> }> = {
  category: {
    title: '商品分类',
    icon: 'box',
    cols: [['code', '编码'], ['name', '名称'], ['sort_no', '排序']],
  },
  product: {
    title: '商品 / SKU',
    icon: 'package',
    cols: [['sku', 'SKU'], ['name', '名称'], ['spec', '规格'], ['unit', '单位'], ['sale_price', '售价'], ['tax_rate', '税率']],
  },
  supplier: {
    title: '供应商',
    icon: 'truck',
    cols: [['code', '编码'], ['name', '名称'], ['contact_name', '联系人'], ['contact_phone', '电话'], ['payment_terms', '账期']],
  },
  warehouse: {
    title: '仓库',
    icon: 'building',
    cols: [['code', '编码'], ['name', '名称'], ['location', '位置'], ['type', '类型'], ['status', '状态']],
  },
  currency: {
    title: '汇率',
    icon: 'wallet',
    cols: [['currency_from', '源'], ['currency_to', '目标'], ['rate', '汇率'], ['rate_date', '日期']],
  },
}

const overview = ref<Record<string, string | null>[]>([])
const kind = ref<string>('category')
const state = reactive({ q: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
// 五类基础数据新增已迁移至独立页 /new/basedata/<kind>（见 utils/entityForms.ts）

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

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

watch(kind, () => {
  state.page = 1
  state.q = ''
  load()
})

onMounted(async () => {
  await loadOverview()
  await load()
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
        <router-link class="btn btn-primary btn-sm" :to="`/new/basedata/${kind}`">
          <Icon name="plus" :size="14" /> 新增
        </router-link>
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

        <TableShell
          :rows="data.list"
          :loading="loading"
          :page="data.page"
          :pages="totalPages"
          empty-title="暂无数据，可点击右上角新增"
          @go="go"
        >
          <template #head>
            <tr><th v-for="c in meta.cols" :key="c[0]">{{ c[1] }}</th></tr>
          </template>

          <tr v-for="(r, i) in data.list" :key="i">
            <td v-for="c in meta.cols" :key="c[0]">{{ r[c[0]] ?? '—' }}</td>
          </tr>
        </TableShell>
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

</style>

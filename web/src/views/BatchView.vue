<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import { toast } from '../stores/toast'

const SIZE = 8
const state = reactive({ q: '', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const expiring = ref<Record<string, string | null>[]>([])

const errorMsg = ref('')
// 登记批次已迁移至独立页 /new/batch（见 utils/entityForms.ts）

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.batches(state.q || undefined, state.page, SIZE)
    expiring.value = await api.expiringBatches()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    toast.error(err.response?.data?.msg || '批次列表加载失败')
  } finally {
    loading.value = false
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function daysLeft(v: string | null | undefined) {
  const d = Number(v)
  if (isNaN(d)) return '—'
  if (d < 0) return `已过期 ${-d} 天`
  return `${d} 天后到期`
}

onMounted(load)
</script>

<template>
  <div>
    <div v-if="expiring.length" class="alert-bar">
      <Icon name="alert-triangle" :size="16" />
      <span><b>{{ expiring.length }}</b> 个批次 30 天内到期或已过期：</span>
      <span v-for="(b, i) in expiring.slice(0, 3)" :key="i" class="chip">
        {{ b.batch_no }}（{{ daysLeft(b.days_left) }}）
      </span>
    </div>

    <div class="card">
      <div class="card-head">
        <h3><Icon name="box" :size="17" /> 批次 / 库位</h3>
        <router-link class="btn btn-primary btn-sm" to="/new/batch">
          <Icon name="plus" :size="14" /> 登记批次
        </router-link>
      </div>

      <div class="card-body">
        <div class="toolbar">
          <div class="search-box">
            <Icon name="search" :size="15" />
            <input v-model="state.q" class="input" placeholder="搜索批次号 / 商品 / 库位" @input="load" />
          </div>
          <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
          <div class="spacer" />
          <span class="count">共 {{ data.total }} 条</span>
        </div>

        <p v-if="errorMsg" class="err">{{ errorMsg }}</p>

        <div class="table-wrap">
          <table class="list">
            <thead>
              <tr>
                <th>批次号</th>
                <th>商品</th>
                <th>仓库</th>
                <th>库位</th>
                <th>数量</th>
                <th>成本</th>
                <th>入库日</th>
                <th>到期日</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in data.list" :key="i">
                <td>{{ r.batch_no }}</td>
                <td>{{ r.sku }} · {{ r.product_name }}</td>
                <td>{{ r.warehouse_name }}</td>
                <td>{{ r.location_code || '—' }}</td>
                <td>{{ r.qty }}</td>
                <td>{{ r.cost_price }}</td>
                <td>{{ r.inbound_date }}</td>
                <td>{{ r.expire_date || '—' }}</td>
              </tr>
            </tbody>
          </table>

          <div v-if="!loading && data.total === 0" class="empty">
            <Icon name="inbox" :size="46" />
            <div>暂无批次记录</div>
          </div>
        </div>

        <div class="pager">
          <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
          <span>第 {{ data.page }} / {{ totalPages }} 页</span>
          <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.alert-bar {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  background: var(--warn-light); color: #b45309; border: 1px solid #fde68a;
  border-radius: var(--radius); padding: 11px 16px; margin-bottom: 14px; font-size: 13px;
}
.chip { background: #fff; border-radius: 6px; padding: 2px 8px; font-size: 12px; }
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin: 10px 0 0; }
</style>

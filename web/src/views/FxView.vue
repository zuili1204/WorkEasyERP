<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import { toast } from '../stores/toast'

const currencies = ref<Record<string, string | null>[]>([])
const rates = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: 8 })
const page = ref(1)
const filterCurrency = ref('')

// 新增币种 / 录入汇率已迁移至独立页 /new/fx/currency｜rate（见 utils/entityForms.ts）

const conv = reactive({ amount: 100, from: 'USD', to: 'CNY', date: '' })
const convResult = ref<Record<string, string | null> | null>(null)

const loading = ref(false)
const errorMsg = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(rates.value.total / 8)))
const baseCode = computed(() => currencies.value.find((c) => c.is_base === 'true')?.code ?? '—')

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    currencies.value = await api.currencies()
    rates.value = await api.fxRates(filterCurrency.value || undefined, page.value, 8)
  } finally {
    loading.value = false
  }
}

async function removeRate(id: string) {
  try {
    await api.deleteFxRate(id)
    await load()
    toast.success('汇率记录已删除')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    const msg = err.response?.data?.msg || '删除失败'
    errorMsg.value = msg
    toast.error(msg)
  }
}

async function makeBase(code: string) {
  try {
    await api.setBaseCurrency(code)
    await load()
    toast.success(`已将 ${code} 设为本位币`)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    const msg = err.response?.data?.msg || '设置失败'
    errorMsg.value = msg
    toast.error(msg)
  }
}

async function toggle(code: string, enabled: boolean) {
  try {
    await api.toggleCurrency(code, enabled)
    await load()
    toast.success(`${code} 已${enabled ? '启用' : '停用'}`)
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    toast.error(err.response?.data?.msg || '状态切换失败')
  }
}

async function doConvert() {
  errorMsg.value = ''
  convResult.value = null
  try {
    convResult.value = await api.fxConvert(
      Number(conv.amount),
      conv.from.toUpperCase(),
      conv.to.toUpperCase(),
      conv.date || undefined,
    )
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    const msg = err.response?.data?.msg || '换算失败'
    errorMsg.value = msg
    toast.error(msg)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <p v-if="errorMsg" class="err">{{ errorMsg }}</p>

    <!-- 币种 -->
    <div class="card">
      <div class="card-head">
        <h3><Icon name="dollar-sign" :size="17" /> 币种</h3>
        <router-link class="btn btn-primary btn-sm" to="/new/fx/currency">
          <Icon name="plus" :size="14" /> 新增币种
        </router-link>
      </div>
      <div class="card-body">
        <div class="cur-grid">
          <div
            v-for="c in currencies" :key="String(c.code)"
            class="cur" :class="{ base: c.is_base === 'true', off: c.enabled !== 'true' }"
          >
            <div class="top">
              <span class="sym">{{ c.symbol }}</span>
              <b>{{ c.code }}</b>
              <span v-if="c.is_base === 'true'" class="tag">本位币</span>
            </div>
            <div class="nm">{{ c.name }}</div>
            <div class="rx">
              <span v-if="c.latest_rate">对 {{ baseCode }}：{{ c.latest_rate }}</span>
              <span v-else class="dim">未维护汇率</span>
            </div>
            <div class="acts">
              <a v-if="c.is_base !== 'true'" @click="makeBase(String(c.code))">设为本位币</a>
              <a v-if="c.is_base !== 'true'" @click="toggle(String(c.code), c.enabled !== 'true')">
                {{ c.enabled === 'true' ? '停用' : '启用' }}
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 汇率 -->
    <div class="card">
      <div class="card-head">
        <h3><Icon name="trending-up" :size="17" /> 汇率</h3>
        <router-link class="btn btn-primary btn-sm" to="/new/fx/rate">
          <Icon name="plus" :size="14" /> 录入汇率
        </router-link>
      </div>
      <div class="card-body">
        <div class="toolbar">
          <select v-model="filterCurrency" class="input" style="width: 150px" @change="page = 1; load()">
            <option value="">全部币种</option>
            <option v-for="c in currencies" :key="String(c.code)" :value="String(c.code)">{{ c.code }} · {{ c.name }}</option>
          </select>
          <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
          <div class="spacer" />
          <span class="count">共 {{ rates.total }} 条</span>
        </div>

        <div class="table-wrap">
          <table class="list">
            <thead>
              <tr>
                <th>源币种</th>
                <th>目标币种</th>
                <th>汇率</th>
                <th>生效日</th>
                <th>来源</th>
                <th class="nosort">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in rates.list" :key="i">
                <td>{{ r.currency_from }} · {{ r.currency_name }}</td>
                <td>{{ r.currency_to }}</td>
                <td><b>{{ r.rate }}</b></td>
                <td>{{ r.rate_date }}</td>
                <td>{{ r.source }}</td>
                <td class="op"><a class="danger" @click="removeRate(String(r.id))">删除</a></td>
              </tr>
            </tbody>
          </table>
          <div v-if="!loading && rates.total === 0" class="empty">
            <Icon name="inbox" :size="46" />
            <div>暂无汇率记录</div>
          </div>
        </div>

        <div class="pager">
          <span class="pbtn" :class="{ dis: page <= 1 }" @click="page--; load()">‹ 上一页</span>
          <span>第 {{ rates.page }} / {{ totalPages }} 页</span>
          <span class="pbtn" :class="{ dis: page >= totalPages }" @click="page++; load()">下一页 ›</span>
        </div>

        <div class="conv">
          <b>换算试算</b>
          <input v-model.number="conv.amount" type="number" class="input sm" />
          <select v-model="conv.from" class="input sm">
            <option v-for="c in currencies" :key="String(c.code)" :value="String(c.code)">{{ c.code }}</option>
          </select>
          <span>→</span>
          <select v-model="conv.to" class="input sm">
            <option v-for="c in currencies" :key="String(c.code)" :value="String(c.code)">{{ c.code }}</option>
          </select>
          <input v-model="conv.date" type="date" class="input sm" />
          <button class="btn btn-sm btn-primary" @click="doConvert">换算</button>
          <span v-if="convResult" class="conv-out">
            {{ convResult.amount }} {{ convResult.from }} = <b>{{ convResult.result }}</b> {{ convResult.to }}
            <span class="dim">（汇率 {{ convResult.rate }} @ {{ convResult.date }}）</span>
          </span>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin-bottom: 12px; }
.cur-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 12px; }
.cur { border: 1px solid var(--border); border-radius: var(--radius); padding: 13px 15px; background: #fff; }
.cur.base { border-color: var(--primary); border-left: 4px solid var(--primary); }
.cur.off { opacity: .55; }
.cur .top { display: flex; align-items: center; gap: 8px; }
.cur .sym { font-size: 19px; color: var(--primary-active); }
.cur .nm { font-size: 13px; color: var(--text-2); margin-top: 3px; }
.cur .rx { font-size: 12.5px; margin-top: 7px; }
.cur .tag { background: var(--primary-light); color: var(--primary-active); font-size: 11px; padding: 1px 7px; border-radius: 20px; }
.cur .acts { margin-top: 9px; display: flex; gap: 12px; font-size: 12.5px; }
.dim { color: var(--text-3); }
.op .danger { color: var(--danger); }
.nosort { cursor: default; }

.conv { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--border-2); font-size: 13px; }
.input.sm { width: auto; padding: 6px 9px; font-size: 12.5px; }
.conv-out { background: var(--primary-light); color: var(--primary-active); padding: 5px 11px; border-radius: var(--radius-sm); font-size: 12.5px; }

</style>

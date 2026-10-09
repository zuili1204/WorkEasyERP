<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, type DashboardOverview } from '../api'
import { auth } from '../stores/auth'
import Icon from '../components/Icon.vue'
import { WORKFLOW_STATUS, statusOf } from '../utils/dict'
import { fmtCompact, fmtDateTime } from '../utils/format'

const router = useRouter()
const data = ref<DashboardOverview | null>(null)
const loading = ref(true)

/** 按指标名匹配图标，让 KPI 卡片更具可读性（无匹配时按色调兜底） */
const CARD_ICON: Array<[RegExp, string]> = [
  [/营收|销售额|回款|金额|毛利|利润|收入/, 'dollar-sign'],
  [/待办|审批|待处理|流程/, 'check-square'],
  [/预警|风险|逾期|超期|异常/, 'alert-triangle'],
  [/订单|合同/, 'briefcase'],
  [/客户|商机|线索/, 'users'],
  [/库存|库存金额|在库/, 'package'],
  [/出勤|考勤|打卡/, 'clock'],
  [/员工|人数|入职|招聘/, 'user'],
]
function cardIcon(label: string): string {
  return (CARD_ICON.find(([re]) => re.test(label))?.[1] ?? 'bar-chart')
}

const hour = new Date().getHours()
const greeting = hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好'

const SCOPE_TEXT: Record<string, string> = {
  all: '全部数据',
  dept: '本部门数据',
  customer: '本人负责的客户与单据',
  self: '本人数据',
}

// 审批状态统一取自 utils/dict

const ALERT_ICON: Record<string, string> = {
  stock: 'package',
  expire: 'clock',
  overdue: 'alert-triangle',
}

/** 坐标轴上限：取整到「整十/整百」量级，使 Y 轴刻度可读（原来直接用原始最大值，没有刻度） */
const axisTop = computed(() => {
  const raw = Math.max(1, ...(data.value?.trend ?? []).map((t) => Math.max(t.salesRaw, t.profitRaw)))
  const mag = Math.pow(10, Math.floor(Math.log10(raw)))
  return Math.max(1, Math.ceil(raw / mag) * mag)
})
/** Y 轴刻度：由高到低 [上限, 1/2, 0] */
const axisTicks = computed(() => [axisTop.value, axisTop.value / 2, 0])

function barHeight(v: number) {
  return Math.max(4, Math.round((v / axisTop.value) * 100))
}

onMounted(async () => {
  try {
    data.value = await api.overview()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <div class="hero">
      <div>
        <h2>{{ greeting }}，{{ auth.user?.displayName }}</h2>
        <p>
          当前身份：{{ (auth.user?.roles || []).join(' / ') }} · {{ auth.user?.deptName || '—' }} ·
          数据范围 {{ SCOPE_TEXT[auth.user?.dataScope || 'self'] || auth.user?.dataScope }}
        </p>
      </div>
      <div class="hero-actions">
        <button class="btn" @click="router.push('/todos')"><Icon name="check-square" :size="15" /> 待办中心</button>
        <button class="btn btn-primary" @click="router.push('/orders/sales')">
          <Icon name="briefcase" :size="15" /> 销售订单
        </button>
      </div>
    </div>

    <!-- 统计卡（按角色数据范围聚合） -->
    <div class="stat-grid">
      <template v-if="loading">
        <div v-for="i in 4" :key="i" class="stat-card skeleton-card"><span class="sk" /></div>
      </template>
      <template v-else>
        <div v-for="c in data?.cards ?? []" :key="c.label" class="stat-card" :class="c.tone">
          <Icon class="stat-ico" :name="cardIcon(c.label)" :size="20" />
          <div class="label">{{ c.label }}</div>
          <div class="num">{{ c.value }}</div>
          <div class="sub">{{ c.sub }}</div>
        </div>
      </template>
    </div>

    <!-- 加载态由上方统计卡骨架屏承担，此处不再叠加 LoadingState（原先两者会同时出现） -->
    <template v-if="!loading">
    <div class="grid2">
      <!-- 营收 / 毛利趋势 -->
      <div class="card">
        <div class="card-head">
          <h3><Icon name="trending-up" :size="17" /> 近 6 月营收与毛利</h3>
          <span class="count">销售订单口径</span>
        </div>
        <div class="card-body">
          <div v-if="(data?.trend ?? []).length === 0" class="empty sm">
            <Icon name="inbox" :size="34" />
            <div>暂无订单数据</div>
          </div>
          <div v-else class="chart-wrap">
            <div class="y-axis">
              <span v-for="tick in axisTicks" :key="tick">{{ fmtCompact(tick) }}</span>
            </div>
            <div class="plot">
              <div
                v-for="(tick, i) in axisTicks"
                :key="'line' + i"
                class="grid-line"
                :style="{ top: (i / (axisTicks.length - 1)) * 100 + '%' }"
              ></div>
              <div v-for="t in data?.trend ?? []" :key="t.month" class="col">
                <div class="bars">
                  <div class="bar sales" :style="{ height: barHeight(t.salesRaw) + '%' }" :title="`营收 ${t.sales}`"></div>
                  <div class="bar profit" :style="{ height: barHeight(t.profitRaw) + '%' }" :title="`毛利 ${t.profit}`"></div>
                </div>
                <div class="m">{{ t.month.slice(2) }}</div>
                <div class="v">{{ t.sales }}</div>
              </div>
            </div>
          </div>
          <div class="legend">
            <span><i class="sw sales"></i> 营收</span>
            <span><i class="sw profit"></i> 毛利</span>
          </div>
        </div>
      </div>

      <!-- 应收 Top5 -->
      <div class="card">
        <div class="card-head">
          <h3><Icon name="wallet" :size="17" /> 应收余额 Top5</h3>
          <button class="btn btn-sm btn-ghost" @click="router.push('/finance')">查看台账</button>
        </div>
        <div class="card-body">
          <div v-if="(data?.topCustomers ?? []).length === 0" class="empty sm">
            <Icon name="inbox" :size="34" />
            <div>暂无未结应收</div>
          </div>
          <div v-for="(t, i) in data?.topCustomers ?? []" :key="i" class="rank">
            <span class="idx">{{ i + 1 }}</span>
            <span class="nm">{{ t.name }}</span>
            <b class="amt">{{ t.amount }}</b>
          </div>
        </div>
      </div>
    </div>

    <!-- 预警 -->
    <div class="card">
      <div class="card-head">
        <h3><Icon name="alert-triangle" :size="17" /> 风险预警</h3>
        <span class="count">{{ (data?.alerts ?? []).length }} 条</span>
      </div>
      <div class="card-body">
        <div v-if="(data?.alerts ?? []).length === 0" class="empty sm">
          <Icon name="check-circle" :size="34" />
          <div>当前无预警，一切正常</div>
        </div>
        <div v-for="(a, i) in data?.alerts ?? []" :key="i" class="alert">
          <Icon :name="ALERT_ICON[a.kind] ?? 'alert-triangle'" :size="16" />
          <div>
            <div class="t">{{ a.title }}</div>
            <div class="d">{{ a.detail }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3><Icon name="list" :size="17" /> 与我相关的流程</h3>
        <button class="btn btn-sm btn-ghost" @click="router.push('/todos')">查看全部</button>
      </div>
      <div class="card-body">
        <div v-if="(data?.recent ?? []).length === 0" class="empty sm">
          <Icon name="inbox" :size="34" />
          <div>暂无相关流程</div>
        </div>
        <table v-else class="list">
          <thead>
            <tr>
              <th>事项</th>
              <th>类型</th>
              <th>状态</th>
              <th>发起时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in data?.recent ?? []" :key="r.id">
              <td>{{ r.title }}</td>
              <td>{{ r.bizType }}</td>
              <td>
                <span class="badge" :class="statusOf(WORKFLOW_STATUS, r.status).cls">
                  {{ statusOf(WORKFLOW_STATUS, r.status).text }}
                </span>
              </td>
              <td>{{ fmtDateTime(r.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    </template>
  </div>
</template>

<style scoped>
.hero {
  background: linear-gradient(135deg, #EEF2FF, #FFFFFF);
  border: 1px solid var(--border); border-radius: var(--radius);
  padding: 22px 24px; display: flex; align-items: center; justify-content: space-between;
  gap: 16px; margin-bottom: 16px; flex-wrap: wrap;
}
.hero h2 { margin: 0 0 6px; font-size: 21px; font-weight: 800; }
.hero p { margin: 0; color: var(--text-2); font-size: 13.5px; }
.hero-actions { display: flex; gap: 10px; }
.stat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 14px; margin-bottom: 16px; }
.stat-card {
  position: relative; overflow: hidden; background: var(--surface); border: 1px solid var(--border);
  border-radius: var(--radius); padding: 18px 20px; box-shadow: var(--shadow-sm);
  border-left: 4px solid var(--primary);
}
.stat-card.blue { border-left-color: var(--blue); }
.stat-card.orange { border-left-color: var(--warn); }
.stat-card.green { border-left-color: var(--accent); }
.stat-card.red { border-left-color: var(--danger); }
.stat-card.indigo { border-left-color: var(--primary); }
.stat-card .label { color: var(--text-2); font-size: 13px; font-weight: 500; }
.stat-card .num { font-size: 26px; font-weight: 800; margin-top: 6px; color: var(--text); }
.stat-card .sub { font-size: 12px; color: var(--text-3); margin-top: 4px; }
.stat-ico { position: absolute; top: 15px; right: 16px; color: var(--text-4); opacity: .45; }
.skeleton-card { display: flex; align-items: center; min-height: 92px; }
.skeleton-card .sk {
  width: 56%; height: 16px; border-radius: 7px;
  background: linear-gradient(90deg, #EEF2F6 25%, #F8FAFC 37%, #EEF2F6 63%);
  background-size: 400% 100%; animation: shimmer 1.4s ease infinite;
}
.grid2 { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 16px; margin-bottom: 16px; }

/* 柱状图 */
.chart-wrap { display: grid; grid-template-columns: 46px 1fr; gap: 8px; padding: 8px 4px 0; }
.y-axis {
  height: 118px; display: flex; flex-direction: column; justify-content: space-between;
  align-items: flex-end; font-size: 10.5px; color: var(--text-4); font-variant-numeric: tabular-nums;
}
.plot { position: relative; display: flex; align-items: flex-end; gap: 14px; height: 118px; }
.grid-line { position: absolute; left: 0; right: 0; border-top: 1px dashed var(--border-2); }
.chart-wrap .col {
  flex: 1; display: flex; flex-direction: column; align-items: center;
  height: 100%; position: relative; z-index: 1;
}
.bars { display: flex; align-items: flex-end; gap: 4px; height: 118px; }
.bar { width: 15px; border-radius: 3px 3px 0 0; transition: height .4s var(--ease); min-height: 4px; }
.bar.sales { background: var(--primary); }
.bar.profit { background: var(--accent); }
.chart .m { font-size: 11px; color: var(--text-3); margin-top: 6px; }
.chart .v { font-size: 11.5px; font-weight: 700; color: var(--text-2); }
.legend { display: flex; gap: 16px; font-size: 12px; color: var(--text-3); margin-top: 10px; }
.sw { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 5px; }
.sw.sales { background: var(--primary); }
.sw.profit { background: var(--accent); }

/* 排名 */
.rank { display: flex; align-items: center; gap: 12px; padding: 9px 0; border-bottom: 1px solid var(--border-2); font-size: 13.5px; }
.rank .idx {
  width: 21px; height: 21px; border-radius: 50%; background: var(--primary-light);
  color: var(--primary-active); font-size: 11.5px; font-weight: 800;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.rank .nm { flex: 1; color: var(--text); }
.rank .amt { color: var(--text); }

/* 预警 */
.alert { display: flex; gap: 11px; padding: 11px 13px; border: 1px solid var(--border); border-left: 3px solid var(--warn); border-radius: var(--radius-sm); margin-bottom: 8px; background: #fff; }
.alert .t { font-size: 13px; font-weight: 700; color: var(--text); }
.alert .d { font-size: 12px; color: var(--text-3); margin-top: 2px; }
.empty.sm { padding: 28px 0; }
.empty.sm svg { opacity: .35; }
</style>

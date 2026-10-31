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
        <div class="kicker hero-kicker">OVERVIEW / 经营概览</div>
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
        <div v-for="(c, i) in data?.cards ?? []" :key="c.label" class="stat-card" :class="c.tone">
          <div class="stat-top">
            <span class="stat-idx">{{ String(i + 1).padStart(2, '0') }}</span>
            <Icon class="stat-ico" :name="cardIcon(c.label)" :size="17" />
          </div>
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
/* 概览头：墨青图纸 + 铜色角标，让"今天该干什么"一眼可见 */
.hero {
  position: relative; overflow: hidden;
  background: #0D1213; color: #F2F0E9;
  border: 1px solid #1C2526; border-radius: var(--radius-lg);
  padding: 20px 24px; display: flex; align-items: center; justify-content: space-between;
  gap: 16px; margin-bottom: 16px; flex-wrap: wrap;
  background-image:
    linear-gradient(rgba(255, 255, 255, .04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, .04) 1px, transparent 1px),
    radial-gradient(90% 130% at 100% 0%, rgba(184, 92, 46, .22), transparent 60%);
  background-size: 30px 30px, 30px 30px, 100% 100%;
}
.hero::after {
  content: ''; position: absolute; right: 22px; bottom: 18px; width: 40px; height: 40px;
  border-bottom: 1px solid rgba(184, 92, 46, .5); border-right: 1px solid rgba(184, 92, 46, .5);
  pointer-events: none;
}
.hero-kicker { color: var(--copper-soft); margin-bottom: 6px; }
.hero h2 { margin: 0 0 6px; font-size: 21px; font-weight: 700; letter-spacing: -.01em; }
.hero p { margin: 0; color: #A9B4B1; font-size: 12.5px; }
.hero-actions { display: flex; gap: 8px; }
.hero-actions .btn {
  background: rgba(255, 255, 255, .06); border-color: rgba(255, 255, 255, .2); color: #E8E6DF;
}
.hero-actions .btn:hover { background: rgba(255, 255, 255, .12); border-color: var(--copper-soft); color: #fff; }
.hero-actions .btn-primary { background: var(--primary); border-color: var(--primary-active); color: #fff; }
.hero-actions .btn-primary:hover { background: var(--primary-active); color: #fff; }

/* 指标卡：规格表样式 —— 序号 + 图标 + 等宽数字 + 顶部色标 */
.stat-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(196px, 1fr)); gap: 12px; margin-bottom: 16px; }
.stat-card {
  --tone: var(--primary);
  position: relative; overflow: hidden; background: var(--surface);
  border: 1px solid var(--border); border-radius: var(--radius-lg);
  padding: 14px 16px 15px; box-shadow: var(--shadow-sm);
  transition: box-shadow var(--dur) var(--ease), transform var(--dur) var(--ease), border-color var(--dur) var(--ease);
}
.stat-card::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 2px; background: var(--tone);
}
.stat-card:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); border-color: var(--hairline); }
.stat-card.blue { --tone: var(--blue); }
.stat-card.orange { --tone: var(--warn); }
.stat-card.green { --tone: var(--accent); }
.stat-card.red { --tone: var(--danger); }
.stat-card.indigo { --tone: var(--primary); }
.stat-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.stat-idx {
  font-family: var(--font-mono); font-size: 10.5px; font-weight: 600;
  letter-spacing: .1em; color: var(--tone); opacity: .9;
}
.stat-card .label { color: var(--text-3); font-size: 12px; font-weight: 500; letter-spacing: .02em; }
.stat-card .num {
  font-family: var(--font-mono); font-size: 25px; font-weight: 700;
  margin-top: 4px; color: var(--text); letter-spacing: -.02em; line-height: 1.25;
}
.stat-card .sub { font-size: 11.5px; color: var(--text-4); margin-top: 3px; }
.stat-ico { color: var(--text-4); opacity: .45; }
.skeleton-card { display: flex; align-items: center; min-height: 92px; }
.skeleton-card .sk {
  width: 56%; height: 14px; border-radius: var(--radius-xs);
  background: linear-gradient(90deg, #EDECE5 25%, #F7F6F1 37%, #EDECE5 63%);
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
  width: 20px; height: 20px; border-radius: var(--radius-xs); background: var(--primary-light);
  border: 1px solid #EAD3C3;
  color: var(--primary-active); font-family: var(--font-mono); font-size: 10.5px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.rank .nm { flex: 1; color: var(--text); }
.rank .amt { color: var(--text); font-family: var(--font-mono); font-weight: 600; }

/* 预警 */
.alert {
  display: flex; gap: 11px; padding: 10px 12px; border: 1px solid var(--border);
  border-left: 2px solid var(--warn); border-radius: var(--radius-sm);
  margin-bottom: 8px; background: #FCFBF7;
}
.alert svg { color: var(--warn); flex-shrink: 0; margin-top: 2px; }
.alert .t { font-size: 13px; font-weight: 700; color: var(--text); }
.alert .d { font-size: 12px; color: var(--text-3); margin-top: 2px; }
.empty.sm { padding: 28px 0; }
.empty.sm svg { opacity: .35; }
</style>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'
import TableShell from '../components/TableShell.vue'
import { toast } from '../stores/toast'

type Row = Record<string, string | null>
type AnyRow = Record<string, unknown>

const SIZE = 8
const TABS: [string, string][] = [
  ['overview', '概览'],
  ['orders', '商城订单'],
  ['payments', '支付流水'],
  ['afterSales', '售后'],
  ['bindings', '会员绑定'],
  ['invites', '邀请码'],
  ['banners', '轮播'],
  ['settings', '商城设置'],
]
const ORDER_STATUS: [string, string][] = [
  ['', '全部'],
  ['pending_pay', '待支付'],
  ['pending_confirm', '待确认'],
  ['pending_ship', '待发货'],
  ['shipped', '已发货'],
  ['completed', '已完成'],
  ['cancelled', '已取消'],
  ['closed', '已关闭'],
]

const tab = ref('overview')
const loading = ref(false)

const overview = ref<AnyRow | null>(null)

const ordersState = reactive({ q: '', status: '', page: 1 })
const orders = ref<PageResult<Row>>({ list: [], total: 0, page: 1, size: SIZE })
const detail = ref<AnyRow | null>(null)

const payState = reactive({ q: '', status: '', page: 1 })
const payments = ref<PageResult<Row>>({ list: [], total: 0, page: 1, size: SIZE })

const asState = reactive({ status: '', page: 1 })
const afterSales = ref<PageResult<Row>>({ list: [], total: 0, page: 1, size: SIZE })

const bindingStatus = ref('')
const bindings = ref<Row[]>([])
const bindingForm = reactive({ customerId: '', remark: '' })

const invites = ref<Row[]>([])
const inviteForm = reactive({ customerId: '', remark: '' })

const banners = ref<Row[]>([])
const bannerForm = reactive({ title: '', imageUrl: '', linkUrl: '', sortNo: 0 })

const settings = ref<Row[]>([])
const settingsDraft = ref<Record<string, string>>({})

const ordersPages = computed(() => Math.max(1, Math.ceil(orders.value.total / SIZE)))
const payPages = computed(() => Math.max(1, Math.ceil(payments.value.total / SIZE)))
const asPages = computed(() => Math.max(1, Math.ceil(afterSales.value.total / SIZE)))
const detailItems = computed<Row[]>(() => (detail.value?.items as Row[] | undefined) ?? [])

const overviewCards = computed(() => {
  const o = overview.value || {}
  const counts = (o.statusCounts as Record<string, number>) || {}
  return [
    ['今日订单', Number(o.todayOrders ?? 0)],
    ['今日金额', money(String(o.todayAmount ?? 0))],
    ['待发货', Number(counts.pending_ship ?? 0)],
    ['待确认', Number(counts.pending_confirm ?? 0)],
    ['待审核绑定', Number(o.pendingBindings ?? 0)],
    ['待处理售后', Number(o.pendingRefunds ?? 0)],
  ] as [string, string | number][]
})

function money(v: string | null | undefined) {
  if (v == null || v === '') return '—'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function errMsg(e: unknown, fallback: string) {
  const err = e as { response?: { data?: { msg?: string } } }
  return err.response?.data?.msg || fallback
}

const ORDER_BADGE: Record<string, { text: string; cls: string }> = {
  pending_pay: { text: '待支付', cls: 'badge-orange' },
  pending_confirm: { text: '待确认', cls: 'badge-orange' },
  pending_ship: { text: '待发货', cls: 'badge-blue' },
  shipped: { text: '已发货', cls: 'badge-blue' },
  completed: { text: '已完成', cls: 'badge-green' },
  cancelled: { text: '已取消', cls: 'badge-gray' },
  closed: { text: '已关闭', cls: 'badge-gray' },
}
function orderBadge(s: string | null) {
  return ORDER_BADGE[s ?? ''] || { text: s || '—', cls: 'badge-gray' }
}

const PAY_BADGE: Record<string, { text: string; cls: string }> = {
  pending: { text: '待支付', cls: 'badge-orange' },
  success: { text: '已支付', cls: 'badge-green' },
  failed: { text: '失败', cls: 'badge-gray' },
  refunded: { text: '已退款', cls: 'badge-blue' },
}
function payBadge(s: string | null) {
  return PAY_BADGE[s ?? ''] || { text: s || '—', cls: 'badge-gray' }
}

const REFUND_BADGE: Record<string, { text: string; cls: string }> = {
  applied: { text: '待审核', cls: 'badge-orange' },
  approved: { text: '已通过', cls: 'badge-green' },
  rejected: { text: '已驳回', cls: 'badge-gray' },
  done: { text: '已完成', cls: 'badge-green' },
}
function refundBadge(s: string | null) {
  return REFUND_BADGE[s ?? ''] || { text: s || '—', cls: 'badge-gray' }
}

const BIND_BADGE: Record<string, { text: string; cls: string }> = {
  pending: { text: '待审核', cls: 'badge-orange' },
  active: { text: '已绑定', cls: 'badge-green' },
  disabled: { text: '已停用', cls: 'badge-gray' },
}
function bindBadge(s: string | null) {
  return BIND_BADGE[s ?? ''] || { text: s || '—', cls: 'badge-gray' }
}

async function load() {
  loading.value = true
  try {
    if (tab.value === 'overview') overview.value = await api.mallOverview()
    else if (tab.value === 'orders') {
      orders.value = await api.mallOrders(ordersState.q || undefined, ordersState.status || undefined, ordersState.page, SIZE)
    } else if (tab.value === 'payments') {
      payments.value = await api.mallPayments(payState.q || undefined, payState.status || undefined, payState.page, SIZE)
    } else if (tab.value === 'afterSales') {
      afterSales.value = await api.mallAfterSales(asState.status || undefined, asState.page, SIZE)
    } else if (tab.value === 'bindings') {
      bindings.value = await api.mallBindings(bindingStatus.value || undefined)
    } else if (tab.value === 'invites') invites.value = await api.mallInvites()
    else if (tab.value === 'banners') banners.value = await api.mallBanners()
    else if (tab.value === 'settings') {
      settings.value = await api.mallSettings()
      settingsDraft.value = Object.fromEntries(
        settings.value.map((r) => [String(r.key), String(r.value ?? '')]),
      )
    }
  } catch (e: unknown) {
    toast.error(errMsg(e, '加载失败'))
  } finally {
    loading.value = false
  }
}

function goOrders(p: number) {
  if (p < 1 || p > ordersPages.value) return
  ordersState.page = p
  load()
}
function goPayments(p: number) {
  if (p < 1 || p > payPages.value) return
  payState.page = p
  load()
}
function goAfterSales(p: number) {
  if (p < 1 || p > asPages.value) return
  asState.page = p
  load()
}

async function run(act: () => Promise<unknown>, ok: string) {
  try {
    await act()
    await load()
    toast.success(ok)
  } catch (e: unknown) {
    toast.error(errMsg(e, '操作失败'))
  }
}

function closeOrder(id: string) {
  if (!window.confirm('确认关闭该订单？锁定库存将被释放。')) return
  void run(() => api.closeMallOrder(id, '后台关闭'), '订单已关闭')
}

function confirmCredit(id: string) {
  void run(() => api.confirmMallCredit(id), '账期订单已确认，已生成销售订单与应收')
}

function confirmOffline(id: string) {
  void run(() => api.confirmMallOffline(id), '已登记线下收款并生成销售订单')
}

async function openDetail(id: string) {
  try {
    detail.value = await api.mallOrderDetail(id)
  } catch (e: unknown) {
    toast.error(errMsg(e, '加载订单详情失败'))
  }
}

function approveRefund(id: string) {
  void run(() => api.approveMallRefund(id, '后台审核通过'), '售后已通过，已生成退货单')
}
function rejectRefund(id: string) {
  const remark = window.prompt('驳回原因', '不符合售后条件') || undefined
  void run(() => api.rejectMallRefund(id, remark), '售后已驳回')
}

function approveBinding(id: string, customerId: string | null) {
  const input = window.prompt('绑定到的客户 ID（留空则按邀请码自动匹配）', customerId || bindingForm.customerId)
  if (input === null) return
  void run(() => api.approveMallBinding(id, input || undefined, bindingForm.remark || undefined), '会员绑定已通过')
}
function rejectBinding(id: string) {
  const remark = window.prompt('驳回原因', '资料不符') || undefined
  void run(() => api.rejectMallBinding(id, remark), '绑定申请已驳回')
}

function createInvite() {
  if (!inviteForm.customerId.trim()) {
    toast.error('请填写客户 ID')
    return
  }
  void run(
    () => api.createMallInvite({ customerId: inviteForm.customerId.trim(), remark: inviteForm.remark }),
    '邀请码已生成',
  ).then(() => {
    inviteForm.customerId = ''
    inviteForm.remark = ''
  })
}

function toggleInvite(r: Row) {
  const next = r.status === 'active' ? 'disabled' : 'active'
  void run(() => api.setMallInviteStatus(String(r.id), next), next === 'active' ? '邀请码已启用' : '邀请码已停用')
}

function createBanner() {
  if (!bannerForm.imageUrl.trim()) {
    toast.error('请填写图片地址')
    return
  }
  void run(
    () =>
      api.createMallBanner({
        title: bannerForm.title,
        imageUrl: bannerForm.imageUrl,
        linkUrl: bannerForm.linkUrl,
        sortNo: Number(bannerForm.sortNo) || 0,
        status: 'active',
      }),
    '轮播已新增',
  ).then(() => {
    bannerForm.title = ''
    bannerForm.imageUrl = ''
    bannerForm.linkUrl = ''
  })
}

function removeBanner(id: string) {
  if (!window.confirm('确认删除该轮播？')) return
  void run(() => api.deleteMallBanner(id), '轮播已删除')
}

function saveSettings() {
  void run(() => api.saveMallSettings(settingsDraft.value), '商城设置已保存')
}

watch(tab, () => {
  detail.value = null
  load()
})

onMounted(load)
</script>

<template>
  <div>
    <div class="card">
      <div class="card-head">
        <h3><Icon name="shopping-cart" :size="17" /> 订货商城后台</h3>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
      </div>
      <div class="card-body">
        <div class="seg">
          <button
            v-for="t in TABS" :key="t[0]" class="seg-btn"
            :class="{ active: tab === t[0] }" @click="tab = t[0]"
          >
            {{ t[1] }}
          </button>
        </div>

        <!-- 概览 -->
        <div v-if="tab === 'overview'" class="stat-grid">
          <div v-for="c in overviewCards" :key="c[0]" class="stat">
            <span class="stat-label">{{ c[0] }}</span>
            <b class="stat-value">{{ c[1] }}</b>
          </div>
        </div>

        <!-- 商城订单 -->
        <template v-else-if="tab === 'orders'">
          <div class="toolbar">
            <div class="search-box">
              <Icon name="search" :size="15" />
              <input v-model="ordersState.q" class="input" placeholder="搜索订单号 / 客户 / 收货人" @input="ordersState.page = 1; load()" />
            </div>
            <select v-model="ordersState.status" class="input" @change="ordersState.page = 1; load()">
              <option v-for="s in ORDER_STATUS" :key="s[0]" :value="s[0]">{{ s[1] }}</option>
            </select>
            <div class="spacer" />
            <span class="count">共 {{ orders.total }} 条</span>
          </div>
          <TableShell
            :rows="orders.list" :loading="loading" :page="orders.page" :pages="ordersPages"
            empty-title="暂无商城订单" @go="goOrders"
          >
            <template #head>
              <tr>
                <th>订单号</th><th>客户</th><th>金额</th><th>支付方式</th>
                <th>状态</th><th>下单时间</th><th class="nosort">操作</th>
              </tr>
            </template>
            <tr v-for="r in orders.list" :key="String(r.id)">
              <td>{{ r.orderNo }}</td>
              <td>{{ r.customerName }}</td>
              <td>{{ money(r.totalAmount) }}</td>
              <td>{{ r.payType === 'credit' ? '账期' : r.payType === 'offline' ? '线下' : '微信' }}</td>
              <td>
                <span class="badge" :class="orderBadge(r.status).cls">{{ orderBadge(r.status).text }}</span>
                <span v-if="r.creditOver === 'true'" class="badge badge-orange" style="margin-left: 4px">超授信</span>
              </td>
              <td>{{ r.createdAt }}</td>
              <td class="op">
                <a style="color: var(--accent)" @click="openDetail(String(r.id))">详情</a>
                <a v-if="r.status === 'pending_confirm'" style="color: var(--accent); margin-left: 8px" @click="confirmCredit(String(r.id))">确认账期</a>
                <a v-if="r.status === 'pending_pay'" style="color: var(--accent); margin-left: 8px" @click="confirmOffline(String(r.id))">确认收款</a>
                <a v-if="r.status === 'pending_pay' || r.status === 'pending_confirm'" style="color: var(--danger); margin-left: 8px" @click="closeOrder(String(r.id))">关闭</a>
              </td>
            </tr>
          </TableShell>
        </template>

        <!-- 支付流水 -->
        <template v-else-if="tab === 'payments'">
          <div class="toolbar">
            <div class="search-box">
              <Icon name="search" :size="15" />
              <input v-model="payState.q" class="input" placeholder="搜索商户单号 / 订单号 / 交易号" @input="payState.page = 1; load()" />
            </div>
            <select v-model="payState.status" class="input" @change="payState.page = 1; load()">
              <option value="">全部</option>
              <option value="pending">待支付</option>
              <option value="success">已支付</option>
              <option value="failed">失败</option>
              <option value="refunded">已退款</option>
            </select>
            <div class="spacer" />
            <span class="count">共 {{ payments.total }} 条</span>
          </div>
          <TableShell
            :rows="payments.list" :loading="loading" :page="payments.page" :pages="payPages"
            empty-title="暂无支付流水" @go="goPayments"
          >
            <template #head>
              <tr>
                <th>商户单号</th><th>订单号</th><th>渠道</th><th>金额</th>
                <th>状态</th><th>交易号</th><th>支付时间</th>
              </tr>
            </template>
            <tr v-for="r in payments.list" :key="String(r.id)">
              <td>{{ r.outTradeNo }}</td>
              <td>{{ r.orderNo }}</td>
              <td>{{ r.provider }}</td>
              <td>{{ money(r.amount) }}</td>
              <td><span class="badge" :class="payBadge(r.status).cls">{{ payBadge(r.status).text }}</span></td>
              <td>{{ r.transactionId || '—' }}</td>
              <td>{{ r.paidAt || '—' }}</td>
            </tr>
          </TableShell>
        </template>

        <!-- 售后 -->
        <template v-else-if="tab === 'afterSales'">
          <div class="toolbar">
            <select v-model="asState.status" class="input" @change="asState.page = 1; load()">
              <option value="">全部</option>
              <option value="applied">待审核</option>
              <option value="approved">已通过</option>
              <option value="rejected">已驳回</option>
            </select>
            <div class="spacer" />
            <span class="count">共 {{ afterSales.total }} 条</span>
          </div>
          <TableShell
            :rows="afterSales.list" :loading="loading" :page="afterSales.page" :pages="asPages"
            empty-title="暂无售后申请" @go="goAfterSales"
          >
            <template #head>
              <tr>
                <th>售后单号</th><th>订单号</th><th>客户</th><th>金额</th>
                <th>原因</th><th>状态</th><th class="nosort">操作</th>
              </tr>
            </template>
            <tr v-for="r in afterSales.list" :key="String(r.id)">
              <td>{{ r.refundNo }}</td>
              <td>{{ r.orderNo }}</td>
              <td>{{ r.customerName }}</td>
              <td>{{ money(r.amount) }}</td>
              <td>{{ r.reason }}</td>
              <td><span class="badge" :class="refundBadge(r.status).cls">{{ refundBadge(r.status).text }}</span></td>
              <td class="op">
                <template v-if="r.status === 'applied'">
                  <a style="color: var(--accent)" @click="approveRefund(String(r.id))">通过</a>
                  <a style="color: var(--danger); margin-left: 8px" @click="rejectRefund(String(r.id))">驳回</a>
                </template>
                <span v-else style="color: var(--text-4)">—</span>
              </td>
            </tr>
          </TableShell>
        </template>

        <!-- 会员绑定 -->
        <template v-else-if="tab === 'bindings'">
          <div class="toolbar">
            <select v-model="bindingStatus" class="input" @change="load()">
              <option value="">全部</option>
              <option value="pending">待审核</option>
              <option value="active">已绑定</option>
              <option value="disabled">已停用</option>
            </select>
            <input v-model="bindingForm.customerId" class="input" placeholder="审核时绑定的客户 ID（可留空）" />
            <div class="spacer" />
            <span class="count">共 {{ bindings.length }} 条</span>
          </div>
          <TableShell :rows="bindings" :loading="loading" empty-title="暂无绑定申请">
            <template #head>
              <tr>
                <th>会员</th><th>手机号</th><th>邀请码</th><th>绑定客户</th>
                <th>状态</th><th>申请时间</th><th class="nosort">操作</th>
              </tr>
            </template>
            <tr v-for="r in bindings" :key="String(r.id)">
              <td>{{ r.nickname || '—' }}</td>
              <td>{{ r.phone || '—' }}</td>
              <td>{{ r.inviteCode || '—' }}</td>
              <td>{{ r.customerName || r.customerId || '—' }}</td>
              <td><span class="badge" :class="bindBadge(r.status).cls">{{ bindBadge(r.status).text }}</span></td>
              <td>{{ r.createdAt }}</td>
              <td class="op">
                <template v-if="r.status !== 'active'">
                  <a style="color: var(--accent)" @click="approveBinding(String(r.id), r.customerId)">通过</a>
                  <a style="color: var(--danger); margin-left: 8px" @click="rejectBinding(String(r.id))">驳回</a>
                </template>
                <span v-else style="color: var(--text-4)">—</span>
              </td>
            </tr>
          </TableShell>
        </template>

        <!-- 邀请码 -->
        <template v-else-if="tab === 'invites'">
          <div class="toolbar">
            <input v-model="inviteForm.customerId" class="input" placeholder="客户 ID（必填）" />
            <input v-model="inviteForm.remark" class="input" placeholder="备注（可空）" />
            <button class="btn btn-sm btn-primary" @click="createInvite"><Icon name="plus" :size="14" /> 生成邀请码</button>
          </div>
          <TableShell :rows="invites" :loading="loading" empty-title="暂无邀请码">
            <template #head>
              <tr>
                <th>邀请码</th><th>客户</th><th>归属业务员</th><th>状态</th>
                <th>备注</th><th>创建时间</th><th class="nosort">操作</th>
              </tr>
            </template>
            <tr v-for="r in invites" :key="String(r.id)">
              <td><code>{{ r.code }}</code></td>
              <td>{{ r.customerName || r.customerId }}</td>
              <td>{{ r.ownerName || '—' }}</td>
              <td>
                <span class="badge" :class="r.status === 'active' ? 'badge-green' : 'badge-gray'">
                  {{ r.status === 'active' ? '启用' : '停用' }}
                </span>
              </td>
              <td>{{ r.remark || '—' }}</td>
              <td>{{ r.createdAt }}</td>
              <td class="op">
                <a style="color: var(--accent)" @click="toggleInvite(r)">{{ r.status === 'active' ? '停用' : '启用' }}</a>
              </td>
            </tr>
          </TableShell>
        </template>

        <!-- 轮播 -->
        <template v-else-if="tab === 'banners'">
          <div class="toolbar">
            <input v-model="bannerForm.title" class="input" placeholder="标题" />
            <input v-model="bannerForm.imageUrl" class="input" placeholder="图片地址（必填）" />
            <input v-model="bannerForm.linkUrl" class="input" placeholder="跳转链接（可空）" />
            <input v-model.number="bannerForm.sortNo" class="input" type="number" placeholder="排序" style="max-width: 90px" />
            <button class="btn btn-sm btn-primary" @click="createBanner"><Icon name="plus" :size="14" /> 新增</button>
          </div>
          <TableShell :rows="banners" :loading="loading" empty-title="暂无轮播">
            <template #head>
              <tr><th>标题</th><th>图片</th><th>链接</th><th>排序</th><th>状态</th><th class="nosort">操作</th></tr>
            </template>
            <tr v-for="r in banners" :key="String(r.id)">
              <td>{{ r.title || '—' }}</td>
              <td class="ellipsis">{{ r.imageUrl }}</td>
              <td class="ellipsis">{{ r.linkUrl || '—' }}</td>
              <td>{{ r.sortNo }}</td>
              <td>
                <span class="badge" :class="r.status === 'active' ? 'badge-green' : 'badge-gray'">
                  {{ r.status === 'active' ? '展示中' : '已下线' }}
                </span>
              </td>
              <td class="op"><a style="color: var(--danger)" @click="removeBanner(String(r.id))">删除</a></td>
            </tr>
          </TableShell>
        </template>

        <!-- 设置 -->
        <template v-else-if="tab === 'settings'">
          <div class="form-grid">
            <div v-for="r in settings" :key="String(r.key)" class="field">
              <label>{{ r.remark || r.key }}</label>
              <input v-model="settingsDraft[String(r.key)]" class="input" />
              <small class="hint">{{ r.key }}</small>
            </div>
          </div>
          <div class="toolbar" style="margin-top: 12px">
            <button class="btn btn-primary btn-sm" @click="saveSettings"><Icon name="check" :size="14" /> 保存设置</button>
          </div>
        </template>
      </div>
    </div>

    <!-- 订单详情 -->
    <div v-if="detail" class="card" style="margin-top: 14px">
      <div class="card-head">
        <h3><Icon name="file-text" :size="17" /> 订单详情 {{ detail.order_no }}</h3>
        <button class="btn btn-sm btn-ghost" @click="detail = null"><Icon name="x" :size="15" /> 关闭</button>
      </div>
      <div class="card-body">
        <div class="kv">
          <div><span>客户</span><b>{{ detail.customer_name }}</b></div>
          <div><span>状态</span><b>{{ detail.status }}</b></div>
          <div><span>金额</span><b>{{ money(String(detail.total_amount ?? '')) }}</b></div>
          <div><span>支付方式</span><b>{{ detail.pay_type }}</b></div>
          <div><span>收货人</span><b>{{ detail.receiver }} {{ detail.receiver_phone }}</b></div>
          <div><span>地址</span><b>{{ detail.address }}</b></div>
        </div>
        <table class="list" style="margin-top: 12px">
          <thead><tr><th>商品</th><th>数量</th><th>单价</th><th>金额</th></tr></thead>
          <tbody>
            <tr v-for="(it, i) in detailItems" :key="i">
              <td>{{ it.productName }} {{ it.spec || '' }}</td>
              <td>{{ it.qty }} {{ it.unit }}</td>
              <td>{{ money(it.price) }}</td>
              <td>{{ money(it.amount) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.seg { margin-bottom: 14px; }
.stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; }
.stat {
  position: relative; border: 1px solid var(--border);
  border-radius: var(--radius-lg); padding: 14px 16px;
  display: flex; flex-direction: column; gap: 4px; background: var(--surface);
}
.stat::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 2px; background: var(--primary);
}
.stat-label { font-size: 11.5px; color: var(--text-3); letter-spacing: .02em; }
.stat-value {
  font-family: var(--font-mono); font-size: 21px; font-weight: 700;
  color: var(--text); letter-spacing: -.02em;
}
.nosort { cursor: default; }
.ellipsis { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-family: var(--font-mono); font-size: 12px; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field label { font-size: 11.5px; color: var(--text-3); font-weight: 600; letter-spacing: .02em; }
.hint { color: var(--text-4); font-size: 11px; font-family: var(--font-mono); }
.kv { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 8px; font-size: 13px; }
.kv span { color: var(--text-4); margin-right: 6px; }
.kv b { font-family: var(--font-mono); font-weight: 600; }
</style>

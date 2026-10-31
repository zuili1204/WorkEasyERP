/** 商城状态文案与标签色，统一维护避免各页面各写一套 */

const ORDER_STATUS: Record<string, string> = {
  pending_pay: '待支付',
  pending_confirm: '待确认',
  pending_ship: '待发货',
  shipped: '已发货',
  completed: '已完成',
  cancelled: '已取消',
  closed: '已关闭',
}

const ORDER_STATUS_COLOR: Record<string, string> = {
  pending_pay: '#b23a2e',
  pending_confirm: '#b8842a',
  pending_ship: '#b8842a',
  shipped: '#3a6772',
  completed: '#3f7a58',
  cancelled: '#8c9693',
  closed: '#8c9693',
}

const PAY_TYPE_LABEL: Record<string, string> = {
  online: '在线支付',
  wechat: '微信支付',
  credit: '账期支付',
  offline: '线下打款',
}

const REFUND_STATUS: Record<string, string> = {
  applied: '待审核',
  approved: '已通过',
  rejected: '已驳回',
  refunded: '已退款',
}

const AR_STATUS: Record<string, string> = {
  open: '未结清',
  partial: '部分核销',
  settled: '已结清',
  closed: '已关闭',
}

export function statusText(s?: string | null): string {
  if (!s) return '-'
  return ORDER_STATUS[s] || s
}

export function statusColor(s?: string | null): string {
  if (!s) return '#8c9693'
  return ORDER_STATUS_COLOR[s] || '#616b68'
}

export function payTypeText(s?: string | null): string {
  if (!s) return '-'
  return PAY_TYPE_LABEL[s] || s
}

export function refundStatusText(s?: string | null): string {
  if (!s) return '-'
  return REFUND_STATUS[s] || s
}

export function arStatusText(s?: string | null): string {
  if (!s) return '-'
  return AR_STATUS[s] || s
}

/** 订单列表状态筛选 tab */
export const ORDER_TABS = [
  { key: '', label: '全部' },
  { key: 'pending_pay', label: '待支付' },
  { key: 'pending_ship', label: '待发货' },
  { key: 'shipped', label: '已发货' },
  { key: 'completed', label: '已完成' },
]

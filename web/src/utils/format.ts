/** 统一格式化：收口各视图手写的 replace/slice/toFixed，避免口径不一致 */

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function toDate(v?: string | number | null): Date | null {
  if (v == null || v === '') return null
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? null : d
}

/** ISO → 本地 YYYY-MM-DD */
export function fmtDate(v?: string | null): string {
  const d = toDate(v)
  if (!d) return '—'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** ISO → 本地 YYYY-MM-DD HH:mm */
export function fmtDateTime(v?: string | null): string {
  const d = toDate(v)
  if (!d) return '—'
  return `${fmtDate(v)} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 带秒：审计日志等需要精确时间的场景 */
export function fmtDateTimeSec(v?: string | null): string {
  const d = toDate(v)
  if (!d) return '—'
  return `${fmtDateTime(v)}:${pad(d.getSeconds())}`
}

/** 本地 HH:mm（班次时间等） */
export function fmtTime(v?: string | null): string {
  const d = toDate(v)
  if (!d) return '—'
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 金额：千分位 + 可选币种符号 */
export function fmtMoney(v?: string | number | null, currency?: string, digits = 2): string {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  const body = n.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
  return currency ? `${currency} ${body}` : body
}

/** 数量：不带货币语义，精度可配 */
export function fmtQty(v?: string | number | null, digits = 3): string {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return n.toFixed(digits)
}

/** datetime-local / date 控件所需的本地值（YYYY-MM-DDTHH:mm）；支持 Date 或 ISO 串 */
export function toLocalInput(v?: string | Date | null): string {
  const d = v instanceof Date ? v : toDate(v)
  if (!d || Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 紧凑金额：1.2万 / 3.4亿，用于图表坐标轴等窄空间 */
export function fmtCompact(v?: string | number | null): string {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  const abs = Math.abs(n)
  if (abs >= 1e8) return `${(n / 1e8).toFixed(1)}亿`
  if (abs >= 1e4) return `${(n / 1e4).toFixed(1)}万`
  return String(Math.round(n))
}

/** 本地日期 → 提交后端的 UTC ISO 串（避免跨时区偏移一天） */
export function toUtcIso(localValue: string): string | undefined {
  const d = toDate(localValue)
  return d ? d.toISOString() : undefined
}

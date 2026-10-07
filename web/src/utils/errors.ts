/**
 * 错误信息净化：任何要展示给用户的文案都必须经过这里。
 * 目标：界面上永不出现堆栈、SQL、类名、UUID、字段名等技术细节，
 * 也避免把后端的内部错误原文当作"报错弹窗"抛给用户。
 */

/** 技术细节特征词（命中即降级为通用文案） */
const TECHNICAL_PATTERNS = [
  'Exception',
  'Error:',
  'Stack',
  'Trace',
  String.raw`at\s+[\w.$]+\(`,
  String.raw`SELECT\s`,
  String.raw`INSERT\s`,
  String.raw`UPDATE\s`,
  String.raw`DELETE\s`,
  String.raw`FROM\s+\w+`,
  String.raw`WHERE\s`,
  'ILIKE',
  'jdbc',
  String.raw`\bsql\b`,
  'psycopg',
  String.raw`org\.`,
  String.raw`java\.`,
  String.raw`kotlin\.`,
  'springframework',
  'hibernate',
  'Instant',
  'LocalDate',
  'LocalDateTime',
  'UUID',
  'BigDecimal',
  'deserialize',
  String.raw`\bparse\b`,
  'constraint',
  'violates',
  String.raw`null\s+value`,
  String.raw`invalid\s+input\s+syntax`,
  String.raw`[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}`,
]

const TECHNICAL = new RegExp(`(${TECHNICAL_PATTERNS.join('|')})`, 'i')

const SERVER_DOWN = '服务暂时不可用，请稍后再试'
const GENERIC = '操作未成功，请稍后重试'
const NETWORK = '网络异常，请检查连接后重试'
const NOT_FOUND = '未找到相关记录'

/**
 * @param raw    后端返回的原始 msg
 * @param status HTTP 状态码
 */
export function cleanMsg(raw: string | undefined | null, status?: number): string {
  const s = (raw ?? '').trim()
  if (status && status >= 500) return SERVER_DOWN
  if (!s) return status === 404 ? NOT_FOUND : GENERIC
  if (TECHNICAL.test(s)) return GENERIC
  // 过长文案通常是内部信息拼接，截断避免出现半截技术内容
  return s.length > 120 ? `${s.slice(0, 120)}…` : s
}

/** 从任意异常中安全提取可展示文案 */
export function errMsg(e: unknown, fallback = GENERIC): string {
  const err = e as {
    response?: { status?: number; data?: { msg?: string } }
    code?: string
    message?: string
  }
  if (!err?.response) {
    // 无响应：网络中断 / 超时 / 跨域
    return err?.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : NETWORK
  }
  return cleanMsg(err.response.data?.msg, err.response.status) || fallback
}

export function isAuthError(e: unknown): boolean {
  return (e as { response?: { status?: number } })?.response?.status === 401
}

export function isForbidden(e: unknown): boolean {
  return (e as { response?: { status?: number } })?.response?.status === 403
}

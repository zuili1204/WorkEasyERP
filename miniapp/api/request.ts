import { session, clearAll } from '../stores/session'

/**
 * 统一请求封装：
 * - 自动带 token（平台会员 / 租户域两种 realm）与 X-Tenant-Code
 * - 后端统一响应 { code, msg, data }，业务码非 0 一律 reject
 * - 401 清空登录态并回到登录页，避免停在空白页
 * - 网络层失败给出可读提示（弱网/断网有明确反馈）
 */
const BASE_URL = (import.meta.env.VITE_API_BASE as string) || 'http://localhost:18080/api'

export type Realm = 'none' | 'platform' | 'tenant'

export interface ApiResp<T> {
  code: string
  msg: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  data?: unknown
  realm?: Realm
  /** 静默模式：404/业务提示不弹 toast（用于首页公告等非关键数据） */
  silent?: boolean
}

function currentToken(realm: Realm): string {
  if (realm === 'none') return ''
  if (realm === 'platform') return session.platformToken
  return session.mallToken || session.platformToken
}

let redirecting = false
function toLogin() {
  if (redirecting) return
  redirecting = true
  clearAll()
  uni.reLaunch({
    url: '/pages/login/index',
    complete: () => {
      redirecting = false
    },
  })
}

export function request<T>(opts: RequestOptions): Promise<T> {
  const realm: Realm = opts.realm || 'tenant'
  const token = currentToken(realm)
  const header: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) header.Authorization = `Bearer ${token}`
  if (session.tenantCode) header['X-Tenant-Code'] = session.tenantCode

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: BASE_URL + opts.url,
      method: opts.method || 'GET',
      data: opts.data as Record<string, unknown> | undefined,
      header,
      timeout: 15000,
      success: (res) => {
        const status = res.statusCode
        const body = res.data as ApiResp<T> | undefined
        if (status === 401) {
          if (!opts.silent) uni.showToast({ title: '登录已过期', icon: 'none' })
          toLogin()
          reject(new Error('登录已过期'))
          return
        }
        if (status !== 200 || !body || typeof body.code !== 'string') {
          const msg = body?.msg || '服务繁忙，请稍后重试'
          if (!opts.silent) uni.showToast({ title: msg, icon: 'none' })
          reject(new Error(msg))
          return
        }
        if (body.code !== '0') {
          if (!opts.silent) uni.showToast({ title: body.msg || '操作失败', icon: 'none' })
          reject(new Error(body.msg || '操作失败'))
          return
        }
        resolve(body.data)
      },
      fail: () => {
        if (!opts.silent) uni.showToast({ title: '网络异常，请检查网络后重试', icon: 'none' })
        reject(new Error('网络异常'))
      },
    })
  })
}

/** 带日期/金额的展示格式化，统一入口避免各页面各写一套 */
export function money(v: string | number | null | undefined): string {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = Number(v)
  if (!isFinite(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function qtyText(v: string | number | null | undefined): string {
  const n = Number(v || 0)
  return isFinite(n) ? String(Number(n.toFixed(3))) : '0'
}

import { reactive } from 'vue'

const K_PLATFORM = 'we_mall_platform_token'
const K_MALL = 'we_mall_token'
const K_TENANT = 'we_mall_tenant_code'
const K_TENANT_NAME = 'we_mall_tenant_name'
const K_CUSTOMER = 'we_mall_customer'
const K_NICKNAME = 'we_mall_nickname'

export interface CustomerInfo {
  id: string
  code?: string
  name?: string
  level?: string
}

export const session = reactive({
  platformToken: '',
  mallToken: '',
  tenantCode: '',
  tenantName: '',
  nickname: '',
  customer: null as CustomerInfo | null,
})

function str(key: string): string {
  const v = uni.getStorageSync(key)
  return typeof v === 'string' ? v : ''
}

export function restore() {
  session.platformToken = str(K_PLATFORM)
  session.mallToken = str(K_MALL)
  session.tenantCode = str(K_TENANT)
  session.tenantName = str(K_TENANT_NAME)
  session.nickname = str(K_NICKNAME)
  const c = uni.getStorageSync(K_CUSTOMER)
  session.customer = c && typeof c === 'object' ? (c as CustomerInfo) : null
}

export function setPlatformToken(token: string, nickname?: string) {
  session.platformToken = token
  uni.setStorageSync(K_PLATFORM, token)
  if (nickname) {
    session.nickname = nickname
    uni.setStorageSync(K_NICKNAME, nickname)
  }
}

/** 进入/切换租户：清掉旧租户的域令牌与缓存，保证购物车/订单互不可见 */
export function setTenant(code: string, name: string) {
  session.tenantCode = code
  session.tenantName = name
  session.mallToken = ''
  session.customer = null
  uni.setStorageSync(K_TENANT, code)
  uni.setStorageSync(K_TENANT_NAME, name)
  uni.removeStorageSync(K_MALL)
  uni.removeStorageSync(K_CUSTOMER)
  uni.removeStorageSync('we_mall_selected_address')
}

export function setMallToken(token: string, customer: CustomerInfo | null) {
  session.mallToken = token
  session.customer = customer
  uni.setStorageSync(K_MALL, token)
  uni.setStorageSync(K_CUSTOMER, customer || '')
}

export function clearTenant() {
  session.tenantCode = ''
  session.tenantName = ''
  session.mallToken = ''
  session.customer = null
  uni.removeStorageSync(K_TENANT)
  uni.removeStorageSync(K_TENANT_NAME)
  uni.removeStorageSync(K_MALL)
  uni.removeStorageSync(K_CUSTOMER)
}

export function clearAll() {
  session.platformToken = ''
  session.nickname = ''
  clearTenant()
  uni.removeStorageSync(K_PLATFORM)
  uni.removeStorageSync(K_NICKNAME)
}

export function isJoined(): boolean {
  return !!session.mallToken && !!session.tenantCode
}

import { request, type PageResult } from './request'

export interface TenantOption {
  tenantCode: string
  name: string
  joined: boolean
  bindStatus?: string | null
  customerId?: string | null
}

export interface MemberInfo {
  id: string
  nickname?: string
  phone?: string
  status: string
}

export interface ProductItem {
  productId: string
  sku?: string
  name: string
  onlineTitle?: string
  spec?: string
  brand?: string
  unit?: string
  auxUnit?: string
  convertRate?: string | number
  salePrice?: string | number
  minOrderQty?: string | number
  stepQty?: string | number
  categoryId?: string
  categoryName?: string
  imageUrl?: string
  price?: string | number
  listPrice?: string | number
  source?: string
  availableQty?: string | number
}

export interface ProductDetail extends ProductItem {
  onlineDesc?: string
  origin?: string
  taxRate?: string | number
  images?: { id: string; url: string; isPrimary?: string }[]
}

export interface CartItem {
  id: string
  productId: string
  name: string
  spec?: string
  unit?: string
  imageUrl?: string
  qty: string | number
  price: string | number
  amount: string | number
  availableQty: string | number
  minOrderQty?: string | number
  stepQty?: string | number
  stockEnough: boolean
  online: boolean
}

export interface AddressItem {
  id: string
  receiver: string
  phone: string
  province?: string
  city?: string
  district?: string
  detail: string
  isDefault?: string | boolean
}

export interface OrderRow {
  id: string
  orderNo: string
  status: string
  payType: string
  totalQty?: string | number
  totalAmount?: string | number
  itemCount?: number
  summary?: string
  createdAt?: string
  paidAt?: string
  shippedAt?: string
}

export interface OrderLine {
  id?: string
  lineNo?: number
  productId: string
  productName: string
  spec?: string
  unit?: string
  qty: string | number
  price: string | number
  taxRate?: string | number
  amount: string | number
  imageUrl?: string
}

export interface OrderDetail extends OrderRow {
  receiver?: string
  receiverPhone?: string
  address?: string
  remark?: string
  creditOver?: boolean | string
  salesOrderId?: string
  outTradeNo?: string
  completedAt?: string
  salesOrderStatus?: string
  items?: OrderLine[]
  payments?: { paymentNo?: string; outTradeNo?: string; provider?: string; amount?: string; status?: string; paidAt?: string }[]
}

export interface PayOption {
  type: string
  label: string
  enabled: boolean
}

export interface OrderPreview {
  lines: (OrderLine & { name?: string; netAmount?: string; taxAmount?: string })[]
  totalQty: string | number
  totalNet: string | number
  totalTax: string | number
  totalAmount: string | number
  warehouseId: string
  credit?: Record<string, unknown>
  payType?: string
  payOptions?: PayOption[]
  address?: { receiver?: string; phone?: string; province?: string; city?: string; district?: string; detail?: string } | null
  minOrderAmount?: string | number
}

export interface PublicConfig {
  tenantCode?: string
  tenantName?: string
  notice?: string
  servicePhone?: string
  creditEnabled?: boolean
  wechatEnabled?: boolean
  autoConfirmDays?: string
  minOrderAmount?: string
}

export interface BannerItem {
  id: string
  title?: string
  imageUrl: string
  linkUrl?: string
}

export interface ArItem {
  id: string
  bizType?: string
  bizNo?: string
  amount?: string
  settledAmount?: string
  remainAmount?: string
  dueDate?: string
  status?: string
  overdueDays?: number
}

export interface ArSummary {
  totalRemain: string | number
  overdueRemain: string | number
  aging: { bucket: string; amount: string | number }[]
  items: ArItem[]
  credit?: Record<string, unknown>
}

export interface RefundRow {
  id: string
  refundNo: string
  orderNo: string
  amount: string | number
  reason: string
  status: string
  createdAt?: string
}

/** 平台会员登录：code 换平台 openid（后端 mock 模式直接签发平台令牌） */
export const wxLogin = (code: string, nickname?: string, phone?: string) =>
  request<{ token: string; member: MemberInfo; mock: boolean }>({
    url: '/mall/auth/wx-login',
    method: 'POST',
    data: { code, nickname, phone },
    realm: 'none',
  })

export const bindPhone = (phone: string) =>
  request<MemberInfo>({ url: '/mall/auth/phone', method: 'POST', data: { phone }, realm: 'platform' })

export const myTenants = () =>
  request<TenantOption[]>({ url: '/mall/tenants', realm: 'platform' })

export const joinTenant = (tenantCode: string, inviteCode?: string) =>
  request<{ tenantCode: string; status: string; customerId?: string }>({
    url: '/mall/auth/join',
    method: 'POST',
    data: { tenantCode, inviteCode },
    realm: 'platform',
  })

export const tenantToken = (tenantCode: string) =>
  request<{ token: string; tenantCode: string; tenantName: string; customer: MemberInfo & { code?: string; name?: string; level?: string } }>({
    url: '/mall/auth/token',
    method: 'POST',
    data: { tenantCode },
    realm: 'platform',
  })

export const me = () => request<MemberInfo>({ url: '/mall/me', realm: 'platform' })

export const publicConfig = () => request<PublicConfig>({ url: '/mall/public/config', realm: 'none', silent: true })

export const banners = () => request<BannerItem[]>({ url: '/mall/public/banners', realm: 'none', silent: true })

export const products = (q: string | undefined, categoryId: string | undefined, page: number, size: number) =>
  request<PageResult<ProductItem>>({
    url: '/mall/products',
    data: { q, categoryId, page, size },
    realm: 'tenant',
  })

export const productDetail = (id: string) =>
  request<ProductDetail>({ url: `/mall/products/${id}`, realm: 'tenant' })

export const categories = () =>
  request<{ id: string; name: string; productCount: string }[]>({ url: '/mall/categories', realm: 'tenant' })

export const cart = () => request<CartItem[]>({ url: '/mall/cart', realm: 'tenant' })

export const addCart = (productId: string, qty: string | number) =>
  request<boolean>({ url: '/mall/cart', method: 'POST', data: { productId, qty }, realm: 'tenant' })

export const updateCartQty = (id: string, qty: string | number) =>
  request<boolean>({ url: `/mall/cart/${id}`, method: 'PATCH', data: { qty }, realm: 'tenant' })

export const removeCart = (id: string) =>
  request<boolean>({ url: `/mall/cart/${id}`, method: 'DELETE', realm: 'tenant' })

export const clearCart = () =>
  request<boolean>({ url: '/mall/cart', method: 'DELETE', realm: 'tenant' })

export const addresses = () => request<AddressItem[]>({ url: '/mall/addresses', realm: 'tenant' })

export const createAddress = (body: Record<string, unknown>) =>
  request<{ id: string }>({ url: '/mall/addresses', method: 'POST', data: body, realm: 'tenant' })

export const updateAddress = (id: string, body: Record<string, unknown>) =>
  request<boolean>({ url: `/mall/addresses/${id}`, method: 'PATCH', data: body, realm: 'tenant' })

export const deleteAddress = (id: string) =>
  request<boolean>({ url: `/mall/addresses/${id}`, method: 'DELETE', realm: 'tenant' })

export const orderPreview = (items: { productId: string; qty: string | number }[], addressId?: string, payType?: string) =>
  request<OrderPreview>({
    url: '/mall/orders/preview',
    method: 'POST',
    data: { items, addressId, payType },
    realm: 'tenant',
  })

export const createOrder = (items: { productId: string; qty: string | number }[], addressId: string, payType: string, remark?: string) =>
  request<OrderDetail>({
    url: '/mall/orders',
    method: 'POST',
    data: { items, addressId, payType, remark },
    realm: 'tenant',
  })

export const orders = (status: string | undefined, page: number, size: number) =>
  request<PageResult<OrderRow>>({ url: '/mall/orders', data: { status, page, size }, realm: 'tenant' })

export const orderDetail = (id: string) =>
  request<OrderDetail>({ url: `/mall/orders/${id}`, realm: 'tenant' })

export const cancelOrder = (id: string) =>
  request<void>({ url: `/mall/orders/${id}/cancel`, method: 'POST', realm: 'tenant' })

export const confirmReceipt = (id: string) =>
  request<void>({ url: `/mall/orders/${id}/confirm`, method: 'POST', realm: 'tenant' })

export const payJsapi = (orderId: string) =>
  request<Record<string, string>>({ url: '/mall/pay/jsapi', method: 'POST', data: { orderId }, realm: 'tenant' })

/** mock 支付：仅后端 workeasy.mall.wx.mock=true 时可用，等价于已验签回调 */
export const mockPay = (orderId: string) =>
  request<Record<string, unknown>>({ url: `/mall/pay/mock/${orderId}`, method: 'POST', realm: 'tenant' })

export const statements = () => request<ArItem[]>({ url: '/mall/statements', realm: 'tenant' })

export const arSummary = () => request<ArSummary>({ url: '/mall/ar', realm: 'tenant' })

export const afterSales = () => request<RefundRow[]>({ url: '/mall/after-sales', realm: 'tenant' })

export const applyAfterSales = (orderId: string, reason: string, amount?: string | number) =>
  request<RefundRow>({
    url: '/mall/after-sales',
    method: 'POST',
    data: { orderId, reason, amount },
    realm: 'tenant',
  })

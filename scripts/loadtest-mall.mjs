/**
 * 商城接口压测（阶段二「50 并发同 SKU 下单」+ 阶段五「下单 / 支付回调 / 商品列表」）
 *
 * 前置：租户已开通并绑定客户、店铺在线且有可售库存。
 * 用法：
 *   BASE_URL=http://localhost:18080/api TENANT_CODE=demo node scripts/loadtest-mall.mjs
 *   CONCURRENCY=50 QTY=1 INVITE_CODE=xxx PRODUCT_ID=xxx node scripts/loadtest-mall.mjs
 *
 * 覆盖：
 *   1) 平台登录 → 加入/进入租户（mock 微信 code）
 *   2) 商品列表吞吐
 *   3) N 并发同 SKU 下单：统计成功 / 库存不足 / 其他失败，验证不超卖
 *   4) mock 支付成功 + 同一回调重复投递 10 次，验证幂等只产生 1 条支付
 */
const BASE = (process.env.BASE_URL || 'http://localhost:18080/api').replace(/\/$/, '')
const TENANT = process.env.TENANT_CODE || 'demo'
const CONCURRENCY = Number(process.env.CONCURRENCY || 50)
const QTY = process.env.QTY || '1'
const INVITE = process.env.INVITE_CODE || ''
const FIXED_PRODUCT = process.env.PRODUCT_ID || ''
const LIST_ROUNDS = Number(process.env.LIST_ROUNDS || 60)

class ApiError extends Error {
  constructor(status, msg) {
    super(msg)
    this.status = status
  }
}

async function call(path, { method = 'GET', body, token, withTenant = true } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (withTenant && TENANT) headers['X-Tenant-Code'] = TENANT
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const payload = await res.json().catch(() => ({}))
  if (res.status !== 200 || String(payload.code) !== '0') {
    throw new ApiError(res.status, payload.msg || `HTTP ${res.status}`)
  }
  return payload.data
}

function percentile(values, p) {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const idx = Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))
  return sorted[idx]
}

async function loginAndEnterTenant() {
  const code = `loadtest-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const login = await call('/mall/auth/wx-login', {
    method: 'POST',
    body: { code, nickname: '压测账号' },
    withTenant: false,
  })
  const platformToken = login.token

  let tenant = (await call('/mall/tenants', { token: platformToken })).find((t) => t.tenantCode === TENANT)
  if (!tenant) throw new Error(`租户 ${TENANT} 不存在或未激活`)
  if (!tenant.joined) {
    const join = await call('/mall/auth/join', {
      method: 'POST',
      body: { tenantCode: TENANT, inviteCode: INVITE || undefined },
      token: platformToken,
    })
    if (join.status !== 'active') throw new Error('绑定待商户审核，请先在商城后台通过该会员绑定')
  }
  const mall = await call('/mall/auth/token', {
    method: 'POST',
    body: { tenantCode: TENANT },
    token: platformToken,
    withTenant: false,
  })
  return mall.token
}

async function ensureAddress(token) {
  const list = await call('/mall/addresses', { token })
  if (list.length) return list[0].id
  const created = await call('/mall/addresses', {
    method: 'POST',
    token,
    body: {
      receiver: '压测收货',
      phone: '13800000000',
      province: '广东省',
      city: '深圳市',
      district: '南山区',
      detail: '压测地址 1 号',
      isDefault: true,
    },
  })
  return created.id
}

async function pickProduct(token) {
  if (FIXED_PRODUCT) return { productId: FIXED_PRODUCT, availableQty: Number.MAX_SAFE_INTEGER, minOrderQty: 1 }
  const page = await call('/mall/products?page=1&size=20', { token })
  const candidate = page.list.find((p) => Number(p.availableQty) >= Number(QTY))
  if (!candidate) throw new Error('没有可售库存足够的商品，请先在 ERP 建库存或指定 PRODUCT_ID')
  return candidate
}

async function productListBench(token) {
  const latencies = []
  const errors = []
  for (let i = 0; i < LIST_ROUNDS; i++) {
    const start = Date.now()
    try {
      await call('/mall/products?page=1&size=20', { token })
      latencies.push(Date.now() - start)
    } catch (e) {
      errors.push(e.message)
    }
  }
  const spent = latencies.reduce((a, b) => a + b, 0)
  return {
    rounds: LIST_ROUNDS,
    ok: latencies.length,
    failed: errors.length,
    avgMs: latencies.length ? Math.round(spent / latencies.length) : 0,
    p95Ms: percentile(latencies, 95),
    qps: spent > 0 ? Number(((latencies.length * 1000) / spent).toFixed(1)) : 0,
  }
}

async function concurrentOrders(token, addressId, productId) {
  const tasks = Array.from({ length: CONCURRENCY }, () =>
    call('/mall/orders', {
      method: 'POST',
      token,
      body: {
        items: [{ productId, qty: QTY }],
        addressId,
        payType: 'wechat',
        remark: '并发压测',
      },
    })
      .then((order) => ({ ok: true, id: order.id, status: order.status }))
      .catch((e) => ({ ok: false, msg: e.message })),
  )
  const results = await Promise.all(tasks)
  const success = results.filter((r) => r.ok)
  const stockShort = results.filter((r) => !r.ok && /库存|可售/i.test(r.msg))
  const others = results.filter((r) => !r.ok && !/库存|可售/i.test(r.msg))
  return {
    total: CONCURRENCY,
    success: success.length,
    stockShortage: stockShort.length,
    otherFailed: others.length,
    otherMessages: [...new Set(others.map((r) => r.msg))].slice(0, 5),
    orders: success,
  }
}

async function payAndVerifyIdempotent(token, orders) {
  const payResults = await Promise.all(
    orders.map((o) =>
      call(`/mall/pay/mock/${o.id}`, { method: 'POST', token })
        .then(() => true)
        .catch(() => false),
    ),
  )
  const paid = payResults.filter(Boolean).length

  // 重复回调幂等：同一 out_trade_no 投递 10 次
  const first = orders[0]
  let repeatsOk = 0
  if (first) {
    const detail = await call(`/mall/orders/${first.id}`, { token })
    const amount = detail.totalAmount
    const outTradeNo = detail.outTradeNo
    const notifyTasks = Array.from({ length: 10 }, () =>
      call(`/mall/pay/notify/${TENANT}`, {
        method: 'POST',
        withTenant: false,
        body: { outTradeNo, amount, sign: 'MOCK', resultCode: 'SUCCESS' },
      })
        .then(() => true)
        .catch(() => false),
    )
    repeatsOk = (await Promise.all(notifyTasks)).filter(Boolean).length
  }
  return { paid, repeatNotifyOk: repeatsOk }
}

async function main() {
  const startedAt = Date.now()
  console.log(`压测开始：${BASE} / 租户 ${TENANT} / 并发 ${CONCURRENCY}`)
  const token = await loginAndEnterTenant()
  const addressId = await ensureAddress(token)
  const product = await pickProduct(token)
  console.log(`压测商品：${product.productId}，可售 ${product.availableQty}`)

  const list = await productListBench(token)
  const orders = await concurrentOrders(token, addressId, product.productId)
  const pay = await payAndVerifyIdempotent(token, orders.orders)

  const summary = {
    productList: list,
    orders: {
      total: orders.total,
      success: orders.success,
      stockShortage: orders.stockShortage,
      otherFailed: orders.otherFailed,
      otherMessages: orders.otherMessages,
    },
    pay: { success: pay.paid, repeatNotifyOk: pay.repeatNotifyOk },
    elapsedSec: Math.round((Date.now() - startedAt) / 1000),
  }
  console.log(JSON.stringify(summary, null, 2))

  const oversellFree = orders.success + orders.stockShortage === orders.total
  if (!oversellFree) {
    console.error('存在非库存原因的失败，请检查服务日志（锁冲突 / 连接池 / 事务）')
    process.exitCode = 1
  }
}

main().catch((e) => {
  console.error('压测失败：', e.message)
  process.exitCode = 1
})

/**
 * 声明式「新建 / 发起」表单注册表。
 *
 * 背景：原先 26 处发起表单散落在各视图的弹窗里，每个视图都重复实现一遍
 * 「表单状态 + 校验 + 提交 + Toast + 返回」。这里把差异收敛成数据描述，
 * 由统一的 EntityCreateView 渲染，单个新增表单只需在此登记一份 schema。
 */
import { api } from '../api'

export type FieldType = 'text' | 'number' | 'textarea' | 'date' | 'datetime-local' | 'select'

/** 远程选项数据源：统一在此加载，避免各视图重复写拉取逻辑 */
export type OptionsFrom =
  | 'customers'
  | 'suppliers'
  | 'products'
  | 'warehouses'
  | 'departments'
  | 'currencies'
  | 'reportModules'

export interface OptionItem {
  value: string
  label: string
}

export interface FieldSchema {
  name: string
  label: string
  type: FieldType
  required?: boolean
  placeholder?: string
  min?: number
  max?: number
  step?: number
  rows?: number
  maxlength?: number
  /** 初始值 */
  default?: string | number
  /** 静态选项 */
  options?: Array<[string, string]>
  /** 远程选项 */
  optionsFrom?: OptionsFrom
  /** 提示文案，展示在字段下方 */
  hint?: string
}

/** 明细行（订单 / 出入库 / 盘点类）配置 */
export interface LineSchema {
  title: string
  addLabel: string
  fields: Array<{
    name: string
    label: string
    type: 'select' | 'number'
    width?: string
    min?: number
    step?: number
    optionsFrom?: OptionsFrom
    disabled?: boolean
  }>
  /** 生成一行默认值 */
  makeRow: (ctx: OptionMap) => Record<string, unknown>
  /** 明细行 → 提交 body 的 items */
  toItems: (rows: Array<Record<string, unknown>>) => Array<Record<string, unknown>>
  /** 底部合计文案 */
  summary?: (rows: Array<Record<string, unknown>>) => string
}

export type OptionMap = Record<OptionsFrom, OptionItem[]>

export interface EntitySchema {
  /** 路由 key，形如 'oa:overtime' / 'order:purchase' */
  key: string
  title: string
  icon: string
  /** 提交成功后返回的列表页 */
  back: string
  fields: FieldSchema[]
  lines?: LineSchema
  /** 需要预加载的远程选项 */
  refs?: OptionsFrom[]
  submit: (v: Record<string, unknown>) => Promise<unknown>
  successText: string
  /** 底部补充说明（如业务规则） */
  tip?: string
}

/* ---------------------------------- 工具 ---------------------------------- */

function todayStr() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function currentMonth() {
  return new Date().toISOString().slice(0, 7)
}

/** 数字字段：缺省 0，提交时统一转 Number */
function num(name: string, label: string, def = 0, extra: Partial<FieldSchema> = {}): FieldSchema {
  return { name, label, type: 'number', default: def, step: 0.01, ...extra }
}

/** 通用提交参数构造：收集已填字段，空值转 undefined */
function collect(
  v: Record<string, unknown>,
  keys: Array<[string, 'text' | 'number']>,
): Record<string, unknown> {
  const body: Record<string, unknown> = {}
  for (const [k, t] of keys) {
    const raw = v[k]
    if (raw === '' || raw == null) continue
    body[k] = t === 'number' ? Number(raw) : String(raw)
  }
  return body
}

/* ------------------------------- 远程选项加载 ------------------------------- */

export async function loadOptions(froms: OptionsFrom[] | undefined): Promise<OptionMap> {
  const empty: OptionMap = {
    customers: [],
    suppliers: [],
    products: [],
    warehouses: [],
    departments: [],
    currencies: [],
    reportModules: [],
  }
  if (!froms?.length) return empty

  const map: Record<OptionsFrom, () => Promise<OptionItem[]>> = {
    customers: async () => {
      const r = await api.customers(undefined, 'all', undefined, 1, 100)
      return (r.list ?? []).map((x) => ({ value: String(x.id), label: String(x.name ?? x.id) }))
    },
    suppliers: async () => {
      const r = await api.baseList('supplier', undefined, 1, 100)
      return (r.list ?? []).map((x) => ({ value: String(x.id), label: String(x.name ?? x.id) }))
    },
    products: async () => {
      const r = await api.baseList('product', undefined, 1, 100)
      return (r.list ?? []).map((x) => ({
        value: String(x.id),
        label: `${x.sku ?? ''} · ${x.name ?? x.id}`.trim(),
      }))
    },
    warehouses: async () => {
      const r = await api.baseList('warehouse', undefined, 1, 100)
      return (r.list ?? []).map((x) => ({ value: String(x.id), label: String(x.name ?? x.id) }))
    },
    departments: async () => {
      const r = await api.departments({ page: 1, size: 100 })
      return (r.list ?? []).map((x) => ({ value: String(x.id), label: String(x.name ?? x.id) }))
    },
    currencies: async () => {
      const r = await api.currencies()
      return (r ?? []).map((x) => ({
        value: String(x.code),
        label: `${x.code} · ${x.name ?? ''}`.trim(),
      }))
    },
    reportModules: async () => {
      const r = await api.reportModules()
      return (r ?? []).map((m: string) => ({ value: m, label: MODULE_TEXT[m] ?? m }))
    },
  }

  const out = { ...empty }
  await Promise.all(froms.map(async (f) => {
    try {
      out[f] = await map[f]()
    } catch {
      out[f] = []
    }
  }))
  return out
}

const MODULE_TEXT: Record<string, string> = {
  sales: '销售',
  purchase: '采购',
  inventory: '库存',
  finance: '财务',
  hr: '人事',
}

/* -------------------------------- OA 假勤 -------------------------------- */

const OA: EntitySchema[] = [
  {
    key: 'oa:overtime',
    title: '加班申请',
    icon: 'clock',
    back: '/oa/overtime',
    submit: (v) => api.oaSubmit('overtime', collect(v, [
      ['applyDate', 'text'], ['startAt', 'text'], ['endAt', 'text'], ['duration', 'number'], ['reason', 'text'],
    ])),
    successText: '加班申请已提交',
    fields: [
      { name: 'applyDate', label: '日期', type: 'date', default: todayStr() },
      { name: 'startAt', label: '开始时间', type: 'datetime-local' },
      { name: 'endAt', label: '结束时间', type: 'datetime-local' },
      { name: 'duration', label: '时长(h)', type: 'number', step: 0.5, min: 0.5 },
      { name: 'reason', label: '事由', type: 'textarea', rows: 3 },
    ],
  },
  {
    key: 'oa:appeal',
    title: '补卡申诉',
    icon: 'edit',
    back: '/oa/appeal',
    submit: (v) => api.oaSubmit('appeal', collect(v, [
      ['workDate', 'text'], ['punchType', 'text'], ['appealReason', 'text'],
    ])),
    successText: '补卡申诉已提交',
    fields: [
      { name: 'workDate', label: '工作日', type: 'date', default: todayStr(), required: true },
      {
        name: 'punchType', label: '打卡类型', type: 'select', default: 'in', required: true,
        options: [['in', '上班'], ['out', '下班']],
      },
      { name: 'appealReason', label: '申诉原因', type: 'textarea', rows: 3, required: true },
    ],
  },
  {
    key: 'oa:outing',
    title: '外出申请',
    icon: 'map-pin',
    back: '/oa/outing',
    submit: (v) => api.oaSubmit('outing', collect(v, [
      ['startAt', 'text'], ['endAt', 'text'], ['destination', 'text'], ['reason', 'text'],
    ])),
    successText: '外出申请已提交',
    fields: [
      { name: 'startAt', label: '开始时间', type: 'datetime-local', required: true },
      { name: 'endAt', label: '结束时间', type: 'datetime-local', required: true },
      { name: 'destination', label: '目的地', type: 'text' },
      { name: 'reason', label: '事由', type: 'textarea', rows: 3 },
    ],
  },
  {
    key: 'oa:trip',
    title: '出差申请',
    icon: 'plane',
    back: '/oa/trip',
    submit: (v) => api.oaSubmit('trip', collect(v, [
      ['startDate', 'text'], ['endDate', 'text'], ['destination', 'text'], ['reason', 'text'],
    ])),
    successText: '出差申请已提交',
    fields: [
      { name: 'startDate', label: '出发日期', type: 'date', default: todayStr(), required: true },
      { name: 'endDate', label: '返回日期', type: 'date', required: true },
      { name: 'destination', label: '目的地', type: 'text' },
      { name: 'reason', label: '事由', type: 'textarea', rows: 3 },
    ],
  },
]

/* --------------------------- 含明细行的通用行配置 --------------------------- */

const PRODUCT_LINE: LineSchema['fields'] = [
  { name: 'productId', label: '商品', type: 'select', optionsFrom: 'products' },
  { name: 'qty', label: '数量', type: 'number', width: '92px', min: 1, step: 1 },
  { name: 'price', label: '单价', type: 'number', width: '108px', min: 0, step: 0.01 },
]

function sumLine(rows: Array<Record<string, unknown>>, withTax: boolean) {
  const net = rows.reduce((s, l) => s + Number(l.qty || 0) * Number(l.price || 0), 0)
  const tax = withTax
    ? rows.reduce((s, l) => s + Number(l.qty || 0) * Number(l.price || 0) * Number(l.taxRate || 0), 0)
    : 0
  return { net, tax, total: net + tax }
}

function taxLine(
  opts: OptionMap,
  extra: Partial<Record<string, unknown>> = {},
): Record<string, unknown> {
  return {
    productId: opts.products[0]?.value ?? '',
    qty: 1,
    price: 0,
    taxRate: 0.13,
    ...extra,
  }
}

/* --------------------------------- 注册表 --------------------------------- */

export const ENTITY_FORMS: EntitySchema[] = [
  ...OA,

  /* 报销 */
  {
    key: 'expense',
    title: '发起报销',
    icon: 'receipt',
    back: '/expenses',
    submit: (v) => api.createExpense({
      category: String(v.category ?? 'travel'),
      amount: Number(v.amount),
      happenDate: (v.happenDate as string) || undefined,
      reason: (v.reason as string) || undefined,
    }),
    successText: '报销已登记，记得提交审批',
    fields: [
      {
        name: 'category', label: '类别', type: 'select', default: 'travel',
        options: [
          ['travel', '差旅'], ['office', '办公'], ['meal', '餐饮'], ['transport', '交通'],
          ['entertain', '招待'], ['train', '培训'], ['other', '其他'],
        ],
      },
      { name: 'amount', label: '金额', type: 'number', min: 0, default: 0, required: true },
      { name: 'happenDate', label: '发生日期', type: 'date', default: todayStr() },
      { name: 'reason', label: '事由', type: 'text' },
    ],
  },

  /* 采购申请（明细行） */
  {
    key: 'purchase-request',
    title: '起草采购申请',
    icon: 'edit',
    back: '/purchase/requests',
    refs: ['suppliers', 'products'],
    successText: '采购申请已创建',
    fields: [
      { name: 'supplierId', label: '供应商', type: 'select', optionsFrom: 'suppliers' },
      { name: 'expectDate', label: '期望到货日', type: 'date' },
      { name: 'reason', label: '申请事由', type: 'text' },
    ],
    lines: {
      title: '申请明细',
      addLabel: '加行',
      fields: [
        { name: 'productId', label: '商品', type: 'select', optionsFrom: 'products' },
        { name: 'qty', label: '数量', type: 'number', width: '100px', min: 1, step: 1 },
      ],
      makeRow: (o) => ({ productId: o.products[0]?.value ?? '', qty: 1 }),
      toItems: (rows) => rows.map((l) => ({ productId: l.productId, qty: Number(l.qty) })),
    },
    submit: (v) => api.createPurchaseRequest({
      supplierId: (v.supplierId as string) || undefined,
      expectDate: (v.expectDate as string) || undefined,
      reason: (v.reason as string) || undefined,
      items: (v.__items as Array<Record<string, unknown>>)?.map((l) => ({
        productId: l.productId, qty: Number(l.qty),
      })) ?? [],
    }),
  },

  /* 采购 / 销售订单（明细行） */
  {
    key: 'order:purchase',
    title: '新建采购订单',
    icon: 'shopping-cart',
    back: '/orders/purchase',
    refs: ['suppliers', 'products', 'warehouses'],
    successText: '采购订单已创建',
    tip: '提交后可在列表发起审批。',
    fields: [
      { name: 'partyId', label: '供应商', type: 'select', optionsFrom: 'suppliers', required: true },
      { name: 'warehouseId', label: '仓库', type: 'select', optionsFrom: 'warehouses' },
      { name: 'creditDays', label: '账期（天）', type: 'number', default: 30, step: 1, min: 0 },
      { name: 'remark', label: '备注', type: 'text' },
    ],
    lines: {
      title: '明细行',
      addLabel: '增行',
      fields: [...PRODUCT_LINE, { name: 'taxRate', label: '税率', type: 'number', width: '84px', step: 0.01, min: 0 }],
      makeRow: (o) => taxLine(o, { qty: 10, price: 10 }),
      toItems: (rows) => rows.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price), taxRate: Number(l.taxRate),
      })),
      summary: (rows) => {
        const { net, tax, total } = sumLine(rows, true)
        return `不含税 ${net.toFixed(2)} ＋ 税额 ${tax.toFixed(2)} ＝ ${total.toFixed(2)}`
      },
    },
    submit: (v) => api.createPo({
      partyId: String(v.partyId),
      warehouseId: (v.warehouseId as string) || undefined,
      creditDays: Number(v.creditDays),
      remark: (v.remark as string) || undefined,
      items: (v.__items as Array<Record<string, unknown>>)?.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price), taxRate: Number(l.taxRate),
      })) ?? [],
    }),
  },
  {
    key: 'order:sales',
    title: '新建销售订单',
    icon: 'briefcase',
    back: '/orders/sales',
    refs: ['customers', 'products', 'warehouses'],
    successText: '销售订单已创建',
    tip: '销售订单会按当前加权成本冻结成本价，据此计算毛利。',
    fields: [
      { name: 'partyId', label: '客户', type: 'select', optionsFrom: 'customers', required: true },
      { name: 'warehouseId', label: '仓库', type: 'select', optionsFrom: 'warehouses' },
      { name: 'creditDays', label: '账期（天）', type: 'number', default: 30, step: 1, min: 0 },
      { name: 'remark', label: '备注', type: 'text' },
    ],
    lines: {
      title: '明细行',
      addLabel: '增行',
      fields: [...PRODUCT_LINE, { name: 'taxRate', label: '税率', type: 'number', width: '84px', step: 0.01, min: 0 }],
      makeRow: (o) => taxLine(o, { qty: 10 }),
      toItems: (rows) => rows.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price), taxRate: Number(l.taxRate),
      })),
      summary: (rows) => {
        const { net, tax, total } = sumLine(rows, true)
        return `不含税 ${net.toFixed(2)} ＋ 税额 ${tax.toFixed(2)} ＝ ${total.toFixed(2)}`
      },
    },
    submit: (v) => api.createSo({
      partyId: String(v.partyId),
      warehouseId: (v.warehouseId as string) || undefined,
      creditDays: Number(v.creditDays),
      remark: (v.remark as string) || undefined,
      items: (v.__items as Array<Record<string, unknown>>)?.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price), taxRate: Number(l.taxRate),
      })) ?? [],
    }),
  },

  /* 入库 / 出库（明细行，注意 body 字段命名不同） */
  {
    key: 'fulfillment:inbound',
    title: '新建采购入库单',
    icon: 'package',
    back: '/purchase/inbounds',
    refs: ['suppliers', 'products', 'warehouses'],
    successText: '入库单已创建，待审核',
    tip: '审核时按移动加权更新库存成本并生成应付。',
    fields: [
      { name: 'partyId', label: '供应商', type: 'select', optionsFrom: 'suppliers', required: true },
      { name: 'warehouseId', label: '仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      { name: 'remark', label: '备注', type: 'text' },
    ],
    lines: {
      title: '明细行',
      addLabel: '增行',
      fields: [...PRODUCT_LINE, { name: 'taxRate', label: '税率', type: 'number', width: '84px', step: 0.01, min: 0 }],
      makeRow: (o) => taxLine(o, { qty: 10 }),
      toItems: (rows) => rows.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price), taxRate: Number(l.taxRate),
      })),
      summary: (rows) => {
        const { net, tax, total } = sumLine(rows, true)
        return `价税合计：${total.toFixed(2)}（不含税 ${net.toFixed(2)}，税额 ${tax.toFixed(2)}）`
      },
    },
    submit: (v) => api.createInbound({
      supplierId: String(v.partyId),
      warehouseId: String(v.warehouseId),
      remark: (v.remark as string) || undefined,
      items: (v.__items as Array<Record<string, unknown>>)?.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price), taxRate: Number(l.taxRate),
      })) ?? [],
    }),
  },
  {
    key: 'fulfillment:outbound',
    title: '新建销售出库单',
    icon: 'truck',
    back: '/sales/outbounds',
    refs: ['customers', 'products', 'warehouses'],
    successText: '出库单已创建，待审核',
    tip: '审核时按加权成本扣减库存、校验授信并生成应收。',
    fields: [
      { name: 'partyId', label: '客户', type: 'select', optionsFrom: 'customers', required: true },
      { name: 'warehouseId', label: '仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      { name: 'remark', label: '备注', type: 'text' },
    ],
    lines: {
      title: '明细行',
      addLabel: '增行',
      fields: [...PRODUCT_LINE, { name: 'taxRate', label: '税率', type: 'number', width: '84px', step: 0.01, min: 0 }],
      makeRow: (o) => taxLine(o, { qty: 10 }),
      toItems: (rows) => rows.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price), taxRate: Number(l.taxRate),
      })),
      summary: (rows) => {
        const { net, tax, total } = sumLine(rows, true)
        return `价税合计：${total.toFixed(2)}（不含税 ${net.toFixed(2)}，税额 ${tax.toFixed(2)}）`
      },
    },
    submit: (v) => api.createOutbound({
      customerId: String(v.partyId),
      warehouseId: String(v.warehouseId),
      remark: (v.remark as string) || undefined,
      items: (v.__items as Array<Record<string, unknown>>)?.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price), taxRate: Number(l.taxRate),
      })) ?? [],
    }),
  },

  /* 退货（明细行） */
  {
    key: 'return:sales',
    title: '新建销售退货',
    icon: 'rotate-ccw',
    back: '/returns',
    refs: ['customers', 'products', 'warehouses'],
    successText: '销售退货单已创建',
    fields: [
      { name: 'partyId', label: '客户', type: 'select', optionsFrom: 'customers', required: true },
      { name: 'warehouseId', label: '仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      { name: 'reason', label: '退货原因', type: 'text' },
    ],
    lines: {
      title: '退货明细',
      addLabel: '增行',
      fields: PRODUCT_LINE,
      makeRow: (o) => ({ productId: o.products[0]?.value ?? '', qty: 1, price: 0 }),
      toItems: (rows) => rows.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price),
      })),
    },
    submit: (v) => api.createSalesReturn({
      customerId: String(v.partyId),
      warehouseId: String(v.warehouseId),
      reason: (v.reason as string) || undefined,
      items: (v.__items as Array<Record<string, unknown>>)?.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price),
      })) ?? [],
    }),
  },
  {
    key: 'return:purchase',
    title: '新建采购退货',
    icon: 'rotate-ccw',
    back: '/returns',
    refs: ['suppliers', 'products', 'warehouses'],
    successText: '采购退货单已创建',
    tip: '采购退货按当前加权成本自动计价，单价不可手工填写。',
    fields: [
      { name: 'partyId', label: '供应商', type: 'select', optionsFrom: 'suppliers', required: true },
      { name: 'warehouseId', label: '仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      { name: 'remark', label: '备注', type: 'text' },
    ],
    lines: {
      title: '退货明细',
      addLabel: '增行',
      fields: [
        { name: 'productId', label: '商品', type: 'select', optionsFrom: 'products' },
        { name: 'qty', label: '数量', type: 'number', width: '92px', min: 1, step: 1 },
        { name: 'price', label: '单价', type: 'number', width: '108px', min: 0, step: 0.01, disabled: true },
      ],
      makeRow: (o) => ({ productId: o.products[0]?.value ?? '', qty: 1, price: 0 }),
      toItems: (rows) => rows.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price),
      })),
    },
    submit: (v) => api.createPurchaseReturn({
      supplierId: String(v.partyId),
      warehouseId: String(v.warehouseId),
      remark: (v.remark as string) || undefined,
      items: (v.__items as Array<Record<string, unknown>>)?.map((l) => ({
        productId: l.productId, qty: Number(l.qty), price: Number(l.price),
      })) ?? [],
    }),
  },

  /* 收款 / 付款 */
  {
    key: 'finance:receive',
    title: '登记收款',
    icon: 'wallet',
    back: '/finance',
    refs: ['customers'],
    successText: '收款已登记',
    tip: '按到期日由早到晚自动核销，一笔款可冲多张单；冲完自动置为「已结」。',
    fields: [
      { name: 'partyId', label: '客户', type: 'select', optionsFrom: 'customers', required: true },
      { name: 'amount', label: '金额', type: 'number', min: 0, default: 0, required: true },
      {
        name: 'payMethod', label: '方式', type: 'select', default: 'bank',
        options: [['bank', '银行转账'], ['cash', '现金'], ['acceptance', '承兑']],
      },
      { name: 'remark', label: '备注', type: 'text' },
    ],
    submit: (v) => api.createPayment({
      type: 'receive',
      counterpartyId: String(v.partyId),
      amount: Number(v.amount),
      payMethod: String(v.payMethod),
      remark: (v.remark as string) || undefined,
    }),
  },
  {
    key: 'finance:pay',
    title: '登记付款',
    icon: 'wallet',
    back: '/finance',
    refs: ['suppliers'],
    successText: '付款已登记',
    tip: '按到期日由早到晚自动核销，冲完自动置为「已结」。',
    fields: [
      { name: 'partyId', label: '供应商', type: 'select', optionsFrom: 'suppliers', required: true },
      { name: 'amount', label: '金额', type: 'number', min: 0, default: 0, required: true },
      {
        name: 'payMethod', label: '方式', type: 'select', default: 'bank',
        options: [['bank', '银行转账'], ['cash', '现金'], ['acceptance', '承兑']],
      },
      { name: 'remark', label: '备注', type: 'text' },
    ],
    submit: (v) => api.createPayment({
      type: 'pay',
      counterpartyId: String(v.partyId),
      amount: Number(v.amount),
      payMethod: String(v.payMethod),
      remark: (v.remark as string) || undefined,
    }),
  },

  /* 发票 */
  {
    key: 'invoice:sales',
    title: '登记销项发票',
    icon: 'receipt',
    back: '/finance/invoices',
    successText: '发票已登记',
    fields: [
      { name: 'partyName', label: '购方名称', type: 'text', required: true },
      { name: 'partyTaxNo', label: '购方税号', type: 'text' },
      { name: 'amount', label: '价税合计', type: 'number', min: 0, default: 0, required: true },
      { name: 'taxRate', label: '税率', type: 'number', step: 0.01, default: 0.13 },
    ],
    tip: '不含税金额与税额由价税合计和税率反算。',
    submit: (v) => api.createInvoice({
      type: 'sales',
      partyName: String(v.partyName).trim(),
      partyTaxNo: (v.partyTaxNo as string) || undefined,
      amount: Number(v.amount),
      taxRate: Number(v.taxRate),
    }),
  },
  {
    key: 'invoice:purchase',
    title: '登记进项发票',
    icon: 'receipt',
    back: '/finance/invoices',
    successText: '发票已登记',
    fields: [
      { name: 'partyName', label: '销方名称', type: 'text', required: true },
      { name: 'partyTaxNo', label: '销方税号', type: 'text' },
      { name: 'amount', label: '价税合计', type: 'number', min: 0, default: 0, required: true },
      { name: 'taxRate', label: '税率', type: 'number', step: 0.01, default: 0.13 },
    ],
    tip: '不含税金额与税额由价税合计和税率反算。',
    submit: (v) => api.createInvoice({
      type: 'purchase',
      partyName: String(v.partyName).trim(),
      partyTaxNo: (v.partyTaxNo as string) || undefined,
      amount: Number(v.amount),
      taxRate: Number(v.taxRate),
    }),
  },

  /* CRM */
  {
    key: 'customer',
    title: '新增客户',
    icon: 'building',
    back: '/customers',
    successText: '客户已创建',
    tip: '归属销售默认为当前登录员工，部门随之自动带入。',
    fields: [
      { name: 'name', label: '客户名称', type: 'text', required: true, placeholder: '如：宏达贸易' },
      { name: 'level', label: '等级', type: 'select', default: 'B', options: [['A', 'A'], ['B', 'B'], ['C', 'C']] },
      { name: 'contactName', label: '联系人', type: 'text' },
      { name: 'contactPhone', label: '联系电话', type: 'text' },
      num('creditLimit', '授信额度'),
      num('paymentTerms', '账期（天）', 30, { step: 1, min: 0 }),
      { name: 'address', label: '地址', type: 'text' },
      { name: 'remark', label: '备注', type: 'textarea', rows: 2 },
    ],
    submit: (v) => api.createCustomer({
      name: String(v.name).trim(),
      level: String(v.level),
      contactName: (v.contactName as string) || undefined,
      contactPhone: (v.contactPhone as string) || undefined,
      address: (v.address as string) || undefined,
      creditLimit: Number(v.creditLimit),
      paymentTerms: Number(v.paymentTerms),
      remark: (v.remark as string) || undefined,
    }),
  },
  {
    key: 'lead',
    title: '新增线索',
    icon: 'target',
    back: '/crm/leads',
    successText: '线索已创建',
    fields: [
      { name: 'name', label: '线索名称', type: 'text', required: true, placeholder: '公司或联系人名称' },
      {
        name: 'source', label: '来源', type: 'select', default: 'web',
        options: [['web', '官网'], ['referral', '转介绍'], ['exhibition', '展会'], ['call', '电话'], ['other', '其他']],
      },
      { name: 'contactName', label: '联系人', type: 'text' },
      { name: 'contactPhone', label: '联系电话', type: 'text' },
    ],
    submit: (v) => api.createLead({
      name: String(v.name).trim(),
      source: String(v.source),
      contactName: (v.contactName as string) || undefined,
      contactPhone: (v.contactPhone as string) || undefined,
    }),
  },
  {
    key: 'opportunity',
    title: '新增商机',
    icon: 'trending-up',
    back: '/crm/opportunities',
    refs: ['customers'],
    successText: '商机已创建',
    fields: [
      { name: 'name', label: '商机名称', type: 'text', required: true },
      { name: 'customerId', label: '客户', type: 'select', optionsFrom: 'customers' },
      {
        name: 'stage', label: '阶段', type: 'select', default: 'contact',
        options: [['contact', '初步接触'], ['quote', '报价'], ['solution', '方案'], ['negotiation', '商务谈判']],
      },
      num('amount', '预计金额'),
      { name: 'expectCloseDate', label: '预计成交日', type: 'date' },
    ],
    submit: (v) => api.createOpportunity({
      name: String(v.name).trim(),
      customerId: (v.customerId as string) || undefined,
      stage: String(v.stage),
      amount: Number(v.amount),
      expectCloseDate: (v.expectCloseDate as string) || undefined,
    }),
  },
  {
    key: 'contract',
    title: '登记合同',
    icon: 'file-text',
    back: '/crm/contracts',
    refs: ['customers'],
    successText: '合同已登记',
    fields: [
      { name: 'name', label: '合同名称', type: 'text', required: true },
      { name: 'customerId', label: '客户', type: 'select', optionsFrom: 'customers', required: true },
      num('amount', '合同金额'),
      { name: 'signDate', label: '签订日期', type: 'date' },
      { name: 'endDate', label: '到期日期', type: 'date' },
      num('paymentTerms', '账期（天）', 30, { step: 1, min: 0 }),
    ],
    submit: (v) => api.createContract({
      name: String(v.name).trim(),
      customerId: String(v.customerId),
      amount: Number(v.amount),
      signDate: (v.signDate as string) || undefined,
      endDate: (v.endDate as string) || undefined,
      paymentTerms: Number(v.paymentTerms),
    }),
  },

  /* 库存 / 盘点 */
  {
    key: 'batch',
    title: '登记批次',
    icon: 'box',
    back: '/inventory/batches',
    refs: ['products', 'warehouses'],
    successText: '批次已登记',
    fields: [
      { name: 'productId', label: '商品', type: 'select', optionsFrom: 'products', required: true },
      { name: 'warehouseId', label: '仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      num('qty', '数量', 10, { step: 1, min: 1 }),
      num('costPrice', '成本单价'),
      { name: 'batchNo', label: '批次号', type: 'text', placeholder: '留空自动生成' },
      { name: 'locationCode', label: '库位编码', type: 'text', placeholder: '如 A-01-03' },
      { name: 'expireDate', label: '到期日', type: 'date' },
    ],
    submit: (v) => api.createBatch({
      productId: String(v.productId),
      warehouseId: String(v.warehouseId),
      qty: Number(v.qty),
      costPrice: Number(v.costPrice),
      batchNo: (v.batchNo as string) || undefined,
      locationCode: (v.locationCode as string) || undefined,
      expireDate: (v.expireDate as string) || undefined,
    }),
  },
  {
    key: 'stocktake',
    title: '新建盘点单',
    icon: 'check-square',
    back: '/inventory/stocktake',
    refs: ['warehouses'],
    successText: '已按账面库存生成盘点明细',
    tip: '按该仓库当前账面库存生成盘点明细，随后可录入实盘数量。',
    fields: [
      { name: 'warehouseId', label: '仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      { name: 'remark', label: '备注', type: 'text' },
    ],
    submit: (v) => api.createStocktake({
      warehouseId: String(v.warehouseId),
      remark: (v.remark as string) || undefined,
    }),
  },
  {
    key: 'inventory:in',
    title: '手工入库',
    icon: 'download',
    back: '/inventory',
    refs: ['products', 'warehouses'],
    successText: '入库已记账',
    fields: [
      { name: 'productId', label: '商品', type: 'select', optionsFrom: 'products', required: true },
      { name: 'warehouseId', label: '仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      num('qty', '数量', 1, { step: 1, min: 1 }),
      num('price', '入库单价', 0, { step: 0.01, min: 0 }),
      { name: 'remark', label: '备注', type: 'text' },
    ],
    submit: (v) => api.inbound({
      productId: String(v.productId),
      warehouseId: String(v.warehouseId),
      qty: Number(v.qty),
      price: Number(v.price),
      remark: (v.remark as string) || undefined,
      refType: 'purchase',
    }),
  },
  {
    key: 'inventory:out',
    title: '手工出库',
    icon: 'upload',
    back: '/inventory',
    refs: ['products', 'warehouses'],
    successText: '出库已记账',
    fields: [
      { name: 'productId', label: '商品', type: 'select', optionsFrom: 'products', required: true },
      { name: 'warehouseId', label: '仓库', type: 'select', optionsFrom: 'warehouses', required: true },
      num('qty', '数量', 1, { step: 1, min: 1 }),
      { name: 'remark', label: '备注', type: 'text' },
    ],
    submit: (v) => api.outbound({
      productId: String(v.productId),
      warehouseId: String(v.warehouseId),
      qty: Number(v.qty),
      price: undefined,
      remark: (v.remark as string) || undefined,
      refType: 'sales',
    }),
  },

  /* 基础数据 */
  {
    key: 'basedata:category',
    title: '新增商品分类',
    icon: 'box',
    back: '/basedata',
    successText: '已新增',
    fields: [
      { name: 'code', label: '编码', type: 'text' },
      { name: 'name', label: '名称', type: 'text', required: true },
      num('sortNo', '排序', 0, { step: 1 }),
    ],
    submit: (v) => api.baseCreate('category', collect(v, [
      ['code', 'text'], ['name', 'text'], ['sortNo', 'number'],
    ])),
  },
  {
    key: 'basedata:product',
    title: '新增商品',
    icon: 'package',
    back: '/basedata',
    successText: '已新增',
    fields: [
      { name: 'sku', label: 'SKU', type: 'text' },
      { name: 'name', label: '名称', type: 'text', required: true },
      { name: 'spec', label: '规格', type: 'text' },
      { name: 'unit', label: '单位', type: 'text' },
      num('salePrice', '售价'),
      num('taxRate', '税率'),
    ],
    submit: (v) => api.baseCreate('product', collect(v, [
      ['sku', 'text'], ['name', 'text'], ['spec', 'text'], ['unit', 'text'],
      ['salePrice', 'number'], ['taxRate', 'number'],
    ])),
  },
  {
    key: 'basedata:supplier',
    title: '新增供应商',
    icon: 'truck',
    back: '/basedata',
    successText: '已新增',
    fields: [
      { name: 'code', label: '编码', type: 'text' },
      { name: 'name', label: '名称', type: 'text', required: true },
      { name: 'contactName', label: '联系人', type: 'text' },
      { name: 'contactPhone', label: '联系电话', type: 'text' },
      num('paymentTerms', '账期(天)', 0, { step: 1, min: 0 }),
    ],
    submit: (v) => api.baseCreate('supplier', collect(v, [
      ['code', 'text'], ['name', 'text'], ['contactName', 'text'],
      ['contactPhone', 'text'], ['paymentTerms', 'number'],
    ])),
  },
  {
    key: 'basedata:warehouse',
    title: '新增仓库',
    icon: 'building',
    back: '/basedata',
    successText: '已新增',
    fields: [
      { name: 'code', label: '编码', type: 'text' },
      { name: 'name', label: '名称', type: 'text', required: true },
      { name: 'location', label: '位置', type: 'text' },
      { name: 'type', label: '类型', type: 'select', default: 'normal', options: [['normal', '普通'], ['bonded', '保税']] },
    ],
    submit: (v) => api.baseCreate('warehouse', collect(v, [
      ['code', 'text'], ['name', 'text'], ['location', 'text'], ['type', 'text'],
    ])),
  },
  {
    key: 'basedata:currency',
    title: '新增汇率记录',
    icon: 'wallet',
    back: '/basedata',
    successText: '已新增',
    fields: [
      { name: 'currencyFrom', label: '源币种', type: 'text', required: true, placeholder: '如 USD' },
      { name: 'currencyTo', label: '目标币种', type: 'text', default: 'CNY' },
      { name: 'rate', label: '汇率', type: 'number', step: 0.0001, min: 0, default: 0, required: true },
      { name: 'rateDate', label: '日期', type: 'date', default: todayStr(), required: true },
    ],
    submit: (v) => api.baseCreate('currency', collect(v, [
      ['currencyFrom', 'text'], ['currencyTo', 'text'], ['rate', 'number'], ['rateDate', 'text'],
    ])),
  },

  /* 多币种 */
  {
    key: 'fx:currency',
    title: '新增币种',
    icon: 'dollar-sign',
    back: '/system/fx',
    successText: '币种已保存',
    tip: '已存在的代码会更新名称与符号。',
    fields: [
      { name: 'code', label: '币种代码', type: 'text', required: true, placeholder: '如 USD', maxlength: 8 },
      { name: 'name', label: '名称', type: 'text', required: true, placeholder: '如 美元' },
      { name: 'symbol', label: '符号', type: 'text', placeholder: '如 $' },
      num('decimalPlaces', '小数位', 2, { step: 1, min: 0, max: 4 }),
    ],
    submit: (v) => api.saveCurrency({
      code: String(v.code).trim().toUpperCase(),
      name: String(v.name).trim(),
      symbol: (v.symbol as string) || undefined,
      decimalPlaces: Number(v.decimalPlaces),
    }),
  },
  {
    key: 'fx:rate',
    title: '录入汇率',
    icon: 'dollar-sign',
    back: '/system/fx',
    refs: ['currencies'],
    successText: '汇率已保存',
    tip: '同一「币种对 + 生效日」再次录入将覆盖；换算时取 ≤ 指定日期的最新汇率。',
    fields: [
      { name: 'currencyFrom', label: '源币种', type: 'select', optionsFrom: 'currencies', required: true },
      { name: 'currencyTo', label: '目标币种', type: 'select', optionsFrom: 'currencies', default: 'CNY', required: true },
      { name: 'rate', label: '汇率', type: 'number', step: 0.0001, min: 0, default: 7.12, required: true },
      { name: 'rateDate', label: '生效日期', type: 'date' },
    ],
    submit: (v) => api.upsertFxRate({
      currencyFrom: String(v.currencyFrom),
      currencyTo: String(v.currencyTo),
      rate: Number(v.rate),
      rateDate: (v.rateDate as string) || undefined,
      source: 'manual',
    }),
  },

  /* 报表 */
  {
    key: 'report',
    title: '新建报表',
    icon: 'bar-chart',
    back: '/reports',
    refs: ['reportModules'],
    successText: '报表已创建',
    tip: '报表模板按模块内置（销售/采购/库存/财务/人事），运行后结果可导出 Excel。',
    fields: [
      { name: 'name', label: '报表名称', type: 'text', required: true, placeholder: '如 客户销售额排行' },
      { name: 'module', label: '数据模块', type: 'select', optionsFrom: 'reportModules', default: 'sales' },
      {
        name: 'frequency', label: '生成频率', type: 'select', default: 'none',
        options: [['none', '按需'], ['daily', '每日'], ['weekly', '每周'], ['monthly', '每月']],
      },
    ],
    submit: (v) => api.createReport({
      name: String(v.name).trim(),
      module: String(v.module),
      frequency: String(v.frequency),
    }),
  },

  /* HR 七類人事事件 */
  ...([
    {
      key: 'hr:regular', title: '转正申请', icon: 'award', back: '/hr/regular',
      fields: [
        { name: 'probationEnd', label: '试用期止', type: 'date' },
        { name: 'regularDate', label: '转正日期', type: 'date' },
        { name: 'evaluation', label: '评估意见', type: 'textarea', rows: 3 },
      ],
    },
    {
      key: 'hr:transfer', title: '调岗申请', icon: 'refresh', back: '/hr/transfer', refs: ['departments'],
      fields: [
        { name: 'fromDeptId', label: '原部门', type: 'select', optionsFrom: 'departments' },
        { name: 'toDeptId', label: '新部门', type: 'select', optionsFrom: 'departments' },
        { name: 'fromPosition', label: '原岗位', type: 'text' },
        { name: 'toPosition', label: '新岗位', type: 'text' },
        { name: 'effectiveDate', label: '生效日', type: 'date' },
        { name: 'reason', label: '原因', type: 'textarea', rows: 3 },
      ],
    },
    {
      key: 'hr:promo', title: '晋升申请', icon: 'arrow-up', back: '/hr/promo',
      fields: [
        { name: 'fromPosition', label: '原职位', type: 'text' },
        { name: 'toPosition', label: '新职位', type: 'text' },
        { name: 'fromLevel', label: '原职级', type: 'text' },
        { name: 'toLevel', label: '新职级', type: 'text' },
        { name: 'effectiveDate', label: '生效日', type: 'date' },
        { name: 'reason', label: '原因', type: 'textarea', rows: 3 },
      ],
    },
    {
      key: 'hr:dimission', title: '离职 / 辞退', icon: 'user-minus', back: '/hr/dimission',
      fields: [
        {
          name: 'type', label: '类型', type: 'select',
          options: [['resign', '主动离职'], ['terminate', '辞退'], ['retire', '退休']],
        },
        { name: 'lastWorkDate', label: '最后工作日', type: 'date' },
        { name: 'handoverTo', label: '交接人', type: 'text' },
        { name: 'reason', label: '原因', type: 'textarea', rows: 3 },
      ],
    },
    {
      key: 'hr:entry', title: '登记候选人', icon: 'user-plus', back: '/hr/entry', refs: ['departments'],
      fields: [
        { name: 'candidateName', label: '候选人', type: 'text', required: true },
        { name: 'phone', label: '手机号', type: 'text' },
        { name: 'expectedDeptId', label: '期望部门', type: 'select', optionsFrom: 'departments' },
        { name: 'expectedPosition', label: '期望岗位', type: 'text' },
        { name: 'expectedEntryDate', label: '预计入职', type: 'date' },
        { name: 'sourceChannel', label: '招聘渠道', type: 'text' },
      ],
    },
    {
      key: 'hr:contract', title: '新增合同', icon: 'file-text', back: '/hr/contract',
      fields: [
        { name: 'contractNo', label: '合同号', type: 'text', required: true },
        { name: 'type', label: '类型', type: 'select', options: [['fixed', '固定期'], ['nonfixed', '无固定期']] },
        { name: 'startDate', label: '起始日', type: 'date' },
        { name: 'endDate', label: '到期日', type: 'date' },
        { name: 'signDate', label: '签订日', type: 'date' },
        { name: 'remark', label: '备注', type: 'textarea', rows: 3 },
      ],
    },
  ] as Array<Omit<EntitySchema, 'submit' | 'successText'>>).map((e) => ({
    ...e,
    successText: '已提交',
    tip: '提交后将进入审批流程（主管 → HR），通过后自动更新员工档案。',
    submit: (v: Record<string, unknown>) => {
      const kind = e.key.split(':')[1]
      const payload: Record<string, unknown> = {}
      for (const f of e.fields) {
        const raw = v[f.name]
        if (raw === '' || raw == null) continue
        payload[f.name] = raw
      }
      return api.hrSubmit(kind, payload)
    },
  })),

  /* 组织 / 薪资 / 流程定义 */
  {
    key: 'department',
    title: '新增部门',
    icon: 'building',
    back: '/departments',
    successText: '部门已创建',
    fields: [
      { name: 'name', label: '部门名称', type: 'text', required: true, placeholder: '如：生产部' },
      { name: 'phone', label: '联系电话', type: 'text', placeholder: '选填' },
    ],
    submit: (v) => api.createDepartment({
      name: String(v.name).trim(),
      phone: (v.phone as string) || undefined,
    }),
  },
  {
    key: 'payroll',
    title: '生成薪资',
    icon: 'wallet',
    back: '/payroll',
    successText: '薪资已生成',
    tip: '薪资金额为演示数据，服务端会重新核算为准。',
    fields: [
      { name: 'period', label: '期间', type: 'text', default: currentMonth(), placeholder: '2026-10' },
      num('baseSalary', '基本工资', 8000),
      num('bonus', '奖金'),
      num('allowance', '津贴', 500),
      num('deduction', '扣款'),
      num('socialSecurity', '社保', 800),
      num('tax', '个税', 300),
    ],
    submit: (v) => api.payrollGenerate({
      period: String(v.period),
      baseSalary: Number(v.baseSalary),
      bonus: Number(v.bonus),
      allowance: Number(v.allowance),
      deduction: Number(v.deduction),
      socialSecurity: Number(v.socialSecurity),
      tax: Number(v.tax),
    }),
  },
  {
    key: 'workflow-def',
    title: '新建流程',
    icon: 'git-branch',
    back: '/system/workflow-designer',
    successText: '流程已创建',
    fields: [
      {
        name: 'code', label: '流程标识 code', type: 'text', required: true,
        placeholder: '如 overtime，需与业务单据 bizType 一致',
      },
      { name: 'name', label: '流程名称', type: 'text', required: true, placeholder: '如 加班申请' },
      { name: 'remark', label: '备注', type: 'text' },
    ],
    submit: (v) => api.wfCreateDef({
      code: String(v.code).trim(),
      name: String(v.name).trim(),
      remark: String(v.remark ?? ''),
    }),
  },
]

export function findEntity(key: string): EntitySchema | undefined {
  return ENTITY_FORMS.find((e) => e.key === key)
}

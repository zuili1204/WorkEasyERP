import http from './http'

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

export interface LoginUserInfo {
  id: string
  username: string
  displayName: string
  deptName?: string
  roles: string[]
  dataScope: string
  /** 当前角色不可见的敏感字段（成本 / 毛利 / 薪资），前端据此隐藏列 */
  hiddenFields?: string[]
}

export interface LoginData {
  token: string
  user: LoginUserInfo
}

export interface EmployeeRow {
  id: string
  employeeNo?: string
  realName: string
  deptName?: string
  position?: string
  status: string
}

export interface DepartmentRow {
  id: string
  name: string
  managerName?: string
  count: number
  status: string
}

export interface LeaveRow {
  id: string
  orderNo?: string
  employeeName?: string
  leaveType?: string
  startAt?: string
  endAt?: string
  duration?: string
  reason?: string
  status: string
  createdAt?: string
}

export interface LeaveSubmit {
  leaveType: string
  startAt?: string
  endAt?: string
  duration: number
  reason?: string
}

export interface TodoRow {
  id: string
  title?: string
  bizType?: string
  bizId?: string
  nodeName?: string
  status: string
  createdAt?: string
  actionable: boolean
}

export interface NoticeRow {
  id: string
  type: string
  title?: string
  content?: string
  bizType?: string
  bizId?: string
  status: string
  createdAt?: string
}

export interface PrintLine {
  name: string
  spec?: string | null
  unit?: string | null
  qty: string
  price: string
  amount: string
  remark?: string | null
}

export interface PrintBill {
  title: string
  kind: string
  no: string
  partyLabel: string
  partyName: string
  date: string
  meta: string[][]
  items: PrintLine[]
  totalQty: string
  totalAmount: string
  amountUpper: string
  remark?: string | null
  printedBy: string
  printedAt: string
  company: string
}

export interface ReportResult {
  name: string
  module: string
  columns: string[]
  rows: (string | null)[][]
  generatedAt: string
}

export interface DashboardOverview {
  cards: { label: string; value: string; sub: string; tone: string }[]
  myTodo: number
  unread: number
  recent: { id: string; title?: string; bizType?: string; status: string; createdAt?: string }[]
  trend: { month: string; sales: string; profit: string; salesRaw: number; profitRaw: number }[]
  topCustomers: { name: string; amount: string }[]
  alerts: { kind: string; title: string; detail: string }[]
}

export interface ListQuery {
  q?: string
  scope?: string
  status?: string
  page: number
  size: number
  sort?: string
}

async function unwrap<T>(p: Promise<{ data: ApiResp<T> }>): Promise<T> {
  const res = await p
  return res.data.data
}

export const api = {
  login: (username: string, password: string) =>
    unwrap(http.post<ApiResp<LoginData>>('/auth/login', { username, password })),

  me: () => unwrap(http.get<ApiResp<LoginUserInfo>>('/auth/me')),

  employees: (q: ListQuery) =>
    unwrap(http.get<ApiResp<PageResult<EmployeeRow>>>('/employees', { params: q })),

  departments: (q: Omit<ListQuery, 'scope' | 'status'>) =>
    unwrap(http.get<ApiResp<PageResult<DepartmentRow>>>('/departments', { params: q })),

  createDepartment: (body: { name: string; parentId?: string; phone?: string }) =>
    unwrap(http.post<ApiResp<DepartmentRow>>('/departments', body)),

  // 请假
  leaves: (q: Partial<ListQuery>) =>
    unwrap(http.get<ApiResp<PageResult<LeaveRow>>>('/leaves', { params: q })),
  submitLeave: (body: LeaveSubmit) =>
    unwrap(http.post<ApiResp<LeaveRow>>('/leaves', body)),

  // 待办中心
  todos: (view: string, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<TodoRow>>>('/todos', { params: { view, page, size } })),
  approve: (taskId: string, comment?: string) =>
    unwrap(http.post<ApiResp<boolean>>(`/workflow/tasks/${taskId}/approve`, { comment })),
  reject: (taskId: string, comment?: string) =>
    unwrap(http.post<ApiResp<boolean>>(`/workflow/tasks/${taskId}/reject`, { comment })),

  // 消息中心
  notices: (onlyUnread: boolean | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<NoticeRow>>>('/notices', { params: { onlyUnread, page, size } })),
  unreadCount: () => unwrap(http.get<ApiResp<{ count: number }>>('/notices/unread-count')),
  markRead: (id: string) => unwrap(http.post<ApiResp<boolean>>(`/notices/${id}/read`)),
  markAllRead: () => unwrap(http.post<ApiResp<boolean>>('/notices/read-all')),

  // 看板概览
  overview: () => unwrap(http.get<ApiResp<DashboardOverview>>('/dashboard/overview')),

  // 请假扩展
  resubmitLeave: (id: string, body: LeaveSubmit) =>
    unwrap(http.post<ApiResp<LeaveRow>>(`/leaves/${id}/resubmit`, body)),
  cancelLeave: (id: string) => unwrap(http.post<ApiResp<boolean>>(`/leaves/${id}/cancel`)),

  // 假勤：overtime 加班 / appeal 补卡 / outing 外出 / trip 出差
  oaList: (bizType: string, q: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>(`/oa/${bizType}`, { params: { q, scope, page, size } })),
  oaSubmit: (bizType: string, payload: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>(`/oa/${bizType}`, payload)),

  // 考勤
  punch: (type: string, location?: string) =>
    unwrap(http.post<ApiResp<boolean>>('/attendance/punch', { type, location })),
  punches: (q: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/attendance/punches', { params: { q, scope, page, size } })),
  attendanceDaily: (q: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/attendance/daily', { params: { q, scope, page, size } })),
  shifts: (q: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/attendance/shifts', { params: { q, page, size } })),
  createShift: (body: { name: string; workStart?: string; workEnd?: string; lateThreshold?: number }) =>
    unwrap(http.post<ApiResp<boolean>>('/attendance/shifts', body)),
  schedules: (q: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/attendance/schedules', { params: { q, scope, page, size } })),
  createSchedule: (body: { employeeId?: string; shiftId?: string; workDate?: string }) =>
    unwrap(http.post<ApiResp<boolean>>('/attendance/schedules', body)),

  // 人事事件：regular 转正 / transfer 调岗 / promo 晋升 / dimission 离职 / entry 入职 / contract 合同
  hrList: (kind: string, q: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>(`/hr/${kind}`, { params: { q, scope, page, size } })),
  hrSubmit: (kind: string, payload: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>(`/hr/${kind}`, payload)),
  confirmEntry: (id: string) => unwrap(http.post<ApiResp<boolean>>(`/hr/entry/${id}/confirm`)),

  // 薪资
  payrollList: (period: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/payroll', { params: { period, scope, page, size } })),
  payrollGenerate: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<boolean>>('/payroll', body)),

  // 个人中心
  profile: () => unwrap(http.get<ApiResp<Record<string, unknown>>>('/profile')),

  // 系统管理
  auditLogs: (q: string | undefined, module: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/system/audit-logs', { params: { q, module, page, size } })),
  roles: () => unwrap(http.get<ApiResp<Record<string, string | null>[]>>('/system/roles')),
  permissions: () => unwrap(http.get<ApiResp<Record<string, string | null>[]>>('/system/permissions')),
  rolePermissions: (code: string) => unwrap(http.get<ApiResp<string[]>>(`/system/roles/${code}/permissions`)),
  assignPermissions: (code: string, codes: string[]) =>
    unwrap(http.post<ApiResp<boolean>>(`/system/roles/${code}/permissions`, { codes })),
  workflows: () => unwrap(http.get<ApiResp<Record<string, string | null>[]>>('/system/workflows')),
  workflowNodes: (id: string) => unwrap(http.get<ApiResp<Record<string, string | null>[]>>(`/system/workflows/${id}/nodes`)),

  // 基础数据
  baseOverview: () => unwrap(http.get<ApiResp<Record<string, string | null>[]>>('/basedata/overview')),
  baseList: (kind: string, q: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>(`/basedata/${kind}`, { params: { q, page, size } })),
  baseCreate: (kind: string, payload: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>(`/basedata/${kind}`, payload)),

  // 客户（数据范围：mine 我的 / dept 本部门 / all 全部）
  customers: (q: string | undefined, scope: string | undefined, level: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/customers', { params: { q, scope, level, page, size } })),
  createCustomer: (body: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>('/customers', body)),

  // 库存（移动加权成本）
  stock: (q: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/inventory/stock', { params: { q, page, size } })),
  invTxns: (q: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/inventory/txns', { params: { q, page, size } })),
  inbound: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<boolean>>('/inventory/inbound', body)),
  outbound: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<boolean>>('/inventory/outbound', body)),

  // 采购入库（审核 → 库存 + 应付）
  inbounds: (q: string | undefined, status: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/purchase/inbounds', { params: { q, status, page, size } })),
  createInbound: (body: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>('/purchase/inbounds', body)),
  auditInbound: (id: string) => unwrap(http.post<ApiResp<boolean>>(`/purchase/inbounds/${id}/audit`)),

  // 销售出库（审核 → 库存 + 应收）
  outbounds: (q: string | undefined, status: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/sales/outbounds', { params: { q, status, scope, page, size } })),
  createOutbound: (body: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>('/sales/outbounds', body)),
  auditOutbound: (id: string) => unwrap(http.post<ApiResp<boolean>>(`/sales/outbounds/${id}/audit`)),

  // 财务台账与核销
  arList: (q: string | undefined, status: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/finance/ar', { params: { q, status, page, size } })),
  apList: (q: string | undefined, status: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/finance/ap', { params: { q, status, page, size } })),
  paymentList: (page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/finance/payments', { params: { page, size } })),
  createPayment: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<boolean>>('/finance/payment', body)),

  // 订单（purchase 采购 / sales 销售）
  poList: (q: string | undefined, status: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/orders/purchase', { params: { q, status, page, size } })),
  createPo: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<Record<string, string | null>>>('/orders/purchase', body)),
  soList: (q: string | undefined, status: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/orders/sales', { params: { q, status, scope, page, size } })),
  createSo: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<Record<string, string | null>>>('/orders/sales', body)),
  submitPo: (id: string) => unwrap(http.post<ApiResp<Record<string, string | null>>>(`/orders/purchase/${id}/submit`)),
  submitSo: (id: string) => unwrap(http.post<ApiResp<Record<string, string | null>>>(`/orders/sales/${id}/submit`)),

  // 盘点
  stocktakes: (q: string | undefined, status: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/inventory/stocktake', { params: { q, status, page, size } })),
  createStocktake: (body: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>('/inventory/stocktake', body)),
  stocktakeItems: (id: string) => unwrap(http.get<ApiResp<Record<string, string | null>[]>>(`/inventory/stocktake/${id}/items`)),
  inputStocktake: (id: string, lines: Record<string, unknown>[]) =>
    unwrap(http.post<ApiResp<boolean>>(`/inventory/stocktake/${id}/input`, lines)),
  auditStocktake: (id: string) => unwrap(http.post<ApiResp<boolean>>(`/inventory/stocktake/${id}/audit`)),

  // 批次 / 库位
  batches: (q: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/inventory/batches', { params: { q, page, size } })),
  createBatch: (body: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>('/inventory/batches', body)),
  expiringBatches: () => unwrap(http.get<ApiResp<Record<string, string | null>[]>>('/inventory/batches/expiring')),

  // 趋势 / 预警（看板深化已在 overview 内，此处为独立查询入口）
  exportUrl: (type: string) => `/export/${type}`,

  // 报销
  expenses: (q: string | undefined, status: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/expenses', { params: { q, status, scope, page, size } })),
  createExpense: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<Record<string, string | null>>>('/expenses', body)),
  submitExpense: (id: string) => unwrap(http.post<ApiResp<Record<string, string | null>>>(`/expenses/${id}/submit`)),
  payExpense: (id: string) => unwrap(http.post<ApiResp<boolean>>(`/expenses/${id}/pay`)),

  // CRM：线索 / 商机 / 合同
  leads: (q: string | undefined, status: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/crm/leads', { params: { q, status, scope, page, size } })),
  createLead: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<Record<string, string | null>>>('/crm/leads', body)),
  convertLead: (id: string, level?: string, creditLimit?: number, terms?: number) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>(`/crm/leads/${id}/convert`, null, { params: { level, creditLimit, terms } })),
  opportunities: (q: string | undefined, status: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/crm/opportunities', { params: { q, status, scope, page, size } })),
  createOpportunity: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<Record<string, string | null>>>('/crm/opportunities', body)),
  updateOppStage: (id: string, stage: string, lostReason?: string) =>
    unwrap(http.post<ApiResp<boolean>>(`/crm/opportunities/${id}/stage?stage=${stage}${lostReason ? `&lostReason=${encodeURIComponent(lostReason)}` : ''}`)),
  convertOpportunity: (id: string) => unwrap(http.post<ApiResp<Record<string, string | null>>>(`/crm/opportunities/${id}/convert`)),
  contracts: (q: string | undefined, status: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/crm/contracts', { params: { q, status, scope, page, size } })),
  createContract: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<Record<string, string | null>>>('/crm/contracts', body)),

  // 采购申请
  purchaseRequests: (q: string | undefined, status: string | undefined, scope: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/purchase/requests', { params: { q, status, scope, page, size } })),
  createPurchaseRequest: (body: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>('/purchase/requests', body)),
  submitPurchaseRequest: (id: string) => unwrap(http.post<ApiResp<Record<string, string | null>>>(`/purchase/requests/${id}/submit`)),
  purchaseRequestToOrder: (id: string) => unwrap(http.post<ApiResp<Record<string, string | null>>>(`/purchase/requests/${id}/to-order`)),

  // 自定义报表
  reports: (q: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/reports', { params: { q, page, size } })),
  reportModules: () => unwrap(http.get<ApiResp<string[]>>('/reports/modules')),
  createReport: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<Record<string, string | null>>>('/reports', body)),
  deleteReport: (id: string) => unwrap(http.delete<ApiResp<boolean>>(`/reports/${id}`)),
  runReport: (id: string) => unwrap(http.get<ApiResp<ReportResult>>(`/reports/${id}/run`)),
  previewReport: (module: string) => unwrap(http.get<ApiResp<ReportResult>>(`/reports/preview?module=${module}`)),

  // 附件（报销发票 / 请假证明 / 合同扫描件）
  attachments: (bizType: string, bizId: string) =>
    unwrap(http.get<ApiResp<Record<string, string | null>[]>>('/attachments', { params: { bizType, bizId } })),
  uploadAttachment: (file: File, bizType: string, bizId: string, remark?: string) => {
    const fd = new FormData()
    fd.append('file', file)
    return unwrap(
      http.post<ApiResp<Record<string, string | null>>>('/attachments/upload', fd, {
        params: { bizType, bizId, remark },
        headers: { 'Content-Type': 'multipart/form-data' },
      }),
    )
  },
  attachmentUrl: (id: string) => `/attachments/${id}/download`,
  deleteAttachment: (id: string) => unwrap(http.delete<ApiResp<boolean>>(`/attachments/${id}`)),

  // 单据打印
  printBill: (type: string, id: string) => unwrap(http.get<ApiResp<PrintBill>>(`/print/${type}/${id}`)),
  printPdfUrl: (type: string, id: string) => `/print/${type}/${id}/pdf`,
  printPdfBlob: async (type: string, id: string) => {
    const res = await http.get(`/print/${type}/${id}/pdf`, { responseType: 'blob' })
    return res.data as Blob
  },

  // 审批流程可视化配置
  wfDefs: () => unwrap(http.get<ApiResp<Record<string, string | null>[]>>('/workflow/config/definitions')),
  wfDetail: (code: string) => unwrap(http.get<ApiResp<{ nodes: Record<string, string | null>[]; runningCount: string }>>(`/workflow/config/definitions/${code}`)),
  wfSaveNodes: (code: string, nodes: unknown[]) => unwrap(http.post<ApiResp<boolean>>(`/workflow/config/definitions/${code}/nodes`, nodes)),
  wfCreateDef: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<boolean>>('/workflow/config/definitions', body)),
  wfSetStatus: (code: string, status: string) =>
    unwrap(http.post<ApiResp<boolean>>(`/workflow/config/definitions/${code}/status?status=${status}`)),
  wfRoles: () => unwrap(http.get<ApiResp<Record<string, string | null>[]>>('/workflow/config/roles')),

  // 多币种与汇率
  currencies: () => unwrap(http.get<ApiResp<Record<string, string | null>[]>>('/fx/currencies')),
  saveCurrency: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<Record<string, string | null>>>('/fx/currencies', body)),
  setBaseCurrency: (code: string) => unwrap(http.post<ApiResp<boolean>>(`/fx/currencies/${code}/base`)),
  toggleCurrency: (code: string, enabled: boolean) =>
    unwrap(http.post<ApiResp<boolean>>(`/fx/currencies/${code}/toggle?enabled=${enabled}`)),
  fxRates: (currency: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/fx/rates', { params: { currency, page, size } })),
  upsertFxRate: (body: Record<string, unknown>) => unwrap(http.post<ApiResp<boolean>>('/fx/rates', body)),
  deleteFxRate: (id: string) => unwrap(http.delete<ApiResp<boolean>>(`/fx/rates/${id}`)),
  fxConvert: (amount: number, from: string, to: string, date?: string) =>
    unwrap(http.get<ApiResp<Record<string, string | null>>>('/fx/convert', { params: { amount, from, to, date } })),

  /** Excel 导出：返回 blob 供前端触发下载 */
  exportFile: async (type: string) => {
    const res = await http.get(`/export/${type}`, { responseType: 'blob' })
    return res.data as Blob
  },
  /** 触发文件下载（配合 exportFile 使用） */
  saveBlob: (blob: Blob, filename: string) => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  },

  // 退货
  salesReturns: (q: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/returns/sales', { params: { q, page, size } })),
  createSalesReturn: (body: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>('/returns/sales', body)),
  auditSalesReturn: (id: string) => unwrap(http.post<ApiResp<boolean>>(`/returns/sales/${id}/audit`)),
  purchaseReturns: (q: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/returns/purchase', { params: { q, page, size } })),
  createPurchaseReturn: (body: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>('/returns/purchase', body)),
  auditPurchaseReturn: (id: string) => unwrap(http.post<ApiResp<boolean>>(`/returns/purchase/${id}/audit`)),

  // 发票与对账
  invoices: (type: string | undefined, q: string | undefined, page: number, size: number) =>
    unwrap(http.get<ApiResp<PageResult<Record<string, string | null>>>>('/finance/invoices', { params: { type, q, page, size } })),
  createInvoice: (body: Record<string, unknown>) =>
    unwrap(http.post<ApiResp<Record<string, string | null>>>('/finance/invoices', body)),
  reconPurchase: () => unwrap(http.get<ApiResp<Record<string, string | null>[]>>('/finance/recon/purchase')),
  reconSales: () => unwrap(http.get<ApiResp<Record<string, string | null>[]>>('/finance/recon/sales')),
}

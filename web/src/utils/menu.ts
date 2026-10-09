/**
 * 菜单单点配置。
 *
 * 背景：菜单原先硬编码在 Layout.vue 内（约 100 行），与 router 各写一份，
 * 且顶栏搜索拿不到这份数据。抽出来之后，侧边栏渲染与顶栏全局搜索共用同
 * 一份数据（#26 / #28），后续若与 router meta 联动也只需改这一处。
 */

import { findEntity } from './entityForms'

export interface MenuItem {
  id: string
  label: string
  icon: string
  path: string
  /** 为空表示所有角色可见 */
  roles: string[]
}

export interface MenuGroup {
  group: string
  items: MenuItem[]
}

export const MENU: MenuGroup[] = [
  {
    group: '通用',
    items: [
      { id: 'dashboard', label: '工作台', icon: 'dashboard', path: '/', roles: [] },
      { id: 'todos', label: '待办中心', icon: 'check-square', path: '/todos', roles: [] },
      { id: 'notices', label: '消息中心', icon: 'bell', path: '/notices', roles: [] },
    ],
  },
  {
    group: 'OA 办公',
    items: [
      { id: 'departments', label: '部门管理', icon: 'building', path: '/departments', roles: ['boss', 'manager', 'hr'] },
      { id: 'employees', label: '员工档案', icon: 'user', path: '/employees', roles: ['boss', 'manager', 'hr'] },
      { id: 'leaves', label: '请假申请', icon: 'calendar', path: '/leaves', roles: [] },
      { id: 'overtime', label: '加班', icon: 'clock', path: '/oa/overtime', roles: [] },
      { id: 'appeal', label: '补卡申诉', icon: 'edit', path: '/oa/appeal', roles: [] },
      { id: 'outing', label: '外出', icon: 'map-pin', path: '/oa/outing', roles: [] },
      { id: 'trip', label: '出差', icon: 'plane', path: '/oa/trip', roles: [] },
    ],
  },
  {
    group: '组织人事',
    items: [
      { id: 'hr_entry', label: '入职办理', icon: 'user-plus', path: '/hr/entry', roles: ['boss', 'manager', 'hr'] },
      { id: 'hr_regular', label: '转正', icon: 'award', path: '/hr/regular', roles: ['boss', 'manager', 'hr'] },
      { id: 'hr_transfer', label: '调岗', icon: 'refresh', path: '/hr/transfer', roles: ['boss', 'manager', 'hr'] },
      { id: 'hr_promo', label: '晋升', icon: 'arrow-up', path: '/hr/promo', roles: ['boss', 'manager', 'hr'] },
      { id: 'hr_dimission', label: '离职/辞退', icon: 'user-minus', path: '/hr/dimission', roles: ['boss', 'manager', 'hr'] },
      { id: 'hr_contract', label: '劳动合同', icon: 'file-text', path: '/hr/contract', roles: ['boss', 'manager', 'hr'] },
      { id: 'payroll', label: '薪资核算', icon: 'wallet', path: '/payroll', roles: ['boss', 'finance', 'hr'] },
    ],
  },
  {
    group: '考勤管理',
    items: [
      { id: 'att_punch', label: '打卡记录', icon: 'map-pin', path: '/attendance/punch', roles: [] },
      { id: 'att_daily', label: '日考勤结果', icon: 'calendar', path: '/attendance/daily', roles: [] },
      { id: 'att_shift', label: '班次定义', icon: 'clock', path: '/attendance/shift', roles: ['boss', 'manager', 'hr'] },
      { id: 'att_schedule', label: '排班', icon: 'calendar', path: '/attendance/schedule', roles: ['boss', 'manager', 'hr'] },
      { id: 'att_duty', label: '排班日历', icon: 'calendar', path: '/attendance/duty-calendar', roles: ['boss', 'manager', 'hr'] },
    ],
  },
  {
    group: 'CRM 客户',
    items: [
      { id: 'lead', label: '线索管理', icon: 'target', path: '/crm/leads', roles: ['boss', 'manager', 'finance', 'sales'] },
      { id: 'customer', label: '客户管理', icon: 'building', path: '/customers', roles: ['boss', 'manager', 'finance', 'sales'] },
      { id: 'opportunity', label: '商机管理', icon: 'trending-up', path: '/crm/opportunities', roles: ['boss', 'manager', 'finance', 'sales'] },
      { id: 'contract', label: '合同管理', icon: 'file-text', path: '/crm/contracts', roles: ['boss', 'manager', 'finance', 'sales'] },
    ],
  },
  {
    group: '库存',
    items: [
      { id: 'inventory', label: '库存管理', icon: 'package', path: '/inventory', roles: ['boss', 'manager', 'finance', 'sales'] },
      { id: 'stocktake', label: '库存盘点', icon: 'check-square', path: '/inventory/stocktake', roles: ['boss', 'manager', 'finance', 'sales'] },
      { id: 'batch', label: '批次/库位', icon: 'box', path: '/inventory/batches', roles: ['boss', 'manager', 'finance', 'sales'] },
      { id: 'returns', label: '退货管理', icon: 'rotate-ccw', path: '/returns', roles: ['boss', 'manager', 'finance', 'sales'] },
    ],
  },
  {
    group: '采购',
    items: [
      { id: 'purchase_req', label: '采购申请', icon: 'edit', path: '/purchase/requests', roles: ['boss', 'manager', 'finance', 'purchase'] },
      { id: 'purchase_order', label: '采购订单', icon: 'shopping-cart', path: '/orders/purchase', roles: ['boss', 'manager', 'finance', 'purchase'] },
      { id: 'purchase_inbound', label: '采购入库', icon: 'package', path: '/purchase/inbounds', roles: ['boss', 'manager', 'finance', 'purchase'] },
    ],
  },
  {
    group: '销售',
    items: [
      { id: 'sales_order', label: '销售订单', icon: 'briefcase', path: '/orders/sales', roles: ['boss', 'manager', 'finance', 'sales'] },
      { id: 'sales_outbound', label: '销售出库', icon: 'truck', path: '/sales/outbounds', roles: ['boss', 'manager', 'finance', 'sales'] },
    ],
  },
  {
    group: '财务',
    items: [
      { id: 'finance', label: '应收应付', icon: 'wallet', path: '/finance', roles: ['boss', 'manager', 'finance'] },
      { id: 'invoices', label: '发票与对账', icon: 'receipt', path: '/finance/invoices', roles: ['boss', 'manager', 'finance'] },
      { id: 'expense', label: '报销', icon: 'receipt', path: '/expenses', roles: [] },
    ],
  },
  {
    group: '报表与分析',
    items: [
      { id: 'report', label: '自定义报表', icon: 'bar-chart', path: '/reports', roles: ['boss', 'manager', 'finance'] },
    ],
  },
  {
    group: '系统管理',
    items: [
      { id: 'role_perm', label: '角色与权限', icon: 'shield', path: '/system/role-perm', roles: ['boss', 'manager', 'finance'] },
      { id: 'workflow_cfg', label: '审批流程配置', icon: 'git-branch', path: '/system/workflow-designer', roles: ['boss', 'manager', 'finance'] },
      { id: 'workflow_def', label: '流程定义列表', icon: 'list', path: '/system/workflow-cfg', roles: ['boss', 'manager', 'finance'] },
      { id: 'fx', label: '多币种汇率', icon: 'dollar-sign', path: '/system/fx', roles: ['boss', 'manager', 'finance'] },
      { id: 'basedata', label: '基础数据', icon: 'box', path: '/basedata', roles: ['boss', 'manager', 'finance', 'hr'] },
      { id: 'audit_log', label: '操作日志', icon: 'list', path: '/system/audit-logs', roles: ['boss', 'manager', 'finance'] },
    ],
  },
]

/** 不在菜单中的路由标题兜底（打印页等） */
export const EXTRA_TITLE: Record<string, string> = {
  '/print': '单据打印',
  '/system/workflow-cfg': '审批流程配置',
  // 新建页不在菜单中，需兜底，否则面包屑会回退成「工作台」
  '/leaves/new': '发起请假',
  '/employees/new': '新建员工',
}

/** 扁平菜单项 */
export function flatMenu(): MenuItem[] {
  return MENU.flatMap((g) => g.items)
}

/** 按角色过滤可见菜单，并剔除空分组 */
export function visibleMenu(roles: string[]): MenuGroup[] {
  return MENU.map((g) => ({
    ...g,
    items: g.items.filter((i) => i.roles.length === 0 || i.roles.some((r) => roles.includes(r))),
  })).filter((g) => g.items.length > 0)
}

/**
 * 当前路由标题：菜单命中 → EXTRA_TITLE 前缀兜底 → 工作台
 *
 * #27 复核结论（2026-10-09）：多态路由 `oa/:bizType`、`attendance/:kind`、`hr/:kind`
 * 解析出的 route.path（/oa/overtime、/attendance/punch、/hr/entry …）与 MENU 中的
 * path 字面完全一致，精确匹配即可命中 → 面包屑与侧边栏高亮**实际均正常**。
 * 因此**决定不再拆分为真实子路由**（拆分收益低于预期），
 * 仅在未来需要按 kind 单独配置 meta（如独立权限/标题）时再拆。
 * 注：`attendance/duty-calendar` 是独立字面路由且定义在 `:kind` 之前，不会被参数路由误匹配。
 */
export function titleOf(path: string): string {
  const hit = flatMenu().find((i) => i.path === path)?.label
  if (hit) return hit

  // 统一新建页 /new/:base/:variant? → 直接用注册表里的标题（已含动作词，如「新建采购订单」「发起报销」）
  if (path.startsWith('/new/')) {
    const [base, variant] = path.slice('/new/'.length).split('/').filter(Boolean)
    const key = variant ? `${base}:${variant}` : (base ?? '')
    return (key && findEntity(key)?.title) || '新建单据'
  }

  const extra = Object.keys(EXTRA_TITLE).find((p) => path.startsWith(p))
  return extra ? EXTRA_TITLE[extra] : '工作台'
}

/** 顶栏全局搜索：按菜单名称匹配，供快速跳转 */
export function searchMenu(q: string, roles: string[], limit = 8): MenuItem[] {
  const kw = q.trim().toLowerCase()
  if (!kw) return []
  return flatMenu()
    .filter((i) => i.roles.length === 0 || i.roles.some((r) => roles.includes(r)))
    .filter((i) => i.label.toLowerCase().includes(kw) || i.path.toLowerCase().includes(kw))
    .slice(0, limit)
}

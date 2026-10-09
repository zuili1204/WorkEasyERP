/** 全局统一字典：原先 STATUS_MAP / TYPES 在 6 个视图里各抄一份，文案改一处漏五处 */

export interface StatusCell {
  text: string
  cls: string
}

const GRAY: StatusCell = { text: '未知', cls: 'badge-gray' }

/** 工作流 / 审批状态：请假、假勤、人事事件、待办、工作台通用 */
export const WORKFLOW_STATUS: Record<string, StatusCell> = {
  pending: { text: '审批中', cls: 'badge-orange' },
  running: { text: '审批中', cls: 'badge-orange' },
  approved: { text: '已通过', cls: 'badge-green' },
  rejected: { text: '已驳回', cls: 'badge-red' },
  canceled: { text: '已撤销', cls: 'badge-gray' },
  cancel: { text: '已撤销', cls: 'badge-gray' },
}

/** 考勤日结果状态 */
export const ATTENDANCE_STATUS: Record<string, StatusCell> = {
  normal: { text: '正常', cls: 'badge-green' },
  late: { text: '迟到', cls: 'badge-orange' },
  early: { text: '早退', cls: 'badge-orange' },
  absent: { text: '缺勤', cls: 'badge-red' },
  leave: { text: '请假', cls: 'badge-blue' },
}

/** 员工在职状态（与 StatusBadge 组件口径对齐） */
export const EMPLOYEE_STATUS: Record<string, StatusCell> = {
  active: { text: '在职', cls: 'badge-green' },
  probation: { text: '试用期', cls: 'badge-orange' },
  resigned: { text: '离职', cls: 'badge-gray' },
  deleted: { text: '已删除', cls: 'badge-red' },
  normal: { text: '正常', cls: 'badge-blue' },
}

/** 请假类型 */
export const LEAVE_TYPES: Array<[string, string]> = [
  ['annual', '年假'],
  ['sick', '病假'],
  ['personal', '事假'],
  ['marriage', '婚假'],
  ['maternity', '产假'],
]

/**
 * 取状态单元格：未知状态给出可读兜底，避免直接把原始状态码暴露到界面。
 * fallbackText 为空时回显原始值，便于排查脏数据。
 */
export function statusOf(map: Record<string, StatusCell>, key?: string | null, raw = true): StatusCell {
  if (!key) return GRAY
  return map[key] ?? (raw ? { text: key, cls: 'badge-gray' } : GRAY)
}

/** 从 [value, label] 型字典里取文案 */
export function labelOf(list: Array<[string, string]>, value?: string | null): string {
  if (!value) return '—'
  return list.find(([v]) => v === value)?.[1] ?? value
}

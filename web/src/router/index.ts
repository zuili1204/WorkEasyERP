import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '../stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
  {
    path: '/',
    component: () => import('../layout/Layout.vue'),
    children: [
      { path: '', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
      { path: 'employees', name: 'employees', component: () => import('../views/EmployeeView.vue') },
      { path: 'departments', name: 'departments', component: () => import('../views/DepartmentView.vue') },
      { path: 'leaves', name: 'leaves', component: () => import('../views/LeaveView.vue') },
      { path: 'todos', name: 'todos', component: () => import('../views/TodoView.vue') },
      { path: 'notices', name: 'notices', component: () => import('../views/NoticeView.vue') },
      { path: 'oa/:bizType', name: 'oa', component: () => import('../views/OaApplyView.vue') },
      { path: 'attendance/:kind', name: 'attendance', component: () => import('../views/AttendanceView.vue') },
      { path: 'hr/:kind', name: 'hr', component: () => import('../views/HrView.vue') },
      { path: 'payroll', name: 'payroll', component: () => import('../views/PayrollView.vue') },
      { path: 'profile', name: 'profile', component: () => import('../views/ProfileView.vue') },
      { path: 'system/audit-logs', name: 'audit-logs', component: () => import('../views/AuditLogView.vue') },
      { path: 'system/role-perm', name: 'role-perm', component: () => import('../views/RolePermView.vue') },
      { path: 'system/workflow-cfg', name: 'workflow-cfg', component: () => import('../views/WorkflowCfgView.vue') },
      { path: 'system/workflow-designer', name: 'workflow-designer', component: () => import('../views/WorkflowDesignerView.vue') },
      { path: 'system/fx', name: 'fx', component: () => import('../views/FxView.vue') },
      { path: 'print/:type/:id', name: 'print', component: () => import('../views/PrintPreviewView.vue') },
      { path: 'basedata', name: 'basedata', component: () => import('../views/BaseDataView.vue') },
      { path: 'customers', name: 'customers', component: () => import('../views/CustomerView.vue') },
      { path: 'inventory', name: 'inventory', component: () => import('../views/InventoryView.vue') },
      { path: 'inventory/stocktake', name: 'stocktake', component: () => import('../views/StocktakeView.vue') },
      { path: 'inventory/batches', name: 'batches', component: () => import('../views/BatchView.vue') },
      { path: 'purchase/inbounds', name: 'inbounds', component: () => import('../views/FulfillmentView.vue') },
      { path: 'sales/outbounds', name: 'outbounds', component: () => import('../views/FulfillmentView.vue') },
      { path: 'finance', name: 'finance', component: () => import('../views/FinanceView.vue') },
      { path: 'orders/purchase', name: 'po', component: () => import('../views/OrderView.vue') },
      { path: 'orders/sales', name: 'so', component: () => import('../views/OrderView.vue') },
      { path: 'returns', name: 'returns', component: () => import('../views/ReturnView.vue') },
      { path: 'finance/invoices', name: 'invoices', component: () => import('../views/InvoiceView.vue') },
      { path: 'expenses', name: 'expenses', component: () => import('../views/ExpenseView.vue') },
      { path: 'crm/leads', name: 'leads', component: () => import('../views/LeadView.vue') },
      { path: 'crm/opportunities', name: 'opportunities', component: () => import('../views/OpportunityView.vue') },
      { path: 'crm/contracts', name: 'contracts', component: () => import('../views/ContractView.vue') },
      { path: 'purchase/requests', name: 'purchase-requests', component: () => import('../views/PurchaseReqView.vue') },
      { path: 'reports', name: 'reports', component: () => import('../views/ReportView.vue') },
    ],
  },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.path !== '/login' && !auth.token) return '/login'
  if (to.path === '/login' && auth.token) return '/'
  return true
})

export default router

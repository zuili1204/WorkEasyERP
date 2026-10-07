<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '../stores/auth'
import { api } from '../api'
import Icon from '../components/Icon.vue'

const router = useRouter()
const route = useRoute()

/** 菜单：roles 为空表示所有角色可见（与原型 MENU 一致，M0 仅放开已实现页面） */
const MENU = [
  {
    group: '通用',
    items: [
      { id: 'dashboard', label: '工作台', icon: 'dashboard', path: '/', roles: [] as string[] },
      { id: 'todos', label: '待办中心', icon: 'check-square', path: '/todos', roles: [] as string[] },
      { id: 'notices', label: '消息中心', icon: 'bell', path: '/notices', roles: [] as string[] },
    ],
  },
  {
    group: 'OA 办公',
    items: [
      { id: 'departments', label: '部门管理', icon: 'building', path: '/departments', roles: ['boss', 'manager', 'hr'] },
      { id: 'employees', label: '员工档案', icon: 'user', path: '/employees', roles: ['boss', 'manager', 'hr'] },
      { id: 'leaves', label: '请假申请', icon: 'calendar', path: '/leaves', roles: [] as string[] },
      { id: 'overtime', label: '加班', icon: 'clock', path: '/oa/overtime', roles: [] as string[] },
      { id: 'appeal', label: '补卡申诉', icon: 'edit', path: '/oa/appeal', roles: [] as string[] },
      { id: 'outing', label: '外出', icon: 'map-pin', path: '/oa/outing', roles: [] as string[] },
      { id: 'trip', label: '出差', icon: 'plane', path: '/oa/trip', roles: [] as string[] },
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
      { id: 'att_punch', label: '打卡记录', icon: 'map-pin', path: '/attendance/punch', roles: [] as string[] },
      { id: 'att_daily', label: '日考勤结果', icon: 'calendar', path: '/attendance/daily', roles: [] as string[] },
      { id: 'att_shift', label: '班次定义', icon: 'clock', path: '/attendance/shift', roles: ['boss', 'manager', 'hr'] },
      { id: 'att_schedule', label: '排班', icon: 'calendar', path: '/attendance/schedule', roles: ['boss', 'manager', 'hr'] },
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
      { id: 'expense', label: '报销', icon: 'receipt', path: '/expenses', roles: [] as string[] },
    ],
  },
  {
    group: '报表与分析',
    icon: 'bar-chart',
    items: [
      { id: 'report', label: '自定义报表', icon: 'bar-chart', path: '/reports', roles: ['boss', 'manager', 'finance'] },
    ],
  },
  {
    group: '系统管理',
    items: [
      { id: 'role_perm', label: '角色与权限', icon: 'shield', path: '/system/role-perm', roles: ['boss', 'manager', 'finance'] },
      { id: 'workflow_cfg', label: '审批流程配置', icon: 'git-branch', path: '/system/workflow-designer', roles: ['boss', 'manager', 'finance'] },
      { id: 'fx', label: '多币种汇率', icon: 'dollar-sign', path: '/system/fx', roles: ['boss', 'manager', 'finance'] },
      { id: 'basedata', label: '基础数据', icon: 'box', path: '/basedata', roles: ['boss', 'manager', 'finance', 'hr'] },
      { id: 'audit_log', label: '操作日志', icon: 'list', path: '/system/audit-logs', roles: ['boss', 'manager', 'finance'] },
    ],
  },
]

const menuOpen = ref(false)
const searchRef = ref<HTMLInputElement | null>(null)
const unread = ref(0)

async function loadUnread() {
  try {
    unread.value = (await api.unreadCount()).count
  } catch {
    unread.value = 0
  }
}

const visibleMenu = computed(() =>
  MENU.map((g) => ({
    ...g,
    items: g.items.filter((i) => i.roles.length === 0 || i.roles.some((r) => auth.user?.roles.includes(r))),
  })).filter((g) => g.items.length > 0),
)

const currentTitle = computed(() => {
  const all = MENU.flatMap((g) => g.items)
  return all.find((i) => i.path === route.path)?.label ?? '工作台'
})

function logout() {
  auth.clear()
  router.push('/login')
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') menuOpen.value = false
  if (e.key === '/' && (e.target as HTMLElement)?.tagName !== 'INPUT') {
    e.preventDefault()
    searchRef.value?.focus()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKey)
  loadUnread()
})
// 路由切换后刷新未读数（审批/提交会产生新消息）
watch(() => route.fullPath, () => loadUnread())
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="app">
    <aside class="sidebar">
      <div class="side-logo">
        <div class="logo">W</div>
        <span>WorkEasyERP</span>
      </div>

      <nav class="side-nav">
        <template v-for="g in visibleMenu" :key="g.group">
          <div class="nav-group-title">{{ g.group }}</div>
          <router-link
            v-for="i in g.items"
            :key="i.id"
            :to="i.path"
            class="nav-item"
            active-class="active"
          >
            <Icon :name="i.icon" :size="18" />
            <span class="nav-txt">{{ i.label }}</span>
          </router-link>
        </template>
      </nav>

      <div class="side-user" @click="menuOpen = !menuOpen">
        <div class="ava">{{ auth.user?.displayName?.[0] ?? 'U' }}</div>
        <div>
          <div class="nm">{{ auth.user?.displayName }}</div>
          <div class="rl">{{ auth.user?.deptName || (auth.user?.roles || []).join(',') }}</div>
        </div>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div class="crumb">
          <span class="root"><Icon name="dashboard" :size="15" />工作台</span>
          <span class="sep">/</span>
          <span class="cur">{{ currentTitle }}</span>
        </div>

        <div class="search">
          <Icon name="search" :size="16" />
          <input ref="searchRef" placeholder="搜索单据 / 客户 / 员工…" />
        </div>

        <div class="top-actions">
          <span class="ic" title="通知" @click="router.push('/notices')">
            <Icon name="bell" :size="18" />
            <span v-if="unread > 0" class="dot">{{ unread > 99 ? '99+' : unread }}</span>
          </span>
          <div class="ava-top" @click="menuOpen = !menuOpen">
            {{ auth.user?.displayName?.[0] ?? 'U' }}
          </div>
        </div>

        <div v-if="menuOpen" class="user-menu" @mouseleave="menuOpen = false">
          <div class="um-head">
            <div class="ava">{{ auth.user?.displayName?.[0] }}</div>
            <div>
              <div class="nm">{{ auth.user?.displayName }}</div>
              <div class="rl">{{ auth.user?.deptName || '—' }} · {{ (auth.user?.roles || []).join(',') }}</div>
            </div>
          </div>
          <div class="um-item" @click="router.push('/profile'); menuOpen = false">
            <Icon name="user" :size="16" /> 个人中心
          </div>
          <div class="um-item danger" @click="logout">
            <Icon name="log-out" :size="16" /> 退出登录
          </div>
        </div>
      </header>

      <section class="content">
        <router-view />
      </section>
    </main>
  </div>
</template>

<style scoped>
.app { display: flex; height: 100vh; }
.sidebar { width: 232px; background: var(--sidebar); display: flex; flex-direction: column; flex-shrink: 0; }
.side-logo {
  height: 60px; display: flex; align-items: center; gap: 11px; padding: 0 18px;
  border-bottom: 1px solid rgba(255, 255, 255, .06);
  background: linear-gradient(180deg, rgba(79, 70, 229, .10), transparent);
}
.logo {
  width: 34px; height: 34px; border-radius: 10px; background: linear-gradient(135deg, #6366F1, #4F46E5);
  color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 800;
}
.side-logo span { font-weight: 800; font-size: 15px; color: #fff; letter-spacing: .3px; }
.side-nav { flex: 1; overflow-y: auto; padding: 10px 0; }
.nav-group-title {
  padding: 14px 18px 6px; font-size: 10.5px; color: var(--sidebar-weak);
  font-weight: 700; letter-spacing: 1.2px; text-transform: uppercase;
}
.nav-item {
  position: relative; margin: 2px 10px; padding: 9px 12px; border-radius: 9px;
  font-size: 13px; color: var(--sidebar-text);
  display: flex; align-items: center; gap: 11px; cursor: pointer;
  transition: background .18s var(--ease), color .18s var(--ease), transform .18s var(--ease-out);
}
.nav-item svg { transition: transform .18s var(--ease-out); }
.nav-item:hover { background: var(--sidebar-2); color: #fff; transform: translateX(2px); }
.nav-item:hover svg { transform: scale(1.06); }
.nav-item.active { background: var(--sidebar-active); color: #fff; font-weight: 600; box-shadow: 0 2px 8px rgba(79, 70, 229, .35); }
.nav-item.active::before {
  content: ''; position: absolute; left: -10px; top: 50%; transform: translateY(-50%);
  width: 3px; height: 18px; border-radius: 0 3px 3px 0; background: var(--primary-active);
}
.side-user {
  border-top: 1px solid rgba(255, 255, 255, .08); padding: 11px 14px; display: flex;
  align-items: center; gap: 10px; cursor: pointer; transition: .15s;
}
.side-user:hover { background: rgba(255, 255, 255, .04); }
.side-user .ava {
  width: 34px; height: 34px; border-radius: 9px; background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center; font-weight: 700; flex-shrink: 0;
}
.side-user .nm { font-size: 13px; font-weight: 700; color: #fff; line-height: 1.2; }
.side-user .rl { font-size: 11px; color: var(--sidebar-weak); }

.main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.topbar {
  height: 60px; background: var(--surface); border-bottom: 1px solid var(--border);
  display: flex; align-items: center; padding: 0 22px; gap: 14px; flex-shrink: 0; position: relative;
  box-shadow: var(--shadow-xs); z-index: 5;
}
.crumb { display: flex; align-items: center; gap: 7px; font-size: 13px; color: var(--text-3); white-space: nowrap; }
.crumb .root { display: inline-flex; align-items: center; gap: 5px; }
.crumb .cur { color: var(--text); font-weight: 700; }
.crumb .sep { color: var(--text-4); }
.search { flex: 1; max-width: 420px; margin-left: auto; position: relative; }
.search input {
  width: 100%; padding: 8px 12px 8px 36px; border: 1px solid var(--border); border-radius: 20px;
  background: var(--surface-2); transition: .18s; font-size: 13px;
}
.search input:focus { background: #fff; border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-light); outline: none; }
.search svg { position: absolute; left: 12px; top: 9px; color: var(--text-3); }
.top-actions { display: flex; align-items: center; gap: 6px; }
.top-actions .ic { color: var(--text-2); cursor: pointer; border-radius: 9px; padding: 7px; display: flex; }
.top-actions .ic:hover { background: var(--slate-light); color: var(--primary); }
.top-actions .ic { position: relative; }
.top-actions .ic .dot {
  position: absolute; top: 1px; right: 0; min-width: 16px; height: 16px; padding: 0 4px;
  border-radius: 8px; background: var(--danger); color: #fff; font-size: 10px; font-weight: 700;
  display: flex; align-items: center; justify-content: center; box-shadow: 0 0 0 2px #fff;
}
.ava-top {
  width: 34px; height: 34px; border-radius: 9px; background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center; font-weight: 700; cursor: pointer;
}
.user-menu {
  position: absolute; top: 54px; right: 22px; width: 230px; background: #fff;
  border: 1px solid var(--border); border-radius: var(--radius); box-shadow: var(--shadow-lg);
  padding: 6px; z-index: 120;
}
.um-head { padding: 12px; display: flex; gap: 10px; align-items: center; border-bottom: 1px solid var(--border-2); margin-bottom: 6px; }
.um-head .ava {
  width: 40px; height: 40px; border-radius: 10px; background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center; font-weight: 700;
}
.um-head .nm { font-weight: 700; font-size: 14px; }
.um-head .rl { font-size: 12px; color: var(--text-3); }
.um-item {
  display: flex; align-items: center; gap: 10px; padding: 9px 12px; border-radius: 8px;
  cursor: pointer; font-size: 13.5px; color: var(--text-2); transition: .15s;
}
.um-item:hover { background: var(--slate-light); color: var(--primary); }
.um-item.danger:hover { background: var(--danger-light); color: var(--danger); }

.content { flex: 1; overflow-y: auto; padding: 22px 24px; }

@media (max-width: 1100px) {
  .sidebar { width: 64px; }
  .nav-item .nav-txt, .nav-group-title, .side-user .nm, .side-user .rl, .side-logo span { display: none; }
  .nav-item { justify-content: center; padding: 11px 0; }
}
</style>

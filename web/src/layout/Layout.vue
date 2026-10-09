<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '../stores/auth'
import { api } from '../api'
import Icon from '../components/Icon.vue'
import { searchMenu, titleOf, visibleMenu as visibleMenuOf, type MenuItem } from '../utils/menu'

const router = useRouter()
const route = useRoute()

// 菜单已抽取到 utils/menu.ts（侧边栏渲染与顶栏搜索共用同一份数据）

const menuOpen = ref(false)
const searchRef = ref<HTMLInputElement | null>(null)
const unread = ref(0)

/* ---- 顶栏全局搜索（#28）：原先是无任何绑定的装饰性死控件 ---- */
const searchText = ref('')
const searchIndex = ref(0)
const searchHits = computed<MenuItem[]>(() => searchMenu(searchText.value, auth.user?.roles ?? []))
const searchOpen = computed(() => searchText.value.trim().length > 0)

function onSearchInput() {
  searchIndex.value = 0
}

function goSearch(hit?: MenuItem) {
  const target = hit ?? searchHits.value[searchIndex.value]
  if (!target) return
  router.push(target.path)
  searchText.value = ''
}

function moveSearch(step: number) {
  const n = searchHits.value.length
  if (!n) return
  searchIndex.value = (searchIndex.value + step + n) % n
}

function closeSearch() {
  searchText.value = ''
}

/* ---- 侧边栏：分组折叠(#1) + 整栏折叠(#2)，状态持久化到 localStorage ---- */
const GROUPS_KEY = 'we-sidebar-groups'
const MINI_KEY = 'we-sidebar-mini'
const AUTO_MINI_WIDTH = 1100

/** 手动折叠的分组名 */
const collapsedGroups = ref<string[]>(JSON.parse(localStorage.getItem(GROUPS_KEY) || '[]'))
/** 用户手动「收起侧边栏」的偏好 */
const miniPref = ref(localStorage.getItem(MINI_KEY) === '1')
/** 窄屏自动折叠：不写偏好，避免污染用户手动选择 */
const autoMini = ref(false)

const sidebarMini = computed(() => miniPref.value || autoMini.value)

function isGroupCollapsed(group: string) {
  // 整栏收起时图标已是纯图标模式，分组折叠无意义，保持展开
  return !sidebarMini.value && collapsedGroups.value.includes(group)
}
function toggleGroup(group: string) {
  const i = collapsedGroups.value.indexOf(group)
  if (i >= 0) collapsedGroups.value.splice(i, 1)
  else collapsedGroups.value.push(group)
  localStorage.setItem(GROUPS_KEY, JSON.stringify(collapsedGroups.value))
}
function toggleSidebar() {
  miniPref.value = !miniPref.value
  localStorage.setItem(MINI_KEY, miniPref.value ? '1' : '0')
}
function syncAutoMini() {
  autoMini.value = window.innerWidth <= AUTO_MINI_WIDTH
}

async function loadUnread() {
  try {
    unread.value = (await api.unreadCount()).count
  } catch {
    unread.value = 0
  }
}

const visibleMenu = computed(() => visibleMenuOf(auth.user?.roles ?? []))

const currentTitle = computed(() => titleOf(route.path))

function logout() {
  auth.clear()
  router.push('/login')
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    menuOpen.value = false
    closeSearch()
  }
  if (e.key === '/' && (e.target as HTMLElement)?.tagName !== 'INPUT') {
    e.preventDefault()
    searchRef.value?.focus()
  }
}

/** 未读角标轮询：原先仅在路由变化时刷新，停留在同一页时新消息不会体现 */
let unreadTimer: number | undefined

onMounted(() => {
  window.addEventListener('keydown', onKey)
  window.addEventListener('resize', syncAutoMini)
  syncAutoMini()
  loadUnread()
  unreadTimer = window.setInterval(loadUnread, 60_000)
})
// 路由切换后刷新未读数（审批/提交会产生新消息）
watch(() => route.fullPath, () => loadUnread())
onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('resize', syncAutoMini)
  if (unreadTimer) window.clearInterval(unreadTimer)
})
</script>

<template>
  <div class="app">
    <aside class="sidebar" :class="{ mini: sidebarMini }">
      <div class="side-logo">
        <div class="logo">W</div>
        <span v-if="!sidebarMini">WorkEasyERP</span>
        <button
          class="logo-toggle"
          :title="sidebarMini ? '展开侧边栏' : '收起侧边栏'"
          :aria-label="sidebarMini ? '展开侧边栏' : '收起侧边栏'"
          @click="toggleSidebar"
        >
          <Icon :name="sidebarMini ? 'chevron-right' : 'chevron-left'" :size="16" />
        </button>
      </div>

      <nav class="side-nav">
        <template v-for="g in visibleMenu" :key="g.group">
          <button
            class="nav-group-title"
            :title="sidebarMini ? g.group : undefined"
            :aria-expanded="!isGroupCollapsed(g.group)"
            @click="toggleGroup(g.group)"
          >
            <span class="gt-txt">{{ g.group }}</span>
            <Icon
              v-if="!sidebarMini"
              class="gt-chevron"
              :class="{ collapsed: isGroupCollapsed(g.group) }"
              name="chevron-down"
              :size="13"
            />
          </button>
          <router-link
            v-for="i in g.items"
            v-show="!isGroupCollapsed(g.group)"
            :key="i.id"
            :to="i.path"
            class="nav-item"
            active-class="active"
            :title="i.label"
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
          <input
            ref="searchRef"
            v-model="searchText"
            placeholder="搜索菜单 / 功能…（/ 聚焦，↑↓ 选择，回车跳转）"
            @input="onSearchInput"
            @keydown.enter.prevent="goSearch()"
            @keydown.down.prevent="moveSearch(1)"
            @keydown.up.prevent="moveSearch(-1)"
            @keydown.esc.prevent="closeSearch"
          />
          <div v-if="searchOpen" class="search-pop">
            <div v-if="searchHits.length === 0" class="search-empty">无匹配项</div>
            <div
              v-for="(h, i) in searchHits"
              :key="h.id"
              class="search-item"
              :class="{ on: i === searchIndex }"
              @mousedown.prevent="goSearch(h)"
            >
              <Icon :name="h.icon" :size="14" />
              <span class="lb">{{ h.label }}</span>
              <span class="path">{{ h.path }}</span>
            </div>
          </div>
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
.sidebar {
  width: 232px; background: var(--sidebar); display: flex; flex-direction: column; flex-shrink: 0;
  transition: width .2s var(--ease);
}
.sidebar.mini { width: 64px; }
.side-logo {
  height: 60px; display: flex; align-items: center; gap: 11px; padding: 0 14px;
  border-bottom: 1px solid rgba(255, 255, 255, .06);
  background: linear-gradient(180deg, rgba(79, 70, 229, .10), transparent);
}
.logo-toggle {
  margin-left: auto; width: 24px; height: 24px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255, 255, 255, .08); border: 0; border-radius: 7px;
  color: var(--sidebar-text); cursor: pointer; transition: .16s var(--ease);
}
.logo-toggle:hover { background: rgba(255, 255, 255, .16); color: #fff; }
.sidebar.mini .logo-toggle { margin-left: 0; }
.logo {
  width: 34px; height: 34px; border-radius: 10px; background: linear-gradient(135deg, #6366F1, #4F46E5);
  color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 800;
}
.side-logo span { font-weight: 800; font-size: 15px; color: #fff; letter-spacing: .3px; }
.side-nav { flex: 1; overflow-y: auto; padding: 10px 0; }
.nav-group-title {
  width: 100%; padding: 14px 18px 6px; font-size: 10.5px; color: var(--sidebar-weak);
  font-weight: 700; letter-spacing: 1.2px; text-transform: uppercase;
  background: none; border: 0; cursor: pointer;
  display: flex; align-items: center; justify-content: space-between; gap: 6px;
  transition: color .16s var(--ease);
}
.nav-group-title:hover { color: #fff; }
.gt-chevron { flex-shrink: 0; transition: transform .2s var(--ease-out); }
.gt-chevron.collapsed { transform: rotate(-90deg); }
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
  box-shadow: var(--shadow-xs); z-index: var(--z-topbar);
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
/* 搜索结果浮层 */
.search-pop {
  position: absolute; top: 38px; left: 0; right: 0; z-index: var(--z-dropdown);
  background: #fff; border: 1px solid var(--border); border-radius: var(--radius-sm);
  box-shadow: var(--shadow-lg); padding: 5px; max-height: 320px; overflow: auto;
}
.search-item {
  display: flex; align-items: center; gap: 8px; padding: 7px 9px;
  border-radius: 7px; cursor: pointer; font-size: 13px; color: var(--text-2);
}
.search-item:hover, .search-item.on { background: var(--primary-light); color: var(--primary-active); }
.search-item .lb { font-weight: 600; }
.search-item .path { margin-left: auto; font-size: 11px; color: var(--text-4); font-family: monospace; }
.search-empty { padding: 12px 10px; font-size: 12.5px; color: var(--text-3); text-align: center; }
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
  padding: 6px; z-index: var(--z-dropdown);
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

/* ---- 收起态：手动 mini 与窄屏 autoMini 均由 .mini 类驱动，避免两套逻辑分叉 ---- */
.sidebar.mini .nav-item .nav-txt,
.sidebar.mini .side-user .nm,
.sidebar.mini .side-user .rl,
.sidebar.mini .side-logo span { display: none; }
.sidebar.mini .nav-item { justify-content: center; padding: 11px 0; }
.sidebar.mini .side-user { justify-content: center; }
/* 收起态下分组标题降级为一条细分隔线，保持分组语义又不占空间 */
.sidebar.mini .nav-group-title { justify-content: center; padding: 7px 0; pointer-events: none; }
.sidebar.mini .nav-group-title .gt-txt { display: none; }
.sidebar.mini .nav-group-title::before {
  content: ''; width: 22px; height: 1px; background: rgba(255, 255, 255, .14);
}

/* 无 JS 首帧兜底：窄屏直接收窄，避免闪现宽侧栏 */
@media (max-width: 1100px) {
  .sidebar:not(.mini) { width: 64px; }
  .sidebar:not(.mini) .nav-item .nav-txt,
  .sidebar:not(.mini) .side-user .nm,
  .sidebar:not(.mini) .side-user .rl,
  .sidebar:not(.mini) .side-logo span { display: none; }
  .sidebar:not(.mini) .nav-item { justify-content: center; padding: 11px 0; }
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import Icon from '../components/Icon.vue'

const roles = ref<Record<string, string | null>[]>([])
const permissions = ref<Record<string, string | null>[]>([])
const selected = ref<string>('boss')
const checked = ref<string[]>([])
const saving = ref(false)

const SCOPE_TEXT: Record<string, string> = {
  all: '全部数据',
  dept: '本部门',
  self: '本人',
  customer: '负责客户',
  warehouse: '管辖仓库',
}

/** 按模块分组展示权限点 */
const grouped = computed(() => {
  const map = new Map<string, Record<string, string | null>[]>()
  permissions.value.forEach((p) => {
    const m = String(p.module ?? '其他')
    if (!map.has(m)) map.set(m, [])
    map.get(m)!.push(p)
  })
  return Array.from(map.entries()).map(([module, list]) => ({ module, list }))
})

async function loadRoles() {
  roles.value = await api.roles()
}

async function loadPermissions() {
  permissions.value = await api.permissions()
}

async function loadRolePerms() {
  checked.value = await api.rolePermissions(selected.value)
}

async function save() {
  saving.value = true
  try {
    await api.assignPermissions(selected.value, checked.value)
  } finally {
    saving.value = false
  }
}

function toggle(code: string) {
  const i = checked.value.indexOf(code)
  if (i >= 0) checked.value.splice(i, 1)
  else checked.value.push(code)
}

watch(selected, () => loadRolePerms())

onMounted(async () => {
  await loadRoles()
  await loadPermissions()
  await loadRolePerms()
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="shield" :size="17" /> 角色与权限</h3>
      <button class="btn btn-primary btn-sm" :disabled="saving" @click="save">
        <Icon name="check" :size="14" /> {{ saving ? '保存中…' : '保存授权' }}
      </button>
    </div>

    <div class="card-body">
      <div class="body">
        <aside class="side">
          <div
            v-for="r in roles"
            :key="String(r.code)"
            class="item"
            :class="{ active: selected === String(r.code) }"
            @click="selected = String(r.code)"
          >
            <div class="nm">{{ r.name }}</div>
            <div class="meta">
              <code>{{ r.code }}</code>
              <span>{{ SCOPE_TEXT[String(r.data_scope)] || r.data_scope }}</span>
            </div>
          </div>
        </aside>

        <div class="main">
          <div class="hint">
            当前角色 <b>{{ selected }}</b> 已选 <b>{{ checked.length }}</b> / {{ permissions.length }} 项权限
          </div>
          <div v-for="g in grouped" :key="g.module" class="group">
            <div class="gtitle">{{ g.module }} <span>{{ g.list.length }} 项</span></div>
            <div class="items">
              <label v-for="p in g.list" :key="String(p.code)" class="chk">
                <input
                  type="checkbox"
                  :checked="checked.includes(String(p.code))"
                  @change="toggle(String(p.code))"
                />
                <span>{{ p.code }}</span>
                <em>{{ p.description }}</em>
              </label>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.body { display: flex; gap: 18px; }
.side { width: 210px; flex-shrink: 0; border-right: 1px solid var(--border-2); padding-right: 12px; }
.side .item {
  padding: 10px 12px; border-radius: 8px; cursor: pointer; margin-bottom: 4px; transition: .15s var(--ease);
}
.side .item:hover { background: var(--slate-light); }
.side .item.active { background: var(--primary-light); }
.side .nm { font-weight: 700; font-size: 13.5px; color: var(--text); }
.side .meta { display: flex; gap: 8px; font-size: 11.5px; color: var(--text-3); margin-top: 2px; }
.side .meta code { background: var(--slate-light); border-radius: 4px; padding: 0 4px; }
.main { flex: 1; min-width: 0; }
.hint { font-size: 13px; color: var(--text-2); margin-bottom: 14px; }
.group { margin-bottom: 16px; }
.gtitle {
  font-weight: 700; font-size: 13px; color: var(--text); margin-bottom: 8px;
  display: flex; align-items: center; gap: 8px;
}
.gtitle span { font-weight: 500; font-size: 11.5px; color: var(--text-3); }
.items { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 8px; }
.chk {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px; border: 1px solid var(--border);
  border-radius: var(--radius-sm); cursor: pointer; transition: .15s var(--ease); background: #fff;
}
.chk:hover { border-color: var(--primary); }
.chk span { font-size: 12.5px; font-weight: 600; color: var(--text-2); }
.chk em { font-style: normal; font-size: 11.5px; color: var(--text-4); }
@media (max-width: 900px) {
  .body { flex-direction: column; }
  .side { width: 100%; border-right: 0; }
}
</style>

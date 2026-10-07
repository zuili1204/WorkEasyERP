<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type DepartmentRow, type PageResult } from '../api'
import Icon from '../components/Icon.vue'

const PAGE_SIZE = 8

const state = reactive({ q: '', sort: '', page: 1 })
const data = ref<PageResult<DepartmentRow>>({ list: [], total: 0, page: 1, size: PAGE_SIZE })
const loading = ref(false)
const showModal = ref(false)
const form = reactive({ name: '', phone: '' })
const saving = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / PAGE_SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.departments({
      q: state.q || undefined,
      page: state.page,
      size: PAGE_SIZE,
      sort: state.sort || undefined,
    })
  } finally {
    loading.value = false
  }
}

let timer: number | undefined
function onSearch() {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => {
    state.page = 1
    load()
  }, 250)
}

function toggleSort(key: string) {
  if (state.sort.startsWith(key)) {
    state.sort = state.sort.includes('desc') ? `${key},asc` : `${key},desc`
  } else {
    state.sort = `${key},asc`
  }
  load()
}

function sortIcon(key: string) {
  if (!state.sort.startsWith(key)) return 'arrow-up-down'
  return state.sort.includes('desc') ? 'chevron-down' : 'chevron-up'
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

async function submit() {
  if (!form.name.trim()) return
  saving.value = true
  try {
    await api.createDepartment({ name: form.name.trim(), phone: form.phone || undefined })
    showModal.value = false
    form.name = ''
    form.phone = ''
    load()
  } finally {
    saving.value = false
  }
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') showModal.value = false
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onKey)
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="building" :size="17" /> 部门管理</h3>
      <button class="btn btn-primary btn-sm" @click="showModal = true">
        <Icon name="plus" :size="14" /> 新增部门
      </button>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索部门" @input="onSearch" />
        </div>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th class="sortable" :class="{ on: state.sort.startsWith('name') }" @click="toggleSort('name')">
                部门<span class="sico"><Icon :name="sortIcon('name')" :size="12" /></span>
              </th>
              <th>主管</th>
              <th class="sortable" :class="{ on: state.sort.startsWith('count') }" @click="toggleSort('count')">
                人数<span class="sico"><Icon :name="sortIcon('count')" :size="12" /></span>
              </th>
              <th class="nosort">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in data.list" :key="r.id">
              <td>{{ r.name }}</td>
              <td>{{ r.managerName || '—' }}</td>
              <td>{{ r.count }}</td>
              <td><span class="badge badge-green">正常</span></td>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon :name="state.q ? 'search' : 'inbox'" :size="46" />
          <div>{{ state.q ? `未找到与「${state.q}」匹配的数据` : '暂无部门数据' }}</div>
        </div>
      </div>

      <div class="pager">
        <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
        <span>第 {{ data.page }} / {{ totalPages }} 页</span>
        <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
      </div>
    </div>
  </div>

  <!-- 新增部门弹窗（Esc 关闭） -->
  <div v-if="showModal" class="mask" @click.self="showModal = false">
    <div class="modal">
      <div class="modal-head">
        <h3><Icon name="building" :size="18" /> 新增部门</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="field">
          <label>部门名称 *</label>
          <input v-model="form.name" class="input" placeholder="如：生产部" />
        </div>
        <div class="field">
          <label>联系电话</label>
          <input v-model="form.phone" class="input" placeholder="选填" />
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <Icon name="check" :size="15" /> 保存
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nosort { cursor: default; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 80px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 460px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.field { margin-bottom: 16px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

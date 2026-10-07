<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, type PageResult } from '../api'
import Icon from '../components/Icon.vue'

const SIZE = 8
const route = useRoute()
const kind = computed(() => String(route.params.kind || 'punch'))

const META: Record<string, { title: string; icon: string; cols: Array<[string, string]> }> = {
  punch: {
    title: '打卡记录',
    icon: 'map-pin',
    cols: [
      ['employee_name', '员工'],
      ['punch_time', '打卡时间'],
      ['punch_type', '类型'],
      ['source', '来源'],
      ['location', '位置'],
    ],
  },
  daily: {
    title: '日考勤结果',
    icon: 'calendar',
    cols: [
      ['employee_name', '员工'],
      ['work_date', '日期'],
      ['check_in', '签到'],
      ['check_out', '签退'],
      ['status', '状态'],
    ],
  },
  shift: {
    title: '班次定义',
    icon: 'clock',
    cols: [
      ['name', '班次'],
      ['work_start', '上班'],
      ['work_end', '下班'],
      ['late_threshold', '迟到阈值(分)'],
    ],
  },
  schedule: {
    title: '排班',
    icon: 'calendar',
    cols: [
      ['employee_name', '员工'],
      ['work_date', '日期'],
      ['shift_id', '班次'],
    ],
  },
}

const STATUS_MAP: Record<string, { text: string; cls: string }> = {
  normal: { text: '正常', cls: 'badge-green' },
  late: { text: '迟到', cls: 'badge-orange' },
  early: { text: '早退', cls: 'badge-orange' },
  absent: { text: '缺勤', cls: 'badge-red' },
  leave: { text: '请假', cls: 'badge-blue' },
}

const meta = computed(() => META[kind.value] ?? META.punch)
const state = reactive({ q: '', scope: 'mine', page: 1 })
const data = ref<PageResult<Record<string, string | null>>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const shifts = ref<Record<string, string | null>[]>([])
const showModal = ref(false)
const form = reactive({ name: '', workStart: '', workEnd: '', lateThreshold: 0, shiftId: '', workDate: '' })

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    if (kind.value === 'punch') data.value = await api.punches(state.q || undefined, state.scope, state.page, SIZE)
    else if (kind.value === 'daily') data.value = await api.attendanceDaily(state.q || undefined, state.scope, state.page, SIZE)
    else if (kind.value === 'shift') data.value = await api.shifts(state.q || undefined, state.page, SIZE)
    else data.value = await api.schedules(state.q || undefined, state.scope, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

async function loadShifts() {
  const r = await api.shifts(undefined, 1, 50)
  shifts.value = r.list
}

async function doPunch(type: string) {
  await api.punch(type, '公司')
  load()
}

async function submit() {
  if (kind.value === 'shift') {
    await api.createShift({
      name: form.name,
      workStart: form.workStart || undefined,
      workEnd: form.workEnd || undefined,
      lateThreshold: Number(form.lateThreshold) || 0,
    })
  } else {
    await api.createSchedule({ shiftId: form.shiftId, workDate: form.workDate })
  }
  showModal.value = false
  load()
  if (kind.value === 'schedule') loadShifts()
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

function cell(row: Record<string, string | null>, key: string) {
  const v = row[key]
  if (v == null) return '—'
  return String(v).replace('T', ' ').slice(0, 16)
}

watch(kind, () => {
  state.page = 1
  state.q = ''
  load()
})

onMounted(async () => {
  await loadShifts()
  load()
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon :name="meta.icon" :size="17" /> {{ meta.title }}</h3>
      <div class="head-actions">
        <template v-if="kind === 'punch'">
          <button class="btn btn-sm" @click="doPunch('in')"><Icon name="log-out" :size="14" /> 上班打卡</button>
          <button class="btn btn-primary btn-sm" @click="doPunch('out')"><Icon name="log-out" :size="14" /> 下班打卡</button>
        </template>
        <button v-else-if="kind === 'shift' || kind === 'schedule'" class="btn btn-primary btn-sm" @click="showModal = true">
          <Icon name="plus" :size="14" /> {{ kind === 'shift' ? '新增班次' : '批量排班' }}
        </button>
      </div>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" :placeholder="kind === 'shift' ? '搜索班次' : '搜索员工'" @input="load" />
        </div>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <div v-if="kind !== 'shift'" class="seg">
          <button class="seg-btn" :class="{ active: state.scope === 'mine' }" @click="state.scope = 'mine'; load()">我的</button>
          <button class="seg-btn" :class="{ active: state.scope === 'dept' }" @click="state.scope = 'dept'; load()">本部门</button>
          <button class="seg-btn" :class="{ active: state.scope === 'all' }" @click="state.scope = 'all'; load()">全部</button>
        </div>
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <div class="table-wrap">
        <table class="list">
          <thead>
            <tr>
              <th v-for="c in meta.cols" :key="c[0]">{{ c[1] }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, i) in data.list" :key="i">
              <template v-for="c in meta.cols" :key="c[0]">
                <td v-if="c[0] === 'status'">
                  <span class="badge" :class="(STATUS_MAP[r.status ?? ''] ?? { cls: 'badge-gray' }).cls">
                    {{ (STATUS_MAP[r.status ?? ''] ?? { text: r.status ?? '-' }).text }}
                  </span>
                </td>
                <td v-else-if="c[0] === 'punch_type'">
                  <span class="badge" :class="r.punch_type === 'in' ? 'badge-green' : 'badge-blue'">
                    {{ r.punch_type === 'in' ? '上班' : '下班' }}
                  </span>
                </td>
                <td v-else>{{ cell(r, c[0]) }}</td>
              </template>
            </tr>
          </tbody>
        </table>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon name="inbox" :size="46" />
          <div>暂无数据</div>
        </div>
      </div>

      <div class="pager">
        <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
        <span>第 {{ data.page }} / {{ totalPages }} 页</span>
        <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
      </div>
    </div>
  </div>

  <div v-if="showModal" class="mask" @click.self="showModal = false">
    <div class="modal">
      <div class="modal-head">
        <h3><Icon :name="meta.icon" :size="18" /> {{ kind === 'shift' ? '新增班次' : '排班' }}</h3>
        <span class="x" @click="showModal = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <template v-if="kind === 'shift'">
          <div class="field">
            <label>班次名称 *</label>
            <input v-model="form.name" class="input" placeholder="如：标准班" />
          </div>
          <div class="grid">
            <div class="field">
              <label>上班时间</label>
              <input v-model="form.workStart" class="input" placeholder="09:00" />
            </div>
            <div class="field">
              <label>下班时间</label>
              <input v-model="form.workEnd" class="input" placeholder="18:00" />
            </div>
          </div>
          <div class="field">
            <label>迟到阈值（分钟）</label>
            <input v-model.number="form.lateThreshold" type="number" class="input" />
          </div>
        </template>
        <template v-else>
          <div class="field">
            <label>班次 *</label>
            <select v-model="form.shiftId" class="input">
              <option v-for="s in shifts" :key="String(s.id)" :value="String(s.id)">{{ s.name }}</option>
            </select>
          </div>
          <div class="field">
            <label>日期 *</label>
            <input v-model="form.workDate" type="date" class="input" />
          </div>
          <p class="tip">排班对象默认为当前登录员工</p>
        </template>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="showModal = false">取消</button>
        <button class="btn btn-primary" @click="submit"><Icon name="check" :size="15" /> 保存</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head-actions { display: flex; gap: 8px; }
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.tip { font-size: 12px; color: var(--text-3); margin: 0; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 80px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 520px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

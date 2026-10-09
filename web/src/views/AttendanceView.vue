<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, type PageResult } from '../api'
import { toast } from '../stores/toast'
import Icon from '../components/Icon.vue'
import AppModal from '../components/AppModal.vue'
import TableShell from '../components/TableShell.vue'
import { ATTENDANCE_STATUS, statusOf } from '../utils/dict'
import { fmtDateTime } from '../utils/format'

type Row = Record<string, string | null>

const SIZE = 8
const route = useRoute()
const router = useRouter()
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
      // 迟到/早退分钟与加班时长此前完全没进界面，这里补齐
      ['late_min', '迟到(分)'],
      ['early_min', '早退(分)'],
      ['overtime_hours', '加班(h)'],
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

const meta = computed(() => META[kind.value] ?? META.punch)
const state = reactive({ q: '', scope: 'mine', page: 1 })
const data = ref<PageResult<Row>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const shifts = ref<Row[]>([])
const showModal = ref(false)
const saving = ref(false)
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

/** 搜索防抖：原先 @input 直接触发请求，每敲一个字都会打一次接口 */
let timer: number | undefined
function onSearch() {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => {
    state.page = 1
    load()
  }, 250)
}

async function doPunch(type: string) {
  try {
    await api.punch(type, '公司')
    toast.success(type === 'in' ? '上班打卡成功' : '下班打卡成功')
    load()
  } catch (e) {
    toast.error((e as Error)?.message || '打卡失败')
  }
}

async function submit() {
  saving.value = true
  try {
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
    toast.success('保存成功')
    showModal.value = false
    load()
    if (kind.value === 'schedule') loadShifts()
  } catch (e) {
    toast.error((e as Error)?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

/**
 * 单元格渲染：区分 TIME / DATE / DATETIME 三类。
 * 原先统一用 replace('T',' ').slice(0,16)，会把班次时间这类纯时间值截断出错。
 */
function cell(row: Row, key: string) {
  const raw = row[key]
  if (raw == null || raw === '') return '—'
  const v = String(raw)
  if (/^\d{2}:\d{2}(:\d{2})?$/.test(v)) return v.slice(0, 5) // 09:00:00 → 09:00
  if (/^\d{4}-\d{2}-\d{2}$/.test(v)) return v // 纯日期
  const dt = fmtDateTime(v)
  return dt === '—' ? v : dt
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
        <button v-if="kind === 'schedule'" class="btn btn-sm" @click="router.push('/attendance/duty-calendar')">
          <Icon name="calendar" :size="14" /> 日历视图
        </button>
        <template v-if="kind === 'punch'">
          <button class="btn btn-sm" @click="doPunch('in')"><Icon name="log-in" :size="14" /> 上班打卡</button>
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
          <input v-model="state.q" class="input" :placeholder="kind === 'shift' ? '搜索班次' : '搜索员工'" @input="onSearch" />
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

      <TableShell
        :rows="data.list"
        :loading="loading"
        :page="data.page"
        :pages="totalPages"
        @go="go"
      >
        <template #head>
          <tr>
            <th v-for="c in meta.cols" :key="c[0]">{{ c[1] }}</th>
          </tr>
        </template>

        <tr v-for="(r, i) in data.list" :key="i">
          <template v-for="c in meta.cols" :key="c[0]">
            <td v-if="c[0] === 'status'">
              <span class="badge" :class="statusOf(ATTENDANCE_STATUS, r.status).cls">
                {{ statusOf(ATTENDANCE_STATUS, r.status).text }}
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
      </TableShell>
    </div>
  </div>

  <AppModal
    :open="showModal"
    :title="kind === 'shift' ? '新增班次' : '排班'"
    :icon="meta.icon"
    @close="showModal = false"
  >
    <template v-if="kind === 'shift'">
      <div class="field">
        <label>班次名称 *</label>
        <input v-model="form.name" class="input" placeholder="如：标准班" />
      </div>
      <div class="grid">
        <div class="field">
          <label>上班时间</label>
          <input v-model="form.workStart" type="time" class="input" />
        </div>
        <div class="field">
          <label>下班时间</label>
          <input v-model="form.workEnd" type="time" class="input" />
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
      <p class="tip">排班对象默认为当前登录员工；需要给他人排班请到「日历视图」。</p>
    </template>

    <template #footer>
      <button class="btn" @click="showModal = false">取消</button>
      <button class="btn btn-primary" :disabled="saving" @click="submit">
        <Icon name="check" :size="15" /> 保存
      </button>
    </template>
  </AppModal>
</template>

<style scoped>
.head-actions { display: flex; gap: 8px; }
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.tip { font-size: 12px; color: var(--text-3); margin: 0; }
@media (max-width: 900px) {
  .grid { grid-template-columns: 1fr; }
}
</style>

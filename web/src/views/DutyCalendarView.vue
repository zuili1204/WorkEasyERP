<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type PageResult } from '../api'
import { toast } from '../stores/toast'
import Icon from '../components/Icon.vue'

type Row = Record<string, string | null>

/** 后端不同迁移里字段名大小写/命名不完全一致，这里做兼容取值 */
function pick(row: Row, ...keys: string[]) {
  for (const k of keys) {
    const v = row[k]
    if (v != null && v !== '') return String(v)
  }
  return ''
}

const LOAD_SIZE = 500

const loading = ref(false)
const rows = ref<Row[]>([])
const shifts = ref<Row[]>([])
const employees = ref<Array<{ id: string; realName: string; deptName?: string }>>([])

const today = new Date()
const cursor = ref({ y: today.getFullYear(), m: today.getMonth() }) // m: 0-11
const selectedDate = ref<string>('')

const WEEK = ['一', '二', '三', '四', '五', '六', '日']

const shiftName = computed(() => {
  const map: Record<string, string> = {}
  for (const s of shifts.value) map[pick(s, 'id')] = pick(s, 'name')
  return map
})
const employeeName = computed(() => {
  const map: Record<string, string> = {}
  for (const e of employees.value) map[e.id] = e.realName
  return map
})

/** 按天聚合：workDate -> [{employeeId, employeeLabel, shiftId, shiftLabel}] */
const byDate = computed(() => {
  const map: Record<string, Array<{ employeeId: string; employeeLabel: string; shiftId: string; shiftLabel: string }>> = {}
  for (const r of rows.value) {
    const d = pick(r, 'work_date', 'workDate').slice(0, 10)
    if (!d) continue
    const employeeId = pick(r, 'employee_id', 'employeeId')
    const employeeLabel = pick(r, 'employee_name', 'employeeName') || employeeName.value[employeeId] || '未知'
    const shiftId = pick(r, 'shift_id', 'shiftId')
    const shiftLabel = pick(r, 'shift_name', 'shiftName') || shiftName.value[shiftId] || shiftId || '未指定班次'
    ;(map[d] ??= []).push({ employeeId, employeeLabel, shiftId, shiftLabel })
  }
  return map
})

const title = computed(() => `${cursor.value.y} 年 ${cursor.value.m + 1} 月`)

/** 月历格子：含补齐的前后空白天 */
const cells = computed(() => {
  const { y, m } = cursor.value
  const first = new Date(y, m, 1)
  // getDay() 周日=0，转成「周一为首」的偏移
  const lead = (first.getDay() + 6) % 7
  const start = new Date(y, m, 1 - lead)
  const list: Array<{ date: string; day: number; inMonth: boolean; isToday: boolean; count: number }> = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i)
    const p = (n: number) => String(n).padStart(2, '0')
    const date = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
    list.push({
      date,
      day: d.getDate(),
      inMonth: d.getMonth() === m,
      isToday: d.toDateString() === today.toDateString(),
      count: byDate.value[date]?.length ?? 0,
    })
  }
  return list
})

const selectedPeople = computed(() => byDate.value[selectedDate.value] ?? [])

const adding = ref({ employeeId: '', shiftId: '' })
const saving = ref(false)

function selectDay(d: string) {
  selectedDate.value = d
  adding.value.employeeId = ''
  adding.value.shiftId = ''
}

function shiftMonth(step: number) {
  const { y, m } = cursor.value
  const d = new Date(y, m + step, 1)
  cursor.value = { y: d.getFullYear(), m: d.getMonth() }
}
function goToday() {
  cursor.value = { y: today.getFullYear(), m: today.getMonth() }
  const p = (n: number) => String(n).padStart(2, '0')
  selectDay(`${today.getFullYear()}-${p(today.getMonth() + 1)}-${p(today.getDate())}`)
}

async function load() {
  loading.value = true
  try {
    const [sRows, sShifts, sEmps] = await Promise.all([
      api.schedules(undefined, 'all', 1, LOAD_SIZE),
      api.shifts(undefined, 1, LOAD_SIZE),
      api.employees({ page: 1, size: LOAD_SIZE, scope: 'all' }) as unknown as Promise<
        PageResult<Row>
      >,
    ])
    rows.value = sRows.list ?? []
    shifts.value = sShifts.list ?? []
    employees.value = (sEmps.list ?? []).map((r) => ({
      id: pick(r, 'id'),
      realName: pick(r, 'real_name', 'realName', 'name') || pick(r, 'id'),
      deptName: pick(r, 'dept_name', 'deptName') || undefined,
    }))
    const p = (n: number) => String(n).padStart(2, '0')
    if (!selectedDate.value) selectDay(`${today.getFullYear()}-${p(today.getMonth() + 1)}-${p(today.getDate())}`)
  } catch (e) {
    toast.error((e as Error)?.message || '排班数据加载失败')
  } finally {
    loading.value = false
  }
}

async function addSchedule() {
  if (!selectedDate.value) return
  if (!adding.value.employeeId) {
    toast.error('请选择员工')
    return
  }
  if (!adding.value.shiftId) {
    toast.error('请选择班次')
    return
  }
  saving.value = true
  try {
    await api.createSchedule({
      employeeId: adding.value.employeeId,
      shiftId: adding.value.shiftId,
      workDate: selectedDate.value,
    })
    toast.success('排班已保存')
    adding.value.employeeId = ''
    adding.value.shiftId = ''
    await load()
  } catch (e) {
    toast.error((e as Error)?.message || '排班保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="calendar" :size="17" /> 排班 / 值班日历</h3>
      <div class="head-tools">
        <button class="btn btn-sm btn-ghost" @click="shiftMonth(-1)">
          <Icon name="chevron-left" :size="14" /> 上月
        </button>
        <button class="btn btn-sm btn-ghost" @click="goToday">今天</button>
        <button class="btn btn-sm btn-ghost" @click="shiftMonth(1)">
          下月 <Icon name="chevron-right" :size="14" />
        </button>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="14" /> 刷新</button>
      </div>
    </div>

    <div class="card-body">
      <div class="cal-box">
        <div class="cal-main">
          <div class="cal-title">{{ title }}</div>
          <div v-if="loading" class="empty sm"><Icon name="inbox" :size="30" /> 加载中…</div>
          <div v-else class="cal">
            <div v-for="w in WEEK" :key="w" class="cal-week">周{{ w }}</div>
            <div
              v-for="c in cells"
              :key="c.date"
              class="cal-cell"
              :class="{
                out: !c.inMonth,
                today: c.isToday,
                sel: c.date === selectedDate,
                has: c.count > 0,
              }"
              @click="selectDay(c.date)"
            >
              <div class="day">{{ c.day }}</div>
              <div v-if="c.count > 0" class="members">
                <span v-for="(p, i) in (byDate[c.date] ?? []).slice(0, 3)" :key="i" class="chip">
                  {{ p.employeeLabel.slice(0, 1) }}
                </span>
                <span v-if="c.count > 3" class="chip more">+{{ c.count - 3 }}</span>
              </div>
              <div v-if="c.count > 0" class="cnt">值班 {{ c.count }} 人</div>
            </div>
          </div>
        </div>

        <aside class="cal-side">
          <div class="side-title">{{ selectedDate || '请选择日期' }}</div>

          <div v-if="selectedPeople.length === 0" class="empty sm">
            <Icon name="inbox" :size="34" />
            <div>当日暂无排班 / 值班人员</div>
          </div>

          <div v-else class="people">
            <div v-for="(p, i) in selectedPeople" :key="i" class="person">
              <span class="ava">{{ p.employeeLabel.slice(0, 1) }}</span>
              <div class="p-info">
                <div class="p-name">{{ p.employeeLabel }}</div>
                <div class="p-shift">{{ p.shiftLabel }}</div>
              </div>
            </div>
          </div>

          <div class="add-box">
            <div class="side-title sm">新增排班</div>
            <div class="field">
              <label>员工</label>
              <select v-model="adding.employeeId" class="input">
                <option value="">请选择员工</option>
                <option v-for="e in employees" :key="e.id" :value="e.id">
                  {{ e.realName }}{{ e.deptName ? ` · ${e.deptName}` : '' }}
                </option>
              </select>
            </div>
            <div class="field">
              <label>班次</label>
              <select v-model="adding.shiftId" class="input">
                <option value="">请选择班次</option>
                <option v-for="s in shifts" :key="pick(s, 'id')" :value="pick(s, 'id')">
                  {{ pick(s, 'name') }}
                </option>
              </select>
            </div>
            <button class="btn btn-primary btn-sm" :disabled="saving" @click="addSchedule">
              <Icon name="check" :size="14" /> 保存排班
            </button>
          </div>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head-tools { display: flex; align-items: center; gap: 8px; }
.cal-box { display: grid; grid-template-columns: 1fr 300px; gap: 18px; }
.cal-title { font-size: 15px; font-weight: 800; margin-bottom: 12px; }
.cal { display: grid; grid-template-columns: repeat(7, 1fr); gap: 6px; }
.cal-week {
  text-align: center; font-size: 12px; color: var(--text-3); font-weight: 700; padding: 4px 0;
}
.cal-cell {
  min-height: 84px; padding: 7px; border: 1px solid var(--border); border-radius: 10px;
  background: #fff; cursor: pointer; transition: .16s var(--ease);
  display: flex; flex-direction: column; gap: 6px;
}
.cal-cell:hover { border-color: var(--primary); box-shadow: var(--shadow-sm); }
.cal-cell.out { opacity: .42; background: var(--surface-2); }
.cal-cell.today { border-color: var(--primary); }
.cal-cell.sel { background: var(--primary-light); border-color: var(--primary); }
.cal-cell.has { border-left: 3px solid var(--accent); }
.day { font-size: 13px; font-weight: 700; color: var(--text-2); }
.cal-cell.today .day { color: var(--primary); }
.members { display: flex; flex-wrap: wrap; gap: 3px; }
.chip {
  min-width: 20px; height: 20px; padding: 0 4px; border-radius: 6px;
  background: var(--accent-light); color: var(--accent);
  font-size: 11px; font-weight: 700; display: flex; align-items: center; justify-content: center;
}
.chip.more { background: var(--slate-light); color: var(--text-3); }
.cnt { margin-top: auto; font-size: 11px; color: var(--text-3); font-weight: 600; }

.cal-side { border-left: 1px solid var(--border-2); padding-left: 18px; }
.side-title { font-size: 14px; font-weight: 800; margin-bottom: 10px; }
.side-title.sm { font-size: 12.5px; color: var(--text-2); margin: 16px 0 8px; }
.people { display: flex; flex-direction: column; gap: 8px; }
.person { display: flex; gap: 9px; align-items: center; padding: 8px; border-radius: 10px; background: var(--surface-2); }
.person .ava {
  width: 30px; height: 30px; border-radius: 8px; background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center; font-weight: 700; flex-shrink: 0;
}
.p-name { font-size: 13px; font-weight: 700; }
.p-shift { font-size: 11.5px; color: var(--text-3); }
.add-box .field label { display: block; font-size: 12px; color: var(--text-2); margin-bottom: 5px; font-weight: 600; }
.add-box .field { margin-bottom: 10px; }

@media (max-width: 1100px) {
  .cal-box { grid-template-columns: 1fr; }
  .cal-side { border-left: 0; padding-left: 0; border-top: 1px solid var(--border-2); padding-top: 16px; }
}
</style>

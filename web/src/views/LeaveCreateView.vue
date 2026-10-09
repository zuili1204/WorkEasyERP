<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { toast } from '../stores/toast'
import Icon from '../components/Icon.vue'
import { LEAVE_TYPES } from '../utils/dict'
import { toLocalInput, toUtcIso } from '../utils/format'

const router = useRouter()
const route = useRoute()

// 请假类型统一取自 utils/dict，避免多视图各抄一份文案

/** 重新提交：列表页通过 query 带入原单据字段（后端暂无单条详情接口） */
const resubmitId = String(route.query.id ?? '')
const isResubmit = Boolean(resubmitId)

const form = reactive({
  leaveType: String(route.query.leaveType ?? 'annual'),
  startAt: '',
  endAt: '',
  duration: 1,
  reason: String(route.query.reason ?? ''),
})
/** 用户是否手动改过天数：改过之后不再随起止时间自动覆盖 */
const durationTouched = ref(false)
const saving = ref(false)
const errors = reactive<Record<string, string>>({})

// 本地时间串统一由 utils/format 的 toLocalInput 提供

/** 默认时段：开始 = 此刻，结束 = 此刻 + 1 天 */
function fillDefaults() {
  const now = new Date()
  const end = new Date(now)
  end.setDate(end.getDate() + 1)
  form.startAt = toLocalInput(now)
  form.endAt = toLocalInput(end)
}

/** 由起止时间自动折算天数：半天为最小粒度，上限按自然时间换算 */
const autoDuration = computed(() => {
  if (!form.startAt || !form.endAt) return 0
  const s = new Date(form.startAt).getTime()
  const e = new Date(form.endAt).getTime()
  if (!Number.isFinite(s) || !Number.isFinite(e) || e <= s) return 0
  const hours = (e - s) / 36e5
  // 不足半天按半天计，其余向上取整到 0.5 天
  return Math.max(0.5, Math.ceil(hours / 12) * 0.5)
})

// 天数未手动改过时，随起止时间自动联动
watch(
  () => [form.startAt, form.endAt],
  () => {
    if (!durationTouched.value && autoDuration.value > 0) form.duration = autoDuration.value
  },
)

function validate() {
  errors.leaveType = form.leaveType ? '' : '请选择请假类型'
  errors.startAt = form.startAt ? '' : '请选择开始时间'
  errors.endAt = form.endAt ? '' : '请选择结束时间'
  if (!errors.endAt && form.startAt && form.endAt) {
    const s = new Date(form.startAt).getTime()
    const e = new Date(form.endAt).getTime()
    if (!(e > s)) errors.endAt = '结束时间必须晚于开始时间'
  }
  errors.duration = Number(form.duration) > 0 ? '' : '请填写有效天数'
  return !Object.values(errors).some(Boolean)
}

async function submit() {
  if (!validate()) {
    toast.error('请先修正表单中的错误')
    return
  }
  saving.value = true
  try {
    // datetime-local 为本地时间，转 UTC 交给后端，避免跨时区偏移一天
    const body = {
      leaveType: form.leaveType,
      startAt: toUtcIso(form.startAt),
      endAt: toUtcIso(form.endAt),
      duration: Number(form.duration),
      reason: form.reason || undefined,
    }
    if (isResubmit) await api.resubmitLeave(resubmitId, body)
    else await api.submitLeave(body)
    toast.success(isResubmit ? '已重新提交，等待审批' : '请假已提交，等待审批')
    router.push('/leaves')
  } catch (e) {
    toast.error((e as Error)?.message || '提交失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (isResubmit) {
    form.startAt = String(route.query.startAt ?? '')
    form.endAt = String(route.query.endAt ?? '')
    const d = Number(route.query.duration)
    form.duration = Number.isFinite(d) && d > 0 ? d : 1
    durationTouched.value = true
    return
  }
  fillDefaults()
  form.duration = autoDuration.value || 1
})
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="calendar" :size="17" /> {{ isResubmit ? '重新提交请假' : '发起请假' }}</h3>
      <button class="btn btn-sm" @click="router.back()">
        <Icon name="chevron-left" :size="14" /> 返回列表
      </button>
    </div>

    <div class="card-body">
      <p v-if="!isResubmit" class="hint">已默认填入「此刻 → 此刻 + 1 天」，可自行调整。</p>

      <div class="grid">
        <div class="field" :class="{ invalid: errors.leaveType }">
          <label>请假类型 <b class="req">*</b></label>
          <select v-model="form.leaveType" class="input">
            <option v-for="t in LEAVE_TYPES" :key="t[0]" :value="t[0]">{{ t[1] }}</option>
          </select>
          <span v-if="errors.leaveType" class="err-tip">{{ errors.leaveType }}</span>
        </div>

        <div class="field" :class="{ invalid: errors.duration }">
          <label>请假天数 <b class="req">*</b></label>
          <input
            v-model.number="form.duration"
            type="number"
            min="0.5"
            step="0.5"
            class="input"
            @input="durationTouched = true"
          />
          <span class="sub-hint">按起止时间自动折算 {{ autoDuration || '—' }} 天，可手动覆盖</span>
          <span v-if="errors.duration" class="err-tip">{{ errors.duration }}</span>
        </div>

        <div class="field" :class="{ invalid: errors.startAt }">
          <label>开始时间 <b class="req">*</b></label>
          <input v-model="form.startAt" type="datetime-local" class="input" />
          <span v-if="errors.startAt" class="err-tip">{{ errors.startAt }}</span>
        </div>

        <div class="field" :class="{ invalid: errors.endAt }">
          <label>结束时间 <b class="req">*</b></label>
          <input v-model="form.endAt" type="datetime-local" class="input" />
          <span v-if="errors.endAt" class="err-tip">{{ errors.endAt }}</span>
        </div>
      </div>

      <div class="field">
        <label>事由</label>
        <textarea v-model="form.reason" rows="3" class="input" placeholder="选填"></textarea>
      </div>

      <div class="form-foot">
        <button class="btn" @click="router.back()">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <Icon name="check" :size="15" /> {{ isResubmit ? '重新提交审批' : '提交审批' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.req { color: var(--danger); font-weight: 700; }
.sub-hint { display: block; margin-top: 5px; font-size: 11.5px; color: var(--text-3); }
.err-tip { display: block; margin-top: 5px; font-size: 11.5px; color: var(--danger); }
.field.invalid .input { border-color: var(--danger); box-shadow: 0 0 0 3px var(--danger-light); }
.hint {
  margin: 0 0 16px; padding: 9px 12px; font-size: 12.5px; color: var(--primary);
  background: var(--primary-light); border-radius: var(--radius-sm);
}
.form-foot {
  display: flex; justify-content: flex-end; gap: 10px;
  margin-top: 18px; padding-top: 16px; border-top: 1px solid var(--border-2);
}
@media (max-width: 900px) {
  .grid { grid-template-columns: 1fr; }
}
</style>

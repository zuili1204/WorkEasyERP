<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from '../stores/toast'
import Icon from '../components/Icon.vue'
import FormField from '../components/FormField.vue'
import {
  findEntity,
  loadOptions,
  type EntitySchema,
  type FieldSchema,
  type OptionItem,
  type OptionMap,
} from '../utils/entityForms'

const route = useRoute()
const router = useRouter()

/** 路由形如 /new/order/purchase → key = 'order:purchase' */
const key = computed(() => {
  const base = String(route.params.base ?? '')
  const variant = route.params.variant ? String(route.params.variant) : ''
  return variant ? `${base}:${variant}` : base
})

const schema = computed<EntitySchema | undefined>(() => findEntity(key.value))

const values = reactive<Record<string, string | number | null>>({})
const errors = reactive<Record<string, string>>({})
const options = ref<OptionMap>({
  customers: [], suppliers: [], products: [], warehouses: [],
  departments: [], currencies: [], reportModules: [],
})
const rows = ref<Array<Record<string, unknown>>>([])
const loading = ref(true)
const saving = ref(false)

function optionsOf(f: FieldSchema): OptionItem[] {
  if (f.optionsFrom) return options.value[f.optionsFrom]
  return (f.options ?? []).map(([v, l]) => ({ value: v, label: l }))
}

function initValues(s: EntitySchema) {
  for (const f of s.fields) {
    values[f.name] = f.default ?? ''
  }
}

function clearErrors() {
  for (const k of Object.keys(errors)) delete errors[k]
}

function validate(s: EntitySchema): boolean {
  clearErrors()
  for (const f of s.fields) {
    if (!f.required) continue
    const v = values[f.name]
    if (v === '' || v == null) errors[f.name] = `${f.label.replace(' *', '')}不能为空`
  }
  if (s.lines && rows.value.length === 0) {
    toast.error('请至少添加一行明细')
    return false
  }
  return !Object.keys(errors).length
}

async function submit() {
  const s = schema.value
  if (!s) return
  if (!validate(s)) {
    toast.error('请先修正表单中的错误')
    return
  }
  saving.value = true
  try {
    // 明细行通过约定键 __items 传给各 schema 的 submit
    const payload: Record<string, unknown> = { ...values }
    if (s.lines) payload.__items = rows.value
    await s.submit(payload)
    toast.success(s.successText)
    router.push(s.back)
  } catch (e) {
    toast.error((e as Error)?.message || '提交失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function addRow() {
  const s = schema.value
  if (!s?.lines) return
  rows.value.push(s.lines.makeRow(options.value))
}

function removeRow(i: number) {
  rows.value.splice(i, 1)
}

const lineSummary = computed(() => {
  const s = schema.value
  return s?.lines?.summary?.(rows.value) ?? ''
})

onMounted(async () => {
  const s = schema.value
  if (!s) {
    loading.value = false
    return
  }
  initValues(s)
  options.value = await loadOptions(s.refs)
  // 远程下拉：选项就绪后回填首个可选项，避免提交空值
  for (const f of s.fields) {
    if (f.optionsFrom && (values[f.name] === '' || values[f.name] == null)) {
      values[f.name] = optionsOf(f)[0]?.value ?? ''
    }
  }
  if (s.lines) rows.value = [s.lines.makeRow(options.value)]
  loading.value = false
})
</script>

<template>
  <div v-if="!schema" class="card">
    <div class="card-body">
      <div class="empty">
        <Icon name="inbox" :size="46" />
        <div>未找到该表单类型：{{ key }}</div>
        <button class="btn btn-sm" style="margin-top: 14px" @click="router.back()">返回</button>
      </div>
    </div>
  </div>

  <div v-else class="card">
    <div class="card-head">
      <h3><Icon :name="schema.icon" :size="17" /> {{ schema.title }}</h3>
      <button class="btn btn-sm" @click="router.back()">
        <Icon name="chevron-left" :size="14" /> 返回列表
      </button>
    </div>

    <div class="card-body">
      <div v-if="loading" class="empty sm"><Icon name="inbox" :size="30" /> 加载选项…</div>

      <template v-else>
        <div class="grid">
          <FormField
            v-for="f in schema.fields"
            :key="f.name"
            :label="f.label.replace(' *', '')"
            :required="f.required"
            :error="errors[f.name]"
            :hint="f.hint"
          >
            <select
              v-if="f.type === 'select'"
              v-model="values[f.name]"
              class="input"
            >
              <option value="">请选择</option>
              <option v-for="o in optionsOf(f)" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>

            <textarea
              v-else-if="f.type === 'textarea'"
              v-model="values[f.name]"
              :rows="f.rows ?? 3"
              class="input"
              :placeholder="f.placeholder"
            ></textarea>

            <input
              v-else
              v-model="values[f.name]"
              :type="f.type"
              class="input"
              :placeholder="f.placeholder"
              :min="f.min"
              :max="f.max"
              :step="f.step"
              :maxlength="f.maxlength"
            />

          </FormField>
        </div>

        <!-- 明细行 -->
        <template v-if="schema.lines">
          <div class="lines-head">
            <b>{{ schema.lines.title }}</b>
            <button class="btn btn-sm" @click="addRow">
              <Icon name="plus" :size="13" /> {{ schema.lines.addLabel }}
            </button>
          </div>
          <table class="lines">
            <thead>
              <tr>
                <th v-for="c in schema.lines.fields" :key="c.name" :style="c.width ? { width: c.width } : undefined">
                  {{ c.label }}
                </th>
                <th style="width: 44px"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(l, i) in rows" :key="i">
                <td v-for="c in schema.lines!.fields" :key="c.name">
                  <select
                    v-if="c.type === 'select'"
                    v-model="l[c.name]"
                    class="input sm"
                  >
                    <option
                      v-for="o in (c.optionsFrom ? options[c.optionsFrom] : [])"
                      :key="o.value"
                      :value="o.value"
                    >
                      {{ o.label }}
                    </option>
                  </select>
                  <input
                    v-else
                    v-model.number="l[c.name]"
                    type="number"
                    class="input sm"
                    :min="c.min"
                    :step="c.step"
                    :disabled="c.disabled"
                    :title="c.disabled ? '由系统按加权成本自动计价' : undefined"
                  />
                </td>
                <td>
                  <a
                    class="rm"
                    :class="{ dis: rows.length === 1 }"
                    @click="rows.length > 1 && removeRow(i)"
                  >删</a>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-if="lineSummary" class="total">{{ lineSummary }}</p>
        </template>

        <p v-if="schema.tip" class="tip">{{ schema.tip }}</p>

        <div class="form-foot">
          <button class="btn" @click="router.back()">取消</button>
          <button class="btn btn-primary" :disabled="saving" @click="submit">
            <Icon name="check" :size="15" /> 提交
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
.tip { font-size: 12px; color: var(--text-3); margin: 0; }
.total { text-align: right; font-size: 13px; color: var(--text-2); margin: 10px 0 0; }
.lines-head { display: flex; align-items: center; justify-content: space-between; margin: 18px 0 8px; }
.lines { width: 100%; border-collapse: collapse; }
.lines th {
  text-align: left; font-size: 12px; color: var(--text-3); font-weight: 700;
  padding: 6px 8px; background: var(--surface-2); border-bottom: 1px solid var(--border);
}
.lines td { padding: 5px 8px; border-bottom: 1px solid var(--border-2); }
.input.sm { padding: 6px 8px; font-size: 12.5px; width: 100%; }
.rm { color: var(--danger); cursor: pointer; font-size: 12.5px; }
.rm.dis { color: var(--text-4); cursor: not-allowed; }
.form-foot {
  display: flex; justify-content: flex-end; gap: 10px;
  margin-top: 18px; padding-top: 16px; border-top: 1px solid var(--border-2);
}
@media (max-width: 900px) {
  .grid { grid-template-columns: 1fr; }
}
</style>

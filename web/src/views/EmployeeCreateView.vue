<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { toast } from '../stores/toast'
import Icon from '../components/Icon.vue'

const router = useRouter()

const form = reactive({
  employeeNo: '',
  realName: '',
  departmentId: '',
  position: '',
  phone: '',
  // 默认入职日期：今天，与请假页自动填充保持一致的使用习惯
  hireDate: new Date().toISOString().slice(0, 10),
})

const departments = ref<Array<{ id: string; name: string }>>([])
const saving = ref(false)
const errors = reactive<Record<string, string>>({})

async function loadDepartments() {
  try {
    const r = await api.departments({ page: 1, size: 200 })
    departments.value = (r.list ?? []).map((d) => ({ id: String(d.id), name: d.name }))
  } catch {
    // 部门加载失败不阻塞建档，只是无法选择归属部门
    departments.value = []
  }
}

function validate() {
  errors.realName = form.realName.trim() ? '' : '姓名不能为空'
  errors.phone = !form.phone || /^[\d\-+()\s]{5,20}$/.test(form.phone) ? '' : '手机号格式不正确'
  errors.hireDate = form.hireDate ? '' : '请选择入职日期'
  return !Object.values(errors).some(Boolean)
}

async function submit() {
  if (!validate()) {
    toast.error('请先修正表单中的错误')
    return
  }
  saving.value = true
  try {
    await api.createEmployee({
      employeeNo: form.employeeNo.trim() || undefined,
      realName: form.realName.trim(),
      departmentId: form.departmentId || undefined,
      position: form.position.trim() || undefined,
      phone: form.phone.trim() || undefined,
      hireDate: form.hireDate || undefined,
    })
    toast.success('员工档案已创建')
    router.push('/employees')
  } catch (e) {
    toast.error((e as Error)?.message || '创建失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(loadDepartments)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="user-plus" :size="17" /> 新建员工档案</h3>
      <button class="btn btn-sm" @click="router.back()">
        <Icon name="chevron-left" :size="14" /> 返回列表
      </button>
    </div>

    <div class="card-body">
      <p class="hint">入职日期已默认填入今天，可自行调整。</p>

      <div class="grid">
        <div class="field" :class="{ invalid: errors.realName }">
          <label>姓名 <b class="req">*</b></label>
          <input v-model="form.realName" class="input" placeholder="请输入真实姓名" />
          <span v-if="errors.realName" class="err-tip">{{ errors.realName }}</span>
        </div>

        <div class="field">
          <label>工号</label>
          <input v-model="form.employeeNo" class="input" placeholder="留空则由系统规则生成" />
        </div>

        <div class="field">
          <label>所属部门</label>
          <select v-model="form.departmentId" class="input">
            <option value="">未指定</option>
            <option v-for="d in departments" :key="d.id" :value="d.id">{{ d.name }}</option>
          </select>
        </div>

        <div class="field">
          <label>岗位</label>
          <input v-model="form.position" class="input" placeholder="如：Java 工程师" />
        </div>

        <div class="field" :class="{ invalid: errors.phone }">
          <label>手机号</label>
          <input v-model="form.phone" class="input" placeholder="选填" />
          <span v-if="errors.phone" class="err-tip">{{ errors.phone }}</span>
        </div>

        <div class="field" :class="{ invalid: errors.hireDate }">
          <label>入职日期 <b class="req">*</b></label>
          <input v-model="form.hireDate" type="date" class="input" />
          <span v-if="errors.hireDate" class="err-tip">{{ errors.hireDate }}</span>
        </div>
      </div>

      <div class="form-foot">
        <button class="btn" @click="router.back()">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="submit">
          <Icon name="check" :size="15" /> 保存档案
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

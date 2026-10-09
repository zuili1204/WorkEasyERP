<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import Icon from '../components/Icon.vue'
import WorkflowCanvas from '../components/WorkflowCanvas.vue'
import WorkflowNodePanel from '../components/WorkflowNodePanel.vue'
import { toast } from '../stores/toast'

type NodeType = 'approve' | 'cc' | 'end' | 'condition'

interface NodeVm {
  uid: string
  nodeKey: string
  name: string
  nodeType: NodeType
  approverType: 'role' | 'user' | 'dept_manager'
  role: string
  sameDept: boolean
  userId: string
  canTransfer: boolean
  // 条件分支
  conditionField: string
  conditionOp: string
  conditionValue: string
  nextNodeKey: string
  elseNodeKey: string
}

// PALETTE / OP_TEXT / FIELD_OPTIONS 已随画布与属性面板拆分移入对应组件

const defs = ref<Record<string, string | null>[]>([])
const code = ref('leave')
const roles = ref<Record<string, string | null>[]>([])
const nodes = ref<NodeVm[]>([])
const selected = ref<string>('')
const runningCount = ref('0')
const saving = ref(false)
const errorMsg = ref('')
const notice = ref('')

const selectedNode = computed(() => nodes.value.find((n) => n.uid === selected.value) ?? null)
const currentDef = computed(() => defs.value.find((d) => d.code === code.value) ?? null)
const hasApprove = computed(() => nodes.value.some((n) => n.nodeType === 'approve'))
const hasEnd = computed(() => nodes.value.some((n) => n.nodeType === 'end'))
/** 可作为分支目标的下游节点（排除自身与条件节点自身作为目标意义不大，仍允许） */
const branchTargets = computed(() => nodes.value.filter((n) => n.uid !== selected.value))

let seq = 0
const uid = () => `n${Date.now()}_${++seq}`

function baseOf(t: NodeType) {
  return { approve: 'approve', cc: 'cc', end: 'end', condition: 'condition' }[t]
}

function nameOf(t: NodeType) {
  return { approve: '审批', cc: '抄送', end: '结束', condition: '条件分支' }[t]
}

function addNode(type: NodeType) {
  errorMsg.value = ''
  const endIdx = nodes.value.findIndex((x) => x.nodeType === 'end')
  if (type === 'end' && endIdx >= 0) {
    errorMsg.value = '流程只能有一个结束节点'
    return
  }
  const idx = nodes.value.length
  const n: NodeVm = {
    uid: uid(),
    nodeKey: `${baseOf(type)}_${idx + 1}`,
    name: nameOf(type),
    nodeType: type,
    approverType: 'role',
    role: 'manager',
    sameDept: true,
    userId: '',
    canTransfer: true,
    conditionField: 'amount',
    conditionOp: 'gte',
    conditionValue: '100000',
    nextNodeKey: '',
    elseNodeKey: '',
  }
  if (endIdx >= 0) nodes.value.splice(endIdx, 0, n)
  else nodes.value.push(n)
  selected.value = n.uid
}

function removeNode(target: string) {
  nodes.value = nodes.value.filter((n) => n.uid !== target)
  if (selected.value === target) selected.value = ''
}

function move(idx: number, delta: number) {
  const t = idx + delta
  if (t < 0 || t >= nodes.value.length) return
  const arr = nodes.value.slice()
  const tmp = arr[idx]
  arr[idx] = arr[t]
  arr[t] = tmp
  nodes.value = arr
}

/** 拖拽重排：由 WorkflowCanvas 抛出 (from → to) */
function reorder(from: number, to: number) {
  const arr = nodes.value.slice()
  const [moved] = arr.splice(from, 1)
  arr.splice(to, 0, moved)
  nodes.value = arr
}

// nodeIcon / nameOfKey / summary 已移入 WorkflowCanvas

function parseJson(json: string | null | undefined): Record<string, unknown> {
  if (!json) return {}
  try {
    return JSON.parse(json) as Record<string, unknown>
  } catch {
    return {}
  }
}

async function loadDef() {
  errorMsg.value = ''
  const d = await api.wfDetail(code.value)
  runningCount.value = String(d.runningCount ?? '0')
  nodes.value = (d.nodes ?? []).map((raw) => {
    const rule = parseJson(String(raw.approver_rule ?? '{}'))
    const cond = parseJson(raw.condition ? String(raw.condition) : null)
    return {
      uid: uid(),
      nodeKey: String(raw.node_key ?? ''),
      name: String(raw.name ?? ''),
      nodeType: (String(raw.node_type ?? 'approve') as NodeType),
      approverType: (String(raw.approver_type ?? 'role') as NodeVm['approverType']),
      role: String(rule.role ?? 'manager'),
      sameDept: rule.sameDept === true,
      userId: String(rule.user ?? ''),
      canTransfer: String(raw.can_transfer ?? 'true') === 'true',
      conditionField: String(cond.field ?? 'amount'),
      conditionOp: String(cond.op ?? 'gte'),
      conditionValue: String(cond.value ?? '100000'),
      nextNodeKey: String(raw.next_node_key ?? ''),
      elseNodeKey: String(raw.else_node_key ?? ''),
    }
  })
  selected.value = nodes.value[0]?.uid ?? ''
}

async function save() {
  errorMsg.value = ''
  notice.value = ''
  if (!hasApprove.value) {
    errorMsg.value = '至少需要 1 个审批节点'
    toast.error(errorMsg.value)
    return
  }
  if (!hasEnd.value) {
    errorMsg.value = '需要 1 个结束节点'
    toast.error(errorMsg.value)
    return
  }
  saving.value = true
  try {
    const payload = nodes.value.map((n) => ({
      nodeKey: n.nodeKey,
      name: n.name,
      nodeType: n.nodeType,
      approverType: n.approverType,
      role: n.role,
      sameDept: n.sameDept,
      userId: n.userId,
      canTransfer: n.canTransfer,
      conditionField: n.conditionField,
      conditionOp: n.conditionOp,
      conditionValue: n.conditionValue,
      nextNodeKey: n.nextNodeKey,
      elseNodeKey: n.elseNodeKey,
    }))
    await api.wfSaveNodes(code.value, payload)
    notice.value = '已保存，新提交的单据将按此流程与条件流转'
    await loadDef()
    toast.success('流程已保存')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    const msg = err.response?.data?.msg || '保存失败'
    errorMsg.value = msg
    toast.error(msg)
  } finally {
    saving.value = false
  }
}

async function toggleStatus() {
  try {
    const next = currentDef.value?.status === 'active' ? 'disabled' : 'active'
    await api.wfSetStatus(code.value, next)
    defs.value = await api.wfDefs()
    toast.success(next === 'active' ? '流程已启用' : '流程已停用')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    toast.error(err.response?.data?.msg || '状态切换失败')
  }
}

// 新建流程已迁移至独立页 /new/workflow-def（见 utils/entityForms.ts）

onMounted(async () => {
  defs.value = await api.wfDefs()
  roles.value = await api.wfRoles()
  if (defs.value.length) code.value = String(defs.value[0].code)
  await loadDef()
})

watch(code, loadDef)
</script>

<template>
  <div>
    <div class="card">
      <div class="card-head">
        <h3><Icon name="git-branch" :size="17" /> 审批流程配置</h3>
        <div class="head-actions">
          <select v-model="code" class="input" style="width: 220px">
            <option v-for="d in defs" :key="String(d.code)" :value="String(d.code)">
              {{ d.name }}（{{ d.code }}）
            </option>
          </select>
          <span class="badge" :class="currentDef?.status === 'active' ? 'badge-green' : 'badge-gray'">
            {{ currentDef?.status === 'active' ? '启用中' : '已停用' }}
          </span>
          <button class="btn btn-sm" @click="toggleStatus">
            {{ currentDef?.status === 'active' ? '停用' : '启用' }}
          </button>
          <router-link class="btn btn-sm" to="/new/workflow-def"><Icon name="plus" :size="14" /> 新建流程</router-link>
          <button class="btn btn-sm btn-primary" :disabled="saving" @click="save">
            <Icon name="check" :size="14" /> 保存流程
          </button>
        </div>
      </div>

      <div class="card-body">
        <p v-if="errorMsg" class="err">{{ errorMsg }}</p>
        <p v-if="notice" class="ok">{{ notice }}</p>
        <p v-if="runningCount !== '0'" class="warn">
          该流程有 {{ runningCount }} 条在途单据，为保证流转正确，暂不可修改节点；请先处理完在途单据。
        </p>

        <div class="wrap">
          <!-- 节点库 + 画布（#25：已拆分为 WorkflowCanvas 组件） -->
          <WorkflowCanvas
            :nodes="nodes"
            :selected="selected"
            :roles="roles"
            @add="addNode"
            @select="selected = $event"
            @move="({ index, delta }) => move(index, delta)"
            @remove="removeNode"
            @reorder="({ from, to }) => reorder(from, to)"
          />

          <!-- 属性（#25：已拆分为 WorkflowNodePanel 组件） -->
          <WorkflowNodePanel :node="selectedNode" :roles="roles" :targets="branchTargets" />
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.head-actions { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.wrap { display: grid; grid-template-columns: 220px 1fr 280px; gap: 16px; align-items: start; }
/* 画布与节点属性面板的样式已分别移入 WorkflowCanvas.vue / WorkflowNodePanel.vue */
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin-bottom: 12px; }
.ok { background: var(--accent-light); color: #15803d; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin-bottom: 12px; }
.warn { background: var(--warn-light); color: #b45309; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin-bottom: 12px; }

</style>

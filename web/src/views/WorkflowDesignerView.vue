<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import Icon from '../components/Icon.vue'

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

const PALETTE: { type: NodeType; label: string; icon: string; desc: string }[] = [
  { type: 'approve', label: '审批节点', icon: 'check-circle', desc: '需人工处理，可驳回' },
  { type: 'condition', label: '条件分支', icon: 'git-branch', desc: '按金额等条件决定走向' },
  { type: 'cc', label: '抄送节点', icon: 'message', desc: '通知后自动通过' },
  { type: 'end', label: '结束节点', icon: 'flag', desc: '流程终点' },
]

const OP_TEXT: Record<string, string> = {
  gte: '≥', gt: '>', lte: '≤', lt: '<', eq: '=', neq: '≠',
}

const FIELD_OPTIONS = [
  { value: 'amount', label: '单据金额（amount）' },
  { value: 'total_amount', label: '价税合计（total_amount）' },
  { value: 'duration', label: '天数（duration）' },
]

const defs = ref<Record<string, string | null>[]>([])
const code = ref('leave')
const roles = ref<Record<string, string | null>[]>([])
const nodes = ref<NodeVm[]>([])
const selected = ref<string>('')
const runningCount = ref('0')
const saving = ref(false)
const errorMsg = ref('')
const notice = ref('')
const dragIndex = ref(-1)

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

function dragStart(i: number) {
  dragIndex.value = i
}

function drop(target: number) {
  if (dragIndex.value < 0 || dragIndex.value === target) return
  const arr = nodes.value.slice()
  const [moved] = arr.splice(dragIndex.value, 1)
  arr.splice(target, 0, moved)
  nodes.value = arr
  dragIndex.value = -1
}

function dragEnd() {
  dragIndex.value = -1
}

function nodeIcon(n: NodeVm) {
  return PALETTE.find((p) => p.type === n.nodeType)?.icon ?? 'check-circle'
}

function nameOfKey(k: string) {
  return nodes.value.find((n) => n.nodeKey === k)?.name ?? k
}

function summary(n: NodeVm): string {
  if (n.nodeType === 'end') return '流程结束'
  if (n.nodeType === 'condition') {
    const cond = `${n.conditionField} ${OP_TEXT[n.conditionOp] ?? '?'} ${n.conditionValue}`
    const yes = n.nextNodeKey ? nameOfKey(n.nextNodeKey) : '顺序下一节点'
    const no = n.elseNodeKey ? nameOfKey(n.elseNodeKey) : '顺序下一节点'
    return `当 ${cond} → ${yes}；否则 → ${no}`
  }
  if (n.approverType === 'user') return `指定人：${n.userId || '未设置'}`
  if (n.approverType === 'dept_manager') return '本部门主管'
  const r = roles.value.find((x) => x.code === n.role)
  return `${r?.name ?? n.role}${n.sameDept ? ' · 同部门优先' : ''}`
}

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
    return
  }
  if (!hasEnd.value) {
    errorMsg.value = '需要 1 个结束节点'
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
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '保存失败'
  } finally {
    saving.value = false
  }
}

async function toggleStatus() {
  const next = currentDef.value?.status === 'active' ? 'disabled' : 'active'
  await api.wfSetStatus(code.value, next)
  defs.value = await api.wfDefs()
}

const showCreate = ref(false)
const newDef = ref({ code: '', name: '', remark: '' })

async function createDef() {
  errorMsg.value = ''
  try {
    await api.wfCreateDef({ code: newDef.value.code.trim(), name: newDef.value.name.trim(), remark: newDef.value.remark })
    showCreate.value = false
    newDef.value = { code: '', name: '', remark: '' }
    defs.value = await api.wfDefs()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    errorMsg.value = err.response?.data?.msg || '新建失败'
  }
}

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
          <button class="btn btn-sm" @click="showCreate = true"><Icon name="plus" :size="14" /> 新建流程</button>
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
          <!-- 节点库 -->
          <div class="palette">
            <b class="sec">节点库</b>
            <div v-for="p in PALETTE" :key="p.type" class="pal" @click="addNode(p.type)">
              <Icon :name="p.icon" :size="16" />
              <div>
                <div class="pl">{{ p.label }}</div>
                <div class="pd">{{ p.desc }}</div>
              </div>
              <Icon name="plus" :size="14" />
            </div>
            <p class="tip">点击添加到画布；画布内可<b>拖拽重排</b>，顺序即审批顺序。条件分支按判定结果跳转到目标节点。</p>
          </div>

          <!-- 画布 -->
          <div class="canvas">
            <b class="sec">流程画布</b>
            <div v-if="nodes.length === 0" class="empty sm">
              <Icon name="inbox" :size="34" />
              <div>从左侧添加节点开始编排</div>
            </div>
            <div
              v-for="(n, i) in nodes" :key="n.uid"
              class="node" :class="[n.nodeType, { sel: selected === n.uid, dragging: dragIndex === i }]"
              draggable="true"
              @dragstart="dragStart(i)"
              @dragend="dragEnd"
              @dragover.prevent
              @drop="drop(i)"
              @click="selected = n.uid"
            >
              <div class="grip"><Icon name="filter" :size="14" /></div>
              <div class="body">
                <div class="t">
                  <Icon :name="nodeIcon(n)" :size="15" />
                  <b>{{ n.name }}</b>
                  <span class="key">{{ n.nodeKey }}</span>
                  <span v-if="n.nodeType === 'condition'" class="tag-cond">条件</span>
                </div>
                <div class="r">{{ summary(n) }}</div>
                <div v-if="n.nodeType === 'condition'" class="branches">
                  <span class="br yes">成立 → {{ n.nextNodeKey ? nameOfKey(n.nextNodeKey) : '顺序继续' }}</span>
                  <span class="br no">否则 → {{ n.elseNodeKey ? nameOfKey(n.elseNodeKey) : '顺序继续' }}</span>
                </div>
              </div>
              <div class="acts">
                <span class="idx">{{ i + 1 }}</span>
                <Icon name="chevron-up" :size="14" class="mv" @click.stop="move(i, -1)" />
                <Icon name="chevron-down" :size="14" class="mv" @click.stop="move(i, 1)" />
                <Icon name="x" :size="15" class="del" @click.stop="removeNode(n.uid)" />
              </div>
            </div>
          </div>

          <!-- 属性 -->
          <div class="props">
            <b class="sec">节点属性</b>
            <div v-if="!selectedNode" class="empty sm">
              <Icon name="settings" :size="30" />
              <div>选中节点后编辑</div>
            </div>
            <template v-else>
              <div class="field">
                <label>节点名称</label>
                <input v-model="selectedNode.name" class="input" />
              </div>
              <div class="field">
                <label>节点标识（nodeKey）</label>
                <input v-model="selectedNode.nodeKey" class="input" />
              </div>

              <!-- 条件分支 -->
              <template v-if="selectedNode.nodeType === 'condition'">
                <div class="field">
                  <label>判断字段</label>
                  <select v-model="selectedNode.conditionField" class="input">
                    <option v-for="f in FIELD_OPTIONS" :key="f.value" :value="f.value">{{ f.label }}</option>
                  </select>
                </div>
                <div class="grid2">
                  <div class="field">
                    <label>比较</label>
                    <select v-model="selectedNode.conditionOp" class="input">
                      <option value="gte">≥ 大于等于</option>
                      <option value="gt">＞ 大于</option>
                      <option value="lte">≤ 小于等于</option>
                      <option value="lt">＜ 小于</option>
                      <option value="eq">＝ 等于</option>
                      <option value="neq">≠ 不等于</option>
                    </select>
                  </div>
                  <div class="field">
                    <label>阈值</label>
                    <input v-model="selectedNode.conditionValue" class="input" placeholder="如 100000" />
                  </div>
                </div>
                <div class="field">
                  <label>条件成立 → 走</label>
                  <select v-model="selectedNode.nextNodeKey" class="input">
                    <option value="">（顺序下一节点）</option>
                    <option v-for="t in branchTargets" :key="t.uid" :value="t.nodeKey">{{ t.name }}（{{ t.nodeKey }}）</option>
                  </select>
                </div>
                <div class="field">
                  <label>条件不成立 → 走</label>
                  <select v-model="selectedNode.elseNodeKey" class="input">
                    <option value="">（顺序下一节点）</option>
                    <option v-for="t in branchTargets" :key="t.uid" :value="t.nodeKey">{{ t.name }}（{{ t.nodeKey }}）</option>
                  </select>
                </div>
                <p class="tip">金额字段由业务侧提交审批时传入（如订单的 <code>amount</code>）；字段缺失时按“不成立”处理，避免漏批。</p>
              </template>

              <!-- 审批人 -->
              <template v-else-if="selectedNode.nodeType !== 'end'">
                <div class="field">
                  <label>审批人来源</label>
                  <select v-model="selectedNode.approverType" class="input">
                    <option value="role">按角色</option>
                    <option value="dept_manager">本部门主管</option>
                    <option value="user">指定人员</option>
                  </select>
                </div>
                <div class="field" v-if="selectedNode.approverType === 'role'">
                  <label>角色</label>
                  <select v-model="selectedNode.role" class="input">
                    <option v-for="r in roles" :key="String(r.code)" :value="String(r.code)">{{ r.name }}</option>
                  </select>
                </div>
                <label v-if="selectedNode.approverType === 'role'" class="switch">
                  <input v-model="selectedNode.sameDept" type="checkbox" /> 同部门优先
                </label>
                <div class="field" v-if="selectedNode.approverType === 'user'">
                  <label>人员工号 / 用户 ID</label>
                  <input v-model="selectedNode.userId" class="input" />
                </div>
                <label v-if="selectedNode.nodeType === 'approve'" class="switch">
                  <input v-model="selectedNode.canTransfer" type="checkbox" /> 允许转交
                </label>
              </template>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建流程 -->
    <div v-if="showCreate" class="mask" @click.self="showCreate = false">
      <div class="modal">
        <div class="modal-head">
          <h3><Icon name="plus" :size="18" /> 新建审批流程</h3>
          <span class="x" @click="showCreate = false"><Icon name="x" :size="20" /></span>
        </div>
        <div class="modal-body">
          <div class="field">
            <label>流程标识 code *</label>
            <input v-model="newDef.code" class="input" placeholder="如 overtime，需与业务单据 bizType 一致" />
          </div>
          <div class="field">
            <label>流程名称 *</label>
            <input v-model="newDef.name" class="input" placeholder="如 加班申请" />
          </div>
          <div class="field">
            <label>备注</label>
            <input v-model="newDef.remark" class="input" />
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn" @click="showCreate = false">取消</button>
          <button class="btn btn-primary" @click="createDef">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head-actions { display: flex; align-items: center; gap: 9px; flex-wrap: wrap; }
.sec { display: block; font-size: 12px; color: var(--text-3); margin-bottom: 10px; letter-spacing: .5px; }
.wrap { display: grid; grid-template-columns: 220px 1fr 280px; gap: 16px; align-items: start; }
.palette { border: 1px dashed var(--border); border-radius: var(--radius); padding: 13px; }
.pal { display: flex; align-items: center; gap: 10px; padding: 9px 11px; border: 1px solid var(--border-2); border-radius: var(--radius-sm); margin-bottom: 8px; cursor: pointer; transition: .15s var(--ease); }
.pal:hover { border-color: var(--primary); background: var(--primary-light); }
.pal .pl { font-size: 13px; font-weight: 700; }
.pal .pd { font-size: 11.5px; color: var(--text-3); }
.tip { font-size: 11.5px; color: var(--text-3); margin: 10px 0 0; line-height: 1.6; }
.canvas { border: 1px solid var(--border); border-radius: var(--radius); padding: 14px; min-height: 320px; }
.node { display: flex; align-items: center; gap: 11px; border: 1px solid var(--border-2); border-left: 4px solid var(--primary); border-radius: var(--radius-sm); padding: 11px 13px; margin-bottom: 9px; background: #fff; cursor: pointer; transition: .15s var(--ease); }
.node:hover { box-shadow: var(--shadow-sm); }
.node.sel { border-color: var(--primary); background: var(--primary-light); }
.node.dragging { opacity: .45; }
.node.cc { border-left-color: var(--blue); }
.node.end { border-left-color: var(--text-3); }
.node.condition { border-left-color: var(--warn); background: #fffdf7; }
.grip { color: var(--text-3); cursor: grab; }
.body { flex: 1; min-width: 0; }
.body .t { display: flex; align-items: center; gap: 7px; font-size: 13.5px; }
.body .key { font-size: 11px; color: var(--text-3); background: var(--slate-light); padding: 1px 6px; border-radius: 4px; }
.tag-cond { font-size: 10.5px; background: var(--warn-light); color: #b45309; padding: 1px 6px; border-radius: 4px; }
.body .r { font-size: 12px; color: var(--text-2); margin-top: 3px; }
.branches { display: flex; gap: 8px; margin-top: 5px; flex-wrap: wrap; }
.br { font-size: 11px; padding: 1px 7px; border-radius: 4px; }
.br.yes { background: var(--accent-light); color: #15803d; }
.br.no { background: var(--slate-light); color: var(--text-2); }
.acts { display: flex; align-items: center; gap: 7px; }
.acts .idx { width: 20px; height: 20px; border-radius: 50%; background: var(--slate-light); color: var(--text-2); font-size: 11px; display: flex; align-items: center; justify-content: center; }
.acts .mv { color: var(--text-3); cursor: pointer; }
.acts .mv:hover { color: var(--primary); }
.acts .del { color: var(--text-3); cursor: pointer; }
.acts .del:hover { color: var(--danger); }
.props { border: 1px solid var(--border); border-radius: var(--radius); padding: 14px; }
.field { margin-bottom: 12px; }
.field label { display: block; font-size: 12px; color: var(--text-2); margin-bottom: 5px; font-weight: 600; }
.grid2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.switch { display: flex; align-items: center; gap: 7px; font-size: 12.5px; margin-bottom: 10px; }
code { background: var(--slate-light); padding: 1px 4px; border-radius: 3px; font-size: 11px; }
.err { background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin-bottom: 12px; }
.ok { background: var(--accent-light); color: #15803d; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin-bottom: 12px; }
.warn { background: var(--warn-light); color: #b45309; padding: 9px 12px; border-radius: var(--radius-sm); font-size: 13px; margin-bottom: 12px; }
.empty.sm { padding: 26px 0; }
.empty.sm svg { opacity: .35; }
.mask { position: fixed; inset: 0; background: rgba(15,23,42,.45); backdrop-filter: blur(3px); display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 60px 20px; }
.modal { background: #fff; border-radius: var(--radius-lg); width: 520px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 20px 24px; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
</style>

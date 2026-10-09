<script setup lang="ts">
/**
 * 流程画布（#25 拆分自 WorkflowDesignerView）：节点库 + 节点列表 + 拖拽重排。
 * 组件只负责展示与交互事件，节点的增删改与顺序调整全部通过 emit 交给父视图，
 * 这样父视图只保留「定义加载 + 保存」职责。
 */
import { ref } from 'vue'
import Icon from './Icon.vue'

type NodeType = 'approve' | 'cc' | 'end' | 'condition'

interface NodeVm {
  uid: string
  nodeKey: string
  name: string
  nodeType: string
  approverType: string
  role: string
  sameDept: boolean
  userId: string
  canTransfer: boolean
  conditionField: string
  conditionOp: string
  conditionValue: string
  nextNodeKey: string
  elseNodeKey: string
}

const props = defineProps<{
  nodes: NodeVm[]
  selected: string
  roles: Record<string, string | null>[]
}>()

const emit = defineEmits<{
  (e: 'add', type: NodeType): void
  (e: 'select', uid: string): void
  (e: 'move', payload: { index: number; delta: number }): void
  (e: 'remove', uid: string): void
  (e: 'reorder', payload: { from: number; to: number }): void
}>()

const PALETTE: { type: NodeType; label: string; icon: string; desc: string }[] = [
  { type: 'approve', label: '审批节点', icon: 'check-circle', desc: '需人工处理，可驳回' },
  { type: 'condition', label: '条件分支', icon: 'git-branch', desc: '按金额等条件决定走向' },
  { type: 'cc', label: '抄送节点', icon: 'message', desc: '通知后自动通过' },
  { type: 'end', label: '结束节点', icon: 'flag', desc: '流程终点' },
]

const OP_TEXT: Record<string, string> = { gte: '≥', gt: '>', lte: '≤', lt: '<', eq: '=', neq: '≠' }

/** 拖拽中的行下标，仅用于本组件的视觉反馈 */
const dragIndex = ref(-1)

function dragStart(i: number) {
  dragIndex.value = i
}

function dragEnd() {
  dragIndex.value = -1
}

function onDrop(target: number) {
  if (dragIndex.value < 0 || dragIndex.value === target) return
  emit('reorder', { from: dragIndex.value, to: target })
  dragIndex.value = -1
}

function nodeIcon(n: NodeVm) {
  return PALETTE.find((p) => p.type === n.nodeType)?.icon ?? 'check-circle'
}

function nameOfKey(k: string) {
  return props.nodes.find((n) => n.nodeKey === k)?.name ?? k
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
  const r = props.roles.find((x) => x.code === n.role)
  return `${r?.name ?? n.role}${n.sameDept ? ' · 同部门优先' : ''}`
}
</script>

<template>
  <div class="palette">
    <b class="sec">节点库</b>
    <div v-for="p in PALETTE" :key="p.type" class="pal" @click="emit('add', p.type)">
      <Icon :name="p.icon" :size="16" />
      <div>
        <div class="pl">{{ p.label }}</div>
        <div class="pd">{{ p.desc }}</div>
      </div>
      <Icon name="plus" :size="14" />
    </div>
    <p class="tip">
      点击添加到画布；画布内可<b>拖拽重排</b>，顺序即审批顺序。条件分支按判定结果跳转到目标节点。
    </p>
  </div>

  <div class="canvas">
    <b class="sec">流程画布</b>
    <div v-if="nodes.length === 0" class="empty sm">
      <Icon name="inbox" :size="34" />
      <div>从左侧添加节点开始编排</div>
    </div>
    <div
      v-for="(n, i) in nodes"
      :key="n.uid"
      class="node"
      :class="[n.nodeType, { sel: selected === n.uid, dragging: dragIndex === i }]"
      draggable="true"
      @dragstart="dragStart(i)"
      @dragend="dragEnd"
      @dragover.prevent
      @drop="onDrop(i)"
      @click="emit('select', n.uid)"
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
        <Icon name="chevron-up" :size="14" class="mv" @click.stop="emit('move', { index: i, delta: -1 })" />
        <Icon name="chevron-down" :size="14" class="mv" @click.stop="emit('move', { index: i, delta: 1 })" />
        <Icon name="x" :size="15" class="del" @click.stop="emit('remove', n.uid)" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.palette { border: 1px dashed var(--border); border-radius: var(--radius); padding: 13px; }
.pal {
  display: flex; align-items: center; gap: 10px; padding: 9px 11px;
  border: 1px solid var(--border-2); border-radius: var(--radius-sm); margin-bottom: 8px;
  cursor: pointer; transition: .15s var(--ease);
}
.pal:hover { border-color: var(--primary); background: var(--primary-light); }
.pal .pl { font-size: 13px; font-weight: 700; }
.pal .pd { font-size: 11.5px; color: var(--text-3); }
.sec { display: block; font-size: 12px; color: var(--text-3); margin-bottom: 10px; letter-spacing: .5px; }
.tip { font-size: 11.5px; color: var(--text-3); margin: 10px 0 0; line-height: 1.6; }
.canvas { border: 1px solid var(--border); border-radius: var(--radius); padding: 14px; min-height: 320px; }
.node {
  display: flex; align-items: center; gap: 11px; border: 1px solid var(--border-2);
  border-left: 4px solid var(--primary); border-radius: var(--radius-sm); padding: 11px 13px;
  margin-bottom: 9px; background: #fff; cursor: pointer; transition: .15s var(--ease);
}
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
.acts .idx {
  width: 20px; height: 20px; border-radius: 50%; background: var(--slate-light);
  color: var(--text-2); font-size: 11px; display: flex; align-items: center; justify-content: center;
}
.acts .mv { color: var(--text-3); cursor: pointer; }
.acts .mv:hover { color: var(--primary); }
.acts .del { color: var(--text-3); cursor: pointer; }
.acts .del:hover { color: var(--danger); }
.empty.sm { padding: 26px 0; }
.empty.sm svg { opacity: .35; }
</style>

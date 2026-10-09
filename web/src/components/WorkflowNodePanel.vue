<script setup lang="ts">
/**
 * 流程节点属性面板（#25 拆分自 WorkflowDesignerView）。
 * 该视图原本约 430 行，画布（节点列表 + 拖拽）与属性面板耦合在一起；
 * 这里把右侧属性编辑抽成独立组件，视图只负责画布与保存。
 */
import Icon from './Icon.vue'

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

defineProps<{
  node: NodeVm | null
  /** 可选角色列表 */
  roles: Record<string, string | null>[]
  /** 可作为分支目标的下游节点 */
  targets: NodeVm[]
}>()

const FIELD_OPTIONS = [
  { value: 'amount', label: '单据金额（amount）' },
  { value: 'total_amount', label: '价税合计（total_amount）' },
  { value: 'duration', label: '天数（duration）' },
]
</script>

<template>
  <div class="props">
    <b class="sec">节点属性</b>

    <div v-if="!node" class="empty sm">
      <Icon name="settings" :size="30" />
      <div>选中节点后编辑</div>
    </div>

    <template v-else>
      <div class="field">
        <label>节点名称</label>
        <input v-model="node.name" class="input" />
      </div>
      <div class="field">
        <label>节点标识（nodeKey）</label>
        <input v-model="node.nodeKey" class="input" />
      </div>

      <!-- 条件分支 -->
      <template v-if="node.nodeType === 'condition'">
        <div class="field">
          <label>判断字段</label>
          <select v-model="node.conditionField" class="input">
            <option v-for="f in FIELD_OPTIONS" :key="f.value" :value="f.value">{{ f.label }}</option>
          </select>
        </div>
        <div class="grid2">
          <div class="field">
            <label>比较</label>
            <select v-model="node.conditionOp" class="input">
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
            <input v-model="node.conditionValue" class="input" placeholder="如 100000" />
          </div>
        </div>
        <div class="field">
          <label>条件成立 → 走</label>
          <select v-model="node.nextNodeKey" class="input">
            <option value="">（顺序下一节点）</option>
            <option v-for="t in targets" :key="t.uid" :value="t.nodeKey">{{ t.name }}（{{ t.nodeKey }}）</option>
          </select>
        </div>
        <div class="field">
          <label>条件不成立 → 走</label>
          <select v-model="node.elseNodeKey" class="input">
            <option value="">（顺序下一节点）</option>
            <option v-for="t in targets" :key="t.uid" :value="t.nodeKey">{{ t.name }}（{{ t.nodeKey }}）</option>
          </select>
        </div>
        <p class="tip">
          金额字段由业务侧提交审批时传入（如订单的 <code>amount</code>）；字段缺失时按“不成立”处理，避免漏批。
        </p>
      </template>

      <!-- 审批人 -->
      <template v-else-if="node.nodeType !== 'end'">
        <div class="field">
          <label>审批人来源</label>
          <select v-model="node.approverType" class="input">
            <option value="role">按角色</option>
            <option value="dept_manager">本部门主管</option>
            <option value="user">指定人员</option>
          </select>
        </div>
        <div class="field" v-if="node.approverType === 'role'">
          <label>角色</label>
          <select v-model="node.role" class="input">
            <option v-for="r in roles" :key="String(r.code)" :value="String(r.code)">{{ r.name }}</option>
          </select>
        </div>
        <label v-if="node.approverType === 'role'" class="switch">
          <input v-model="node.sameDept" type="checkbox" /> 同部门优先
        </label>
        <div class="field" v-if="node.approverType === 'user'">
          <label>人员工号 / 用户 ID</label>
          <input v-model="node.userId" class="input" />
        </div>
        <label v-if="node.nodeType === 'approve'" class="switch">
          <input v-model="node.canTransfer" type="checkbox" /> 允许转交
        </label>
      </template>
    </template>
  </div>
</template>

<style scoped>
.props { border: 1px solid var(--border); border-radius: var(--radius); padding: 14px; }
.sec { display: block; font-size: 12px; color: var(--text-3); margin-bottom: 10px; letter-spacing: .5px; }
.field { margin-bottom: 12px; }
.field label { display: block; font-size: 12px; color: var(--text-2); margin-bottom: 5px; font-weight: 600; }
.grid2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.switch { display: flex; align-items: center; gap: 7px; font-size: 12.5px; margin-bottom: 10px; }
.tip { font-size: 11.5px; color: var(--text-3); margin: 10px 0 0; line-height: 1.6; }
code { background: var(--slate-light); padding: 1px 4px; border-radius: 3px; font-size: 11px; }
.empty.sm { padding: 26px 0; }
.empty.sm svg { opacity: .35; }
</style>

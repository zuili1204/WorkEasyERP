<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import Icon from '../components/Icon.vue'

const defs = ref<Record<string, string | null>[]>([])
const nodes = ref<Record<string, string | null>[]>([])
const expanded = ref<string>('')

const TYPE_TEXT: Record<string, string> = {
  approve: '审批',
  cc: '抄送',
  start: '开始',
  end: '结束',
}

async function load() {
  defs.value = await api.workflows()
}

async function toggle(id: string) {
  if (expanded.value === id) {
    expanded.value = ''
    return
  }
  expanded.value = id
  nodes.value = await api.workflowNodes(id)
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="settings" :size="17" /> 审批流程配置</h3>
      <span class="count">共 {{ defs.length }} 个流程定义</span>
    </div>

    <div class="card-body">
      <div class="grid">
        <div v-for="d in defs" :key="String(d.id)" class="def" :class="{ open: expanded === String(d.id) }">
          <div class="head" @click="toggle(String(d.id))">
            <div>
              <div class="nm">{{ d.name }}</div>
              <div class="meta">
                <code>{{ d.code }}</code>
                <span>v{{ d.version }}</span>
                <span class="badge" :class="d.status === 'active' ? 'badge-green' : 'badge-gray'">
                  {{ d.status === 'active' ? '启用' : '停用' }}
                </span>
              </div>
            </div>
            <div class="counts">
              <span>节点 {{ d.node_count }}</span>
              <span>审批 {{ d.approve_count }}</span>
              <span>抄送 {{ d.cc_count }}</span>
              <Icon :name="expanded === String(d.id) ? 'chevron-up' : 'chevron-down'" :size="16" />
            </div>
          </div>

          <div v-if="expanded === String(d.id)" class="nodes">
            <table class="list">
              <thead>
                <tr>
                  <th>序</th>
                  <th>节点</th>
                  <th>类型</th>
                  <th>审批人规则</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(n, i) in nodes" :key="i">
                  <td>{{ n.order_idx }}</td>
                  <td>{{ n.name }} <code>{{ n.node_key }}</code></td>
                  <td>
                    <span class="badge" :class="n.node_type === 'cc' ? 'badge-blue' : n.node_type === 'end' ? 'badge-gray' : 'badge-orange'">
                      {{ TYPE_TEXT[String(n.node_type)] || n.node_type }}
                    </span>
                  </td>
                  <td class="rule">{{ n.approver_rule }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <p class="tip">
        说明：M0~M1 为固定节点配置（角色 + 同部门优先 + 金额/天数阈值规则）；可视化拖拽配置器规划在 M3。
      </p>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 12px; }
.def { border: 1px solid var(--border); border-radius: var(--radius-sm); overflow: hidden; background: #fff; }
.def.open { border-color: var(--primary); box-shadow: var(--shadow-sm); }
.head {
  display: flex; justify-content: space-between; align-items: center; gap: 12px;
  padding: 14px 16px; cursor: pointer; transition: .15s var(--ease);
}
.head:hover { background: var(--slate-light); }
.nm { font-weight: 700; font-size: 14px; }
.meta { display: flex; align-items: center; gap: 8px; margin-top: 4px; font-size: 11.5px; color: var(--text-3); }
.meta code { background: var(--slate-light); border-radius: 4px; padding: 0 5px; }
.counts { display: flex; align-items: center; gap: 10px; font-size: 11.5px; color: var(--text-3); }
.nodes { padding: 0 16px 14px; }
.rule { font-family: ui-monospace, Consolas, monospace; font-size: 11.5px; color: var(--text-3); }
.tip { font-size: 12.5px; color: var(--text-3); margin: 16px 0 0; }
code { font-family: ui-monospace, Consolas, monospace; }
</style>

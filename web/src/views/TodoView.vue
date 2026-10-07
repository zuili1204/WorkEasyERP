<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type PageResult, type TodoRow } from '../api'
import Icon from '../components/Icon.vue'

const SIZE = 8

const VIEWS: Array<[string, string]> = [
  ['pending', '待我办'],
  ['submitted', '我提交的'],
  ['done', '已办结'],
  ['cc', '抄送我'],
]

const STATUS_MAP: Record<string, { text: string; cls: string }> = {
  running: { text: '审批中', cls: 'badge-orange' },
  pending: { text: '待处理', cls: 'badge-orange' },
  approved: { text: '已通过', cls: 'badge-green' },
  rejected: { text: '已驳回', cls: 'badge-red' },
  transferred: { text: '已转交', cls: 'badge-blue' },
  skipped: { text: '已跳过', cls: 'badge-gray' },
  unread: { text: '未读', cls: 'badge-blue' },
  read: { text: '已读', cls: 'badge-gray' },
}

const state = reactive({ view: 'pending', page: 1 })
const data = ref<PageResult<TodoRow>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const dialog = reactive({ show: false, taskId: '', action: 'approve' as 'approve' | 'reject', comment: '' })

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.todos(state.view, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

function setView(v: string) {
  state.view = v
  state.page = 1
  load()
}

function openDialog(row: TodoRow, action: 'approve' | 'reject') {
  dialog.taskId = row.id
  dialog.action = action
  dialog.comment = ''
  dialog.show = true
}

async function submitAction() {
  if (dialog.action === 'approve') {
    await api.approve(dialog.taskId, dialog.comment)
  } else {
    await api.reject(dialog.taskId, dialog.comment)
  }
  dialog.show = false
  load()
}

function go(p: number) {
  if (p < 1 || p > totalPages.value) return
  state.page = p
  load()
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="card-head">
      <h3><Icon name="check-square" :size="17" /> 待办中心</h3>
      <span class="count">当前视图：{{ VIEWS.find((v) => v[0] === state.view)?.[1] }}</span>
    </div>

    <div class="card-body">
      <div class="body-grid">
        <!-- 四视图切换 -->
        <div class="todo-side">
          <div
            v-for="v in VIEWS"
            :key="v[0]"
            class="item"
            :class="{ active: state.view === v[0] }"
            @click="setView(v[0])"
          >
            <span>{{ v[1] }}</span>
          </div>
        </div>

        <div class="todo-main">
          <div class="toolbar">
            <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
            <div class="spacer" />
            <span class="count">共 {{ data.total }} 条</span>
          </div>

          <div class="table-wrap">
            <table class="list">
              <thead>
                <tr>
                  <th>事项</th>
                  <th>当前节点</th>
                  <th>状态</th>
                  <th>时间</th>
                  <th class="nosort">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in data.list" :key="r.id">
                  <td>{{ r.title }}</td>
                  <td>{{ r.nodeName || '—' }}</td>
                  <td>
                    <span class="badge" :class="(STATUS_MAP[r.status] ?? { cls: 'badge-gray' }).cls">
                      {{ (STATUS_MAP[r.status] ?? { text: r.status }).text }}
                    </span>
                  </td>
                  <td>{{ (r.createdAt || '').replace('T', ' ').slice(0, 16) }}</td>
                  <td class="op">
                    <template v-if="r.actionable">
                      <a style="color: var(--accent)" @click="openDialog(r, 'approve')">通过</a>
                      <a style="color: var(--danger)" @click="openDialog(r, 'reject')">驳回</a>
                    </template>
                    <span v-else style="color: var(--text-4)">—</span>
                  </td>
                </tr>
              </tbody>
            </table>

            <div v-if="!loading && data.total === 0" class="empty">
              <Icon name="inbox" :size="46" />
              <div>该视图下暂无数据</div>
            </div>
          </div>

          <div class="pager">
            <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
            <span>第 {{ data.page }} / {{ totalPages }} 页</span>
            <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 审批意见弹窗 -->
  <div v-if="dialog.show" class="mask" @click.self="dialog.show = false">
    <div class="modal">
      <div class="modal-head">
        <h3>
          <Icon :name="dialog.action === 'approve' ? 'check-circle' : 'x'" :size="18" />
          {{ dialog.action === 'approve' ? '审批通过' : '审批驳回' }}
        </h3>
        <span class="x" @click="dialog.show = false"><Icon name="x" :size="20" /></span>
      </div>
      <div class="modal-body">
        <div class="field">
          <label>审批意见</label>
          <textarea v-model="dialog.comment" rows="3" class="input" placeholder="选填，驳回时建议填写原因"></textarea>
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn" @click="dialog.show = false">取消</button>
        <button
          class="btn"
          :class="dialog.action === 'approve' ? 'btn-primary' : ''"
          @click="submitAction"
        >
          <Icon name="check" :size="15" /> 确认
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.body-grid { display: flex; gap: 16px; }
.todo-side { width: 150px; border-right: 1px solid var(--border-2); padding-right: 12px; flex-shrink: 0; }
.todo-side .item {
  padding: 10px 12px; border-radius: 8px; cursor: pointer; color: var(--text-2);
  margin-bottom: 3px; transition: .15s; font-size: 13.5px;
}
.todo-side .item:hover { background: var(--slate-light); }
.todo-side .item.active { background: var(--primary-light); color: var(--primary-active); font-weight: 700; }
.todo-main { flex: 1; min-width: 0; }
.nosort { cursor: default; }
.mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, .45); backdrop-filter: blur(3px);
  display: flex; align-items: flex-start; justify-content: center; z-index: 100; padding: 80px 20px;
}
.modal { background: #fff; border-radius: var(--radius-lg); width: 460px; max-width: 100%; box-shadow: var(--shadow-lg); }
.modal-head { padding: 18px 24px; border-bottom: 1px solid var(--border-2); display: flex; justify-content: space-between; align-items: center; }
.modal-head h3 { margin: 0; font-size: 17px; font-weight: 800; display: flex; align-items: center; gap: 9px; }
.modal-head .x { cursor: pointer; color: var(--text-3); display: flex; padding: 5px; border-radius: 8px; }
.modal-head .x:hover { background: var(--danger-light); color: var(--danger); }
.modal-body { padding: 24px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.field textarea { resize: vertical; }
.modal-foot { padding: 15px 24px; border-top: 1px solid var(--border-2); display: flex; justify-content: flex-end; gap: 10px; }
@media (max-width: 900px) {
  .body-grid { flex-direction: column; }
  .todo-side { width: 100%; border-right: 0; display: flex; gap: 8px; padding: 0 0 10px; }
}
</style>

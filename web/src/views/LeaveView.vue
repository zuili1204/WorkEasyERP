<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, type LeaveRow, type PageResult } from '../api'
import { toast } from '../stores/toast'
import Icon from '../components/Icon.vue'
import AppModal from '../components/AppModal.vue'
import TableShell from '../components/TableShell.vue'
import AttachmentPanel from '../components/AttachmentPanel.vue'
import { LEAVE_TYPES, WORKFLOW_STATUS, labelOf, statusOf } from '../utils/dict'
import { fmtDateTime } from '../utils/format'

const router = useRouter()
const SIZE = 8

const state = reactive({ q: '', scope: 'mine', page: 1 })
const data = ref<PageResult<LeaveRow>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const showAtt = ref(false)
const attId = ref('')
const attNo = ref('')

function openAttachments(r: LeaveRow) {
  attId.value = String(r.id)
  attNo.value = String(r.orderNo ?? '')
  showAtt.value = true
}

/** 发起 / 重新提交统一走独立页面（原弹窗已废弃） */
function goCreate() {
  router.push('/leaves/new')
}
function goResubmit(r: LeaveRow) {
  router.push({
    path: '/leaves/new',
    query: {
      id: r.id,
      leaveType: r.leaveType ?? 'annual',
      startAt: fmtDateTime(r.startAt) === '—' ? '' : String(r.startAt ?? ''),
      endAt: fmtDateTime(r.endAt) === '—' ? '' : String(r.endAt ?? ''),
      duration: String(r.duration ?? 1),
      reason: r.reason ?? '',
    },
  })
}

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.leaves({ q: state.q || undefined, scope: state.scope, page: state.page, size: SIZE })
  } finally {
    loading.value = false
  }
}

let timer: number | undefined
function onSearch() {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => {
    state.page = 1
    load()
  }, 250)
}

function setScope(s: string) {
  state.scope = s
  state.page = 1
  load()
}

async function cancel(row: LeaveRow) {
  try {
    await api.cancelLeave(row.id)
    toast.success('已撤销该请假单')
    load()
  } catch (e) {
    toast.error((e as Error)?.message || '撤销失败')
  }
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
      <h3><Icon name="calendar" :size="17" /> 请假申请</h3>
      <button class="btn btn-primary btn-sm" @click="goCreate">
        <Icon name="plus" :size="14" /> 发起请假
      </button>
    </div>

    <div class="card-body">
      <div class="toolbar">
        <div class="search-box">
          <Icon name="search" :size="15" />
          <input v-model="state.q" class="input" placeholder="搜索单号 / 姓名" @input="onSearch" />
        </div>
        <button class="btn btn-sm btn-ghost" @click="load"><Icon name="refresh" :size="15" /> 刷新</button>
        <div class="spacer" />
        <div class="seg">
          <button class="seg-btn" :class="{ active: state.scope === 'mine' }" @click="setScope('mine')">我的</button>
          <button class="seg-btn" :class="{ active: state.scope === 'dept' }" @click="setScope('dept')">本部门</button>
          <button class="seg-btn" :class="{ active: state.scope === 'all' }" @click="setScope('all')">全部</button>
        </div>
        <span class="count">共 {{ data.total }} 条</span>
      </div>

      <TableShell
        :rows="data.list"
        :loading="loading"
        :page="data.page"
        :pages="totalPages"
        :empty-icon="state.q ? 'search' : 'inbox'"
        :empty-title="state.q ? `未找到与「${state.q}」匹配的数据` : '暂无请假记录'"
        @go="go"
      >
        <template #head>
          <tr>
            <th>单号</th>
            <th>申请人</th>
            <th>类型</th>
            <th class="nosort">起止时间</th>
            <th>天数</th>
            <th>状态</th>
            <th class="nosort">操作</th>
          </tr>
        </template>

        <tr v-for="r in data.list" :key="r.id">
          <td>{{ r.orderNo }}</td>
          <td>{{ r.employeeName }}</td>
          <td>{{ labelOf(LEAVE_TYPES, r.leaveType) }}</td>
          <td>{{ fmtDateTime(r.startAt) }} ~ {{ fmtDateTime(r.endAt) }}</td>
          <td>{{ r.duration }}</td>
          <td>
            <span class="badge" :class="statusOf(WORKFLOW_STATUS, r.status).cls">
              {{ statusOf(WORKFLOW_STATUS, r.status).text }}
            </span>
          </td>
          <td class="op">
            <a @click="openAttachments(r)">附件</a>
            <a v-if="r.status === 'rejected' || r.status === 'canceled'" @click="goResubmit(r)">重新提交</a>
            <a v-if="r.status === 'pending'" style="color: var(--danger)" @click="cancel(r)">撤销</a>
            <span v-if="r.status === 'approved'" style="color: var(--text-4)">—</span>
          </td>
        </tr>

        <template #empty>
          <button class="btn btn-primary btn-sm" @click="goCreate">发起请假</button>
        </template>
      </TableShell>
    </div>
  </div>

  <AppModal :open="showAtt" :title="`附件 · ${attNo}`" icon="paperclip" @close="showAtt = false">
    <AttachmentPanel biz-type="leave" :biz-id="attId" />
    <template #footer>
      <button class="btn" @click="showAtt = false">关闭</button>
    </template>
  </AppModal>
</template>

<style scoped>
.nosort { cursor: default; }
</style>

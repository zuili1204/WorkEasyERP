<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type NoticeRow, type PageResult } from '../api'
import Icon from '../components/Icon.vue'

const SIZE = 8

const state = reactive({ onlyUnread: false, page: 1 })
const data = ref<PageResult<NoticeRow>>({ list: [], total: 0, page: 1, size: SIZE })
const loading = ref(false)
const activeId = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(data.value.total / SIZE)))

async function load() {
  loading.value = true
  try {
    data.value = await api.notices(state.onlyUnread ? true : undefined, state.page, SIZE)
  } finally {
    loading.value = false
  }
}

function toggleUnread() {
  state.onlyUnread = !state.onlyUnread
  state.page = 1
  load()
}

async function read(row: NoticeRow) {
  activeId.value = row.id
  if (row.status === 'unread') {
    await api.markRead(row.id)
    row.status = 'read'
  }
}

async function readAll() {
  await api.markAllRead()
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
      <h3><Icon name="bell" :size="17" /> 消息中心</h3>
      <div class="head-actions">
        <button class="btn btn-sm" :class="{ 'btn-primary': state.onlyUnread }" @click="toggleUnread">
          <Icon name="filter" :size="14" /> 仅看未读
        </button>
        <button class="btn btn-sm btn-ghost" @click="readAll"><Icon name="check" :size="14" /> 全部已读</button>
      </div>
    </div>

    <div class="card-body">
      <div class="msg-list">
        <div
          v-for="r in data.list"
          :key="r.id"
          class="msg-row"
          :class="{ active: activeId === r.id, unread: r.status === 'unread' }"
          @click="read(r)"
        >
          <div class="av"><Icon :name="r.type === 'todo' ? 'check-square' : 'message'" :size="18" /></div>
          <div class="body">
            <div class="t">{{ r.title }}</div>
            <div class="p">{{ r.content }}</div>
            <div class="meta">
              <span class="badge" :class="r.status === 'unread' ? 'badge-blue' : 'badge-gray'">
                {{ r.status === 'unread' ? '未读' : '已读' }}
              </span>
              <span>{{ (r.createdAt || '').replace('T', ' ').slice(0, 16) }}</span>
            </div>
          </div>
        </div>

        <div v-if="!loading && data.total === 0" class="empty">
          <Icon name="inbox" :size="46" />
          <div>{{ state.onlyUnread ? '没有未读消息' : '暂无消息' }}</div>
        </div>
      </div>

      <div class="pager">
        <span class="pbtn" :class="{ dis: state.page <= 1 }" @click="go(state.page - 1)">‹ 上一页</span>
        <span>第 {{ data.page }} / {{ totalPages }} 页 · 共 {{ data.total }} 条</span>
        <span class="pbtn" :class="{ dis: state.page >= totalPages }" @click="go(state.page + 1)">下一页 ›</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.head-actions { display: flex; gap: 8px; }
.msg-list { display: flex; flex-direction: column; }
.msg-row {
  display: flex; gap: 12px; padding: 13px 14px; border: 1px solid var(--border-2);
  border-radius: var(--radius-sm); margin-bottom: 8px; cursor: pointer; transition: .15s var(--ease);
}
.msg-row:hover { background: var(--slate-light); }
.msg-row.active { background: var(--primary-light); border-color: var(--primary-light); }
.msg-row.unread .t { font-weight: 800; }
.av {
  width: 40px; height: 40px; border-radius: 10px; background: var(--blue-light); color: #1D4ED8;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.body { flex: 1; min-width: 0; }
.t { font-weight: 700; font-size: 13.5px; margin-bottom: 3px; }
.p { color: var(--text-3); font-size: 12.5px; line-height: 1.6; }
.meta { display: flex; align-items: center; gap: 10px; margin-top: 8px; font-size: 11.5px; color: var(--text-4); }
</style>

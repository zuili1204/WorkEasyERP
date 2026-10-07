<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import Icon from './Icon.vue'

const props = defineProps<{ bizType: string; bizId: string }>()

const list = ref<Record<string, string | null>[]>([])
const loading = ref(false)
const uploading = ref(false)
const errorMsg = ref('')
const picked = ref<File | null>(null)

function sizeText(v: string | null) {
  const n = Number(v ?? 0)
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

function iconOf(name: string | null) {
  const n = (name ?? '').toLowerCase()
  const i = n.lastIndexOf('.')
  const ext = i >= 0 ? n.slice(i) : ''
  if (['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp'].includes(ext)) return 'eye'
  if (ext === '.pdf') return 'file-text'
  return 'paperclip'
}

async function load() {
  loading.value = true
  try {
    list.value = await api.attachments(props.bizType, props.bizId)
  } finally {
    loading.value = false
  }
}

function onPick(e: Event) {
  const input = e.target as HTMLInputElement
  picked.value = input.files?.[0] ?? null
}

async function upload() {
  if (!picked.value) {
    errorMsg.value = '请先选择文件'
    return
  }
  uploading.value = true
  errorMsg.value = ''
  try {
    await api.uploadAttachment(picked.value, props.bizType, props.bizId)
    picked.value = null
    const input = document.getElementById('att-file') as HTMLInputElement | null
    if (input) input.value = ''
    await load()
  } catch (err: unknown) {
    const e = err as { response?: { data?: { msg?: string } } }
    errorMsg.value = e.response?.data?.msg || '上传失败'
  } finally {
    uploading.value = false
  }
}

async function remove(id: string) {
  errorMsg.value = ''
  try {
    await api.deleteAttachment(id)
    await load()
  } catch (err: unknown) {
    const e = err as { response?: { data?: { msg?: string } } }
    errorMsg.value = e.response?.data?.msg || '删除失败'
  }
}

onMounted(load)
defineExpose({ load })
</script>

<template>
  <div class="att">
    <div class="bar">
      <input id="att-file" type="file" class="file" @change="onPick" />
      <button class="btn btn-sm btn-primary" :disabled="uploading" @click="upload">
        <Icon name="upload" :size="14" /> {{ uploading ? '上传中…' : '上传附件' }}
      </button>
      <span class="tip">支持图片 / PDF / Office / zip，单文件 ≤ 10MB</span>
    </div>

    <p v-if="errorMsg" class="err">{{ errorMsg }}</p>

    <div v-if="loading" class="dim">加载中…</div>
    <div v-else-if="list.length === 0" class="empty sm">
      <Icon name="paperclip" :size="30" />
      <div>暂无附件</div>
    </div>
    <div v-else class="rows">
      <div v-for="a in list" :key="String(a.id)" class="row">
        <Icon :name="iconOf(a.file_name)" :size="16" />
        <div class="meta">
          <div class="nm">{{ a.file_name }}</div>
          <div class="sub">
            {{ sizeText(a.file_size) }} · {{ a.uploader_name }} ·
            {{ (a.created_at ?? '').replace('T', ' ').slice(0, 16) }}
          </div>
        </div>
        <a class="dl" :href="api.attachmentUrl(String(a.id))" target="_blank">下载</a>
        <a class="rm" @click="remove(String(a.id))">删除</a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.att { display: flex; flex-direction: column; gap: 10px; }
.bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.file { font-size: 12.5px; max-width: 260px; }
.tip { font-size: 11.5px; color: var(--text-3); }
.err { background: var(--danger-light); color: #B91C1C; padding: 8px 11px; border-radius: var(--radius-sm); font-size: 12.5px; }
.dim { color: var(--text-3); font-size: 12.5px; }
.rows { display: flex; flex-direction: column; gap: 8px; max-height: 260px; overflow: auto; }
.row { display: flex; align-items: center; gap: 10px; border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 9px 11px; }
.meta { flex: 1; min-width: 0; }
.nm { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sub { font-size: 11.5px; color: var(--text-3); margin-top: 2px; }
.dl { font-size: 12.5px; color: var(--primary); text-decoration: none; }
.rm { font-size: 12.5px; color: var(--danger); }
.empty.sm { padding: 18px 0; }
.empty.sm svg { opacity: .35; }
</style>

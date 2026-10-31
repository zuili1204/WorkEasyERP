<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import http from '../api/http'
import TableShell from '../components/TableShell.vue'
import { toast } from '../stores/toast'

type Row = Record<string, unknown>

const TOKEN_KEY = 'we_platform_token'
const STATUS_LABEL: Record<string, string> = {
  active: '运行中',
  suspended: '已停用',
  failed: '开通失败',
  provisioning: '开通中',
}

const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const loading = ref(false)
const tenants = ref<Row[]>([])
const metrics = ref<Row | null>(null)
const creating = ref(false)

const form = reactive({
  code: '',
  name: '',
  dbName: '',
  adminUsername: 'admin',
  adminPassword: '123456',
  adminRole: 'boss',
  plan: '',
  expireAt: '',
})

const counts = computed(() => (metrics.value?.tenants as Record<string, number>) || {})
const pool = computed(() => (metrics.value?.pool as Record<string, unknown>) || {})
const failures = computed(() => (metrics.value?.failures as Record<string, number>) || {})
const alerts = computed(() => (metrics.value?.alerts as { level: string; message: string }[]) || [])
const expiring = computed(() => (metrics.value?.expiring as Row[]) || [])
const negative = computed(() => (metrics.value?.negativeInventory as Row[]) || [])

function headers(): Record<string, string> {
  return { 'X-Platform-Token': token.value.trim() }
}

function saveToken() {
  localStorage.setItem(TOKEN_KEY, token.value.trim())
  toast.success('平台令牌已保存')
  load()
}

async function load() {
  if (!token.value.trim()) return
  loading.value = true
  try {
    const [t, m] = await Promise.all([
      http.get('/platform/tenants', { headers: headers() }),
      http.get('/platform/metrics', { headers: headers() }),
    ])
    tenants.value = (t.data?.data || []) as Row[]
    metrics.value = (m.data?.data || null) as Row | null
  } catch {
    // 错误提示已由 http 拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function createTenant() {
  if (!form.code.trim() || !form.name.trim()) {
    toast.error('租户编码与名称不能为空')
    return
  }
  creating.value = true
  try {
    await http.post(
      '/platform/tenants',
      {
        ...form,
        code: form.code.trim(),
        name: form.name.trim(),
        dbName: form.dbName.trim(),
        expireAt: form.expireAt ? `${form.expireAt}T23:59:59+08:00` : '',
      },
      { headers: headers() },
    )
    toast.success('已提交开通，失败可在列表中重试')
    form.code = ''
    form.name = ''
    form.dbName = ''
    load()
  } catch {
    // 错误提示已由 http 拦截器统一处理
  } finally {
    creating.value = false
  }
}

async function toggleStatus(row: Row, status: string) {
  try {
    await http.patch(`/platform/tenants/${row.code}/status`, { status }, { headers: headers() })
    toast.success(status === 'active' ? '已启用' : '已停用')
    load()
  } catch {
    // 错误提示已由 http 拦截器统一处理
  }
}

async function editExpire(row: Row) {
  const current = String(row.expire_at || '').slice(0, 10)
  const v = window.prompt('到期日（YYYY-MM-DD，留空保持不变）', current)
  if (v === null) return
  const expireAt = v.trim() ? `${v.trim()}T23:59:59+08:00` : ''
  try {
    await http.patch(
      `/platform/tenants/${row.code}/status`,
      { status: row.status, expireAt },
      { headers: headers() },
    )
    toast.success('到期日已更新')
    load()
  } catch {
    // 错误提示已由 http 拦截器统一处理
  }
}

function statusLabel(s: unknown) {
  return STATUS_LABEL[String(s || '')] || String(s || '—')
}

function statusClass(s: unknown) {
  const v = String(s || '')
  if (v === 'active') return 'badge badge-green'
  if (v === 'failed') return 'badge badge-red'
  if (v === 'suspended') return 'badge badge-gray'
  return 'badge badge-orange'
}

function day(v: unknown) {
  return String(v || '').slice(0, 10) || '—'
}

onMounted(() => {
  if (token.value) load()
})
</script>

<template>
  <div class="platform-page">
    <header class="top">
      <div>
        <div class="title">平台运营</div>
        <p class="sub">租户开通 / 停用 / 到期与上线监控，使用平台令牌访问，与租户员工账号互不影响</p>
      </div>
      <div class="token-box">
        <input v-model="token" class="input" placeholder="平台令牌 X-Platform-Token" />
        <button class="btn primary" type="button" @click="saveToken">保存并刷新</button>
      </div>
    </header>

    <section v-if="!token" class="hint">
      请先在右上角填写平台令牌（服务端 `workeasy.platform.admin-token`），保存后自动加载租户与监控指标。
    </section>

    <template v-else>
      <section class="cards">
        <div class="card">
          <span class="k">租户总数</span><strong>{{ counts.total ?? 0 }}</strong>
        </div>
        <div class="card">
          <span class="k">运行中</span><strong class="green">{{ counts.active ?? 0 }}</strong>
        </div>
        <div class="card">
          <span class="k">已停用</span><strong>{{ counts.suspended ?? 0 }}</strong>
        </div>
        <div class="card">
          <span class="k">开通失败</span><strong class="red">{{ counts.failed ?? 0 }}</strong>
        </div>
        <div class="card">
          <span class="k">连接池</span><strong>{{ pool.activePools ?? 0 }} / {{ pool.maxActive ?? 0 }}</strong>
        </div>
        <div class="card">
          <span class="k">24h 迁移失败</span><strong class="red">{{ failures.migrateFailed24h ?? 0 }}</strong>
        </div>
      </section>

      <section v-if="alerts.length" class="panel">
        <h3>告警</h3>
        <ul class="alerts">
          <li v-for="(a, i) in alerts" :key="i" :class="a.level">{{ a.message }}</li>
        </ul>
      </section>

      <section v-if="expiring.length || negative.length" class="panel">
        <h3>待处理</h3>
        <div v-if="expiring.length" class="list">
          <div class="row" v-for="e in expiring" :key="String(e.code)">
            <span>租户 {{ e.code }} 到期</span>
            <span class="muted">{{ day(e.expire_at) }}（剩余 {{ e.daysLeft }} 天）</span>
          </div>
        </div>
        <div v-if="negative.length" class="list">
          <div class="row" v-for="n in negative" :key="String(n.tenantCode)">
            <span>租户 {{ n.tenantCode }} 负库存</span>
            <span class="red">{{ n.count }} 条</span>
          </div>
        </div>
      </section>

      <section class="panel">
        <h3>开通租户</h3>
        <div class="form-grid">
          <label>编码<input v-model="form.code" class="input" placeholder="小写字母/数字/下划线" /></label>
          <label>名称<input v-model="form.name" class="input" placeholder="租户名称" /></label>
          <label>库名（可空）<input v-model="form.dbName" class="input" placeholder="默认 we_tenant_编码" /></label>
          <label>管理员账号<input v-model="form.adminUsername" class="input" /></label>
          <label>管理员密码<input v-model="form.adminPassword" class="input" type="password" /></label>
          <label>管理员角色
            <select v-model="form.adminRole" class="input">
              <option value="boss">boss</option>
              <option value="manager">manager</option>
              <option value="finance">finance</option>
            </select>
          </label>
          <label>套餐<input v-model="form.plan" class="input" placeholder="如 standard" /></label>
          <label>到期日<input v-model="form.expireAt" class="input" type="date" /></label>
        </div>
        <button class="btn primary" type="button" :disabled="creating" @click="createTenant">
          {{ creating ? '提交中…' : '开通租户' }}
        </button>
        <p class="muted">开通包含建库、迁移、种子管理员与激活；失败会记录开通日志，可重复提交从失败步骤续跑。</p>
      </section>

      <section class="panel">
        <div class="panel-head">
          <h3>租户列表</h3>
          <button class="btn" type="button" @click="load">刷新</button>
        </div>
        <TableShell :rows="tenants" :loading="loading" empty-title="暂无租户">
          <template #head>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>状态</th>
              <th>库名</th>
              <th>套餐</th>
              <th>到期日</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </template>
          <tr v-for="t in tenants" :key="String(t.code)">
            <td>{{ t.code }}</td>
            <td>{{ t.name }}</td>
            <td><span :class="statusClass(t.status)">{{ statusLabel(t.status) }}</span></td>
            <td class="muted">{{ t.db_name || '—' }}</td>
            <td>{{ t.plan || '—' }}</td>
            <td>{{ day(t.expire_at) }}</td>
            <td class="muted">{{ day(t.created_at) }}</td>
            <td class="actions">
              <button
                v-if="t.status === 'active'"
                class="btn small danger"
                type="button"
                @click="toggleStatus(t, 'suspended')"
              >
                停用
              </button>
              <button
                v-else-if="t.status === 'suspended'"
                class="btn small"
                type="button"
                @click="toggleStatus(t, 'active')"
              >
                启用
              </button>
              <button v-else class="btn small" type="button" @click="toggleStatus(t, 'active')">重试</button>
              <button class="btn small" type="button" @click="editExpire(t)">到期日</button>
            </td>
          </tr>
        </TableShell>
      </section>
    </template>
  </div>
</template>

<style scoped>
.platform-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 26px 20px 60px;
}
.top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}
.title {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -.01em;
  margin: 0;
}
.sub {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--text-3);
}
.token-box {
  display: flex;
  gap: 8px;
}
.input {
  padding: 7px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 12.5px;
  color: var(--text);
  background: #FBFAF6;
  min-width: 0;
  font-family: var(--font-mono);
}
.input:focus { outline: none; border-color: var(--primary); box-shadow: var(--ring); background: #fff; }
.btn {
  padding: 7px 13px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-2);
  cursor: pointer;
  font-size: 12.5px;
  font-weight: 600;
  transition: .15s var(--ease);
}
.btn:hover { border-color: var(--primary); color: var(--primary); background: var(--primary-light); }
.btn.primary {
  background: var(--primary);
  border-color: var(--primary-active);
  color: #fff;
}
.btn.primary:hover { background: var(--primary-active); color: #fff; }
.btn.small {
  padding: 3px 9px;
  font-size: 11.5px;
}
.btn.danger {
  color: var(--danger);
  border-color: #E3BAB3;
}
.btn.danger:hover { background: var(--danger-light); border-color: var(--danger); color: var(--danger); }
.btn:disabled {
  opacity: .5;
  cursor: not-allowed;
}
.hint {
  padding: 13px 15px;
  border-radius: var(--radius-sm);
  background: var(--warn-light);
  border: 1px solid #EADBB8;
  color: #8C6318;
  font-size: 12.5px;
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.card {
  position: relative;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 2px; background: var(--primary);
}
.card .k {
  font-size: 11.5px;
  color: var(--text-3);
  letter-spacing: .02em;
}
.card strong {
  font-family: var(--font-mono);
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -.02em;
}
.green {
  color: var(--accent);
}
.red {
  color: var(--danger);
}
.panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 16px 18px;
  margin-bottom: 16px;
}
.panel h3 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 700;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-2);
}
.panel-head h3 {
  margin: 0;
}
.alerts {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.9;
}
.alerts li.critical {
  color: var(--danger);
}
.alerts li.warn {
  color: #8C6318;
}
.list {
  font-size: 12.5px;
}
.row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px dashed var(--border-2);
  font-variant-numeric: tabular-nums;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}
.form-grid label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 11.5px;
  color: var(--text-2);
  font-weight: 600;
  letter-spacing: .02em;
}
.muted {
  color: var(--text-4);
}
.badge {
  display: inline-block;
  padding: 1px 7px;
  border-radius: var(--radius-xs);
  border: 1px solid transparent;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.65;
}
.badge-green {
  background: var(--accent-light);
  color: #2F6446;
  border-color: #C9DED1;
}
.badge-red {
  background: var(--danger-light);
  color: #953027;
  border-color: #EBCBC5;
}
.badge-gray {
  background: var(--slate-light);
  color: #5A6462;
  border-color: #DFDED6;
}
.badge-orange {
  background: var(--warn-light);
  color: #8C6318;
  border-color: #EADBB8;
}
.actions {
  display: flex;
  gap: 8px;
}
</style>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { auth } from '../stores/auth'
import Icon from '../components/Icon.vue'

const router = useRouter()
const data = ref<Record<string, unknown> | null>(null)

const SCOPE_TEXT: Record<string, string> = {
  all: '全部数据',
  dept: '本部门数据',
  customer: '本人负责的客户与单据',
  self: '本人数据',
}

const ENTRY_MAP: Record<string, { text: string; cls: string }> = {
  probation: { text: '试用期', cls: 'badge-orange' },
  regular: { text: '已转正', cls: 'badge-green' },
}

onMounted(async () => {
  data.value = await api.profile()
})

function asMap(v: unknown): Record<string, unknown> {
  return (v ?? {}) as Record<string, unknown>
}

function stats(): Record<string, unknown> {
  return asMap(data.value?.stats)
}
</script>

<template>
  <div>
    <div class="hero">
      <div class="ava">{{ auth.user?.displayName?.[0] ?? 'U' }}</div>
      <div>
        <h2>{{ auth.user?.displayName }}</h2>
        <p>
          {{ asMap(data?.user).username }} · {{ asMap(data?.user).deptName || '—' }} ·
          角色 {{ ((asMap(data?.user).roles as string[]) || []).join(' / ') }} ·
          数据范围 {{ SCOPE_TEXT[String(asMap(data?.user).dataScope)] || asMap(data?.user).dataScope }}
        </p>
      </div>
    </div>

    <div class="grid2">
      <div class="card">
        <div class="card-head"><h3><Icon name="user" :size="17" /> 我的档案</h3></div>
        <div class="card-body">
          <div class="kv"><span>工号</span><b>{{ asMap(data?.employee).employee_no || '—' }}</b></div>
          <div class="kv"><span>岗位</span><b>{{ asMap(data?.employee).position || '—' }}</b></div>
          <div class="kv"><span>入职日期</span><b>{{ asMap(data?.employee).hire_date || '—' }}</b></div>
          <div class="kv"><span>在职状态</span>
            <span class="badge" :class="(ENTRY_MAP[String(asMap(data?.employee).entry_status)] ?? { cls: 'badge-gray' }).cls">
              {{ (ENTRY_MAP[String(asMap(data?.employee).entry_status)] ?? { text: asMap(data?.employee).entry_status || '—' }).text }}
            </span>
          </div>
          <div class="kv"><span>手机</span><b>{{ asMap(data?.employee).phone || '—' }}</b></div>
          <div class="kv"><span>邮箱</span><b>{{ asMap(data?.employee).email || '—' }}</b></div>
        </div>
      </div>

      <div class="card">
        <div class="card-head"><h3><Icon name="bar-chart" :size="17" /> 我的数据</h3></div>
        <div class="card-body">
          <div class="stat-grid">
            <div class="mini" @click="router.push('/leaves')">
              <div class="n">{{ stats().leaveCount }}</div>
              <div class="l">请假单</div>
            </div>
            <div class="mini" @click="router.push('/attendance/punch')">
              <div class="n">{{ stats().punchCount }}</div>
              <div class="l">打卡记录</div>
            </div>
            <div class="mini" @click="router.push('/payroll')">
              <div class="n">{{ stats().payrollCount }}</div>
              <div class="l">薪资条</div>
            </div>
            <div class="mini" @click="router.push('/todos')">
              <div class="n">{{ stats().todoCount }}</div>
              <div class="l">待我审批</div>
            </div>
            <div class="mini" @click="router.push('/todos')">
              <div class="n">{{ stats().submittedCount }}</div>
              <div class="l">我提交的</div>
            </div>
            <div class="mini" @click="router.push('/notices')">
              <div class="n">{{ stats().unreadCount }}</div>
              <div class="l">未读消息</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hero {
  display: flex; align-items: center; gap: 18px; padding: 22px 24px; margin-bottom: 16px;
  background: linear-gradient(135deg, #EEF2FF, #FFFFFF);
  border: 1px solid var(--border); border-radius: var(--radius);
}
.ava {
  width: 64px; height: 64px; border-radius: 16px; background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 26px;
}
.hero h2 { margin: 0 0 6px; font-size: 21px; font-weight: 800; }
.hero p { margin: 0; color: var(--text-2); font-size: 13.5px; }
.grid2 { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; }
.kv { display: flex; justify-content: space-between; padding: 9px 0; border-bottom: 1px solid var(--border-2); font-size: 13.5px; }
.kv span { color: var(--text-3); }
.kv b { color: var(--text); font-weight: 600; }
.stat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.mini {
  background: var(--surface-2); border: 1px solid var(--border); border-radius: var(--radius-sm);
  padding: 14px; text-align: center; cursor: pointer; transition: .15s var(--ease);
}
.mini:hover { border-color: var(--primary); background: var(--primary-light); }
.mini .n { font-size: 22px; font-weight: 800; color: var(--primary); }
.mini .l { font-size: 12px; color: var(--text-3); margin-top: 2px; }
</style>

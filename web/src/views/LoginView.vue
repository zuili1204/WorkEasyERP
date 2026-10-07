<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { auth } from '../stores/auth'
import Icon from '../components/Icon.vue'

const router = useRouter()
const username = ref('')
const password = ref('123456')
const loading = ref(false)
const error = ref('')

/** 演示账号（与原型、数据库文档 §16.2 一致，密码统一 123456） */
const ACCOUNTS = [
  { user: 'boss@we', name: '张总', role: '老板 / 总经理', color: '#4F46E5' },
  { user: 'mgr@we', name: '李经理', role: '销售部 主管', color: '#0EA5E9' },
  { user: 'fin@we', name: '赵财务', role: '财务部 会计', color: '#16A34A' },
  { user: 'sal@we', name: '王销售', role: '销售部 客户经理', color: '#F59E0B' },
  { user: 'hr@we', name: '陈人事', role: '人事行政部', color: '#EC4899' },
  { user: 'emp@we', name: '小林', role: '销售部 专员', color: '#64748B' },
]

async function doLogin() {
  error.value = ''
  if (!username.value.trim()) {
    error.value = '请输入账号，或点击上方角色卡片直接体验'
    return
  }
  loading.value = true
  try {
    const data = await api.login(username.value.trim(), password.value)
    auth.set(data.token, data.user)
    router.push('/')
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    error.value = err.response?.data?.msg || '登录失败，请检查后端服务是否已启动'
  } finally {
    loading.value = false
  }
}

function pick(a: (typeof ACCOUNTS)[number]) {
  username.value = a.user
  password.value = '123456'
  doLogin()
}
</script>

<template>
  <div class="login-wrap">
    <div class="login-brand">
      <div class="lg"><div class="logo">W</div>WorkEasyERP</div>
      <h2>企业资源计划<br />人 · 财 · 物 · 事 一体化</h2>
      <p>一个系统打通组织、审批、进销存与财务，让老板看全局、员工高效办事。</p>
      <ul>
        <li><Icon name="check-circle" :size="16" /> 角色即视图：菜单与数据范围天然不同</li>
        <li><Icon name="check-circle" :size="16" /> 流程驱动：提交 → 审批 → 执行 → 归档</li>
        <li><Icon name="check-circle" :size="16" /> 单据联动：订单 → 出库 → 应收 → 核销</li>
      </ul>
    </div>

    <div class="login-form">
      <h3>登录到控制台</h3>
      <div class="sub">点击角色卡片直接登录，演示密码均为 <b>123456</b>。</div>

      <div class="account-grid">
        <div v-for="a in ACCOUNTS" :key="a.user" class="account-card" @click="pick(a)">
          <div class="ava" :style="{ background: a.color }">{{ a.name[0] }}</div>
          <div>
            <div class="nm">{{ a.name }}</div>
            <div class="rl">{{ a.role }}</div>
          </div>
        </div>
      </div>

      <div class="field">
        <label>账号</label>
        <input v-model="username" class="input" placeholder="如 hr@we" @keyup.enter="doLogin" />
      </div>
      <div class="field">
        <label>密码</label>
        <input v-model="password" type="password" class="input" @keyup.enter="doLogin" />
      </div>

      <div v-if="error" class="err">{{ error }}</div>

      <button class="btn btn-primary submit" :disabled="loading" @click="doLogin">
        {{ loading ? '登录中…' : '登录' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.login-wrap { display: grid; grid-template-columns: 1.05fr .95fr; min-height: 100vh; }
.login-brand {
  background: linear-gradient(160deg, #1E1B4B, #312E81 45%, #4F46E5);
  color: #fff; padding: 56px 60px; display: flex; flex-direction: column; justify-content: center;
}
.lg { display: flex; align-items: center; gap: 12px; font-weight: 800; font-size: 19px; letter-spacing: .3px; }
.logo {
  width: 38px; height: 38px; border-radius: 11px; background: rgba(255, 255, 255, .16);
  display: flex; align-items: center; justify-content: center; font-weight: 800;
}
.login-brand h2 { font-size: 30px; font-weight: 800; line-height: 1.45; margin: 36px 0 14px; }
.login-brand p { color: rgba(255, 255, 255, .78); line-height: 1.8; margin: 0 0 26px; font-size: 14px; }
.login-brand ul { list-style: none; padding: 0; margin: 0; color: rgba(255, 255, 255, .9); }
.login-brand li { display: flex; align-items: center; gap: 9px; margin-bottom: 11px; font-size: 13.5px; }

.login-form { padding: 48px 44px; display: flex; flex-direction: column; justify-content: center; background: #fff; }
.login-form h3 { margin: 0 0 4px; font-size: 19px; font-weight: 800; }
.sub { color: var(--text-3); font-size: 13px; margin-bottom: 18px; }
.account-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-bottom: 18px; }
.account-card {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid var(--border);
  border-radius: var(--radius-sm); cursor: pointer; transition: .15s var(--ease); background: #fff;
}
.account-card:hover { border-color: var(--primary); box-shadow: var(--shadow-sm); transform: translateY(-1px); }
.ava { width: 34px; height: 34px; border-radius: 9px; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; flex-shrink: 0; }
.account-card .nm { font-weight: 700; font-size: 13.5px; }
.account-card .rl { font-size: 11.5px; color: var(--text-3); }
.field { margin-bottom: 14px; }
.field label { display: block; font-size: 12.5px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; }
.err {
  background: var(--danger-light); color: #B91C1C; padding: 9px 12px; border-radius: var(--radius-sm);
  font-size: 13px; margin-bottom: 12px;
}
.submit { width: 100%; justify-content: center; padding: 11px; }

@media (max-width: 900px) {
  .login-wrap { grid-template-columns: 1fr; }
  .login-brand { display: none; }
}
</style>

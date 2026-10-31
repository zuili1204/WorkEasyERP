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
  { user: 'boss@we', name: '张总', role: '老板 / 总经理', color: '#B85C2E' },
  { user: 'mgr@we', name: '李经理', role: '销售部 主管', color: '#3A6772' },
  { user: 'fin@we', name: '赵财务', role: '财务部 会计', color: '#3F7A58' },
  { user: 'sal@we', name: '王销售', role: '销售部 客户经理', color: '#B8842A' },
  { user: 'hr@we', name: '陈人事', role: '人事行政部', color: '#8C5A6E' },
  { user: 'emp@we', name: '小林', role: '销售部 专员', color: '#6C7674' },
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
      <h2>企业资源计划<br /><span>人 · 财 · 物 · 事</span> 一体化</h2>
      <p>一个系统打通组织、审批、进销存与财务，让老板看全局、员工高效办事。</p>
      <ul>
        <li><Icon name="check-circle" :size="15" /> 角色即视图：菜单与数据范围天然不同</li>
        <li><Icon name="check-circle" :size="15" /> 流程驱动：提交 → 审批 → 执行 → 归档</li>
        <li><Icon name="check-circle" :size="15" /> 单据联动：订单 → 出库 → 应收 → 核销</li>
      </ul>
      <div class="brand-foot">
        <span>WORK EASY ERP</span><span>V2 · MULTI-TENANT</span><span>40+ MODULES</span>
      </div>
    </div>

    <div class="login-form">
      <div class="form-inner">
      <div class="kicker">CONSOLE / 控制台</div>
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
  </div>
</template>

<style scoped>
.login-wrap {
  display: grid; grid-template-columns: 1.05fr .95fr; min-height: 100vh;
  background: var(--paper);
}
/* 左：墨青图纸面板 —— 网格、铜色辉光、规格脚注 */
.login-brand {
  position: relative; overflow: hidden;
  background: #0D1213; color: #F2F0E9;
  padding: 56px 62px; display: flex; flex-direction: column; justify-content: center;
  border-right: 1px solid #1C2526;
  background-image:
    linear-gradient(rgba(255, 255, 255, .045) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, .045) 1px, transparent 1px),
    radial-gradient(120% 90% at 0% 0%, rgba(184, 92, 46, .26), transparent 58%);
  background-size: 34px 34px, 34px 34px, 100% 100%;
}
.login-brand::after {
  content: ''; position: absolute; right: 28px; top: 28px; width: 54px; height: 54px;
  border-top: 1px solid rgba(184, 92, 46, .55); border-right: 1px solid rgba(184, 92, 46, .55);
}
.lg {
  display: flex; align-items: center; gap: 12px;
  font-family: var(--font-mono); font-size: 17px; font-weight: 600; letter-spacing: .06em;
}
.logo {
  width: 36px; height: 36px; border-radius: var(--radius-sm);
  background: linear-gradient(150deg, #D98B5F, #B85C2E 60%, #9C4A20);
  display: flex; align-items: center; justify-content: center;
  font-family: var(--font-mono); font-size: 18px; font-weight: 700;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, .18);
}
.login-brand h2 {
  font-size: 31px; font-weight: 700; line-height: 1.42; margin: 40px 0 16px;
  letter-spacing: -.01em;
}
.login-brand h2 span { color: var(--copper-soft); }
.login-brand p { color: #A9B4B1; line-height: 1.85; margin: 0 0 28px; font-size: 13.5px; max-width: 420px; }
.login-brand ul { list-style: none; padding: 0; margin: 0; }
.login-brand li {
  display: flex; align-items: center; gap: 9px; margin-bottom: 12px; font-size: 13px;
  color: #C8D0CD; padding-left: 12px; position: relative;
}
.login-brand li::before {
  content: ''; position: absolute; left: 0; top: 50%; transform: translateY(-50%);
  width: 3px; height: 3px; background: var(--copper);
}
.login-brand li svg { color: var(--copper-soft); flex-shrink: 0; }
.brand-foot {
  display: flex; flex-wrap: wrap; gap: 18px; margin-top: 44px; padding-top: 18px;
  border-top: 1px solid rgba(255, 255, 255, .1);
  font-family: var(--font-mono); font-size: 10.5px; letter-spacing: .14em; color: #6F7C79;
}

/* 右：纸白表单区 */
.login-form {
  display: flex; flex-direction: column; justify-content: center;
  padding: 48px 44px; background: var(--paper);
}
.form-inner { width: 100%; max-width: 440px; margin: 0 auto; }
.login-form h3 { margin: 8px 0 4px; font-size: 21px; font-weight: 700; letter-spacing: -.01em; }
.sub { color: var(--text-3); font-size: 12.5px; margin-bottom: 20px; }
.sub b { color: var(--text); font-family: var(--font-mono); font-weight: 700; }
.account-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-bottom: 20px; }
.account-card {
  display: flex; align-items: center; gap: 10px; padding: 9px 11px;
  border: 1px solid var(--border); border-left: 2px solid transparent;
  border-radius: var(--radius-sm); cursor: pointer; transition: .15s var(--ease); background: #fff;
}
.account-card:hover {
  border-color: var(--border); border-left-color: var(--primary);
  background: #FCFBF7; box-shadow: var(--shadow-xs);
}
.ava {
  width: 30px; height: 30px; border-radius: var(--radius-xs); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 12.5px; flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, .14);
}
.account-card .nm { font-weight: 700; font-size: 13px; line-height: 1.3; }
.account-card .rl { font-size: 11px; color: var(--text-3); line-height: 1.4; }
.field { margin-bottom: 14px; }
.field label {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px; color: var(--text-2); margin-bottom: 6px; font-weight: 600; letter-spacing: .03em;
}
.field label::before { content: ''; width: 2px; height: 10px; background: var(--copper); opacity: .55; }
.err {
  background: var(--danger-light); color: #953027; border: 1px solid #EBCBC5;
  padding: 8px 11px; border-radius: var(--radius-sm);
  font-size: 12.5px; margin-bottom: 12px;
}
.submit {
  width: 100%; justify-content: center; padding: 11px;
  letter-spacing: .12em; font-size: 13.5px;
}
.login-form .kicker { margin-bottom: 2px; color: var(--copper); }

@media (max-width: 900px) {
  .login-wrap { grid-template-columns: 1fr; }
  .login-brand { display: none; }
  .login-form { padding: 32px 22px; }
}
</style>

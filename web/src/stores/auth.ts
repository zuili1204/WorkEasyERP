import { reactive } from 'vue'
import type { LoginUserInfo } from '../api'

function loadUser(): LoginUserInfo | null {
  const raw = localStorage.getItem('user')
  if (!raw) return null
  try {
    return JSON.parse(raw) as LoginUserInfo
  } catch {
    return null
  }
}

export const auth = reactive({
  token: localStorage.getItem('token') || '',
  user: loadUser(),

  set(token: string, user: LoginUserInfo) {
    this.token = token
    this.user = user
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(user))
  },

  clear() {
    this.token = ''
    this.user = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  },

  get isLogin(): boolean {
    return !!this.token
  },

  /** 字段级权限：该字段对当前角色是否不可见（后端已同步脱敏，前端隐藏列与之保持一致） */
  hidden(field: string): boolean {
    return (this.user?.hiddenFields ?? []).includes(field)
  },
})

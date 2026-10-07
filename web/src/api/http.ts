import axios from 'axios'
import { cleanMsg, errMsg } from '../utils/errors'
import { toast } from '../stores/toast'

const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

/**
 * 就地净化后端返回的 msg：
 * 页面拿到的永远是"可展示文案"，不会把堆栈 / SQL / 字段名渲染到界面。
 */
function sanitize(payload: unknown, status: number) {
  if (payload && typeof payload === 'object' && 'msg' in payload) {
    const p = payload as { msg?: unknown }
    if (typeof p.msg === 'string') p.msg = cleanMsg(p.msg, status)
  }
}

http.interceptors.response.use(
  (res) => {
    sanitize(res.data, res.status)
    return res
  },
  (err) => {
    if (err.response?.status === 401) {
      const path = window.location.pathname
      if (path !== '/login') {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        window.location.href = '/login'
      }
    } else if (err.response) {
      sanitize(err.response.data, err.response.status)
      // 统一给出友好提示（文案已净化，不会暴露技术细节）
      toast.error(errMsg(err))
    } else {
      toast.error(errMsg(err))
    }
    return Promise.reject(err)
  },
)

export default http

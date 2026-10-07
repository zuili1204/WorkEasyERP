import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    // 专属端口：避免与本机其他前端项目争用 5173（strictPort 防止端口漂移导致误判）
    port: 5180,
    strictPort: true,
    host: '127.0.0.1',
    proxy: {
      // 后端 context-path 为 /api，代理时保留前缀
      '/api': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
    },
  },
})

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 开发环境代理到 Spring Boot 后端，实现前后端联调
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})

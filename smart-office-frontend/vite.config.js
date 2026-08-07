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
  },
  build: {
    rollupOptions: {
      output: {
        // 手动分包：压缩主入口首屏 JS，第三方库独立缓存提升二次加载命中率
        manualChunks: {
          'chunk-vue': ['vue', 'vue-router'],
          'chunk-element': ['element-plus'],
          'chunk-echarts': ['echarts', 'zrender']
        }
      }
    }
  }
})

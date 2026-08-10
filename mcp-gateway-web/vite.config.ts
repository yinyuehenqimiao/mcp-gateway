import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5273,
    proxy: {
      // 必须用 ^/api/，否则 /apis 页面会被误代理到后端，出现 No static resource apis
      '^/api/': {
        target: 'http://127.0.0.1:18190',
        changeOrigin: true,
      },
      '^/gateway': {
        target: 'http://127.0.0.1:18190',
        changeOrigin: true,
      },
    },
  },
})

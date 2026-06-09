import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8087',
        changeOrigin: true,
        timeout: 0,
        proxyTimeout: 0
      }
    }
  },
  build: {
    outDir: resolve(__dirname, '../shopping-orchestrator/src/main/resources/static'),
    emptyOutDir: true
  }
})

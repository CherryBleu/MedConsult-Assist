import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  cacheDir: './.vite-cache',
  define: {
    // 消除 Vue esm-bundler 运行时关于特性标志的控制台警告
    __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: false
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalizedId = id.replace(/\\/g, '/')
          if (!normalizedId.includes('/node_modules/')) return undefined

          if (normalizedId.includes('/node_modules/@element-plus/icons-vue/')) {
            return 'element-icons'
          }

          if (normalizedId.includes('/node_modules/element-plus/')) {
            return 'element-plus'
          }

          if (
            normalizedId.includes('/node_modules/vue/') ||
            normalizedId.includes('/node_modules/@vue/') ||
            normalizedId.includes('/node_modules/vue-router/') ||
            normalizedId.includes('/node_modules/pinia/')
          ) {
            return 'vue-vendor'
          }

          if (
            normalizedId.includes('/node_modules/axios/') ||
            normalizedId.includes('/node_modules/dayjs/')
          ) {
            return 'utility-vendor'
          }

          return 'vendor'
        }
      }
    }
  }
})

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
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
            const elementPlusGroups = [
              [
                'element-plus-form',
                [
                  '/components/checkbox',
                  '/components/date-picker',
                  '/components/form',
                  '/components/input',
                  '/components/input-number',
                  '/components/option',
                  '/components/radio',
                  '/components/select',
                  '/components/time-picker',
                  '/components/upload'
                ]
              ],
              [
                'element-plus-data',
                [
                  '/components/descriptions',
                  '/components/empty',
                  '/components/pagination',
                  '/components/progress',
                  '/components/rate',
                  '/components/table',
                  '/components/tag'
                ]
              ],
              [
                'element-plus-overlay',
                [
                  '/components/dialog',
                  '/components/drawer',
                  '/components/dropdown',
                  '/components/loading',
                  '/components/message',
                  '/components/message-box',
                  '/components/popover',
                  '/components/tooltip'
                ]
              ],
              [
                'element-plus-layout',
                [
                  '/components/alert',
                  '/components/aside',
                  '/components/avatar',
                  '/components/badge',
                  '/components/breadcrumb',
                  '/components/button',
                  '/components/card',
                  '/components/col',
                  '/components/container',
                  '/components/header',
                  '/components/icon',
                  '/components/main',
                  '/components/menu',
                  '/components/row',
                  '/components/tabs'
                ]
              ]
            ]

            const matchedGroup = elementPlusGroups.find(([, paths]) => {
              return paths.some((componentPath) => normalizedId.includes(componentPath))
            })

            return matchedGroup ? matchedGroup[0] : 'element-plus-core'
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

import { createApp } from 'vue'
import App from './App.vue'
import pinia from './store'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 全量导入图标库
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
// 全局样式
import './styles/global.css'

const app = createApp(App)

// 批量全局注册所有图标（官方推荐写法）
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.mount('#app')
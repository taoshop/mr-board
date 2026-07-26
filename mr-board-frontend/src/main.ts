import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import VueVirtualScroller from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import './styles/element-override.scss'

import { ElMessage } from 'element-plus'

const app = createApp(App)

app.config.errorHandler = (err, _vm, info) => {
  console.error('Vue Error:', err, info)
  ElMessage.error('页面出现错误，请刷新重试')
}

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)
app.use(pinia)
app.use(router)
app.use(VueVirtualScroller)

app.mount('#app')

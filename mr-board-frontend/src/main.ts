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
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/notification/style/css'

// 全局消息提示（供非组件代码如 request 拦截器使用）
// 使用原生 DOM 实现，避免 Element Plus ElMessage 在拦截器中偶发不显示的问题
const TOAST_CONTAINER_ID = 'global-toast-container'
function getToastContainer() {
  // 清理可能因 HMR 产生的旧容器
  document.querySelectorAll(`#${TOAST_CONTAINER_ID}`).forEach((el, idx) => {
    if (idx > 0) el.remove()
  })
  let container = document.getElementById(TOAST_CONTAINER_ID) as HTMLDivElement | null
  if (!container) {
    container = document.createElement('div')
    container.id = TOAST_CONTAINER_ID
    container.style.cssText = 'position:fixed;top:20px;left:50%;transform:translateX(-50%);z-index:99999;display:flex;flex-direction:column;gap:12px;pointer-events:none;'
    document.body.appendChild(container)
  }
  return container
}

(window as any).$showErrorMessage = (msg: string) => {
  const container = getToastContainer()
  const el = document.createElement('div')
  el.style.cssText = 'pointer-events:auto;min-width:280px;padding:10px 16px;border-radius:4px;background:#fef0f0;border:1px solid #fde2e2;color:#f56c6c;font-size:14px;display:flex;align-items:center;gap:8px;box-shadow:0 4px 12px rgba(0,0,0,0.15);'
  el.innerHTML = `<svg width="16" height="16" viewBox="0 0 1024 1024" fill="currentColor"><path d="M512 64a448 448 0 1 1 0 896 448 448 0 0 1 0-896zm0 64a384 384 0 1 0 0 768 384 384 0 0 0 0-768zm-32 224v256h64V352h-64zm0 320v64h64v-64h-64z"/></svg><span>${msg}</span>`
  container.appendChild(el)
  setTimeout(() => {
    el.style.opacity = '0'
    el.style.transition = 'opacity 0.3s'
    setTimeout(() => el.remove(), 300)
  }, 8000)
}

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

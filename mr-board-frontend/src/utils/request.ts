/// <reference types="vite/client" />
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request = axios.create({
  baseURL: (import.meta as any).env?.VITE_API_BASE_URL || '/api',
  timeout: 10000,
})

let isRefreshing = false
let refreshSubscribers: ((token: string) => void)[] = []

function subscribeTokenRefresh(callback: (token: string) => void) {
  refreshSubscribers.push(callback)
}

function onTokenRefreshed(newToken: string) {
  refreshSubscribers.forEach((callback) => callback(newToken))
  refreshSubscribers = []
}

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.accessToken) {
      config.headers.Authorization = `Bearer ${userStore.accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const { code, message } = response.data
    if (code !== 200) {
      ElMessage.error({ message: message || '请求失败', duration: 8000 })
      return Promise.reject(new Error(message))
    }
    return response.data
  },
  async (error) => {
    const originalRequest = error.config
    const userStore = useUserStore()

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve) => {
          subscribeTokenRefresh((token: string) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(request(originalRequest))
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const res = await axios.post('/api/auth/refresh', null, {
          headers: { Authorization: `Bearer ${userStore.refreshToken}` },
        })
        if (res.data.code === 200) {
          const { accessToken, refreshToken } = res.data.data
          userStore.setToken(accessToken, refreshToken)
          onTokenRefreshed(accessToken)
          originalRequest.headers.Authorization = `Bearer ${accessToken}`
          return request(originalRequest)
        }
      } catch {
        userStore.logout()
        router.push('/login')
      } finally {
        isRefreshing = false
      }
    }

    const message = error.response?.data?.message || '网络错误'
    ElMessage.error({ message, duration: 8000 })
    return Promise.reject(error)
  }
)

export default request

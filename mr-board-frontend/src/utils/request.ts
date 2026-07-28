/// <reference types="vite/client" />
import axios from 'axios'
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
    // Blob 下载响应直接返回，不解析 JSON
    if (response.config.responseType === 'blob') {
      return response.data
    }
    const { code, message: msg } = response.data
    if (code !== 200) {
      console.log('[request interceptor] error msg:', msg)
      try {
        const show = (window as any).$showErrorMessage
        if (show) show(msg || '请求失败')
      } catch (e) {
        console.error('[request interceptor] showErrorMessage failed:', e)
      }
      return Promise.reject(new Error(msg))
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

    const errMsg = error.response?.data?.message || '网络错误'
    console.log('[request interceptor] network error msg:', errMsg)
    try {
      const show = (window as any).$showErrorMessage
      if (show) show(errMsg)
    } catch (e) {
      console.error('[request interceptor] showErrorMessage failed:', e)
    }
    return Promise.reject(error)
  }
)

export default request

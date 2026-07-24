import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface UserInfo {
  id: number
  username: string
  displayName: string
  avatar: string
  roles: string[]
}

export const useUserStore = defineStore(
  'user',
  () => {
    const accessToken = ref(localStorage.getItem('accessToken') || '')
    const refreshToken = ref(localStorage.getItem('refreshToken') || '')
    const userInfo = ref<UserInfo | null>(null)

    const isLoggedIn = computed(() => !!accessToken.value)
    const isAdmin = computed(() => userInfo.value?.roles?.includes('admin') || false)

    function setToken(access: string, refresh: string) {
      accessToken.value = access
      refreshToken.value = refresh
      localStorage.setItem('accessToken', access)
      localStorage.setItem('refreshToken', refresh)
    }

    function setUserInfo(info: UserInfo) {
      userInfo.value = info
      localStorage.setItem('userInfo', JSON.stringify(info))
    }

    function initFromStorage() {
      const stored = localStorage.getItem('userInfo')
      if (stored) {
        try {
          userInfo.value = JSON.parse(stored)
        } catch {
          userInfo.value = null
        }
      }
    }

    function logout() {
      accessToken.value = ''
      refreshToken.value = ''
      userInfo.value = null
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
    }

    return {
      accessToken,
      refreshToken,
      userInfo,
      isLoggedIn,
      isAdmin,
      setToken,
      setUserInfo,
      logout,
      initFromStorage,
    }
  }
)

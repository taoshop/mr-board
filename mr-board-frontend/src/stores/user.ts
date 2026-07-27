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
    const accessToken = ref('')
    const refreshToken = ref('')
    const userInfo = ref<UserInfo | null>(null)
    const mustChangePassword = ref(false)

    const isLoggedIn = computed(() => !!accessToken.value)
    const isAdmin = computed(() => userInfo.value?.roles?.includes('admin') || false)
    const canViewReport = computed(() => {
      const roles = userInfo.value?.roles || []
      return roles.some((r) => ['admin', 'pm', 'techlead'].includes(r))
    })

    function setToken(access: string, refresh: string) {
      accessToken.value = access
      refreshToken.value = refresh
    }

    function setUserInfo(info: UserInfo) {
      userInfo.value = info
    }

    function setMustChangePassword(value: boolean) {
      mustChangePassword.value = value
    }

    function logout() {
      accessToken.value = ''
      refreshToken.value = ''
      userInfo.value = null
      mustChangePassword.value = false
    }

    return {
      accessToken,
      refreshToken,
      userInfo,
      mustChangePassword,
      isLoggedIn,
      isAdmin,
      canViewReport,
      setToken,
      setUserInfo,
      setMustChangePassword,
      logout,
    }
  },
  {
    persist: {
      key: 'mr-board-user',
      paths: ['accessToken', 'refreshToken', 'userInfo', 'mustChangePassword'],
    },
  }
)

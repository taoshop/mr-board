<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <el-icon :size="28" color="#409EFF"><Grid /></el-icon>
        <span>MR 看板</span>
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        collapse-transition
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :src="userStore.userInfo?.avatar" />
              {{ userStore.userInfo?.displayName || userStore.userInfo?.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人设置</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElNotification } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
let ws: WebSocket | null = null

const menuItems = computed(() => {
  const items = [
    { path: '/dashboard', title: '看板', icon: 'Grid' },
    { path: '/git-sources', title: 'Git 源配置', icon: 'Link' },
    { path: '/sync-logs', title: '同步日志', icon: 'Timer' },
  ]
  if (userStore.canViewReport) {
    items.push({ path: '/reports', title: '统计报表', icon: 'TrendCharts' })
  }
  if (userStore.isAdmin) {
    items.push({ path: '/users', title: '用户管理', icon: 'User' })
  }
  return items
})

function handleCommand(command: string) {
  if (command === 'logout') {
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

function connectWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/sync`
  ws = new WebSocket(wsUrl)

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'sync_started') {
        ElNotification({
          title: '同步开始',
          message: `项目「${data.projectName || data.projectId}」正在同步...`,
          type: 'info',
          duration: 3000,
        })
      } else if (data.type === 'sync_completed') {
        ElNotification({
          title: '同步完成',
          message: `项目「${data.projectName || data.projectId}」同步成功，处理 ${data.mrCount || 0} 个 MR`,
          type: 'success',
          duration: 5000,
        })
        window.dispatchEvent(new CustomEvent('sync-completed', { detail: data }))
      } else if (data.type === 'sync_failed') {
        ElNotification({
          title: '同步失败',
          message: `项目「${data.projectName || data.projectId}」同步失败：${data.errorMsg || '未知错误'}`,
          type: 'error',
          duration: 8000,
        })
      }
    } catch {
      // ignore invalid message
    }
  }

  ws.onclose = () => {
    // 5秒后重连
    setTimeout(() => {
      if (userStore.isLoggedIn) {
        connectWebSocket()
      }
    }, 5000)
  }
}

function disconnectWebSocket() {
  if (ws) {
    ws.close()
    ws = null
  }
}

onMounted(() => {
  if (userStore.isLoggedIn) {
    connectWebSocket()
  }
})

onUnmounted(() => {
  disconnectWebSocket()
})
</script>

<style scoped lang="scss">
.layout-container {
  min-height: 100vh;
}

.sidebar {
  background-color: #304156;

  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    color: #fff;
    font-size: 18px;
    font-weight: 600;
    border-bottom: 1px solid #1f2d3d;
  }
}

.header {
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: flex-end;

  .header-right {
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      color: #606266;
    }
  }
}

.main {
  background-color: #f0f2f5;
  padding: 16px;
}

@media screen and (max-width: 1440px) {
  .sidebar {
    width: 200px !important;
  }
}
</style>

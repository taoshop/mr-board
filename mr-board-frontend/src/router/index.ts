import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      name: 'Layout',
      component: () => import('@/views/LayoutView.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '看板', icon: 'Grid' },
        },
        {
          path: 'git-sources',
          name: 'GitSources',
          component: () => import('@/views/GitSourceView.vue'),
          meta: { title: 'Git 源配置', icon: 'Link' },
        },
        {
          path: 'sync-logs',
          name: 'SyncLogs',
          component: () => import('@/views/SyncLogView.vue'),
          meta: { title: '同步日志', icon: 'Timer' },
        },
        {
          path: 'reports',
          name: 'Reports',
          component: () => import('@/views/ReportView.vue'),
          meta: { title: '统计报表', icon: 'TrendCharts', report: true },
        },
        {
          path: 'users',
          name: 'UserManagement',
          component: () => import('@/views/UserManagementView.vue'),
          meta: { title: '用户管理', icon: 'User', admin: true },
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/ProfileView.vue'),
          meta: { title: '个人设置', icon: 'UserFilled' },
        },
      ],
    },
    {
      path: '/403',
      name: 'Forbidden',
      component: () => import('@/views/ForbiddenView.vue'),
      meta: { public: true },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()

  if (to.meta.public) {
    next()
    return
  }

  if (!userStore.isLoggedIn) {
    next('/login')
    return
  }

  // 强制修改密码：首次登录必须修改默认密码后才能访问其他页面
  if (userStore.mustChangePassword && to.name !== 'Profile') {
    next('/profile')
    return
  }

  if (to.meta.admin && !userStore.isAdmin) {
    next('/403')
    return
  }

  next()
})

export default router

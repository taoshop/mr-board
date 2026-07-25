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
      ],
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

  if (to.meta.admin && !userStore.isAdmin) {
    next('/403')
    return
  }

  next()
})

export default router

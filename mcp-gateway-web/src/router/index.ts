import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '总览' },
        },
        {
          path: 'systems',
          name: 'systems',
          component: () => import('@/views/SystemsView.vue'),
          meta: { title: '业务系统' },
        },
        {
          path: 'apis',
          name: 'apis',
          component: () => import('@/views/ApisView.vue'),
          meta: { title: '接口管理' },
        },
        {
          path: 'mcp-servers',
          name: 'mcp-servers',
          component: () => import('@/views/McpServersView.vue'),
          meta: { title: 'MCP 发布' },
        },
        {
          path: 'tools',
          name: 'tools',
          component: () => import('@/views/ToolsView.vue'),
          meta: { title: '已发布工具' },
        },
      ],
    },
  ],
})

export default router

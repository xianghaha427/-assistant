import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../pages/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../layouts/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('../pages/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'ai', name: 'ai', component: () => import('../pages/AiChat.vue'), meta: { title: 'AI 助手' } },
      { path: 'agents', name: 'agents', component: () => import('../pages/Agents.vue'), meta: { title: '智能体管理' } },
      { path: 'knowledge', name: 'knowledge', component: () => import('../pages/Knowledge.vue'), meta: { title: '知识库', icon: 'Collection' } },
      { path: 'meeting', name: 'meeting', component: () => import('../pages/meeting/MeetingList.vue'), meta: { title: '会议管理', icon: 'Calendar' } },
      { path: 'meeting/create', name: 'meetingCreate', component: () => import('../pages/meeting/MeetingCreate.vue'), meta: { title: '新建会议' } },
      { path: 'meeting/:id', name: 'meetingDetail', component: () => import('../pages/meeting/MeetingDetail.vue'), meta: { title: '会议详情' } },
      { path: 'task', name: 'task', component: () => import('../pages/Placeholder.vue'), meta: { title: '任务中心', icon: 'List' } },
      { path: 'news', name: 'news', component: () => import('../pages/Placeholder.vue'), meta: { title: '行业简报', icon: 'TrendCharts' } },
      { path: 'gen', name: 'gen', component: () => import('../pages/Placeholder.vue'), meta: { title: '内容生成', icon: 'EditPen' } },
      { path: 'board', name: 'board', component: () => import('../pages/Placeholder.vue'), meta: { title: '数据看板', icon: 'DataAnalysis' } },
      { path: 'profile', name: 'profile', component: () => import('../pages/Profile.vue'), meta: { title: '个人中心' } },
      { path: 'system', name: 'system', component: () => import('../pages/Admin.vue'), meta: { title: '系统管理', icon: 'Setting' } },
      { path: 'admin', redirect: '/system' }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫
router.beforeEach((to) => {
  const token = localStorage.getItem('so_token')
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return { path: '/dashboard' }
  }
})

export default router
import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../pages/Login.vue'),
    meta: { public: true, theme: 'dark' }
  },
  {
    path: '/',
    component: () => import('../layouts/Layout.vue'),
    redirect: '/dashboard',
    children: [
      // 工作台（深色主题页）
      { path: 'dashboard', name: 'dashboard', component: () => import('../pages/Dashboard.vue'), meta: { title: '工作台', theme: 'dark', icon: 'Odometer' } },
      // 协同办公
      { path: 'meeting', name: 'meeting', component: () => import('../pages/meeting/MeetingList.vue'), meta: { title: '会议中心', icon: 'Calendar' } },
      { path: 'meeting/create', name: 'meetingCreate', component: () => import('../pages/meeting/MeetingCreate.vue'), meta: { title: '新建会议' } },
      { path: 'meeting/:id', name: 'meetingDetail', component: () => import('../pages/meeting/MeetingDetail.vue'), meta: { title: '会议详情' } },
      { path: 'task', name: 'task', component: () => import('../pages/task/TaskBoard.vue'), meta: { title: '任务看板', icon: 'List' } },
      { path: 'knowledge', name: 'knowledge', component: () => import('../pages/Knowledge.vue'), meta: { title: '知识库', icon: 'Collection' } },
      // 创作工具
      { path: 'writer', redirect: '/writer/weekly' },
      { path: 'writer/weekly', name: 'writerWeekly', component: () => import('../pages/writer/WeeklyTool.vue'), meta: { title: '智能周报', icon: 'Notebook' } },
      { path: 'writer/email', name: 'writerEmail', component: () => import('../pages/writer/EmailTool.vue'), meta: { title: '邮件助手', icon: 'Message' } },
      { path: 'writer/notice', name: 'writerNotice', component: () => import('../pages/writer/NoticeTool.vue'), meta: { title: '通知公告', icon: 'Bell' } },
      { path: 'writer/ppt', name: 'writerPpt', component: () => import('../pages/writer/PptTool.vue'), meta: { title: 'PPT大纲生成', icon: 'Present' } },
      // 智能分析
      { path: 'insight', name: 'insight', component: () => import('../pages/insight/Insight.vue'), meta: { title: '行业洞察', icon: 'TrendCharts' } },
      { path: 'briefing', name: 'briefing', component: () => import('../pages/insight/BriefingHistory.vue'), meta: { title: '行业简报', icon: 'Document' } },
      // 数据看板
      { path: 'board', name: 'board', component: () => import('../pages/board/DataBoard.vue'), meta: { title: '数据看板', icon: 'DataAnalysis', theme: 'dark' } },
      // 个人与系统
      { path: 'profile', name: 'profile', component: () => import('../pages/Profile.vue'), meta: { title: '个人中心' } },
      { path: 'system', name: 'system', component: () => import('../pages/Admin.vue'), meta: { title: '系统管理', icon: 'Setting', roles: ['ADMIN'] } },
      { path: 'admin', redirect: '/system' }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 读取本地登录信息中的角色列表 */
function getLocalRoles() {
  try {
    const u = JSON.parse(localStorage.getItem('so_user') || '{}')
    return Array.isArray(u.roles) ? u.roles : []
  } catch (e) {
    return []
  }
}

// 登录守卫 + 角色守卫
router.beforeEach((to) => {
  const token = localStorage.getItem('so_token')
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token) {
    return { path: '/dashboard' }
  }
  // 角色校验：路由声明了 meta.roles 时，本地角色须包含其一
  if (to.meta.roles && to.meta.roles.length) {
    const roles = getLocalRoles()
    const allowed = to.meta.roles.some((r) => roles.includes(r))
    if (!allowed) {
      ElMessage.warning('无权限访问该页面，请联系管理员')
      return { path: '/dashboard' }
    }
  }
})

// 全局主题应用：按路由 meta.theme 切换 html.dark（覆盖 Layout 之外的页面，如登录页保持深色）
router.afterEach((to) => {
  if (to.meta.theme === 'dark') {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
})

export default router

<template>
  <el-container class="layout">
    <!-- ====== 侧边栏 ====== -->
    <el-aside class="sidebar" :width="collapsed ? '68px' : '224px'">
      <div class="sidebar-logo" @click="$router.push('/')">
        <div class="logo-mark"><Building2 :size="20" /></div>
        <transition name="fade">
          <div v-if="!collapsed" class="logo-text">恒生<span class="grad-text">智能办公助手</span></div>
        </transition>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="sidebar-menu"
        background-color="transparent"
        text-color="var(--text-secondary)"
        active-text-color="var(--accent)"
      >
        <el-menu-item-group>
          <template #title><span class="group-label">工作概览</span></template>
          <el-menu-item index="/dashboard"><LayoutDashboard :size="18" /><template #title>工作台</template></el-menu-item>
          <el-menu-item index="/board"><BarChart3 :size="18" /><template #title>数据看板</template></el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group>
          <template #title><span class="group-label">协同办公</span></template>
          <el-menu-item index="/meeting"><Calendar :size="18" /><template #title>会议中心</template></el-menu-item>
          <el-menu-item index="/task"><Columns3 :size="18" /><template #title>任务看板</template></el-menu-item>
          <el-menu-item index="/knowledge"><Library :size="18" /><template #title>知识库</template></el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group>
          <template #title><span class="group-label">创作工具</span></template>
          <el-menu-item index="/writer/weekly"><FileText :size="18" /><template #title>智能周报</template></el-menu-item>
          <el-menu-item index="/writer/email"><Mail :size="18" /><template #title>邮件助手</template></el-menu-item>
          <el-menu-item index="/writer/notice"><Bell :size="18" /><template #title>通知公告</template></el-menu-item>
          <el-menu-item index="/writer/ppt"><Presentation :size="18" /><template #title>PPT大纲</template></el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group>
          <template #title><span class="group-label">智能分析</span></template>
          <el-menu-item index="/insight"><TrendingUp :size="18" /><template #title>行业洞察</template></el-menu-item>
          <el-menu-item index="/briefing"><Newspaper :size="18" /><template #title>行业简报</template></el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group v-if="isAdmin">
          <template #title><span class="group-label">管理</span></template>
          <el-menu-item index="/system"><Settings :size="18" /><template #title>系统管理</template></el-menu-item>
          <el-menu-item index="/security"><Shield :size="18" /><template #title>安全管控</template></el-menu-item>
        </el-menu-item-group>
      </el-menu>

      <div class="sidebar-footer">
        <div class="engine-badge">
          <span class="engine-dot"></span>
          <span v-if="!collapsed">智能引擎在线</span>
        </div>
      </div>
    </el-aside>

    <el-container>
      <!-- ====== 顶栏 ====== -->
      <el-header class="topbar">
        <div class="topbar-left">
          <span class="collapse-btn" @click="collapsed = !collapsed">
            <PanelLeft v-if="!collapsed" :size="18" /><PanelRight v-else :size="18" />
          </span>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>恒生智能办公助手</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentPageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="topbar-right">
          <el-popover placement="bottom-end" :width="360" trigger="click" @show="loadNotifications">
            <template #reference>
              <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
                <el-button text class="bell-btn"><Bell :size="20" /></el-button>
              </el-badge>
            </template>
            <div class="notif-popover">
              <div class="notif-header">
                <span class="notif-header-title">消息通知</span>
                <el-button text size="small" @click="handleMarkAllRead" v-if="unreadCount > 0">全部已读</el-button>
              </div>
              <div v-if="notifications.length === 0" class="empty-state" style="padding:24px"><div class="empty-desc">暂无通知</div></div>
              <div v-for="n in notifications" :key="n.id" class="notif-item" :class="{ unread: n.is_read === 0 }" @click="openNotif(n)">
                <div class="notif-title">{{ n.title }}</div>
                <div class="notif-content">{{ n.content }}</div>
                <div class="notif-time">{{ n.create_time }}</div>
              </div>
            </div>
          </el-popover>
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-chip">
              <el-avatar :size="30" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ userInfo.real_name || '未登录' }}</span>
              <ChevronDown :size="14" class="chip-arrow" />
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMe, logout, getNotifications, getUnreadCount, markNotificationRead, markAllNotificationsRead } from '../api'
import { Building2, LayoutDashboard, BarChart3, Calendar, Columns3, Library, FileText, Mail, Bell, Presentation, TrendingUp, Newspaper, Settings, Shield, ChevronDown, PanelLeft, PanelRight } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const userInfo = ref({})

const currentPageTitle = computed(() => route.meta.title || '工作台')
const avatarText = computed(() => (userInfo.value.real_name || userInfo.value.realName || 'U').slice(0, 1))
const activeMenu = computed(() => route.path.startsWith('/meeting') ? '/meeting' : route.path)

const localRoles = (() => {
  try { const u = JSON.parse(localStorage.getItem('so_user') || '{}'); return Array.isArray(u.roles) ? u.roles : [] }
  catch (e) { return [] }
})()
const isAdmin = localRoles.includes('ADMIN')

onMounted(async () => {
  try { userInfo.value = await getMe(); loadUnreadCount() } catch (e) { /* ignore */ }
})

const notifications = ref([])
const unreadCount = ref(0)
async function loadNotifications() { try { notifications.value = await getNotifications() || [] } catch (e) {} }
async function loadUnreadCount() { try { const r = await getUnreadCount(); unreadCount.value = r.count || 0 } catch (e) {} }
async function openNotif(n) {
  try { await markNotificationRead(n.id); unreadCount.value = Math.max(0, unreadCount.value - 1) } catch (e) {}
  if (n.link) router.push(n.link)
}
async function handleMarkAllRead() {
  try { await markAllNotificationsRead(); unreadCount.value = 0; notifications.value.forEach(n => n.is_read = 1) } catch (e) {}
}

async function handleCommand(cmd) {
  if (cmd === 'logout') {
    try { await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消', beforeClose: (action, instance, done) => done() }) } catch (e) { return }
    try { await logout() } catch (e) {}
    localStorage.removeItem('so_token'); localStorage.removeItem('so_user')
    ElMessage.success('已退出登录'); router.push('/login')
  } else if (cmd === 'profile') { router.push('/profile') }
}
</script>

<style scoped>
.layout { height: 100vh; }

/* 侧边栏 */
.sidebar { display: flex; flex-direction: column; background: var(--sidebar-bg); border-right: 1px solid var(--border); transition: width 0.22s ease; overflow: hidden; }
.sidebar-logo { display: flex; align-items: center; gap: 10px; padding: 16px 16px 12px; cursor: pointer; white-space: nowrap; }
.logo-mark { width: 34px; height: 34px; flex-shrink: 0; border-radius: 8px; background: var(--accent); color: #fff; display: flex; align-items: center; justify-content: center; }
.logo-text { font-size: 17px; font-weight: 600; letter-spacing: -0.01em; color: var(--text-primary); white-space: nowrap; }
.sidebar-menu { flex: 1; border-right: none !important; padding: 4px 8px; overflow-y: auto; }
.sidebar-menu :deep(.el-menu-item-group__title) { padding: 14px 12px 6px; height: auto; line-height: 1.4; }
.group-label { font-size: 11px; font-weight: 600; letter-spacing: 0.06em; color: var(--text-tertiary); text-transform: uppercase; }
.sidebar-menu.el-menu--collapse :deep(.el-menu-item-group__title) { display: none; }
.sidebar-menu .el-menu-item { border-radius: 8px; margin-bottom: 2px; height: 40px; line-height: 40px; transition: background 0.15s; display: flex; align-items: center; gap: 8px; }
.sidebar-menu .el-menu-item:hover { background: var(--surface-hover); }
.sidebar-menu .el-menu-item.is-active { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
.sidebar-menu .el-menu-item.is-active::before { content: ""; position: absolute; left: 0; top: 50%; transform: translateY(-50%); width: 3px; height: 16px; border-radius: 2px; background: var(--accent); }
.sidebar-footer { padding: 12px 16px; border-top: 1px solid var(--border); }
.engine-badge { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--text-tertiary); white-space: nowrap; }
.engine-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); flex-shrink: 0; }

/* 顶栏 */
.topbar { height: 54px; display: flex; align-items: center; justify-content: space-between; background: var(--topbar-bg); border-bottom: 1px solid var(--border); }
.topbar-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn { font-size: 18px; color: var(--text-secondary); cursor: pointer; transition: color 0.15s; }
.collapse-btn:hover { color: var(--accent); }
:deep(.el-breadcrumb__inner) { color: var(--text-tertiary) !important; font-weight: 400; }
:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) { color: var(--text-primary) !important; font-weight: 500; }
.topbar-right { display: flex; align-items: center; gap: 8px; }

.user-chip { display: flex; align-items: center; gap: 8px; padding: 4px 12px 4px 4px; border-radius: 24px; border: 1px solid var(--border); background: var(--surface); cursor: pointer; transition: all 0.15s; }
.user-chip:hover { background: var(--surface-hover); border-color: var(--border-strong); }
.user-avatar { background: var(--accent); color: #fff; font-size: 12px; font-weight: 600; }
.user-name { font-size: 13px; color: var(--text-primary); font-weight: 500; }
.chip-arrow { font-size: 12px; color: var(--text-tertiary); }

/* 通知 */
.bell-btn { color: var(--text-secondary); }
.bell-btn:hover { color: var(--accent); }
.notif-popover { max-height: 400px; overflow-y: auto; }
.notif-header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 10px; border-bottom: 1px solid var(--border-light); margin-bottom: 8px; }
.notif-header-title { font-weight: 600; font-size: 14px; }
.notif-item { padding: 10px 8px; border-radius: 6px; cursor: pointer; transition: background 0.12s; }
.notif-item:hover { background: var(--surface-hover); }
.notif-item.unread { background: var(--accent-soft); }
.notif-title { font-size: 13px; font-weight: 500; color: var(--text-primary); margin-bottom: 2px; }
.notif-content { font-size: 12px; color: var(--text-secondary); margin-bottom: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notif-time { font-size: 11px; color: var(--text-tertiary); }

/* 内容区 */
.main-content { overflow-y: auto; padding: 32px 36px; background: var(--bg); }
.fade-enter-active { transition: opacity 0.15s ease 0.06s; }
.fade-enter-from { opacity: 0; }
</style>

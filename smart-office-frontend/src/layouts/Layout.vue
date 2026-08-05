<template>
  <el-container class="layout">
    <!-- ====== 侧边栏 ====== -->
    <el-aside class="sidebar" :width="collapsed ? '72px' : '240px'">
      <div class="sidebar-logo" @click="$router.push('/')">
        <div class="logo-mark">✦</div>
        <transition name="fade">
          <div v-if="!collapsed" class="logo-text">Fin<span class="grad-text">Pilot</span></div>
        </transition>
      </div>

      <el-menu
        :default-active="$route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="sidebar-menu"
        background-color="transparent"
        text-color="var(--text-secondary)"
        active-text-color="var(--text-primary)"
      >
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>
        <el-menu-item index="/ai">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>AI 助手</template>
        </el-menu-item>
        <el-menu-item index="/meeting">
          <el-icon><Calendar /></el-icon>
          <template #title>智能会议</template>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Collection /></el-icon>
          <template #title>知识库</template>
        </el-menu-item>
        <el-menu-item index="/agents">
          <el-icon><MagicStick /></el-icon>
          <template #title>智能体管理</template>
        </el-menu-item>
        <el-menu-item index="/profile">
          <el-icon><User /></el-icon>
          <template #title>个人中心</template>
        </el-menu-item>
        <el-menu-item index="/system">
          <el-icon><Setting /></el-icon>
          <template #title>系统管理</template>
        </el-menu-item>
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
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>FinPilot</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentPageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="topbar-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-chip">
              <el-avatar :size="30" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ userInfo.real_name || '未登录' }}</span>
              <el-icon class="chip-arrow"><ArrowDown /></el-icon>
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

      <!-- ====== 内容区 ====== -->
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
import { getMe, logout } from '../api'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const userInfo = ref({})

const currentPageTitle = computed(() => route.meta.title || '工作台')
const avatarText = computed(() => (userInfo.value.real_name || 'U').slice(0, 1))

onMounted(async () => {
  try {
    userInfo.value = await getMe()
  } catch (e) {
    // 拦截器会处理 401
  }
})

async function handleCommand(cmd) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    await logout()
    localStorage.removeItem('so_token')
    localStorage.removeItem('so_user')
    ElMessage.success('已退出登录')
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.layout { height: 100vh; }

/* ====== 侧边栏 ====== */
.sidebar {
  display: flex;
  flex-direction: column;
  background: rgba(10, 15, 28, 0.55);
  backdrop-filter: blur(24px);
  border-right: 1px solid var(--border-light);
  transition: width 0.25s ease;
  overflow: hidden;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 18px 18px 14px;
  cursor: pointer;
  white-space: nowrap;
}
.logo-mark {
  width: 36px; height: 36px;
  flex-shrink: 0;
  border-radius: 11px;
  background: var(--grad-brand);
  color: #fff;
  font-size: 17px;
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--glow-brand);
}
.logo-text {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}

.sidebar-menu {
  flex: 1;
  border-right: none !important;
  padding: 8px;
}
.sidebar-menu .el-menu-item {
  border-radius: 11px;
  margin-bottom: 4px;
  height: 46px;
  line-height: 46px;
  transition: all 0.2s ease;
  position: relative;
}
.sidebar-menu .el-menu-item:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}
.sidebar-menu .el-menu-item.is-active {
  background: var(--accent-soft);
  color: var(--text-primary);
}
/* 激活项左侧发光指示条 */
.sidebar-menu .el-menu-item.is-active::before {
  content: "";
  position: absolute;
  left: 0; top: 50%;
  transform: translateY(-50%);
  width: 3px; height: 20px;
  border-radius: 2px;
  background: var(--grad-brand);
  box-shadow: 0 0 10px rgba(77, 141, 255, 0.8);
}
.sidebar-menu .el-menu-item.is-active .el-icon { color: var(--accent); }

.sidebar-footer {
  padding: 14px 18px;
  border-top: 1px solid var(--border-light);
}
.engine-badge {
  display: flex; align-items: center; gap: 8px;
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
}
.engine-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: #34D399;
  box-shadow: 0 0 8px rgba(52, 211, 153, 0.9);
  animation: breathe 2.2s ease-in-out infinite;
  flex-shrink: 0;
}
@keyframes breathe {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.4; }
}

/* ====== 顶栏 ====== */
.topbar {
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(10, 15, 28, 0.5);
  backdrop-filter: blur(24px);
  border-bottom: 1px solid var(--border-light);
}
.topbar-left { display: flex; align-items: center; gap: 12px; }

.collapse-btn {
  font-size: 18px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: color 0.15s;
}
.collapse-btn:hover { color: var(--accent); }

:deep(.el-breadcrumb__inner) { color: var(--text-tertiary) !important; font-weight: 400; }
:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--text-primary) !important; font-weight: 500;
}

.topbar-right { display: flex; align-items: center; gap: 10px; }

.user-chip {
  display: flex; align-items: center; gap: 9px;
  padding: 5px 12px 5px 5px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  cursor: pointer;
  transition: all 0.18s ease;
}
.user-chip:hover {
  background: var(--surface-hover);
  border-color: var(--border-strong);
}
.user-avatar {
  background: var(--grad-brand);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 0 12px rgba(77, 141, 255, 0.4);
}
.user-name { font-size: 13px; color: var(--text-primary); font-weight: 500; }
.chip-arrow { font-size: 12px; color: var(--text-tertiary); }

/* ====== 内容区 ====== */
.main-content {
  overflow-y: auto;
  padding: 22px 26px;
}

.fade-enter-active { transition: opacity 0.18s ease 0.08s; }
.fade-enter-from { opacity: 0; }
</style>

<template>
  <div class="login-page">
    <!-- 极光光斑 -->
    <div class="aurora aurora-1"></div>
    <div class="aurora aurora-2"></div>
    <div class="aurora aurora-3"></div>
    <!-- 细网格纹理 -->
    <div class="grid-lines"></div>

    <div class="login-card">
      <div class="brand">
        <div class="brand-logo">✦</div>
        <div class="brand-name">Fin<span class="grad-text">Pilot</span></div>
        <div class="brand-tag">金融企业智能办公提效平台</div>
      </div>

      <el-form class="login-form" @submit.prevent="handleLogin">
        <el-input
          v-model="form.username"
          size="large"
          placeholder="用户名"
          :prefix-icon="User"
          @keyup.enter="handleLogin"
        />
        <el-input
          v-model="form.password"
          size="large"
          type="password"
          show-password
          placeholder="密码"
          :prefix-icon="Lock"
          @keyup.enter="handleLogin"
          style="margin-top: 14px"
        />
        <el-button
          size="large"
          class="login-btn"
          :loading="loading"
          @click="handleLogin"
        >
          进入平台
        </el-button>
      </el-form>

      <div class="login-footer">
        <span class="status-dot"></span> 智能体引擎已就绪 · 由大模型提供算力支持
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '../api'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

/**
 * 校验 redirect 参数，防开放重定向：
 * - 必须以单个 / 开头的站内相对路径
 * - 拒绝 // 开头（协议相对 URL）
 * - 拒绝跳回登录页本身
 */
function safeRedirect(raw) {
  if (!raw || typeof raw !== 'string') return null
  if (!raw.startsWith('/') || raw.startsWith('//')) return null
  if (/\/login(\/|\?|$)/.test(raw)) return null
  return raw
}

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await login(form)
    localStorage.setItem('so_token', res.token)
    localStorage.setItem('so_user', JSON.stringify(res))
    ElMessage.success('欢迎回来')
    // 优先回跳守卫携带的 redirect；无/非法时三角色统一落地工作台
    const redirect = safeRedirect(route.query.redirect)
    router.replace(redirect || '/dashboard')
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

/* ---- 极光光斑（缓慢漂移） ---- */
.aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  pointer-events: none;
}
.aurora-1 {
  width: 560px; height: 420px;
  background: rgba(8, 180, 188, 0.28);
  top: -140px; left: -100px;
  animation: drift1 16s ease-in-out infinite alternate;
}
.aurora-2 {
  width: 480px; height: 400px;
  background: rgba(4, 126, 133, 0.20);
  bottom: -160px; right: -80px;
  animation: drift2 20s ease-in-out infinite alternate;
}
.aurora-3 {
  width: 300px; height: 300px;
  background: rgba(92, 208, 214, 0.12);
  top: 55%; left: 12%;
  animation: drift1 24s ease-in-out infinite alternate-reverse;
}
@keyframes drift1 {
  from { transform: translate(0, 0) scale(1); }
  to   { transform: translate(60px, 40px) scale(1.12); }
}
@keyframes drift2 {
  from { transform: translate(0, 0) scale(1.05); }
  to   { transform: translate(-50px, -30px) scale(1); }
}

/* ---- 细网格 ---- */
.grid-lines {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.025) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse 70% 60% at 50% 50%, black 30%, transparent 100%);
  -webkit-mask-image: radial-gradient(ellipse 70% 60% at 50% 50%, black 30%, transparent 100%);
  pointer-events: none;
}

/* ---- 玻璃登录卡 ---- */
.login-card {
  width: 400px;
  padding: 44px 40px 32px;
  border-radius: 24px;
  background: rgba(6, 46, 51, 0.62);
  border: 1px solid var(--border);
  backdrop-filter: blur(28px);
  box-shadow: var(--shadow-lg), var(--glow-soft);
  position: relative;
  z-index: 1;
}

.brand { text-align: center; margin-bottom: 32px; }
.brand-logo {
  width: 62px; height: 62px;
  margin: 0 auto 16px;
  border-radius: 18px;
  background: var(--grad-brand);
  display: flex; align-items: center; justify-content: center;
  font-size: 30px;
  color: #fff;
  box-shadow: var(--glow-brand);
  position: relative;
}
.brand-logo::after {
  content: "";
  position: absolute;
  inset: -6px;
  border-radius: 23px;
  border: 1px solid rgba(8, 180, 188, 0.35);
  animation: pulse-ring 2.6s ease-out infinite;
}
@keyframes pulse-ring {
  0%   { transform: scale(0.92); opacity: 0.9; }
  70%  { transform: scale(1.12); opacity: 0; }
  100% { transform: scale(1.12); opacity: 0; }
}
.brand-name {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}
.brand-tag {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
  letter-spacing: 0.06em;
}

.login-form { margin-top: 8px; }
.login-btn {
  width: 100%;
  margin-top: 22px;
  height: 46px;
  font-size: 15px;
  letter-spacing: 0.12em;
}

.login-footer {
  margin-top: 26px;
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
}
.status-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: #34D399;
  box-shadow: 0 0 8px rgba(52, 211, 153, 0.9);
  animation: breathe 2s ease-in-out infinite;
}
@keyframes breathe {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.45; }
}
</style>

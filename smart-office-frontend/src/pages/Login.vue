<template>
  <div class="login-page">
    <div class="login-shell">
      <section class="brand-panel">
        <div class="brand-mark"><Building2 :size="24" /></div>
        <div class="brand-kicker">FINOFFICE PLATFORM</div>
        <h1>恒生智能办公助手</h1>
        <p>让每一次会议、协作与决策，都有据可依。</p>
        <div class="brand-rule"></div>
        <small>金融企业智能办公提效平台</small>
      </section>

      <section class="login-card">
        <div class="login-heading">
          <div class="brand-name">欢迎回来</div>
          <div class="brand-desc">登录平台，继续处理今日工作</div>
        </div>

      <el-form class="login-form" @submit.prevent="handleLogin">
        <el-input v-model="form.username" size="large" placeholder="用户名" @keyup.enter="handleLogin"><template #prefix><User :size="18" /></template></el-input>
        <el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" @keyup.enter="handleLogin" style="margin-top:14px"><template #prefix><Lock :size="18" /></template></el-input>
        <el-button size="large" class="login-btn" type="primary" :loading="loading" @click="handleLogin">登录</el-button>
      </el-form>

        <div class="login-footer"><span class="secure-dot"></span>安全连接 · 企业内部系统</div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Building2, User, Lock } from 'lucide-vue-next'
import { login } from '../api'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

function safeRedirect(raw) {
  if (!raw || typeof raw !== 'string') return null
  if (!raw.startsWith('/') || raw.startsWith('//')) return null
  if (/\/login(\/|\?|$)/.test(raw)) return null
  return raw
}

async function handleLogin() {
  if (!form.username || !form.password) { ElMessage.warning('请输入用户名和密码'); return }
  loading.value = true
  try {
    const res = await login(form)
    localStorage.setItem('so_token', res.token)
    localStorage.setItem('so_user', JSON.stringify(res))
    ElMessage.success('欢迎回来')
    const redirect = safeRedirect(route.query.redirect)
    router.replace(redirect || '/dashboard')
  } catch (e) { /* 拦截器处理 */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
  padding: 28px;
}
.login-shell { width: min(900px, 100%); min-height: 560px; display: grid; grid-template-columns: 1.08fr .92fr; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg); overflow: hidden; }
.brand-panel { display: flex; flex-direction: column; justify-content: center; padding: 58px; background: #123D2C; color: #F6F5F2; }
.brand-mark { width: 48px; height: 48px; display: flex; align-items: center; justify-content: center; margin-bottom: 28px; border: 1px solid rgba(255,255,255,.28); border-radius: 10px; color: #E7F0EB; }
.brand-kicker { color: #B5D0C0; font-size: 11px; font-weight: 700; letter-spacing: .14em; }
.brand-panel h1 { margin: 16px 0 10px; font-size: 30px; letter-spacing: -.03em; }
.brand-panel p { max-width: 280px; margin: 0; color: #D2E1D9; font-size: 14px; line-height: 1.8; }
.brand-rule { width: 42px; height: 1px; margin: 34px 0 14px; background: #7BAF96; }
.brand-panel small { color: #B5D0C0; font-size: 12px; }
.login-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 58px 52px;
  background: var(--surface);
}
.login-heading { margin-bottom: 30px; }
.brand-name { font-size: 23px; font-weight: 700; letter-spacing: -.02em; color: var(--text-primary); }
.brand-desc { margin-top: 6px; font-size: 13px; color: var(--text-secondary); }
.login-form { margin-top: 8px; }
.login-btn { width: 100%; margin-top: 22px; height: 44px; font-size: 15px; }
.login-footer { display: flex; align-items: center; justify-content: center; gap: 6px; margin-top: 24px; text-align: center; font-size: 12px; color: var(--text-tertiary); }
.secure-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--el-color-success); }
@media (max-width: 680px) {
  .login-page { padding: 16px; }
  .login-shell { display: block; min-height: auto; }
  .brand-panel { padding: 30px; }
  .brand-panel h1 { font-size: 25px; }
  .login-card { padding: 34px 28px 30px; }
}
</style>

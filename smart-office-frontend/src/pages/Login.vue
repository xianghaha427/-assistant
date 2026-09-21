<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-icon"><Building2 :size="28" /></div>
        <div class="brand-name">恒生智能办公助手</div>
        <div class="brand-desc">金融企业智能办公提效平台</div>
      </div>

      <el-form class="login-form" @submit.prevent="handleLogin">
        <el-input v-model="form.username" size="large" placeholder="用户名" @keyup.enter="handleLogin"><template #prefix><User :size="18" /></template></el-input>
        <el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" @keyup.enter="handleLogin" style="margin-top:14px"><template #prefix><Lock :size="18" /></template></el-input>
        <el-button size="large" class="login-btn" type="primary" :loading="loading" @click="handleLogin">登录</el-button>
      </el-form>

      <div class="login-footer">智能引擎已就绪 · 安全连接</div>
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
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg);
}
.login-card {
  width: 400px;
  padding: 44px 40px 36px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
}
.brand { text-align: center; margin-bottom: 32px; }
.brand-icon {
  width: 56px; height: 56px;
  margin: 0 auto 14px;
  border-radius: 12px;
  background: var(--accent);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
}
.brand-name { font-size: 22px; font-weight: 600; color: var(--text-primary); }
.brand-desc { margin-top: 6px; font-size: 13px; color: var(--text-secondary); }
.login-form { margin-top: 8px; }
.login-btn { width: 100%; margin-top: 22px; height: 44px; font-size: 15px; }
.login-footer { margin-top: 24px; text-align: center; font-size: 12px; color: var(--text-tertiary); }
</style>

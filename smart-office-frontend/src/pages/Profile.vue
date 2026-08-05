<template>
  <div class="profile-page">
    <div class="profile-header">
      <h2 class="page-title">个人<span class="grad-text">中心</span></h2>
      <div class="page-subtitle">管理您的个人信息与账户安全</div>
    </div>

    <div class="profile-grid" v-loading="loading">
      <!-- 左侧：头像 + 基本信息 -->
      <div class="profile-left">
        <div class="card avatar-card">
          <div class="avatar-wrapper" @click="triggerAvatarUpload">
            <el-avatar :size="88" :src="userInfo.avatar" class="user-avatar-lg">
              {{ avatarText }}
            </el-avatar>
            <div class="avatar-overlay">
              <el-icon><Camera /></el-icon>
              <span>更换头像</span>
            </div>
            <input
              ref="avatarInput"
              type="file"
              accept="image/*"
              style="display: none"
              @change="handleAvatarChange"
            />
          </div>
          <div class="user-name-lg">{{ userInfo.real_name || userInfo.username }}</div>
          <div class="user-username">@{{ userInfo.username }}</div>
          <div class="user-roles">
            <el-tag
              v-for="role in userInfo.roles"
              :key="role"
              size="small"
              effect="plain"
              round
            >
              {{ roleLabel(role) }}
            </el-tag>
          </div>
        </div>

        <div class="card info-card">
          <div class="info-card-title">账户信息</div>
          <div class="info-row">
            <span class="info-label">所属部门</span>
            <span class="info-value">{{ userInfo.dept_name || '未分配' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">用户角色</span>
            <span class="info-value">{{ roleNames }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">账户状态</span>
            <span class="info-value status-active">正常</span>
          </div>
        </div>
      </div>

      <!-- 右侧：编辑表单 -->
      <div class="profile-right">
        <div class="card form-card">
          <div class="form-card-header">
            <span>基本信息</span>
            <el-button type="primary" size="small" :loading="saving" @click="handleSaveProfile">
              保存修改
            </el-button>
          </div>
          <el-form :model="profileForm" label-position="top" class="profile-form">
            <el-form-item label="用户名">
              <el-input :model-value="userInfo.username" disabled />
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="profileForm.real_name" placeholder="请输入姓名" />
            </el-form-item>
            <el-form-item label="所属部门">
              <el-input :model-value="userInfo.dept_name || '未分配'" disabled />
            </el-form-item>
          </el-form>
        </div>

        <div class="card form-card">
          <div class="form-card-header">
            <span>账户安全</span>
            <el-button size="small" @click="showPasswordDialog = true">
              修改密码
            </el-button>
          </div>
          <div class="security-info">
            <div class="security-row">
              <span class="security-label">登录密码</span>
              <span class="security-value">已设置</span>
            </div>
            <div class="security-row">
              <span class="security-label">建议</span>
              <span class="security-value">定期更换密码以保障账户安全</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="showPasswordDialog" title="修改密码" width="440px">
      <el-form :model="passwordForm" label-position="top" class="password-form">
        <el-form-item label="旧密码">
          <el-input
            v-model="passwordForm.old_password"
            type="password"
            show-password
            placeholder="请输入旧密码"
          />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model="passwordForm.new_password"
            type="password"
            show-password
            placeholder="请输入新密码（至少6位）"
          />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input
            v-model="passwordForm.confirm_password"
            type="password"
            show-password
            placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPasswordDialog = false">取消</el-button>
        <el-button type="primary" :loading="changingPwd" @click="handleChangePassword">
          确认修改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera } from '@element-plus/icons-vue'
import { getMe, updateProfile, changePassword } from '../api'

const loading = ref(false)
const saving = ref(false)
const changingPwd = ref(false)
const showPasswordDialog = ref(false)
const avatarInput = ref(null)
const userInfo = ref({})

const profileForm = reactive({
  real_name: ''
})

const passwordForm = reactive({
  old_password: '',
  new_password: '',
  confirm_password: ''
})

const avatarText = computed(() => (userInfo.value.real_name || userInfo.value.username || 'U').slice(0, 1))
const roleNames = computed(() => {
  const roles = userInfo.value.roles || []
  return roles.map(r => roleLabel(r)).join('、') || '无'
})

const ROLE_MAP = {
  ADMIN: '管理员',
  USER: '普通用户',
  MANAGER: '部门经理'
}
const roleLabel = (code) => ROLE_MAP[code] || code

onMounted(async () => {
  loading.value = true
  try {
    const data = await getMe()
    userInfo.value = data
    profileForm.real_name = data.real_name || ''
  } finally {
    loading.value = false
  }
})

function triggerAvatarUpload() {
  avatarInput.value?.click()
}

function handleAvatarChange(e) {
  const file = e.target.files[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    userInfo.value.avatar = ev.target.result
    ElMessage.success('头像已更换（演示模式，未上传服务器）')
  }
  reader.readAsDataURL(file)
}

async function handleSaveProfile() {
  saving.value = true
  try {
    await updateProfile({ real_name: profileForm.real_name })
    const data = await getMe()
    userInfo.value = data
    ElMessage.success('个人信息已保存')
  } finally {
    saving.value = false
  }
}

async function handleChangePassword() {
  if (!passwordForm.old_password) {
    ElMessage.warning('请输入旧密码')
    return
  }
  if (!passwordForm.new_password || passwordForm.new_password.length < 6) {
    ElMessage.warning('新密码长度不能少于6位')
    return
  }
  if (passwordForm.new_password !== passwordForm.confirm_password) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  changingPwd.value = true
  try {
    await changePassword({
      old_password: passwordForm.old_password,
      new_password: passwordForm.new_password
    })
    ElMessage.success('密码修改成功')
    showPasswordDialog.value = false
    passwordForm.old_password = ''
    passwordForm.new_password = ''
    passwordForm.confirm_password = ''
  } finally {
    changingPwd.value = false
  }
}
</script>

<style scoped>
.profile-page {
  min-height: 100%;
}

.profile-header {
  margin-bottom: 24px;
}

.profile-grid {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 20px;
  align-items: start;
}

.profile-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 头像卡片 */
.avatar-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 20px 24px;
  text-align: center;
}

.avatar-wrapper {
  position: relative;
  cursor: pointer;
  margin-bottom: 16px;
}

.user-avatar-lg {
  background: var(--grad-brand);
  color: #fff;
  font-size: 28px;
  font-weight: 600;
  box-shadow: 0 0 30px rgba(77, 141, 255, 0.4);
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
  color: #fff;
  font-size: 12px;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.user-name-lg {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.user-username {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.user-roles {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
}

/* 信息卡片 */
.info-card {
  padding: 20px;
}

.info-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.info-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.info-value {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
}

.status-active {
  color: #34D399;
}

/* 表单卡片 */
.form-card {
  padding: 24px;
}

.form-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-light);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.profile-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.profile-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-size: 13px;
}

/* 安全信息 */
.security-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.security-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.security-label {
  font-size: 13px;
  color: var(--text-secondary);
  min-width: 70px;
}

.security-value {
  font-size: 13px;
  color: var(--text-primary);
}

/* 密码弹窗 */
.password-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.password-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-size: 13px;
}

/* 响应式 */
@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>

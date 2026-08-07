<template>
  <div class="admin-page">
    <div class="admin-header">
      <div>
        <h2 class="page-title">系统<span class="grad-text">管理</span></h2>
        <div class="page-subtitle">用户管理、角色配置与操作日志审计</div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="admin-tabs">
      <!-- 用户管理 -->
      <el-tab-pane label="用户管理" name="users">
        <div class="tab-toolbar">
          <el-input
            v-model="userKeyword"
            placeholder="搜索用户名或姓名"
            clearable
            style="width: 260px"
            @clear="loadUsers"
            @keyup.enter="loadUsers"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="openCreateUser">＋ 新增用户</el-button>
        </div>

        <el-table
          :data="users"
          v-loading="usersLoading"
          stripe
          class="admin-table"
        >
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="real_name" label="姓名" width="120" />
          <el-table-column prop="dept_name" label="部门" width="140">
            <template #default="{ row }">
              {{ row.dept_name || '未分配' }}
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="160">
            <template #default="{ row }">
              <el-tag
                v-for="role in row.roles"
                :key="role"
                size="small"
                effect="plain"
                round
                style="margin-right: 4px"
              >
                {{ roleLabel(role) }}
              </el-tag>
              <span v-if="!row.roles?.length" class="text-muted">无</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <span :class="row.status === 1 ? 'status-active' : 'status-disabled'">
                {{ row.status === 1 ? '正常' : '禁用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="openEditUser(row)">编辑</el-button>
              <el-button size="small" text type="danger" @click="handleDeleteUser(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="userPage"
            v-model:page-size="userPageSize"
            :total="userTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadUsers"
            @current-change="loadUsers"
          />
        </div>
      </el-tab-pane>

      <!-- 角色管理 -->
      <el-tab-pane label="角色管理" name="roles">
        <el-table :data="roles" v-loading="rolesLoading" stripe class="admin-table">
          <el-table-column prop="roleCode" label="角色编码" width="200" />
          <el-table-column prop="roleName" label="角色名称" width="200" />
        </el-table>
      </el-tab-pane>

      <!-- 操作日志 -->
      <el-tab-pane label="操作日志" name="logs">
        <el-table :data="logs" v-loading="logsLoading" stripe class="admin-table">
          <el-table-column prop="createTime" label="时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.create_time) }}
            </template>
          </el-table-column>
          <el-table-column prop="username" label="用户" width="120" />
          <el-table-column prop="module" label="模块" width="120" />
          <el-table-column prop="action" label="操作" min-width="180" />
          <el-table-column prop="result" label="结果" width="100">
            <template #default="{ row }">
              <span :class="(row.result || '').toLowerCase() === 'success' ? 'status-active' : 'status-error'">
                {{ (row.result || '').toLowerCase() === 'success' ? '成功' : '失败' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="140" />
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="logPage"
            v-model:page-size="logPageSize"
            :total="logTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadLogs"
            @current-change="loadLogs"
          />
        </div>
      </el-tab-pane>

      <!-- 智能体引擎（阶段1：知识问答双通道切换） -->
      <el-tab-pane label="智能体引擎" name="engine">
        <div v-loading="engineLoading" class="engine-panel">
          <div class="engine-title">企业知识问答助手 · 回答引擎</div>
          <div class="engine-desc">
            自研引擎：平台内置大模型直连回答、数据不出域；Dify 引擎：走云端智能体链路。自研引擎异常时自动回落 Dify。
          </div>
          <el-radio-group v-model="engineValue" class="engine-radio">
            <el-radio value="native">自研引擎（native）</el-radio>
            <el-radio value="dify">Dify（dify）</el-radio>
          </el-radio-group>
          <div class="engine-actions">
            <el-button type="primary" :loading="engineSaving" @click="handleSaveEngine">保存</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 用户创建/编辑弹窗 -->
    <el-dialog
      v-model="userDialogVisible"
      :title="isEditUser ? '编辑用户' : '新增用户'"
      width="500px"
    >
      <el-form :model="userForm" label-position="top" class="user-form">
        <el-form-item label="用户名" v-if="!isEditUser">
          <el-input v-model="userForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" v-if="!isEditUser">
          <el-input v-model="userForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="userForm.real_name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="userForm.dept_id" placeholder="部门 ID（可选）" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.role_codes" multiple placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in roles"
              :key="role.roleCode"
              :label="roleLabel(role.roleCode)"
              :value="role.roleCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" v-if="isEditUser">
          <el-select v-model="userForm.status" style="width: 100%">
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="userSaving" @click="handleSaveUser">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getUsers, createUser, updateUser, deleteUser, getRoles, getLogs, getAgents, updateAgent } from '../api'

const activeTab = ref('users')

// 用户管理
const users = ref([])
const usersLoading = ref(false)
const userPage = ref(1)
const userPageSize = ref(20)
const userTotal = ref(0)
const userKeyword = ref('')
const userDialogVisible = ref(false)
const isEditUser = ref(false)
const editingUserId = ref(null)
const userSaving = ref(false)

const userForm = reactive({
  username: '',
  password: '',
  real_name: '',
  dept_id: '',
  role_codes: [],
  status: 1
})

// 角色管理
const roles = ref([])
const rolesLoading = ref(false)

// 操作日志
const logs = ref([])
const logsLoading = ref(false)
const logPage = ref(1)
const logPageSize = ref(20)
const logTotal = ref(0)

const ROLE_MAP = {
  ADMIN: '管理员',
  USER: '普通用户',
  MANAGER: '部门经理'
}
const roleLabel = (code) => ROLE_MAP[code] || code

function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  loadRoles()
  loadUsers()
})

watch(activeTab, (tab) => {
  if (tab === 'users') loadUsers()
  else if (tab === 'roles') loadRoles()
  else if (tab === 'logs') loadLogs()
  else if (tab === 'engine') loadEngine()
})

async function loadUsers() {
  usersLoading.value = true
  try {
    const data = await getUsers({
      page: userPage.value,
      size: userPageSize.value,
      keyword: userKeyword.value || undefined
    })
    users.value = data.records || []
    userTotal.value = data.total || 0
  } finally {
    usersLoading.value = false
  }
}

async function loadRoles() {
  rolesLoading.value = true
  try {
    roles.value = await getRoles() || []
  } finally {
    rolesLoading.value = false
  }
}

async function loadLogs() {
  logsLoading.value = true
  try {
    const data = await getLogs({
      page: logPage.value,
      size: logPageSize.value
    })
    logs.value = data.records || []
    logTotal.value = data.total || 0
  } finally {
    logsLoading.value = false
  }
}

function openCreateUser() {
  isEditUser.value = false
  editingUserId.value = null
  Object.assign(userForm, {
    username: '',
    password: '',
    real_name: '',
    dept_id: '',
    role_codes: [],
    status: 1
  })
  userDialogVisible.value = true
}

function openEditUser(row) {
  isEditUser.value = true
  editingUserId.value = row.id
  Object.assign(userForm, {
    real_name: row.real_name || '',
    dept_id: row.dept_id || '',
    role_codes: row.roles || [],
    status: row.status
  })
  userDialogVisible.value = true
}

async function handleSaveUser() {
  if (!isEditUser.value) {
    if (!userForm.username) {
      ElMessage.warning('请输入用户名')
      return
    }
    if (!userForm.password) {
      ElMessage.warning('请输入密码')
      return
    }
  }
  userSaving.value = true
  try {
    if (isEditUser.value) {
      await updateUser(editingUserId.value, {
        real_name: userForm.real_name,
        dept_id: userForm.dept_id ? Number(userForm.dept_id) : null,
        role_codes: userForm.role_codes,
        status: userForm.status
      })
      ElMessage.success('已更新')
    } else {
      await createUser({
        username: userForm.username,
        password: userForm.password,
        real_name: userForm.real_name,
        dept_id: userForm.dept_id ? Number(userForm.dept_id) : null,
        role_codes: userForm.role_codes
      })
      ElMessage.success('创建成功')
    }
    userDialogVisible.value = false
    await loadUsers()
  } finally {
    userSaving.value = false
  }
}

async function handleDeleteUser(row) {
  await ElMessageBox.confirm(`确定删除用户「${row.real_name || row.username}」吗？`, '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  await loadUsers()
}

// 智能体引擎（阶段1：知识问答双通道，读写 agent_config#1 的 engine 列）
const engineLoading = ref(false)
const engineSaving = ref(false)
const engineValue = ref('dify')

async function loadEngine() {
  engineLoading.value = true
  try {
    const list = await getAgents() || []
    const kbAgent = list.find((a) => a.id === 1)
    engineValue.value = kbAgent?.engine || 'dify'
  } finally {
    engineLoading.value = false
  }
}

async function handleSaveEngine() {
  engineSaving.value = true
  try {
    await updateAgent(1, { engine: engineValue.value })
    ElMessage.success('引擎配置已保存')
  } finally {
    engineSaving.value = false
  }
}
</script>

<style scoped>
.admin-page {
  min-height: 100%;
}

.admin-header {
  margin-bottom: 20px;
}

.admin-tabs {
  margin-top: 8px;
}

.admin-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}

.admin-tabs :deep(.el-tabs__nav-wrap::after) {
  background-color: var(--border-light);
}

.admin-tabs :deep(.el-tabs__item) {
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
}

.admin-tabs :deep(.el-tabs__item.is-active) {
  color: var(--text-primary);
}

.admin-tabs :deep(.el-tabs__active-bar) {
  background: var(--grad-brand);
}

.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
}

.admin-table {
  border-radius: var(--radius-md);
  overflow: hidden;
}

.admin-table :deep(.el-table__header th) {
  background: var(--surface-solid);
  color: var(--text-secondary);
  font-weight: 500;
  font-size: 13px;
}

.admin-table :deep(.el-table__row:hover > td) {
  background: var(--surface-hover) !important;
}

.admin-table :deep(.el-table__row > td) {
  background: var(--surface);
  border-bottom: 1px solid var(--border-light);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.text-muted {
  color: var(--text-tertiary);
  font-size: 13px;
}

.status-active {
  color: #34D399;
  font-weight: 500;
}

.status-disabled {
  color: var(--text-tertiary);
}

.status-error {
  color: #F87171;
  font-weight: 500;
}

.user-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.user-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-size: 13px;
}

.engine-panel {
  padding: 8px 4px;
}

.engine-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.engine-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.engine-radio {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 18px;
}

.engine-actions {
  margin-top: 4px;
}
</style>

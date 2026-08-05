<template>
  <div>
    <div class="agents-header">
      <div>
        <h2 class="page-title">智能体<span class="grad-text"> 管理</span></h2>
        <div class="page-subtitle">创建、配置并启用面向业务场景的 AI 助手</div>
      </div>
      <el-button type="primary" @click="openCreate">＋ 创建智能体</el-button>
    </div>

    <div class="agents-grid" v-loading="loading">
      <div
        v-for="a in agents"
        :key="a.id"
        class="agent-card card"
        :class="{ disabled: !a.enabled }"
      >
        <div class="agent-card-top">
          <div class="agent-card-icon">{{ iconOf(a.icon) }}</div>
          <el-switch v-model="a.enabled" :active-value="1" :inactive-value="0" @change="toggleEnabled(a)" />
        </div>
        <div class="agent-card-name">{{ a.name }}</div>
        <div class="agent-card-desc">{{ a.description || '暂无描述' }}</div>
        <div class="agent-card-meta">
          <span class="meta-tag">
            <span class="meta-dot"></span>{{ typeOf(a.icon) }}
          </span>
          <span class="meta-dify" :class="{ on: !!a.dify_app_id }">
            <span class="dify-dot"></span>{{ a.dify_app_id ? 'Dify 已接入' : 'Dify 待接入' }}
          </span>
        </div>
        <div class="agent-card-actions">
          <el-button size="small" text @click="openEdit(a)">编辑配置</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(a)">删除</el-button>
        </div>
      </div>

      <!-- 新建卡片 -->
      <div class="agent-card add-card" @click="openCreate">
        <div class="add-icon">＋</div>
        <div class="add-text">创建智能体</div>
      </div>
    </div>

    <!-- 创建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑智能体' : '创建智能体'"
      width="520px"
    >
      <el-form :model="form" label-width="90px" label-position="top">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="例如：会议纪要助手" />
        </el-form-item>
        <el-form-item label="类型图标">
          <el-select v-model="form.icon" style="width: 100%">
            <el-option label="💬 对话问答" value="chat" />
            <el-option label="📄 文档纪要" value="document" />
            <el-option label="📈 行业洞察" value="trend" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="一句话描述该智能体的用途" />
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input v-model="form.system_prompt" type="textarea" :rows="4" placeholder="定义智能体的角色与行为规范" />
        </el-form-item>
        <el-form-item label="Dify 应用密钥（可选）">
          <el-input v-model="form.dify_app_id" placeholder="接入 Dify 后填写，留空则使用内置演示模型" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAgents, createAgent, updateAgent, deleteAgent } from '../api'

const agents = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const editingId = ref(null)

/** icon 关键字 → 表情 / 类型名 */
const ICON_MAP = { chat: '💬', document: '📄', trend: '📈' }
const TYPE_MAP = { chat: '对话问答', document: '文档纪要', trend: '行业洞察' }
const iconOf = (key) => ICON_MAP[key] || '✦'
const typeOf = (key) => TYPE_MAP[key] || '通用助手'

const emptyForm = {
  name: '',
  icon: 'chat',
  description: '',
  system_prompt: '',
  dify_app_id: ''
}
const form = reactive({ ...emptyForm })

onMounted(() => load())

async function load() {
  loading.value = true
  try {
    agents.value = await getAgents()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, emptyForm)
  dialogVisible.value = true
}

function openEdit(a) {
  isEdit.value = true
  editingId.value = a.id
  Object.assign(form, {
    name: a.name,
    icon: a.icon || 'chat',
    description: a.description || '',
    system_prompt: a.system_prompt || '',
    dify_app_id: a.dify_app_id || ''
  })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      icon: form.icon,
      description: form.description,
      system_prompt: form.system_prompt,
      dify_app_id: form.dify_app_id
    }
    if (isEdit.value) {
      await updateAgent(editingId.value, payload)
      ElMessage.success('已保存')
    } else {
      await createAgent({ ...payload, enabled: 1 })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(a) {
  await updateAgent(a.id, { enabled: a.enabled })
  ElMessage.success(a.enabled ? '已启用' : '已停用')
}

async function handleDelete(a) {
  await ElMessageBox.confirm(`确定删除智能体「${a.name}」吗？`, '提示', { type: 'warning' })
  await deleteAgent(a.id)
  ElMessage.success('已删除')
  await load()
}
</script>

<style scoped>
.agents-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.agents-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.agent-card {
  position: relative;
  padding: 22px;
  transition: all 0.22s ease;
  overflow: hidden;
}
/* 悬停时顶部渐变光带 */
.agent-card::after {
  content: "";
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: var(--grad-aurora);
  opacity: 0;
  transition: opacity 0.22s ease;
}
.agent-card:hover {
  transform: translateY(-3px);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md), var(--glow-soft);
}
.agent-card:hover::after { opacity: 1; }
.agent-card.disabled { opacity: 0.55; }

.agent-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14px;
}
.agent-card-icon {
  width: 48px; height: 48px;
  border-radius: 14px;
  background: var(--grad-brand);
  color: #fff;
  font-size: 23px;
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--glow-brand);
}

.agent-card-name {
  font-size: 15.5px;
  font-weight: 600;
  color: var(--text-primary);
}
.agent-card-desc {
  font-size: 12.5px;
  color: var(--text-secondary);
  margin: 6px 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 34px;
}

.agent-card-meta {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.meta-tag {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 11.5px;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 3px 9px;
  border-radius: 999px;
  border: 1px solid rgba(77, 141, 255, 0.22);
}
.meta-dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 6px rgba(77, 141, 255, 0.9);
}
.meta-dify {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 11.5px;
  color: var(--text-tertiary);
  padding: 3px 9px;
  border-radius: 999px;
  border: 1px solid var(--border);
}
.dify-dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: var(--text-tertiary);
}
.meta-dify.on {
  color: #34D399;
  border-color: rgba(52, 211, 153, 0.3);
}
.meta-dify.on .dify-dot {
  background: #34D399;
  box-shadow: 0 0 6px rgba(52, 211, 153, 0.9);
}

.agent-card-actions {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--border-light);
  padding-top: 10px;
  margin-top: 4px;
}

/* ---- 新建占位卡 ---- */
.add-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 220px;
  border-style: dashed;
  border-color: var(--border);
  background: transparent;
  backdrop-filter: none;
  cursor: pointer;
  color: var(--text-tertiary);
}
.add-card::before { display: none; }
.add-card:hover {
  border-color: rgba(77, 141, 255, 0.5);
  color: var(--accent);
  background: var(--accent-soft);
  box-shadow: 0 0 30px rgba(77, 141, 255, 0.1);
  transform: translateY(-3px);
}
.add-icon {
  width: 46px; height: 46px;
  border-radius: 13px;
  border: 1px solid var(--border-strong);
  display: flex; align-items: center; justify-content: center;
  font-size: 22px;
}
.add-text { font-size: 13px; }
</style>

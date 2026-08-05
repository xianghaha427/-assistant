<template>
  <div>
    <!-- Hero 区域 -->
    <div class="knowledge-hero">
      <div>
        <h2 class="page-title">知识库<span class="grad-text"> 管理</span></h2>
        <div class="page-subtitle">企业制度、流程文档智能管理，支持知识问答</div>
      </div>
      <el-button type="primary" @click="openUploadDialog">📄 上传文档</el-button>
    </div>

    <!-- 文档列表 -->
    <div class="docs-grid" v-loading="loading">
      <div
        v-for="doc in documents"
        :key="doc.id"
        class="doc-card card"
      >
        <div class="doc-card-top">
          <div class="doc-icon" :class="fileTypeClass(doc.file_type)">
            {{ fileIcon(doc.file_type) }}
          </div>
          <span class="status-tag" :class="'status-' + doc.status">
            <span class="status-dot"></span>
            {{ statusText(doc.status) }}
          </span>
        </div>
        <div class="doc-name">{{ doc.name }}</div>
        <div class="doc-meta">
          <span>{{ doc.word_count || 0 }} 字</span>
          <span>{{ formatTime(doc.create_time) }}</span>
        </div>
        <div class="doc-actions">
          <el-button size="small" text type="danger" @click="handleDelete(doc)">删除</el-button>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && documents.length === 0" class="empty-state">
        <div class="empty-icon">📚</div>
        <div class="empty-text">暂无文档，点击上方按钮上传</div>
      </div>
    </div>

    <!-- 知识问答区域 -->
    <div class="qa-section card">
      <div class="qa-header">
        <h3 class="qa-title">💡 知识问答</h3>
        <div class="qa-subtitle">基于知识库内容智能回答您的问题</div>
      </div>
      <div class="qa-messages" v-if="qaHistory.length > 0">
        <div v-for="(item, idx) in qaHistory" :key="idx" class="qa-item" :class="item.role">
          <div class="qa-bubble">
            <div class="qa-content">{{ item.content }}</div>
            <div v-if="item.sources" class="qa-sources">
              <span class="source-label">引用来源：</span>{{ item.sources }}
            </div>
          </div>
        </div>
      </div>
      <div class="qa-input">
        <el-input
          v-model="qaQuery"
          placeholder="输入您的问题，例如：公司的报销流程是什么？"
          @keyup.enter="handleSearch"
          :disabled="searching"
        />
        <el-button type="primary" @click="handleSearch" :loading="searching">发送</el-button>
      </div>
    </div>

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadVisible" title="上传文档" width="560px">
      <el-tabs v-model="uploadTab">
        <el-tab-pane label="文本输入" name="text">
          <el-form label-position="top">
            <el-form-item label="文档名称">
              <el-input v-model="textForm.name" placeholder="请输入文档名称" />
            </el-form-item>
            <el-form-item label="文档内容">
              <el-input
                v-model="textForm.content"
                type="textarea"
                :rows="8"
                placeholder="粘贴或输入文档内容..."
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="文件上传" name="file">
          <el-form label-position="top">
            <el-form-item label="文档名称（可选）">
              <el-input v-model="fileForm.name" placeholder="不填则使用文件名" />
            </el-form-item>
            <el-form-item label="选择文件">
              <el-upload
                ref="uploadRef"
                drag
                :auto-upload="false"
                :limit="1"
                accept=".txt,.pdf,.docx,.doc"
                :on-change="handleFileChange"
                :on-exceed="handleExceed"
              >
                <div class="upload-dragger">
                  <div class="upload-icon">📁</div>
                  <div class="upload-text">将文件拖到此处，或<em>点击上传</em></div>
                  <div class="upload-hint">支持 txt / pdf / docx 格式</div>
                </div>
              </el-upload>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">确认上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDocuments, uploadDocument, deleteDocument, searchKnowledge } from '../api'

const documents = ref([])
const loading = ref(false)

// 上传相关
const uploadVisible = ref(false)
const uploadTab = ref('text')
const uploading = ref(false)
const uploadRef = ref(null)
const selectedFile = ref(null)

const textForm = reactive({ name: '', content: '' })
const fileForm = reactive({ name: '' })

// 问答相关
const qaQuery = ref('')
const qaHistory = ref([])
const searching = ref(false)

onMounted(() => load())

async function load() {
  loading.value = true
  try {
    documents.value = await getDocuments()
  } finally {
    loading.value = false
  }
}

function openUploadDialog() {
  textForm.name = ''
  textForm.content = ''
  fileForm.name = ''
  selectedFile.value = null
  uploadTab.value = 'text'
  uploadVisible.value = true
}

function handleFileChange(file) {
  selectedFile.value = file.raw
}

function handleExceed() {
  ElMessage.warning('只能上传一个文件')
}

async function handleUpload() {
  uploading.value = true
  try {
    if (uploadTab.value === 'text') {
      if (!textForm.name.trim()) {
        ElMessage.warning('请输入文档名称')
        return
      }
      if (!textForm.content.trim()) {
        ElMessage.warning('请输入文档内容')
        return
      }
      await uploadDocument({ name: textForm.name, content: textForm.content })
    } else {
      if (!selectedFile.value) {
        ElMessage.warning('请选择文件')
        return
      }
      const formData = new FormData()
      formData.append('file', selectedFile.value)
      if (fileForm.name.trim()) {
        formData.append('name', fileForm.name)
      }
      await uploadDocument(formData)
    }
    ElMessage.success('上传成功')
    uploadVisible.value = false
    await load()
  } finally {
    uploading.value = false
  }
}

async function handleDelete(doc) {
  await ElMessageBox.confirm(`确定删除文档「${doc.name}」吗？`, '提示', { type: 'warning' })
  await deleteDocument(doc.id)
  ElMessage.success('已删除')
  await load()
}

async function handleSearch() {
  if (!qaQuery.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  searching.value = true
  const query = qaQuery.value
  qaHistory.value.push({ role: 'user', content: query })
  qaQuery.value = ''
  try {
    const result = await searchKnowledge(query)
    qaHistory.value.push({
      role: 'assistant',
      content: result.answer,
      sources: result.sources || null
    })
  } catch (e) {
    qaHistory.value.push({
      role: 'assistant',
      content: '抱歉，查询失败，请稍后重试。'
    })
  } finally {
    searching.value = false
  }
}

// 工具函数
const FILE_ICONS = { text: '📄', pdf: '📕', docx: '📘' }
const fileIcon = (type) => FILE_ICONS[type] || '📄'
const fileTypeClass = (type) => 'type-' + (type || 'text')

const STATUS_MAP = {
  pending: '处理中',
  indexing: '索引中',
  completed: '已完成',
  failed: '失败',
  local_only: '仅本地'
}
const statusText = (s) => STATUS_MAP[s] || s

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.knowledge-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.docs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 28px;
  min-height: 100px;
}

.doc-card {
  position: relative;
  padding: 20px;
  transition: all 0.22s ease;
  overflow: hidden;
}
.doc-card::after {
  content: "";
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: var(--grad-aurora);
  opacity: 0;
  transition: opacity 0.22s ease;
}
.doc-card:hover {
  transform: translateY(-3px);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md), var(--glow-soft);
}
.doc-card:hover::after { opacity: 1; }

.doc-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14px;
}

.doc-icon {
  width: 44px; height: 44px;
  border-radius: 12px;
  background: var(--grad-brand);
  color: #fff;
  font-size: 20px;
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--glow-brand);
}
.doc-icon.type-pdf { background: linear-gradient(135deg, #EF4444, #F97316); }
.doc-icon.type-docx { background: linear-gradient(135deg, #3B82F6, #06B6D4); }

.status-tag {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 11px;
  padding: 3px 9px;
  border-radius: 999px;
  border: 1px solid var(--border);
  color: var(--text-tertiary);
}
.status-dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.status-pending { color: var(--text-tertiary); border-color: var(--border); }
.status-indexing { color: #60A5FA; border-color: rgba(96, 165, 250, 0.3); }
.status-indexing .status-dot { animation: pulse 1.5s ease-in-out infinite; }
.status-completed { color: #34D399; border-color: rgba(52, 211, 153, 0.3); }
.status-failed { color: #F87171; border-color: rgba(248, 113, 113, 0.3); }
.status-local_only { color: #FB923C; border-color: rgba(251, 146, 60, 0.3); }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.doc-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.doc-actions {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid var(--border-light);
  padding-top: 10px;
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-secondary);
}
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-text { font-size: 14px; }

/* 知识问答区域 */
.qa-section {
  margin-top: 8px;
}
.qa-header {
  margin-bottom: 18px;
}
.qa-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}
.qa-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
}

.qa-messages {
  max-height: 400px;
  overflow-y: auto;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.qa-item {
  display: flex;
}
.qa-item.user {
  justify-content: flex-end;
}
.qa-item.assistant {
  justify-content: flex-start;
}

.qa-bubble {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  font-size: 14px;
  line-height: 1.6;
}
.qa-item.user .qa-bubble {
  background: var(--grad-brand);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.qa-item.assistant .qa-bubble {
  background: var(--surface-hover);
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  border-bottom-left-radius: 4px;
}

.qa-sources {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border-light);
  font-size: 12px;
  color: var(--text-secondary);
}
.source-label {
  color: var(--accent);
}

.qa-input {
  display: flex;
  gap: 10px;
}
.qa-input .el-input {
  flex: 1;
}

/* 上传弹窗 */
.upload-dragger {
  padding: 30px 20px;
  text-align: center;
}
.upload-icon {
  font-size: 40px;
  margin-bottom: 10px;
}
.upload-text {
  font-size: 14px;
  color: var(--text-primary);
}
.upload-text em {
  color: var(--accent);
  font-style: normal;
}
.upload-hint {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 6px;
}
</style>

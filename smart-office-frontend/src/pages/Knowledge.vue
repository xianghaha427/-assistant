<template>
  <div>
    <div class="knowledge-hero">
      <div>
        <h2 class="page-title">知识库</h2>
        <p class="page-subtitle">企业制度、流程文档智能问答与文档管理</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="kb-tabs">
      <!-- ===== Tab 1: 知识问答 ===== -->
      <el-tab-pane label="知识问答" name="qa">
        <div class="qa-section card">
          <div class="qa-header">
            <h3 class="qa-title"><Library :size="20" style="margin-right:6px;vertical-align:middle" />知识问答</h3>
            <div class="qa-hint">基于企业知识库内容智能回答，支持自研RAG引擎</div>
          </div>
          <div v-if="qaHistory.length === 0" class="qa-empty">
            <Library :size="40" color="var(--border-strong)" />
            <div class="qa-empty-text">输入问题开始提问，例如：公司的报销流程是什么？</div>
          </div>
          <div class="qa-messages" v-else>
            <div v-for="(item, idx) in qaHistory" :key="idx" class="qa-item" :class="item.role">
              <div class="qa-bubble">
                <div class="qa-content">{{ item.content }}</div>
                <div v-if="item.source === 'dify'" class="qa-source-tag qa-source-dify">Dify 知识问答助手</div>
                <div v-else-if="item.source === 'mock'" class="qa-source-tag">演示模式</div>
              </div>
            </div>
          </div>
          <div class="qa-input">
            <el-input v-model="qaQuery" placeholder="输入您的问题..." @keyup.enter="handleSearch" :disabled="searching" />
            <el-button type="primary" @click="handleSearch" :loading="searching">发送</el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 2: 文档管理 ===== -->
      <el-tab-pane label="文档管理" name="docs">
        <div class="docs-panel card">
          <div class="docs-toolbar">
            <div class="docs-toolbar-left">
              <h3 class="qa-title"><FileText :size="20" style="margin-right:6px;vertical-align:middle" />知识文档</h3>
              <span class="docs-count" v-if="documents.length">{{ documents.length }} 篇文档</span>
            </div>
            <div class="docs-toolbar-right">
              <el-select v-model="uploadCategory" placeholder="分类" size="small" style="width:120px" clearable>
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
              <el-upload :show-file-list="false" :before-upload="handleUpload" accept=".pdf,.md,.txt,.docx">
                <el-button type="primary" size="small"><Upload :size="14" style="margin-right:4px" />上传文档</el-button>
              </el-upload>
            </div>
          </div>

          <el-table :data="documents" v-loading="docsLoading" empty-text="暂无文档，请上传" style="width:100%">
            <el-table-column prop="name" label="文档名称" min-width="200">
              <template #default="{row}"><span class="doc-name">{{ row.name }}</span></template>
            </el-table-column>
            <el-table-column prop="fileType" label="格式" width="80">
              <template #default="{row}"><el-tag size="small" effect="plain">{{ row.fileType }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="category" label="分类" width="100">
              <template #default="{row}"><el-tag size="small" effect="plain" type="info">{{ row.category || '未分类' }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="wordCount" label="字数" width="80" />
            <el-table-column prop="createTime" label="上传时间" width="160" />
            <el-table-column label="操作" width="80">
              <template #default="{row}">
                <el-button size="small" type="danger" text @click="handleDelete(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchKnowledge, getDocuments, uploadDocument, deleteDocument } from '../api'
import { Library, FileText, Upload } from 'lucide-vue-next'

const activeTab = ref('qa')
const qaQuery = ref('')
const qaHistory = ref([])
const searching = ref(false)

const documents = ref([])
const docsLoading = ref(false)
const uploadCategory = ref('')
const categories = ['规章制度', '流程指引', 'FAQ', '项目文档', '培训资料', '未分类']

onMounted(() => { loadDocuments() })

async function handleSearch() {
  if (!qaQuery.value.trim()) { ElMessage.warning('请输入问题'); return }
  searching.value = true
  const query = qaQuery.value
  qaHistory.value.push({ role: 'user', content: query })
  qaQuery.value = ''
  try {
    const result = await searchKnowledge(query)
    qaHistory.value.push({ role: 'assistant', content: result.answer, source: result.source || 'dify' })
  } catch (e) {
    qaHistory.value.push({ role: 'assistant', content: '查询失败，请重试', source: 'error' })
  } finally { searching.value = false }
}

async function loadDocuments() {
  docsLoading.value = true
  try { documents.value = await getDocuments() || [] } catch (e) { /* ignore */ }
  finally { docsLoading.value = false }
}

async function handleUpload(file) {
  try {
    await uploadDocument(file, uploadCategory.value || '未分类')
    ElMessage.success('文档上传成功，RAG 索引已更新')
    loadDocuments()
  } catch (e) { ElMessage.error('上传失败') }
  return false // 阻止 el-upload 默认上传
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除该文档？', '提示', { type: 'warning' })
    await deleteDocument(id)
    ElMessage.success('已删除，RAG 索引已更新')
    loadDocuments()
  } catch (e) { /* 取消 */ }
}
</script>

<style scoped>
.knowledge-hero { margin-bottom: 24px; }
.kb-tabs { margin-top: -8px; }
.qa-section { min-height: 460px; display: flex; flex-direction: column; }
.qa-header { margin-bottom: 16px; }
.qa-title { font-size: 17px; font-weight: 600; color: var(--text-primary); margin: 0 0 4px; }
.qa-hint { font-size: 13px; color: var(--text-secondary); }
.qa-empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 60px 20px; color: var(--text-secondary); gap: 12px; }
.qa-empty-text { font-size: 14px; }
.qa-messages { flex: 1; max-height: 480px; overflow-y: auto; margin-bottom: 16px; display: flex; flex-direction: column; gap: 12px; }
.qa-item { display: flex; }
.qa-item.user { justify-content: flex-end; }
.qa-item.assistant { justify-content: flex-start; }
.qa-bubble { max-width: 80%; padding: 12px 16px; border-radius: var(--radius-md); font-size: 14px; line-height: 1.6; }
.qa-item.user .qa-bubble { background: var(--accent); color: #fff; border-bottom-right-radius: 4px; }
.qa-item.assistant .qa-bubble { background: var(--bg); border: 1px solid var(--border); color: var(--text-primary); border-bottom-left-radius: 4px; }
.qa-source-tag { margin-top: 8px; font-size: 12px; color: var(--text-tertiary); }
.qa-source-native, .qa-source-dify { color: var(--accent); }
.qa-input { display: flex; gap: 10px; margin-top: auto; }
.qa-input .el-input { flex: 1; }

.docs-panel { min-height: 400px; }
.docs-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.docs-toolbar-left { display: flex; align-items: center; gap: 12px; }
.docs-toolbar-right { display: flex; gap: 10px; align-items: center; }
.docs-count { font-size: 13px; color: var(--text-tertiary); }
.doc-name { font-size: 13px; color: var(--text-primary); font-weight: 500; }
</style>

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
            <div class="qa-header-main">
              <h3 class="qa-title"><Library :size="20" style="margin-right:6px;vertical-align:middle" />知识问答</h3>
              <div class="qa-hint">{{ qaModeHint }}</div>
            </div>
            <div class="qa-header-right">
              <el-radio-group v-model="qaMode" size="small" :disabled="searching">
                <el-radio-button value="dify">Dify 知识库</el-radio-button>
                <el-radio-button value="local">本地文档</el-radio-button>
              </el-radio-group>
              <el-tag v-if="qaMode === 'local' && !localModeAvailable" size="small" type="warning" effect="plain">不可用</el-tag>
            </div>
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
                <div v-else-if="(item.source || '').startsWith('local')" class="qa-source-tag qa-source-local">
                  {{ item.source === 'local' ? '本地知识文档' : '本地模式（未检索）' }}
                </div>
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
            <el-table-column label="操作" width="240">
              <template #default="{row}">
                <el-button size="small" text @click="openDocument(row)"><FileText :size="13" style="vertical-align:middle;margin-right:3px" />查看</el-button>
                <el-button v-if="row.canEdit" size="small" text @click="openEditor(row)">编辑</el-button>
                <el-button v-if="row.canManageAcl" size="small" text @click="openPermissions(row)"><Lock :size="13" style="vertical-align:middle;margin-right:3px" />授权</el-button>
                <el-button v-if="row.canDelete" size="small" type="danger" text @click="handleDelete(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="permissionDialogVisible" title="文档细粒度授权" width="680px">
      <div class="permission-toolbar">
        <span class="permission-doc">{{ permissionDocument?.name }}</span>
        <el-button size="small" @click="addGrant">新增授权</el-button>
      </div>
      <div v-if="grantRows.length === 0" class="permission-empty">暂无授权，只有文档创建者和管理员可以访问</div>
      <div v-for="(grant, index) in grantRows" :key="index" class="grant-row">
        <el-select v-model="grant.subjectType" style="width:120px" @change="setGrantSubjectType(grant)">
          <el-option label="全体用户" value="ALL" />
          <el-option label="用户" value="USER" />
          <el-option label="角色" value="ROLE" />
          <el-option label="部门" value="DEPT" />
        </el-select>
        <el-select v-if="grant.subjectType !== 'ALL'" v-model="grant.subjectId" filterable placeholder="选择对象" style="flex:1">
          <el-option v-for="item in grantSubjects(grant.subjectType)" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <div v-else class="grant-all-label">所有用户</div>
        <el-select v-model="grant.permission" style="width:120px">
          <el-option label="可阅读" value="READ" />
          <el-option label="可编辑" value="EDIT" />
          <el-option label="可删除" value="DELETE" />
        </el-select>
        <el-button type="danger" text @click="grantRows.splice(index, 1)">移除</el-button>
      </div>
      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permissionSaving" @click="savePermissions">保存授权</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="documentDialogVisible" :title="editingDocument ? '编辑知识文档' : '查看知识文档'" width="760px">
      <el-form :model="documentForm" label-position="top">
        <el-form-item label="文档名称">
          <el-input v-model="documentForm.name" :disabled="!editingDocument" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="documentForm.category" :disabled="!editingDocument" style="width:100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="文档内容">
          <el-input v-model="documentForm.content" type="textarea" :rows="16" :disabled="!editingDocument" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="documentDialogVisible = false">关闭</el-button>
        <el-button v-if="editingDocument" type="primary" :loading="documentSaving" @click="saveDocument">保存修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchKnowledge, searchLocalKnowledge, getLocalKnowledgeMode, getDocuments, uploadDocument, deleteDocument, getDocument, updateDocument, getDocumentPermissions, replaceDocumentPermissions, getKnowledgePermissionOptions } from '../api'
import { Library, FileText, Upload, Lock } from 'lucide-vue-next'

const activeTab = ref('qa')
const qaQuery = ref('')
const qaHistory = ref([])
const searching = ref(false)
// 问答模式：dify（默认，行为与改动前完全一致）/ local（只检索本地有权限的文档）
const qaMode = ref('dify')
const localModeEnabled = ref(false)

const documents = ref([])
const docsLoading = ref(false)
const uploadCategory = ref('')
const categories = ['规章制度', '流程指引', 'FAQ', '项目文档', '培训资料', '未分类']
const permissionDialogVisible = ref(false)
const permissionSaving = ref(false)
const permissionDocument = ref(null)
const grantRows = ref([])
const grantOptions = ref({ users: [], roles: [], depts: [] })
const documentDialogVisible = ref(false)
const documentSaving = ref(false)
const editingDocument = ref(false)
const documentForm = ref({ id: null, name: '', category: '', content: '' })

// 「本地文档」模式可用性：既要有模型配置，也要真有可检索的文档（documents 已是按权限过滤后的列表）
const localModeAvailable = computed(() => localModeEnabled.value && documents.value.length > 0)
const qaModeHint = computed(() => {
  if (qaMode.value !== 'local') return '基于 Dify 企业知识库内容智能回答'
  // 不可用时把原因和该配哪个变量直接写出来，不要只把按钮置灰——那样看不出问题在哪
  if (!localModeEnabled.value) {
    return '本地文档检索未启用：后端未拿到模型密钥（请在 .env 填 FINPILOT_KB_MODEL_API_KEY 或 DEEPSEEK_API_KEY，然后整体重建）'
  }
  if (!documents.value.length) {
    return '暂无可检索的文档：你当前没有可访问的知识文档，请先到「文档管理」上传，或让管理员给你授权'
  }
  return `基于你有权限访问的 ${documents.value.length} 篇本地文档回答，并标注来源`
})

onMounted(() => { loadDocuments(); loadLocalMode() })

async function handleSearch() {
  if (!qaQuery.value.trim()) { ElMessage.warning('请输入问题'); return }
  const useLocal = qaMode.value === 'local'
  if (useLocal && !localModeAvailable.value) {
    ElMessage.warning('本地文档模式当前不可用，请先上传文档或改用 Dify 知识库')
    return
  }
  searching.value = true
  const query = qaQuery.value
  qaHistory.value.push({ role: 'user', content: query })
  qaQuery.value = ''
  try {
    const result = useLocal ? await searchLocalKnowledge(query) : await searchKnowledge(query)
    qaHistory.value.push({ role: 'assistant', content: result.answer, source: result.source || (useLocal ? 'local' : 'dify') })
  } catch (e) {
    qaHistory.value.push({ role: 'assistant', content: '查询失败，请重试', source: 'error' })
  } finally { searching.value = false }
}

async function loadLocalMode() {
  try {
    const status = await getLocalKnowledgeMode()
    localModeEnabled.value = !!status?.enabled
  } catch (e) { localModeEnabled.value = false }
}

async function loadDocuments() {
  docsLoading.value = true
  try { documents.value = await getDocuments() || [] } catch (e) { /* ignore */ }
  finally { docsLoading.value = false }
}

async function handleUpload(file) {
  try {
    await uploadDocument(file, uploadCategory.value || '未分类')
    ElMessage.success('文档上传成功')
    loadDocuments()
  } catch (e) { ElMessage.error('上传失败') }
  return false // 阻止 el-upload 默认上传
}

function grantSubjects(type) {
  if (type === 'ROLE') return grantOptions.value.roles
  if (type === 'DEPT') return grantOptions.value.depts
  return grantOptions.value.users
}

function setGrantSubjectType(grant) {
  grant.subjectId = grant.subjectType === 'ALL' ? 0 : null
}

async function openPermissions(row) {
  permissionDocument.value = row
  try {
    const [options, grants] = await Promise.all([
      getKnowledgePermissionOptions(row.id),
      getDocumentPermissions(row.id)
    ])
    grantOptions.value = options || { users: [], roles: [], depts: [] }
    const byKey = new Map()
    for (const grant of (grants || [])) {
      const key = `${grant.subjectType}:${grant.subjectId}`
      const current = byKey.get(key)
      const rank = { READ: 1, EDIT: 2, DELETE: 3 }
      if (!current || rank[grant.permission] > rank[current.permission]) {
        byKey.set(key, { subjectType: grant.subjectType, subjectId: grant.subjectId, permission: grant.permission })
      }
    }
    grantRows.value = [...byKey.values()]
    permissionDialogVisible.value = true
  } catch (e) {
    ElMessage.error('没有文档授权管理权限，或授权信息加载失败')
  }
}

async function openDocument(row) {
  try {
    const doc = await getDocument(row.id)
    documentForm.value = { ...documentForm.value, ...doc }
    editingDocument.value = false
    documentDialogVisible.value = true
  } catch (e) { ElMessage.error('文档读取失败或没有阅读权限') }
}

async function openEditor(row) {
  try {
    const doc = await getDocument(row.id)
    documentForm.value = { ...documentForm.value, ...doc }
    editingDocument.value = true
    documentDialogVisible.value = true
  } catch (e) { ElMessage.error('文档读取失败或没有编辑权限') }
}

async function saveDocument() {
  documentSaving.value = true
  try {
    const doc = await updateDocument(documentForm.value.id, documentForm.value)
    documentForm.value = { ...documentForm.value, ...doc }
    ElMessage.success('文档已保存')
    documentDialogVisible.value = false
    await loadDocuments()
  } finally { documentSaving.value = false }
}

function addGrant() {
  grantRows.value.push({ subjectType: 'USER', subjectId: null, permission: 'READ' })
}

async function savePermissions() {
  if (grantRows.value.some(g => !g.subjectType || !g.permission || (g.subjectType !== 'ALL' && g.subjectId == null))) {
    ElMessage.warning('请完整选择授权对象和权限')
    return
  }
  permissionSaving.value = true
  try {
    await replaceDocumentPermissions(permissionDocument.value.id, grantRows.value)
    ElMessage.success('文档授权已保存')
    permissionDialogVisible.value = false
  } finally { permissionSaving.value = false }
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除该文档？', '提示', { type: 'warning' })
    await deleteDocument(id)
    ElMessage.success('已删除')
    loadDocuments()
  } catch (e) { /* 取消 */ }
}
</script>

<style scoped>
.knowledge-hero { margin-bottom: 24px; }
.kb-tabs { margin-top: -8px; }
.qa-section { min-height: 460px; display: flex; flex-direction: column; }
.qa-header { margin-bottom: 16px; display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.qa-header-main { min-width: 0; }
.qa-header-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
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
.qa-source-dify { color: var(--accent); }
.qa-source-local { color: #1D6FA8; }
.qa-input { display: flex; gap: 10px; margin-top: auto; }
.qa-input .el-input { flex: 1; }

.docs-panel { min-height: 400px; }
.docs-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.docs-toolbar-left { display: flex; align-items: center; gap: 12px; }
.docs-toolbar-right { display: flex; gap: 10px; align-items: center; }
.docs-count { font-size: 13px; color: var(--text-tertiary); }
.doc-name { font-size: 13px; color: var(--text-primary); font-weight: 500; }
.permission-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.permission-doc { color: var(--text-primary); font-size: 14px; font-weight: 600; }
.permission-empty { padding: 28px 0; text-align: center; color: var(--text-tertiary); font-size: 13px; }
.grant-row { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.grant-all-label { flex: 1; color: var(--text-secondary); font-size: 13px; padding-left: 12px; }
</style>

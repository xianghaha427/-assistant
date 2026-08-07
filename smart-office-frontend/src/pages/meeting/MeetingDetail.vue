<template>
  <div class="meeting-detail" v-loading="loading">
    <!-- ====== 顶部信息 ====== -->
    <div class="detail-header card">
      <div class="header-left">
        <button class="back-btn" @click="$router.push('/meeting')">← 返回列表</button>
        <h1 class="detail-title">{{ meeting.title }}</h1>
        <div class="detail-meta">
          <span class="meta-time">{{ formatTime(meeting.created_at) }}</span>
          <span class="status-tag" :class="meeting.status">
            <span class="status-dot"></span>
            {{ statusText(meeting.status) }}
          </span>
          <span class="meta-source">{{ sourceText(meeting.source_type) }}</span>
        </div>
      </div>
      <div class="header-actions">
        <el-button
          v-if="meeting.status === 'failed'"
          type="primary"
          size="small"
          :loading="regenerating"
          @click="handleRegenerate"
        >
          重新生成
        </el-button>
      </div>
    </div>

    <!-- ====== 主体两栏 ====== -->
    <div class="detail-body" v-if="summary">
      <!-- 左侧：纪要概览 -->
      <div class="detail-main">
        <div class="card summary-card">
          <div class="section-title">
            <span class="title-icon">📋</span>
            纪要概览
            <span v-if="summary.mock" class="mock-badge">演示模式</span>
          </div>

          <!-- 参会人 -->
          <div class="summary-section" v-if="summary.participants && summary.participants.length">
            <div class="section-label">参会人</div>
            <div class="participant-tags">
              <span v-for="p in summary.participants" :key="p" class="participant-tag">{{ p }}</span>
            </div>
          </div>

          <!-- 议题摘要 -->
          <div class="summary-section" v-if="summary.topics && summary.topics.length">
            <div class="section-label">议题摘要</div>
            <ol class="topic-list">
              <li v-for="(t, i) in summary.topics" :key="i">{{ t }}</li>
            </ol>
          </div>

          <!-- 决议事项 -->
          <div class="summary-section" v-if="summary.decisions && summary.decisions.length">
            <div class="section-label">决议事项</div>
            <ul class="decision-list">
              <li v-for="(d, i) in summary.decisions" :key="i">
                <span class="decision-icon">✓</span>
                {{ d }}
              </li>
            </ul>
          </div>

          <!-- 空状态 -->
          <div v-if="!summary.participants?.length && !summary.topics?.length && !summary.decisions?.length" class="empty-hint">
            暂无纪要内容
          </div>
        </div>

        <!-- 原始文本折叠 -->
        <div class="card raw-card">
          <el-collapse>
            <el-collapse-item title="原始转写文本" name="raw">
              <div class="raw-text">{{ meeting.raw_text || '暂无原始文本' }}</div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>

      <!-- 右侧：待办事项 -->
      <div class="detail-side">
        <div class="card actions-card">
          <div class="section-title">
            <span class="title-icon">✅</span>
            待办事项
            <span class="action-count">{{ actions.length }}</span>
          </div>

          <div v-if="actions.length === 0" class="empty-hint">暂无待办事项</div>

          <div class="action-list">
            <div
              v-for="a in actions"
              :key="a.id"
              class="action-item"
              :class="{ done: a.status === 'completed' }"
            >
              <span class="action-status" :class="a.status">{{ ACTION_STATUS_TEXT[a.status] || '未开始' }}</span>
              <div class="action-content">
                <div class="action-text">{{ a.content }}</div>
                <div class="action-meta">
                  <span v-if="a.assignee" class="assignee-tag">{{ a.assignee }}</span>
                  <span v-if="a.deadline" class="deadline-tag">{{ formatDate(a.deadline) }}</span>
                  <span v-if="a.task_id" class="linked-task">🔗 已关联任务</span>
                  <button v-else class="convert-btn" @click="handleConvert(a)">转为任务</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 纪要未生成 / 生成中 / 生成失败 -->
    <div class="detail-body" v-else-if="!loading">
      <div class="no-summary card" :class="{ 'is-failed': meeting.status === 'failed' }">
        <div class="no-summary-icon">{{ meeting.status === 'failed' ? '⚠' : '✨' }}</div>
        <div class="no-summary-title">
          {{ meeting.status === 'processing' ? 'AI 生成中…' : meeting.status === 'failed' ? '纪要生成失败' : '纪要尚未生成' }}
        </div>
        <div class="no-summary-desc">
          {{ meeting.status === 'processing' ? '正在调用 AI 生成会议纪要，页面将自动刷新'
             : meeting.status === 'failed' ? (meeting.fail_reason || '文档解析失败，请重试')
             : '点击下方按钮生成会议纪要' }}
        </div>
        <el-button v-if="meeting.status === 'processing'" type="primary" loading @click="loadData">刷新</el-button>
        <el-button v-else-if="meeting.status === 'failed'" type="danger" :loading="regenerating" @click="handleRegenerate">重新生成</el-button>
        <el-button v-else type="primary" :loading="regenerating" @click="handleRegenerate">生成纪要</el-button>
      </div>
    </div>

    <!-- ====== 底部操作栏 ====== -->
    <div class="detail-footer card" v-if="summary">
      <el-button @click="$router.push('/meeting')">返回</el-button>
      <el-button type="primary" @click="handleExport">
        导出纪要
      </el-button>
    </div>
    <!-- ====== 转任务弹窗：选择执行人 ====== -->
    <el-dialog v-model="convertVisible" title="转为任务" width="460px">
      <div class="convert-tip">{{ convertingAction?.content }}</div>
      <el-form label-width="72px">
        <el-form-item label="执行人" required>
          <el-select
            v-model="convertAssigneeId"
            placeholder="请选择任务执行人"
            filterable
            style="width: 100%"
            :loading="userOptionsLoading"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="optionLabel(u)"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!convertAssigneeId"
          :loading="converting"
          @click="confirmConvert"
        >
          转为任务
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMeeting, getSummary, getActions, generateSummary } from '../../api/meeting.js'
import { convertFromAction } from '../../api/task.js'
import { getUserOptions } from '../../api/index.js'

const route = useRoute()
const router = useRouter()

const meetingId = route.params.id

const meeting = ref({})
const summary = ref(null)
const actions = ref([])
const loading = ref(true)
const regenerating = ref(false)

let pollTimer = null

onMounted(() => loadData())
onUnmounted(() => stopPoll())

async function loadData() {
  loading.value = true
  try {
    const [m, s, a] = await Promise.allSettled([
      getMeeting(meetingId),
      getSummary(meetingId),
      getActions(meetingId)
    ])
    if (m.status === 'fulfilled') meeting.value = m.value
    if (s.status === 'fulfilled') {
      // 纪要未生成时后端返回占位体（status=processing/empty），前端据此展示对应占位态而非空纪要
      const sv = s.value
      summary.value = sv && (sv.status === 'processing' || sv.status === 'empty') ? null : sv
    }
    if (a.status === 'fulfilled') actions.value = a.value
  } finally {
    loading.value = false
  }
  // 纪要生成中：每 3 秒轮询直到 completed/failed
  if (meeting.value.status === 'processing') startPoll()
}

/** 轮询会议状态（AI 生成纪要中） */
function startPoll() {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    try {
      const m = await getMeeting(meetingId)
      meeting.value = m
      if (m.status !== 'processing') {
        stopPoll()
        await loadData()
        if (m.status === 'completed') ElMessage.success('会议纪要生成完成')
        else if (m.status === 'failed') ElMessage.warning('纪要生成失败，可重试')
      }
    } catch (e) { /* 轮询瞬时失败忽略 */ }
  }, 3000)
}

function stopPoll() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

async function handleRegenerate() {
  regenerating.value = true
  try {
    const record = await generateSummary(meetingId)
    meeting.value = { ...meeting.value, ...record }
    if (record.status === 'processing') {
      ElMessage.info('AI 正在生成纪要，请稍候…')
      startPoll()
    } else {
      await loadData()
    }
  } catch (e) {
    ElMessage.error('生成失败，请稍后重试')
  } finally {
    regenerating.value = false
  }
}

/** 待办状态只读展示（状态单源在任务看板，改状态请到任务看板操作，会反向同步回待办） */
const ACTION_STATUS_TEXT = { pending: '未开始', in_progress: '进行中', completed: '已完成' }

/* ====== 转任务弹窗：选择执行人 ====== */
const convertVisible = ref(false)
const convertingAction = ref(null)
const convertAssigneeId = ref(null)
const converting = ref(false)
const userOptions = ref([])
const userOptionsLoading = ref(false)

const ROLE_LABEL = { ADMIN: '管理员', DEPT_MANAGER: '部门负责人', EMPLOYEE: '员工', KB_ADMIN: '知识管理员' }
function optionLabel(u) {
  const role = (u.roles || []).map(r => ROLE_LABEL[r] || r).join('/')
  return role ? `${u.real_name}（${role}）` : u.real_name
}

/** 打开弹窗：预填纪要识别出的匹配执行人（assignee_id），无匹配则不预选 */
async function handleConvert(a) {
  convertingAction.value = a
  convertAssigneeId.value = a.assignee_id || null
  convertVisible.value = true
  if (!userOptions.value.length) {
    userOptionsLoading.value = true
    try {
      userOptions.value = await getUserOptions()
      // 预填的匹配人若不在可选列表中，则取消预选
      if (convertAssigneeId.value && !userOptions.value.some(u => u.id === convertAssigneeId.value)) {
        convertAssigneeId.value = null
      }
    } catch (e) {
      // 拦截器已提示错误
    } finally {
      userOptionsLoading.value = false
    }
  }
}

/** 确认转任务（必选执行人后才可提交） */
async function confirmConvert() {
  if (!convertAssigneeId.value) {
    ElMessage.warning('请先选择任务执行人')
    return
  }
  converting.value = true
  try {
    await convertFromAction(convertingAction.value.id, convertAssigneeId.value)
    ElMessage.success('已转为任务，可在任务看板查看')
    convertVisible.value = false
    actions.value = await getActions(meetingId)
  } catch (e) {
    // 拦截器已提示错误
  } finally {
    converting.value = false
  }
}

async function handleExport() {
  const exportData = {
    title: meeting.value.title,
    time: meeting.value.created_at,
    summary: summary.value,
    actions: actions.value
  }
  try {
    await navigator.clipboard.writeText(JSON.stringify(exportData, null, 2))
    ElMessage.success('纪要已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败，请手动导出')
  }
}

const STATUS_MAP = { pending: '待处理', processing: '处理中', completed: '已完成', failed: '失败' }
const SOURCE_MAP = { text: '文本输入', audio: '音频上传' }

function statusText(s) { return STATUS_MAP[s] || s }
function sourceText(s) { return SOURCE_MAP[s] || s }

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatDate(t) {
  if (!t) return ''
  const d = new Date(t)
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
</script>

<style scoped>
.meeting-detail { max-width: 1200px; margin: 0 auto; }

/* ====== 顶部 ====== */
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 24px 28px;
  margin-bottom: 20px;
}
.back-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  padding: 0;
  margin-bottom: 10px;
  transition: color 0.15s;
}
.back-btn:hover { color: var(--accent); }

.detail-title {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
}
.meta-time {
  font-size: 12.5px;
  color: var(--text-tertiary);
}
.meta-source {
  font-size: 12px;
  color: var(--text-tertiary);
  padding: 2px 8px;
  border: 1px solid var(--border);
  border-radius: 999px;
}

/* 状态标签 */
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid;
}
.status-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
}
.status-tag.pending {
  color: var(--text-tertiary);
  border-color: rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.03);
}
.status-tag.pending .status-dot { background: var(--text-tertiary); }
.status-tag.processing {
  color: var(--accent);
  border-color: rgba(8, 180, 188, 0.3);
  background: rgba(8, 180, 188, 0.08);
}
.status-tag.processing .status-dot {
  background: var(--accent);
  box-shadow: 0 0 6px rgba(8, 180, 188, 0.9);
  animation: breathe 1.8s ease-in-out infinite;
}
@keyframes breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
.status-tag.completed {
  color: #34D399;
  border-color: rgba(52, 211, 153, 0.3);
  background: rgba(52, 211, 153, 0.08);
}
.status-tag.completed .status-dot { background: #34D399; box-shadow: 0 0 6px rgba(52, 211, 153, 0.9); }
.status-tag.failed {
  color: #f56c6c;
  border-color: rgba(245, 108, 108, 0.3);
  background: rgba(245, 108, 108, 0.08);
}
.status-tag.failed .status-dot { background: #f56c6c; box-shadow: 0 0 6px rgba(245, 108, 108, 0.9); }

/* ====== 两栏布局 ====== */
.detail-body {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 18px;
  margin-bottom: 18px;
}

/* 纪要卡片 */
.summary-card { padding: 24px 28px; }
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
}
.title-icon { font-size: 17px; }

.summary-section { margin-bottom: 22px; }
.summary-section:last-child { margin-bottom: 0; }
.section-label {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 10px;
}

.participant-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.participant-tag {
  font-size: 12.5px;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid rgba(8, 180, 188, 0.2);
}

.topic-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13.5px;
  color: var(--text-primary);
  line-height: 1.9;
}
.topic-list li { margin-bottom: 4px; }

.decision-list {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 13.5px;
  color: var(--text-primary);
  line-height: 1.8;
}
.decision-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}
.decision-icon {
  color: #34D399;
  font-size: 13px;
  flex-shrink: 0;
  margin-top: 3px;
}

/* 原始文本 */
.raw-card { padding: 0; }
.raw-card :deep(.el-collapse) {
  border: none;
}
.raw-card :deep(.el-collapse-item__header) {
  background: transparent;
  border-bottom: 1px solid var(--border-light);
  color: var(--text-secondary);
  font-size: 13px;
  padding: 0 20px;
  height: 46px;
}
.raw-card :deep(.el-collapse-item__wrap) {
  background: transparent;
  border-bottom: none;
}
.raw-card :deep(.el-collapse-item__content) {
  padding: 16px 20px;
}
.raw-text {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
}

/* ====== 右侧待办 ====== */
.actions-card { padding: 22px 20px; }
.action-count {
  font-size: 11.5px;
  color: var(--text-tertiary);
  background: rgba(255, 255, 255, 0.05);
  padding: 2px 8px;
  border-radius: 999px;
  margin-left: auto;
}

.action-list { display: flex; flex-direction: column; gap: 4px; }
.action-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 10px;
  border-radius: var(--radius-sm);
  transition: background 0.15s;
}
.action-item:hover { background: var(--surface-hover); }
.action-item.done .action-text {
  text-decoration: line-through;
  color: var(--text-tertiary);
}

/* 待办状态标签（只读，状态单源在任务看板） */
.action-status {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--border-light);
  color: var(--text-tertiary);
  background: rgba(255, 255, 255, 0.04);
  white-space: nowrap;
}
.action-status.in_progress {
  color: var(--accent);
  border-color: rgba(8, 180, 188, 0.3);
  background: rgba(8, 180, 188, 0.1);
}
.action-status.completed {
  color: #34D399;
  border-color: rgba(52, 211, 153, 0.3);
  background: rgba(52, 211, 153, 0.1);
}

.action-content { flex: 1; min-width: 0; }
.action-text {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.5;
  word-break: break-word;
}
.action-meta {
  display: flex;
  gap: 8px;
  margin-top: 5px;
  flex-wrap: wrap;
}
.assignee-tag {
  font-size: 11px;
  color: var(--accent);
  background: rgba(8, 180, 188, 0.12);
  padding: 2px 7px;
  border-radius: 999px;
  border: 1px solid rgba(8, 180, 188, 0.25);
}
.deadline-tag {
  font-size: 11px;
  color: var(--text-tertiary);
  background: rgba(255, 255, 255, 0.04);
  padding: 2px 7px;
  border-radius: 999px;
  border: 1px solid var(--border-light);
}

.convert-btn {
  font-size: 11px;
  color: var(--accent);
  background: var(--accent-soft);
  border: 1px solid rgba(8, 180, 188, 0.25);
  border-radius: 999px;
  padding: 2px 8px;
  cursor: pointer;
  transition: filter 0.15s;
}
.convert-btn:hover { filter: brightness(1.1); }
.linked-task {
  font-size: 11px;
  color: #10B981;
  padding: 2px 4px;
}

.empty-hint {
  padding: 28px 0;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

/* ====== 未生成状态 ====== */
.no-summary {
  text-align: center;
  padding: 60px 20px;
}
.no-summary-icon {
  font-size: 36px;
  color: var(--accent);
  margin-bottom: 14px;
  animation: pulse 2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
.no-summary-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.no-summary-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 18px;
  word-break: break-word;
}

/* 纪要生成失败态（红色提示） */
.no-summary.is-failed .no-summary-icon { color: #f56c6c; animation: none; }
.no-summary.is-failed .no-summary-title { color: #f56c6c; }
.no-summary.is-failed .no-summary-desc { color: rgba(245, 108, 108, 0.85); }

/* 演示模式标记（Mock 兜底纪要明示） */
.mock-badge {
  margin-left: auto;
  font-size: 11px;
  font-weight: 500;
  color: #E6A23C;
  background: rgba(230, 162, 60, 0.12);
  border: 1px solid rgba(230, 162, 60, 0.3);
  padding: 2px 8px;
  border-radius: 999px;
}

/* ====== 底部操作栏 ====== */
.detail-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 20px;
}

/* 转任务弹窗 */
.convert-tip {
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--surface-hover);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  margin-bottom: 16px;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .detail-body { grid-template-columns: 1fr; }
}
</style>

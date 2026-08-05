<template>
  <div class="meeting-detail" v-loading="loading">
    <!-- ====== 椤堕儴淇℃伅 ====== -->
    <div class="detail-header card">
      <div class="header-left">
        <button class="back-btn" @click="$router.push('/meeting')">鈫?杩斿洖鍒楄〃</button>
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
          閲嶆柊鐢熸垚
        </el-button>
      </div>
    </div>

    <!-- ====== 涓讳綋涓ゆ爮 ====== -->
    <div class="detail-body" v-if="summary">
      <!-- 宸︿晶锛氱邯瑕佹瑙?-->
      <div class="detail-main">
        <div class="card summary-card">
          <div class="section-title">
            <span class="title-icon">馃搵</span>
            绾姒傝
          </div>

          <!-- 鍙備細浜?-->
          <div class="summary-section" v-if="summary.participants && summary.participants.length">
            <div class="section-label">鍙備</div>
            <div class="participant-tags">
              <span v-for="p in summary.participants" :key="p" class="participant-tag">{{ p }}</span>
            </div>
          </div>

          <!-- 璁鎽樿 -->
          <div class="summary-section" v-if="summary.topics && summary.topics.length">
            <div class="section-label">璁鎽樿</div>
            <ol class="topic-list">
              <li v-for="(t, i) in summary.topics" :key="i">{{ t }}</li>
            </ol>
          </div>

          <!-- 鍐宠浜嬮」 -->
          <div class="summary-section" v-if="summary.decisions && summary.decisions.length">
            <div class="section-label">鍐宠浜嬮」</div>
            <ul class="decision-list">
              <li v-for="(d, i) in summary.decisions" :key="i">
                <span class="decision-icon"></span>
                {{ d }}
              </li>
            </ul>
          </div>

          <!-- 绌虹姸鎬?-->
          <div v-if="!summary.participants?.length && !summary.topics?.length && !summary.decisions?.length" class="empty-hint">
            鏆傛棤绾鍐呭
          </div>
        </div>

        <!-- 鍘熷鏂囨湰鎶樺彔 -->
        <div class="card raw-card">
          <el-collapse>
            <el-collapse-item title="鍘熷杞啓鏂囨湰" name="raw">
              <div class="raw-text">{{ meeting.raw_text || '鏆傛棤鍘熷鏂囨湰' }}</div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>

      <!-- 鍙充晶锛氬緟鍔炰簨椤?-->
      <div class="detail-side">
        <div class="card actions-card">
          <div class="section-title">
            <span class="title-icon"></span>
            寰呭姙浜嬮」
            <span class="action-count">{{ actions.length }}</span>
          </div>

          <div v-if="actions.length === 0" class="empty-hint">鏆傛棤寰呭姙浜嬮」</div>

          <div class="action-list">
            <div
              v-for="a in actions"
              :key="a.id"
              class="action-item"
              :class="{ done: a.status === 'completed' }"
            >
              <div class="action-check" @click="toggleAction(a)">
                <span class="check-box" :class="a.status">
                  <span v-if="a.status === 'completed'" class="check-icon"></span>
                  <span v-else-if="a.status === 'in_progress'" class="check-icon"></span>
                </span>
              </div>
              <div class="action-content">
                <div class="action-text">{{ a.content }}</div>
                <div class="action-meta">
                  <span v-if="a.assignee" class="assignee-tag">{{ a.assignee }}</span>
                  <span v-if="a.deadline" class="deadline-tag">{{ formatDate(a.deadline) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 绾鏈敓鎴?-->
    <div class="detail-body" v-else-if="!loading">
      <div class="no-summary card">
        <div class="no-summary-icon"></div>
        <div class="no-summary-title">绾灏氭湭鐢熸垚</div>
        <div class="no-summary-desc">AI 姝ｅ湪澶勭悊涓紝璇风◢鍚庡埛鏂伴</div>
        <el-button type="primary" @click="loadData">鍒锋柊</el-button>
      </div>
    </div>

    <!-- ====== 搴曢儴鎿嶄綔鏍?====== -->
    <div class="detail-footer card" v-if="summary">
      <el-button @click="$router.push('/meeting')">杩斿洖</el-button>
      <el-button type="primary" @click="handleExport">
        瀵煎嚭绾
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMeeting, getSummary, getActions, generateSummary, updateAction } from '../../api/meeting.js'

const route = useRoute()
const router = useRouter()

const meetingId = route.params.id

const meeting = ref({})
const summary = ref(null)
const actions = ref([])
const loading = ref(true)
const regenerating = ref(false)

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const [m, s, a] = await Promise.allSettled([
      getMeeting(meetingId),
      getSummary(meetingId),
      getActions(meetingId)
    ])
    if (m.status === 'fulfilled') meeting.value = m.value
    if (s.status === 'fulfilled') summary.value = s.value
    if (a.status === 'fulfilled') actions.value = a.value
  } finally {
    loading.value = false
  }
}

async function handleRegenerate() {
  regenerating.value = true
  try {
    await generateSummary(meetingId)
    ElMessage.success('纪要已重新生成')
    await loadData()
  } catch (e) {
    ElMessage.error('鐢熸垚澶辫触锛岃绋嶅悗閲嶈瘯')
  } finally {
    regenerating.value = false
  }
}

const STATUS_CYCLE = { pending: 'in_progress', in_progress: 'completed', completed: 'pending' }

async function toggleAction(a) {
  const next = STATUS_CYCLE[a.status] || 'pending'
  try {
    await updateAction(a.id, { status: next })
    a.status = next
    ElMessage.success(next === 'completed' ? '已完成' : next === 'in_progress' ? '进行中' : '已重置')
  } catch (e) {
    // 蹇界暐
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
    ElMessage.error('澶嶅埗澶辫触锛岃鎵嬪姩瀵煎嚭')
  }
}

const STATUS_MAP = { pending: '待处理', processing: '处理中', completed: '已完成', failed: '失败' }
const SOURCE_MAP = { text: '鏂囨湰杈撳叆', audio: '闊抽涓婁紶' }

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

/* ====== 椤堕儴 ====== */
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

/* 鐘舵€佹爣绛?*/
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
  border-color: rgba(77, 141, 255, 0.3);
  background: rgba(77, 141, 255, 0.08);
}
.status-tag.processing .status-dot {
  background: var(--accent);
  box-shadow: 0 0 6px rgba(77, 141, 255, 0.9);
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

/* ====== 涓ゆ爮甯冨眬 ====== */
.detail-body {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 18px;
  margin-bottom: 18px;
}

/* 绾鍗＄墖 */
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
  border: 1px solid rgba(77, 141, 255, 0.2);
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

/* 鍘熷鏂囨湰 */
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

/* ====== 鍙充晶寰呭姙 ====== */
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

.action-check { cursor: pointer; flex-shrink: 0; margin-top: 2px; }
.check-box {
  width: 20px; height: 20px;
  border-radius: 6px;
  border: 1.5px solid var(--border-strong);
  display: flex; align-items: center; justify-content: center;
  transition: all 0.15s ease;
  font-size: 11px;
}
.check-box.pending { border-color: var(--border-strong); }
.check-box.in_progress {
  border-color: var(--accent);
  background: rgba(77, 141, 255, 0.15);
  color: var(--accent);
}
.check-box.completed {
  border-color: #34D399;
  background: rgba(52, 211, 153, 0.2);
  color: #34D399;
}
.check-icon { font-size: 11px; line-height: 1; }

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
  color: var(--accent-2);
  background: rgba(139, 92, 246, 0.12);
  padding: 2px 7px;
  border-radius: 999px;
  border: 1px solid rgba(139, 92, 246, 0.2);
}
.deadline-tag {
  font-size: 11px;
  color: var(--text-tertiary);
  background: rgba(255, 255, 255, 0.04);
  padding: 2px 7px;
  border-radius: 999px;
  border: 1px solid var(--border-light);
}

.empty-hint {
  padding: 28px 0;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

/* ====== 鏈敓鎴愮姸鎬?====== */
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
}

/* ====== 搴曢儴鎿嶄綔鏍?====== */
.detail-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 20px;
}

@media (max-width: 900px) {
  .detail-body { grid-template-columns: 1fr; }
}
</style>

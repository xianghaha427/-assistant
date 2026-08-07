<template>
  <div class="dashboard">
    <!-- ====== 问候区 ====== -->
    <div class="hero card">
      <div class="hero-main">
        <h1 class="hero-title">
          {{ greeting }}，<span class="grad-text">{{ userName }}</span>
        </h1>
        <p class="hero-sub">{{ todayText }} · {{ deptText }}</p>
      </div>
      <div class="hero-date">
        <div class="date-day">{{ dateDay }}</div>
        <div class="date-sub">{{ dateWeek }} · {{ dateMonth }}</div>
      </div>
    </div>

    <!-- ====== 指标卡 ====== -->
    <div class="stat-grid">
      <div v-for="s in statCards" :key="s.label" class="stat-card card" @click="$router.push(s.path)">
        <div class="stat-icon">{{ s.icon }}</div>
        <div class="stat-info">
          <div class="stat-num">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
        <div class="stat-arrow">→</div>
      </div>
    </div>

    <!-- ====== 中部：今日会议 + 我的待办 ====== -->
    <div class="mid-row">
      <div class="card panel">
        <div class="panel-head">
          <span class="panel-title">今日会议</span>
          <el-link type="primary" :underline="false" @click="$router.push('/meeting')">全部会议</el-link>
        </div>

        <template v-if="loading">
          <div class="loading-hint">加载中…</div>
        </template>
        <template v-else-if="todayMeetings.length">
          <div
            v-for="m in todayMeetings"
            :key="m.id"
            class="meeting-row"
            @click="$router.push('/meeting/' + m.id)"
          >
            <div class="meeting-time">{{ fmtTime(m.meeting_time) }}</div>
            <div class="meeting-info">
              <div class="meeting-title">{{ m.title }}</div>
              <div class="meeting-status">
                <span class="status-dot" :class="m.status"></span>
                {{ statusText(m.status) }}
              </div>
            </div>
            <span class="row-go">进入 ›</span>
          </div>
        </template>
        <div v-else class="empty-hint">
          <div class="empty-icon">📅</div>
          今天暂无会议安排
          <el-button size="small" text type="primary" @click.stop="$router.push('/meeting/create')">新建会议</el-button>
        </div>
      </div>

      <div class="card panel">
        <div class="panel-head">
          <span class="panel-title">我的待办</span>
          <el-link type="primary" :underline="false" @click="$router.push('/task')">任务看板</el-link>
        </div>

        <template v-if="loading">
          <div class="loading-hint">加载中…</div>
        </template>
        <template v-else-if="myTodos.length">
          <div
            v-for="t in myTodos"
            :key="t.id"
            class="todo-row"
            @click="t.meeting_id && $router.push('/meeting/' + t.meeting_id)"
          >
            <span class="todo-check" :class="t.status"></span>
            <div class="todo-info">
              <div class="todo-content" :class="{ done: t.status === 'done' }">{{ t.content }}</div>
              <div class="todo-meta">
                <span v-if="t.meeting_title" class="todo-src">来自：{{ t.meeting_title }}</span>
                <span v-if="t.deadline" class="todo-deadline">截止 {{ t.deadline }}</span>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="empty-hint">
          <div class="empty-icon">✅</div>
          暂无待办，保持这个节奏
        </div>
      </div>
    </div>

    <!-- ====== 底部：今日简报 ====== -->
    <div class="card panel briefing">
      <div class="panel-head">
        <span class="panel-title">今日简报</span>
        <el-tag size="small" effect="plain" round>AI 生成</el-tag>
      </div>
      <template v-if="briefingText">
        <div v-if="briefingTitle" class="briefing-title">{{ briefingTitle }}</div>
        <div class="briefing-text md-body" v-html="renderedBriefing"></div>
        <el-link type="primary" :underline="false" class="briefing-link" @click="$router.push('/briefing')">
          查看完整简报 ›
        </el-link>
      </template>
      <div v-else class="briefing-empty">
        <span class="briefing-spark">✦</span>
        今日简报尚未生成，可前往「行业简报」页手动触发
        <el-link type="primary" :underline="false" @click="$router.push('/briefing')">去生成</el-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getMe, getWorkbenchHome } from '../api'
import { renderMd } from '../utils/markdown.js'

const loading = ref(true)
const user = ref({})
const todayMeetings = ref([])
const myTodos = ref([])
const todayBriefing = ref(null)
const stats = ref({})

const userName = computed(() => user.value.real_name || user.value.realName || '同事')
const deptText = computed(() => (user.value.dept_name || user.value.deptName || '祝你高效的一天'))

/**
 * 今日简报：后端 today_briefing 为 { title, content(摘要前200字), brief_date }；
 * 兼容早期字符串格式
 */
const briefingTitle = computed(() => {
  const b = todayBriefing.value
  return b && typeof b === 'object' ? (b.title || '') : ''
})
const briefingText = computed(() => {
  const b = todayBriefing.value
  if (!b) return ''
  return typeof b === 'string' ? b : (b.content || '')
})
/** 简报摘要轻量 Markdown 渲染（共用工具 src/utils/markdown.js），避免原始符号裸露 */
const renderedBriefing = computed(() => renderMd(briefingText.value))

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const now = new Date()
const pad = (n) => String(n).padStart(2, '0')
const todayText = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`
const dateDay = pad(now.getDate())
const dateMonth = `${now.getFullYear()}/${pad(now.getMonth() + 1)}`
const dateWeek = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][now.getDay()]

const statCards = computed(() => [
  { icon: '📅', label: '会议总数', value: stats.value.meeting_count ?? 0, path: '/meeting' },
  { icon: '✅', label: '已完成会议', value: stats.value.completed_meeting_count ?? 0, path: '/meeting' },
  { icon: '✦', label: 'AI 使用次数', value: stats.value.ai_usage_count ?? 0, path: '/insight' },
  { icon: '📚', label: '知识库文档', value: stats.value.kb_doc_count ?? 0, path: '/knowledge' }
])

function fmtTime(t) {
  if (!t) return '--:--'
  // 后端格式 yyyy-MM-dd HH:mm:ss，只取时分
  const m = String(t).match(/(\d{2}:\d{2})/)
  return m ? m[1] : t
}

function statusText(s) {
  return { pending: '待开始', processing: '进行中', completed: '已完成' }[s] || s || '未知'
}

onMounted(async () => {
  // 用户信息与工作台数据并行加载；任一失败不影响整页渲染
  const [userRes, homeRes] = await Promise.allSettled([getMe(), getWorkbenchHome()])
  if (userRes.status === 'fulfilled' && userRes.value) user.value = userRes.value
  if (homeRes.status === 'fulfilled' && homeRes.value) {
    const d = homeRes.value
    todayMeetings.value = Array.isArray(d.today_meetings) ? d.today_meetings : []
    myTodos.value = Array.isArray(d.my_todos) ? d.my_todos.slice(0, 5) : []
    todayBriefing.value = d.today_briefing || null
    stats.value = d.stats || {}
  }
  loading.value = false
})
</script>

<style scoped>
.dashboard {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ====== 问候区 ====== */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26px 28px;
}
.hero-title {
  margin: 0;
  font-size: 24px;
  font-weight: 680;
  letter-spacing: -0.015em;
  color: var(--text-primary);
}
.hero-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--text-secondary);
}
.hero-date { text-align: right; }
.date-day {
  font-size: 34px;
  font-weight: 720;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}
.date-sub {
  margin-top: 5px;
  font-size: 12px;
  color: var(--text-tertiary);
}

/* ====== 指标卡 ====== */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--border);
}
.stat-icon {
  width: 42px; height: 42px;
  flex-shrink: 0;
  border-radius: 11px;
  background: var(--accent-soft);
  display: flex; align-items: center; justify-content: center;
  font-size: 19px;
}
.stat-num {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}
.stat-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}
.stat-arrow {
  margin-left: auto;
  color: var(--text-tertiary);
  opacity: 0;
  transform: translateX(-4px);
  transition: all 0.18s ease;
}
.stat-card:hover .stat-arrow { opacity: 1; transform: translateX(0); color: var(--accent); }

/* ====== 中部两栏 ====== */
.mid-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.panel { padding: 18px 20px; }
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.panel-title {
  font-size: 15px;
  font-weight: 620;
  color: var(--text-primary);
}

/* ---- 今日会议 ---- */
.meeting-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 11px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.16s ease;
}
.meeting-row:hover { background: var(--surface-hover); }
.meeting-row:hover .row-go { opacity: 1; }
.meeting-time {
  flex-shrink: 0;
  width: 52px;
  font-size: 14px;
  font-weight: 620;
  color: var(--accent);
  font-variant-numeric: tabular-nums;
}
.meeting-info { min-width: 0; flex: 1; }
.meeting-title {
  font-size: 13.5px;
  font-weight: 520;
  color: var(--text-primary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.meeting-status {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 3px;
}
.status-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--text-tertiary);
}
.status-dot.pending { background: #F59E0B; }
.status-dot.processing { background: #08B4BC; }
.status-dot.completed { background: #34D399; }
.row-go {
  font-size: 12px;
  color: var(--accent);
  opacity: 0;
  transition: opacity 0.16s ease;
}

/* ---- 我的待办 ---- */
.todo-row {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 11px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.16s ease;
}
.todo-row:hover { background: var(--surface-hover); }
.todo-check {
  width: 16px; height: 16px;
  flex-shrink: 0;
  margin-top: 2px;
  border-radius: 5px;
  border: 1.5px solid var(--border-strong);
}
.todo-check.done {
  background: var(--accent);
  border-color: var(--accent);
}
.todo-info { min-width: 0; }
.todo-content {
  font-size: 13.5px;
  color: var(--text-primary);
  line-height: 1.5;
}
.todo-content.done {
  color: var(--text-tertiary);
  text-decoration: line-through;
}
.todo-meta {
  display: flex; gap: 12px;
  font-size: 11.5px;
  color: var(--text-tertiary);
  margin-top: 3px;
}

/* ---- 空态/加载 ---- */
.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 26px 0 20px;
  font-size: 13px;
  color: var(--text-tertiary);
}
.empty-icon { font-size: 26px; opacity: 0.85; }
.loading-hint {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--text-tertiary);
}

/* ====== 今日简报 ====== */
.briefing { padding: 18px 20px; }
.briefing-title {
  font-size: 14px;
  font-weight: 620;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.briefing-text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.75;
  color: var(--text-secondary);
  word-break: break-word;
  max-height: 240px;
  overflow: hidden;
}
/* 简报卡内 Markdown 排版：标题字号收敛适配卡片，表格可横向滚动 */
.briefing-text :deep(h1),
.briefing-text :deep(h2),
.briefing-text :deep(h3),
.briefing-text :deep(h4),
.briefing-text :deep(h5),
.briefing-text :deep(h6) {
  color: var(--text-primary);
  font-weight: 620;
  font-size: 14px;
  margin: 8px 0 4px;
  line-height: 1.45;
}
.briefing-text :deep(p) { margin: 4px 0; }
.briefing-text :deep(ul),
.briefing-text :deep(ol) { margin: 4px 0; padding-left: 20px; }
.briefing-text :deep(li) { margin: 2px 0; }
.briefing-text :deep(strong) { color: var(--text-primary); font-weight: 600; }
.briefing-text :deep(code) {
  background: var(--surface-hover);
  border: 1px solid var(--border-light);
  border-radius: 5px;
  padding: 1px 5px;
  font-size: 12px;
}
.briefing-text :deep(hr) {
  border: none;
  border-top: 1px solid var(--border-light);
  margin: 10px 0;
}
.briefing-text :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 12.5px;
}
.briefing-text :deep(th),
.briefing-text :deep(td) {
  border: 1px solid var(--border);
  padding: 5px 9px;
  text-align: left;
  white-space: nowrap;
}
.briefing-text :deep(th) {
  background: var(--surface-hover);
  color: var(--text-primary);
  font-weight: 600;
}
.briefing-link { margin-top: 8px; font-size: 12.5px; }
.briefing-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 2px 8px;
  font-size: 13px;
  color: var(--text-tertiary);
}
.briefing-spark { color: var(--accent); }

@media (max-width: 1100px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .mid-row { grid-template-columns: 1fr; }
}
</style>

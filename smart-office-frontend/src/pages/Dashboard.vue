<template>
  <div class="dashboard">
    <div class="hero">
      <div class="hero-main">
        <h1 class="hero-title">{{ greeting }}，<span class="hero-name">{{ userName }}</span></h1>
        <p class="hero-sub">{{ todayText }}</p>
      </div>
      <div class="hero-date">
        <div class="date-day">{{ dateDay }}</div>
        <div class="date-sub">{{ dateWeek }} · {{ dateMonth }}</div>
      </div>
    </div>

    <div class="stat-grid">
      <div v-for="s in statCards" :key="s.label" class="stat-card" @click="$router.push(s.path)">
        <div class="stat-icon"><component :is="s.icon" :size="20" /></div>
        <div class="stat-info">
          <div class="stat-num">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
        <ChevronRight :size="16" class="stat-arrow" />
      </div>
    </div>

    <div class="mid-row">
      <div class="card panel">
        <div class="panel-head">
          <span class="panel-title">今日会议</span>
          <el-link :underline="false" @click="$router.push('/meeting')">全部</el-link>
        </div>
        <template v-if="loading"><div class="skeleton-panel"><div class="sk-line w70"></div><div class="sk-line w50"></div><div class="sk-line w90"></div></div></template>
        <template v-else-if="todayMeetings.length">
          <div v-for="m in todayMeetings" :key="m.id" class="meeting-row" @click="$router.push('/meeting/' + m.id)">
            <div class="meeting-time">{{ fmtTime(m.meeting_time) }}</div>
            <div class="meeting-info">
              <div class="meeting-title">{{ m.title }}</div>
              <div class="meeting-status"><span class="status-dot" :class="m.status"></span>{{ statusText(m.status) }}</div>
            </div>
          </div>
        </template>
        <div v-else class="empty-state">
          <Calendar :size="28" color="var(--border-strong)" />
          <div class="empty-title">今天暂无会议安排</div>
          <div class="empty-desc">创建会议，让 AI 帮你生成纪要</div>
          <el-button size="small" @click="$router.push('/meeting/create')">新建会议</el-button>
        </div>
      </div>

      <div class="card panel">
        <div class="panel-head">
          <span class="panel-title">我的待办</span>
          <el-link :underline="false" @click="$router.push('/task')">任务看板</el-link>
        </div>
        <template v-if="loading"><div class="skeleton-panel"><div class="sk-line w70"></div><div class="sk-line w50"></div><div class="sk-line w90"></div></div></template>
        <template v-else-if="myTodos.length">
          <div v-for="t in myTodos" :key="'todo-'+t.id" class="todo-row" @click="$router.push('/task')">
            <div class="todo-info">
              <div class="todo-content">{{ t.content }}</div>
              <div class="todo-meta">
                <el-tag size="small" :type="t.priority === 'high' ? 'danger' : t.priority === 'medium' ? 'warning' : ''" effect="plain">{{ priorityLabel(t.priority) }}</el-tag>
                <span v-if="t.deadline">截止 {{ t.deadline }}</span>
                <span class="status-dot" :class="t.status" style="margin-left:4px"></span>
                <span>{{ t.status === 'pending' ? '待开始' : '进行中' }}</span>
              </div>
            </div>
          </div>
        </template>
        <div v-else class="empty-state">
          <CheckSquare :size="28" color="var(--border-strong)" />
          <div class="empty-title">暂无待办</div>
          <div class="empty-desc">保持这个节奏</div>
        </div>
      </div>
    </div>

    <div class="card panel briefing">
      <div class="panel-head">
        <span class="panel-title">今日简报</span>
        <el-tag size="small" effect="plain" round>AI 生成</el-tag>
      </div>
      <template v-if="briefingText">
        <div v-if="briefingTitle" class="briefing-title">{{ briefingTitle }}</div>
        <div class="briefing-text md-body" v-html="renderedBriefing"></div>
        <el-link :underline="false" class="briefing-link" @click="$router.push('/briefing')">查看完整简报</el-link>
      </template>
      <div v-else class="empty-state" style="flex-direction:row;gap:8px;padding:14px 0 4px">
        <span style="font-size:13px;color:var(--text-tertiary)">今日简报尚未生成</span>
        <el-link :underline="false" @click="$router.push('/briefing')">去生成</el-link>
      </div>
    </div>

    <div class="card panel inbox-panel">
      <div class="panel-head">
        <span class="panel-title">收信箱 <el-badge v-if="inboxUnreadCount" :value="inboxUnreadCount" :max="99" /></span>
        <el-link :underline="false" @click="$router.push('/inbox')">查看全部</el-link>
      </div>
      <template v-if="inboxMessages.length">
        <div v-for="message in inboxMessages" :key="'inbox-'+message.id" class="inbox-row" @click="$router.push('/inbox')">
          <div class="inbox-row-title"><span>{{ message.title }}</span><el-tag size="small" effect="plain">{{ inboxSourceLabel(message.source_type) }}</el-tag></div>
          <div class="inbox-row-preview">{{ message.content }}</div>
          <div class="inbox-row-time">{{ message.create_time }}</div>
        </div>
      </template>
      <div v-else class="empty-state" style="padding:14px 0 4px"><span style="font-size:13px;color:var(--text-tertiary)">暂无收件内容</span><el-link :underline="false" @click="$router.push('/inbox')">进入收信箱</el-link></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Calendar, CheckSquare, Library, BarChart3, ChevronRight } from 'lucide-vue-next'
import { getMe, getWorkbenchHome } from '../api'
import { renderMd } from '../utils/markdown.js'

const loading = ref(true)
const user = ref({})
const todayMeetings = ref([])
const myTodos = ref([])
const todayBriefing = ref(null)
const inboxMessages = ref([])
const inboxUnreadCount = ref(0)
const stats = ref({})

const userName = computed(() => user.value.real_name || user.value.realName || '同事')
const deptText = computed(() => user.value.dept_name || user.value.deptName || '祝你高效的一天')
const briefingTitle = computed(() => { const b = todayBriefing.value; return b && typeof b === 'object' ? (b.title || '') : '' })
const briefingText = computed(() => { const b = todayBriefing.value; if (!b) return ''; return typeof b === 'string' ? b : (b.content || '') })
const renderedBriefing = computed(() => renderMd(briefingText.value))
const greeting = computed(() => { const h = new Date().getHours(); if (h < 6) return '夜深了'; if (h < 9) return '早上好'; if (h < 12) return '上午好'; if (h < 14) return '中午好'; if (h < 18) return '下午好'; return '晚上好' })
const now = new Date()
const pad = (n) => String(n).padStart(2, '0')
const todayText = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`
const dateDay = pad(now.getDate())
const dateMonth = `${now.getFullYear()}/${pad(now.getMonth() + 1)}`
const dateWeek = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][now.getDay()]
const statCards = computed(() => [
  { icon: Calendar, label: '会议总数', value: stats.value.meeting_count ?? 0, path: '/meeting' },
  { icon: CheckSquare, label: '已完成会议', value: stats.value.completed_meeting_count ?? 0, path: '/meeting' },
  { icon: BarChart3, label: 'AI 使用次数', value: stats.value.ai_usage_count ?? 0, path: '/insight' },
  { icon: Library, label: '知识库文档', value: stats.value.kb_doc_count ?? 0, path: '/knowledge' }
])
function fmtTime(t) { if (!t) return '--:--'; const m = String(t).match(/(\d{2}:\d{2})/); return m ? m[1] : t }
function statusText(s) { return { pending: '待开始', processing: '进行中', completed: '已完成' }[s] || s || '未知' }
function priorityLabel(p) { return { high: '高', medium: '中', low: '低' }[p] || p }
function inboxSourceLabel(type) { return { writer_weekly: '智能周报', writer_email: '邮件助手', writer_notice: '通知公告' }[type] || '业务消息' }
onMounted(async () => {
  const [userRes, homeRes] = await Promise.allSettled([getMe(), getWorkbenchHome()])
  if (userRes.status === 'fulfilled' && userRes.value) user.value = userRes.value
  if (homeRes.status === 'fulfilled' && homeRes.value) {
    const d = homeRes.value; todayMeetings.value = Array.isArray(d.today_meetings) ? d.today_meetings : []
    myTodos.value = Array.isArray(d.my_todos) ? d.my_todos.slice(0, 5) : []
    todayBriefing.value = d.today_briefing || null; stats.value = d.stats || {}
    inboxMessages.value = Array.isArray(d.inbox_messages) ? d.inbox_messages : []
    inboxUnreadCount.value = d.inbox_unread_count || 0
  }
  loading.value = false
})
</script>

<style scoped>
.dashboard { max-width: 1100px; margin: 0 auto; display: flex; flex-direction: column; gap: 18px; }
.hero { position: relative; display: flex; align-items: center; justify-content: space-between; padding: 24px 28px; overflow: hidden; border: 1px solid #CFE4D5; border-radius: var(--radius-lg); background: #F0F8F2; }
.hero::after { content: ""; position: absolute; right: -58px; top: -100px; width: 250px; height: 250px; border: 1px solid rgba(15, 91, 60, .12); border-radius: 50%; box-shadow: 0 0 0 24px rgba(15, 91, 60, .035), 0 0 0 48px rgba(15, 91, 60, .02); pointer-events: none; }
.hero-main, .hero-date { position: relative; z-index: 1; }
.hero-title { margin: 0; font-size: 28px; font-weight: 700; color: var(--text-primary); letter-spacing: -0.03em; }
.hero-name { color: var(--accent); }
.hero-sub { margin: 6px 0 0; font-size: 14px; color: var(--text-secondary); }
.hero-date { text-align: right; }
.date-day { font-size: 32px; font-weight: 700; line-height: 1; color: var(--text-primary); }
.date-sub { margin-top: 4px; font-size: 12px; color: var(--text-tertiary); }
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.stat-card { display: flex; align-items: center; gap: 14px; padding: 17px 18px; background: var(--surface); border: 1px solid var(--border-light); border-radius: var(--radius-md); cursor: pointer; transition: border-color .18s, background .18s; }
.stat-card:hover { border-color: var(--accent-light); background: var(--accent-tint); }
.stat-icon { width: 38px; height: 38px; flex-shrink: 0; border-radius: 8px; background: var(--accent-soft); color: var(--accent); display: flex; align-items: center; justify-content: center; }
.stat-num { font-size: 24px; font-weight: 700; line-height: 1.15; color: var(--text-primary); font-family: "Geist", "JetBrains Mono", "Noto Sans SC", monospace; font-variant-numeric: tabular-nums; }
.stat-label { font-size: 13px; color: var(--text-tertiary); margin-top: 3px; font-weight: 500; }
.stat-arrow { margin-left: auto; color: var(--text-tertiary); opacity: 0; transition: all 0.2s ease; }
.stat-card:hover .stat-arrow { opacity: 1; color: var(--accent); }
.mid-row { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.panel { padding: 20px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.panel-title { font-size: 16px; font-weight: 600; color: var(--text-primary); }
.meeting-row { display: flex; align-items: center; gap: 14px; padding: 12px 10px; border-top: 1px solid var(--border-light); cursor: pointer; transition: background 0.18s ease; }
.meeting-row:hover { background: var(--surface-hover); }
.meeting-time { flex-shrink: 0; width: 50px; font-size: 14px; font-weight: 600; color: var(--accent); }
.meeting-info { min-width: 0; flex: 1; }
.meeting-title { font-size: 14px; font-weight: 500; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.meeting-status { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-tertiary); margin-top: 3px; }
.todo-row { padding: 11px 10px; border-top: 1px solid var(--border-light); cursor: pointer; transition: background 0.12s; }
.todo-row:hover { background: var(--surface-hover); }
.todo-info { min-width: 0; }
.todo-content { font-size: 14px; color: var(--text-primary); line-height: 1.5; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.todo-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--text-tertiary); margin-top: 4px; }
.briefing { padding: 20px; }
.briefing-title { font-size: 14px; font-weight: 600; color: var(--text-primary); margin-bottom: 6px; }
.briefing-text { margin: 0; font-size: 14px; line-height: 1.75; color: var(--text-secondary); word-break: break-word; max-height: 240px; overflow: hidden; }
.briefing-text :deep(h1),.briefing-text :deep(h2),.briefing-text :deep(h3) { color: var(--text-primary); font-weight: 600; font-size: 14px; margin: 8px 0 4px; }
.briefing-text :deep(strong) { color: var(--text-primary); font-weight: 600; }
.briefing-text :deep(table) { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; margin: 8px 0; font-size: 12px; }
.briefing-text :deep(th),.briefing-text :deep(td) { border: 1px solid var(--border); padding: 5px 9px; text-align: left; }
.briefing-text :deep(th) { background: var(--surface-hover); font-weight: 600; }
.briefing-link { margin-top: 8px; font-size: 13px; }
.inbox-panel { padding: 20px; }
.inbox-row { padding: 10px 12px; border-radius: 8px; cursor: pointer; }
.inbox-row:hover { background: var(--surface-hover); }
.inbox-row-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 500; color: var(--text-primary); }
.inbox-row-preview { margin-top: 4px; color: var(--text-secondary); font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.inbox-row-time { margin-top: 3px; color: var(--text-tertiary); font-size: 11px; }
/* 骨架屏 */
.skeleton-panel { padding: 12px 0; display: flex; flex-direction: column; gap: 10px; }
.sk-line { height: 14px; border-radius: 6px; background: linear-gradient(90deg, var(--surface-hover) 25%, var(--border-light) 50%, var(--surface-hover) 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.sk-line.w50 { width: 50%; } .sk-line.w70 { width: 70%; } .sk-line.w90 { width: 90%; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
@media (max-width: 900px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } .mid-row { grid-template-columns: 1fr; } }
</style>

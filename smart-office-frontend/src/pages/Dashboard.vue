<template>
  <div class="dashboard">
    <!-- ====== Hero 区 ====== -->
    <div class="hero card">
      <div class="hero-orb hero-orb-1"></div>
      <div class="hero-orb hero-orb-2"></div>
      <div class="hero-grid"></div>

      <div class="hero-main">
        <h1 class="hero-title">
          {{ greeting }}，<span class="grad-text">{{ user.real_name || '同事' }}</span>
        </h1>
        <p class="hero-sub">
          {{ user.dept_name }} · 今天也是被 AI 提效的一天
        </p>

        <div class="hero-actions">
          <button class="hero-btn primary" @click="$router.push('/ai')">
            <span class="btn-icon">✦</span> 与 AI 对话
          </button>
          <button class="hero-btn ghost" @click="$router.push('/meeting')">
            整理会议纪要
          </button>
        </div>
      </div>

      <div class="hero-stats">
        <div class="hero-stat">
          <div class="stat-num grad-text">{{ agents.length }}</div>
          <div class="stat-label">可用智能体</div>
        </div>
        <div class="hero-stat-divider"></div>
        <div class="hero-stat">
          <div class="stat-num grad-text">{{ sessions.length }}</div>
          <div class="stat-label">我的会话</div>
        </div>
      </div>
    </div>

    <!-- ====== 快捷入口 ====== -->
    <div class="quick-grid">
      <div v-for="q in quickLinks" :key="q.title" class="quick-card card" @click="$router.push(q.path)">
        <div class="quick-icon" :style="{ background: q.grad }">{{ q.icon }}</div>
        <div class="quick-info">
          <div class="quick-title">{{ q.title }}</div>
          <div class="quick-desc">{{ q.desc }}</div>
        </div>
        <div class="quick-arrow">→</div>
      </div>
    </div>

    <!-- ====== 下方两栏 ====== -->
    <div class="bottom-row">
      <div class="card">
        <div class="panel-head">
          <span class="panel-title">智能体</span>
          <el-link type="primary" :underline="false" @click="$router.push('/agents')">管理</el-link>
        </div>
        <div v-for="a in agents.slice(0, 3)" :key="a.id" class="agent-row" @click="$router.push('/ai?agent=' + a.id)">
          <div class="agent-icon">{{ iconOf(a.icon) }}</div>
          <div class="agent-info">
            <div class="agent-name">{{ a.name }}</div>
            <div class="agent-desc">{{ a.description }}</div>
          </div>
          <span class="agent-go">对话 ›</span>
        </div>
        <div v-if="agents.length === 0" class="empty-hint">暂无智能体</div>
      </div>

      <div class="card">
        <div class="panel-head">
          <span class="panel-title">最近会话</span>
          <el-link type="primary" :underline="false" @click="$router.push('/ai')">全部</el-link>
        </div>
        <div v-for="s in sessions.slice(0, 5)" :key="s.id" class="session-row" @click="$router.push('/ai?session=' + s.id)">
          <div class="session-dot"></div>
          <div class="session-info">
            <div class="session-title">{{ s.title }}</div>
            <div class="session-time">{{ formatTime(s.update_time) }}</div>
          </div>
        </div>
        <div v-if="sessions.length === 0" class="empty-hint">
          还没有会话，<span class="link" @click="$router.push('/ai')">去发起第一次对话 ›</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getMe, getAgents, getSessions } from '../api'

const user = ref({})
const agents = ref([])
const sessions = ref([])

/** 智能体 icon 关键字 → 表情 */
const ICON_MAP = { chat: '💬', document: '📄', trend: '📈' }
const iconOf = (key) => ICON_MAP[key] || '✦'

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const quickLinks = [
  { title: 'AI 对话', desc: '与智能体自由问答', icon: '✦', grad: 'linear-gradient(135deg,#4D8DFF,#8B5CF6)', path: '/ai' },
  { title: '会议纪要', desc: '转写稿一键出纪要', icon: '✎', grad: 'linear-gradient(135deg,#8B5CF6,#EC4899)', path: '/meeting' },
  { title: '知识库', desc: '企业知识即问即答', icon: '❖', grad: 'linear-gradient(135deg,#22D3EE,#4D8DFF)', path: '/knowledge' },
  { title: '智能体', desc: '创建与管理智能体', icon: '⬡', grad: 'linear-gradient(135deg,#34D399,#22D3EE)', path: '/agents' },
]

onMounted(async () => {
  try {
    user.value = await getMe()
    agents.value = await getAgents()
    sessions.value = await getSessions()
  } catch (e) {
    // 忽略
  }
})

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  if (d.toDateString() === now.toDateString()) {
    return `今天 ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  return `${d.getMonth() + 1}-${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.dashboard { max-width: 1200px; margin: 0 auto; }

/* ====== Hero ====== */
.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 34px 38px;
  overflow: hidden;
  margin-bottom: 18px;
}
.hero-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(64px);
  pointer-events: none;
}
.hero-orb-1 {
  width: 300px; height: 220px;
  background: rgba(77, 141, 255, 0.22);
  top: -80px; right: 10%;
  animation: drift 14s ease-in-out infinite alternate;
}
.hero-orb-2 {
  width: 220px; height: 180px;
  background: rgba(139, 92, 246, 0.16);
  bottom: -70px; left: 22%;
  animation: drift 18s ease-in-out infinite alternate-reverse;
}
@keyframes drift {
  from { transform: translate(0, 0); }
  to   { transform: translate(40px, 18px); }
}
.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 80% 90% at 30% 40%, black 20%, transparent 100%);
  -webkit-mask-image: radial-gradient(ellipse 80% 90% at 30% 40%, black 20%, transparent 100%);
  pointer-events: none;
}

.hero-main { position: relative; z-index: 1; }
.hero-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}
.hero-sub {
  margin: 8px 0 0;
  font-size: 13.5px;
  color: var(--text-secondary);
}

.hero-actions { display: flex; gap: 12px; margin-top: 20px; }
.hero-btn {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 10px 20px;
  border-radius: 11px;
  font-size: 13.5px;
  font-weight: 550;
  cursor: pointer;
  transition: all 0.18s ease;
  border: none;
}
.hero-btn.primary {
  background: var(--grad-brand);
  color: #fff;
  box-shadow: var(--glow-brand);
}
.hero-btn.primary:hover { filter: brightness(1.14); transform: translateY(-1px); }
.hero-btn.ghost {
  background: var(--surface);
  color: var(--text-primary);
  border: 1px solid var(--border);
}
.hero-btn.ghost:hover { background: var(--surface-hover); border-color: var(--border-strong); }
.btn-icon { font-size: 14px; }

.hero-stats {
  display: flex; align-items: center; gap: 26px;
  position: relative; z-index: 1;
  padding: 18px 26px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border-light);
}
.hero-stat { text-align: center; }
.stat-num { font-size: 30px; font-weight: 750; letter-spacing: -0.02em; line-height: 1.1; }
.stat-label { font-size: 12px; color: var(--text-tertiary); margin-top: 4px; }
.hero-stat-divider { width: 1px; height: 34px; background: var(--border); }

/* ====== 快捷入口 ====== */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.quick-card {
  display: flex; align-items: center; gap: 13px;
  padding: 17px 18px;
  cursor: pointer;
  transition: all 0.22s ease;
  overflow: hidden;
}
.quick-card:hover {
  transform: translateY(-3px);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md), var(--glow-soft);
}
.quick-icon {
  width: 42px; height: 42px;
  flex-shrink: 0;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 19px;
  color: #fff;
}
.quick-info { flex: 1; min-width: 0; }
.quick-title { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.quick-desc {
  font-size: 12px; color: var(--text-tertiary); margin-top: 2px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.quick-arrow {
  color: var(--text-tertiary);
  opacity: 0;
  transform: translateX(-6px);
  transition: all 0.22s ease;
}
.quick-card:hover .quick-arrow { opacity: 1; transform: translateX(0); color: var(--accent); }

/* ====== 底部两栏 ====== */
.bottom-row {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 14px;
}

.panel-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 6px;
}
.panel-title { font-size: 15px; font-weight: 600; color: var(--text-primary); }

.agent-row {
  display: flex; align-items: center; gap: 12px;
  padding: 11px 10px;
  border-radius: 11px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.agent-row:hover { background: var(--surface-hover); }
.agent-icon {
  width: 38px; height: 38px;
  flex-shrink: 0;
  border-radius: 11px;
  background: var(--grad-brand);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px;
  box-shadow: 0 0 14px rgba(77, 141, 255, 0.3);
}
.agent-info { flex: 1; min-width: 0; }
.agent-name { font-size: 13.5px; font-weight: 550; color: var(--text-primary); }
.agent-desc {
  font-size: 12px; color: var(--text-tertiary); margin-top: 1px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.agent-go {
  font-size: 12px; color: var(--accent);
  opacity: 0; transition: opacity 0.15s;
}
.agent-row:hover .agent-go { opacity: 1; }

.session-row {
  display: flex; align-items: center; gap: 11px;
  padding: 11px 10px;
  border-radius: 11px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.session-row:hover { background: var(--surface-hover); }
.session-dot {
  width: 7px; height: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--grad-aurora);
  box-shadow: 0 0 8px rgba(139, 92, 246, 0.7);
}
.session-info { flex: 1; min-width: 0; }
.session-title {
  font-size: 13.5px; color: var(--text-primary); font-weight: 500;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.session-time { font-size: 12px; color: var(--text-tertiary); margin-top: 1px; }

.empty-hint {
  padding: 28px 0;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}
.empty-hint .link { color: var(--accent); cursor: pointer; }

@media (max-width: 1100px) {
  .quick-grid { grid-template-columns: repeat(2, 1fr); }
  .bottom-row { grid-template-columns: 1fr; }
}
</style>

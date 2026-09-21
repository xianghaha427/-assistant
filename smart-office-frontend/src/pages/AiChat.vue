<template>
  <div class="ai-chat">
    <div class="session-panel">
      <el-button type="primary" class="new-chat-btn" @click="startNewSession">＋ 新对话</el-button>
      <div class="session-list">
        <div v-for="s in sessions" :key="s.id" class="session-item" :class="{ active: s.id === currentSessionId }" @click="selectSession(s)">
          <div class="session-item-title">{{ s.title }}</div>
          <div class="session-item-sub">
            <span>{{ formatTime(s.update_time) }}</span>
            <Trash2 :size="14" class="del-icon" @click.stop="handleDelete(s)" />
          </div>
        </div>
        <div v-if="sessions.length === 0" class="no-session">暂无会话</div>
      </div>
    </div>

    <div class="chat-area">
      <div v-if="messages.length === 0" class="welcome">
        <div class="welcome-logo"><TrendingUp :size="32" /></div>
        <h2 class="welcome-title">你好，我是<span class="grad-text">恒生智能办公助手</span></h2>
        <p class="welcome-sub">金融企业智能办公伙伴，随时为你解答、起草、分析</p>
        <div class="quick-questions">
          <div v-for="(q, i) in quickQuestions" :key="i" class="quick-q" @click="sendMessage(q)">{{ q }}</div>
        </div>
      </div>

      <div v-else class="message-list" ref="messageListRef">
        <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
          <div class="msg-avatar" v-if="m.role === 'assistant'"><Building2 :size="16" /></div>
          <div class="msg-bubble">
            <template v-if="m.role === 'assistant'">
              <template v-for="(seg, j) in splitSegments(m.content, m.streaming)" :key="j">
                <div v-if="seg.type === 'chart'" class="chart-slot" :ref="(el) => bindChart(el, i + '-' + j, seg.option)"></div>
                <div v-else v-html="renderMd(seg.text)"></div>
              </template>
            </template>
            <template v-else>{{ m.content }}</template>
            <span v-if="m.streaming" class="cursor">▍</span>
          </div>
        </div>
      </div>

      <div class="input-bar">
        <div class="input-shell">
          <textarea v-model="inputText" rows="1" placeholder="输入消息，Enter 发送…" @keydown="handleKeydown" @input="autoResize" ref="inputRef"></textarea>
          <button class="send-btn" :disabled="sending || !inputText.trim()" @click="sendMessage()">
            <span v-if="sending" class="sending-dots"><i></i><i></i><i></i></span>
            <span v-else>↑</span>
          </button>
        </div>
        <div class="input-tip">内容由 AI 生成，请注意甄别 · Enter 发送</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Trash2 } from 'lucide-vue-next'
import { TrendingUp } from 'lucide-vue-next'
import { getSessions, getSessionMessages, deleteSession, streamChat } from '../api'
import * as echarts from 'echarts/core'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { TitleComponent, GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([LineChart, PieChart, BarChart, TitleComponent, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps({ agentId: { type: Number, default: null } })
const route = useRoute()
const sessions = ref([])
const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const currentSessionId = ref(null)
const currentAgentId = ref(props.agentId ?? (route.query.agent ? Number(route.query.agent) : null))
const messageListRef = ref(null)
const inputRef = ref(null)
let chatController = null

const quickQuestions = [
  '帮我写一份本周工作总结，重点突出客户拜访和项目进展',
  '用通俗的语言解释什么是 RAG 检索增强生成',
  '把以下会议记录整理成纪要：讨论了Q3预算调整，决定增加IT投入10%'
]

onMounted(async () => {
  if (!props.agentId && route.query.agent) currentAgentId.value = Number(route.query.agent)
  await loadSessions()
  const sid = route.query.session
  if (sid) { const s = sessions.value.find(x => x.id === Number(sid)); if (s) selectSession(s) }
})
onBeforeUnmount(() => { if (chatController) { chatController.abort(); chatController = null } disposeAllCharts(); window.removeEventListener('resize', handleChartResize) })
window.addEventListener('resize', handleChartResize)
watch(() => props.agentId, (v) => { if (v) switchAgent(v) })
watch(() => route.query.agent, (v) => { if (!props.agentId && v) switchAgent(Number(v)) })

function switchAgent(agentId) { if (currentAgentId.value === agentId) return; currentAgentId.value = agentId; currentSessionId.value = null; messages.value = []; disposeAllCharts(); loadSessions() }
async function loadSessions() { const params = currentAgentId.value ? { agentId: currentAgentId.value } : undefined; sessions.value = await getSessions(params) }
async function selectSession(s) { if (sending.value) { ElMessage.warning('正在回复中，请稍候'); return } currentSessionId.value = s.id; if (s.agent_id) currentAgentId.value = s.agent_id; disposeAllCharts(); messages.value = await getSessionMessages(s.id); scrollToBottom() }
function startNewSession() { if (sending.value) { ElMessage.warning('正在回复中，请稍候'); return } currentSessionId.value = null; messages.value = []; disposeAllCharts(); inputRef.value?.focus() }
async function handleDelete(s) { await ElMessageBox.confirm(`删除会话「${s.title}」？`, '提示', { type: 'warning' }); await deleteSession(s.id); ElMessage.success('已删除'); if (currentSessionId.value === s.id) { currentSessionId.value = null; messages.value = [] } await loadSessions() }
function handleKeydown(e) { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage() } }
function autoResize(e) { const el = e.target; el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 160) + 'px' }

async function sendMessage(text) {
  const content = (text || inputText.value).trim()
  if (!content || sending.value) return
  inputText.value = ''; if (inputRef.value) inputRef.value.style.height = 'auto'
  messages.value.push({ role: 'user', content })
  const aiMsg = { role: 'assistant', content: '', streaming: true }
  messages.value.push(aiMsg); sending.value = true; scrollToBottom()
  chatController = new AbortController()
  await streamChat({ message: content, session_id: currentSessionId.value, agent_id: currentAgentId.value }, {
    onSession: (sid) => { currentSessionId.value = sid; loadSessions() },
    onMessage: (chunk) => { aiMsg.content += chunk; scrollToBottom() },
    onDone: () => { aiMsg.streaming = false },
    onError: (msg) => { aiMsg.content = aiMsg.content || ('抱歉，出了点问题：' + msg); aiMsg.streaming = false }
  }, chatController.signal)
  chatController = null; sending.value = false; aiMsg.streaming = false; scrollToBottom()
}
function scrollToBottom() { nextTick(() => { if (messageListRef.value) messageListRef.value.scrollTop = messageListRef.value.scrollHeight }) }
function renderMd(text) { const escaped = String(text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); return escaped.replace(/\n/g, '<br>') }

/* ====== ECharts (unchanged logic) ====== */
const chartInitMap = new Map(); const chartObserverMap = new Map()
const LEGACY_COLOR_MAP = { '#4d8dff': '#6FAF7B', '#8b5cf6': '#4E9A5F', '#7c5cfc': '#4E9A5F', '#a78bfa': '#81C995', '#22d3ee': '#81C995', '#0fb5ce': '#6FAF7B', '#6366f1': '#4E9A5F', '#818cf8': '#81C995', '#67e8f9': '#A5D5AF' }
function normalizeOption(opt) { try { let s = JSON.stringify(opt); for (const [o, n] of Object.entries(LEGACY_COLOR_MAP)) s = s.replace(new RegExp(o, 'gi'), n); return JSON.parse(s) } catch (e) { return opt } }
function splitSegments(content, streaming) {
  const segs = []; const text = String(content || ''); const re = /```echarts\s*\n?([\s\S]*?)```/g; let last = 0, m
  while ((m = re.exec(text)) !== null) { if (m.index > last) segs.push({ type: 'text', text: text.slice(last, m.index) }); let option = null; try { const p = JSON.parse(m[1].trim()); if (p && typeof p === 'object') option = p } catch (e) {} segs.push(option ? { type: 'chart', option } : { type: 'text', text: m[0] }); last = m.index + m[0].length }
  let tail = text.slice(last); if (streaming) { const idx = tail.lastIndexOf('```echarts'); if (idx !== -1) tail = tail.slice(0, idx) + '\n正在生成图表…' }
  if (tail) segs.push({ type: 'text', text: tail }); return segs
}
function bindChart(el, key, option) {
  if (!el) return; const existing = chartInitMap.get(key)
  if (existing) { if (existing.getDom() === el) { existing.setOption(normalizeOption(option)); nextTick(() => existing.resize()); return } existing.dispose(); chartInitMap.delete(key); chartObserverMap.get(key)?.disconnect(); chartObserverMap.delete(key) }
  try { const inst = echarts.init(el); inst.setOption(normalizeOption(option)); chartInitMap.set(key, inst); nextTick(() => inst.resize()); if (typeof ResizeObserver !== 'undefined') { const ro = new ResizeObserver(() => inst.resize()); ro.observe(el); chartObserverMap.set(key, ro) } } catch (e) {}
}
function disposeAllCharts() { chartObserverMap.forEach(r => r.disconnect()); chartObserverMap.clear(); chartInitMap.forEach(i => i.dispose()); chartInitMap.clear() }
function handleChartResize() { chartInitMap.forEach(i => i.resize()) }
function formatTime(t) { if (!t) return ''; const d = new Date(t); const now = new Date(); const pad = n => String(n).padStart(2,'0'); if (d.toDateString() === now.toDateString()) return `${pad(d.getHours())}:${pad(d.getMinutes())}`; return `${d.getMonth()+1}-${d.getDate()}` }
</script>

<style scoped>
.ai-chat { display: flex; gap: 16px; height: calc(100vh - 102px); }
.session-panel { width: 250px; flex-shrink: 0; display: flex; flex-direction: column; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); padding: 14px; }
.new-chat-btn { margin-bottom: 12px; }
.session-list { flex: 1; overflow-y: auto; }
.session-item { padding: 10px 12px; border-radius: 8px; cursor: pointer; margin-bottom: 4px; border: 1px solid transparent; transition: background 0.12s; }
.session-item:hover { background: var(--surface-hover); }
.session-item:hover .del-icon { opacity: 1; }
.session-item.active { background: var(--accent-soft); border-color: var(--accent-light); }
.session-item-title { font-size: 13px; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session-item-sub { display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: var(--text-tertiary); margin-top: 3px; }
.del-icon { font-size: 13px; color: var(--text-tertiary); opacity: 0; transition: opacity 0.12s, color 0.12s; }
.del-icon:hover { color: #E06060; }
.no-session { text-align: center; color: var(--text-tertiary); font-size: 13px; padding: 24px 0; }

.chat-area { flex: 1; display: flex; flex-direction: column; min-width: 0; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; }

.welcome { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding-bottom: 6vh; }
.welcome-logo { width: 64px; height: 64px; border-radius: 14px; background: var(--accent); color: #fff; display: flex; align-items: center; justify-content: center; margin-bottom: 18px; }
.welcome-title { margin: 0; font-size: 24px; font-weight: 600; color: var(--text-primary); }
.welcome-sub { margin: 8px 0 0; font-size: 14px; color: var(--text-secondary); }
.quick-questions { display: flex; flex-direction: column; gap: 10px; margin-top: 28px; width: 500px; max-width: 90%; }
.quick-q { padding: 12px 16px; border-radius: 10px; background: var(--bg); border: 1px solid var(--border); font-size: 13px; color: var(--text-secondary); cursor: pointer; transition: all 0.15s; }
.quick-q:hover { color: var(--text-primary); border-color: var(--accent-light); background: var(--accent-soft); }

.message-list { flex: 1; overflow-y: auto; padding: 20px 24px; }
.msg-row { display: flex; margin-bottom: 16px; align-items: flex-start; }
.msg-row.user { justify-content: flex-end; }
.msg-avatar { width: 30px; height: 30px; flex-shrink: 0; border-radius: 8px; background: var(--accent); color: #fff; display: flex; align-items: center; justify-content: center; margin-right: 10px; }
.msg-bubble { max-width: 72%; padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.65; word-break: break-word; }
.msg-row.user .msg-bubble { background: var(--accent); color: #fff; border-bottom-right-radius: 4px; }
.msg-row.assistant .msg-bubble { background: var(--bg); border: 1px solid var(--border); color: var(--text-primary); border-bottom-left-radius: 4px; }
.msg-row.assistant .msg-bubble:has(.chart-slot) { max-width: 92%; }
.chart-slot { width: 100%; height: 320px; margin: 8px 0 4px; border-radius: var(--radius-sm); border: 1px solid var(--border); background: var(--surface); padding: 4px; }
.cursor { display: inline-block; animation: blink 0.8s steps(1) infinite; color: var(--accent); font-weight: 700; }
@keyframes blink { 50% { opacity: 0; } }

.input-bar { padding: 12px 20px 14px; }
.input-shell { display: flex; align-items: flex-end; gap: 8px; background: var(--bg); border: 1px solid var(--border); border-radius: 12px; padding: 8px 8px 8px 14px; transition: border-color 0.15s; }
.input-shell:focus-within { border-color: var(--accent); }
.input-shell textarea { flex: 1; border: none; outline: none; resize: none; background: transparent; color: var(--text-primary); font-size: 14px; line-height: 1.6; font-family: inherit; max-height: 140px; padding: 4px 0; }
.input-shell textarea::placeholder { color: var(--text-tertiary); }
.send-btn { width: 34px; height: 34px; flex-shrink: 0; border-radius: 8px; border: none; background: var(--accent); color: #fff; font-size: 16px; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: opacity 0.15s; }
.send-btn:hover:not(:disabled) { opacity: 0.85; }
.send-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.sending-dots { display: flex; gap: 3px; }
.sending-dots i { width: 4px; height: 4px; border-radius: 50%; background: #fff; animation: dot-jump 1s ease-in-out infinite; }
.sending-dots i:nth-child(2) { animation-delay: 0.15s; }
.sending-dots i:nth-child(3) { animation-delay: 0.3s; }
@keyframes dot-jump { 0%,60%,100% { transform: translateY(0); opacity: 0.5; } 30% { transform: translateY(-4px); opacity: 1; } }
.input-tip { text-align: center; font-size: 12px; color: var(--text-tertiary); margin-top: 8px; }
</style>

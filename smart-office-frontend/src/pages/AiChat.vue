<template>
  <div class="ai-chat">
    <!-- ====== 会话列表侧栏 ====== -->
    <div class="session-panel">
      <el-button type="primary" class="new-chat-btn" @click="startNewSession">
        ＋ 新对话
      </el-button>

      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === currentSessionId }"
          @click="selectSession(s)"
        >
          <div class="session-item-title">{{ s.title }}</div>
          <div class="session-item-sub">
            <span>{{ formatTime(s.update_time) }}</span>
            <el-icon class="del-icon" @click.stop="handleDelete(s)"><Delete /></el-icon>
          </div>
        </div>
        <div v-if="sessions.length === 0" class="no-session">暂无会话</div>
      </div>
    </div>

    <!-- ====== 对话区 ====== -->
    <div class="chat-area">
      <!-- 空状态欢迎页 -->
      <div v-if="messages.length === 0" class="welcome">
        <div class="welcome-logo">✦</div>
        <h2 class="welcome-title">你好，我是 Fin<span class="grad-text">Pilot</span></h2>
        <p class="welcome-sub">你的金融办公智能助手，随时为你解答、起草、分析</p>
        <div class="quick-questions">
          <div
            v-for="(q, i) in quickQuestions"
            :key="i"
            class="quick-q"
            @click="sendMessage(q)"
          >{{ q }}</div>
        </div>
      </div>

      <!-- 消息流 -->
      <div v-else class="message-list" ref="messageListRef">
        <div
          v-for="(m, i) in messages"
          :key="i"
          class="msg-row"
          :class="m.role"
        >
          <div class="msg-avatar" v-if="m.role === 'assistant'">✦</div>
          <div class="msg-bubble">
            <div v-if="m.role === 'assistant'" v-html="renderMd(m.content)"></div>
            <template v-else>{{ m.content }}</template>
            <span v-if="m.streaming" class="cursor">▍</span>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-bar">
        <div class="input-shell">
          <textarea
            v-model="inputText"
            rows="1"
            placeholder="输入消息，Enter 发送…"
            @keydown="handleKeydown"
            @input="autoResize"
            ref="inputRef"
          ></textarea>
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
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { getSessions, getSessionMessages, deleteSession, streamChat } from '../api'

const route = useRoute()

const sessions = ref([])
const messages = ref([])
const inputText = ref('')
const sending = ref(false)
const currentSessionId = ref(null)
const messageListRef = ref(null)
const inputRef = ref(null)

const quickQuestions = [
  '帮我写一份本周工作总结，重点突出客户拜访和项目进展',
  '用通俗的语言解释什么是 RAG 检索增强生成',
  '把以下会议记录整理成纪要：讨论了Q3预算调整，决定增加IT投入10%'
]

onMounted(async () => {
  await loadSessions()
  // URL 参数恢复会话（从工作台跳转）
  const sid = route.query.session
  if (sid) {
    const s = sessions.value.find(x => x.id === Number(sid))
    if (s) selectSession(s)
  }
})

async function loadSessions() {
  sessions.value = await getSessions()
}

async function selectSession(s) {
  if (sending.value) {
    ElMessage.warning('正在回复中，请稍候')
    return
  }
  currentSessionId.value = s.id
  messages.value = await getSessionMessages(s.id)
  scrollToBottom()
}

function startNewSession() {
  if (sending.value) {
    ElMessage.warning('正在回复中，请稍候')
    return
  }
  currentSessionId.value = null
  messages.value = []
  inputRef.value?.focus()
}

async function handleDelete(s) {
  await ElMessageBox.confirm(`删除会话「${s.title}」？`, '提示', { type: 'warning' })
  await deleteSession(s.id)
  ElMessage.success('已删除')
  if (currentSessionId.value === s.id) {
    currentSessionId.value = null
    messages.value = []
  }
  await loadSessions()
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function autoResize(e) {
  const el = e.target
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

async function sendMessage(text) {
  const content = (text || inputText.value).trim()
  if (!content || sending.value) return
  inputText.value = ''
  if (inputRef.value) inputRef.value.style.height = 'auto'

  messages.value.push({ role: 'user', content })
  const aiMsg = { role: 'assistant', content: '', streaming: true }
  messages.value.push(aiMsg)
  sending.value = true
  scrollToBottom()

  await streamChat(
    { message: content, session_id: currentSessionId.value },
    {
      onSession: (sid) => {
        currentSessionId.value = sid
        loadSessions()
      },
      onMessage: (chunk) => {
        aiMsg.content += chunk
        scrollToBottom()
      },
      onDone: () => {
        aiMsg.streaming = false
      },
      onError: (msg) => {
        aiMsg.content = aiMsg.content || ('抱歉，出了点问题：' + msg)
        aiMsg.streaming = false
      }
    }
  )
  sending.value = false
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

function renderMd(text) {
  // 极简渲染：转义 HTML + 保留换行
  const escaped = String(text || '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return escaped.replace(/\n/g, '<br>')
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  if (d.toDateString() === now.toDateString()) {
    return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  return `${d.getMonth() + 1}-${d.getDate()}`
}
</script>

<style scoped>
.ai-chat {
  display: flex;
  gap: 16px;
  height: calc(100vh - 102px);
}

/* ====== 会话侧栏 ====== */
.session-panel {
  width: 250px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 14px;
  backdrop-filter: blur(18px);
}
.new-chat-btn { margin-bottom: 12px; }

.session-list { flex: 1; overflow-y: auto; }
.session-item {
  padding: 10px 12px;
  border-radius: 11px;
  cursor: pointer;
  margin-bottom: 4px;
  border: 1px solid transparent;
  transition: all 0.16s ease;
  position: relative;
}
.session-item:hover { background: var(--surface-hover); }
.session-item:hover .del-icon { opacity: 1; }
.session-item.active {
  background: var(--accent-soft);
  border-color: rgba(77, 141, 255, 0.25);
}
.session-item.active::before {
  content: "";
  position: absolute;
  left: 0; top: 50%;
  transform: translateY(-50%);
  width: 3px; height: 16px;
  border-radius: 2px;
  background: var(--grad-brand);
  box-shadow: 0 0 8px rgba(77, 141, 255, 0.8);
}
.session-item-title {
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.session-item-sub {
  display: flex; justify-content: space-between; align-items: center;
  font-size: 11.5px;
  color: var(--text-tertiary);
  margin-top: 3px;
}
.del-icon {
  font-size: 13px;
  color: var(--text-tertiary);
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
}
.del-icon:hover { color: #F87171; }
.no-session { text-align: center; color: var(--text-tertiary); font-size: 12.5px; padding: 24px 0; }

/* ====== 对话区 ====== */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--surface);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  backdrop-filter: blur(18px);
  position: relative;
  overflow: hidden;
}

/* ---- 欢迎页 ---- */
.welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: 8vh;
  position: relative;
}
.welcome::before {
  content: "";
  position: absolute;
  width: 480px; height: 300px;
  background: radial-gradient(ellipse, rgba(77, 141, 255, 0.14), transparent 70%);
  top: 18%;
  pointer-events: none;
}
.welcome-logo {
  width: 72px; height: 72px;
  border-radius: 22px;
  background: var(--grad-brand);
  color: #fff;
  font-size: 34px;
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--glow-brand);
  position: relative;
  animation: float 4s ease-in-out infinite;
}
.welcome-logo::after {
  content: "";
  position: absolute;
  inset: -8px;
  border-radius: 28px;
  border: 1px solid rgba(77, 141, 255, 0.3);
  animation: pulse-ring 2.8s ease-out infinite;
}
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50%      { transform: translateY(-7px); }
}
@keyframes pulse-ring {
  0%   { transform: scale(0.94); opacity: 0.9; }
  70%  { transform: scale(1.14); opacity: 0; }
  100% { transform: scale(1.14); opacity: 0; }
}
.welcome-title {
  margin: 22px 0 0;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}
.welcome-sub {
  margin: 8px 0 0;
  font-size: 13.5px;
  color: var(--text-secondary);
}
.quick-questions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 30px;
  width: 520px;
  max-width: 90%;
}
.quick-q {
  padding: 13px 18px;
  border-radius: 13px;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid var(--border);
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.18s ease;
}
.quick-q:hover {
  color: var(--text-primary);
  border-color: rgba(77, 141, 255, 0.45);
  background: var(--accent-soft);
  transform: translateX(4px);
  box-shadow: 0 0 18px rgba(77, 141, 255, 0.15);
}

/* ---- 消息流 ---- */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px 26px;
}
.msg-row {
  display: flex;
  margin-bottom: 18px;
  align-items: flex-start;
}
.msg-row.user { justify-content: flex-end; }

.msg-avatar {
  width: 32px; height: 32px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--grad-brand);
  color: #fff;
  font-size: 15px;
  display: flex; align-items: center; justify-content: center;
  margin-right: 10px;
  box-shadow: 0 0 12px rgba(77, 141, 255, 0.35);
}

.msg-bubble {
  max-width: 70%;
  padding: 11px 16px;
  border-radius: 14px;
  font-size: 13.5px;
  line-height: 1.65;
  word-break: break-word;
}
.msg-row.user .msg-bubble {
  background: var(--grad-brand);
  color: #fff;
  border-bottom-right-radius: 5px;
  box-shadow: 0 4px 18px rgba(77, 141, 255, 0.25);
}
.msg-row.assistant .msg-bubble {
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid var(--border-light);
  color: var(--text-primary);
  border-bottom-left-radius: 5px;
}

/* 打字光标 */
.cursor {
  display: inline-block;
  animation: blink 0.8s steps(1) infinite;
  color: var(--accent);
  font-weight: 700;
}
@keyframes blink {
  50% { opacity: 0; }
}

/* ---- 输入区 ---- */
.input-bar { padding: 14px 22px 16px; }
.input-shell {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 8px 8px 8px 16px;
  transition: border-color 0.18s, box-shadow 0.18s;
}
.input-shell:focus-within {
  border-color: rgba(77, 141, 255, 0.55);
  box-shadow: 0 0 0 3px rgba(77, 141, 255, 0.12), 0 0 24px rgba(77, 141, 255, 0.12);
}
.input-shell textarea {
  flex: 1;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
  line-height: 1.6;
  font-family: inherit;
  max-height: 160px;
  padding: 5px 0;
}
.input-shell textarea::placeholder { color: var(--text-tertiary); }

.send-btn {
  width: 36px; height: 36px;
  flex-shrink: 0;
  border-radius: 11px;
  border: none;
  background: var(--grad-brand);
  color: #fff;
  font-size: 17px;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: filter 0.15s, opacity 0.15s;
  box-shadow: 0 2px 12px rgba(77, 141, 255, 0.35);
}
.send-btn:hover:not(:disabled) { filter: brightness(1.15); }
.send-btn:disabled { opacity: 0.35; cursor: not-allowed; box-shadow: none; }

/* 发送中的呼吸圆点 */
.sending-dots { display: flex; gap: 3px; }
.sending-dots i {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: #fff;
  animation: dot-jump 1s ease-in-out infinite;
}
.sending-dots i:nth-child(2) { animation-delay: 0.15s; }
.sending-dots i:nth-child(3) { animation-delay: 0.3s; }
@keyframes dot-jump {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
  30%           { transform: translateY(-4px); opacity: 1; }
}

.input-tip {
  text-align: center;
  font-size: 11.5px;
  color: var(--text-tertiary);
  margin-top: 9px;
}
</style>

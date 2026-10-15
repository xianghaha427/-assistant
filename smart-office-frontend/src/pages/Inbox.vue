<template>
  <div class="inbox-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">收信箱</h2>
        <p class="page-subtitle">查看智能周报、邮件助手和通知公告发送给你的业务内容</p>
      </div>
      <el-button v-if="unreadCount" text @click="markAllRead">全部已读</el-button>
    </div>

    <div class="inbox-panel card">
      <div v-if="loading" class="inbox-empty">正在加载收件内容...</div>
      <div v-else-if="messages.length === 0" class="inbox-empty">暂无收件内容</div>
      <div v-else class="message-list">
        <article v-for="message in messages" :key="message.id" class="message-item" :class="{ unread: message.is_read === 0 }" @click="openMessage(message)">
          <div class="message-main">
            <div class="message-title-row">
              <span class="message-title">{{ message.title }}</span>
              <el-tag size="small" effect="plain">{{ sourceLabel(message.source_type) }}</el-tag>
            </div>
            <div class="message-preview">{{ message.content }}</div>
            <div class="message-time">{{ message.create_time }}</div>
          </div>
          <span v-if="message.is_read === 0" class="unread-dot"></span>
        </article>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="activeMessage?.title || '收件内容'" width="720px">
      <div v-if="activeMessage" class="message-detail">
        <div class="detail-meta">{{ sourceLabel(activeMessage.source_type) }} · {{ activeMessage.create_time }}</div>
        <pre>{{ activeMessage.content }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getInbox, markInboxRead, markAllInboxRead } from '../api'

const messages = ref([])
const unreadCount = ref(0)
const loading = ref(true)
const dialogVisible = ref(false)
const activeMessage = ref(null)

function sourceLabel(type) {
  return { writer_weekly: '智能周报', writer_email: '邮件助手', writer_notice: '通知公告' }[type] || '业务消息'
}
async function load() {
  loading.value = true
  try {
    messages.value = await getInbox(100) || []
    unreadCount.value = messages.value.filter(m => m.is_read === 0).length
  } finally { loading.value = false }
}
async function openMessage(message) {
  activeMessage.value = message
  dialogVisible.value = true
  if (message.is_read === 0) {
    await markInboxRead(message.id)
    message.is_read = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
}
async function markAllRead() {
  await markAllInboxRead()
  messages.value.forEach(m => { m.is_read = 1 })
  unreadCount.value = 0
  ElMessage.success('已全部标记为已读')
}
onMounted(load)
</script>

<style scoped>
.inbox-page { max-width: 1000px; margin: 0 auto; display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; }
.page-title { margin: 0; }
.page-subtitle { margin: 6px 0 0; color: var(--text-secondary); font-size: 13px; }
.inbox-panel { padding: 0; overflow: hidden; }
.inbox-empty { padding: 72px 20px; text-align: center; color: var(--text-tertiary); font-size: 13px; }
.message-item { display: flex; align-items: flex-start; gap: 12px; padding: 18px 22px; border-bottom: 1px solid var(--border-light); cursor: pointer; }
.message-item:last-child { border-bottom: 0; }
.message-item:hover { background: var(--surface-hover); }
.message-item.unread { background: var(--accent-soft); }
.message-main { min-width: 0; flex: 1; }
.message-title-row { display: flex; align-items: center; gap: 8px; }
.message-title { font-weight: 600; color: var(--text-primary); }
.message-preview { margin-top: 8px; color: var(--text-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.message-time { margin-top: 8px; font-size: 12px; color: var(--text-tertiary); }
.unread-dot { width: 7px; height: 7px; flex-shrink: 0; margin-top: 7px; border-radius: 50%; background: var(--accent); }
.message-detail .detail-meta { color: var(--text-tertiary); font-size: 12px; margin-bottom: 14px; }
.message-detail pre { margin: 0; max-height: 520px; overflow: auto; white-space: pre-wrap; word-break: break-word; color: var(--text-primary); font: inherit; line-height: 1.8; }
</style>

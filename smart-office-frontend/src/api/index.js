import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * axios 实例：统一携带 token、统一处理业务错误码
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('so_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && body.code !== undefined && body.code !== 0) {
      // 未登录 → 回登录页
      if (body.code === 401) {
        localStorage.removeItem('so_token')
        window.location.href = '/login'
        return Promise.reject(new Error(body.msg))
      }
      ElMessage.error(body.msg || '请求失败')
      return Promise.reject(new Error(body.msg))
    }
    return body
  },
  (err) => {
    ElMessage.error(err.response?.data?.msg || err.message || '网络错误')
    return Promise.reject(err)
  }
)

/**
 * SSE 流式对话（POST + text/event-stream）
 * @param {object} payload { session_id, agent_id, message }
 * @param {object} handlers { onSession, onMessage, onDone, onError }
 * @param {AbortSignal} [signal] AbortController 的 signal，组件卸载/页面切换时主动中断
 */
export async function streamChat(payload, handlers, signal) {
  const token = localStorage.getItem('so_token')
  let resp
  try {
    resp = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify(payload),
      signal
    })
  } catch (e) {
    // 主动中断（页面切换/组件卸载）静默忽略，不弹错
    if (e?.name === 'AbortError') return
    handlers.onError?.(e)
    return
  }
  if (!resp.ok) {
    handlers.onError?.(new Error('对话请求失败：' + resp.status))
    return
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 按 SSE 协议解析：事件块之间以空行分隔
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop()
      for (const block of blocks) {
        let eventName = 'message'
        let data = ''
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) eventName = line.slice(6).trim()
          else if (line.startsWith('data:')) data += line.slice(5)
        }
        if (eventName === 'session') handlers.onSession?.(Number(data))
        else if (eventName === 'message') handlers.onMessage?.(data)
        else if (eventName === 'done') handlers.onDone?.()
      }
    }
    handlers.onDone?.()
  } catch (e) {
    // 流读取中被 abort（页面切换/组件卸载）静默忽略
    if (e?.name === 'AbortError') return
    handlers.onError?.(e)
  }
}

export default request

/** 统一取响应体中的 data 字段 */
const data = (p) => p.then((body) => body.data)

/* ==================== 认证 ==================== */
export const login = (form) => data(request.post('/auth/login', form))
export const logout = () => data(request.post('/auth/logout'))
export const getMe = () => data(request.get('/auth/me'))

/* ==================== 工作台聚合 ==================== */
/**
 * 工作台首页聚合数据（响应字段 SNAKE_CASE）：
 * today_meetings / my_todos / today_briefing / stats
 */
export const getWorkbenchHome = () => data(request.get('/workbench/home'))

/* ==================== 智能体（后端管理员用，页面入口已下线） ==================== */
export const getAgents = () => data(request.get('/agents'))
export const createAgent = (form) => data(request.post('/agents', form))
export const updateAgent = (id, form) => data(request.put(`/agents/${id}`, form))
export const deleteAgent = (id) => data(request.delete(`/agents/${id}`))

/* ==================== AI 会话 ==================== */
export const createSession = (form) => data(request.post('/ai/sessions', form))
export const getSessions = (params) => data(request.get('/ai/sessions', { params }))
export const getSessionMessages = (id) => data(request.get(`/ai/sessions/${id}/messages`))
export const deleteSession = (id) => data(request.delete(`/ai/sessions/${id}`))

/* ==================== 个人中心 ==================== */
export const updateProfile = (form) => data(request.put('/user/profile', form))
export const changePassword = (form) => data(request.put('/user/password', form))

/* ==================== 系统管理 ==================== */
export const getUsers = (params) => data(request.get('/admin/users', { params }))
export const createUser = (form) => data(request.post('/admin/users', form))
export const updateUser = (id, form) => data(request.put(`/admin/users/${id}`, form))
export const deleteUser = (id) => data(request.delete(`/admin/users/${id}`))
export const getRoles = () => data(request.get('/admin/roles'))
export const getLogs = (params) => data(request.get('/admin/logs', { params }))
export const getKnowledgeEngine = () => data(request.get('/admin/knowledge-engine'))
export const updateKnowledgeEngine = (engine) => data(request.put('/admin/knowledge-engine', { engine }))

/* ==================== 会议模块 ==================== */
export const createMeeting = form => data(request.post('/meetings', form))
export const getMeetings = () => data(request.get('/meetings'))
export const getMeeting = id => data(request.get(`/meetings/${id}`))
export const deleteMeeting = id => data(request.delete(`/meetings/${id}`))
export const generateMeetingSummary = (id, fileIds) => data(request.post(`/meetings/${id}/summary`, fileIds?.length ? { file_ids: fileIds } : {}))
export const getMeetingSummary = id => data(request.get(`/meetings/${id}/summary`))
export const getMeetingActions = id => data(request.get(`/meetings/${id}/actions`))
/** 纪要文档直传 Dify（pdf/md/docx/txt），返回 { dify_file_id, file_name } */
export const uploadMeetingFile = file => {
  const formData = new FormData()
  formData.append('file', file)
  return data(request.post('/meetings/files', formData, { headers: { 'Content-Type': 'multipart/form-data' } }))
}
export const pushMeetingToDingtalk = (id) => data(request.post(`/meetings/${id}/push`))

export const transcribeAudio = file => {
  const formData = new FormData()
  formData.append('file', file)
  return data(request.post('/meetings/transcribe', formData, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 300000 }))
}


/* ==================== 通知中心 ==================== */
export const getNotifications = () => data(request.get('/notifications'))
export const getUnreadCount = () => data(request.get('/notifications/unread-count'))
export const markNotificationRead = (id) => data(request.put(`/notifications/${id}/read`))
export const markAllNotificationsRead = () => data(request.put('/notifications/read-all'))

/* ==================== bash 安全管控 ==================== */
export const execBashCommand = (command) => data(request.post('/security/bash-exec', { command }))

/* ==================== 用户选项（待办转任务选人） ==================== */
export const getUserOptions = () => data(request.get('/users/options'))

/* ==================== 知识库 ==================== */
export const uploadDocument = (file, category) => {
  const fd = new FormData(); fd.append('file', file); fd.append('category', category || '未分类');
  return data(request.post('/knowledge/documents', fd, { headers: { 'Content-Type': 'multipart/form-data' } }))
}
export const getDocuments = () => data(request.get('/knowledge/documents'))
export const deleteDocument = (id) => data(request.delete(`/knowledge/documents/${id}`))
export const rebuildIndex = () => data(request.post('/knowledge/rebuild-index'))
export const searchKnowledge = (query) => data(request.post('/knowledge/search', { query }))

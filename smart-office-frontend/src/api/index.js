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
 * @param {object} payload { sessionId, message }
 * @param {object} handlers { onSession, onMessage, onDone, onError }
 */
export async function streamChat(payload, handlers) {
  const token = localStorage.getItem('so_token')
  const resp = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload)
  })
  if (!resp.ok) {
    handlers.onError?.(new Error('对话请求失败：' + resp.status))
    return
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
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
}

export default request

/** 统一取响应体中的 data 字段 */
const data = (p) => p.then((body) => body.data)

/* ==================== 认证 ==================== */
export const login = (form) => data(request.post('/auth/login', form))
export const logout = () => data(request.post('/auth/logout'))
export const getMe = () => data(request.get('/auth/me'))

/* ==================== 智能体 ==================== */
export const getAgents = () => data(request.get('/agents'))
export const createAgent = (form) => data(request.post('/agents', form))
export const updateAgent = (id, form) => data(request.put(`/agents/${id}`, form))
export const deleteAgent = (id) => data(request.delete(`/agents/${id}`))

/* ==================== AI 会话 ==================== */
export const createSession = (form) => data(request.post('/ai/sessions', form))
export const getSessions = () => data(request.get('/ai/sessions'))
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

/* ==================== 会议模块 ==================== */
export const createMeeting = form => data(request.post('/meetings', form))
export const getMeetings = () => data(request.get('/meetings'))
export const getMeeting = id => data(request.get(`/meetings/${id}`))
export const deleteMeeting = id => data(request.delete(`/meetings/${id}`))
export const generateMeetingSummary = id => data(request.post(`/meetings/${id}/summary`))
export const getMeetingSummary = id => data(request.get(`/meetings/${id}/summary`))
export const getMeetingActions = id => data(request.get(`/meetings/${id}/actions`))
export const updateActionStatus = (actionId, form) => data(request.put(`/meetings/actions/${actionId}`, form))
export const uploadMeetingAudio = formData => data(request.post('/meetings/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } }))


/* ==================== 知识库 ==================== */
export const uploadDocument = (form) => {
  if (form instanceof FormData) {
    return data(request.post('/knowledge/documents', form, { headers: { 'Content-Type': 'multipart/form-data' } }))
  }
  return data(request.post('/knowledge/documents', form))
}
export const getDocuments = () => data(request.get('/knowledge/documents'))
export const deleteDocument = (id) => data(request.delete(`/knowledge/documents/${id}`))
export const searchKnowledge = (query) => data(request.post('/knowledge/search', { query }))

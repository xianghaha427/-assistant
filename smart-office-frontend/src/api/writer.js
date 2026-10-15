import axios from 'axios'

/**
 * 创作工具 API（办公写作助手 + PPT大纲生成）
 * 独立 axios 实例 raw：绕开全局 30s timeout，用于 PPT 生成（blocking 长耗时）与 blob 下载
 */
const raw = axios.create({
  baseURL: '/api',
  timeout: 240000
})

raw.interceptors.request.use((config) => {
  const token = localStorage.getItem('so_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 办公写作助手 SSE 流式生成（POST /api/writer/generate）
 * @param {object} payload { type: weekly|email|notice, points, plan?, recipient?, scene?, subject?, tone? }
 * @param {object} handlers { onSource, onMessage, onDone, onError }
 * @param {AbortSignal} [signal] AbortController 的 signal，组件卸载/页面切换时主动中断
 */
export async function writerGenerate(payload, handlers, signal) {
  const token = localStorage.getItem('so_token')
  let resp
  try {
    resp = await fetch('/api/writer/generate', {
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
    if (e?.name !== 'AbortError') handlers.onError?.(e)
    return
  }
  if (!resp.ok) {
    let msg = '生成请求失败：' + resp.status
    try {
      const body = await resp.json()
      if (body && body.msg) msg = body.msg
    } catch (e) { /* ignore */ }
    handlers.onError?.(new Error(msg))
    return
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let done = false
  try {
    while (true) {
      const { done: eof, value } = await reader.read()
      if (eof) break
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
        if (eventName === 'source') handlers.onSource?.(data)
        else if (eventName === 'message') handlers.onMessage?.(data)
        else if (eventName === 'done') {
          done = true
          handlers.onDone?.()
        }
      }
    }
    if (!done) handlers.onDone?.()
  } catch (e) {
    // 流读取中被 abort（页面切换/组件卸载）静默忽略
    if (e?.name !== 'AbortError') handlers.onError?.(e)
  }
}

export async function sendWriterMessage(payload) {
  const resp = await raw.post('/writer/send', payload)
  const body = resp.data
  if (!body || body.code !== 0) throw new Error(body?.msg || '发送失败')
  return body.data
}

/**
 * PPT大纲生成（blocking，返回 { id, title, pages, source, style }）
 */
export async function pptGenerate(topic, style) {
  const resp = await raw.post('/ppt/generate', { topic, style })
  const body = resp.data
  if (!body || body.code !== 0) {
    throw new Error(body?.msg || 'PPT 生成失败')
  }
  return body.data
}

/**
 * 下载已生成的 .pptx（blob 请求，避开 JSON 拦截与全局 timeout）
 */
export async function pptDownload(id, filename = '演示文稿.pptx') {
  const resp = await raw.get(`/ppt/download/${id}`, { responseType: 'blob' })
  const url = URL.createObjectURL(resp.data)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

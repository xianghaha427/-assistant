import request from './index.js'

/** 统一取响应体中的 data 字段 */
const data = (p) => p.then((body) => body.data)

/** 今日简报：未生成时返回字段为 null 的空结构 { brief_date, id, title, content, status, create_time } */
export const getTodayBriefing = () => data(request.get('/briefing/today'))

/** 历史简报分页：params = { page, size }，返回 { total, records } */
export const getBriefingHistory = (params) => data(request.get('/briefing/history', { params }))

/**
 * 手动触发生成今日简报（后端异步执行）
 * force=false（默认）：当日已生成 success 时同步返回 { alreadyExists:true, triggered:false }
 * force=true：强制重新生成并覆盖当日记录，返回 { triggered:true }
 */
export const generateBriefingNow = (force = false) =>
  data(request.post('/briefing/generate-now', null, { params: { force } }))

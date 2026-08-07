import request from './index'

/** 统一取响应体中的 data 字段 */
const data = (p) => p.then((body) => body.data)

/* ==================== 数据看板（/api/stats） ==================== */
/** 指标总览：meeting_count / summary_count / ai_usage_count / task_* / kb_doc_count / briefing_count */
export const getStatsOverview = () => data(request.get('/stats/overview'))
/** 近7天趋势：dates / ai_usage / meetings */
export const getStatsTrend = () => data(request.get('/stats/trend'))

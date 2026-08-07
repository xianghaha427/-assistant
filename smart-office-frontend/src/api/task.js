import request from './index.js'

// 任务模块 API（后端 /api/tasks）
const data = (p) => p.then((body) => body.data)

/** 看板分组查询：返回 pending / in_progress / done 三列 */
export const getTaskBoard = () => data(request.get('/tasks/board'))

/** 新建任务 */
export const createTask = (form) => data(request.post('/tasks', form))

/** 更新任务（状态推进/字段修改） */
export const updateTask = (id, form) => data(request.put(`/tasks/${id}`, form))

/** 删除任务 */
export const deleteTask = (id) => data(request.delete(`/tasks/${id}`))

/** 会议待办转任务（assigneeId：前端弹窗所选执行人，必填） */
export const convertFromAction = (actionItemId, assigneeId) => data(request.post('/tasks/from-action', { action_item_id: actionItemId, assignee_id: assigneeId }))

<template>
  <div class="task-board-page" v-loading="loading">
    <!-- ====== 页头 ====== -->
    <div class="board-header">
      <div>
        <h1 class="page-title">任务看板</h1>
        <div class="page-subtitle">会议待办与日常任务的跟进闭环 · 共 {{ totalCount }} 项</div>
      </div>
      <el-button type="primary" @click="openCreate">＋ 新建任务</el-button>
    </div>

    <!-- ====== 三列看板 ====== -->
    <div class="board-columns">
      <div class="board-column" v-for="col in columns" :key="col.key">
        <div class="column-header">
          <span class="column-dot" :class="col.key"></span>
          <span class="column-name">{{ col.label }}</span>
          <span class="column-count">{{ (board[col.key] || []).length }}</span>
        </div>

        <div class="column-body">
          <div v-if="!(board[col.key] || []).length" class="column-empty"><Columns3 :size="24" /><span>暂无任务</span></div>

          <div class="task-card card" v-for="t in board[col.key]" :key="t.id">
            <div class="task-title">{{ t.title }}</div>
            <div class="task-desc" v-if="t.description">{{ t.description }}</div>

            <div class="task-tags">
              <span class="priority-tag" :class="t.priority">{{ PRIORITY_MAP[t.priority] || '中' }}</span>
              <span v-if="t.source_meeting_id" class="source-tag"><Calendar :size="12" /> 来自会议</span>
            </div>

            <div class="task-footer">
              <span class="task-assignee">👤 {{ t.assignee_name || '未指派' }}</span>
              <span class="task-deadline" :class="{ overdue: isOverdue(t) }">
                {{ t.deadline ? formatDate(t.deadline) : '无截止' }}
              </span>
            </div>

            <div class="task-actions">
              <el-button v-if="col.key === 'pending'" size="small" type="primary" plain @click="advance(t, 'in_progress')">
                开始处理
              </el-button>
              <el-button v-else-if="col.key === 'in_progress'" size="small" type="success" plain @click="advance(t, 'done')">
                标记完成
              </el-button>
              <el-button v-else size="small" plain @click="advance(t, 'pending')">
                重新打开
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ====== 新建任务弹窗 ====== -->
    <el-dialog v-model="createVisible" title="新建任务" width="480px">
      <el-form :model="form" label-width="72px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="请输入任务标题" maxlength="100" show-overflow-tooltip />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="任务描述（可选）"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio-button value="high">高</el-radio-button>
            <el-radio-button value="medium">中</el-radio-button>
            <el-radio-button value="low">低</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="form.deadline"
            type="date"
            placeholder="选择截止日期（可选）"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getTaskBoard, createTask, updateTask } from '../../api/task.js'
import { Columns3, Calendar } from 'lucide-vue-next'

const columns = [
  { key: 'pending', label: '待办' },
  { key: 'in_progress', label: '进行中' },
  { key: 'done', label: '已完成' }
]
const PRIORITY_MAP = { high: '高优先级', medium: '中', low: '低' }

const board = ref({ pending: [], in_progress: [], done: [] })
const loading = ref(false)
const createVisible = ref(false)
const creating = ref(false)
const form = ref({ title: '', description: '', priority: 'medium', deadline: null })

const totalCount = computed(() =>
  columns.reduce((sum, c) => sum + (board.value[c.key] || []).length, 0)
)

onMounted(() => loadBoard())

async function loadBoard() {
  loading.value = true
  try {
    const data = await getTaskBoard()
    board.value = {
      pending: data.pending || [],
      in_progress: data.in_progress || [],
      done: data.done || []
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/** 状态推进 */
async function advance(t, status) {
  try {
    await updateTask(t.id, { status })
    ElMessage.success(status === 'done' ? '任务已完成' : status === 'in_progress' ? '已开始处理' : '已重新打开')
    await loadBoard()
  } catch (e) {
    // 拦截器已提示（如无权限）
  }
}

function openCreate() {
  form.value = { title: '', description: '', priority: 'medium', deadline: null }
  createVisible.value = true
}

async function handleCreate() {
  if (!form.value.title || !form.value.title.trim()) {
    ElMessage.warning('请输入任务标题')
    return
  }
  creating.value = true
  try {
    await createTask({
      title: form.value.title.trim(),
      description: form.value.description || null,
      priority: form.value.priority,
      deadline: form.value.deadline ? toLocalIso(form.value.deadline) : null
    })
    ElMessage.success('任务已创建')
    createVisible.value = false
    await loadBoard()
  } catch (e) {
    // 拦截器已提示
  } finally {
    creating.value = false
  }
}

/** Date → 本地零点 ISO 字符串（后端 LocalDateTime 反序列化格式） */
function toLocalIso(d) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T00:00:00`
}

function formatDate(t) {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return String(t).slice(0, 10)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function isOverdue(t) {
  if (!t.deadline || t.status === 'done') return false
  return new Date(t.deadline).getTime() < Date.now()
}
</script>

<style scoped>
.task-board-page { max-width: 1280px; margin: 0 auto; }

/* ====== 页头 ====== */
.board-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 20px;
}

/* ====== 三列布局 ====== */
.board-columns {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  align-items: start;
}

.board-column {
  background: var(--surface-hover);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 12px;
  min-height: 320px;
}

.column-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 12px;
}
.column-dot { width: 8px; height: 8px; border-radius: 50%; }
.column-dot.pending { background: var(--text-tertiary); }
.column-dot.in_progress { background: var(--accent); box-shadow: 0 0 6px rgba(8, 180, 188, 0.6); }
.column-dot.done { background: #10B981; box-shadow: 0 0 6px rgba(16, 185, 129, 0.5); }
.column-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-primary);
}
.column-count {
  font-size: 11.5px;
  color: var(--text-tertiary);
  background: var(--surface);
  border: 1px solid var(--border-light);
  padding: 1px 8px;
  border-radius: 999px;
}

.column-body { display: flex; flex-direction: column; gap: 10px; }
.column-empty {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  color: var(--text-tertiary); font-size: 13px; padding: 48px 0;
}

/* ====== 任务卡片 ====== */
.task-card {
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s ease;
}
.task-card:hover { transform: translateY(-1px); box-shadow: var(--shadow-md); }
.task-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.task-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.5;
  word-break: break-word;
}
.task-desc {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}

.task-tags {
  display: flex;
  gap: 6px;
  margin-top: 10px;
  flex-wrap: wrap;
}
.priority-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid;
}
.priority-tag.high {
  color: #E5484D;
  border-color: rgba(229, 72, 77, 0.3);
  background: rgba(229, 72, 77, 0.08);
}
.priority-tag.medium {
  color: var(--accent);
  border-color: rgba(8, 180, 188, 0.3);
  background: var(--accent-soft);
}
.priority-tag.low {
  color: var(--text-tertiary);
  border-color: var(--border);
  background: var(--surface-hover);
}
.source-tag {
  font-size: 11px;
  color: var(--accent);
  background: rgba(8, 180, 188, 0.08);
  border: 1px solid rgba(8, 180, 188, 0.25);
  padding: 2px 8px;
  border-radius: 999px;
}

.task-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-secondary);
}
.task-deadline.overdue { color: #E5484D; font-weight: 600; }

.task-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .board-columns { grid-template-columns: 1fr; }
}
</style>

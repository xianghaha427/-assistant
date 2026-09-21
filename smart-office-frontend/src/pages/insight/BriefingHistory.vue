<template>
  <div class="briefing-page">
    <!-- ====== 标题区 ====== -->
    <div class="briefing-header">
      <div class="briefing-title-wrap">
        <div class="briefing-icon">
          <Newspaper :size="20" />
        </div>
        <div>
          <h1 class="page-title">行业简报</h1>
          <p class="page-subtitle">AI 每日自动生成金融行业简报，掌握市场动态、监管趋势与风险提示</p>
        </div>
      </div>
      <el-button type="primary" :loading="generating" :disabled="generating" @click="generateNow">
        <Sparkles v-if="!generating" :size="16" class="gen-btn-icon" />
        {{ generating ? '生成中…' : '立即生成今日简报' }}
      </el-button>
    </div>

    <!-- ====== 历史列表 ====== -->
    <div class="list-card card">
      <el-table
        v-loading="loading"
        :data="records"
        class="briefing-table"
        @row-click="openDetail"
      >
        <el-table-column prop="brief_date" label="日期" width="130" />
        <el-table-column prop="title" label="标题" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="row-title">{{ row.title || '（无标题）' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.create_time) }}</template>
        </el-table-column>
        <template #empty>
          <div class="table-empty">
            <Newspaper :size="36" color="var(--border-strong)" />
            <span>暂无简报记录</span>
            <span style="font-size:12px;color:var(--text-tertiary)">点击右上角「立即生成今日简报」</span>
          </div>
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          background
          layout="prev, pager, next, total"
          :total="total"
          :page-size="pageSize"
          @current-change="loadHistory"
        />
      </div>
    </div>

    <!-- ====== 全文弹窗（Markdown 渲染） ====== -->
    <el-dialog v-model="detailVisible" :title="detailTitle" width="960px" top="2vh">
      <div class="briefing-content md-body" v-html="renderedContent"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Newspaper, Sparkles } from 'lucide-vue-next'
import { getBriefingHistory, getTodayBriefing, generateBriefingNow } from '../../api/briefing.js'
import { renderMd } from '../../utils/markdown.js'

const loading = ref(false)
const generating = ref(false)
const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10

// 全文弹窗
const detailVisible = ref(false)
const detailTitle = ref('')
const detailContent = ref('')

/** Markdown 轻量渲染（标题/加粗/斜体/行内代码/列表/表格/换行），共用工具见 src/utils/markdown.js */
const renderedContent = computed(() => renderMd(detailContent.value))

// 轮询定时器（触发生成后轮询 /today）
let pollTimer = null

/** 加载历史分页 */
async function loadHistory() {
  loading.value = true
  try {
    const res = await getBriefingHistory({ page: page.value, size: pageSize })
    records.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/** 打开全文弹窗 */
function openDetail(row) {
  detailTitle.value = row.title || '行业简报'
  detailContent.value = row.content || '（该简报生成失败，无内容）'
  detailVisible.value = true
}

/**
 * 立即生成今日简报（预检二选一）：
 * 1. 先查 /today：当日已有成功简报 → ElMessageBox 二选一
 *    【重新生成】（confirm，warning 样式）→ force=true 覆盖生成
 *    【查看已有简报】（cancel）→ 直接打开详情弹窗
 *    关闭弹窗（close）→ 不做任何操作
 * 2. 当日无成功简报 → 直接触发生成（force=false）
 */
async function generateNow() {
  if (generating.value) return

  // 预检：当日是否已有成功简报
  let today = null
  try {
    today = await getTodayBriefing()
  } catch (e) {
    // 拦截器已提示
    return
  }
  const exists = !!(today && today.status === 'success' && today.content)

  if (!exists) {
    startGenerate(false, null)
    return
  }

  // 当日已有成功简报：弹窗二选一
  try {
    await ElMessageBox.confirm(
      `今日简报已于 ${fmtTime(today.create_time)} 生成，重新生成将覆盖现有内容。`,
      '今日简报已生成',
      {
        confirmButtonText: '重新生成',
        cancelButtonText: '查看已有简报',
        type: 'warning',
        distinguishCancelAndClose: true,
        confirmButtonClass: 'el-button--warning',
      }
    )
    // confirm：重新生成（覆盖）
    startGenerate(true, today.create_time)
  } catch (action) {
    // cancel：查看已有简报；close（Esc/X）：不做任何操作
    if (action === 'cancel') {
      openDetail(today)
    }
  }
}

/**
 * 触发生成并轮询 /today 等待结果：
 * - force=true：覆盖重新生成，后端完成后 create_time 刷新，以 create_time 变化判定完成
 * - force=false：首次生成，出现 status=success 且有内容即完成
 * 每 2 秒轮询一次，最长约 3 分钟
 */
async function startGenerate(force, prevCreateTime) {
  generating.value = true
  try {
    await generateBriefingNow(force)
    ElMessage.info(force ? '已触发重新生成，正在等待结果…' : '已触发简报生成，正在等待结果…')
    let attempts = 0
    stopPolling()
    pollTimer = setInterval(async () => {
      attempts++
      try {
        const t = await getTodayBriefing()
        if (t && t.status === 'success' && t.content) {
          // 覆盖重新生成：必须等 create_time 变化才算完成（避免误判为旧内容）
          const done = !force || (prevCreateTime && String(t.create_time) !== String(prevCreateTime))
          if (done) {
            stopPolling()
            generating.value = false
            ElMessage.success(force ? '已重新生成今日简报' : '今日简报已生成')
            page.value = 1
            loadHistory()
            return
          }
        }
      } catch (e) { /* 轮询失败忽略，继续重试 */ }
      if (attempts >= 90) {
        stopPolling()
        generating.value = false
        ElMessage.warning('生成耗时较长，请稍后刷新页面查看')
      }
    }, 2000)
  } catch (e) {
    generating.value = false
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 后端 ISO 时间（2026-08-06T08:00:00）→ 展示为 yyyy-MM-dd HH:mm:ss */
function fmtTime(t) {
  if (!t) return '--'
  return String(t).replace('T', ' ').slice(0, 19)
}

onMounted(loadHistory)
onBeforeUnmount(stopPolling)
</script>

<style scoped>
.briefing-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1280px;
  margin: 0 auto;
}

/* ====== 标题区 ====== */
.briefing-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.briefing-title-wrap {
  display: flex;
  align-items: center;
  gap: 14px;
}
.briefing-icon {
  width: 46px; height: 46px;
  flex-shrink: 0;
  border-radius: 13px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 22px;
  display: flex; align-items: center; justify-content: center;
}
.briefing-page .page-title { margin: 0; font-size: 20px; }
.briefing-page .page-subtitle { margin-top: 2px; }
.gen-btn-icon { margin-right: 6px; }

/* ====== 列表卡片 ====== */
.list-card { min-height: calc(100vh - 200px); padding: 8px 12px 12px; display: flex; flex-direction: column; }
.list-card :deep(.el-table) { flex: 1; }
.list-card .pager { margin-top: auto; }
.briefing-table { cursor: pointer; }
.row-title {
  color: var(--text-primary);
  font-weight: 500;
}
.table-empty {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 48px 0; font-size: 13px; color: var(--text-tertiary);
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 6px 2px;
}

/* ====== 全文弹窗 ====== */
.briefing-content {
  margin: 0;
  padding: 20px 24px;
  max-height: 78vh;
  min-height: 50vh;
  overflow-y: auto;
  border-radius: 10px;
  background: var(--surface);
  border: 1px solid var(--border-light);
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--text-secondary);
  word-break: break-word;
}
/* Markdown 排版（跟随主题令牌，不新增色相） */
.briefing-content :deep(h1),
.briefing-content :deep(h2),
.briefing-content :deep(h3),
.briefing-content :deep(h4),
.briefing-content :deep(h5),
.briefing-content :deep(h6) {
  color: var(--text-primary);
  font-weight: 650;
  margin: 18px 0 8px;
  line-height: 1.4;
}
.briefing-content :deep(h1) { font-size: 19px; }
.briefing-content :deep(h2) { font-size: 17px; }
.briefing-content :deep(h3) { font-size: 15px; }
.briefing-content :deep(h4),
.briefing-content :deep(h5),
.briefing-content :deep(h6) { font-size: 14px; }
.briefing-content :deep(p) { margin: 6px 0; }
.briefing-content :deep(ul),
.briefing-content :deep(ol) { margin: 6px 0; padding-left: 22px; }
.briefing-content :deep(li) { margin: 3px 0; }
.briefing-content :deep(strong) { color: var(--text-primary); font-weight: 600; }
.briefing-content :deep(code) {
  background: var(--surface-hover);
  border: 1px solid var(--border-light);
  border-radius: 5px;
  padding: 1px 5px;
  font-size: 12.5px;
}
.briefing-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--border-light);
  margin: 14px 0;
}
.briefing-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 13px;
}
.briefing-content :deep(th),
.briefing-content :deep(td) {
  border: 1px solid var(--border);
  padding: 7px 10px;
  text-align: left;
}
.briefing-content :deep(th) {
  background: var(--surface-hover);
  color: var(--text-primary);
  font-weight: 600;
}
</style>

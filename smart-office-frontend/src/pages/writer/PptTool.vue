<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pptGenerate, pptDownload } from '../../api/writer'

const topic = ref('')
const style = ref('正式商务')
const generating = ref(false)
const downloading = ref(false)
/** 生成完成后的记录 { id, title, pages, source } */
const record = ref(null)

/** 生成进度提示（Dify workflow 为 blocking 调用，耗时较长，用轮播文案反馈进度） */
const progressText = ref('')
const progressSteps = [
  '正在调用大模型策划 PPT 大纲…',
  '正在逐页生成内容要点…',
  '正在组装结构化大纲…',
  '正在渲染 .pptx 文件…'
]
let progressTimer = null

function startProgress() {
  let i = 0
  progressText.value = progressSteps[0]
  progressTimer = setInterval(() => {
    i = (i + 1) % progressSteps.length
    progressText.value = progressSteps[i]
  }, 4000)
}

function stopProgress() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

async function submit() {
  if (generating.value) return
  if (!topic.value.trim()) {
    ElMessage.warning('请输入 PPT 主题')
    return
  }
  generating.value = true
  record.value = null
  startProgress()
  try {
    record.value = await pptGenerate(topic.value.trim(), style.value)
    ElMessage.success('PPT 生成完成，可下载文件')
  } catch (e) {
    ElMessage.error(e?.message || 'PPT 生成失败，请稍后重试')
  } finally {
    generating.value = false
    stopProgress()
  }
}

async function download() {
  if (!record.value || downloading.value) return
  downloading.value = true
  try {
    await pptDownload(record.value.id, `${record.value.title || '演示文稿'}.pptx`)
  } catch (e) {
    ElMessage.error('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

onBeforeUnmount(stopProgress)
</script>

<template>
  <div class="ppt-page">
    <header class="page-head">
      <h2 class="page-title">PPT大纲生成</h2>
      <p class="page-subtitle">输入汇报主题，自动生成结构化 PPT 并渲染为可下载的 .pptx 文件（含演讲备注）</p>
    </header>

    <!-- 主题输入 -->
    <section class="card ppt-input-card">
      <el-form @submit.prevent>
        <el-form-item label="PPT 主题">
          <el-input
            v-model="topic"
            placeholder="例如：2026年上半年零售业务经营分析汇报 / 新员工信息安全培训课件"
            clearable
            :disabled="generating"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item label="生成风格">
          <el-select v-model="style" :disabled="generating" class="style-select">
            <el-option label="正式商务" value="正式商务" />
            <el-option label="简洁现代" value="简洁现代" />
            <el-option label="数据驱动" value="数据驱动" />
          </el-select>
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="generating" @click="submit">
          {{ generating ? '生成中…' : '生成 PPT' }}
        </el-button>
      </el-form>
    </section>

    <!-- 生成进度态 -->
    <section v-if="generating" class="card ppt-progress-card">
      <div class="progress-icon">
        <span class="spinner"></span>
      </div>
      <div class="progress-text">{{ progressText }}</div>
      <div class="progress-tip">Dify 工作流逐页生成内容，通常需要 30-90 秒，请耐心等待</div>
    </section>

    <!-- 完成后下载卡片 -->
    <section v-else-if="record" class="card ppt-result-card">
      <div class="file-icon">PPT</div>
      <div class="file-info">
        <div class="file-title">{{ record.title }}.pptx</div>
        <div class="file-meta">
          <span>共 {{ record.pages }} 页</span>
          <span class="dot">·</span>
          <span>含演讲备注</span>
          <span class="dot">·</span>
          <span>{{ record.style || style }} 风格</span>
        </div>
      </div>
      <el-button type="primary" :loading="downloading" @click="download">
        {{ downloading ? '下载中…' : '下载 .pptx' }}
      </el-button>
    </section>

    <!-- 空态引导 -->
    <section v-else class="card ppt-empty-card">
      <div class="empty-steps">
        <div class="step"><span class="step-num">1</span>输入汇报主题</div>
        <div class="step"><span class="step-num">2</span>AI 自动策划大纲并逐页生成内容</div>
        <div class="step"><span class="step-num">3</span>渲染为真实 .pptx 文件，一键下载即用</div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.ppt-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 860px;
}
.page-head .page-title { margin: 0; }
.submit-btn { width: 100%; }
.style-select { width: 220px; }

/* 进度态 */
.ppt-progress-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 36px 20px;
}
.progress-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent-soft);
}
.spinner {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 3px solid var(--border);
  border-top-color: var(--accent);
  animation: spin 0.9s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.progress-text {
  font-size: 14px;
  font-weight: 550;
  color: var(--text-primary);
}
.progress-tip {
  font-size: 12px;
  color: var(--text-tertiary);
}

/* 下载卡片 */
.ppt-result-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px;
}
.file-icon {
  width: 54px;
  height: 54px;
  flex: none;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.04em;
  background: var(--grad-brand);
  box-shadow: var(--glow-brand);
}
.file-info { flex: 1; min-width: 0; }
.file-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}
.file-meta .dot { color: var(--text-tertiary); }

/* 空态引导 */
.ppt-empty-card { padding: 26px; }
.empty-steps {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.step {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--text-secondary);
}
.step-num {
  width: 22px;
  height: 22px;
  flex: none;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
  background: var(--accent-soft);
}
</style>

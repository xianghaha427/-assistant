<template>
  <div class="meeting-create">
    <!-- ====== 顶部标题 ====== -->
    <div class="create-header">
      <button class="back-btn" @click="$router.back()">
        ← 返回
      </button>
      <h2 class="page-title">新建会议</h2>
      <p class="page-subtitle">粘贴转写文本或上传纪要文档，AI 将自动提取议题、决议与待办</p>
    </div>

    <!-- ====== 表单卡片 ====== -->
    <div class="form-card card">
      <!-- 会议标题 -->
      <div class="form-group">
        <label class="form-label">会议标题 <span class="required">*</span></label>
        <el-input
          v-model="form.title"
          placeholder="例如：产品周会 2026-08-05"
          size="large"
        />
      </div>

      <!-- 输入方式 Tab -->
      <div class="form-group">
        <label class="form-label">会议内容</label>
        <el-tabs v-model="activeTab" class="create-tabs">
          <el-tab-pane label="文本输入" name="text">
            <el-input
              v-model="form.raw_text"
              type="textarea"
              :rows="12"
              placeholder="请粘贴会议转写文本...&#10;&#10;支持粘贴从飞书、钉钉、腾讯会议等工具导出的转写内容，AI 将自动识别发言人并提取关键信息。"
              class="text-area"
            />
          </el-tab-pane>
          <el-tab-pane label="录音转写" name="audio">
            <div class="audio-section">
              <el-upload
                class="audio-upload"
                drag
                :auto-upload="false"
                :show-file-list="false"
                accept=".mp3,.wav,.m4a,.ogg"
                :on-change="handleAudioChange"
                :disabled="transcribing"
              >
                <div class="upload-inner">
                  <div class="upload-icon">{{ audioFile ? '🎵' : '🎙️' }}</div>
                  <div class="upload-title" v-if="!audioFile">
                    拖拽会议录音到此处，或 <em>点击上传</em>
                  </div>
                  <div class="upload-title" v-else>
                    已选择：<em>{{ audioFile.name }}</em>
                  </div>
                  <div class="upload-hint" v-if="!audioFile">
                    支持 MP3 / WAV / M4A / OGG，建议时长 &lt; 30 分钟
                  </div>
                  <div class="upload-hint" v-else>
                    {{ formatSize(audioFile.size) }} · 点击"开始转写"生成文本
                  </div>
                </div>
              </el-upload>
              <div v-if="audioFile" class="audio-actions">
                <el-button
                  type="primary"
                  :loading="transcribing"
                  @click="handleTranscribe"
                >
                  {{ transcribing ? '转写中（可能需要几分钟）...' : '开始转写' }}
                </el-button>
                <el-button @click="audioFile = null">重新选择</el-button>
              </div>
              <div v-if="transcribing" class="transcribe-progress">
                <div class="gen-spinner" style="width:24px;height:24px;margin:0 8px 0 0;display:inline-block;vertical-align:middle;"></div>
                <span>AI 正在识别语音内容，请耐心等待...</span>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="文档上传" name="file">
            <el-upload
              ref="uploadRef"
              class="audio-upload"
              drag
              multiple
              :auto-upload="false"
              :show-file-list="false"
              accept=".pdf,.md,.docx,.txt"
              :on-change="handleFileChange"
            >
              <div class="upload-inner">
                <div class="upload-icon">📄</div>
                <div class="upload-title">拖拽会议纪要文档到此处，或 <em>点击上传</em></div>
                <div class="upload-hint">支持 PDF / MD / DOCX / TXT，系统会先提取文字并填入文本输入框</div>
              </div>
            </el-upload>
            <div v-if="docFiles.length" class="doc-list">
              <div v-for="(f, i) in docFiles" :key="f.name + i" class="file-info">
                <span class="file-icon">📄</span>
                <span class="file-name">{{ f.name }}</span>
                <span class="file-size">{{ formatSize(f.size) }}</span>
                <button class="file-remove" @click="removeDocFile(i)">移除</button>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 提交按钮 -->
      <div class="form-actions">
        <el-button @click="$router.back()">取消</el-button>
        <el-button
          type="primary"
          size="large"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="handleSubmit"
        >
          <span v-if="!submitting">✓ 开始生成</span>
          <span v-else>生成中...</span>
        </el-button>
      </div>
    </div>

    <!-- ====== 全屏 Loading ====== -->
    <Teleport to="body">
      <div v-if="generating" class="gen-overlay">
        <div class="gen-card card">
          <div class="gen-spinner"></div>
          <div class="gen-title">AI 正在分析会议内容...</div>
          <div class="gen-desc">正在识别发言人、提取议题与决议</div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createMeeting, generateSummary } from '../../api/meeting.js'
import { transcribeAudio, extractMeetingFile } from '../../api/index.js'

const router = useRouter()

const form = ref({
  title: '',
  raw_text: '',
  source_type: 'text'
})
const activeTab = ref('text')
const docFiles = ref([])
const audioFile = ref(null)
const transcribing = ref(false)
const extractingDocument = ref(false)
const submitting = ref(false)
const generating = ref(false)

const canSubmit = computed(() => {
  if (!form.value.title.trim()) return false
  return !extractingDocument.value && form.value.raw_text.trim().length > 0
})

async function handleFileChange(file) {
  if (!file?.raw) return
  if (docFiles.value.some(f => f.name === file.raw.name)) {
    ElMessage.warning('该文件已添加')
    return
  }
  docFiles.value.push(file.raw)
  extractingDocument.value = true
  try {
    const result = await extractMeetingFile(file.raw)
    const extracted = result?.text?.trim()
    if (!extracted) {
      ElMessage.warning(`文档 ${file.raw.name} 未识别出文字`)
      return
    }
    const prefix = form.value.raw_text.trim() ? `\n\n===== ${file.raw.name} =====\n\n` : ''
    form.value.raw_text = form.value.raw_text.trim() + prefix + extracted
    activeTab.value = 'text'
    ElMessage.success(`已识别 ${file.raw.name}，共 ${extracted.length} 字并填入文本框`)
  } catch (e) {
    ElMessage.error(`文档 ${file.raw.name} 解析失败，请检查文件内容`)
  } finally {
    extractingDocument.value = false
  }
}

function removeDocFile(i) {
  docFiles.value.splice(i, 1)
}

// 与前端 nginx client_max_body_size / 后端 spring.servlet.multipart.max-request-size 保持一致
const MAX_AUDIO_SIZE = 50 * 1024 * 1024

function handleAudioChange(file) {
  if (!file?.raw) return
  if (file.raw.size > MAX_AUDIO_SIZE) {
    ElMessage.error(`文件过大（当前 ${formatSize(file.raw.size)}，上限 ${formatSize(MAX_AUDIO_SIZE)}），请压缩后重试`)
    audioFile.value = null
    return
  }
  audioFile.value = file.raw
}

async function handleTranscribe() {
  if (!audioFile.value) return
  transcribing.value = true
  try {
    const result = await transcribeAudio(audioFile.value)
    if (result?.text) {
      form.value.raw_text = result.text
      activeTab.value = 'text'
      ElMessage.success(`转写完成，共 ${result.text.length} 字，已自动填入文本区`)
    } else {
      ElMessage.error('转写结果为空，请重试')
    }
  } catch (e) {
    console.error('转写失败详情:', e)
    const msg = e?.response?.data?.msg || e?.message || '语音转写失败，请检查网络和音频文件后重试'
    ElMessage.error(msg)
  } finally {
    transcribing.value = false
  }
}

function formatSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function handleSubmit() {
  if (!canSubmit.value) return
  submitting.value = true

  try {
    const payload = {
      title: form.value.title.trim(),
      source_type: 'text',
      raw_text: form.value.raw_text
    }

    // 创建会议
    const meeting = await createMeeting(payload)
    generating.value = true

    // 触发 AI 生成纪要（仅文件无文字时后端自动以「摘要」为 query）
    try {
      await generateSummary(meeting.id)
      // 纪要为异步生成：不提前报"成功"，进度由详情页轮询展示
      ElMessage.info('纪要正在生成，请到会议详情查看进度')
      router.push(`/meeting/${meeting.id}`)
    } catch (e) {
      // 即使触发失败也跳转到详情，用户可重试
      ElMessage.warning('会议已创建，纪要可到详情页重新生成')
      router.push(`/meeting/${meeting.id}`)
    }
  } catch (e) {
    ElMessage.error('创建会议失败')
  } finally {
    submitting.value = false
    generating.value = false
  }
}
</script>

<style scoped>
.meeting-create { max-width: 800px; margin: 0 auto; }

.create-header {
  margin-bottom: 24px;
}
.back-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  padding: 0;
  margin-bottom: 12px;
  transition: color 0.15s;
}
.back-btn:hover { color: var(--accent); }

.form-card {
  padding: 28px 32px;
}

.form-group {
  margin-bottom: 22px;
}
.form-label {
  display: block;
  font-size: 13.5px;
  font-weight: 550;
  color: var(--text-primary);
  margin-bottom: 8px;
}
.required { color: #f56c6c; }

/* Tabs 样式微调 */
.create-tabs :deep(.el-tabs__nav-wrap::after) {
  background: var(--border-light);
}

/* 文本域 */
.text-area :deep(.el-textarea__inner) {
  font-size: 13.5px;
  line-height: 1.7;
  resize: none;
}

/* 上传区 */
.audio-upload :deep(.el-upload-dragger) {
  background: rgba(255, 255, 255, 0.02);
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-md);
  padding: 40px 20px;
  transition: all 0.2s ease;
}
.audio-upload :deep(.el-upload-dragger:hover) {
  border-color: var(--accent);
  background: var(--accent-soft);
}
.upload-inner { text-align: center; }
.upload-icon { font-size: 36px; margin-bottom: 12px; }
.upload-title {
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.upload-title em {
  color: var(--accent);
  font-style: normal;
}
.upload-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 14px;
  background: var(--accent-soft);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  font-size: 13px;
}
.file-icon { font-size: 16px; }
.file-name {
  color: var(--text-primary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-size { color: var(--text-tertiary); font-size: 12px; }

.doc-list { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }
.file-remove {
  background: none;
  border: none;
  color: #f56c6c;
  font-size: 12px;
  cursor: pointer;
  padding: 0 2px;
}
.file-remove:hover { text-decoration: underline; }

/* 录音转写 */
.audio-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.audio-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}
.transcribe-progress {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  background: var(--accent-soft);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--accent);
}

/* 操作按钮 */
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

/* ====== 全屏 Loading ====== */
.gen-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(7, 11, 20, 0.85);
  backdrop-filter: blur(8px);
}
.gen-card {
  text-align: center;
  padding: 48px 56px;
}
.gen-spinner {
  width: 56px; height: 56px;
  margin: 0 auto 20px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.1);
  border-top-color: var(--accent);
  border-right-color: var(--accent-2);
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.gen-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}
.gen-desc {
  font-size: 13px;
  color: var(--text-secondary);
}
</style>

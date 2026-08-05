<template>
  <div class="meeting-create">
    <!-- ====== 椤堕儴鏍囬 ====== -->
    <div class="create-header">
      <button class="back-btn" @click="$router.back()">
        鈫?杩斿洖
      </button>
      <h2 class="page-title">鏂板缓<span class="grad-text">浼氳</span></h2>
      <p class="page-subtitle">绮樿创杞啓鏂囨湰鎴栦笂浼犻煶棰戯紝AI 灏嗚嚜鍔ㄦ彁鍙栬棰樸€佸喅璁笌寰呭姙</p>
    </div>

    <!-- ====== 琛ㄥ崟鍗＄墖 ====== -->
    <div class="form-card card">
      <!-- 浼氳鏍囬 -->
      <div class="form-group">
        <label class="form-label">浼氳鏍囬 <span class="required">*</span></label>
        <el-input
          v-model="form.title"
          placeholder="渚嬪锛氫骇鍝佸懆浼?2026-08-05"
          size="large"
        />
      </div>

      <!-- 杈撳叆鏂瑰紡 Tab -->
      <div class="form-group">
        <label class="form-label">浼氳鍐呭</label>
        <el-tabs v-model="activeTab" class="create-tabs">
          <el-tab-pane label="鏂囨湰杈撳叆" name="text">
            <el-input
              v-model="form.raw_text"
              type="textarea"
              :rows="12"
              placeholder="请粘贴会议转写文本...&#10;&#10;支持粘贴从飞书、钉钉、腾讯会议等工具导出的转写内容，AI 将自动识别发言人并提取关键信息。"
              class="text-area"
            />
          </el-tab-pane>
          <el-tab-pane label="闊抽涓婁紶" name="audio">
            <el-upload
              ref="uploadRef"
              class="audio-upload"
              drag
              :auto-upload="false"
              :limit="1"
              accept=".mp3,.wav,.m4a,audio/*"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
            >
              <div class="upload-inner">
                <div class="upload-icon">馃帣</div>
                <div class="upload-title">鎷栨嫿闊抽鏂囦欢鍒版澶勶紝鎴?<em>鐐瑰嚮涓婁紶</em></div>
                <div class="upload-hint">鏀寔 MP3 / WAV / M4A 鏍煎紡</div>
              </div>
            </el-upload>
            <div v-if="audioFile" class="file-info">
              <span class="file-icon">馃幍</span>
              <span class="file-name">{{ audioFile.name }}</span>
              <span class="file-size">{{ formatSize(audioFile.size) }}</span>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 鎻愪氦鎸夐挳 -->
      <div class="form-actions">
        <el-button @click="$router.back()">鍙栨秷</el-button>
        <el-button
          type="primary"
          size="large"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="handleSubmit"
        >
          <span v-if="!submitting">鉁?寮€濮嬬敓鎴愮</span>
          <span v-else>鐢熸垚涓?..</span>
        </el-button>
      </div>
    </div>

    <!-- ====== 鍏ㄥ睆 Loading ====== -->
    <Teleport to="body">
      <div v-if="generating" class="gen-overlay">
        <div class="gen-card card">
          <div class="gen-spinner"></div>
          <div class="gen-title">AI 姝ｅ湪鍒嗘瀽浼氳鍐呭...</div>
          <div class="gen-desc">姝ｅ湪璇嗗埆鍙戣█浜恒€佹彁鍙栬棰樹笌鍐宠</div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createMeeting, generateSummary, uploadAudio } from '../../api/meeting.js'

const router = useRouter()

const form = ref({
  title: '',
  raw_text: '',
  source_type: 'text'
})
const activeTab = ref('text')
const audioFile = ref(null)
const submitting = ref(false)
const generating = ref(false)

const canSubmit = computed(() => {
  if (!form.value.title.trim()) return false
  if (activeTab.value === 'text') return form.value.raw_text.trim().length > 0
  return !!audioFile.value
})

function handleFileChange(file) {
  audioFile.value = file.raw
  form.value.source_type = 'audio'
}

function handleFileRemove() {
  audioFile.value = null
  form.value.source_type = 'text'
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
    let payload = {
      title: form.value.title.trim(),
      source_type: activeTab.value
    }

    // 闊抽涓婁紶
    if (activeTab.value === 'audio' && audioFile.value) {
      generating.value = true
      try {
        const uploadResult = await uploadAudio(audioFile.value)
        payload.raw_text = uploadResult?.raw_text || ''
      } catch (e) {
        ElMessage.error('闊抽涓婁紶澶辫触')
        return
      }
    } else {
      payload.raw_text = form.value.raw_text
    }

    // 鍒涘缓浼氳
    const meeting = await createMeeting(payload)
    generating.value = true

    // 瑙﹀彂 AI 鐢熸垚绾
    try {
      await generateSummary(meeting.id)
      ElMessage.success('绾鐢熸垚鎴愬姛')
      router.push(`/meeting/${meeting.id}`)
    } catch (e) {
      // 鍗充娇鐢熸垚澶辫触涔熻烦杞埌璇︽儏锛岀敤鎴峰彲閲嶈瘯
      ElMessage.warning('纪要生成中，请稍后查看')
      router.push(`/meeting/${meeting.id}`)
    }
  } catch (e) {
    ElMessage.error('鍒涘缓浼氳澶辫触')
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

/* Tabs 鏍峰紡寰皟 */
.create-tabs :deep(.el-tabs__nav-wrap::after) {
  background: var(--border-light);
}

/* 鏂囨湰鍩?*/
.text-area :deep(.el-textarea__inner) {
  font-size: 13.5px;
  line-height: 1.7;
  resize: none;
}

/* 涓婁紶鍖?*/
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
  border: 1px solid rgba(77, 141, 255, 0.2);
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

/* 鎿嶄綔鎸夐挳 */
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

/* ====== 鍏ㄥ睆 Loading ====== */
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

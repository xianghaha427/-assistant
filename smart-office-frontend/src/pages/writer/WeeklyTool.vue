<script setup>
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useWriter } from '../../composables/useWriter'

const { result, loading, source, sourceLabels, generate, copyResult } = useWriter('weekly')

const form = reactive({
  points: '',
  plan: '',
  tone: '正式简练'
})

function submit() {
  if (!form.points.trim()) {
    ElMessage.warning('请填写本周工作要点')
    return
  }
  generate({ ...form })
}
</script>

<template>
  <div class="writer-page">
    <header class="page-head">
      <h2 class="page-title">智能周报</h2>
      <p class="page-subtitle">填写本周工作要点，按「本周总结 / 下周计划 / 风险协调」结构一键生成规范周报</p>
    </header>

    <div class="writer-body">
      <!-- 左：表单 -->
      <section class="card writer-form">
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="本周工作要点" required>
            <el-input
              v-model="form.points"
              type="textarea"
              :rows="6"
              placeholder="例如：完成客户系统上线；处理客户工单 120 个；参与两次需求评审……建议包含量化数据"
            />
          </el-form-item>
          <el-form-item label="下周工作计划（可选）">
            <el-input
              v-model="form.plan"
              type="textarea"
              :rows="3"
              placeholder="不填时将根据本周工作合理推导延续性计划"
            />
          </el-form-item>
          <el-form-item label="语气">
            <el-select v-model="form.tone" style="width: 100%">
              <el-option label="正式简练" value="正式简练" />
              <el-option label="平实朴素" value="平实朴素" />
              <el-option label="突出数据成果" value="突出数据成果" />
            </el-select>
          </el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="submit">
            {{ loading ? '生成中…' : '生成周报' }}
          </el-button>
        </el-form>
      </section>

      <!-- 右：流式结果 -->
      <section class="card writer-result">
        <div class="result-head">
          <span class="result-title">生成结果</span>
          <div class="result-actions">
            <el-tag v-if="source" size="small" effect="plain">{{ sourceLabels[source] || source }}</el-tag>
            <el-tag v-if="loading" size="small" type="info" effect="plain">流式生成中…</el-tag>
            <el-button size="small" :disabled="!result || loading" @click="copyResult">复制</el-button>
          </div>
        </div>
        <pre v-if="result" class="result-text">{{ result }}<span v-if="loading" class="caret"></span></pre>
        <div v-else class="result-empty">
          <span class="empty-dot"></span>
          {{ loading ? '正在生成周报，请稍候…' : '填写左侧表单后点击「生成周报」，结果将在此处流式输出' }}
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.writer-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-head .page-title { margin: 0; }
.writer-body {
  display: grid;
  grid-template-columns: 390px 1fr;
  gap: 16px;
  align-items: start;
}
@media (max-width: 1100px) {
  .writer-body { grid-template-columns: 1fr; }
}
.submit-btn { width: 100%; }

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.result-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.result-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.result-text {
  margin: 0;
  min-height: 320px;
  max-height: 620px;
  overflow: auto;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  background: var(--bg);
  color: var(--text-primary);
  font-family: inherit;
  font-size: 13.5px;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
}
.result-empty {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-tertiary);
  font-size: 13px;
}
.empty-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  opacity: 0.7;
}
.caret {
  display: inline-block;
  width: 7px;
  height: 14px;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--accent);
  animation: caret-blink 0.9s steps(1) infinite;
}
@keyframes caret-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>

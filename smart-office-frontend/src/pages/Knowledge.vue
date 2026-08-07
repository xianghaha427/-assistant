<template>
  <div>
    <!-- Hero 区域 -->
    <div class="knowledge-hero">
      <div>
        <h2 class="page-title">知识库<span class="grad-text"> 问答</span></h2>
        <div class="page-subtitle">企业制度、流程文档智能问答 · 知识库内容由管理员在 Dify 侧统一维护</div>
      </div>
    </div>

    <!-- 知识问答区域（主界面） -->
    <div class="qa-section card">
      <div class="qa-header">
        <h3 class="qa-title">💡 知识问答</h3>
        <div class="qa-subtitle">基于企业知识库内容智能回答您的问题</div>
      </div>

      <!-- 空状态引导 -->
      <div v-if="qaHistory.length === 0" class="qa-empty">
        <div class="qa-empty-icon">📚</div>
        <div class="qa-empty-text">输入问题开始提问，例如：公司的报销流程是什么？</div>
      </div>

      <div class="qa-messages" v-else>
        <div v-for="(item, idx) in qaHistory" :key="idx" class="qa-item" :class="item.role">
          <div class="qa-bubble">
            <div class="qa-content">{{ item.content }}</div>
            <div v-if="item.source === 'mock'" class="qa-source-tag">演示文案（Dify 服务暂不可用）</div>
            <div v-else-if="item.source === 'native'" class="qa-source-tag qa-source-native">由 FinPilot 自研引擎回答（数据不出域）</div>
            <div v-else-if="item.role === 'assistant'" class="qa-source-tag qa-source-dify">由 Dify 企业知识问答助手回答</div>
          </div>
        </div>
      </div>

      <div class="qa-input">
        <el-input
          v-model="qaQuery"
          placeholder="输入您的问题，例如：公司的报销流程是什么？"
          @keyup.enter="handleSearch"
          :disabled="searching"
        />
        <el-button type="primary" @click="handleSearch" :loading="searching">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { searchKnowledge } from '../api'

// 问答相关
const qaQuery = ref('')
const qaHistory = ref([])
const searching = ref(false)

async function handleSearch() {
  if (!qaQuery.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  searching.value = true
  const query = qaQuery.value
  qaHistory.value.push({ role: 'user', content: query })
  qaQuery.value = ''
  try {
    const result = await searchKnowledge(query)
    qaHistory.value.push({
      role: 'assistant',
      content: result.answer,
      source: result.source || 'dify'
    })
  } catch (e) {
    qaHistory.value.push({
      role: 'assistant',
      content: '抱歉，查询失败，请稍后重试。',
      source: 'error'
    })
  } finally {
    searching.value = false
  }
}
</script>

<style scoped>
.knowledge-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

/* 知识问答区域 */
.qa-section {
  min-height: 480px;
  display: flex;
  flex-direction: column;
}
.qa-header {
  margin-bottom: 18px;
}
.qa-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}
.qa-subtitle {
  font-size: 13px;
  color: var(--text-secondary);
}

/* 空状态引导 */
.qa-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--text-secondary);
}
.qa-empty-icon { font-size: 48px; margin-bottom: 12px; }
.qa-empty-text { font-size: 14px; }

.qa-messages {
  flex: 1;
  max-height: 520px;
  overflow-y: auto;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.qa-item {
  display: flex;
}
.qa-item.user {
  justify-content: flex-end;
}
.qa-item.assistant {
  justify-content: flex-start;
}

.qa-bubble {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  font-size: 14px;
  line-height: 1.6;
}
.qa-item.user .qa-bubble {
  background: var(--grad-brand);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.qa-item.assistant .qa-bubble {
  background: var(--surface-hover);
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  border-bottom-left-radius: 4px;
}

/* 回答来源标签 */
.qa-source-tag {
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--text-tertiary);
}
.qa-source-dify {
  color: var(--accent);
}
.qa-source-native {
  color: var(--accent);
}

.qa-input {
  display: flex;
  gap: 10px;
  margin-top: auto;
}
.qa-input .el-input {
  flex: 1;
}
</style>

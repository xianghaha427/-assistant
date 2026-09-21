<template>
  <div class="insight-page">
    <!-- ====== 标题区 ====== -->
    <div class="insight-header">
      <div class="insight-title-wrap">
        <div class="insight-icon">
          <TrendingUp :size="22" />
        </div>
        <div>
          <h1 class="page-title">行业洞察</h1>
          <p class="page-subtitle">基于行业知识库的智能问答，洞察金融行业动态与监管趋势</p>
        </div>
      </div>
    </div>

    <!-- ====== 对话组件（固定绑定行业洞察智能体 agentId=3） ====== -->
    <AiChat :agent-id="3" class="insight-chat" />
  </div>
</template>

<script setup>
import AiChat from '../AiChat.vue'
import { TrendingUp } from 'lucide-vue-next'
</script>

<style scoped>
.insight-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.insight-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.insight-title-wrap {
  display: flex;
  align-items: center;
  gap: 14px;
}
.insight-icon {
  width: 46px; height: 46px;
  flex-shrink: 0;
  border-radius: 13px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 22px;
  display: flex; align-items: center; justify-content: center;
}
.insight-page .page-title { margin: 0; font-size: 20px; }
.insight-page .page-subtitle { margin-top: 2px; }

/* 组件内 .ai-chat 高度按内容区自适应 */
.insight-chat { height: calc(100vh - 200px); }
</style>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta.title || '功能开发中')
const icon = computed(() => route.meta.icon || 'MagicStick')
</script>

<template>
  <div class="placeholder-wrap">
    <div class="icon">
      <el-icon><component :is="icon" /></el-icon>
    </div>
    <div class="ph-title">{{ title }}</div>
    <div class="ph-desc">该模块正在建设中，敬请期待</div>
    <div class="ph-badge">
      <span class="ph-dot"></span> IN DEVELOPMENT
    </div>
  </div>
</template>

<style scoped>
.ph-title {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}
.ph-desc {
  font-size: 13px;
  color: var(--text-secondary);
}
.ph-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-top: 10px;
  padding: 5px 14px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--text-tertiary);
}
.ph-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 8px rgba(8, 180, 188, 0.9);
  animation: ph-breathe 1.8s ease-in-out infinite;
}
@keyframes ph-breathe {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.35; }
}
</style>

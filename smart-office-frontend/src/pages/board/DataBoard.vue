<template>
  <div class="board-page" v-loading="loading">
    <!-- ====== 页头 ====== -->
    <div class="board-header">
      <div>
        <h2 class="page-title">数据看板</h2>
        <div class="page-subtitle">平台运营指标总览 · 数据按你的角色权限范围展示</div>
      </div>
      <el-button class="refresh-btn" circle @click="loadAll"><RefreshCw :size="16" /></el-button>
    </div>

    <!-- ====== 指标卡行 ====== -->
    <div class="stat-cards">
      <div class="stat-card" v-for="c in statCards" :key="c.label">
        <div class="stat-icon" :style="{ background: c.color }"><component :is="c.icon" :size="18" /></div>
        <div class="stat-info">
          <div class="stat-value">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </div>
      </div>
    </div>

    <!-- ====== 图表区 ====== -->
    <div class="chart-grid">
      <div class="chart-card wide">
        <div class="chart-title">近 7 天使用趋势</div>
        <div ref="trendRef" class="chart-body"></div>
        <div v-if="noTrend" class="chart-empty">近 7 天暂无使用记录</div>
      </div>
      <div class="chart-card">
        <div class="chart-title">任务状态分布</div>
        <div ref="pieRef" class="chart-body"></div>
        <div v-if="noTask" class="chart-empty">暂无任务数据</div>
      </div>
      <div class="chart-card">
        <div class="chart-title">模块使用统计</div>
        <div ref="barRef" class="chart-body"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { Calendar, FileText, Sparkles, CheckCircle2, Library, RefreshCw } from 'lucide-vue-next'
import { getStatsOverview, getStatsTrend } from '../../api/board'

/* ====== echarts 按需引入（避免全量打包） ====== */
import * as echarts from 'echarts/core'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([LineChart, PieChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

/* ====== 图表公共样式 ====== */
const AXIS_COLOR = 'rgba(0, 0, 0, 0.12)'
const SPLIT_COLOR = 'rgba(0, 0, 0, 0.06)'
const TEXT_COLOR = '#5F656D'
const PALETTE = ['#0F5B3C', '#3C8565', '#B7791F', '#C53030', '#7B8794']
const tooltipStyle = {
  backgroundColor: 'rgba(255, 255, 255, 0.96)',
  borderColor: '#DCEBDD',
  textStyle: { color: '#1F2937', fontSize: 12 }
}

/* ====== 数据 ====== */
const loading = ref(false)
const overview = ref({})
const trend = ref({ dates: [], ai_usage: [], meetings: [] })

const trendRef = ref(null)
const pieRef = ref(null)
const barRef = ref(null)
let trendChart = null
let pieChart = null
let barChart = null

const statCards = computed(() => [
  { label: '会议总数', value: overview.value.meeting_count ?? '-', icon: Calendar, color: '#0F5B3C' },
  { label: '纪要生成数', value: overview.value.summary_count ?? '-', icon: FileText, color: '#0F5B3C' },
  { label: 'AI 使用次数', value: overview.value.ai_usage_count ?? '-', icon: Sparkles, color: '#0F5B3C' },
  { label: '任务完成率', value: overview.value.task_total ? (overview.value.task_completion_rate + '%') : '-', icon: CheckCircle2, color: '#0F5B3C' },
  { label: '知识文档数', value: overview.value.kb_doc_count ?? '-', icon: Library, color: '#0F5B3C' }
])

const noTrend = computed(() =>
  trend.value.ai_usage.every(v => !v) && trend.value.meetings.every(v => !v))
const noTask = computed(() => !(overview.value.task_total > 0))

/* ====== 加载 ====== */
async function loadAll() {
  loading.value = true
  try {
    const [ovRes, trRes] = await Promise.allSettled([getStatsOverview(), getStatsTrend()])
    if (ovRes.status === 'fulfilled' && ovRes.value) overview.value = ovRes.value
    if (trRes.status === 'fulfilled' && trRes.value) trend.value = trRes.value
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

/* ====== 图表渲染 ====== */
function renderCharts() {
  renderTrend()
  renderPie()
  renderBar()
}

function renderTrend() {
  if (!trendRef.value) return
  trendChart?.dispose()
  trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    color: PALETTE,
    tooltip: { trigger: 'axis', ...tooltipStyle },
    legend: {
      data: ['AI 使用次数', '会议数'],
      textStyle: { color: TEXT_COLOR, fontSize: 12 },
      top: 0, right: 0, itemWidth: 14, itemHeight: 8
    },
    grid: { left: 8, right: 12, top: 34, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      data: trend.value.dates.map(d => d.slice(5)),
      axisLine: { lineStyle: { color: AXIS_COLOR } },
      axisLabel: { color: TEXT_COLOR, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: SPLIT_COLOR } },
      axisLabel: { color: TEXT_COLOR, fontSize: 11 }
    },
    series: [
      {
        name: 'AI 使用次数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: trend.value.ai_usage,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(15, 91, 60, 0.18)' },
            { offset: 1, color: 'rgba(15, 91, 60, 0)' }
          ])
        }
      },
      {
        name: '会议数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: trend.value.meetings
      }
    ]
  })
}

function renderPie() {
  if (!pieRef.value) return
  pieChart?.dispose()
  pieChart = echarts.init(pieRef.value)
  const ov = overview.value
  pieChart.setOption({
    color: ['#B7791F', '#0F5B3C', '#7BAF96'],
    tooltip: { trigger: 'item', ...tooltipStyle },
    legend: {
      bottom: 0,
      textStyle: { color: TEXT_COLOR, fontSize: 12 },
      itemWidth: 12, itemHeight: 8
    },
    series: [{
      type: 'pie',
      radius: ['48%', '70%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#FFFFFF', borderWidth: 2 },
      label: { color: TEXT_COLOR, fontSize: 11 },
      data: [
        { name: '待处理', value: ov.task_pending || 0 },
        { name: '进行中', value: ov.task_in_progress || 0 },
        { name: '已完成', value: ov.task_done || 0 }
      ]
    }]
  })
}

function renderBar() {
  if (!barRef.value) return
  barChart?.dispose()
  barChart = echarts.init(barRef.value)
  const ov = overview.value
  barChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle },
    grid: { left: 8, right: 12, top: 18, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      data: ['会议', '纪要', 'AI对话', '任务', '知识文档', '简报'],
      axisLine: { lineStyle: { color: AXIS_COLOR } },
      axisLabel: { color: TEXT_COLOR, fontSize: 11, interval: 0 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: SPLIT_COLOR } },
      axisLabel: { color: TEXT_COLOR, fontSize: 11 }
    },
    series: [{
      type: 'bar',
      barWidth: 22,
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: '#0F5B3C'
      },
      data: [
        ov.meeting_count || 0,
        ov.summary_count || 0,
        ov.ai_usage_count || 0,
        ov.task_total || 0,
        ov.kb_doc_count || 0,
        ov.briefing_count || 0
      ]
    }]
  })
}

/* ====== 生命周期 ====== */
function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
  barChart?.resize()
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
  barChart?.dispose()
})
</script>

<style scoped>
.board-page {
  padding-bottom: 8px;
}
.board-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
.refresh-btn { opacity: 0.85; }

/* ====== 指标卡 ====== */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 16px 18px;
  border-radius: var(--radius-md);
  background: var(--surface);
  border: 1px solid var(--border-light);
  transition: background .18s ease, border-color .18s ease;
}
.stat-card:hover {
  background: var(--accent-tint);
  border-color: var(--accent-light);
}
.stat-icon {
  width: 44px; height: 44px;
  flex-shrink: 0;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-primary);
  line-height: 1.2;
}
.stat-label {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-secondary);
}

/* ====== 图表区 ====== */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.chart-card {
  position: relative;
  padding: 16px 18px;
  border-radius: var(--radius-md);
  background: var(--surface);
  border: 1px solid var(--border-light);
}
.chart-card.wide {
  grid-column: 1 / -1;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.chart-body {
  width: 100%;
  height: 260px;
}
.chart-card.wide .chart-body { height: 280px; }
.chart-empty {
  position: absolute;
  inset: 44px 18px 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: var(--text-tertiary);
  background: rgba(0, 0, 0, 0.03);
  border-radius: 8px;
  pointer-events: none;
}

@media (max-width: 1100px) {
  .stat-cards { grid-template-columns: repeat(2, 1fr); }
  .chart-grid { grid-template-columns: 1fr; }
}
</style>

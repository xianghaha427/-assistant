<template>
  <div class="security-page">
    <div class="page-header">
      <div class="title-block">
        <div class="eyebrow"><ShieldCheck :size="14" /> PLATFORM SECURITY</div>
        <div class="title-line">
          <div class="hero-icon"><ShieldCheck :size="25" /></div>
          <div>
            <h2>平台安全运营中心</h2>
            <p class="page-desc">守护知识库、会议附件与 AI 服务的稳定运行。</p>
          </div>
        </div>
        <div class="hero-meta"><span class="online-dot"></span>系统运行正常 <i></i> 最近检查可追溯</div>
      </div>
      <div class="header-actions">
        <span class="admin-badge"><LockKeyhole :size="14" /> 管理员专属</span>
        <el-button class="refresh-button" :loading="loading" @click="loadChecks"><RefreshCw :size="15" />刷新检查</el-button>
      </div>
    </div>

    <div class="summary-row">
      <div class="summary-item summary-primary"><div class="summary-icon"><ClipboardCheck :size="18" /></div><div><span>安全检查项</span><strong>{{ checks.length }}</strong></div><small>固定诊断</small></div>
      <div class="summary-item"><div class="summary-icon"><Activity :size="18" /></div><div><span>最近状态</span><strong class="status-text">{{ overallLabel }}</strong></div><small class="status-mark"><span></span>实时</small></div>
      <div class="summary-item"><div class="summary-icon"><History :size="18" /></div><div><span>审计记录</span><strong>{{ auditLogs.length }}</strong></div><small>可追溯</small></div>
    </div>

    <div class="content-row">
      <section class="panel checks-panel">
        <div class="panel-title-row">
          <div>
            <div class="section-heading"><div class="section-icon"><ScanSearch :size="18" /></div><div><h3>办公业务健康检查</h3><p class="panel-hint">选择检查项查看当前运行状态与处理建议。</p></div></div>
          </div>
        </div>
        <div class="check-grid">
          <button v-for="(check, index) in checks" :key="check.id" class="check-card"
                  :class="{ selected: selectedCheck?.id === check.id }"
                  :disabled="checking" @click="runCheck(check)">
            <div class="check-card-head">
              <div class="check-title"><span class="check-number">0{{ index + 1 }}</span><strong>{{ check.name }}</strong></div>
              <el-tag :type="riskType(checkRisk(check))" size="small" effect="plain">{{ riskLabel(checkRisk(check)) }}</el-tag>
            </div>
            <span>{{ check.description }}</span>
            <small><span class="impact-dot"></span>{{ checkImpact(check) }}<ArrowUpRight :size="13" /></small>
          </button>
        </div>

        <div v-if="selectedCheck && result" class="result-panel" :class="resultOk ? 'result-pass' : 'result-warn'">
          <div class="result-icon"><CircleCheck v-if="resultOk" :size="24" /><CircleAlert v-else :size="24" /></div>
          <div class="result-main">
            <div class="result-title">{{ resultOk ? '检查通过' : '需要关注' }}：{{ selectedCheck.name }}</div>
            <div class="result-reason">{{ result.reason }}</div>
            <div class="result-meta">退出码：<code>{{ result.exitCode }}</code>　建议：{{ selectedCheck.suggestion }}</div>
            <pre v-if="result.stdout" class="stdout">{{ result.stdout }}</pre>
            <pre v-if="result.stderr" class="stderr">{{ result.stderr }}</pre>
          </div>
        </div>
      </section>

      <section class="panel audit-panel">
        <div class="panel-title-row">
          <div class="section-heading"><div class="section-icon audit-icon"><History :size="18" /></div><div><h3>最近安全审计</h3><p class="panel-hint">记录管理员检查动作与执行结果。</p></div></div>
          <el-button text class="audit-refresh" :loading="auditLoading" @click="loadAudit"><RefreshCw :size="14" />刷新</el-button>
        </div>
        <el-table :data="auditLogs" size="small" max-height="430" empty-text="暂无安全检查记录">
          <el-table-column prop="action" label="动作" width="120" />
          <el-table-column prop="username" label="用户" width="90" />
          <el-table-column prop="create_time" label="时间" min-width="150" />
          <el-table-column label="结果" min-width="160">
            <template #default="{ row }"><span class="audit-detail">{{ row.detail }}</span></template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <section class="panel architecture-panel">
      <div class="panel-title-row"><div class="section-heading"><div class="section-icon flow-icon"><Route :size="18" /></div><div><h3>安全运营闭环</h3><p class="panel-hint">从发现问题到留下处理证据。</p></div></div><el-tag type="success" effect="plain">管理员专属</el-tag></div>
      <div class="flow-row">
        <div class="flow-step"><span>1</span><strong>固定检查</strong><small>知识库、附件、磁盘、进程</small></div>
        <div class="flow-arrow">→</div>
        <div class="flow-step"><span>2</span><strong>业务影响</strong><small>关联问答、会议和 AI 服务</small></div>
        <div class="flow-arrow">→</div>
        <div class="flow-step"><span>3</span><strong>风险建议</strong><small>给出排查和处理方向</small></div>
        <div class="flow-arrow">→</div>
        <div class="flow-step"><span>4</span><strong>审计留痕</strong><small>记录操作者、结果和时间</small></div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Activity, ArrowUpRight, CircleAlert, CircleCheck, ClipboardCheck, History, LockKeyhole, RefreshCw, Route, ScanSearch, ShieldCheck } from 'lucide-vue-next'
import { getBashPresets, executeBashPreset, getBashAudit } from '../../api/index.js'

const loading = ref(false)
const checking = ref(false)
const auditLoading = ref(false)
const checks = ref([])
const auditLogs = ref([])
const selectedCheck = ref(null)
const result = ref(null)

const resultOk = computed(() => Boolean(result.value?.allowed && !result.value?.timedOut && result.value?.exitCode === 0))
const overallType = computed(() => checks.value.some(c => c.lastResult) ? 'success' : 'info')
const overallLabel = computed(() => overallType.value === 'success' ? '已完成检查' : '待执行检查')

function riskType(level) { return level === 'medium' || level === 'high' ? 'warning' : 'success' }
function riskLabel(level) { return level === 'high' ? '高风险' : level === 'medium' ? '中风险' : '低风险' }
function checkRisk(check) { return check.riskLevel || check.risk_level || 'low' }
function checkImpact(check) { return check.businessImpact || check.business_impact || '平台运行稳定性' }

async function loadChecks() {
  loading.value = true
  try {
    const items = await getBashPresets()
    checks.value = items.map(item => ({
      ...item,
      riskLevel: item.riskLevel || item.risk_level || 'low',
      businessImpact: item.businessImpact || item.business_impact || '平台运行稳定性',
      suggestion: item.suggestion || '查看服务日志并记录处理结果'
    }))
  } catch (e) { ElMessage.error('安全检查项加载失败') }
  finally { loading.value = false }
}

async function runCheck(check) {
  checking.value = true
  selectedCheck.value = check
  result.value = null
  try {
    result.value = await executeBashPreset(check.id)
    check.lastResult = result.value
    await loadAudit()
  } catch (e) { ElMessage.error('安全检查执行失败') }
  finally { checking.value = false }
}

async function loadAudit() {
  auditLoading.value = true
  try { auditLogs.value = await getBashAudit(30) } catch (e) { ElMessage.error('审计记录加载失败') }
  finally { auditLoading.value = false }
}

onMounted(async () => { await Promise.all([loadChecks(), loadAudit()]) })
</script>

<style scoped>
.security-page { max-width: 1180px; margin: 0 auto; color: var(--text-primary); }
.page-header, .panel-title-row { display: flex; justify-content: space-between; align-items: center; gap: 18px; }
.page-header { position: relative; margin-bottom: 22px; padding: 25px 28px; overflow: hidden; border: 1px solid rgba(111, 175, 123, .22); border-radius: 14px; background: linear-gradient(120deg, rgba(232, 246, 235, .95), rgba(248, 252, 249, .98) 65%, rgba(238, 247, 240, .96)); }
.page-header::after { content: ""; position: absolute; right: -70px; top: -95px; width: 250px; height: 250px; border: 1px solid rgba(111, 175, 123, .2); border-radius: 50%; box-shadow: 0 0 0 24px rgba(111, 175, 123, .05), 0 0 0 48px rgba(111, 175, 123, .035); pointer-events: none; }
.title-block, .header-actions { position: relative; z-index: 1; }
.eyebrow { display: flex; align-items: center; gap: 6px; margin-bottom: 10px; color: #5b9167; font-size: 10px; font-weight: 700; letter-spacing: .13em; }
.title-line { display: flex; align-items: center; gap: 12px; }
.hero-icon, .section-icon, .summary-icon { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 auto; color: var(--accent); background: rgba(111, 175, 123, .13); }
.hero-icon { width: 46px; height: 46px; border-radius: 12px; }
.page-header h2 { margin: 0 0 5px; font-size: 24px; letter-spacing: -.01em; color: var(--text-primary); }
.page-desc, .panel-hint { margin: 0; color: var(--text-tertiary); font-size: 13px; line-height: 1.55; }
.hero-meta { display: flex; align-items: center; gap: 7px; margin: 14px 0 0 58px; color: #66806c; font-size: 12px; }
.hero-meta i { width: 3px; height: 3px; border-radius: 50%; background: #b1c5b5; }
.online-dot, .status-mark span { width: 7px; height: 7px; border-radius: 50%; background: #53a967; box-shadow: 0 0 0 3px rgba(83, 169, 103, .13); }
.header-actions { display: flex; align-items: center; gap: 12px; }
.admin-badge { display: inline-flex; align-items: center; gap: 6px; padding: 7px 10px; border: 1px solid rgba(111, 175, 123, .25); border-radius: 7px; color: #5b9167; background: rgba(255, 255, 255, .65); font-size: 12px; }
.refresh-button { border-color: rgba(111, 175, 123, .35); background: #fff; color: #4f8c5d; }
.summary-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 18px; }
.summary-item, .panel { background: var(--surface); border: 1px solid var(--border-light); border-radius: 8px; }
.summary-item { display: flex; align-items: center; gap: 11px; min-height: 77px; padding: 13px 16px; color: var(--text-secondary); font-size: 12px; }
.summary-icon { width: 36px; height: 36px; border-radius: 10px; }
.summary-item div:nth-child(2) { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.summary-item strong { color: var(--text-primary); font-size: 21px; line-height: 1; }
.summary-item .status-text { font-size: 15px; }
.summary-item small { margin-left: auto; align-self: flex-start; color: var(--text-tertiary); font-size: 11px; white-space: nowrap; }
.status-mark { display: inline-flex; align-items: center; gap: 5px; color: #569761 !important; }
.content-row { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(360px, 1fr); gap: 18px; margin-bottom: 18px; }
.panel { padding: 21px; }
.panel h3 { margin: 0 0 4px; font-size: 16px; color: var(--text-primary); }
.section-heading { display: flex; align-items: center; gap: 10px; }
.section-icon { width: 34px; height: 34px; border-radius: 9px; }
.audit-icon { color: #6f8ea4; background: rgba(111, 142, 164, .12); }
.flow-icon { color: #8678ad; background: rgba(134, 120, 173, .12); }
.check-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 11px; margin-top: 20px; }
.check-card { position: relative; min-height: 125px; padding: 15px; border: 1px solid var(--border-light); border-radius: 8px; background: #fff; color: var(--text-primary); text-align: left; cursor: pointer; transition: border-color .2s, background .2s; }
.check-card::before { content: ""; position: absolute; left: 0; top: 14px; bottom: 14px; width: 3px; border-radius: 0 3px 3px 0; background: #9bc5a2; opacity: .8; }
.check-card:hover, .check-card.selected { border-color: var(--accent-light); background: var(--accent-tint); }
.check-card.selected::before { background: var(--accent); }
.check-card:disabled { cursor: wait; opacity: .7; }
.check-card-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
.check-title { display: flex; align-items: center; gap: 8px; min-width: 0; }
.check-title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.check-number { color: #9bb3a0; font-family: monospace; font-size: 11px; }
.check-card span, .check-card small { display: block; color: var(--text-secondary); line-height: 1.5; }
.check-card small { display: flex; align-items: center; gap: 5px; margin-top: 11px; color: var(--text-tertiary); font-size: 11px; }
.check-card small svg { margin-left: auto; color: #8cae92; }
.impact-dot { width: 5px; height: 5px; flex: 0 0 auto; border-radius: 50%; background: #88b491; }
.result-panel { display: flex; gap: 12px; margin-top: 16px; padding: 14px; border-radius: var(--radius-sm); }
.result-pass { background: rgba(45, 157, 110, .08); border: 1px solid rgba(45, 157, 110, .22); color: #278a61; }
.result-warn { background: rgba(230, 162, 60, .1); border: 1px solid rgba(230, 162, 60, .25); color: #a96e16; }
.result-main { min-width: 0; flex: 1; }
.result-title { color: var(--text-primary); font-weight: 600; margin-bottom: 4px; }
.result-reason, .result-meta { color: var(--text-secondary); font-size: 12px; line-height: 1.6; }
.result-meta { margin-top: 5px; }
.result-meta code { padding: 1px 5px; background: var(--bg); border-radius: 3px; }
.stdout, .stderr { max-height: 180px; overflow: auto; white-space: pre-wrap; margin: 10px 0 0; padding: 9px 11px; border-radius: 5px; font-size: 12px; line-height: 1.5; }
.stdout { background: #1b2330; color: #dce7f4; }
.stderr { background: #352321; color: #f3c6bc; }
.audit-panel { min-width: 0; }
.audit-refresh { display: inline-flex; align-items: center; gap: 5px; color: var(--accent); }
.audit-panel :deep(.el-table) { background: transparent; }
.audit-panel :deep(.el-table th.el-table__cell) { background: #f7faf8; color: var(--text-tertiary); font-weight: 600; }
.audit-panel :deep(.el-table td.el-table__cell), .audit-panel :deep(.el-table th.el-table__cell) { border-bottom-color: #edf2ee; }
.audit-panel :deep(.el-table__inner-wrapper::before) { display: none; }
.audit-detail { display: block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-tertiary); font-size: 11px; }
.architecture-panel { margin-bottom: 18px; }
.architecture-panel .panel-title-row { align-items: flex-start; }
.flow-row { display: flex; align-items: stretch; gap: 10px; margin-top: 18px; }
.flow-step { flex: 1; padding: 14px 10px; border: 1px solid var(--border-light); background: var(--accent-tint); border-radius: 8px; text-align: center; }
.flow-step span { display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; margin-right: 5px; border-radius: 50%; background: var(--accent); color: #fff; font-size: 12px; }
.flow-step strong { color: var(--text-primary); font-size: 13px; }
.flow-step small { display: block; margin-top: 7px; color: var(--text-tertiary); font-size: 11px; line-height: 1.4; }
.flow-arrow { align-self: center; color: var(--text-tertiary); font-size: 18px; }
@media (max-width: 920px) { .content-row { grid-template-columns: 1fr; } }
@media (max-width: 680px) {
  .page-header { align-items: flex-start; flex-direction: column; padding: 20px; }
  .header-actions { width: 100%; justify-content: space-between; }
  .summary-row, .check-grid { grid-template-columns: 1fr; }
  .flow-row { flex-direction: column; }
  .flow-arrow { transform: rotate(90deg); }
  .hero-meta { margin-left: 0; }
  .panel { padding: 16px; }
}
</style>

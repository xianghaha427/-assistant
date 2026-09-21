<template>
  <div class="bash-page">
    <div class="page-header">
      <h2>bash 命令安全管控</h2>
      <p class="page-desc">对 AI Agent 可能执行的系统命令做白名单/黑名单校验，危险命令自动拦截并写入审计日志。</p>
    </div>

    <div class="content-row">
      <!-- 命令校验区 -->
      <div class="card test-panel">
        <h3>命令安全校验</h3>
        <p class="hint">输入命令测试三级防线：危险模式检测 → 黑名单拦截 → 白名单校验</p>
        <div class="input-row">
          <el-input v-model="command" placeholder="例如：ls -la /home 或 rm -rf /" size="large" @keyup.enter="doExec" />
          <el-button type="primary" size="large" :loading="checking" @click="doExec">执行</el-button>
        </div>

        <div v-if="result" class="check-result" :class="result.allowed ? 'result-pass' : 'result-block'">
          <div class="result-icon"><CheckCircle2 :size="24" v-if="result.allowed" /><Lock :size="24" v-else /></div>
          <div class="result-main">
            <div class="result-verdict">{{ result.allowed ? (result.timedOut ? '执行超时' : '执行完成') : '已拦截' }}</div>
            <div class="result-reason">{{ result.reason }}</div>
            <div class="result-meta">
              风险等级：<el-tag :type="riskTagType" size="small">{{ riskLabel }}</el-tag>
              &nbsp;|&nbsp; 退出码：<code>{{ result.exitCode }}</code>
            </div>
            <pre v-if="result.stdout" class="stdout">{{ result.stdout }}</pre>
            <pre v-if="result.stderr" class="stderr">{{ result.stderr }}</pre>
          </div>
        </div>
      </div>

      <!-- 规则说明 -->
      <div class="card rules-panel">
        <h3>三级安全防线</h3>
        <el-collapse>
          <el-collapse-item title="第 1 道：危险模式检测" name="1">
            <p>检测命令中的路径穿越（<code>../</code>）、管道注入（<code>|</code> <code>&&</code> <code>;</code>）、命令替换（<code>`cmd`</code> <code>$(cmd)</code>）、输出重定向（<code>&gt;</code>）、敏感文件访问（<code>/etc/passwd</code>）等危险模式。</p>
          </el-collapse-item>
          <el-collapse-item title="第 2 道：黑名单拦截" name="2">
            <p>直接禁止包含以下命令的执行：</p>
            <div class="tag-cloud">
              <el-tag v-for="c in blacklist" :key="c" size="small" type="danger" effect="plain" class="cmd-tag">{{ c }}</el-tag>
            </div>
          </el-collapse-item>
          <el-collapse-item title="第 3 道：白名单放行" name="3">
            <p>仅放行以下安全命令，未在白名单中的命令需管理员审批：</p>
            <div class="tag-cloud">
              <el-tag v-for="c in whitelist" :key="c" size="small" type="success" effect="plain" class="cmd-tag">{{ c }}</el-tag>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>

    <!-- 安全架构说明 -->
    <div class="card arch-panel">
      <h3>安全架构说明</h3>
      <div class="arch-diagram">
        <div class="arch-step"><div class="step-num">1</div>用户/AI 代理发起命令</div>
        <div class="arch-arrow">→</div>
        <div class="arch-step"><div class="step-num">2</div>危险模式检测</div>
        <div class="arch-arrow">→</div>
        <div class="arch-step"><div class="step-num">3</div>黑名单拦截</div>
        <div class="arch-arrow">→</div>
        <div class="arch-step"><div class="step-num">4</div>白名单校验</div>
        <div class="arch-arrow">→</div>
        <div class="arch-step pass"><div class="step-num">5</div>放行/拦截</div>
        <div class="arch-arrow">→</div>
        <div class="arch-step"><div class="step-num">6</div>写入审计日志</div>
      </div>
      <div class="arch-note">
        <strong>生产环境扩展</strong>：可配合 Docker 容器沙箱执行，每个 Agent 独立容器运行，进程/网络/文件系统全隔离，禁止外部网络访问。危险命令直接拦截在规则层，不进入沙箱。
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { CheckCircle2, Lock } from 'lucide-vue-next'
import { execBashCommand } from '../../api/index.js'

const command = ref('')
const checking = ref(false)
const result = ref(null)

const blacklist = ['rm', 'mv', 'sudo', 'su', 'curl', 'wget', 'nc', 'ssh', 'kill', 'reboot', 'shutdown', 'crontab']
const whitelist = ['ls', 'cat', 'head', 'tail', 'echo', 'date', 'pwd', 'whoami', 'wc', 'grep', 'find', 'df', 'du', 'git', 'java', 'python', 'node', 'npm', 'docker', 'ps', 'top', 'free', 'ping']

const riskTagType = computed(() => {
  if (!result.value) return 'info'
  if (result.value.riskLevel === 'high') return 'danger'
  if (result.value.riskLevel === 'medium') return 'warning'
  return 'success'
})
const riskLabel = computed(() => {
  if (!result.value) return ''
  if (result.value.riskLevel === 'high') return '高危'
  if (result.value.riskLevel === 'medium') return '中危'
  return '低风险'
})

async function doExec() {
  if (!command.value.trim()) return
  checking.value = true
  result.value = null
  try {
    result.value = await execBashCommand(command.value.trim())
  } catch (e) {
    ElMessage.error('执行失败')
  } finally {
    checking.value = false
  }
}
</script>

<style scoped>
.bash-page { max-width: 960px; margin: 0 auto; }
.page-header { margin-bottom: 24px; }
.page-header h2 { margin: 0 0 8px; font-size: 22px; color: var(--text-primary); }
.page-desc { color: var(--text-secondary); font-size: 14px; margin: 0; }

.content-row { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }
.card { padding: 20px 24px; }
.card h3 { margin: 0 0 12px; font-size: 16px; color: var(--text-primary); }
.hint { font-size: 13px; color: var(--text-tertiary); margin: 0 0 14px; }

.input-row { display: flex; gap: 10px; }
.check-result { display: flex; gap: 14px; align-items: flex-start; margin-top: 16px; padding: 16px; border-radius: var(--radius-md); }
.check-result.result-pass { background: rgba(45, 157, 110, 0.08); border: 1px solid rgba(45, 157, 110, 0.2); }
.check-result.result-block { background: rgba(245, 108, 108, 0.06); border: 1px solid rgba(245, 108, 108, 0.2); }
.result-icon { font-size: 28px; flex-shrink: 0; }
.result-verdict { font-size: 16px; font-weight: 600; color: var(--text-primary); margin-bottom: 4px; }
.result-reason { font-size: 13px; color: var(--text-secondary); margin-bottom: 6px; }
.result-meta { font-size: 12px; color: var(--text-tertiary); }
.result-meta code { background: var(--bg); padding: 1px 6px; border-radius: 4px; font-size: 12px; }
.stdout { margin-top: 10px; padding: 10px 14px; background: #1a1a2e; color: #e0e0e0; border-radius: 6px; font-size: 13px; line-height: 1.5; overflow-x: auto; white-space: pre-wrap; max-height: 300px; overflow-y: auto; }
.stderr { margin-top: 6px; padding: 10px 14px; background: #2e1a1a; color: #f0c0c0; border-radius: 6px; font-size: 13px; line-height: 1.5; overflow-x: auto; white-space: pre-wrap; max-height: 150px; overflow-y: auto; }

.tag-cloud { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.cmd-tag { font-family: monospace; }

.arch-panel { margin-bottom: 20px; }
.arch-diagram { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 16px 0; }
.arch-step { padding: 10px 14px; background: var(--bg); border-radius: var(--radius-sm); font-size: 13px; text-align: center; flex-shrink: 0; }
.arch-step.pass { background: rgba(45, 157, 110, 0.1); }
.step-num { width: 22px; height: 22px; border-radius: 50%; background: var(--accent); color: #fff; font-size: 12px; font-weight: 600; display: inline-flex; align-items: center; justify-content: center; margin-right: 6px; }
.arch-arrow { color: var(--text-tertiary); font-size: 18px; }
.arch-note { margin-top: 12px; padding: 12px 16px; background: var(--accent-soft); border-radius: var(--radius-sm); font-size: 13px; color: var(--text-secondary); line-height: 1.7; }
</style>

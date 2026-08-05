<template>
  <div class="meeting-list">
    <!-- ====== Hero 鍖?====== -->
    <div class="hero card">
      <div class="hero-orb hero-orb-1"></div>
      <div class="hero-orb hero-orb-2"></div>
      <div class="hero-grid"></div>

      <div class="hero-main">
        <h1 class="hero-title">
          鏅鸿兘<span class="grad-text">浼氳</span>
        </h1>
        <p class="hero-sub">
          AI 椹卞姩鐨勪細璁邯瑕佺敓鎴愶紝浠庤浆鍐欐枃鏈埌缁撴瀯鍖栫邯瑕侊紝涓€閿畬鎴?
        </p>

        <div class="hero-actions">
          <button class="hero-btn primary" @click="$router.push('/meeting/create')">
            <span class="btn-icon"></span> 鏂板缓浼氳
          </button>
        </div>
      </div>

      <div class="hero-stats">
        <div class="hero-stat">
          <div class="stat-num grad-text">{{ meetings.length }}</div>
          <div class="stat-label">浼氳鎬绘暟</div>
        </div>
        <div class="hero-stat-divider"></div>
        <div class="hero-stat">
          <div class="stat-num grad-text">{{ completedCount }}</div>
          <div class="stat-label">宸插</div>
        </div>
      </div>
    </div>

    <!-- ====== 浼氳鍗＄墖缃戞牸 ====== -->
    <div v-if="loading" class="meeting-grid">
      <div v-for="i in 6" :key="i" class="meeting-card card skeleton-card">
        <div class="skeleton-line w60"></div>
        <div class="skeleton-line w40"></div>
        <div class="skeleton-line w80"></div>
        <div class="skeleton-line w30"></div>
      </div>
    </div>

    <div v-else-if="meetings.length === 0" class="empty-state">
      <div class="empty-icon"></div>
      <div class="empty-title">鏆傛棤浼氳璁板綍</div>
      <div class="empty-desc">涓婁紶浼氳杞啓鏂囨湰鎴栭煶棰戯紝AI 灏嗚嚜鍔ㄧ敓鎴愮粨鏋勫寲绾</div>
      <button class="hero-btn primary" @click="$router.push('/meeting/create')">
        鍒涘缓绗竴娆′細璁?
      </button>
    </div>

    <div v-else class="meeting-grid">
      <div
        v-for="m in meetings"
        :key="m.id"
        class="meeting-card card"
        @click="goDetail(m.id)"
      >
        <div class="card-top">
          <div class="card-title">{{ m.title }}</div>
          <el-dropdown trigger="click" @command="handleCommand($event, m)" @click.stop>
            <span class="card-more" @click.stop></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="delete">
                  <span style="color: #f56c6c">鍒犻櫎</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="card-time">{{ formatTime(m.created_at) }}</div>
        <div class="card-bottom">
          <span class="status-tag" :class="m.status">
            <span class="status-dot"></span>
            {{ statusText(m.status) }}
          </span>
          <span class="card-source">{{ sourceText(m.source_type) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMeetings, deleteMeeting } from '../../api/meeting.js'

const router = useRouter()
const meetings = ref([])
const loading = ref(true)

const completedCount = computed(() => meetings.value.filter(m => m.status === 'completed').length)

onMounted(async () => {
  await load()
})

async function load() {
  loading.value = true
  try {
    meetings.value = await getMeetings()
  } catch (e) {
    // 蹇界暐
  } finally {
    loading.value = false
  }
}

function goDetail(id) {
  router.push(`/meeting/${id}`)
}

async function handleCommand(cmd, m) {
  if (cmd === 'delete') {
    await ElMessageBox.confirm(`确定删除会议《${m.title}》吗？删除后不可恢复。`, '提示', {
      type: 'warning',
      confirmButtonText: '鍒犻櫎',
      cancelButtonText: '鍙栨秷'
    })
    await deleteMeeting(m.id)
    ElMessage.success('已删除')
    await load()
  }
}

const STATUS_MAP = {
  pending: '待处理',
  processing: '处理中',
  completed: '已完成',
  failed: '澶辫触'
}

const SOURCE_MAP = {
  text: '鏂囨湰杈撳叆',
  audio: '闊抽涓婁紶'
}

function statusText(s) {
  return STATUS_MAP[s] || s
}

function sourceText(s) {
  return SOURCE_MAP[s] || s
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const pad = n => String(n).padStart(2, '0')
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return `今天 ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.meeting-list { max-width: 1200px; margin: 0 auto; }

/* ====== Hero ====== */
.hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 34px 38px;
  overflow: hidden;
  margin-bottom: 24px;
}
.hero-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(64px);
  pointer-events: none;
}
.hero-orb-1 {
  width: 300px; height: 220px;
  background: rgba(77, 141, 255, 0.22);
  top: -80px; right: 10%;
  animation: drift 14s ease-in-out infinite alternate;
}
.hero-orb-2 {
  width: 220px; height: 180px;
  background: rgba(139, 92, 246, 0.16);
  bottom: -70px; left: 22%;
  animation: drift 18s ease-in-out infinite alternate-reverse;
}
@keyframes drift {
  from { transform: translate(0, 0); }
  to   { transform: translate(40px, 18px); }
}
.hero-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 80% 90% at 30% 40%, black 20%, transparent 100%);
  -webkit-mask-image: radial-gradient(ellipse 80% 90% at 30% 40%, black 20%, transparent 100%);
  pointer-events: none;
}

.hero-main { position: relative; z-index: 1; }
.hero-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-primary);
}
.hero-sub {
  margin: 8px 0 0;
  font-size: 13.5px;
  color: var(--text-secondary);
}
.hero-actions { display: flex; gap: 12px; margin-top: 20px; }
.hero-btn {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 10px 20px;
  border-radius: 11px;
  font-size: 13.5px;
  font-weight: 550;
  cursor: pointer;
  transition: all 0.18s ease;
  border: none;
}
.hero-btn.primary {
  background: var(--grad-brand);
  color: #fff;
  box-shadow: var(--glow-brand);
}
.hero-btn.primary:hover { filter: brightness(1.14); transform: translateY(-1px); }
.btn-icon { font-size: 14px; }

.hero-stats {
  display: flex; align-items: center; gap: 26px;
  position: relative; z-index: 1;
  padding: 18px 26px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border-light);
}
.hero-stat { text-align: center; }
.stat-num { font-size: 30px; font-weight: 750; letter-spacing: -0.02em; line-height: 1.1; }
.stat-label { font-size: 12px; color: var(--text-tertiary); margin-top: 4px; }
.hero-stat-divider { width: 1px; height: 34px; background: var(--border); }

/* ====== 浼氳鍗＄墖缃戞牸 ====== */
.meeting-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.meeting-card {
  position: relative;
  padding: 22px;
  cursor: pointer;
  transition: all 0.22s ease;
  overflow: hidden;
  animation: fadeUp 0.4s ease both;
}
.meeting-card::after {
  content: "";
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: var(--grad-aurora);
  opacity: 0;
  transition: opacity 0.22s ease;
}
.meeting-card:hover {
  transform: translateY(-3px);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md), var(--glow-soft);
}
.meeting-card:hover::after { opacity: 1; }

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-more {
  font-size: 18px;
  color: var(--text-tertiary);
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
  transition: color 0.15s;
}
.card-more:hover { color: var(--text-primary); }

.card-time {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 14px;
}
.card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 鐘舵€佹爣绛?*/
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid;
}
.status-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
}
.status-tag.pending {
  color: var(--text-tertiary);
  border-color: rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.03);
}
.status-tag.pending .status-dot { background: var(--text-tertiary); }

.status-tag.processing {
  color: var(--accent);
  border-color: rgba(77, 141, 255, 0.3);
  background: rgba(77, 141, 255, 0.08);
}
.status-tag.processing .status-dot {
  background: var(--accent);
  box-shadow: 0 0 6px rgba(77, 141, 255, 0.9);
  animation: breathe 1.8s ease-in-out infinite;
}
@keyframes breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.status-tag.completed {
  color: #34D399;
  border-color: rgba(52, 211, 153, 0.3);
  background: rgba(52, 211, 153, 0.08);
}
.status-tag.completed .status-dot { background: #34D399; box-shadow: 0 0 6px rgba(52, 211, 153, 0.9); }

.status-tag.failed {
  color: #f56c6c;
  border-color: rgba(245, 108, 108, 0.3);
  background: rgba(245, 108, 108, 0.08);
}
.status-tag.failed .status-dot { background: #f56c6c; box-shadow: 0 0 6px rgba(245, 108, 108, 0.9); }

.card-source {
  font-size: 11.5px;
  color: var(--text-tertiary);
}

/* ====== 绌虹姸鎬?====== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  text-align: center;
}
.empty-icon {
  width: 68px; height: 68px;
  border-radius: 20px;
  display: flex; align-items: center; justify-content: center;
  font-size: 30px;
  color: #fff;
  background: var(--grad-brand);
  box-shadow: var(--glow-brand);
  margin-bottom: 18px;
}
.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.empty-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 22px;
}

/* ====== 楠ㄦ灦灞?====== */
.skeleton-card { pointer-events: none; }
.skeleton-line {
  height: 14px;
  border-radius: 6px;
  background: linear-gradient(90deg, rgba(255,255,255,0.04) 25%, rgba(255,255,255,0.08) 50%, rgba(255,255,255,0.04) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  margin-bottom: 12px;
}
.skeleton-line.w30 { width: 30%; }
.skeleton-line.w40 { width: 40%; }
.skeleton-line.w60 { width: 60%; }
.skeleton-line.w80 { width: 80%; }
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@media (max-width: 768px) {
  .meeting-grid { grid-template-columns: 1fr; }
}
</style>

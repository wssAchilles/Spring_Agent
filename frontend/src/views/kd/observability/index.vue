<template>
  <div class="observability-page">
    <!-- 顶部状态栏 -->
    <header class="obs-header">
      <div class="obs-header-left">
        <h1 class="obs-title">LLM 可观测性</h1>
        <span class="obs-subtitle">Agent 调用追踪 · 质量评估 · 性能监控</span>
      </div>
      <div class="obs-header-right">
        <div class="status-indicator" :class="langfuseEnabled ? 'status-on' : 'status-off'">
          <span class="status-dot"></span>
          <span>{{ langfuseEnabled ? 'LangFuse 已连接' : 'LangFuse 未启用' }}</span>
        </div>
        <el-button v-if="langfuseEnabled" size="small" plain @click="openDashboard">
          <el-icon><Link /></el-icon>
          打开 Dashboard
        </el-button>
      </div>
    </header>

    <!-- 未启用状态 -->
    <div v-if="!langfuseEnabled" class="setup-section">
      <div class="setup-card">
        <div class="setup-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/>
            <path d="M2 17l10 5 10-5"/>
            <path d="M2 12l10 5 10-5"/>
          </svg>
        </div>
        <h2>启用 LLM 可观测性</h2>
        <p>LangFuse 提供 Agent 调用链路追踪、Token 消耗统计、质量评估面板。</p>
        <div class="setup-steps">
          <div class="setup-step">
            <span class="step-num">1</span>
            <div>
              <strong>注册 LangFuse</strong>
              <p>访问 <el-link type="primary" href="https://langfuse.com" target="_blank">langfuse.com</el-link> 创建项目</p>
            </div>
          </div>
          <div class="setup-step">
            <span class="step-num">2</span>
            <div>
              <strong>配置环境变量</strong>
              <pre class="config-block">LANGFUSE_ENABLED=true
LANGFUSE_PUBLIC_KEY=pk-lf-...
LANGFUSE_SECRET_KEY=sk-lf-...</pre>
            </div>
          </div>
          <div class="setup-step">
            <span class="step-num">3</span>
            <div>
              <strong>重启后端服务</strong>
              <p>配置生效后，每次 Agent 对话将自动上报追踪数据</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 已启用状态：仪表盘 -->
    <div v-else class="dashboard-section">
      <!-- 指标卡片行 -->
      <div class="metric-row">
        <div class="metric-card" v-for="m in metrics" :key="m.label">
          <div class="metric-label">{{ m.label }}</div>
          <div class="metric-value">{{ m.value }}<span class="metric-unit">{{ m.unit }}</span></div>
          <div class="metric-trend" :class="m.trend > 0 ? 'trend-up' : m.trend < 0 ? 'trend-down' : ''">
            {{ m.trend > 0 ? '↑' : m.trend < 0 ? '↓' : '—' }} {{ Math.abs(m.trend) }}%
          </div>
        </div>
      </div>

      <!-- 链路追踪列表 -->
      <div class="trace-section">
        <h3>最近对话追踪</h3>
        <div class="trace-list">
          <div v-for="trace in traces" :key="trace.id" class="trace-item">
            <div class="trace-header">
              <span class="trace-status" :class="trace.status">{{ trace.status === 'success' ? '✓' : '✗' }}</span>
              <span class="trace-query">{{ trace.query }}</span>
              <span class="trace-time">{{ trace.duration }}ms</span>
              <span class="trace-tokens">{{ trace.tokens }} tok</span>
            </div>
            <div class="trace-spans">
              <span v-for="span in trace.spans" :key="span.name" class="span-chip" :style="{ background: span.color }">
                {{ span.name }} {{ span.duration }}ms
              </span>
            </div>
          </div>
          <div v-if="traces.length === 0" class="empty-traces">
            暂无追踪数据，发起一次 Agent 对话后刷新
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { Link } from '@element-plus/icons-vue';

const langfuseEnabled = ref(false);
const langfuseUrl = ref('https://cloud.langfuse.com');

const metrics = ref([
  { label: '总对话数', value: '—', unit: '', trend: 0 },
  { label: '平均延迟', value: '—', unit: 'ms', trend: 0 },
  { label: 'Token 消耗', value: '—', unit: '', trend: 0 },
  { label: 'Faithfulness', value: '—', unit: '', trend: 0 },
]);

const traces = ref([]);

function openDashboard() {
  window.open(langfuseUrl.value, '_blank');
}

onMounted(() => {
  langfuseEnabled.value = import.meta.env.VITE_LANGFUSE_ENABLED === 'true';
  if (import.meta.env.VITE_LANGFUSE_BASE_URL) {
    langfuseUrl.value = import.meta.env.VITE_LANGFUSE_BASE_URL;
  }
});
</script>

<style scoped lang="scss">
$bg: #0a0a0a;
$surface: #141414;
$border: #222;
$text: #e5e5e5;
$text-muted: #888;
$accent: #3b82f6;
$success: #10b981;
$danger: #ef4444;

.observability-page {
  background: $bg;
  color: $text;
  min-height: 100vh;
  padding: 24px 32px;
  font-family: 'Inter', -apple-system, sans-serif;
}

.obs-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding-bottom: 20px;
  border-bottom: 1px solid $border;
  margin-bottom: 28px;

  .obs-title {
    font-size: 22px;
    font-weight: 600;
    letter-spacing: -0.02em;
    margin: 0;
  }
  .obs-subtitle {
    font-size: 13px;
    color: $text-muted;
    margin-top: 4px;
    display: block;
  }
  .obs-header-right {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  padding: 4px 10px;
  border-radius: 6px;
  &.status-on { color: $success; background: rgba($success, 0.1); }
  &.status-off { color: $text-muted; background: rgba(#fff, 0.05); }
  .status-dot {
    width: 6px; height: 6px; border-radius: 50%;
    .status-on & { background: $success; }
    .status-off & { background: $text-muted; }
  }
}

// Setup section
.setup-section {
  display: flex;
  justify-content: center;
  padding-top: 60px;
}
.setup-card {
  max-width: 520px;
  text-align: center;
  .setup-icon { color: $accent; margin-bottom: 16px; }
  h2 { font-size: 20px; font-weight: 600; margin: 0 0 8px; }
  p { color: $text-muted; font-size: 14px; line-height: 1.6; margin: 0 0 28px; }
}
.setup-steps { text-align: left; }
.setup-step {
  display: flex;
  gap: 14px;
  padding: 14px 0;
  border-top: 1px solid $border;
  .step-num {
    width: 28px; height: 28px; border-radius: 50%;
    background: $accent; color: #fff; font-size: 13px; font-weight: 600;
    display: flex; align-items: center; justify-content: center;
    flex-shrink: 0;
  }
  strong { font-size: 14px; display: block; margin-bottom: 2px; }
  p { font-size: 13px; color: $text-muted; margin: 0; }
}
.config-block {
  background: $surface;
  border: 1px solid $border;
  border-radius: 6px;
  padding: 10px 14px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: $accent;
  margin-top: 6px;
  white-space: pre;
}

// Dashboard section
.metric-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 28px;
}
.metric-card {
  background: $surface;
  border: 1px solid $border;
  border-radius: 10px;
  padding: 18px 20px;
  .metric-label { font-size: 12px; color: $text-muted; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 6px; }
  .metric-value { font-size: 28px; font-weight: 700; letter-spacing: -0.02em; }
  .metric-unit { font-size: 14px; font-weight: 400; color: $text-muted; margin-left: 2px; }
  .metric-trend { font-size: 12px; margin-top: 4px; }
  .trend-up { color: $success; }
  .trend-down { color: $danger; }
}

.trace-section {
  h3 { font-size: 15px; font-weight: 600; margin: 0 0 14px; }
}
.trace-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.trace-item {
  background: $surface;
  border: 1px solid $border;
  border-radius: 8px;
  padding: 12px 16px;
  .trace-header {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 13px;
  }
  .trace-status {
    width: 20px; height: 20px; border-radius: 50%; font-size: 11px;
    display: flex; align-items: center; justify-content: center;
    &.success { background: rgba($success, 0.15); color: $success; }
    &.error { background: rgba($danger, 0.15); color: $danger; }
  }
  .trace-query { flex: 1; font-weight: 500; }
  .trace-time, .trace-tokens { color: $text-muted; font-family: monospace; font-size: 12px; }
  .trace-spans { margin-top: 8px; display: flex; gap: 6px; flex-wrap: wrap; }
  .span-chip {
    font-size: 11px; padding: 2px 8px; border-radius: 4px;
    color: #fff; opacity: 0.85;
  }
}
.empty-traces {
  text-align: center; padding: 40px; color: $text-muted; font-size: 14px;
}
</style>

<template>
  <div class="app-container observability-page">
    <el-card shadow="never" class="status-card">
      <div class="status-row">
        <div class="status-left">
          <h2 class="page-title">LLM 可观测性</h2>
          <p class="page-desc">Agent 调用追踪 · 质量评估 · 性能监控</p>
        </div>
        <div class="status-right">
          <el-tag :type="langfuseEnabled ? 'success' : 'info'" effect="dark" size="large">
            <el-icon v-if="langfuseEnabled"><CircleCheckFilled /></el-icon>
            <el-icon v-else><WarningFilled /></el-icon>
            {{ langfuseEnabled ? 'LangFuse 已连接' : 'LangFuse 未启用' }}
          </el-tag>
          <el-button v-if="langfuseEnabled" type="primary" plain @click="openDashboard">
            <el-icon><Link /></el-icon>
            打开 Dashboard
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card v-if="!langfuseEnabled" shadow="never" class="setup-card">
      <el-result icon="info" title="启用 LLM 可观测性"
        sub-title="LangFuse 提供 Agent 调用链路追踪、Token 消耗统计、质量评估面板。">
        <template #extra>
          <el-steps direction="vertical" :active="0" style="max-width: 500px; margin: 0 auto;">
            <el-step title="注册 LangFuse 账号" description="访问 langfuse.com 创建项目，获取 API Key" />
            <el-step title="配置环境变量">
              <template #description>
                <pre class="config-block">LANGFUSE_ENABLED=true
LANGFUSE_PUBLIC_KEY=pk-lf-...
LANGFUSE_SECRET_KEY=sk-lf-...</pre>
              </template>
            </el-step>
            <el-step title="重启后端服务" description="配置生效后，Agent 对话将自动上报追踪数据" />
          </el-steps>
        </template>
      </el-result>
    </el-card>

    <template v-else>
      <el-row :gutter="16" class="metric-row">
        <el-col :xs="24" :sm="12" :lg="6" v-for="m in metrics" :key="m.label">
          <el-card shadow="never" class="metric-card">
            <div class="metric-label">{{ m.label }}</div>
            <div class="metric-value">
              {{ m.value }}
              <span v-if="m.unit" class="metric-unit">{{ m.unit }}</span>
            </div>
            <div class="metric-desc">{{ m.desc }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="never" class="trace-card">
        <template #header>
          <div class="trace-header">
            <div>
              <div class="trace-title">最近对话追踪</div>
              <div class="trace-subtitle">展示最近 20 条 LangFuse Trace 及其 Generation / Span</div>
            </div>
            <el-button size="small" :loading="loading" @click="refreshTraces">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </template>
        <el-table
          v-loading="loading"
          :data="traces"
          stripe
          max-height="52vh"
          style="width: 100%"
          empty-text="暂无追踪数据，发起一次 Agent 对话后刷新"
        >
          <el-table-column label="状态" width="88" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'success' ? 'success' : 'danger'" effect="light" size="small">
                {{ row.status === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="查询" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="query-cell">{{ row.query }}</div>
              <div class="trace-id">{{ row.id }}</div>
            </template>
          </el-table-column>
          <el-table-column label="延迟" width="100" align="center">
            <template #default="{ row }">
              <span class="mono">{{ formatMs(row.duration) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="Token" width="100" align="center">
            <template #default="{ row }">
              <span class="mono">{{ formatNumber(row.tokens) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="调用链" min-width="260">
            <template #default="{ row }">
              <el-tag v-for="span in row.spans" :key="`${row.id}-${span.name}-${span.duration}`" size="small"
                :type="span.type" class="span-tag">
                {{ span.name }}
                <span v-if="span.duration != null" class="span-duration">{{ formatMs(span.duration) }}</span>
              </el-tag>
              <span v-if="row.spans.length === 0" class="muted">暂无 observation</span>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="180">
            <template #default="{ row }">
              <span class="muted">{{ row.time }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { Link, Refresh, CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue';
import request from '@/utils/request';

const langfuseEnabled = ref(false);
const langfuseUrl = ref('https://cloud.langfuse.com');
const loading = ref(false);

const metrics = ref([
  { label: '总对话数', value: '0', unit: '', desc: '最近 Trace 数' },
  { label: '平均延迟', value: '0', unit: 'ms', desc: 'Trace / Observation 兼容聚合' },
  { label: 'Token 消耗', value: '0', unit: '', desc: 'Generation usage 汇总' },
  { label: 'Faithfulness', value: '0', unit: '', desc: '待接入质量评分' },
]);

const traces = ref([]);

function openDashboard() {
  window.open(langfuseUrl.value, '_blank');
}

async function checkLangFuseStatus() {
  try {
    const res = await request({ url: '/kb/health', method: 'get' });
    const langfuse = res.data?.langfuse;
    if (langfuse) {
      langfuseEnabled.value = langfuse.enabled === true;
      if (langfuse.baseUrl) langfuseUrl.value = langfuse.baseUrl;
    }
  } catch (e) {
    // 健康检查失败，保持默认状态
  }
}

async function refreshTraces() {
  loading.value = true;
  try {
    const res = await request({ url: '/kb/health/traces', method: 'get' });
    const data = res.data;
    if (data && data.enabled) {
      // 更新指标
      const m = data.metrics || {};
      metrics.value[0].value = String(m.totalConversations ?? 0);
      metrics.value[1].value = String(m.avgLatency ?? 0);
      metrics.value[2].value = String(m.totalTokens ?? 0);

      traces.value = (data.traces || []).map(t => ({
        id: t.id,
        query: t.input || t.name || '—',
        status: t.status || 'success',
        duration: normalizeNumber(t.latency),
        tokens: getTraceTokens(t),
        spans: (t.observations || []).map(o => ({
          name: o.name || o.type || 'span',
          duration: normalizeNumber(o.latency),
          type: getSpanType(o)
        })),
        time: t.timestamp || t.createdAt || '—'
      }));
    }
  } catch (e) {
    // 忽略
  } finally {
    loading.value = false;
  }
}

function normalizeNumber(value) {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function formatMs(value) {
  const n = normalizeNumber(value);
  return n === null ? '—' : `${n}ms`;
}

function formatNumber(value) {
  const n = normalizeNumber(value);
  return n === null ? '0' : String(n);
}

function getUsageTotal(usage) {
  if (!usage) return null;
  const total = normalizeNumber(usage.totalTokens ?? usage.total);
  if (total !== null) return total;
  const input = normalizeNumber(usage.input ?? usage.promptTokens);
  const output = normalizeNumber(usage.output ?? usage.completionTokens);
  return input !== null || output !== null ? (input || 0) + (output || 0) : null;
}

function getTraceTokens(trace) {
  const direct = getUsageTotal(trace.usage);
  if (direct !== null) return direct;
  return (trace.observations || []).reduce((sum, observation) => {
    const usageTotal = getUsageTotal(observation.usage) ?? getUsageTotal(observation.usageDetails);
    return sum + (usageTotal || 0);
  }, 0);
}

function getSpanType(observation) {
  const type = String(observation.type || '').toLowerCase();
  if (type.includes('generation')) return 'success';
  if (type.includes('span')) return 'info';
  return '';
}

onMounted(() => {
  checkLangFuseStatus();
  refreshTraces();
});
</script>

<style scoped lang="scss">
.observability-page {
  min-height: 100%;
  overflow-y: auto;
  padding-bottom: 24px;
}

.status-card {
  margin-bottom: 16px;
  flex-shrink: 0;
  .status-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
    min-height: 48px;
  }
  .page-title {
    font-size: 20px;
    font-weight: 600;
    margin: 0;
    color: #303133;
  }
  .page-desc {
    font-size: 13px;
    color: #909399;
    margin: 4px 0 0;
  }
  .status-right {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }
}

.setup-card {
  margin-bottom: 16px;
  flex-shrink: 0;
}

.config-block {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 14px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #409eff;
  margin-top: 6px;
  white-space: pre;
}

.metric-row {
  margin-bottom: 16px;
  flex-shrink: 0;
}

.metric-card {
  border-radius: 6px;
  min-height: 120px;
  .metric-label {
    font-size: 12px;
    color: #909399;
    text-transform: uppercase;
    letter-spacing: 0;
    margin-bottom: 8px;
  }
  .metric-value {
    font-size: 28px;
    font-weight: 700;
    color: #303133;
    letter-spacing: 0;
    line-height: 1.2;
  }
  .metric-unit {
    font-size: 14px;
    font-weight: 400;
    color: #909399;
    margin-left: 2px;
  }
  .metric-desc {
    font-size: 12px;
    color: #909399;
    margin-top: 8px;
    line-height: 18px;
  }
}

.trace-card {
  flex-shrink: 0;
  .trace-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;
  }
  .trace-title {
    font-weight: 600;
    color: #303133;
  }
  .trace-subtitle {
    margin-top: 4px;
    font-size: 12px;
    color: #909399;
  }
}

.query-cell {
  color: #303133;
  line-height: 20px;
}

.trace-id {
  margin-top: 2px;
  color: #c0c4cc;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
}

.mono {
  font-family: 'JetBrains Mono', monospace;
}

.span-tag {
  margin-right: 6px;
  margin-bottom: 4px;
}

.span-duration {
  margin-left: 4px;
  color: #606266;
  font-family: 'JetBrains Mono', monospace;
}

.muted {
  color: #909399;
}

@media (max-width: 768px) {
  .status-card .status-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
}
</style>

<template>
  <div class="app-container">
    <!-- 顶部状态栏 -->
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

    <!-- 未启用：配置引导 -->
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

    <!-- 已启用：仪表盘 -->
    <template v-else>
      <!-- 指标卡片 -->
      <el-row :gutter="16" class="metric-row">
        <el-col :span="6" v-for="m in metrics" :key="m.label">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-label">{{ m.label }}</div>
            <div class="metric-value">{{ m.value }}<span class="metric-unit">{{ m.unit }}</span></div>
            <div class="metric-trend" :class="m.trend > 0 ? 'trend-up' : m.trend < 0 ? 'trend-down' : ''">
              {{ m.trend > 0 ? '↑' : m.trend < 0 ? '↓' : '—' }} {{ Math.abs(m.trend) }}%
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 链路追踪 -->
      <el-card shadow="never" class="trace-card">
        <template #header>
          <div class="trace-header">
            <span>最近对话追踪</span>
            <el-button size="small" @click="refreshTraces">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </template>
        <el-table :data="traces" stripe style="width: 100%">
          <el-table-column label="状态" width="60" align="center">
            <template #default="{ row }">
              <el-icon :color="row.status === 'success' ? '#10b981' : '#ef4444'" :size="18">
                <CircleCheckFilled v-if="row.status === 'success'" />
                <CircleCloseFilled v-else />
              </el-icon>
            </template>
          </el-table-column>
          <el-table-column prop="query" label="查询" min-width="200" show-overflow-tooltip />
          <el-table-column label="延迟" width="100" align="center">
            <template #default="{ row }">
              <span style="font-family: monospace;">{{ row.duration }}ms</span>
            </template>
          </el-table-column>
          <el-table-column label="Token" width="100" align="center">
            <template #default="{ row }">
              <span style="font-family: monospace;">{{ row.tokens }}</span>
            </template>
          </el-table-column>
          <el-table-column label="调用链" min-width="200">
            <template #default="{ row }">
              <el-tag v-for="span in row.spans" :key="span.name" size="small"
                :type="span.type" style="margin-right: 4px;">
                {{ span.name }} {{ span.duration }}ms
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="time" label="时间" width="180" />
        </el-table>
        <el-empty v-if="traces.length === 0" description="暂无追踪数据，发起一次 Agent 对话后刷新" />
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { Link, Refresh, CircleCheckFilled, CircleCloseFilled, WarningFilled } from '@element-plus/icons-vue';
import request from '@/utils/request';

const langfuseEnabled = ref(false);
const langfuseUrl = ref('https://cloud.langfuse.com');
const loading = ref(false);

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
    const res = await request({ url: '/kb/health', method: 'get' });
    const mcp = res.data?.mcp;
    if (mcp) {
      metrics.value[0].value = String(mcp.tools || 0);
    }
  } catch (e) {
    // 忽略
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  checkLangFuseStatus();
});
</script>

<style scoped lang="scss">
.status-card {
  margin-bottom: 16px;
  .status-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
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
  }
}

.setup-card {
  margin-bottom: 16px;
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
}

.metric-card {
  .metric-label {
    font-size: 12px;
    color: #909399;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 8px;
  }
  .metric-value {
    font-size: 28px;
    font-weight: 700;
    color: #303133;
    letter-spacing: -0.02em;
  }
  .metric-unit {
    font-size: 14px;
    font-weight: 400;
    color: #909399;
    margin-left: 2px;
  }
  .metric-trend {
    font-size: 12px;
    margin-top: 4px;
  }
  .trend-up { color: #10b981; }
  .trend-down { color: #ef4444; }
}

.trace-card {
  .trace-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 500;
  }
}
</style>

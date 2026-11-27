<template>
  <div class="guardrail-dashboard-container">
    <div v-if="loadError" class="load-error-banner">
      {{ loadError }}
    </div>
    <!-- 4 大核心指标卡片 (Level 2 Glass) -->
    <div class="metrics-grid">
      <div class="metric-card">
        <div class="metric-label">审计请求总量 (TOTAL REQUESTS)</div>
        <div class="metric-value mono">{{ metricsData?.totalRequests?.toLocaleString() || '0' }}</div>
        <div class="metric-sub">全链路零侵入切面捕获</div>
      </div>

      <div class="metric-card">
        <div class="metric-label">PII 算法脱敏总量 (SANITIZED)</div>
        <div class="metric-value mono text-warning">{{ metricsData?.sanitizedCount?.toLocaleString() || '0' }}</div>
        <div class="metric-sub">手机号/身份证/银行卡</div>
      </div>

      <div class="metric-card">
        <div class="metric-label">对抗攻击阻断总量 (BLOCKED)</div>
        <div class="metric-value mono text-danger">{{ metricsData?.blockedCount?.toLocaleString() || '0' }}</div>
        <div class="metric-sub">越狱对抗/红线敏感词 (Fail-Close)</div>
      </div>

      <div class="metric-card">
        <div class="metric-label">事实忠实度均值 (FAITHFULNESS)</div>
        <div class="metric-value mono text-success">{{ ((metricsData?.averageFaithfulness || 0) * 100).toFixed(1) }}%</div>
        <div class="metric-sub">超球面向量投影与幽灵引用自愈</div>
      </div>
    </div>

    <!-- 中部图表区: 24h 走势图 + PII 分布与雷达图 -->
    <div class="charts-row">
      <!-- 24 小时时序风险态势走势图 -->
      <div class="chart-panel trend-panel">
        <div class="panel-header">
          <span class="panel-title">24 小时全链路安全拦截态势 (HOURLY RISK EVENTS)</span>
          <el-tag size="small" effect="plain" class="mono-badge">REAL-TIME</el-tag>
        </div>
        <div class="echarts-box" ref="trendChartRef" />
      </div>

      <!-- 右侧多维能力雷达与 PII 分布 -->
      <div class="chart-panel radar-panel">
        <div class="panel-header">
          <span class="panel-title">PII 实体识别分布与防御矩阵</span>
        </div>
        <!-- PII 实体频次进度 -->
        <div class="pii-dist-list">
          <template v-if="Object.keys(metricsData?.piiTypeDistribution || {}).length > 0">
            <div
              v-for="(count, typeName) in metricsData?.piiTypeDistribution"
              :key="typeName"
              class="pii-item"
            >
              <div class="pii-meta">
                <span class="type-name">{{ formatPiiType(String(typeName)) }}</span>
                <span class="type-count mono">{{ count }} 次</span>
              </div>
              <div class="pii-bar">
                <div class="pii-fill" :style="{ width: `${Math.min(100, (Number(count) / maxPiiCount) * 100)}%` }" />
              </div>
            </div>
          </template>
          <div v-else class="pii-empty-hint">暂无 PII 实体脱敏记录</div>
        </div>
        <!-- 防御雷达图 -->
        <div class="radar-box" ref="radarChartRef" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getGuardrailMetrics, GuardrailMetricsVO } from '@/api/audit';

function createEmptyMetrics(): GuardrailMetricsVO {
  return {
    totalRequests: 0,
    sanitizedCount: 0,
    blockedCount: 0,
    redactionRate: 0,
    averageFaithfulness: 0,
    piiTypeDistribution: {},
    hourlyRiskEvents: [],
    timestamp: Date.now()
  };
}

const metricsData = ref<GuardrailMetricsVO | null>(null);
const loadError = ref('');
const trendChartRef = ref<HTMLDivElement | null>(null);
const radarChartRef = ref<HTMLDivElement | null>(null);

const maxPiiCount = computed(() => {
  const values = Object.values(metricsData.value?.piiTypeDistribution || {});
  return values.length > 0 ? Math.max(1, ...values) : 1;
});

let trendChart: echarts.ECharts | null = null;
let radarChart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

function formatPiiType(type: string): string {
  switch (type) {
    case 'PHONE': return '中国大陆手机号 (11位)';
    case 'ID_CARD': return '二代居民身份证 (ISO 7064)';
    case 'BANK_CARD': return '银行卡卡号 (Luhn 模10)';
    case 'EMAIL': return '企业/个人电子邮箱';
    case 'API_TOKEN': return '敏感授权 Bearer Token';
    default: return type;
  }
}

async function loadMetrics() {
  loadError.value = '';
  try {
    const res: any = await getGuardrailMetrics();
    metricsData.value = res?.data || res || createEmptyMetrics();
  } catch {
    // 严格遵守零伪造指标约束：请求失败时归零并展示错误提示
    loadError.value = '安全护栏实时指标获取失败，已归零展示';
    metricsData.value = createEmptyMetrics();
  } finally {
    await nextTick();
    renderCharts();
  }
}

function renderCharts() {
  renderTrendChart();
  renderRadarChart();
}

function renderTrendChart() {
  if (!trendChartRef.value) return;
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value);
  }

  const events = metricsData.value?.hourlyRiskEvents || [];
  const hours = events.map((e) => e.hourLabel);
  const counts = events.map((e) => e.interceptionCount);

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.96)',
      borderColor: 'rgba(0, 0, 0, 0.08)',
      textStyle: { color: '#1d1d1f', fontSize: 12 }
    },
    grid: {
      top: 24,
      left: 45,
      right: 20,
      bottom: 30
    },
    xAxis: {
      type: 'category',
      data: hours,
      axisLine: { lineStyle: { color: 'rgba(0, 0, 0, 0.12)' } },
      axisLabel: { color: '#6e6e73', fontSize: 10, fontFamily: 'JetBrains Mono' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: 'rgba(0, 0, 0, 0.06)' } },
      axisLabel: { color: '#6e6e73', fontSize: 10, fontFamily: 'JetBrains Mono' }
    },
    series: [
      {
        name: '安全拦截事件数',
        type: 'line',
        smooth: true,
        data: counts,
        symbol: 'none',
        lineStyle: { color: '#6366F1', width: 2.5 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(99, 102, 241, 0.35)' },
            { offset: 1, color: 'rgba(99, 102, 241, 0.00)' }
          ])
        }
      }
    ]
  };

  trendChart.setOption(option);
}

function renderRadarChart() {
  if (!radarChartRef.value) return;
  if (!radarChart) {
    radarChart = echarts.init(radarChartRef.value);
  }

  const m = metricsData.value;
  const total = Number(m?.totalRequests || 0);
  const clamp100 = (n: number) => Math.max(0, Math.min(100, Math.round(n)));
  let radarValues = [0, 0, 0, 0, 0];
  if (m && total > 0) {
    const blockedRate = Math.min(1, Number(m.blockedCount || 0) / total);
    const passRateScore = (1 - blockedRate) * 100;
    const faithfulnessScore = Number(m.averageFaithfulness || 0) * 100;
    const redactionCoverage = Number(m.sanitizedCount || 0) > 0
      ? Math.max(passRateScore, Math.min(100, Number(m.redactionRate || 0) * 100 + faithfulnessScore * 0.5))
      : passRateScore;
    radarValues = [
      clamp100(redactionCoverage),
      clamp100(passRateScore),
      clamp100((1 - blockedRate) * 100),
      clamp100(faithfulnessScore),
      clamp100(faithfulnessScore)
    ];
  }

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    radar: {
      indicator: [
        { name: 'PII脱敏', max: 100 },
        { name: '越狱注入防卫', max: 100 },
        { name: '红线敏感词', max: 100 },
        { name: '幽灵引用自愈', max: 100 },
        { name: '事实忠实度', max: 100 }
      ],
      radius: '65%',
      splitNumber: 4,
      axisName: { color: '#3c3c43', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(0, 0, 0, 0.08)' } },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: 'rgba(0, 0, 0, 0.1)' } }
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: radarValues,
            name: '安全护栏综合评分',
            symbol: 'none',
            lineStyle: { color: '#10B981', width: 2 },
            areaStyle: { color: 'rgba(16, 185, 129, 0.25)' }
          }
        ]
      }
    ]
  };

  radarChart.setOption(option);
}

onMounted(() => {
  loadMetrics();

  resizeObserver = new ResizeObserver(() => {
    trendChart?.resize();
    radarChart?.resize();
  });
  if (trendChartRef.value) resizeObserver.observe(trendChartRef.value);
});

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  trendChart?.dispose();
  radarChart?.dispose();
});
</script>

<style scoped lang="scss">
.guardrail-dashboard-container {
  width: 100%;
  height: 100%;
  padding: 24px;
  background: transparent;
  overflow-y: auto;
  box-sizing: border-box;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro", "PingFang SC", sans-serif;

  .load-error-banner {
    margin-bottom: 14px;
    padding: 8px 14px;
    border-radius: 12px;
    background: rgba(255, 59, 48, 0.1);
    border: 0.5px solid rgba(255, 59, 48, 0.3);
    color: #c62828;
    font-size: 12px;
  }

  .pii-empty-hint {
    font-size: 12px;
    color: rgba(60, 60, 67, 0.6);
    padding: 6px 0;
  }

  /* Apple iOS 26 双层玻璃混合 mixin（零扩散阴影 + z-index 分层隔离） */
  .metrics-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 24px;

    .metric-card {
      position: relative;
      background: transparent;
      border: 0.5px solid rgba(255, 255, 255, 0.92);
      border-radius: 20px;
      padding: 18px 20px;
      box-shadow: none;

      &::before {
        content: '';
        position: absolute;
        inset: 0;
        z-index: 1;
        border-radius: inherit;
        background: rgba(255, 255, 255, 0.72);
        mix-blend-mode: plus-lighter;
        pointer-events: none;
      }

      &::after {
        content: '';
        position: absolute;
        inset: 0;
        z-index: 2;
        border-radius: inherit;
        backdrop-filter: blur(50px);
        -webkit-backdrop-filter: blur(50px);
        background: rgba(255, 255, 255, 0.25);
        mix-blend-mode: color-dodge;
        pointer-events: none;
      }

      > :where(*) {
        position: relative;
        z-index: 3;
      }

      .metric-label {
        font-size: 11px;
        font-weight: 600;
        letter-spacing: 0.03em;
        color: #6e6e73;
        margin-bottom: 8px;
      }

      .metric-value {
        font-size: 28px;
        font-weight: 700;
        color: #000000;
        margin-bottom: 4px;

        &.mono {
          font-family: ui-monospace, 'SF Mono', 'JetBrains Mono', monospace;
        }

        &.text-warning {
          color: #ff9500;
        }

        &.text-danger {
          color: #ff3b30;
        }

        &.text-success {
          color: #10B981;
        }
      }

      .metric-sub {
        font-size: 11px;
        color: #3c3c43;
      }
    }
  }

  .charts-row {
    display: grid;
    grid-template-columns: 2fr 1fr;
    gap: 16px;

    .chart-panel {
      position: relative;
      background: transparent;
      border: 0.5px solid rgba(255, 255, 255, 0.92);
      border-radius: 22px;
      padding: 20px;
      box-shadow: none;

      &::before {
        content: '';
        position: absolute;
        inset: 0;
        z-index: 1;
        border-radius: inherit;
        background: rgba(255, 255, 255, 0.72);
        mix-blend-mode: plus-lighter;
        pointer-events: none;
      }

      &::after {
        content: '';
        position: absolute;
        inset: 0;
        z-index: 2;
        border-radius: inherit;
        backdrop-filter: blur(50px);
        -webkit-backdrop-filter: blur(50px);
        background: rgba(255, 255, 255, 0.25);
        mix-blend-mode: color-dodge;
        pointer-events: none;
      }

      > :where(*) {
        position: relative;
        z-index: 3;
      }

      .panel-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 16px;

        .panel-title {
          font-size: 13px;
          font-weight: 590;
          color: #000000;
          letter-spacing: 0.02em;
        }

        .mono-badge {
          font-family: ui-monospace, 'SF Mono', 'JetBrains Mono', monospace;
          background: rgba(0, 122, 255, 0.08);
          border-color: rgba(0, 122, 255, 0.22);
          color: #007aff;
        }
      }

      .echarts-box {
        width: 100%;
        height: 320px;
      }

      .pii-dist-list {
        display: flex;
        flex-direction: column;
        gap: 10px;
        margin-bottom: 16px;

        .pii-item {
          .pii-meta {
            display: flex;
            align-items: center;
            justify-content: space-between;
            font-size: 11px;
            margin-bottom: 4px;

            .type-name {
              color: #3c3c43;
            }

            .type-count {
              color: #000000;
              font-weight: 600;
            }
          }

          .pii-bar {
            width: 100%;
            height: 4px;
            background: rgba(0, 0, 0, 0.08);
            border-radius: 2px;
            overflow: hidden;

            .pii-fill {
              height: 100%;
              background: #007aff;
              border-radius: 2px;
              transition: width 0.3s ease;
            }
          }
        }
      }

      .radar-box {
        width: 100%;
        height: 200px;
      }
    }
  }
}
</style>

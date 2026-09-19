<template>
  <div class="execution-waterfall-panel glass-panel">
    <!-- 头部统计看板 -->
    <div class="waterfall-header">
      <div class="header-title-box">
        <h3 class="panel-title">
          <span class="pulse-indicator"></span>
          全链路多 Agent 协同瀑布流看板
        </h3>
        <span class="trace-id-badge" v-if="activeTraceId">TRACE: {{ activeTraceId }}</span>
      </div>
      <div class="header-actions">
        <el-switch
          v-model="onlyCriticalPath"
          active-text="仅看关键路径"
          class="critical-switch"
        />
        <el-button v-ripple size="small" class="glass-action-btn" @click="toggleExpandAll">
          {{ isAllExpanded ? '全部折叠' : '全部展开' }}
        </el-button>
      </div>
    </div>

    <!-- 顶部核心指标胶囊条 -->
    <div class="metric-capsule-row" v-if="stats">
      <div class="metric-capsule">
        <span class="cap-label">全链路总耗时</span>
        <span class="cap-val highlight-blue">{{ stats.totalDurationMs.toFixed(1) }} ms</span>
      </div>
      <div class="metric-capsule">
        <span class="cap-label">关键路径 (CPM)</span>
        <span class="cap-val highlight-gold">{{ stats.criticalPathDurationMs.toFixed(1) }} ms</span>
      </div>
      <div class="metric-capsule">
        <span class="cap-label">消耗总 Token</span>
        <span class="cap-val highlight-green">{{ stats.totalTokens }}</span>
      </div>
      <div class="metric-capsule">
        <span class="cap-label">执行 Span 数</span>
        <span class="cap-val">{{ stats.totalSpans }}</span>
      </div>
      <div class="metric-capsule" :class="{ 'has-error': stats.errorCount > 0 }">
        <span class="cap-label">异常拦截数</span>
        <span class="cap-val">{{ stats.errorCount }}</span>
      </div>
    </div>

    <!-- 时间轴刻度标尺 -->
    <div class="timeline-ruler-wrap">
      <div class="timeline-name-spacer">调用节点与层级</div>
      <div class="timeline-ticks">
        <span class="tick-mark" style="left: 0%">0ms</span>
        <span class="tick-mark" style="left: 25%">{{ (stats.totalDurationMs * 0.25).toFixed(0) }}ms</span>
        <span class="tick-mark" style="left: 50%">{{ (stats.totalDurationMs * 0.5).toFixed(0) }}ms</span>
        <span class="tick-mark" style="left: 75%">{{ (stats.totalDurationMs * 0.75).toFixed(0) }}ms</span>
        <span class="tick-mark" style="left: 100%">{{ stats.totalDurationMs.toFixed(0) }}ms</span>
      </div>
    </div>

    <!-- 瀑布流甘特列表 -->
    <div class="waterfall-list">
      <div
        v-for="span in displayedSpans"
        :key="span.spanId"
        class="waterfall-row"
        :class="{
          'is-critical': span.isCriticalPath,
          'is-selected': selectedSpan?.spanId === span.spanId
        }"
        @click="selectSpan(span)"
      >
        <!-- 左侧树状层级名称 -->
        <div class="row-tree-cell" :style="{ paddingLeft: `${span.depth * 20 + 8}px` }">
          <span class="span-type-tag" :class="span.spanType.toLowerCase()">{{ span.spanType }}</span>
          <span class="span-name-text" :title="span.spanName">{{ span.spanName }}</span>
          <span v-if="span.isCriticalPath" class="critical-badge">CPM</span>
        </div>

        <!-- 右侧甘特进度条 -->
        <div class="row-gantt-cell">
          <div
            class="gantt-bar-wrap"
            :style="{
              left: `${span.offsetPercent}%`,
              width: `${span.widthPercent}%`
            }"
          >
            <div
              class="gantt-bar"
              :class="[
                span.status.toLowerCase(),
                { 'critical-pulse': span.isCriticalPath }
              ]"
            >
              <span class="bar-duration-label">{{ span.durationMs.toFixed(1) }}ms</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 选中节点下钻抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      title="Span 详情下钻 (Detail Trace)"
      direction="rtl"
      size="480px"
      custom-class="glass-drawer"
    >
      <div v-if="selectedSpan" class="span-detail-content">
        <div class="detail-section">
          <div class="detail-label">片段名称 / 类型</div>
          <div class="detail-val bold">{{ selectedSpan.spanName }} ({{ selectedSpan.spanType }})</div>
        </div>
        <div class="detail-section">
          <div class="detail-label">执行耗时 & Token</div>
          <div class="detail-val">
            <span class="mono">{{ selectedSpan.durationMs.toFixed(2) }} ms</span> ·
            <span class="mono">{{ selectedSpan.tokenCount }} Tokens</span>
          </div>
        </div>
        <div class="detail-section">
          <div class="detail-label">入参摘要 (Summary Input)</div>
          <pre class="detail-code-block">{{ selectedSpan.summaryInput || '(无入参)' }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-label">出参摘要 (Summary Output)</div>
          <pre class="detail-code-block">{{ selectedSpan.summaryOutput || '(无出参)' }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import {
  WaterfallVirtualTimelineEngine,
  WaterfallSpanView,
  WaterfallTimelineStats,
  RawTraceSpan
} from '../engine/WaterfallVirtualTimelineEngine';

const props = defineProps<{
  traceId?: string;
  rawSpans?: RawTraceSpan[];
}>();

const engine = new WaterfallVirtualTimelineEngine();

const onlyCriticalPath = ref(false);
const isAllExpanded = ref(true);
const selectedSpan = ref<WaterfallSpanView | null>(null);
const drawerVisible = ref(false);

const activeTraceId = computed(() => props.traceId || (props.rawSpans?.[0]?.traceId || ''));

const timelineResult = computed(() => {
  return engine.processTimeline(props.rawSpans || []);
});

const stats = computed<WaterfallTimelineStats>(() => timelineResult.value.stats);

const displayedSpans = computed(() => {
  if (onlyCriticalPath.value) {
    return timelineResult.value.spans.filter(s => s.isCriticalPath);
  }
  return timelineResult.value.spans;
});

function selectSpan(span: WaterfallSpanView) {
  selectedSpan.value = span;
  drawerVisible.value = true;
}

function toggleExpandAll() {
  isAllExpanded.value = !isAllExpanded.value;
}
</script>

<style scoped lang="scss">
.execution-waterfall-panel {
  background: rgba(18, 24, 38, 0.72);
  backdrop-filter: blur(24px) saturate(190%);
  -webkit-backdrop-filter: blur(24px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  color: #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 14px;

  .waterfall-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-title-box {
      display: flex;
      align-items: center;
      gap: 10px;

      .panel-title {
        margin: 0;
        font-size: 15px;
        font-weight: 600;
        display: flex;
        align-items: center;
        gap: 8px;
        color: #f8fafc;

        .pulse-indicator {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: #38bdf8;
          box-shadow: 0 0 8px #38bdf8;
        }
      }

      .trace-id-badge {
        font-size: 11px;
        font-family: monospace;
        padding: 2px 6px;
        background: rgba(56, 189, 248, 0.12);
        color: #38bdf8;
        border-radius: 4px;
        border: 1px solid rgba(56, 189, 248, 0.25);
      }
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 12px;

      .glass-action-btn {
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid rgba(255, 255, 255, 0.1);
        color: #cbd5e1;
      }
    }
  }

  .metric-capsule-row {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;

    .metric-capsule {
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-radius: 6px;
      padding: 6px 12px;
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 110px;

      .cap-label {
        font-size: 11px;
        color: #94a3b8;
      }

      .cap-val {
        font-size: 14px;
        font-weight: 600;
        font-family: monospace;
        color: #f1f5f9;

        &.highlight-blue { color: #38bdf8; }
        &.highlight-gold { color: #fbbf24; }
        &.highlight-green { color: #34d399; }
      }

      &.has-error .cap-val { color: #f87171; }
    }
  }

  .timeline-ruler-wrap {
    display: flex;
    align-items: center;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
    padding-bottom: 6px;
    font-size: 11px;
    color: #64748b;

    .timeline-name-spacer {
      width: 320px;
      flex-shrink: 0;
      padding-left: 8px;
    }

    .timeline-ticks {
      flex: 1;
      position: relative;
      height: 16px;

      .tick-mark {
        position: absolute;
        transform: translateX(-50%);
        font-family: monospace;
      }
    }
  }

  .waterfall-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
    max-height: 480px;
    overflow-y: auto;

    .waterfall-row {
      display: flex;
      align-items: center;
      height: 32px;
      border-radius: 4px;
      cursor: pointer;
      transition: background 0.15s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.04);
      }

      &.is-selected {
        background: rgba(56, 189, 248, 0.08);
      }

      .row-tree-cell {
        width: 320px;
        flex-shrink: 0;
        display: flex;
        align-items: center;
        gap: 6px;
        overflow: hidden;

        .span-type-tag {
          font-size: 9px;
          padding: 1px 4px;
          border-radius: 3px;
          background: rgba(148, 163, 184, 0.15);
          color: #94a3b8;

          &.agent_reasoning { background: rgba(56, 189, 248, 0.15); color: #38bdf8; }
          &.swarm_debate { background: rgba(168, 85, 247, 0.15); color: #c084fc; }
          &.tool_mcp { background: rgba(251, 146, 60, 0.15); color: #fb923c; }
          &.graph_rag { background: rgba(52, 211, 153, 0.15); color: #34d399; }
        }

        .span-name-text {
          font-size: 12px;
          color: #cbd5e1;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .critical-badge {
          font-size: 9px;
          font-weight: 700;
          color: #fbbf24;
          background: rgba(251, 191, 36, 0.15);
          padding: 1px 3px;
          border-radius: 2px;
        }
      }

      .row-gantt-cell {
        flex: 1;
        position: relative;
        height: 100%;
        display: flex;
        align-items: center;

        .gantt-bar-wrap {
          position: absolute;
          height: 18px;
          display: flex;
          align-items: center;

          .gantt-bar {
            width: 100%;
            height: 100%;
            border-radius: 3px;
            background: rgba(56, 189, 248, 0.4);
            border: 1px solid rgba(56, 189, 248, 0.6);
            display: flex;
            align-items: center;
            padding-left: 6px;
            overflow: hidden;

            &.success {
              background: rgba(52, 211, 153, 0.35);
              border-color: rgba(52, 211, 153, 0.55);
            }

            &.failed {
              background: rgba(248, 113, 113, 0.45);
              border-color: rgba(248, 113, 113, 0.7);
            }

            &.critical-pulse {
              box-shadow: 0 0 10px rgba(251, 191, 36, 0.4);
              border-color: #fbbf24;
            }

            .bar-duration-label {
              font-size: 10px;
              font-family: monospace;
              color: #f8fafc;
              white-space: nowrap;
            }
          }
        }
      }
    }
  }

  .detail-section {
    margin-bottom: 16px;

    .detail-label {
      font-size: 12px;
      color: #94a3b8;
      margin-bottom: 4px;
    }

    .detail-val {
      font-size: 13px;
      color: #e2e8f0;

      &.bold { font-weight: 600; }
    }

    .detail-code-block {
      background: rgba(0, 0, 0, 0.3);
      padding: 8px 12px;
      border-radius: 6px;
      font-family: monospace;
      font-size: 12px;
      color: #cbd5e1;
      white-space: pre-wrap;
      word-break: break-all;
      max-height: 180px;
      overflow-y: auto;
    }
  }
}
</style>

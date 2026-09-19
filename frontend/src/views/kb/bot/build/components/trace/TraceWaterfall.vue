<template>
  <div class="trace-waterfall-container">
    <!-- 顶部状态栏与关键指标 -->
    <div class="waterfall-header">
      <div class="stats-group">
        <span class="stat-badge">
          <span class="badge-label">总耗时:</span>
          <span class="badge-value highlight">{{ timelineStats.totalDurationMs.toFixed(1) }}ms</span>
        </span>
        <span class="stat-badge">
          <span class="badge-label">关键路径耗时:</span>
          <span class="badge-value amber">{{ timelineStats.criticalPathDurationMs.toFixed(1) }}ms</span>
        </span>
        <span class="stat-badge">
          <span class="badge-label">Tokens 消耗:</span>
          <span class="badge-value emerald">{{ timelineStats.totalTokens }}</span>
        </span>
        <span class="stat-badge">
          <span class="badge-label">Span 总数:</span>
          <span class="badge-value">{{ timelineStats.totalSpans }}</span>
        </span>
      </div>
      <div class="legend-group">
        <span class="legend-item"><span class="legend-dot critical"></span>CPM 关键路径</span>
        <span class="legend-item"><span class="legend-dot normal"></span>普通调用</span>
        <span class="legend-item"><span class="legend-dot active"></span>画布锚定节点</span>
      </div>
    </div>

    <!-- 瀑布流甘特图主体 -->
    <div class="waterfall-body" ref="bodyRef">
      <div class="timeline-ruler">
        <div class="ruler-mark" style="left: 0%">0ms</div>
        <div class="ruler-mark" style="left: 25%">{{ (timelineStats.totalDurationMs * 0.25).toFixed(0) }}ms</div>
        <div class="ruler-mark" style="left: 50%">{{ (timelineStats.totalDurationMs * 0.5).toFixed(0) }}ms</div>
        <div class="ruler-mark" style="left: 75%">{{ (timelineStats.totalDurationMs * 0.75).toFixed(0) }}ms</div>
        <div class="ruler-mark" style="left: 100%">{{ timelineStats.totalDurationMs.toFixed(0) }}ms</div>
      </div>

      <div class="spans-list">
        <div
          v-for="span in processedSpans"
          :key="span.spanId"
          class="span-row"
          :class="{
            'is-critical': span.isCriticalPath,
            'is-active': isSpanActive(span)
          }"
          @click="onSpanClick(span)"
        >
          <!-- 左侧节点名称与信息 -->
          <div class="span-info" :style="{ paddingLeft: `${span.depth * 18 + 12}px` }">
            <span class="span-toggle-icon" v-if="span.hasChildren">▼</span>
            <span class="span-name">{{ span.spanName }}</span>
            <span class="span-type-tag" :class="span.spanType.toLowerCase()">{{ span.spanType }}</span>
            <span class="node-id-tag" v-if="getNodeId(span)">
              #{{ getNodeId(span) }}
            </span>
          </div>

          <!-- 右侧甘特图条柱 -->
          <div class="span-track">
            <div
              class="span-bar"
              :class="{
                'critical-bar': span.isCriticalPath,
                'active-bar': isSpanActive(span)
              }"
              :style="{
                left: `${span.offsetPercent}%`,
                width: `${Math.max(span.widthPercent, 0.8)}%`
              }"
            >
              <span class="bar-label">{{ span.durationMs.toFixed(1) }}ms</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, type PropType } from 'vue';
import {
  WaterfallVirtualTimelineEngine,
  type RawTraceSpan,
  type WaterfallSpanView,
  type WaterfallTimelineStats
} from '@/views/kd/observability/engine/WaterfallVirtualTimelineEngine';

const props = defineProps({
  traceSpans: {
    type: Array as PropType<RawTraceSpan[]>,
    default: () => []
  },
  activeNodeId: {
    type: String,
    default: ''
  }
});

const emit = defineEmits<{
  (e: 'span-click', payload: { span: WaterfallSpanView; nodeId: string | null }): void;
}>();

const bodyRef = ref<HTMLElement | null>(null);
const timelineEngine = new WaterfallVirtualTimelineEngine();

const timelineResult = computed(() => {
  return timelineEngine.processTimeline(props.traceSpans);
});

const processedSpans = computed<WaterfallSpanView[]>(() => {
  return timelineResult.value.spans;
});

const timelineStats = computed<WaterfallTimelineStats>(() => {
  return timelineResult.value.stats;
});

function getNodeId(span: WaterfallSpanView): string | null {
  return span.attributes?.['dsl.node_id'] || span.attributes?.['nodeId'] || null;
}

function isSpanActive(span: WaterfallSpanView): boolean {
  const nodeId = getNodeId(span);
  return Boolean(nodeId && props.activeNodeId && nodeId === props.activeNodeId);
}

function onSpanClick(span: WaterfallSpanView) {
  const nodeId = getNodeId(span);
  emit('span-click', { span, nodeId });
}
</script>

<style scoped>
.trace-waterfall-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  background: rgba(10, 10, 12, 0.85);
  backdrop-filter: blur(24px) saturate(190%);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  color: #e4e4e7;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  user-select: none;
}

.waterfall-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: rgba(18, 18, 22, 0.6);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 12px;
}

.stats-group {
  display: flex;
  gap: 16px;
}

.stat-badge {
  display: flex;
  align-items: center;
  gap: 4px;
}

.badge-label {
  color: #a1a1aa;
}

.badge-value {
  font-weight: 600;
  color: #f4f4f5;
}

.badge-value.highlight {
  color: #38bdf8;
}

.badge-value.amber {
  color: #fbbf24;
}

.badge-value.emerald {
  color: #34d399;
}

.legend-group {
  display: flex;
  gap: 12px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #71717a;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-dot.critical {
  background: #f59e0b;
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.6);
}

.legend-dot.normal {
  background: #38bdf8;
}

.legend-dot.active {
  background: #a855f7;
  box-shadow: 0 0 8px rgba(168, 85, 247, 0.8);
}

.waterfall-body {
  flex: 1;
  overflow-y: auto;
  position: relative;
  padding-bottom: 12px;
}

.timeline-ruler {
  position: relative;
  height: 20px;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.08);
  margin-left: 280px;
}

.ruler-mark {
  position: absolute;
  top: 2px;
  transform: translateX(-50%);
  font-size: 10px;
  color: #52525b;
}

.spans-list {
  display: flex;
  flex-direction: column;
}

.span-row {
  display: flex;
  align-items: center;
  height: 32px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.03);
  cursor: pointer;
  transition: background 0.15s ease;
}

.span-row:hover {
  background: rgba(255, 255, 255, 0.04);
}

.span-row.is-active {
  background: rgba(168, 85, 247, 0.12);
  border-left: 3px solid #a855f7;
}

.span-info {
  width: 280px;
  min-width: 280px;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.span-toggle-icon {
  font-size: 9px;
  color: #71717a;
}

.span-name {
  font-size: 12px;
  color: #e4e4e7;
  overflow: hidden;
  text-overflow: ellipsis;
}

.span-type-tag {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.06);
  color: #a1a1aa;
}

.span-type-tag.agent {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
}

.span-type-tag.mcp {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
}

.span-type-tag.llm {
  background: rgba(244, 63, 94, 0.15);
  color: #fb7185;
}

.node-id-tag {
  font-size: 10px;
  color: #a855f7;
  font-family: monospace;
}

.span-track {
  flex: 1;
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
}

.span-bar {
  position: absolute;
  height: 14px;
  border-radius: 3px;
  background: rgba(56, 189, 248, 0.5);
  border: 1px solid rgba(56, 189, 248, 0.8);
  display: flex;
  align-items: center;
  padding: 0 4px;
  transition: all 0.2s ease;
}

.span-bar.critical-bar {
  background: rgba(245, 158, 11, 0.5);
  border-color: rgba(245, 158, 11, 0.9);
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.3);
}

.span-bar.active-bar {
  background: rgba(168, 85, 247, 0.6);
  border-color: rgba(168, 85, 247, 1);
  box-shadow: 0 0 10px rgba(168, 85, 247, 0.5);
}

.bar-label {
  font-size: 9px;
  color: #ffffff;
  white-space: nowrap;
}
</style>

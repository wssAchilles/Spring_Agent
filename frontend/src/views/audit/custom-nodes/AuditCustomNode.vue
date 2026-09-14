<template>
  <div
    class="audit-custom-node"
    :class="[
      `node-type-${nodeTypeLower}`,
      {
        'is-dimmed': isDimmed,
        'is-causal-active': isCausalActive
      }
    ]"
  >
    <!-- 输入连接桩 (Target Handle) -->
    <Handle
      type="target"
      :position="Position.Left"
      class="node-handle handle-left"
    />

    <!-- 节点头部 -->
    <div class="node-header">
      <div class="header-left">
        <span class="stage-tag">{{ stageLabel }}</span>
      </div>
      <div class="header-right">
        <span class="status-pill" :class="statusClass">{{ data?.securityAuditStatus || 'PASSED' }}</span>
      </div>
    </div>

    <!-- 节点主体 -->
    <div class="node-body">
      <div class="node-title" :title="data?.description || data?.label">
        {{ data?.description || data?.label || '未命名阶段' }}
      </div>
      <div class="hash-fingerprint" v-if="data?.outputHash">
        <span class="prefix">FP:</span>
        <span class="mono">{{ formatHash(data.outputHash) }}</span>
      </div>
    </div>

    <!-- 节点底部: 归因权重与微时间戳 -->
    <div class="node-footer" v-if="data?.attributionWeight !== undefined">
      <div class="attribution-row">
        <span class="attr-label">沙普利贡献</span>
        <span class="attr-val mono">{{ Math.round((data.attributionWeight || 0) * 100) }}%</span>
      </div>
      <div class="attr-progress-bar">
        <div class="bar-fill" :style="{ width: `${(data.attributionWeight || 0) * 100}%` }" />
      </div>
    </div>

    <!-- 输出连接桩 (Source Handle) -->
    <Handle
      type="source"
      :position="Position.Right"
      class="node-handle handle-right"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Handle, Position } from '@vue-flow/core';

const props = defineProps<{
  id: string;
  data: any;
  isDimmed?: boolean;
  isCausalActive?: boolean;
}>();

const nodeTypeLower = computed(() => {
  const type = props.data?.nodeType || props.data?.type || 'query';
  return String(type).toLowerCase();
});

const stageLabel = computed(() => {
  const t = String(props.data?.nodeType || '').toUpperCase();
  switch (t) {
    case 'QUERY': return '01. QUERY';
    case 'GUARDRAIL_SANITIZED': return '02. GUARDRAIL';
    case 'SLA_ROUTED': return '03. SLA ROUTE';
    case 'INTENT_DECOMPOSITION': return '04. INTENT';
    case 'KNOWLEDGE_RETAINED': return '04. KNOWLEDGE';
    case 'SUBGRAPH_PATHS': return '05. GRAPH';
    case 'BFT_CONSENSUS': return '06. CONSENSUS';
    case 'FINAL_OUTPUT': return '07. OUTPUT';
    case 'MERKLE_ANCHOR': return '08. MERKLE';
    default: return t || 'STAGE';
  }
});

const statusClass = computed(() => {
  const st = String(props.data?.securityAuditStatus || 'PASSED').toUpperCase();
  if (st === 'BLOCKED') return 'status-blocked';
  if (st === 'REDACTED') return 'status-redacted';
  return 'status-passed';
});

function formatHash(hash: string): string {
  if (!hash) return '';
  if (hash.length <= 12) return hash;
  return `${hash.substring(0, 6)}...${hash.substring(hash.length - 4)}`;
}
</script>

<style scoped lang="scss">
.audit-custom-node {
  width: 240px;
  min-height: 105px;
  background: var(--glass-l2-bg, rgba(32, 32, 38, 0.88));
  backdrop-filter: blur(var(--glass-l2-blur, 8px));
  border: 1px solid var(--mono-border-subtle, rgba(255, 255, 255, 0.12));
  border-radius: 8px;
  padding: 10px 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  box-sizing: border-box;
  position: relative;
  cursor: pointer;

  &:hover {
    border-color: var(--mono-border-hover, rgba(255, 255, 255, 0.35));
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.6);
  }

  &.is-dimmed {
    opacity: 0.18;
    filter: grayscale(0.8);
    pointer-events: none;
  }

  &.is-causal-active {
    border-color: #EDEDEF !important;
    box-shadow: 0 0 20px rgba(237, 237, 239, 0.5) !important;
    transform: scale(1.03);
    z-index: 5;
  }

  .node-handle {
    width: 8px;
    height: 8px;
    background: #EDEDEF;
    border: 2px solid #0A0A0C;
    border-radius: 50%;
  }

  .node-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;

    .stage-tag {
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 0.05em;
      color: var(--mono-text-muted, #9E9EA4);
      text-transform: uppercase;
    }

    .status-pill {
      font-size: 9px;
      padding: 1px 6px;
      border-radius: 4px;
      font-weight: 600;
      font-family: 'JetBrains Mono', monospace;

      &.status-passed {
        background: rgba(16, 185, 129, 0.15);
        color: #10B981;
        border: 1px solid rgba(16, 185, 129, 0.3);
      }

      &.status-redacted {
        background: rgba(245, 158, 11, 0.15);
        color: #F59E0B;
        border: 1px solid rgba(245, 158, 11, 0.3);
      }

      &.status-blocked {
        background: rgba(239, 68, 68, 0.15);
        color: #EF4444;
        border: 1px solid rgba(239, 68, 68, 0.3);
      }
    }
  }

  .node-body {
    .node-title {
      font-size: 12px;
      font-weight: 600;
      color: var(--mono-text-primary, #EDEDEF);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      margin-bottom: 4px;
    }

    .hash-fingerprint {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 10px;
      color: var(--mono-text-secondary, #71717A);

      .prefix {
        font-weight: 700;
      }

      .mono {
        font-family: 'JetBrains Mono', monospace;
      }
    }
  }

  .node-footer {
    margin-top: 8px;
    padding-top: 6px;
    border-top: 1px solid rgba(255, 255, 255, 0.06);

    .attribution-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 10px;
      margin-bottom: 3px;
      color: var(--mono-text-muted, #9E9EA4);

      .attr-val {
        font-weight: 700;
        color: #EDEDEF;
      }
    }

    .attr-progress-bar {
      width: 100%;
      height: 3px;
      background: rgba(255, 255, 255, 0.08);
      border-radius: 2px;
      overflow: hidden;

      .bar-fill {
        height: 100%;
        background: linear-gradient(90deg, #6366F1, #EDEDEF);
        border-radius: 2px;
        transition: width 0.4s ease;
      }
    }
  }
}
</style>

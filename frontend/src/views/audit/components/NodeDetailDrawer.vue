<template>
  <el-drawer
    v-model="visibleModel"
    title="神经符号微阶段审计详情"
    direction="rtl"
    size="460px"
    class="node-detail-drawer"
    :destroy-on-close="true"
  >
    <div class="drawer-content" v-if="nodeData">
      <!-- 顶部阶段概览卡片 -->
      <div class="meta-hero-card">
        <div class="meta-row">
          <span class="label">NODE ID</span>
          <span class="value mono text-truncate" :title="nodeData.id">{{ nodeData.id }}</span>
        </div>
        <div class="meta-row">
          <span class="label">STAGE TYPE</span>
          <span class="value stage-badge">{{ nodeData.data?.nodeType || 'STAGE' }}</span>
        </div>
        <div class="meta-row">
          <span class="label">AUDIT VERDICT</span>
          <el-tag :type="statusTagType" size="small" effect="plain">{{ nodeData.data?.securityAuditStatus || 'PASSED' }}</el-tag>
        </div>
        <div class="meta-row">
          <span class="label">SHAPLEY ATTRIBUTION</span>
          <span class="value mono text-bold">{{ Math.round((nodeData.data?.attributionWeight || 0) * 100) }}%</span>
        </div>
      </div>

      <!-- 因果回溯快捷触发 -->
      <div class="action-trigger-box">
        <el-button
          type="primary"
          class="full-width-btn"
          @click="handleTriggerCausal"
        >
          从该节点反向因果溯源 (Trace Backward)
        </el-button>
      </div>

      <!-- 节点哈希指纹对账 -->
      <div class="section-title">密码学指纹核验 (Fingerprint)</div>
      <div class="hash-spec-box">
        <div class="hash-field">
          <div class="field-name">INPUT PAYLOAD HASH</div>
          <div class="field-val mono">{{ nodeData.data?.inputHash || 'N/A' }}</div>
        </div>
        <div class="hash-field">
          <div class="field-name">OUTPUT PAYLOAD HASH</div>
          <div class="field-val mono text-success">{{ nodeData.data?.outputHash || 'N/A' }}</div>
        </div>
      </div>

      <!-- 节点语义描述与载荷详情 -->
      <div class="section-title">阶段描述与执行摘要</div>
      <div class="payload-box">
        <p class="desc-text">{{ nodeData.data?.description || '无详细描述' }}</p>
        <pre class="json-code" v-if="nodeData.data"><code>{{ formatDataJson(nodeData.data) }}</code></pre>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  visible: boolean;
  nodeData: any;
}>();

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void;
  (e: 'trigger-causal-trace', nodeId: string): void;
}>();

const visibleModel = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
});

const statusTagType = computed(() => {
  const st = String(props.nodeData?.data?.securityAuditStatus || 'PASSED').toUpperCase();
  if (st === 'BLOCKED') return 'danger';
  if (st === 'REDACTED') return 'warning';
  return 'success';
});

function handleTriggerCausal() {
  if (props.nodeData?.id) {
    emit('trigger-causal-trace', props.nodeData.id);
  }
}

function formatDataJson(data: any): string {
  try {
    return JSON.stringify(data, null, 2);
  } catch {
    return String(data);
  }
}
</script>

<style scoped lang="scss">
.node-detail-drawer {
  :deep(.el-drawer) {
    background: var(--glass-l3-bg, rgba(26, 26, 32, 0.96)) !important;
    backdrop-filter: blur(12px) !important;
    border-left: 1px solid var(--mono-border-strong, rgba(255, 255, 255, 0.15)) !important;
  }

  :deep(.el-drawer__header) {
    margin-bottom: 12px;
    padding: 16px 20px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
    color: var(--mono-text-primary, #EDEDEF);
    font-weight: 600;
  }

  .drawer-content {
    padding: 0 10px;

    .meta-hero-card {
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 8px;
      padding: 14px;
      margin-bottom: 16px;

      .meta-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 8px;
        font-size: 12px;

        &:last-child {
          margin-bottom: 0;
        }

        .label {
          color: var(--mono-text-muted, #9E9EA4);
          font-weight: 500;
        }

        .value {
          color: var(--mono-text-primary, #EDEDEF);

          &.mono {
            font-family: 'JetBrains Mono', monospace;
          }

          &.stage-badge {
            font-weight: 700;
            color: #6366F1;
          }
        }
      }
    }

    .action-trigger-box {
      margin-bottom: 20px;

      .full-width-btn {
        width: 100%;
        font-weight: 600;
      }
    }

    .section-title {
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.05em;
      color: var(--mono-text-muted, #9E9EA4);
      margin-bottom: 8px;
      text-transform: uppercase;
    }

    .hash-spec-box {
      background: rgba(0, 0, 0, 0.4);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 6px;
      padding: 12px;
      margin-bottom: 20px;

      .hash-field {
        margin-bottom: 10px;

        &:last-child {
          margin-bottom: 0;
        }

        .field-name {
          font-size: 10px;
          font-weight: 700;
          color: #71717A;
          margin-bottom: 2px;
        }

        .field-val {
          font-family: 'JetBrains Mono', monospace;
          font-size: 11px;
          word-break: break-all;
          color: #D4D4D8;
        }
      }
    }

    .payload-box {
      .desc-text {
        font-size: 13px;
        color: #EDEDEF;
        line-height: 1.5;
        margin-bottom: 10px;
      }

      .json-code {
        background: #0D0D11;
        border: 1px solid rgba(255, 255, 255, 0.06);
        border-radius: 6px;
        padding: 10px;
        font-family: 'JetBrains Mono', monospace;
        font-size: 11px;
        color: #A1A1AA;
        max-height: 240px;
        overflow: auto;
      }
    }
  }
}
</style>

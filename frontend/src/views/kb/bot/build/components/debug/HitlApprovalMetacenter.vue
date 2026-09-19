<template>
  <Teleport to="body">
    <Transition name="hitl-slide">
      <div v-if="visible" class="hitl-metacenter-backdrop" @click.self="handleDismiss">
        <aside class="hitl-metacenter-drawer">
          <!-- 1. 顶部标题栏与单色钛金状态指示器 -->
          <header class="metacenter-header">
            <div class="header-left">
              <span class="amber-radar-pulse"></span>
              <div class="title-group">
                <h3 class="drawer-heading">人机协同审批决策中枢 (HITL Metacenter)</h3>
                <span class="drawer-subheading">节点挂起等待人工干预与现场热补丁 (Hot Patching)</span>
              </div>
            </div>
            <div class="header-right">
              <span class="ticket-badge">工单: {{ activeTicket?.ticketId || 'HITL_PENDING' }}</span>
              <button class="close-btn" @click="handleDismiss">×</button>
            </div>
          </header>

          <!-- 2. 节点拓扑因果上下文元数据卡片 -->
          <section class="context-card">
            <div class="context-grid">
              <div class="context-item">
                <span class="context-label">挂起节点:</span>
                <span class="context-value font-mono">{{ activeTicket?.nodeName || '审批节点' }} ({{ activeTicket?.nodeId || 'N_HITL' }})</span>
              </div>
              <div class="context-item">
                <span class="context-label">当前步数:</span>
                <span class="context-value">第 {{ activeTicket?.stepIndex ?? 0 }} 步</span>
              </div>
              <div class="context-item">
                <span class="context-label">风险评级:</span>
                <span class="risk-badge" :class="riskClass">{{ activeTicket?.riskLevel || 'HIGH_RISK_MANUAL' }}</span>
              </div>
              <div class="context-item">
                <span class="context-label">溯源批次:</span>
                <span class="context-value font-mono">{{ activeTicket?.executionBatchId || 'BATCH_TRACE' }}</span>
              </div>
            </div>
          </section>

          <!-- 3. 因果前向影响锥与差异比对工作区 -->
          <section class="diff-workspace">
            <div class="workspace-tabs">
              <span class="tab-title">运行时变量热补丁审查与现场纠偏</span>
              <span class="mode-tag">结构共享隔离 · 零反向时空污染</span>
            </div>

            <div class="diff-split-container">
              <!-- 左栏: 原始上游变量输入 (只读基线) -->
              <div class="diff-column baseline-col">
                <div class="col-header">
                  <span class="col-title">原始上游变量 (Baseline Inputs)</span>
                  <span class="badge-readonly">只读</span>
                </div>
                <div class="code-viewer font-mono">
                  <pre><code>{{ formattedOriginalJson }}</code></pre>
                </div>
              </div>

              <!-- 右栏: 在线热补丁编辑区 (Editable Hot Patch) -->
              <div class="diff-column hotpatch-col">
                <div class="col-header">
                  <span class="col-title">现场热补丁注入 (Hotpatch Override)</span>
                  <span class="badge-live">在线编辑</span>
                </div>
                <div class="code-editor font-mono">
                  <textarea
                    v-model="hotPatchJsonText"
                    class="hotpatch-textarea"
                    placeholder="请输入合法的 JSON 变量热补丁..."
                    spellcheck="false"
                  ></textarea>
                </div>
                <div v-if="jsonError" class="json-error-bar">
                  <span class="err-icon">✕</span> {{ jsonError }}
                </div>
              </div>
            </div>
          </section>

          <!-- 4. 密码学存证凭单哈希自验真展示 -->
          <section class="crypto-receipt-bar">
            <div class="receipt-icon">🔐</div>
            <div class="receipt-info font-mono">
              <div class="receipt-row">
                <span class="key">待签 SHA-256 摘要:</span>
                <span class="hash">{{ computedPatchHash }}</span>
              </div>
              <div class="receipt-row">
                <span class="key">审批人鉴权 ID:</span>
                <span class="val">{{ currentOperatorId }}</span>
              </div>
            </div>
          </section>

          <!-- 5. 底部审批决策操作栏 -->
          <footer class="metacenter-footer">
            <div class="notice-text">
              点击“批准并热修改放行”将自动派生出独立时间分叉并生成密码学存证凭单。
            </div>
            <div class="footer-actions">
              <button class="btn-glass-secondary" :disabled="submitting" @click="handleReject">
                拒绝并终止 (Reject)
              </button>
              <button class="btn-titanium-primary" :disabled="submitting || !!jsonError" @click="handleApproveWithPatch">
                <span v-if="submitting">签发凭单中...</span>
                <span v-else>批准并热修改放行 (Approve & Patch)</span>
              </button>
            </div>
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { computeSha256 } from './engine/PersistentSnapshotTree.js';

export interface HitlTicketContext {
  ticketId: string;
  workflowId: string;
  nodeId: string;
  nodeName: string;
  stepIndex: number;
  executionBatchId?: string;
  riskLevel?: 'CRITICAL_DESTRUCTIVE' | 'HIGH_RISK_MANUAL' | 'SENSITIVE_AUDIT';
  originalVariables: Record<string, any>;
  stagingPatchVariables?: Record<string, any>;
  operatorUserId?: string;
}

const props = defineProps<{
  visible: boolean;
  activeTicket: HitlTicketContext | null;
}>();

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void;
  (e: 'approved', payload: {
    ticketId: string;
    stepIndex: number;
    decision: 'APPROVED';
    hotPatches: Record<string, any>;
    hotPatchHash: string;
    operatorId: string;
    timestamp: number;
  }): void;
  (e: 'rejected', payload: {
    ticketId: string;
    stepIndex: number;
    decision: 'REJECTED';
    reason: string;
  }): void;
}>();

const submitting = ref(false);
const hotPatchJsonText = ref('{}');
const jsonError = ref<string | null>(null);

const currentOperatorId = computed(() => {
  return props.activeTicket?.operatorUserId || 'architect-auditor-01';
});

const riskClass = computed(() => {
  const r = props.activeTicket?.riskLevel;
  if (r === 'CRITICAL_DESTRUCTIVE') return 'risk-critical';
  if (r === 'SENSITIVE_AUDIT') return 'risk-audit';
  return 'risk-high';
});

const formattedOriginalJson = computed(() => {
  if (!props.activeTicket?.originalVariables) return '{}';
  return JSON.stringify(props.activeTicket.originalVariables, null, 2);
});

// 监听弹窗打开，初始化热补丁文本
watch(
  () => props.visible,
  (val) => {
    if (val && props.activeTicket) {
      const initialPatch = props.activeTicket.stagingPatchVariables || props.activeTicket.originalVariables || {};
      hotPatchJsonText.value = JSON.stringify(initialPatch, null, 2);
      jsonError.value = null;
    }
  },
  { immediate: true }
);

// 监听热补丁 JSON 实时语法校验
watch(hotPatchJsonText, (text) => {
  try {
    JSON.parse(text);
    jsonError.value = null;
  } catch (err: any) {
    jsonError.value = `JSON 语法错误: ${err.message}`;
  }
});

// 实时计算待提交热补丁的 SHA-256 自签名哈希
const computedPatchHash = computed(() => {
  try {
    const parsed = JSON.parse(hotPatchJsonText.value);
    const sortedJson = JSON.stringify(parsed, Object.keys(parsed).sort());
    return computeSha256(sortedJson);
  } catch {
    return '0000000000000000000000000000000000000000000000000000000000000000';
  }
});

const handleApproveWithPatch = () => {
  if (!props.activeTicket) return;
  try {
    submitting.value = true;
    const hotPatches = JSON.parse(hotPatchJsonText.value);
    const hotPatchHash = computedPatchHash.value;

    emit('approved', {
      ticketId: props.activeTicket.ticketId,
      stepIndex: props.activeTicket.stepIndex,
      decision: 'APPROVED',
      hotPatches,
      hotPatchHash,
      operatorId: currentOperatorId.value,
      timestamp: Date.now()
    });
    emit('update:visible', false);
  } catch (e: any) {
    jsonError.value = `无法签署提交: ${e.message}`;
  } finally {
    submitting.value = false;
  }
};

const handleReject = () => {
  if (!props.activeTicket) return;
  emit('rejected', {
    ticketId: props.activeTicket.ticketId,
    stepIndex: props.activeTicket.stepIndex,
    decision: 'REJECTED',
    reason: '人工审核人明确拒绝放行 (Manual Fail-Close)'
  });
  emit('update:visible', false);
};

const handleDismiss = () => {
  // Fail-Close 原则: 点击遮罩仅关闭抽屉，不放行流程
  emit('update:visible', false);
};
</script>

<style scoped lang="scss">
// 单色钛金毛玻璃设计系统 Token 遵循 UI/UX Pro Max 规范
.hitl-metacenter-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2500;
  background: rgba(2, 2, 3, 0.72);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  justify-content: flex-end;
}

.hitl-metacenter-drawer {
  width: 720px;
  height: 100vh;
  background: rgba(10, 10, 12, 0.94);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-left: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: -20px 0 60px rgba(0, 0, 0, 0.8);
  display: flex;
  flex-direction: column;
  padding: 24px;
  color: #ededef;
  box-sizing: border-box;
}

.metacenter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .amber-radar-pulse {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: #f59e0b;
      box-shadow: 0 0 12px #f59e0b;
      animation: pulse-glow 1.8s infinite ease-in-out;
    }

    .title-group {
      .drawer-heading {
        margin: 0;
        font-size: 16px;
        font-weight: 600;
        color: #ededef;
        letter-spacing: -0.01em;
      }
      .drawer-subheading {
        font-size: 12px;
        color: #8a8f98;
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 12px;

    .ticket-badge {
      font-family: monospace;
      font-size: 12px;
      padding: 3px 8px;
      border-radius: 4px;
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.1);
      color: #d1d5db;
    }

    .close-btn {
      background: transparent;
      border: none;
      color: #8a8f98;
      font-size: 20px;
      cursor: pointer;
      line-height: 1;
      padding: 4px 8px;
      border-radius: 4px;
      transition: color 0.15s;

      &:hover {
        color: #ededef;
        background: rgba(255, 255, 255, 0.08);
      }
    }
  }
}

.context-card {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 8px;
  background: #050506;
  border: 1px solid rgba(255, 255, 255, 0.08);

  .context-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 10px 16px;

    .context-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;

      .context-label {
        color: #8a8f98;
      }
      .context-value {
        color: #ededef;
        font-weight: 500;
      }
    }
  }
}

.risk-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;

  &.risk-critical {
    background: rgba(239, 68, 68, 0.15);
    color: #ef4444;
    border: 1px solid rgba(239, 68, 68, 0.35);
  }
  &.risk-high {
    background: rgba(245, 158, 11, 0.15);
    color: #f59e0b;
    border: 1px solid rgba(245, 158, 11, 0.35);
  }
  &.risk-audit {
    background: rgba(59, 130, 246, 0.15);
    color: #3b82f6;
    border: 1px solid rgba(59, 130, 246, 0.35);
  }
}

.diff-workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-top: 16px;
  min-height: 280px;

  .workspace-tabs {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 8px;

    .tab-title {
      font-size: 13px;
      font-weight: 600;
      color: #ededef;
    }
    .mode-tag {
      font-size: 11px;
      color: #8a8f98;
    }
  }

  .diff-split-container {
    flex: 1;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
    height: 100%;

    .diff-column {
      display: flex;
      flex-direction: column;
      border-radius: 8px;
      background: #020203;
      border: 1px solid rgba(255, 255, 255, 0.08);
      overflow: hidden;

      .col-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 8px 12px;
        background: #0a0a0c;
        border-bottom: 1px solid rgba(255, 255, 255, 0.06);

        .col-title {
          font-size: 12px;
          color: #8a8f98;
        }

        .badge-readonly {
          font-size: 10px;
          padding: 1px 5px;
          border-radius: 3px;
          background: rgba(255, 255, 255, 0.06);
          color: #8a8f98;
        }

        .badge-live {
          font-size: 10px;
          padding: 1px 5px;
          border-radius: 3px;
          background: rgba(16, 185, 129, 0.15);
          color: #10b981;
          border: 1px solid rgba(16, 185, 129, 0.3);
        }
      }

      .code-viewer,
      .code-editor {
        flex: 1;
        overflow: auto;
        padding: 12px;
        font-size: 12px;
        line-height: 1.5;

        pre {
          margin: 0;
          color: #d1d5db;
        }
      }

      .hotpatch-textarea {
        width: 100%;
        height: 100%;
        min-height: 180px;
        background: transparent;
        border: none;
        outline: none;
        resize: none;
        color: #f3f4f6;
        font-family: Menlo, Monaco, Consolas, monospace;
        font-size: 12px;
        line-height: 1.5;
        box-sizing: border-box;
      }

      .json-error-bar {
        padding: 6px 12px;
        background: rgba(239, 68, 68, 0.15);
        border-top: 1px solid rgba(239, 68, 68, 0.3);
        color: #ef4444;
        font-size: 11px;
      }
    }
  }
}

.crypto-receipt-bar {
  margin-top: 14px;
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  gap: 12px;

  .receipt-icon {
    font-size: 18px;
  }

  .receipt-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 11px;

    .receipt-row {
      display: flex;
      gap: 8px;

      .key {
        color: #8a8f98;
      }
      .hash {
        color: #94a3b8;
        letter-spacing: 0.02em;
        word-break: break-all;
      }
      .val {
        color: #ededef;
      }
    }
  }
}

.metacenter-footer {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;

  .notice-text {
    font-size: 11px;
    color: #8a8f98;
    max-width: 320px;
  }

  .footer-actions {
    display: flex;
    gap: 12px;

    .btn-glass-secondary {
      padding: 9px 18px;
      border-radius: 6px;
      background: transparent;
      border: 1px solid rgba(239, 68, 68, 0.4);
      color: #ef4444;
      font-size: 13px;
      cursor: pointer;
      transition: all 0.2s;

      &:hover:not(:disabled) {
        background: rgba(239, 68, 68, 0.15);
      }
    }

    .btn-titanium-primary {
      padding: 9px 22px;
      border-radius: 6px;
      background: #ededef;
      border: none;
      color: #020203;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      box-shadow: 0 0 16px rgba(255, 255, 255, 0.18);
      transition: all 0.2s;

      &:hover:not(:disabled) {
        background: #ffffff;
        box-shadow: 0 0 24px rgba(255, 255, 255, 0.35);
      }

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }
  }
}

@keyframes pulse-glow {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.85); }
}

.hitl-slide-enter-active,
.hitl-slide-leave-active {
  transition: all 0.28s cubic-bezier(0.16, 1, 0.3, 1);
}
.hitl-slide-enter-from,
.hitl-slide-leave-to {
  opacity: 0;
  transform: translateX(100%);
}
</style>

<template>
  <transition name="hitl-fade">
    <!-- 非模态浮动悬浮岛 (非全屏阻塞，允许开发者在审批挂起时自由漫游画布与审查数据) -->
    <div
      v-if="visible"
      class="hitl-floating-island-wrapper"
      :class="{ 'is-minimized': isMinimized }"
    >
      <div class="ios26-liquid-glass-island">
        <!-- 头部: 状态指示、工单号与折叠控制 -->
        <div class="hitl-island-header">
          <div class="header-main-info">
            <span class="warning-aurora-dot"></span>
            <span class="island-headline">人机协同审批挂起 (HITL Gateway)</span>
            <span class="ticket-capsule-badge font-mono">工单: {{ ticket.ticketId }}</span>
          </div>

          <div class="header-actions-group">
            <button
              class="icon-ghost-btn"
              :title="isMinimized ? '展开审批面板' : '最小化挂起'"
              @click="isMinimized = !isMinimized"
            >
              <span>{{ isMinimized ? '▢' : '—' }}</span>
            </button>
            <button class="icon-ghost-btn" title="关闭悬浮岛" @click="$emit('close')">
              <span>✕</span>
            </button>
          </div>
        </div>

        <!-- 最小化态极简提示条 -->
        <div v-if="isMinimized" class="minimized-strip" @click="isMinimized = false">
          <span class="mini-tool font-mono">{{ ticket.toolName }}</span>
          <span class="mini-hint">点击展开审批详情与参数调整</span>
        </div>

        <!-- 展开态完整内容 -->
        <div v-else class="island-expanded-body">
          <!-- 元数据信息条 -->
          <div class="meta-capsules-row">
            <div class="meta-chip">
              <span class="chip-label">目标工具:</span>
              <span class="chip-value font-mono">{{ ticket.toolName }}</span>
            </div>
            <div class="meta-chip">
              <span class="chip-label">服务节点:</span>
              <span class="chip-value">{{ ticket.serverId || 'local-mcp' }}</span>
            </div>
            <div class="meta-chip">
              <span class="chip-label">风险等级:</span>
              <span class="risk-badge font-mono" :class="ticket.riskLevel.toLowerCase()">
                {{ ticket.riskLevel }}
              </span>
            </div>
          </div>

          <!-- 参数编辑与审查面板 (支持原地修改 Hot Patch 与参数 Unified Diff) -->
          <div class="arguments-editor-panel">
            <div class="panel-sub-header">
              <div class="header-label-group">
                <span class="panel-title">执行参数审查与原地调优</span>
                <span class="panel-tip font-mono">可直接修改参数值后触发单步重试</span>
              </div>
              <button
                class="edit-toggle-btn"
                @click="isEditMode = !isEditMode"
              >
                {{ isEditMode ? '完成编辑' : '原地修改参数' }}
              </button>
            </div>

            <!-- 参数列表 (编辑模式与 Diff 模式) -->
            <div class="arguments-list-scroll font-mono">
              <div
                v-for="(val, key) in editablePayload"
                :key="key"
                class="arg-item-row"
                :class="{ 'destructive-highlight': isDestructiveParam(key, val) }"
              >
                <span class="arg-marker">{{ isDestructiveParam(key, val) ? '!' : '+' }}</span>
                <span class="arg-key">{{ key }}:</span>

                <template v-if="isEditMode">
                  <input
                    v-model="editablePayload[key]"
                    type="text"
                    class="arg-inline-input"
                  />
                </template>
                <template v-else>
                  <span class="arg-val">{{ formatValue(val) }}</span>
                </template>
              </div>
            </div>
          </div>

          <!-- 安全爆炸半径与心跳提示 -->
          <div class="safety-advisory-banner">
            <span class="advisory-icon">⚠️</span>
            <span class="advisory-text">
              执行引擎已进入 0 CPU 纯事件挂起守候。确认放行将签发不可变凭单并执行，驳回将自动隔离至三级冷备仓。
            </span>
          </div>

          <!-- 三向流转操作按钮组 (Approve / Hot Patch / Reject) -->
          <div class="hitl-action-footer">
            <button
              class="apple-btn btn-reject"
              :disabled="submitting"
              @click="handleReject"
            >
              <span>✕ 驳回并隔离至冷备仓</span>
            </button>

            <button
              v-if="isEditMode || hasParameterChanged"
              class="apple-btn btn-hot-patch"
              :disabled="submitting"
              @click="handleHotPatch"
            >
              <span>⚡ 原地参数重跑 (Hot Patch)</span>
            </button>

            <button
              class="apple-btn btn-approve"
              :disabled="submitting"
              @click="handleApprove"
            >
              <span v-if="submitting">验真签发中...</span>
              <span v-else>✓ 确认放行 (Approve)</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  HitlFrontendAuditReceipt,
  type HitlFrontendAuditReceiptData
} from './receipt/HitlFrontendAuditReceipt';

export interface HitlTicketData {
  ticketId: string;
  workflowId?: string;
  nodeId?: string;
  stepIndex?: number;
  branchId?: string;
  toolName: string;
  serverId?: string;
  riskLevel: string;
  argumentsPayload: Record<string, unknown>;
  operatorUserId?: string;
  reasoningContent?: string;
}

const props = defineProps<{
  visible: boolean;
  ticket: HitlTicketData;
}>();

const emit = defineEmits<{
  (e: 'approve', receipt: HitlFrontendAuditReceiptData): void;
  (e: 'hotPatch', payload: { receipt: HitlFrontendAuditReceiptData; patchedState: Record<string, unknown> }): void;
  (e: 'reject', receipt: HitlFrontendAuditReceiptData): void;
  (e: 'close'): void;
}>();

const submitting = ref(false);
const isMinimized = ref(false);
const isEditMode = ref(false);

// 可编辑参数镜像副本
const editablePayload = ref<Record<string, unknown>>({});

watch(
  () => props.ticket.argumentsPayload,
  (newVal) => {
    editablePayload.value = JSON.parse(JSON.stringify(newVal || {}));
  },
  { immediate: true, deep: true }
);

const hasParameterChanged = computed(() => {
  return JSON.stringify(editablePayload.value) !== JSON.stringify(props.ticket.argumentsPayload || {});
});

const DESTRUCTIVE_KEYWORDS = ['drop', 'delete', 'truncate', 'rm', 'format', 'kill', 'purge', 'grant', 'revoke'];

function isDestructiveParam(key: string, val: unknown): boolean {
  const k = key.toLowerCase();
  const v = String(val).toLowerCase();
  return DESTRUCTIVE_KEYWORDS.some(kw => k.includes(kw) || v.includes(kw));
}

function formatValue(val: unknown): string {
  if (typeof val === 'object' && val !== null) {
    return JSON.stringify(val);
  }
  return String(val);
}

// 确认通过
function handleApprove() {
  submitting.value = true;
  try {
    const receipt = HitlFrontendAuditReceipt.create(
      props.ticket.workflowId || 'wf_current',
      props.ticket.nodeId || props.ticket.ticketId,
      props.ticket.stepIndex || 1,
      props.ticket.branchId || 'main',
      props.ticket.operatorUserId || 'admin-operator',
      'APPROVE',
      props.ticket.argumentsPayload,
      props.ticket.argumentsPayload,
      props.ticket.reasoningContent || '审批放行'
    );

    if (!receipt.verifySignature()) {
      ElMessage.error('凭单自验真失败，防时序侧信道校验不通过');
      submitting.value = false;
      return;
    }

    emit('approve', receipt);
    ElMessage.success('审批放行凭单已签发，继续执行');
  } catch (err: unknown) {
    ElMessage.error(`审批签发异常: ${String(err)}`);
  } finally {
    submitting.value = false;
  }
}

// 原地修改重跑 (Hot Patch)
function handleHotPatch() {
  submitting.value = true;
  try {
    const receipt = HitlFrontendAuditReceipt.create(
      props.ticket.workflowId || 'wf_current',
      props.ticket.nodeId || props.ticket.ticketId,
      props.ticket.stepIndex || 1,
      props.ticket.branchId || 'main',
      props.ticket.operatorUserId || 'admin-operator',
      'HOT_PATCH',
      props.ticket.argumentsPayload,
      editablePayload.value,
      props.ticket.reasoningContent || '原地修改执行参数'
    );

    if (!receipt.verifySignature()) {
      ElMessage.error('Hot Patch 凭单签名验真失败');
      submitting.value = false;
      return;
    }

    emit('hotPatch', {
      receipt,
      patchedState: editablePayload.value
    });
    ElMessage.success('参数热修补凭单已生成，原地触发单步重试');
  } catch (err: unknown) {
    ElMessage.error(`热修补签发异常: ${String(err)}`);
  } finally {
    submitting.value = false;
  }
}

// 拒绝终止
function handleReject() {
  submitting.value = true;
  try {
    const receipt = HitlFrontendAuditReceipt.create(
      props.ticket.workflowId || 'wf_current',
      props.ticket.nodeId || props.ticket.ticketId,
      props.ticket.stepIndex || 1,
      props.ticket.branchId || 'main',
      props.ticket.operatorUserId || 'admin-operator',
      'REJECT',
      props.ticket.argumentsPayload,
      {},
      props.ticket.reasoningContent || '安全管理员拒绝执行'
    );

    if (!receipt.verifySignature()) {
      ElMessage.error('拒绝凭单签名验真失败');
      submitting.value = false;
      return;
    }

    emit('reject', receipt);
    ElMessage.info('审批已被驳回，状态安全推入三级冷备隔离环形仓');
  } catch (err: unknown) {
    ElMessage.error(`驳回签发异常: ${String(err)}`);
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped lang="scss">
/* ============================================================================
   Apple iOS 26 非模态悬浮液态玻璃岛 (Floating Caustic Island)
   彻底废除 position: fixed; inset: 0 全屏阻塞反模式！
   严格遵守无层叠上下文铁律：
   1. 玻璃卡片自身绝对无 transform、filter、isolation: isolate、z-index
   2. 双层真实高透：::before (plus-lighter 高光) + ::after (blur(50px) + color-dodge)
   3. 子元素通过 > :where(*) 提升
   ============================================================================ */

.hitl-floating-island-wrapper {
  position: absolute;
  top: 24px;
  right: 28px;
  width: 540px;
  max-width: calc(100vw - 56px);
  pointer-events: auto;
  box-sizing: border-box;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro", "PingFang SC", sans-serif;
  letter-spacing: -0.23px;

  &.is-minimized {
    width: 320px;
  }
}

/* 核心高透白晶液态玻璃岛 */
.ios26-liquid-glass-island {
  position: relative;
  border-radius: 24px;
  background: transparent;
  border: 0.5px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  padding: 16px 20px;
  box-sizing: border-box;

  // 底层伪元素：plus-lighter 纯白高光
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: inherit;
    background: rgba(255, 255, 255, 0.82);
    mix-blend-mode: plus-lighter;
    pointer-events: none;
  }

  // 表层伪元素：50px 模糊 + color-dodge 色彩增益
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: inherit;
    backdrop-filter: blur(50px);
    -webkit-backdrop-filter: blur(50px);
    background: rgba(255, 255, 255, 0.28);
    mix-blend-mode: color-dodge;
    pointer-events: none;
  }

  // 内容层：避免文字模糊
  > :where(*) {
    position: relative;
    z-index: 3;
  }
}

.hitl-island-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.06);
}

.header-main-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.warning-aurora-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff8d28; // iOS 26 橙色
  box-shadow: 0 0 10px rgba(255, 141, 40, 0.6);
}

.island-headline {
  font-size: 14px;
  font-weight: 590; // Headline 590 铁律
  color: #000000;
}

.ticket-capsule-badge {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 1000px;
  background: rgba(0, 0, 0, 0.04);
  border: 0.5px solid rgba(0, 0, 0, 0.07);
  color: rgba(60, 60, 67, 0.7);
}

.header-actions-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.icon-ghost-btn {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 0.5px solid rgba(0, 0, 0, 0.08);
  background: rgba(0, 0, 0, 0.03);
  color: #3d3d3d;
  font-size: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.08);
    color: #000000;
  }
}

.minimized-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px 2px 4px;
  cursor: pointer;

  .mini-tool {
    font-size: 11px;
    font-weight: 590;
    color: #0088ff;
  }
  .mini-hint {
    font-size: 11px;
    color: rgba(60, 60, 67, 0.6);
  }
}

.island-expanded-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 10px;
}

.meta-capsules-row {
  display: flex;
  gap: 8px;
}

.meta-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 1000px;
  background: rgba(0, 0, 0, 0.03);
  border: 0.5px solid rgba(0, 0, 0, 0.06);
  font-size: 11px;

  .chip-label {
    color: rgba(60, 60, 67, 0.65);
  }
  .chip-value {
    color: #000000;
    font-weight: 500;
  }
}

.risk-badge {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 590;

  &.high,
  &.critical,
  &.destructive {
    background: rgba(255, 56, 60, 0.12);
    color: #ff383c;
    border: 0.5px solid rgba(255, 56, 60, 0.25);
  }
}

.arguments-editor-panel {
  background: rgba(255, 255, 255, 0.7);
  border: 0.5px solid rgba(0, 0, 0, 0.06);
  border-radius: 14px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.panel-sub-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .header-label-group {
    display: flex;
    flex-direction: column;
    gap: 1px;
  }

  .panel-title {
    font-size: 12px;
    font-weight: 590;
    color: #000000;
  }

  .panel-tip {
    font-size: 10px;
    color: rgba(60, 60, 67, 0.55);
  }

  .edit-toggle-btn {
    font-size: 11px;
    color: #0088ff;
    background: rgba(0, 136, 255, 0.08);
    border: 0.5px solid rgba(0, 136, 255, 0.2);
    padding: 3px 8px;
    border-radius: 1000px;
    cursor: pointer;

    &:hover {
      background: rgba(0, 136, 255, 0.15);
    }
  }
}

.arguments-list-scroll {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 140px;
  overflow-y: auto;
  font-size: 11px;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.1);
    border-radius: 4px;
  }
}

.arg-item-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.02);

  &.destructive-highlight {
    background: rgba(255, 56, 60, 0.08);
    color: #ff383c;
  }

  .arg-marker {
    color: #34c759;
    font-weight: 590;
  }

  &.destructive-highlight .arg-marker {
    color: #ff383c;
  }

  .arg-key {
    color: rgba(60, 60, 67, 0.7);
  }

  .arg-val {
    color: #000000;
    word-break: break-all;
  }

  .arg-inline-input {
    flex: 1;
    border: 0.5px solid rgba(0, 136, 255, 0.4);
    background: #ffffff;
    border-radius: 4px;
    padding: 2px 6px;
    font-size: 11px;
    outline: none;

    &:focus {
      border-color: #0088ff;
      box-shadow: 0 0 0 1px rgba(0, 136, 255, 0.3);
    }
  }
}

.safety-advisory-banner {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 11px;
  color: #3d3d3d;
  background: rgba(255, 141, 40, 0.08);
  border: 0.5px solid rgba(255, 141, 40, 0.22);
  padding: 8px 10px;
  border-radius: 10px;
  line-height: 1.4;

  .advisory-icon {
    font-size: 12px;
  }
}

.hitl-action-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
}

.apple-btn {
  padding: 7px 14px;
  border-radius: 1000px; // 原生药丸胶囊
  font-size: 11px;
  font-weight: 590;
  border: none;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  &.btn-reject {
    background: rgba(0, 0, 0, 0.04);
    border: 0.5px solid rgba(0, 0, 0, 0.08);
    color: #3d3d3d;

    &:hover:not(:disabled) {
      background: rgba(255, 56, 60, 0.08);
      border-color: rgba(255, 56, 60, 0.25);
      color: #ff383c;
    }
  }

  &.btn-hot-patch {
    background: rgba(255, 141, 40, 0.12);
    border: 0.5px solid rgba(255, 141, 40, 0.3);
    color: #c95100;

    &:hover:not(:disabled) {
      background: rgba(255, 141, 40, 0.2);
    }
  }

  &.btn-approve {
    background: linear-gradient(180deg, #0088ff 0%, #0077e6 100%);
    box-shadow: 0 2px 10px rgba(0, 136, 255, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.35);
    color: #ffffff;

    &:hover:not(:disabled) {
      background: linear-gradient(180deg, #0077e6 0%, #0066cc 100%);
      box-shadow: 0 4px 14px rgba(0, 136, 255, 0.4);
    }
  }
}

.font-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
</style>

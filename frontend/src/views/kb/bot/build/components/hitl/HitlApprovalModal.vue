<template>
  <transition name="modal-fade">
    <div v-if="visible" class="hitl-modal-overlay">
      <div class="hitl-modal-card">
        <!-- 头部: 单色钛金状态与工单徽章 -->
        <div class="hitl-header">
          <div class="hitl-title-row">
            <span class="warning-pulse"></span>
            <span class="hitl-title">高危破坏性操作 · 人机二次审批 (HITL)</span>
          </div>
          <div class="hitl-ticket-badge">
            工单号: {{ ticket.ticketId }}
          </div>
        </div>

        <!-- 风险等级与工具标识 -->
        <div class="hitl-meta-banner">
          <div class="meta-item">
            <span class="meta-label">目标工具:</span>
            <span class="meta-value font-mono">{{ ticket.toolName }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">服务节点:</span>
            <span class="meta-value">{{ ticket.serverId || 'local-mcp' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">风险等级:</span>
            <span class="risk-badge destructive">{{ ticket.riskLevel }}</span>
          </div>
        </div>

        <!-- 敏感参数 Unified Diff 对比展示 -->
        <div class="hitl-diff-section">
          <div class="diff-header">
            <span>敏感执行参数审查 (Unified Diff)</span>
            <span class="safe-tag">渐进焦点投影模式 (75%+ 冗余压缩)</span>
          </div>
          <div class="diff-body font-mono">
            <div
              v-for="(val, key) in ticket.argumentsPayload"
              :key="key"
              class="diff-row"
              :class="{ 'destructive-param': isDestructiveParam(key, val) }"
            >
              <span class="diff-indicator">{{ isDestructiveParam(key, val) ? '!' : '+' }}</span>
              <span class="diff-key">{{ key }}:</span>
              <span class="diff-val">{{ formatValue(val) }}</span>
            </div>
          </div>
        </div>

        <!-- 爆炸半径与心跳安全提示 -->
        <div class="hitl-notice-bar">
          <span class="notice-icon">⚠️</span>
          <span>该操作具备不可逆物理副作用，请仔细核对目标表名与语句。通道已开启 15s 心跳探活，超时将自动拒绝挂起。</span>
        </div>

        <!-- 底部操作按钮 -->
        <div class="hitl-footer">
          <button class="btn-reject" :disabled="submitting" @click="handleReject">
            拒绝并终止 (Fail-Close)
          </button>
          <button class="btn-approve" :disabled="submitting" @click="handleApprove">
            <span v-if="submitting">审批中...</span>
            <span v-else>确认签名放行 (Approve)</span>
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { ref } from 'vue';

export interface HitlTicketData {
  ticketId: string;
  toolName: string;
  serverId?: string;
  riskLevel: string;
  argumentsPayload: Record<string, unknown>;
  operatorUserId?: string;
}

const props = defineProps<{
  visible: boolean;
  ticket: HitlTicketData;
}>();

const emit = defineEmits<{
  (e: 'approve', payload: { ticketId: string; approverUserId: string }): void;
  (e: 'reject', payload: { ticketId: string; reason: string }): void;
}>();

const submitting = ref(false);

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

function handleApprove() {
  submitting.value = true;
  emit('approve', {
    ticketId: props.ticket.ticketId,
    approverUserId: 'admin-auditor'
  });
  submitting.value = false;
}

function handleReject() {
  submitting.value = true;
  emit('reject', {
    ticketId: props.ticket.ticketId,
    reason: '安全管理员人机审批拒绝'
  });
  submitting.value = false;
}
</script>

<style scoped lang="scss">
.hitl-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(9, 9, 11, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.hitl-modal-card {
  width: 620px;
  max-width: 90vw;
  background: rgba(24, 24, 27, 0.88);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 16px;
  box-shadow: 0 24px 64px -12px rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(24px) saturate(190%);
  -webkit-backdrop-filter: blur(24px) saturate(190%);
  padding: 24px;
  color: #EDEDEF;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.hitl-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.hitl-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.warning-pulse {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #EF4444;
  box-shadow: 0 0 12px #EF4444;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.15); opacity: 1; }
  100% { transform: scale(0.95); opacity: 0.8; }
}

.hitl-title {
  font-size: 16px;
  font-weight: 600;
  color: #FAFAFA;
}

.hitl-ticket-badge {
  font-size: 12px;
  padding: 3px 8px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: #A1A1AA;
}

.hitl-meta-banner {
  display: flex;
  gap: 16px;
  background: rgba(255, 255, 255, 0.03);
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  margin-bottom: 16px;
  font-size: 13px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.meta-label {
  color: #8A8F98;
}

.meta-value {
  color: #EDEDEF;
}

.risk-badge.destructive {
  background: rgba(239, 68, 68, 0.15);
  color: #F87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.hitl-diff-section {
  background: #121215;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  margin-bottom: 16px;
  overflow: hidden;
}

.diff-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.04);
  font-size: 12px;
  color: #A1A1AA;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.safe-tag {
  color: #34D399;
  font-size: 11px;
}

.diff-body {
  padding: 12px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 180px;
  overflow-y: auto;
}

.diff-row {
  display: flex;
  gap: 8px;
  padding: 2px 4px;
  border-radius: 4px;
}

.diff-row.destructive-param {
  background: rgba(239, 68, 68, 0.12);
  color: #FCA5A5;
}

.diff-indicator {
  font-weight: bold;
  color: #34D399;
}

.destructive-param .diff-indicator {
  color: #EF4444;
}

.diff-key {
  color: #A1A1AA;
}

.hitl-notice-bar {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  color: #E4E4E7;
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.2);
  padding: 10px 12px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.hitl-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn-reject {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #A1A1AA;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: rgba(239, 68, 68, 0.15);
    color: #F87171;
    border-color: rgba(239, 68, 68, 0.3);
  }
}

.btn-approve {
  background: #3B82F6;
  border: 1px solid #60A5FA;
  color: #FFFFFF;
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.35);
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);

  &:hover:not(:disabled) {
    background: #2563EB;
    transform: translateY(-1px);
    box-shadow: 0 6px 20px rgba(59, 130, 246, 0.5);
  }
}

.font-mono {
  font-family: 'SF Mono', Consolas, Monaco, monospace;
}
</style>

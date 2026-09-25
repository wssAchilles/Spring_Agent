<template>
  <transition name="drawer-slide">
    <aside v-if="visible" class="titanium-drawer-overlay">
      <div class="titanium-drawer-panel">
        <!-- 顶部钛金状态栏 -->
        <header class="drawer-header">
          <div class="header-left">
            <span class="caustic-indicator" :class="{ 'is-destructive': hasCriticalDestructive }"></span>
            <div class="title-group">
              <h3 class="drawer-title">人机协同决策中枢 (HITL Metacenter)</h3>
              <span class="workflow-badge">{{ workflowId }} #{{ nodeId }} (步长: {{ stepIndex }})</span>
            </div>
          </div>
          <div class="header-right">
            <span class="branch-tag" :class="{ 'is-fork': branchId !== 'main' }">分支: {{ branchId }}</span>
            <button class="close-btn" @click="handleCancel" title="关闭抽屉 (保留挂起)">✕</button>
          </div>
        </header>

        <!-- 焦点投影与降噪模式状态条 -->
        <div class="drawer-focus-banner">
          <div class="focus-mode">
            <span class="focus-icon">⚡</span>
            <span class="focus-text">渐进焦点投影已激活：已自动折叠 {{ compressedCount }} 个静态元数据 (冗余压缩率 {{ compressionRate }}%)</span>
          </div>
          <button class="toggle-meta-btn" @click="showAllMetadata = !showAllMetadata">
            {{ showAllMetadata ? '折叠元数据' : '展开全部 (' + totalParamsCount + ')' }}
          </button>
        </div>

        <!-- 主审查区：Unified Diff 差异对比 -->
        <div class="drawer-body">
          <!-- 高危破坏性红线警示条 -->
          <div v-if="hasCriticalDestructive" class="destructive-alert-bar">
            <span class="alert-icon">⚠️</span>
            <div class="alert-content">
              <strong>高危破坏性物理写操作拦截</strong>
              <p>检测到参数中包含破坏性写操作（删除、资金划转或特权变更），已触发赤红焦散警示，请严谨核验！</p>
            </div>
          </div>

          <!-- 差异参数列表 -->
          <div class="diff-container">
            <div class="diff-section-title">
              <span>待审查参数 (Unified Diff 视图)</span>
              <span class="diff-stats font-mono">
                <span class="stat-crit">{{ criticalParams.length }} 破坏性</span>
                <span class="stat-risk">{{ highRiskParams.length }} 高危</span>
                <span class="stat-safe">{{ safeParams.length }} 元数据</span>
              </span>
            </div>

            <!-- 破坏性高危字段组 (CRITICAL_DESTRUCTIVE) -->
            <div v-if="criticalParams.length > 0" class="param-group critical-group">
              <div class="group-label font-mono">CRITICAL DESTRUCTIVE PARAMETERS</div>
              <div
                v-for="param in criticalParams"
                :key="param.key"
                class="diff-row critical-row"
              >
                <div class="row-indicator font-mono">!</div>
                <div class="row-content">
                  <div class="row-key font-mono">{{ param.key }}</div>
                  <div class="row-values">
                    <span v-if="param.oldVal !== undefined" class="val-old font-mono">{{ formatValue(param.oldVal) }}</span>
                    <span class="val-arrow">→</span>
                    <input
                      v-if="editMode"
                      class="val-input font-mono"
                      v-model="editableParams[param.key]"
                    />
                    <span v-else class="val-new font-mono critical-text">{{ formatValue(param.newVal) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 高风险手动字段组 (HIGH_RISK_MANUAL) -->
            <div v-if="highRiskParams.length > 0" class="param-group high-risk-group">
              <div class="group-label font-mono">HIGH RISK MANUAL PARAMETERS</div>
              <div
                v-for="param in highRiskParams"
                :key="param.key"
                class="diff-row risk-row"
              >
                <div class="row-indicator font-mono">~</div>
                <div class="row-content">
                  <div class="row-key font-mono">{{ param.key }}</div>
                  <div class="row-values">
                    <span v-if="param.oldVal !== undefined" class="val-old font-mono">{{ formatValue(param.oldVal) }}</span>
                    <span class="val-arrow">→</span>
                    <input
                      v-if="editMode"
                      class="val-input font-mono"
                      v-model="editableParams[param.key]"
                    />
                    <span v-else class="val-new font-mono">{{ formatValue(param.newVal) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 安全元数据字段组 (SAFE_METADATA，默认折叠) -->
            <div v-if="showAllMetadata && safeParams.length > 0" class="param-group safe-group">
              <div class="group-label font-mono">SAFE METADATA (READ-ONLY)</div>
              <div
                v-for="param in safeParams"
                :key="param.key"
                class="diff-row safe-row"
              >
                <div class="row-indicator font-mono">+</div>
                <div class="row-content">
                  <div class="row-key font-mono">{{ param.key }}</div>
                  <div class="row-values">
                    <span class="val-safe font-mono">{{ formatValue(param.newVal) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- DeepSeek 链式长思考摘要预览 -->
          <div class="reasoning-preview-section" v-if="reasoningContent">
            <div class="reasoning-header">
              <span>DeepSeek 链式推理因果存证</span>
              <span class="reasoning-badge font-mono">SHA-256 Digest 锚定</span>
            </div>
            <div class="reasoning-body font-mono">
              {{ reasoningContent }}
            </div>
          </div>
        </div>

        <!-- 底部单色钛金操作区 -->
        <footer class="drawer-footer">
          <div class="footer-left">
            <button
              class="btn-toggle-edit"
              :class="{ 'is-editing': editMode }"
              @click="toggleEditMode"
            >
              {{ editMode ? '取消热补丁' : '现场参数热修改 (Hot Patch)' }}
            </button>
          </div>
          <div class="footer-right">
            <button class="btn-action reject" :disabled="submitting" @click="handleReject">
              拒绝终止 (Fail-Close)
            </button>
            <button class="btn-action approve" :disabled="submitting" @click="handleApprove">
              <span v-if="submitting">签名签署中...</span>
              <span v-else>{{ editMode ? '热补丁签署并放行' : '确认签名放行 (Approve)' }}</span>
            </button>
          </div>
        </footer>
      </div>
    </aside>
  </transition>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { HitlFrontendAuditReceipt } from './receipt/HitlFrontendAuditReceipt';

export interface ParamDiffItem {
  key: string;
  oldVal?: unknown;
  newVal: unknown;
  category: 'CRITICAL_DESTRUCTIVE' | 'HIGH_RISK_MANUAL' | 'SAFE_METADATA';
}

const props = defineProps<{
  visible: boolean;
  workflowId: string;
  nodeId: string;
  stepIndex: number;
  branchId: string;
  operatorId: string;
  originalParams: Record<string, unknown>;
  candidateParams: Record<string, unknown>;
  reasoningContent?: string;
}>();

const emit = defineEmits<{
  (e: 'approved', payload: { receipt: HitlFrontendAuditReceipt; finalParams: Record<string, unknown> }): void;
  (e: 'rejected', payload: { receipt: HitlFrontendAuditReceipt; reason: string }): void;
  (e: 'close'): void;
}>();

const showAllMetadata = ref(false);
const editMode = ref(false);
const submitting = ref(false);
const editableParams = ref<Record<string, unknown>>({});

const DESTRUCTIVE_KEYS = ['drop', 'delete', 'truncate', 'rm', 'format', 'kill', 'purge', 'grant', 'revoke', 'transfer', 'amount', 'payout', 'sudo'];
const METADATA_KEYS = ['traceId', 'spanId', 'timestamp', 'epoch', 'version', 'temperature', 'timeout', 'debug', 'retryCount', 'host'];

watch(
  () => props.candidateParams,
  (newVal) => {
    editableParams.value = JSON.parse(JSON.stringify(newVal || {}));
  },
  { immediate: true }
);

function classifyParam(key: string): 'CRITICAL_DESTRUCTIVE' | 'HIGH_RISK_MANUAL' | 'SAFE_METADATA' {
  const k = key.toLowerCase();
  for (const dk of DESTRUCTIVE_KEYS) {
    if (k.includes(dk)) {
      return 'CRITICAL_DESTRUCTIVE';
    }
  }
  for (const mk of METADATA_KEYS) {
    if (k.includes(mk)) {
      return 'SAFE_METADATA';
    }
  }
  return 'HIGH_RISK_MANUAL';
}

const diffItems = computed<ParamDiffItem[]>(() => {
  const keys = new Set([...Object.keys(props.originalParams || {}), ...Object.keys(props.candidateParams || {})]);
  const list: ParamDiffItem[] = [];

  for (const key of keys) {
    const oldVal = props.originalParams ? props.originalParams[key] : undefined;
    const newVal = props.candidateParams ? props.candidateParams[key] : undefined;
    const category = classifyParam(key);
    list.push({ key, oldVal, newVal, category });
  }

  return list;
});

const criticalParams = computed(() => diffItems.value.filter(item => item.category === 'CRITICAL_DESTRUCTIVE'));
const highRiskParams = computed(() => diffItems.value.filter(item => item.category === 'HIGH_RISK_MANUAL'));
const safeParams = computed(() => diffItems.value.filter(item => item.category === 'SAFE_METADATA'));

const hasCriticalDestructive = computed(() => criticalParams.value.length > 0);
const totalParamsCount = computed(() => diffItems.value.length);
const compressedCount = computed(() => safeParams.value.length);
const compressionRate = computed(() => {
  if (totalParamsCount.value === 0) return 0;
  return Math.round((compressedCount.value / totalParamsCount.value) * 100);
});

function formatValue(v: unknown): string {
  if (v === null) return 'null';
  if (v === undefined) return 'undefined';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

function toggleEditMode(): void {
  editMode.value = !editMode.value;
  if (!editMode.value) {
    editableParams.value = JSON.parse(JSON.stringify(props.candidateParams || {}));
  }
}

function handleApprove(): void {
  submitting.value = true;
  try {
    const isHotPatch = editMode.value;
    const finalParams = isHotPatch ? editableParams.value : props.candidateParams;

    const receipt = HitlFrontendAuditReceipt.create(
      props.workflowId,
      props.nodeId,
      props.stepIndex,
      props.branchId,
      props.operatorId,
      isHotPatch ? 'HOT_PATCH' : 'APPROVE',
      props.originalParams,
      finalParams,
      props.reasoningContent || ''
    );

    emit('approved', { receipt, finalParams });
  } finally {
    submitting.value = false;
  }
}

function handleReject(): void {
  submitting.value = true;
  try {
    const receipt = HitlFrontendAuditReceipt.create(
      props.workflowId,
      props.nodeId,
      props.stepIndex,
      props.branchId,
      props.operatorId,
      'REJECT',
      props.originalParams,
      props.candidateParams,
      props.reasoningContent || ''
    );

    emit('rejected', { receipt, reason: '审批人手动否决' });
  } finally {
    submitting.value = false;
  }
}

function handleCancel(): void {
  emit('close');
}
</script>

<style scoped>
/* Modern Dark Titanium Glassmorphism 样式 */
.titanium-drawer-overlay {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(2, 2, 3, 0.65);
  backdrop-filter: blur(8px);
  z-index: 9999;
  display: flex;
  justify-content: flex-end;
}

.titanium-drawer-panel {
  width: 580px;
  max-width: 90vw;
  height: 100%;
  background: #0a0a0c;
  border-left: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: -8px 0 32px rgba(0, 0, 0, 0.8);
  display: flex;
  flex-direction: column;
  backdrop-filter: blur(20px);
}

.drawer-header {
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.02);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.caustic-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 10px rgba(56, 189, 248, 0.8);
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.caustic-indicator.is-destructive {
  background: #f43f5e;
  box-shadow: 0 0 14px rgba(244, 63, 94, 0.9);
  animation: causticPulse 1.2s infinite alternate cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes causticPulse {
  0% { transform: scale(1); opacity: 0.8; }
  100% { transform: scale(1.35); opacity: 1; }
}

.title-group {
  display: flex;
  flex-direction: column;
}

.drawer-title {
  font-size: 15px;
  font-weight: 600;
  color: #ededef;
  margin: 0;
}

.workflow-badge {
  font-size: 11px;
  color: #8a8f98;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.branch-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.05);
  color: #ededef;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.branch-tag.is-fork {
  background: rgba(168, 85, 247, 0.15);
  color: #c084fc;
  border-color: rgba(168, 85, 247, 0.3);
}

.close-btn {
  background: transparent;
  border: none;
  color: #8a8f98;
  font-size: 14px;
  cursor: pointer;
  padding: 4px;
}

.close-btn:hover {
  color: #ededef;
}

.drawer-focus-banner {
  padding: 10px 20px;
  background: rgba(56, 189, 248, 0.04);
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.focus-mode {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #38bdf8;
}

.toggle-meta-btn {
  background: transparent;
  border: 1px solid rgba(56, 189, 248, 0.2);
  color: #38bdf8;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.destructive-alert-bar {
  padding: 12px 14px;
  background: rgba(244, 63, 94, 0.08);
  border: 1px solid rgba(244, 63, 94, 0.3);
  border-radius: 6px;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  color: #fb7185;
}

.destructive-alert-bar strong {
  display: block;
  font-size: 13px;
  margin-bottom: 2px;
}

.destructive-alert-bar p {
  font-size: 12px;
  margin: 0;
  color: rgba(251, 113, 133, 0.9);
}

.diff-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.diff-section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: #ededef;
}

.diff-stats {
  display: flex;
  gap: 8px;
  font-size: 11px;
}

.stat-crit { color: #f43f5e; }
.stat-risk { color: #fbbf24; }
.stat-safe { color: #8a8f98; }

.param-group {
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  overflow: hidden;
  background: rgba(15, 15, 20, 0.6);
}

.critical-group {
  border-color: rgba(244, 63, 94, 0.35);
  box-shadow: 0 0 16px rgba(244, 63, 94, 0.12);
}

.group-label {
  padding: 6px 12px;
  font-size: 10px;
  letter-spacing: 0.5px;
  background: rgba(255, 255, 255, 0.02);
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  color: #8a8f98;
}

.critical-group .group-label {
  color: #fb7185;
  background: rgba(244, 63, 94, 0.1);
}

.diff-row {
  display: flex;
  padding: 8px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
  font-size: 12px;
  align-items: center;
  gap: 8px;
}

.diff-row:last-child {
  border-bottom: none;
}

.row-indicator {
  width: 14px;
  text-align: center;
  font-weight: 700;
}

.critical-row .row-indicator { color: #f43f5e; }
.risk-row .row-indicator { color: #fbbf24; }
.safe-row .row-indicator { color: #8a8f98; }

.row-content {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.row-key {
  color: #ededef;
  font-weight: 500;
}

.row-values {
  display: flex;
  align-items: center;
  gap: 6px;
}

.val-old {
  color: #8a8f98;
  text-decoration: line-through;
}

.val-arrow {
  color: #52525b;
}

.val-new {
  color: #38bdf8;
}

.val-new.critical-text {
  color: #f43f5e;
  font-weight: 600;
}

.val-safe {
  color: #71717a;
}

.val-input {
  background: #050506;
  border: 1px solid #38bdf8;
  color: #ededef;
  padding: 3px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.reasoning-preview-section {
  padding: 12px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  background: rgba(5, 5, 6, 0.8);
}

.reasoning-header {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #8a8f98;
  margin-bottom: 8px;
}

.reasoning-badge {
  color: #a855f7;
}

.reasoning-body {
  font-size: 11px;
  line-height: 1.5;
  color: #d4d4d8;
  max-height: 90px;
  overflow-y: auto;
  white-space: pre-wrap;
}

.drawer-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.02);
}

.btn-toggle-edit {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #8a8f98;
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-toggle-edit.is-editing {
  background: rgba(168, 85, 247, 0.15);
  border-color: #a855f7;
  color: #c084fc;
}

.footer-right {
  display: flex;
  gap: 10px;
}

.btn-action {
  padding: 7px 16px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;
  cursor: pointer;
  border: none;
  transition: all 0.2s ease;
}

.btn-action.reject {
  background: rgba(244, 63, 94, 0.15);
  color: #fb7185;
  border: 1px solid rgba(244, 63, 94, 0.3);
}

.btn-action.reject:hover {
  background: rgba(244, 63, 94, 0.25);
}

.btn-action.approve {
  background: #ededef;
  color: #020203;
  font-weight: 600;
}

.btn-action.approve:hover {
  background: #ffffff;
  box-shadow: 0 0 12px rgba(255, 255, 255, 0.3);
}

/* 抽屉进出动画 */
.drawer-slide-enter-active,
.drawer-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.drawer-slide-enter-from,
.drawer-slide-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
</style>

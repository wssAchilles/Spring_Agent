<template>
  <div class="mcp-gateway-status-widget">
    <!-- 顶部状态栏 (单色钛金毛玻璃) -->
    <div class="widget-header">
      <div class="header-left">
        <span class="status-indicator" :class="`state-${circuitState.toLowerCase()}`"></span>
        <span class="widget-title">MCP GATEWAY // PHASE 136</span>
      </div>
      <div class="header-right">
        <span class="circuit-badge" :class="`badge-${circuitState.toLowerCase()}`">
          {{ circuitStateText }}
        </span>
      </div>
    </div>

    <!-- 核心配额指标卡片 -->
    <div class="metrics-grid">
      <!-- 多租户配额水位 -->
      <div class="metric-card">
        <div class="card-label">租户在途并发 (In-Flight)</div>
        <div class="card-value-row">
          <span class="value-highlight">{{ currentInFlight }}</span>
          <span class="value-cap">/ {{ maxConcurrent }}</span>
        </div>
        <div class="progress-bar-track">
          <div
            class="progress-bar-fill"
            :style="{ width: `${concurrencyRatio}%` }"
            :class="{ 'fill-warning': concurrencyRatio > 80 }"
          ></div>
        </div>
      </div>

      <!-- 令牌桶流控水位 -->
      <div class="metric-card">
        <div class="card-label">令牌桶可用余量</div>
        <div class="card-value-row">
          <span class="value-highlight">{{ availableTokens }}</span>
          <span class="value-cap">/ {{ burstCapacity }} Tokens</span>
        </div>
        <div class="progress-bar-track">
          <div
            class="progress-bar-fill fill-cyan"
            :style="{ width: `${tokenRatio}%` }"
          ></div>
        </div>
      </div>
    </div>

    <!-- 自适应熔断与滑动窗口指标 -->
    <div class="circuit-detail-panel">
      <div class="detail-row">
        <span class="label">滑动窗口样本:</span>
        <span class="val">{{ windowSamples }} 次调用</span>
      </div>
      <div class="detail-row">
        <span class="label">失败调用比例:</span>
        <span class="val" :class="{ 'val-danger': failureRate >= 0.25 }">{{ (failureRate * 100).toFixed(1) }}% (门限 25%)</span>
      </div>
      <div class="detail-row">
        <span class="label">慢调用比例 (P99):</span>
        <span class="val" :class="{ 'val-warning': slowCallRate >= 0.30 }">{{ (slowCallRate * 100).toFixed(1) }}% (门限 30%)</span>
      </div>
      <div class="detail-row" v-if="circuitState === 'OPEN'">
        <span class="label">语义降级路径:</span>
        <span class="val val-highlight">RAG Local Knowledge Fallback</span>
      </div>
    </div>

    <!-- 零信任脱敏实时审计面板 -->
    <div class="masking-audit-panel">
      <div class="audit-title">零信任双向脱敏审计凭单</div>
      <div class="audit-body">
        <div class="audit-field">
          <span class="k">最近凭单:</span>
          <span class="v code-font">{{ latestReceiptId }}</span>
        </div>
        <div class="audit-field">
          <span class="k">脱敏字段:</span>
          <span class="v tags-wrap">
            <span v-for="tag in maskedTags" :key="tag" class="mask-tag">{{ tag }}</span>
            <span v-if="maskedTags.length === 0" class="empty-tag">无敏感数据</span>
          </span>
        </div>
        <div class="audit-field">
          <span class="k">处理时延:</span>
          <span class="v">{{ maskingLatencyMs }} ms (<= 2.0ms)</span>
        </div>
        <div class="audit-field">
          <span class="k">密码学验真:</span>
          <span class="v signature-verified">
            <i class="el-icon-check"></i> SHA-256 常量时间验真通过
          </span>
        </div>
      </div>
    </div>

    <!-- 交互模拟工具箱 -->
    <div class="widget-actions">
      <button class="titanium-btn" @click="simulateTrafficBurst" title="模拟突发高并发请求">
        ⚡ 突发流量
      </button>
      <button class="titanium-btn" @click="simulateSlowCallTrip" title="模拟下游慢调用触发自适应熔断">
        🛡️ 慢调用熔断
      </button>
      <button class="titanium-btn" @click="simulateSensitiveMasking" title="模拟身份证/手机号数据双向脱敏">
        🔒 敏感脱敏
      </button>
      <button class="titanium-btn" @click="resetGatewayState" title="重置状态">
        ↺ 复位
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

// 断路器状态
type CircuitState = 'CLOSED' | 'HALF_OPEN' | 'OPEN';
const circuitState = ref<CircuitState>('CLOSED');

// 配额与并发
const currentInFlight = ref<number>(3);
const maxConcurrent = ref<number>(20);
const availableTokens = ref<number>(85);
const burstCapacity = ref<number>(100);

// 滑动窗口指标
const windowSamples = ref<number>(20);
const failureRate = ref<number>(0.05);
const slowCallRate = ref<number>(0.10);

// 脱敏审计
const latestReceiptId = ref<string>('RCP-MCP-GW-1727258900-48f1c9');
const maskedTags = ref<string[]>(['CHINESE_ID_CARD', 'MOBILE_PHONE']);
const maskingLatencyMs = ref<number>(0.85);

const circuitStateText = computed(() => {
  switch (circuitState.value) {
    case 'CLOSED': return 'HEALTHY // CLOSED';
    case 'HALF_OPEN': return 'PROBING // HALF-OPEN';
    case 'OPEN': return 'DEGRADED // OPEN';
    default: return 'UNKNOWN';
  }
});

const concurrencyRatio = computed(() => {
  return Math.min(100, Math.round((currentInFlight.value / maxConcurrent.value) * 100));
});

const tokenRatio = computed(() => {
  return Math.min(100, Math.round((availableTokens.value / burstCapacity.value) * 100));
});

// 模拟交互
function simulateTrafficBurst() {
  currentInFlight.value = 18;
  availableTokens.value = 12;
  setTimeout(() => {
    currentInFlight.value = 4;
    availableTokens.value = 75;
  }, 2500);
}

function simulateSlowCallTrip() {
  slowCallRate.value = 0.45;
  circuitState.value = 'OPEN';
  setTimeout(() => {
    circuitState.value = 'HALF_OPEN';
    setTimeout(() => {
      circuitState.value = 'CLOSED';
      slowCallRate.value = 0.05;
    }, 2000);
  }, 3000);
}

function simulateSensitiveMasking() {
  maskedTags.value = ['CHINESE_ID_CARD', 'BANK_CARD', 'JWT_TOKEN', 'SECRET_KEY'];
  maskingLatencyMs.value = 1.12;
  latestReceiptId.value = 'RCP-MCP-GW-' + Date.now().toString().slice(-8);
}

function resetGatewayState() {
  circuitState.value = 'CLOSED';
  currentInFlight.value = 3;
  availableTokens.value = 85;
  slowCallRate.value = 0.10;
  failureRate.value = 0.05;
  maskedTags.value = ['CHINESE_ID_CARD', 'MOBILE_PHONE'];
  maskingLatencyMs.value = 0.85;
}
</script>

<style scoped>
.mcp-gateway-status-widget {
  background: rgba(10, 10, 12, 0.85);
  backdrop-filter: blur(24px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  color: #f4f4f5;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  user-select: none;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.widget-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #71717a;
  transition: all 0.3s ease;
}

.status-indicator.state-closed {
  background: #10b981;
  box-shadow: 0 0 8px #10b981;
}

.status-indicator.state-half_open {
  background: #f59e0b;
  box-shadow: 0 0 8px #f59e0b;
  animation: pulse-warn 1.5s infinite;
}

.status-indicator.state-open {
  background: #ef4444;
  box-shadow: 0 0 10px #ef4444;
  animation: pulse-danger 1s infinite;
}

@keyframes pulse-warn {
  0%, 100% { opacity: 0.8; }
  50% { opacity: 1; }
}

@keyframes pulse-danger {
  0%, 100% { opacity: 0.7; transform: scale(0.95); }
  50% { opacity: 1; transform: scale(1.15); }
}

.widget-title {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #f4f4f5;
}

.circuit-badge {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.badge-closed {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.badge-half_open {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
  border: 1px solid rgba(245, 158, 11, 0.3);
}

.badge-open {
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

/* 指标栅格 */
.metrics-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.metric-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 10px;
}

.card-label {
  font-size: 10px;
  color: #a1a1aa;
}

.card-value-row {
  margin-top: 4px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.value-highlight {
  font-size: 18px;
  font-weight: 700;
  color: #f4f4f5;
}

.value-cap {
  font-size: 11px;
  color: #71717a;
}

.progress-bar-track {
  margin-top: 8px;
  height: 4px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 2px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background: #10b981;
  transition: width 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.progress-bar-fill.fill-warning {
  background: #f59e0b;
}

.progress-bar-fill.fill-cyan {
  background: #38bdf8;
}

/* 熔断详情面板 */
.circuit-detail-panel {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 11px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  color: #a1a1aa;
}

.detail-row .val {
  color: #f4f4f5;
  font-weight: 600;
}

.val-danger {
  color: #ef4444 !important;
}

.val-warning {
  color: #f59e0b !important;
}

.val-highlight {
  color: #38bdf8 !important;
}

/* 脱敏审计面板 */
.masking-audit-panel {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 8px 12px;
}

.audit-title {
  font-size: 11px;
  font-weight: 600;
  color: #a1a1aa;
  margin-bottom: 6px;
}

.audit-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 10px;
}

.audit-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.audit-field .k {
  color: #71717a;
}

.audit-field .v {
  color: #e4e4e7;
}

.code-font {
  font-family: monospace;
  font-size: 9px;
  color: #a1a1aa !important;
}

.tags-wrap {
  display: flex;
  gap: 4px;
}

.mask-tag {
  font-size: 9px;
  padding: 1px 4px;
  border-radius: 3px;
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.empty-tag {
  color: #71717a;
}

.signature-verified {
  color: #10b981 !important;
  font-weight: 600;
}

/* 按钮操作 */
.widget-actions {
  display: flex;
  gap: 6px;
}

.titanium-btn {
  flex: 1;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: #f4f4f5;
  padding: 5px 8px;
  border-radius: 6px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  white-space: nowrap;
}

.titanium-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.16);
}
</style>

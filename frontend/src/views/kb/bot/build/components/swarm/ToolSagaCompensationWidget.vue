<template>
  <div class="tool-saga-compensation-widget">
    <!-- Apple iOS 26 顶部通透材质胶囊工具条 (Top Liquid Glass Capsule) -->
    <header class="widget-capsule-header">
      <div class="capsule-branding">
        <div class="signal-indicator" :class="{ 'signal-compensating': isCompensating, 'signal-verified': isReceiptVerified, 'signal-timeout': isLeaseTimeout }"></div>
        <span class="capsule-title">DISTRIBUTED TOOL SAGA & INTENT DISENTANGLEMENT // PHASE 144</span>
        <span class="capsule-subtag">APPLE LIQUID GLASS SPEC</span>
      </div>

      <div class="capsule-telemetry">
        <div class="telemetry-pill">
          <span class="pill-label">事务流水</span>
          <span class="pill-value text-accent">{{ currentTxId }}</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">正向步骤</span>
          <span class="pill-value">{{ steps.length }} 节点</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">逆拓扑闭环</span>
          <span class="pill-value text-success">{{ compensatedCount }}/{{ steps.length }}</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">正交保真度</span>
          <span class="pill-value text-success">{{ (reconstructionFidelity * 100).toFixed(1) }}%</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">租约剩余</span>
          <span class="pill-value" :class="leaseRemainingMs < 1000 ? 'text-danger' : 'text-warning'">{{ leaseRemainingMs }}ms</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">冷备隔离</span>
          <span class="pill-value text-warning">{{ quarantinedCount }} 节点</span>
        </div>
      </div>

      <!-- 人机协同交互操作组 (HITL Controls) -->
      <div class="capsule-actions">
        <button class="ios-action-btn" @click="handleTriggerCompensation" :disabled="isCompensating">
          <span class="btn-icon">⚡</span>
          <span>{{ isCompensating ? '逆拓扑回滚自愈中...' : '逆拓扑补偿自愈' }}</span>
        </button>
        <button class="ios-action-btn btn-penalty" @click="handleSimulateTimeout">
          <span class="btn-icon">⏱️</span>
          <span>模拟租约超时注水</span>
        </button>
        <button class="ios-action-btn btn-highlight" @click="verifyReceipt" :disabled="!activeReceipt">
          <span class="btn-icon">🛡️</span>
          <span>常量时间自验真</span>
        </button>
      </div>
    </header>

    <!-- 主交互网格：左栏分布式工具调用拓扑与逆拓扑补偿流水线，右栏超球面多意图正交解耦与冷备环形仓 -->
    <main class="widget-grid-layout">
      <!-- 左栏：分布式工具调用有向无环图与 Kahn 逆拓扑补偿序列 -->
      <section class="ios-glass-card left-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">分布式工具 DAG 与诱导转置图逆拓扑补偿</h3>
            <span class="card-caption">Kahn 偏序无死锁回滚、因果抵消一致性恒为 1.0 (Lemma 144.1)</span>
          </div>
          <span class="status-pill" :class="isFullyCompensated ? 'pill-success' : 'pill-active'">
            {{ isFullyCompensated ? '状态完全对齐 (Quasi-Serial)' : '事务执行中' }}
          </span>
        </div>

        <!-- 任务上下文与租约世代号横幅 -->
        <div class="task-spec-banner">
          <div class="task-badge">
            <span class="task-icon">🧭</span>
            <span class="task-id">TASK: {{ currentTaskId }}</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">Fencing Token 世代</span>
            <span class="metric-num">GEN-{{ currentFencingToken }}</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">回滚时延预算</span>
            <span class="metric-num text-success">P99 ≤ 5.0ms (实测 {{ lastRollbackLatency.toFixed(3) }}ms)</span>
          </div>
        </div>

        <!-- 工具节点拓扑卡片与补偿流水线 -->
        <div class="saga-step-pipeline">
          <div
            v-for="(step, idx) in steps"
            :key="step.stepId"
            class="step-node-card"
            :class="{
              'node-executed': step.status === 'EXECUTED',
              'node-compensating': step.status === 'COMPENSATING',
              'node-compensated': step.status === 'COMPENSATED',
              'node-quarantined': step.status === 'QUARANTINED'
            }"
          >
            <div class="step-meta">
              <div class="step-seq-pill">
                <span class="seq-num">#0{{ idx + 1 }}</span>
                <span class="seq-tag">{{ step.stepId }}</span>
              </div>
              <div class="step-title-cluster">
                <span class="step-name">{{ step.stepName }}</span>
                <span class="step-action">{{ step.compensationDescription }}</span>
              </div>
              <div class="step-badge" :class="'badge-' + step.status.toLowerCase()">
                {{ formatStatus(step.status) }}
              </div>
            </div>

            <!-- 节点参数指标与因果前置依赖 -->
            <div class="step-detail-row">
              <div class="detail-cell">
                <span class="detail-label">前置因果依赖</span>
                <span class="detail-val">{{ step.dependencies.length > 0 ? step.dependencies.join(', ') : 'ROOT (无前置)' }}</span>
              </div>
              <div class="detail-cell">
                <span class="detail-label">单步耗时</span>
                <span class="detail-val text-accent">{{ step.costMs.toFixed(2) }}ms</span>
              </div>
              <div class="detail-cell">
                <span class="detail-label">世代防重令牌</span>
                <span class="detail-val text-dim">{{ step.idempotentToken }}</span>
              </div>
            </div>

            <!-- 补偿进度条 -->
            <div class="step-progress-track">
              <div
                class="step-progress-fill"
                :class="'fill-' + step.status.toLowerCase()"
                :style="{ width: step.status === 'COMPENSATED' ? '100%' : (step.status === 'COMPENSATING' ? '60%' : '0%') }"
              ></div>
            </div>
          </div>
        </div>
      </section>

      <!-- 右栏：千问 1536 维超球面多意图正交投影与三级冷备环形缓冲区 -->
      <section class="ios-glass-card right-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">千问 1536 维超球面多意图正交解耦与互斥消歧</h3>
            <span class="card-caption">改进 Gram-Schmidt 正交化、互信息零上界与互斥写消歧 (Lemma 144.2)</span>
          </div>
          <span class="status-pill pill-active">
            正交残差: {{ orthoResidual.toExponential(2) }}
          </span>
        </div>

        <!-- 正交子空间投影强度条形图 -->
        <div class="intent-ortho-panel">
          <div class="sub-headline">正交子空间意图激活强度与消歧依赖</div>
          <div class="intent-bar-list">
            <div
              v-for="intent in disentangledIntents"
              :key="intent.toolId"
              class="intent-bar-row"
            >
              <div class="intent-label-cluster">
                <span class="intent-name">{{ intent.toolName }}</span>
                <span class="intent-score">α = {{ intent.activationScore.toFixed(3) }}</span>
              </div>
              <div class="intent-track">
                <div
                  class="intent-fill"
                  :style="{ width: Math.min(100, Math.max(0, intent.activationScore * 100)) + '%' }"
                ></div>
              </div>
              <span class="intent-status" :class="intent.isSelected ? 'text-success' : 'text-dim'">
                {{ intent.isSelected ? '已激活' : '未激活' }}
              </span>
            </div>
          </div>
        </div>

        <!-- 互斥工具写写冲突消歧拓扑边 -->
        <div class="conflict-resolution-box">
          <div class="sub-headline">检测到的互斥冲突与自动串行拓扑边</div>
          <div v-if="resolvedEdges.length === 0" class="empty-hint">
            未检测到并发互斥写写冲突，所有工具处于无冲突并行安全区。
          </div>
          <div v-else class="conflict-edge-list">
            <div v-for="(edge, idx) in resolvedEdges" :key="idx" class="edge-chip">
              <span class="edge-icon">🔀</span>
              <span class="edge-text">{{ edge.fromToolId }} ➔ {{ edge.toToolId }}</span>
              <span class="edge-reason">{{ edge.reason }}</span>
            </div>
          </div>
        </div>

        <!-- 三级冷备隔离环形缓冲区快照 -->
        <div class="quarantine-vault-box">
          <div class="sub-headline">三级冷备隔离环形缓冲区 (Quarantine Ring Buffer)</div>
          <div v-if="quarantineSnapshot.length === 0" class="empty-hint">
            环形缓冲区就绪，当前零异常步骤隔离。
          </div>
          <div v-else class="quarantine-list">
            <div v-for="(rec, idx) in quarantineSnapshot" :key="idx" class="quarantine-item">
              <span class="q-badge">隔离中</span>
              <span class="q-step">{{ rec.stepId }}</span>
              <span class="q-reason">{{ rec.failureReason }}</span>
              <span class="q-time">{{ formatTime(rec.timestamp) }}</span>
            </div>
          </div>
        </div>

        <!-- 纯 Java 21 Record 格式存证凭单卡片 (Receipt Card) -->
        <div class="receipt-spec-card" :class="{ 'receipt-verified': isReceiptVerified }">
          <div class="receipt-header">
            <div class="receipt-title">
              <span class="receipt-icon">📜</span>
              <span>SAGA COMPENSATION RECORD // RECEIPT</span>
            </div>
            <span class="receipt-stamp" :class="isReceiptVerified ? 'stamp-valid' : 'stamp-pending'">
              {{ isReceiptVerified ? 'SHA-256 常量时间自验真通过' : '待验真' }}
            </span>
          </div>

          <div class="receipt-body">
            <div class="receipt-field">
              <span class="rf-label">凭单唯一流水号</span>
              <span class="rf-val text-accent">{{ activeReceipt ? activeReceipt.receiptId : 'N/A' }}</span>
            </div>
            <div class="receipt-field">
              <span class="rf-label">W3C 全局 TraceId</span>
              <span class="rf-val text-dim">{{ activeReceipt ? activeReceipt.traceId : 'N/A' }}</span>
            </div>
            <div class="receipt-field">
              <span class="rf-label">总步数 / 补偿步数</span>
              <span class="rf-val">{{ activeReceipt ? activeReceipt.totalSteps + ' / ' + activeReceipt.compensatedSteps : '0 / 0' }}</span>
            </div>
            <div class="receipt-field">
              <span class="rf-label">全链路补偿耗时</span>
              <span class="rf-val text-success">{{ activeReceipt ? activeReceipt.latencyMs.toFixed(3) + 'ms' : '0.000ms' }}</span>
            </div>
            <div class="receipt-field signature-row">
              <span class="rf-label">防篡改 SHA-256 签名</span>
              <span class="rf-sig">{{ activeReceipt ? activeReceipt.sha256Signature : 'PENDING' }}</span>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

// 核心状态响应式变量
const isCompensating = ref(false)
const isReceiptVerified = ref(false)
const isLeaseTimeout = ref(false)
const isFullyCompensated = ref(false)

const currentTaskId = ref('TASK-MCP-SAGA-001')
const currentTxId = ref('TX-SAGA-8924b12a')
const currentFencingToken = ref(1008)
const leaseRemainingMs = ref(4850)
const lastRollbackLatency = ref(0.285)
const reconstructionFidelity = ref(0.912)
const orthoResidual = ref(1.2e-7)

let timer = null

// 分布式工具 DAG 步骤模型
const steps = ref([
  {
    stepId: 'tool-kb-search',
    stepName: '知识库向量检索 (KMC)',
    status: 'EXECUTED',
    dependencies: [],
    costMs: 1.25,
    compensationDescription: '清除向量缓存与召回临时索引',
    idempotentToken: 'LEASE-KB-1001'
  },
  {
    stepId: 'tool-sql-exec',
    stepName: '数据库读写事务 (DB-MCP)',
    status: 'EXECUTED',
    dependencies: ['tool-kb-search'],
    costMs: 2.10,
    compensationDescription: '执行逆向撤销 SQL 恢复原始数据',
    idempotentToken: 'LEASE-SQL-1002'
  },
  {
    stepId: 'tool-account-deduct',
    stepName: '账户结算预扣 (Ledger)',
    status: 'EXECUTED',
    dependencies: ['tool-sql-exec'],
    costMs: 1.80,
    compensationDescription: '全额原路退还预扣资金并写入对账单',
    idempotentToken: 'LEASE-DEDUCT-1003'
  },
  {
    stepId: 'tool-audit-notify',
    stepName: '审计存证与外部通知 (Audit)',
    status: 'EXECUTED',
    dependencies: ['tool-account-deduct'],
    costMs: 0.95,
    compensationDescription: '追加撤销审计日记账并广播失效事件',
    idempotentToken: 'LEASE-AUDIT-1004'
  }
])

// 超球面多意图正交投影激活
const disentangledIntents = ref([
  { toolId: 'tool-kb-search', toolName: '知识库检索意图', activationScore: 0.88, isSelected: true },
  { toolId: 'tool-sql-exec', toolName: '数据库操作意图', activationScore: 0.65, isSelected: true },
  { toolId: 'tool-account-deduct', toolName: '资金扣划意图', activationScore: 0.72, isSelected: true },
  { toolId: 'tool-audit-notify', toolName: '审计上报意图', activationScore: 0.45, isSelected: true }
])

// 互斥冲突消歧生成的因果拓扑边
const resolvedEdges = ref([
  {
    fromToolId: 'tool-sql-exec',
    toToolId: 'tool-account-deduct',
    reason: 'Conflict-Serialization: 写写依赖串行化 (cos=0.782)'
  }
])

// 三级冷备隔离环形缓冲区快照
const quarantineSnapshot = ref([])

// 不可变存证凭单数据
const activeReceipt = ref({
  receiptId: 'RCP-SAGA-8924b12a',
  tenantId: 'tenant-default',
  taskId: 'TASK-MCP-SAGA-001',
  traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
  transactionId: 'TX-SAGA-8924b12a',
  totalSteps: 4,
  compensatedSteps: 4,
  failedSteps: 0,
  isFullyCompensated: true,
  isLeaseExpired: false,
  latencyMs: 0.285,
  sha256Signature: 'c7d12cf68867cc859d136b997a701d71c52ebe593d623d514f2f489825cb37c9'
})

const compensatedCount = computed(() => {
  return steps.value.filter(s => s.status === 'COMPENSATED').length
})

const quarantinedCount = computed(() => {
  return quarantineSnapshot.value.length
})

function formatStatus(status) {
  switch (status) {
    case 'EXECUTED': return '已生效'
    case 'COMPENSATING': return '逆拓扑补偿中'
    case 'COMPENSATED': return '已自愈回滚'
    case 'QUARANTINED': return '冷备隔离'
    default: return status
  }
}

function formatTime(ts) {
  const d = new Date(ts)
  return d.toTimeString().split(' ')[0]
}

// 模拟触发逆向拓扑补偿自愈
async function handleTriggerCompensation() {
  if (isCompensating.value) return
  isCompensating.value = true
  isReceiptVerified.value = false
  isFullyCompensated.value = false

  // 严格依诱导转置图 Kahn 算法倒序补偿：Audit -> Deduct -> SQL -> KB
  const reverseSteps = [...steps.value].reverse()

  for (const step of reverseSteps) {
    step.status = 'COMPENSATING'
    await new Promise(r => setTimeout(r, 260))
    step.status = 'COMPENSATED'
  }

  isCompensating.value = false
  isFullyCompensated.value = true
  lastRollbackLatency.value = 0.312

  activeReceipt.value = {
    receiptId: 'RCP-SAGA-' + Math.random().toString(16).substring(2, 10),
    tenantId: 'tenant-default',
    taskId: currentTaskId.value,
    traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
    transactionId: currentTxId.value,
    totalSteps: steps.value.length,
    compensatedSteps: steps.value.length,
    failedSteps: 0,
    isFullyCompensated: true,
    isLeaseExpired: isLeaseTimeout.value,
    latencyMs: lastRollbackLatency.value,
    sha256Signature: '8867cca3ce929d0e0e4736859d136b997a701d71c52ebe593d623d514f2fc7d1'
  }
}

// 模拟租约超时注水
function handleSimulateTimeout() {
  isLeaseTimeout.value = true
  leaseRemainingMs.value = 0
  currentFencingToken.value += 1

  quarantineSnapshot.value.push({
    stepId: 'tool-audit-notify',
    failureReason: 'Lease TTL Expired (纳秒时钟强制驱逐，世代号跃迁拦截幽灵写)',
    timestamp: Date.now()
  })

  // 触发自愈补偿
  handleTriggerCompensation()
}

// 常量时间自验真
function verifyReceipt() {
  if (!activeReceipt.value) return
  // 模拟 MessageDigest.isEqual 常量时间比对
  isReceiptVerified.value = true
}

onMounted(() => {
  timer = setInterval(() => {
    if (leaseRemainingMs.value > 100 && !isLeaseTimeout.value) {
      leaseRemainingMs.value -= 100
    }
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
/* ============================================================
   Apple iOS 26 Liquid Glass 规范 (无层叠上下文铁律 & 双层 50px 模糊)
   ============================================================ */
.tool-saga-compensation-widget {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  color: #F5F5F7;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "PingFang SC", sans-serif;
  letter-spacing: -0.43px;
  box-sizing: border-box;
}

/* 顶部通透材质胶囊工具条 */
.widget-capsule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: rgba(28, 28, 30, 0.65);
  backdrop-filter: blur(50px) saturate(190%);
  -webkit-backdrop-filter: blur(50px) saturate(190%);
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  gap: 16px;
  flex-wrap: wrap;
}

.capsule-branding {
  display: flex;
  align-items: center;
  gap: 10px;
}

.signal-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #30D158;
  transition: all 0.3s ease;
}

.signal-indicator.signal-compensating {
  background: #0A84FF;
  animation: pulse-signal 1s infinite alternate;
}

.signal-indicator.signal-timeout {
  background: #FF453A;
}

.signal-indicator.signal-verified {
  box-shadow: 0 0 10px #30D158;
}

@keyframes pulse-signal {
  from { opacity: 0.4; transform: scale(0.9); }
  to { opacity: 1; transform: scale(1.2); }
}

.capsule-title {
  font-weight: 590;
  font-size: 13px;
  color: #FFFFFF;
}

.capsule-subtag {
  font-size: 10px;
  padding: 2px 6px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  color: #98989D;
}

.capsule-telemetry {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.telemetry-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 12px;
}

.pill-label {
  color: #8E8E93;
}

.pill-value {
  font-weight: 590;
  color: #F2F2F7;
}

.capsule-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ios-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 999px;
  color: #FFFFFF;
  font-size: 12px;
  font-weight: 590;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ios-action-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.3);
}

.ios-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ios-action-btn.btn-penalty {
  border-color: rgba(255, 159, 10, 0.3);
}

.ios-action-btn.btn-highlight {
  border-color: rgba(48, 209, 88, 0.3);
}

/* 主网格排版 */
.widget-grid-layout {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 16px;
  width: 100%;
}

@media (max-width: 1100px) {
  .widget-grid-layout {
    grid-template-columns: 1fr;
  }
}

/* 液态玻璃卡片主体 */
.ios-glass-card {
  background: rgba(30, 30, 32, 0.65);
  backdrop-filter: blur(50px) saturate(190%);
  -webkit-backdrop-filter: blur(50px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 20px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-headline-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.card-headline {
  margin: 0;
  font-size: 15px;
  font-weight: 590;
  color: #FFFFFF;
}

.card-caption {
  font-size: 11px;
  color: #8E8E93;
}

.status-pill {
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 590;
  background: rgba(10, 132, 255, 0.15);
  color: #0A84FF;
  border: 1px solid rgba(10, 132, 255, 0.3);
}

.status-pill.pill-success {
  background: rgba(48, 209, 88, 0.15);
  color: #30D158;
  border-color: rgba(48, 209, 88, 0.3);
}

/* 任务规格条 */
.task-spec-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  flex-wrap: wrap;
  gap: 10px;
}

.task-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 590;
  font-size: 12px;
}

.task-metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.metric-name {
  font-size: 10px;
  color: #8E8E93;
}

.metric-num {
  font-size: 12px;
  font-weight: 590;
}

/* Saga 节点卡片流 */
.saga-step-pipeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.step-node-card {
  padding: 14px;
  background: rgba(44, 44, 46, 0.45);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.3s ease;
}

.step-node-card.node-compensating {
  border-color: rgba(10, 132, 255, 0.5);
  background: rgba(10, 132, 255, 0.1);
}

.step-node-card.node-compensated {
  border-color: rgba(48, 209, 88, 0.3);
}

.step-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.step-seq-pill {
  display: flex;
  align-items: center;
  gap: 6px;
}

.seq-num {
  font-weight: 700;
  font-size: 12px;
  color: #0A84FF;
}

.seq-tag {
  font-size: 11px;
  color: #8E8E93;
}

.step-title-cluster {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.step-name {
  font-size: 13px;
  font-weight: 590;
  color: #FFFFFF;
}

.step-action {
  font-size: 11px;
  color: #A1A1A6;
}

.step-badge {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 590;
}

.badge-executed {
  background: rgba(255, 255, 255, 0.08);
  color: #F2F2F7;
}

.badge-compensating {
  background: rgba(10, 132, 255, 0.2);
  color: #0A84FF;
}

.badge-compensated {
  background: rgba(48, 209, 88, 0.2);
  color: #30D158;
}

.step-detail-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
}

.detail-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-label {
  font-size: 10px;
  color: #8E8E93;
}

.detail-val {
  font-size: 11px;
  font-weight: 500;
}

.step-progress-track {
  width: 100%;
  height: 3px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 2px;
  overflow: hidden;
}

.step-progress-fill {
  height: 100%;
  transition: width 0.3s ease;
}

.fill-compensating {
  background: #0A84FF;
}

.fill-compensated {
  background: #30D158;
}

/* 右栏各板块 */
.sub-headline {
  font-size: 12px;
  font-weight: 590;
  color: #C7C7CC;
  margin-bottom: 8px;
}

.intent-ortho-panel, .conflict-resolution-box, .quarantine-vault-box {
  padding: 12px;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.intent-bar-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.intent-bar-row {
  display: grid;
  grid-template-columns: 110px 1fr 50px;
  align-items: center;
  gap: 8px;
}

.intent-label-cluster {
  display: flex;
  flex-direction: column;
  font-size: 11px;
}

.intent-score {
  font-size: 9px;
  color: #8E8E93;
}

.intent-track {
  height: 6px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 3px;
  overflow: hidden;
}

.intent-fill {
  height: 100%;
  background: #BF5AF2;
  border-radius: 3px;
  transition: width 0.3s ease;
}

.intent-status {
  font-size: 10px;
  text-align: right;
}

.edge-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: rgba(255, 159, 10, 0.1);
  border: 1px solid rgba(255, 159, 10, 0.3);
  border-radius: 8px;
  font-size: 11px;
}

.edge-reason {
  color: #FF9F0A;
  font-size: 10px;
  margin-left: auto;
}

.quarantine-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.quarantine-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px;
  background: rgba(255, 69, 58, 0.1);
  border-radius: 6px;
  font-size: 11px;
}

.q-badge {
  color: #FF453A;
  font-weight: 590;
}

.q-reason {
  color: #EBEBF5;
  flex: 1;
}

.q-time {
  color: #8E8E93;
  font-size: 10px;
}

.empty-hint {
  font-size: 11px;
  color: #8E8E93;
  text-align: center;
  padding: 8px;
}

/* 凭单展示卡片 */
.receipt-spec-card {
  padding: 14px;
  background: rgba(20, 20, 22, 0.7);
  border-radius: 14px;
  border: 1px dashed rgba(255, 255, 255, 0.15);
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.3s ease;
}

.receipt-spec-card.receipt-verified {
  border-style: solid;
  border-color: rgba(48, 209, 88, 0.4);
}

.receipt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.receipt-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 590;
}

.receipt-stamp {
  font-size: 10px;
  padding: 2px 8px;
  border-radius: 4px;
}

.stamp-valid {
  background: rgba(48, 209, 88, 0.15);
  color: #30D158;
}

.stamp-pending {
  background: rgba(255, 255, 255, 0.08);
  color: #8E8E93;
}

.receipt-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 11px;
}

.receipt-field {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rf-label {
  color: #8E8E93;
}

.rf-sig {
  font-family: monospace;
  font-size: 9px;
  color: #A1A1A6;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 辅助色彩类 */
.text-accent { color: #0A84FF; }
.text-success { color: #30D158; }
.text-warning { color: #FF9F0A; }
.text-danger { color: #FF453A; }
.text-dim { color: #8E8E93; }
</style>

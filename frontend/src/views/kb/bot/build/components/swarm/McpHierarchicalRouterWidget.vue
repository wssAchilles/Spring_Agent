<template>
  <div class="mcp-hierarchical-router-widget">
    <!-- Apple iOS 26 顶部通透材质胶囊工具条 (Top Liquid Glass Capsule) -->
    <header class="widget-capsule-header">
      <div class="capsule-branding">
        <div class="signal-indicator" :class="{ 'signal-routing': isRouting, 'signal-verified': isReceiptVerified, 'signal-degraded': hasDegradedTools }"></div>
        <span class="capsule-title">MASSIVE ENTERPRISE MCP VORONOI ROUTING & STREAMING DISTILLATION // PHASE 147</span>
        <span class="capsule-subtag">APPLE LIQUID GLASS SPEC</span>
      </div>

      <div class="capsule-telemetry">
        <div class="telemetry-pill">
          <span class="pill-label">注册工具池</span>
          <span class="pill-value text-accent">{{ totalToolsCount }} API ({{ voronoiCells.length }} 胞腔)</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">检索延迟</span>
          <span class="pill-value text-success">{{ routingLatencyMs.toFixed(3) }} ms (P99 ≤ 3.0ms)</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">Top-K 剪枝</span>
          <span class="pill-value text-accent">{{ candidateTools.length }} 候选 (Top-{{ topKTarget }})</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">流式蒸馏比</span>
          <span class="pill-value text-success">{{ compressionRatio.toFixed(1) }}x (削减 {{ tokenReductionPercent }}%)</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">质心保真度</span>
          <span class="pill-value text-success">{{ (semanticFidelity * 100).toFixed(1) }}% (≥ 88.0%)</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">L3 冷备隔离</span>
          <span class="pill-value text-warning">{{ quarantinedTools.length }} 工具</span>
        </div>
      </div>

      <!-- 人机协同交互操作组 (HITL Controls) -->
      <div class="capsule-actions">
        <button class="ios-action-btn" @click="handleExecuteRouting" :disabled="isRouting">
          <span class="btn-icon">⚡</span>
          <span>{{ isRouting ? 'Voronoi 路由中...' : '测地 Voronoi 动态路由' }}</span>
        </button>
        <button class="ios-action-btn btn-distill" @click="handleExecuteDistill" :disabled="isDistilling">
          <span class="btn-icon">🧪</span>
          <span>{{ isDistilling ? '流式质心压缩中...' : '流式质心认知蒸馏' }}</span>
        </button>
        <button class="ios-action-btn btn-highlight" @click="handleVerifyReceipt" :disabled="!activeReceipt">
          <span class="btn-icon">🛡️</span>
          <span>常量时间验真</span>
        </button>
      </div>
    </header>

    <!-- 主交互网格：左栏测地线 Voronoi 层次拓扑与 UCB 动态调度，右栏流式认知蒸馏与不可变凭单 -->
    <main class="widget-grid-layout">
      <!-- 左栏：测地线 Voronoi 胞腔剖分拓扑与工具臂动态排序 -->
      <section class="ios-glass-card left-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">千问 1536 维超球面测地线 Voronoi 层次化索引与 UCB 路由</h3>
            <span class="card-caption">分支定界三角不等式胞腔粗筛、对数后悔界有界老虎机探索 (Lemma 147.1)</span>
          </div>
          <span class="status-pill" :class="isRouting ? 'pill-active' : 'pill-success'">
            {{ isRouting ? '对数检索中' : 'Voronoi 树收敛就绪' }}
          </span>
        </div>

        <!-- 任务意图与几何度规规格横幅 -->
        <div class="task-spec-banner">
          <div class="task-badge">
            <span class="task-icon">🧭</span>
            <span class="task-id">QUERY: {{ currentQuery }}</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">超球面度量</span>
            <span class="metric-num text-success">d_g = arccos(u·v) ∈ [0, π]</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">分支定界胞腔</span>
            <span class="metric-num text-accent">Top-{{ branchCellsCount }} 胞腔扫描 (覆盖 {{ (branchCellsCount / Math.max(1, voronoiCells.length) * 100).toFixed(0) }}%)</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">UCB 平衡因数</span>
            <span class="metric-num text-success">α_sem = 0.65, c = 1.414</span>
          </div>
        </div>

        <!-- Voronoi 胞腔测地热力拓扑卡片群 -->
        <div class="voronoi-cells-strip">
          <div
            v-for="cell in voronoiCells"
            :key="cell.cellId"
            class="voronoi-cell-chip"
            :class="{ 'cell-active': cell.isSelected }"
          >
            <div class="cell-header">
              <span class="cell-id">CELL #{{ cell.cellId }}</span>
              <span class="cell-count">{{ cell.toolCount }} APIs</span>
            </div>
            <div class="cell-body">
              <span class="cell-radius">半角: {{ cell.radius.toFixed(3) }} rad</span>
              <span class="cell-score">余弦上界: {{ cell.potentialScore.toFixed(3) }}</span>
            </div>
          </div>
        </div>

        <!-- 细筛候选工具卡片列表 -->
        <div class="tool-candidate-pipeline">
          <div
            v-for="(tool, idx) in candidateTools"
            :key="tool.toolId"
            class="tool-card"
            :class="{
              'tool-selected': tool.isSelected,
              'tool-degraded': tool.isDegraded
            }"
          >
            <div class="tool-meta">
              <div class="tool-seq-pill">
                <span class="seq-num">#{{ idx + 1 }}</span>
                <span class="seq-cat tag-badge">{{ tool.category }}</span>
              </div>
              <div class="tool-title-cluster">
                <span class="tool-name">{{ tool.toolName }}</span>
                <span class="tool-id-text">ID: {{ tool.toolId }}</span>
              </div>
              <div class="tool-badge" :class="tool.isSelected ? 'badge-primary' : 'badge-candidate'">
                <span>{{ tool.isSelected ? '最优路由 (Top-1)' : '剪枝候选' }}</span>
              </div>
            </div>

            <!-- 遥测指标行：测地相似度、UCB 分数、调用成功率 -->
            <div class="tool-telemetry-row">
              <div class="tool-stat">
                <span class="stat-lbl">测地内积:</span>
                <span class="stat-val text-success">{{ tool.cosineSimilarity.toFixed(4) }}</span>
              </div>
              <div class="tool-stat">
                <span class="stat-lbl">UCB 综合分:</span>
                <span class="stat-val text-accent">{{ tool.ucbScore.toFixed(4) }}</span>
              </div>
              <div class="tool-stat">
                <span class="stat-lbl">历史胜率:</span>
                <span class="stat-val">{{ (tool.successRate * 100).toFixed(0) }}% ({{ tool.totalCalls }}次)</span>
              </div>
              <div class="tool-stat">
                <span class="stat-lbl">平均延迟:</span>
                <span class="stat-val">{{ tool.avgLatencyMs.toFixed(1) }}ms</span>
              </div>
            </div>

            <!-- 仿真交互：模拟下游网络故障或单步调用测试 -->
            <div class="tool-action-line">
              <span class="tool-state-indicator" :class="tool.isDegraded ? 'text-danger' : 'text-success'">
                {{ tool.isDegraded ? '⚠️ 连续故障降级 (L2 仓)' : '🟢 节点健康 (L1 活跃)' }}
              </span>
              <button
                class="mini-btn btn-fault"
                @click="handleSimulateFault(tool.toolId)"
                :title="'模拟下游微服务报错超时'"
              >
                <span>模拟故障报错</span>
              </button>
              <button
                class="mini-btn btn-quarantine"
                @click="handleSoftQuarantine(tool.toolId)"
                :title="'软隔离至 L3 冷备环形仓'"
              >
                <span>软隔离冷备</span>
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- 右栏：流式质心认知蒸馏双窗对比与三级冷备仓 -->
      <section class="ios-glass-card right-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">流式质心认知蒸馏与不可变存证凭单</h3>
            <span class="card-caption">次模骨架特征抽取、语义保真度拓扑不变性 (Lemma 147.2)</span>
          </div>
          <span class="status-pill" :class="isReceiptVerified ? 'pill-success' : 'pill-warning'">
            {{ isReceiptVerified ? 'SHA-256 常量验真有效' : '待验真' }}
          </span>
        </div>

        <!-- 蒸馏双窗对比看板 (Raw JSON vs Distilled Schema Skeleton) -->
        <div class="distill-comparison-grid">
          <!-- 原始工具海量输出 (Raw Payload) -->
          <div class="distill-window window-raw">
            <div class="window-header">
              <span class="win-title">工具原始返回体 (RAW PAYLOAD)</span>
              <span class="win-metrics text-warning">{{ rawTokensCount }} Tokens / {{ rawPayloadSizeKb }} KB</span>
            </div>
            <div class="window-content">
              <pre class="raw-code-block">{{ rawJsonSample }}</pre>
            </div>
          </div>

          <!-- 认知蒸馏结果 (Distilled Payload for DeepSeek) -->
          <div class="distill-window window-distilled">
            <div class="window-header">
              <span class="win-title">超球面质心蒸馏体 (DEEPSEEK CONTEXT CACHED)</span>
              <span class="win-metrics text-success">{{ distilledTokensCount }} Tokens ({{ compressionRatio.toFixed(1) }}x 压缩)</span>
            </div>
            <div class="window-content">
              <pre class="distilled-code-block">{{ distilledResultSample }}</pre>
            </div>
          </div>
        </div>

        <!-- 三级冷备隔离环形仓视窗 (L3 Cold Standby Ring Buffer) -->
        <div class="l3-quarantine-container">
          <div class="subcard-title-bar">
            <span class="subcard-title">三级冷备隔离环形仓 (L3 COLD STANDBY BUFFER · 容量 256)</span>
            <span class="subcard-badge">{{ quarantinedTools.length }} 个受控冷备工具</span>
          </div>

          <div v-if="quarantinedTools.length === 0" class="empty-standby-state">
            <span>当前无软删除或故障隔离工具，系统运行稳态良好</span>
          </div>

          <div v-else class="standby-tool-list">
            <div v-for="qTool in quarantinedTools" :key="qTool.toolId" class="standby-item">
              <div class="standby-info">
                <span class="standby-name">{{ qTool.toolName }} ({{ qTool.toolId }})</span>
                <span class="standby-reason text-warning">{{ qTool.quarantineReason }}</span>
              </div>
              <button class="mini-btn btn-recover" @click="handleRevokeQuarantine(qTool.toolId)">
                <span>一键可逆回滚</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 纯 Java 21 Record 凭单自验真视窗 -->
        <div class="receipt-audit-container">
          <div class="subcard-title-bar">
            <span class="subcard-title">不可变存证审计凭单 (MCP_TOOL_ROUTING_AUDIT_RECEIPT)</span>
            <span class="receipt-id-tag">{{ activeReceipt?.receiptId || 'PENDING_GENERATION' }}</span>
          </div>

          <div class="receipt-content-matrix">
            <div class="matrix-cell">
              <span class="cell-label">签名哈希 (SHA-256)</span>
              <span class="cell-value text-accent mono-text">{{ activeReceipt?.signatureSha256 || '----' }}</span>
            </div>
            <div class="matrix-cell">
              <span class="cell-label">查询特征哈希</span>
              <span class="cell-value mono-text">{{ activeReceipt?.queryHash || '----' }}</span>
            </div>
            <div class="matrix-cell">
              <span class="cell-label">扫描胞腔数</span>
              <span class="cell-value text-success">{{ activeReceipt?.scannedVoronoiCellsCount || 0 }} / {{ voronoiCells.length }} 胞腔</span>
            </div>
            <div class="matrix-cell">
              <span class="cell-label">最优工具选定</span>
              <span class="cell-value text-accent">{{ activeReceipt?.selectedToolId || 'NONE' }}</span>
            </div>
            <div class="matrix-cell">
              <span class="cell-label">路由/蒸馏总耗时</span>
              <span class="cell-value text-success">{{ ((activeReceipt?.routingLatencyNanos || 0) / 1000000).toFixed(3) }} ms</span>
            </div>
            <div class="matrix-cell">
              <span class="cell-label">密码学验真状态</span>
              <span class="cell-value" :class="isReceiptVerified ? 'text-success' : 'text-warning'">
                {{ isReceiptVerified ? 'MESSAGE_DIGEST_CONSTANT_TIME_VERIFIED' : 'UNVERIFIED' }}
              </span>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

// 仿真状态与数据定义
const currentQuery = ref('查找高并发场景下针对订单支付异常与数据库死锁的自动化自愈运维工具')
const isRouting = ref(false)
const isDistilling = ref(false)
const isReceiptVerified = ref(true)
const totalToolsCount = ref(256)
const topKTarget = ref(5)
const branchCellsCount = ref(3)
const routingLatencyMs = ref(0.985)
const compressionRatio = ref(6.4)
const semanticFidelity = ref(0.924)
const rawTokensCount = ref(1840)
const distilledTokensCount = ref(288)
const rawPayloadSizeKb = ref(22.8)

const tokenReductionPercent = computed(() => {
  return Math.round((1 - distilledTokensCount.value / Math.max(1, rawTokensCount.value)) * 100)
})

interface VoronoiCellView {
  cellId: number
  toolCount: number
  radius: number
  potentialScore: number
  isSelected: boolean
}

interface ToolCandidateView {
  toolId: string
  toolName: string
  category: string
  cosineSimilarity: number
  ucbScore: number
  successRate: number
  totalCalls: number
  avgLatencyMs: number
  isDegraded: boolean
  isSelected: boolean
}

interface QuarantinedToolView {
  toolId: string
  toolName: string
  quarantineReason: string
}

// 模拟胞腔数据
const voronoiCells = ref<VoronoiCellView[]>([
  { cellId: 0, toolCount: 18, radius: 0.285, potentialScore: 0.942, isSelected: true },
  { cellId: 1, toolCount: 16, radius: 0.312, potentialScore: 0.915, isSelected: true },
  { cellId: 2, toolCount: 14, radius: 0.298, potentialScore: 0.884, isSelected: true },
  { cellId: 3, toolCount: 17, radius: 0.340, potentialScore: 0.720, isSelected: false },
  { cellId: 4, toolCount: 15, radius: 0.325, potentialScore: 0.685, isSelected: false },
  { cellId: 5, toolCount: 19, radius: 0.360, potentialScore: 0.650, isSelected: false },
  { cellId: 6, toolCount: 16, radius: 0.305, potentialScore: 0.612, isSelected: false },
  { cellId: 7, toolCount: 15, radius: 0.318, potentialScore: 0.580, isSelected: false }
])

// 模拟剪枝候选工具
const candidateTools = ref<ToolCandidateView[]>([
  {
    toolId: 'tool-db-deadlock-remediator',
    toolName: 'DatabaseTransactionDeadlockRemediator',
    category: 'DATABASE',
    cosineSimilarity: 0.9325,
    ucbScore: 0.9480,
    successRate: 0.98,
    totalCalls: 340,
    avgLatencyMs: 12.5,
    isDegraded: false,
    isSelected: true
  },
  {
    toolId: 'tool-pay-rollback-pipeline',
    toolName: 'PaymentCompensationSagaPipeline',
    category: 'PAYMENT',
    cosineSimilarity: 0.8950,
    ucbScore: 0.9120,
    successRate: 0.96,
    totalCalls: 215,
    avgLatencyMs: 18.2,
    isDegraded: false,
    isSelected: false
  },
  {
    toolId: 'tool-sec-anomaly-tracer',
    toolName: 'SecurityAuditAnomalyTracer',
    category: 'SECURITY',
    cosineSimilarity: 0.8640,
    ucbScore: 0.8750,
    successRate: 0.95,
    totalCalls: 180,
    avgLatencyMs: 9.4,
    isDegraded: false,
    isSelected: false
  },
  {
    toolId: 'tool-conn-pool-expander',
    toolName: 'HikariConnectionPoolDynamicScaler',
    category: 'DATABASE',
    cosineSimilarity: 0.8520,
    ucbScore: 0.8610,
    successRate: 0.99,
    totalCalls: 412,
    avgLatencyMs: 8.1,
    isDegraded: false,
    isSelected: false
  },
  {
    toolId: 'tool-k8s-pod-scaler',
    toolName: 'KubernetesPodReplicaAutoScaler',
    category: 'DEVOPS',
    cosineSimilarity: 0.8210,
    ucbScore: 0.8350,
    successRate: 0.94,
    totalCalls: 150,
    avgLatencyMs: 25.0,
    isDegraded: false,
    isSelected: false
  }
])

const quarantinedTools = ref<QuarantinedToolView[]>([
  {
    toolId: 'tool-legacy-ftp-exporter',
    toolName: 'LegacyFtpLogExporter',
    quarantineReason: '连续 5 次超时熔断，已软隔离至 L3 冷备环形仓'
  }
])

const hasDegradedTools = computed(() => {
  return candidateTools.value.some(t => t.isDegraded) || quarantinedTools.value.length > 0
})

// 原始 Raw JSON 样本
const rawJsonSample = ref(`{
  "status": "ANOMALY_DETECTED",
  "cluster_id": "prod-k8s-db-01",
  "deadlock_events": [
    {
      "timestamp": "2026-09-26T10:15:30.124Z",
      "tx_id": "TX-99824-A",
      "holding_lock": "ROW_LOCK(orders_tab#8841)",
      "waiting_lock": "ROW_LOCK(payments_tab#1029)",
      "thread": "db-worker-pool-88"
    },
    {
      "timestamp": "2026-09-26T10:15:30.130Z",
      "tx_id": "TX-99824-B",
      "holding_lock": "ROW_LOCK(payments_tab#1029)",
      "waiting_lock": "ROW_LOCK(orders_tab#8841)",
      "thread": "db-worker-pool-92"
    }
  ],
  "diagnostics": {
    "active_connections": 198,
    "max_pool_size": 200,
    "blocked_queries_count": 42,
    "engine": "InnoDB",
    "recommendation": "ROLLBACK_YOUNGER_TRANSACTION_AND_RELEASE_LOCK"
  }
}`)

// 蒸馏输出结构样本
const distilledResultSample = ref(`[MCP_DISTILLED_PAYLOAD tool_id=DatabaseTransactionDeadlockRemediator schema_hash=8a9f24cb5e1d fidelity=0.9240 compression=6.4x]
• cluster_id: prod-k8s-db-01 (InnoDB)
• deadlock_summary: TX-99824-A & TX-99824-B circular wait on orders_tab#8841 and payments_tab#1029
• pool_exhaustion: active_connections=198/200 (blocked=42)
• action_advised: ROLLBACK_YOUNGER_TRANSACTION_AND_RELEASE_LOCK (Safe Idempotent)`)

// 凭单数据
const activeReceipt = ref({
  receiptId: 'RCP-MCP-ROUTER-7b19a4e2',
  tenantId: 'tenant-enterprise-prod',
  taskId: 'task-db-deadlock-remediation',
  traceId: 'w3c-trace-9f82419a',
  queryHash: '5e8b24a91c0f772e',
  scannedVoronoiCellsCount: 3,
  selectedToolId: 'DatabaseTransactionDeadlockRemediator',
  selectedUcbScore: 0.9480,
  selectedCosineSimilarity: 0.9325,
  signatureSha256: '9f81a7b4c23e84019a3d421890cf517e3a8901235b2e9871fa403198de7611ab',
  routingLatencyNanos: 985000
})

// 交互操作逻辑
function handleExecuteRouting() {
  isRouting.value = true
  setTimeout(() => {
    routingLatencyMs.value = 0.850 + Math.random() * 0.35
    activeReceipt.value = {
      ...activeReceipt.value,
      receiptId: 'RCP-MCP-ROUTER-' + Math.random().toString(36).substring(2, 10),
      routingLatencyNanos: Math.round(routingLatencyMs.value * 1000000)
    }
    isReceiptVerified.value = true
    isRouting.value = false
  }, 400)
}

function handleExecuteDistill() {
  isDistilling.value = true
  setTimeout(() => {
    compressionRatio.value = 5.8 + Math.random() * 1.2
    semanticFidelity.value = 0.910 + Math.random() * 0.04
    isDistilling.value = false
  }, 350)
}

function handleVerifyReceipt() {
  isReceiptVerified.value = true
}

function handleSimulateFault(toolId: string) {
  const tool = candidateTools.value.find(t => t.toolId === toolId)
  if (tool) {
    tool.isDegraded = true
    tool.ucbScore = Math.max(0.2, tool.ucbScore - 0.45)
    tool.successRate = Math.max(0.4, tool.successRate - 0.25)
    // 自动重新排序
    candidateTools.value.sort((a, b) => b.ucbScore - a.ucbScore)
  }
}

function handleSoftQuarantine(toolId: string) {
  const idx = candidateTools.value.findIndex(t => t.toolId === toolId)
  if (idx !== -1) {
    const removed = candidateTools.value.splice(idx, 1)[0]
    quarantinedTools.value.push({
      toolId: removed.toolId,
      toolName: removed.toolName,
      quarantineReason: '管理员手动软隔离至 L3 冷备环形仓'
    })
  }
}

function handleRevokeQuarantine(toolId: string) {
  const idx = quarantinedTools.value.findIndex(t => t.toolId === toolId)
  if (idx !== -1) {
    const restored = quarantinedTools.value.splice(idx, 1)[0]
    candidateTools.value.push({
      toolId: restored.toolId,
      toolName: restored.toolName,
      category: 'DATABASE',
      cosineSimilarity: 0.88,
      ucbScore: 0.89,
      successRate: 0.95,
      totalCalls: 100,
      avgLatencyMs: 14.0,
      isDegraded: false,
      isSelected: false
    })
    candidateTools.value.sort((a, b) => b.ucbScore - a.ucbScore)
  }
}
</script>

<style scoped>
/* 顶级 Apple iOS 26 Liquid Glass 质感与无层叠上下文铁律规范 */
.mcp-hierarchical-router-widget {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'PingFang SC', sans-serif;
  color: #1d1d1f;
  box-sizing: border-box;
}

/* 顶部通透胶囊导航条 */
.widget-capsule-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(50px) saturate(190%);
  -webkit-backdrop-filter: blur(50px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.45);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.6);
  flex-wrap: wrap;
  gap: 12px;
}

.capsule-branding {
  display: flex;
  align-items: center;
  gap: 10px;
}

.signal-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #34C759;
  transition: all 0.3s ease;
}

.signal-indicator.signal-routing {
  background: #007AFF;
  transform: scale(1.2);
}

.signal-indicator.signal-degraded {
  background: #FF9500;
}

.capsule-title {
  font-size: 13px;
  font-weight: 590;
  letter-spacing: -0.015em;
  color: #1d1d1f;
}

.capsule-subtag {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(0, 122, 255, 0.1);
  color: #007AFF;
  font-weight: 600;
}

.capsule-telemetry {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.telemetry-pill {
  display: flex;
  flex-direction: column;
  padding: 4px 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.03);
  border: 0.5px solid rgba(0, 0, 0, 0.04);
}

.pill-label {
  font-size: 9px;
  color: #86868b;
  text-transform: uppercase;
}

.pill-value {
  font-size: 11px;
  font-weight: 600;
  color: #1d1d1f;
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
  padding: 7px 14px;
  border-radius: 12px;
  border: 1px solid rgba(0, 122, 255, 0.3);
  background: rgba(0, 122, 255, 0.12);
  color: #007AFF;
  font-size: 12px;
  font-weight: 590;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.5);
}

.ios-action-btn:hover:not(:disabled) {
  background: rgba(0, 122, 255, 0.22);
}

.ios-action-btn.btn-distill {
  border-color: rgba(175, 82, 222, 0.3);
  background: rgba(175, 82, 222, 0.12);
  color: #AF52DE;
}

.ios-action-btn.btn-highlight {
  border-color: rgba(52, 199, 89, 0.3);
  background: rgba(52, 199, 89, 0.12);
  color: #34C759;
}

.ios-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 主网格布局 */
.widget-grid-layout {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 16px;
}

@media (max-width: 1200px) {
  .widget-grid-layout {
    grid-template-columns: 1fr;
  }
}

.ios-glass-card {
  padding: 18px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(50px) saturate(190%);
  -webkit-backdrop-filter: blur(50px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.7);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.card-headline-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.title-cluster {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.card-headline {
  margin: 0;
  font-size: 15px;
  font-weight: 590;
  letter-spacing: -0.015em;
  color: #1d1d1f;
}

.card-caption {
  font-size: 11px;
  color: #86868b;
}

.status-pill {
  font-size: 10px;
  padding: 3px 8px;
  border-radius: 8px;
  font-weight: 600;
}

.pill-success {
  background: rgba(52, 199, 89, 0.15);
  color: #34C759;
}

.pill-active {
  background: rgba(0, 122, 255, 0.15);
  color: #007AFF;
}

.pill-warning {
  background: rgba(255, 149, 0, 0.15);
  color: #FF9500;
}

/* 规格横幅 */
.task-spec-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.025);
  border: 0.5px solid rgba(0, 0, 0, 0.05);
  flex-wrap: wrap;
  gap: 8px;
}

.task-badge {
  display: flex;
  align-items: center;
  gap: 6px;
}

.task-id {
  font-size: 11px;
  font-weight: 600;
  color: #1d1d1f;
}

.task-metric {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.metric-name {
  font-size: 9px;
  color: #86868b;
}

.metric-num {
  font-size: 11px;
  font-weight: 600;
}

/* Voronoi 胞腔条带 */
.voronoi-cells-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.voronoi-cell-chip {
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: all 0.2s ease;
}

.voronoi-cell-chip.cell-active {
  background: rgba(0, 122, 255, 0.08);
  border-color: rgba(0, 122, 255, 0.35);
  box-shadow: inset 0 1px 1px rgba(0, 122, 255, 0.2);
}

.cell-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cell-id {
  font-size: 10px;
  font-weight: 600;
  color: #007AFF;
}

.cell-count {
  font-size: 9px;
  color: #86868b;
}

.cell-body {
  display: flex;
  flex-direction: column;
  font-size: 9px;
  color: #6e6e73;
}

/* 工具卡片流水线 */
.tool-candidate-pipeline {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tool-card {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all 0.2s ease;
}

.tool-card.tool-selected {
  border-color: rgba(0, 122, 255, 0.4);
  background: rgba(0, 122, 255, 0.04);
}

.tool-card.tool-degraded {
  border-color: rgba(255, 59, 48, 0.3);
  background: rgba(255, 59, 48, 0.03);
}

.tool-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tool-seq-pill {
  display: flex;
  align-items: center;
  gap: 4px;
}

.seq-num {
  font-size: 11px;
  font-weight: 700;
  color: #86868b;
}

.tag-badge {
  font-size: 9px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.06);
  font-weight: 600;
}

.tool-title-cluster {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.tool-name {
  font-size: 12px;
  font-weight: 600;
  color: #1d1d1f;
}

.tool-id-text {
  font-size: 9px;
  color: #86868b;
}

.badge-primary {
  font-size: 9px;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(0, 122, 255, 0.15);
  color: #007AFF;
  font-weight: 600;
}

.badge-candidate {
  font-size: 9px;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.04);
  color: #6e6e73;
}

.tool-telemetry-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.02);
}

.tool-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
}

.stat-lbl {
  color: #86868b;
}

.stat-val {
  font-weight: 600;
}

.tool-action-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 10px;
}

.mini-btn {
  padding: 3px 8px;
  border-radius: 6px;
  border: 1px solid rgba(0, 0, 0, 0.1);
  background: rgba(255, 255, 255, 0.8);
  font-size: 10px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mini-btn.btn-fault {
  color: #FF3B30;
  border-color: rgba(255, 59, 48, 0.2);
}

.mini-btn.btn-quarantine {
  color: #FF9500;
  border-color: rgba(255, 149, 0, 0.2);
}

.mini-btn.btn-recover {
  color: #34C759;
  border-color: rgba(52, 199, 89, 0.3);
}

/* 右栏蒸馏对比窗 */
.distill-comparison-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

@media (max-width: 800px) {
  .distill-comparison-grid {
    grid-template-columns: 1fr;
  }
}

.distill-window {
  border-radius: 16px;
  background: rgba(0, 0, 0, 0.03);
  border: 1px solid rgba(0, 0, 0, 0.06);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.window-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.win-title {
  font-size: 9px;
  font-weight: 700;
  color: #86868b;
  text-transform: uppercase;
}

.win-metrics {
  font-size: 10px;
  font-weight: 600;
}

.window-content {
  max-height: 160px;
  overflow-y: auto;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
  padding: 8px;
}

.raw-code-block,
.distilled-code-block {
  margin: 0;
  font-family: 'SF Mono', Monaco, Menlo, monospace;
  font-size: 10px;
  line-height: 1.4;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 冷备仓 */
.l3-quarantine-container {
  padding: 12px;
  border-radius: 16px;
  background: rgba(255, 149, 0, 0.04);
  border: 1px solid rgba(255, 149, 0, 0.2);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.subcard-title-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.subcard-title {
  font-size: 11px;
  font-weight: 600;
  color: #1d1d1f;
}

.subcard-badge {
  font-size: 10px;
  color: #FF9500;
  font-weight: 600;
}

.empty-standby-state {
  font-size: 11px;
  color: #86868b;
  text-align: center;
  padding: 8px;
}

.standby-tool-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.standby-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.8);
}

.standby-info {
  display: flex;
  flex-direction: column;
  font-size: 10px;
}

.standby-name {
  font-weight: 600;
}

.standby-reason {
  font-size: 9px;
}

/* 凭单矩阵 */
.receipt-audit-container {
  padding: 12px;
  border-radius: 16px;
  background: rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.receipt-id-tag {
  font-size: 9px;
  font-family: 'SF Mono', monospace;
  color: #86868b;
}

.receipt-content-matrix {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

@media (max-width: 900px) {
  .receipt-content-matrix {
    grid-template-columns: 1fr;
  }
}

.matrix-cell {
  padding: 6px 8px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cell-label {
  font-size: 8px;
  color: #86868b;
  text-transform: uppercase;
}

.cell-value {
  font-size: 10px;
  font-weight: 600;
  word-break: break-all;
}

.mono-text {
  font-family: 'SF Mono', Monaco, Menlo, monospace;
}

/* 信号色彩系统 */
.text-accent {
  color: #007AFF;
}

.text-success {
  color: #34C759;
}

.text-warning {
  color: #FF9500;
}

.text-danger {
  color: #FF3B30;
}
</style>

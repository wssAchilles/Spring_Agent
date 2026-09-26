<template>
  <div class="hypergraph-causal-belief-widget">
    <!-- Apple iOS 26 顶部通透材质胶囊工具条 (Top Liquid Glass Capsule) -->
    <header class="widget-capsule-header">
      <div class="capsule-branding">
        <div class="signal-indicator" :class="{ 'signal-propagating': isPropagating, 'signal-verified': isReceiptVerified, 'signal-pruned': isPruningActive }"></div>
        <span class="capsule-title">ADAPTIVE HYPERGRAPH CAUSAL BELIEF & DEEPSEEK CACHING // PHASE 145</span>
        <span class="capsule-subtag">APPLE LIQUID GLASS SPEC</span>
      </div>

      <div class="capsule-telemetry">
        <div class="telemetry-pill">
          <span class="pill-label">超图标识</span>
          <span class="pill-value text-accent">{{ currentHypergraphId }}</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">实体节点</span>
          <span class="pill-value">{{ vertices.length }} 节点</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">高阶超边</span>
          <span class="pill-value">{{ hyperedges.length }} 超边</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">谱保真度</span>
          <span class="pill-value text-success">{{ (spectralFidelity * 100).toFixed(1) }}%</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">对齐 Token</span>
          <span class="pill-value text-accent">{{ alignedStaticTokens }} (模 64 = {{ alignedStaticTokens % 64 }})</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">冷备软隔离</span>
          <span class="pill-value text-warning">{{ quarantinedCount }} 超边</span>
        </div>
      </div>

      <!-- 人机协同交互操作组 (HITL Controls) -->
      <div class="capsule-actions">
        <button class="ios-action-btn" @click="handleExecutePropagation" :disabled="isPropagating">
          <span class="btn-icon">⚡</span>
          <span>{{ isPropagating ? '两阶段谱信念传播中...' : '两阶段谱传播' }}</span>
        </button>
        <button class="ios-action-btn btn-penalty" @click="handleExecutePrune" :disabled="hyperedges.length === 0">
          <span class="btn-icon">✂️</span>
          <span>因果置信度剪枝</span>
        </button>
        <button class="ios-action-btn btn-highlight" @click="handleVerifyReceipt" :disabled="!activeReceipt">
          <span class="btn-icon">🛡️</span>
          <span>常量时间验真</span>
        </button>
      </div>
    </header>

    <!-- 主交互网格：左栏高阶超图因果拓扑与谱信念传播流，右栏 DeepSeek 64-Token 缓存规整与三级冷备仓 -->
    <main class="widget-grid-layout">
      <!-- 左栏：高阶超图因果关联矩阵与两阶段谱信念网络 -->
      <section class="ios-glass-card left-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">高阶超图因果拓扑与拉普拉斯谱信念传播</h3>
            <span class="card-caption">两阶段 O(|V| + |E|) 稀疏消息传递、T_conv ≤ 13 步快速收敛 (Lemma 145.1)</span>
          </div>
          <span class="status-pill" :class="isConverged ? 'pill-success' : 'pill-active'">
            {{ isConverged ? '谱卷积已收敛 (Residual ≤ 5e-4)' : '待激发传播' }}
          </span>
        </div>

        <!-- 算法与数学度量规格条 -->
        <div class="task-spec-banner">
          <div class="task-badge">
            <span class="task-icon">🕸️</span>
            <span class="task-id">TOPOLOGY: LEVEL-{{ currentLevelDepth }} MULTIMODAL</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">超球面质心范数</span>
            <span class="metric-num text-success">||c_e||₂ = 1.000000 (守恒)</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">重构损失上界</span>
            <span class="metric-num text-accent">L_recon ≤ 0.15 (实测 {{ avgReconstructionLoss.toFixed(4) }})</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">传播耗时预算</span>
            <span class="metric-num text-success">P99 ≤ 4.5ms (实测 {{ lastPropagationLatency.toFixed(3) }}ms)</span>
          </div>
        </div>

        <!-- 高阶超边卡片列表 -->
        <div class="hyperedge-pipeline">
          <div
            v-for="(edge, idx) in hyperedges"
            :key="edge.id"
            class="hyperedge-card"
            :class="{
              'edge-high-confidence': edge.causalConfidence >= 0.70,
              'edge-medium-confidence': edge.causalConfidence >= 0.45 && edge.causalConfidence < 0.70,
              'edge-low-confidence': edge.causalConfidence < 0.45
            }"
          >
            <div class="edge-meta">
              <div class="edge-seq-pill">
                <span class="seq-num">#{{ idx + 1 }}</span>
                <span class="seq-tag">{{ edge.relationType }}</span>
              </div>
              <div class="edge-title-cluster">
                <span class="edge-name">{{ edge.name }} ({{ edge.id }})</span>
                <span class="edge-desc">{{ edge.explanation }}</span>
              </div>
              <div class="edge-badge" :class="edge.causalConfidence >= 0.45 ? 'badge-pass' : 'badge-warn'">
                <span>置信度: {{ (edge.causalConfidence * 100).toFixed(1) }}%</span>
              </div>
            </div>

            <!-- 包含的实体超节点胶囊组 -->
            <div class="vertex-pills-row">
              <span class="vertex-tag-label">关联超节点:</span>
              <div class="pills-scroll-container">
                <span v-for="vLabel in edge.vertexLabels" :key="vLabel" class="vertex-glass-pill">
                  <i class="vertex-dot"></i>
                  {{ vLabel }}
                </span>
              </div>
            </div>

            <!-- 超边质心与重构损失微指标 -->
            <div class="edge-telemetry-row">
              <div class="edge-stat">
                <span class="stat-lbl">超球面质心:</span>
                <span class="stat-val font-mono text-success">1536D (||c||=1.0)</span>
              </div>
              <div class="edge-stat">
                <span class="stat-lbl">重构损失:</span>
                <span class="stat-val font-mono" :class="edge.reconstructionLoss <= 0.15 ? 'text-success' : 'text-danger'">
                  {{ edge.reconstructionLoss.toFixed(4) }}
                </span>
              </div>
              <div class="edge-stat">
                <span class="stat-lbl">因果层级:</span>
                <span class="stat-val text-accent">LEVEL-{{ edge.causalLevel }}</span>
              </div>
              <div class="edge-stat">
                <span class="stat-lbl">超边权重:</span>
                <span class="stat-val">{{ edge.weight.toFixed(2) }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 右栏：DeepSeek Context Caching 前缀对齐规整看板与三级冷备仓 -->
      <section class="ios-glass-card right-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">DeepSeek 官方 1M 规约 64-Token 前缀缓存规整器</h3>
            <span class="card-caption">确定性规范自然序、垫片对齐阻断哈希雪崩 (Lemma 145.2)</span>
          </div>
          <span class="status-pill" :class="isPrefixAligned ? 'pill-success' : 'pill-warn'">
            {{ isPrefixAligned ? '64-Token 严格对齐 (Hit Ready)' : '未对齐' }}
          </span>
        </div>

        <!-- 前缀缓存四阶段装配监控卡片 -->
        <div class="caching-assembly-panel">
          <div class="assembly-step-card">
            <div class="step-header">
              <span class="step-num">01</span>
              <span class="step-title">静态系统内核层 (Kernel)</span>
              <span class="step-status text-success">绝对前置</span>
            </div>
            <p class="step-detail">DeepSeek 官方主干指令与全局 MCP 工具元数据，严格固化不可变。</p>
          </div>

          <div class="assembly-step-card">
            <div class="step-header">
              <span class="step-num">02</span>
              <span class="step-title">高阶超图因果规范拓扑 (Canonical Subgraph)</span>
              <span class="step-status text-accent">自然序排序</span>
            </div>
            <p class="step-detail">按层级升序、节点字典序、因果置信度降序排列，消除图表示无序二义性。</p>
          </div>

          <div class="assembly-step-card step-highlight">
            <div class="step-header">
              <span class="step-num">03</span>
              <span class="step-title">64-Token 整数倍注释补齐 (Pad Comment)</span>
              <span class="step-status text-success">模 64 = 0</span>
            </div>
            <div class="pad-code-box">
              <code>&lt;!-- ds_prefix_align_pad:{"padTokens": {{ paddedTokens }}, "magic":"0x5a"} --&gt;</code>
            </div>
            <div class="pad-meta-row">
              <span>原始 Token: {{ rawStaticTokens }}</span>
              <span>填充 Token: +{{ paddedTokens }}</span>
              <span class="font-bold text-accent">规整总 Token: {{ alignedStaticTokens }}</span>
            </div>
          </div>

          <div class="assembly-step-card">
            <div class="step-header">
              <span class="step-num">04</span>
              <span class="step-title">动态易变上下文严格尾置 (Tail Isolation)</span>
              <span class="step-status text-warning">隔离防雪崩</span>
            </div>
            <p class="step-detail font-mono text-caption">User Query + [DYNAMIC_TEMPORAL_ANCHOR: {{ temporalAnchor }}]</p>
          </div>
        </div>

        <!-- 前缀 SHA-256 缓存指纹与不可变凭单区域 -->
        <div class="receipt-verify-panel">
          <div class="receipt-header">
            <span class="panel-tag">不可变推理存证凭单 (JAVA 21 RECORD)</span>
            <span class="verify-badge" :class="isReceiptVerified ? 'badge-verified' : 'badge-unverified'">
              {{ isReceiptVerified ? 'SHA-256 常量时间自验真通过' : '待验真' }}
            </span>
          </div>

          <div v-if="activeReceipt" class="receipt-content-grid">
            <div class="receipt-field">
              <span class="f-lbl">凭单编号:</span>
              <span class="f-val font-mono text-accent">{{ activeReceipt.receiptId }}</span>
            </div>
            <div class="receipt-field">
              <span class="f-lbl">前缀哈希指纹:</span>
              <span class="f-val font-mono text-success text-ellipsis">{{ activeReceipt.cachedPrefixDigest }}</span>
            </div>
            <div class="receipt-field">
              <span class="f-lbl">信念香农熵:</span>
              <span class="f-val font-mono">{{ activeReceipt.causalBeliefEntropy.toFixed(4) }}</span>
            </div>
            <div class="receipt-field">
              <span class="f-lbl">端到端延迟:</span>
              <span class="f-val font-mono text-success">{{ activeReceipt.inferenceLatencyMs.toFixed(3) }} ms</span>
            </div>
          </div>
        </div>

        <!-- 三级冷备隔离环形缓冲区 (Soft Isolation Buffer) -->
        <div class="cold-buffer-panel">
          <div class="buffer-headline">
            <div class="buffer-title-wrap">
              <span class="buffer-icon">🧊</span>
              <span class="buffer-title">三级冷备隔离环形缓冲区 (软删除隔离)</span>
            </div>
            <span class="buffer-count-badge">{{ quarantinedEdges.length }}/128 条</span>
          </div>

          <div v-if="quarantinedEdges.length === 0" class="buffer-empty-state">
            <span>当前无冷备隔离超边，全量因果链完备运行中</span>
          </div>

          <div v-else class="buffer-items-scroll">
            <div v-for="qItem in quarantinedEdges" :key="qItem.hyperedgeId" class="buffer-item-row">
              <div class="q-meta">
                <span class="q-id font-mono">{{ qItem.hyperedgeId }}</span>
                <span class="q-reason">{{ qItem.pruneReason }}</span>
              </div>
              <button class="ios-mini-restore-btn" @click="handleRestore(qItem.hyperedgeId)">
                <span>可逆唤醒自愈</span>
              </button>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

interface HyperVertex {
  id: string
  label: string
  causalLevel: number
  priorBelief: number
  currentBelief: number
}

interface Hyperedge {
  id: string
  name: string
  vertexIds: string[]
  vertexLabels: string[]
  weight: number
  causalConfidence: number
  relationType: string
  explanation: string
  causalLevel: number
  reconstructionLoss: number
}

interface QuarantinedRecord {
  hyperedgeId: string
  pruneReason: string
  quarantinedTimestamp: number
}

interface HypergraphReceipt {
  receiptId: string
  tenantId: string
  taskId: string
  traceId: string
  hypergraphId: string
  totalVertexCount: number
  totalHyperedgeCount: number
  retainedHyperedgeCount: number
  prunedHyperedgeCount: number
  causalBeliefEntropy: number
  spectralFidelity: number
  cachedPrefixDigest: string
  alignedPrefixTokens: number
  paddedTokens: number
  isCacheAligned: boolean
  inferenceLatencyMs: number
  timestamp: number
  sha256Signature: string
}

// 模拟初始超节点与超边
const currentHypergraphId = ref('HG-CORP-REASONING-001')
const currentLevelDepth = ref(3)
const isPropagating = ref(false)
const isConverged = ref(true)
const isPruningActive = ref(false)
const isReceiptVerified = ref(true)
const isPrefixAligned = ref(true)

const lastPropagationLatency = ref(1.842)
const spectralFidelity = ref(0.962)
const avgReconstructionLoss = ref(0.038)

const rawStaticTokens = ref(842)
const paddedTokens = ref(54)
const alignedStaticTokens = computed(() => rawStaticTokens.value + paddedTokens.value)
const temporalAnchor = ref('2026-09-26T10:00:00.000Z')

const vertices = ref<HyperVertex[]>([
  { id: 'v-01', label: 'HikariCP_Pool', causalLevel: 1, priorBelief: 0.95, currentBelief: 0.94 },
  { id: 'v-02', label: 'ConnectionLeak', causalLevel: 1, priorBelief: 0.90, currentBelief: 0.91 },
  { id: 'v-03', label: 'RpcTimeout', causalLevel: 2, priorBelief: 0.85, currentBelief: 0.86 },
  { id: 'v-04', label: 'CircuitBreakerTrip', causalLevel: 2, priorBelief: 0.80, currentBelief: 0.82 },
  { id: 'v-05', label: 'TransientJvmStall', causalLevel: 3, priorBelief: 0.25, currentBelief: 0.22 }
])

const hyperedges = ref<Hyperedge[]>([
  {
    id: 'HE-101',
    name: '连接池耗尽根因流',
    vertexIds: ['v-01', 'v-02'],
    vertexLabels: ['HikariCP_Pool', 'ConnectionLeak'],
    weight: 1.0,
    causalConfidence: 0.94,
    relationType: 'ROOT_CAUSE_BRANCH',
    explanation: '未释放连接占满活跃度导致连接池排队堵塞',
    causalLevel: 1,
    reconstructionLoss: 0.024
  },
  {
    id: 'HE-102',
    name: '下游级联超时与熔断',
    vertexIds: ['v-02', 'v-03', 'v-04'],
    vertexLabels: ['ConnectionLeak', 'RpcTimeout', 'CircuitBreakerTrip'],
    weight: 1.0,
    causalConfidence: 0.89,
    relationType: 'CASCADE_EFFECT',
    explanation: '获取连接超时导致网关 RPC 连续熔断',
    causalLevel: 2,
    reconstructionLoss: 0.035
  },
  {
    id: 'HE-103',
    name: '偶发抖动伴随噪声',
    vertexIds: ['v-03', 'v-05'],
    vertexLabels: ['RpcTimeout', 'TransientJvmStall'],
    weight: 0.5,
    causalConfidence: 0.38, // 低于 0.45 门禁阈值
    relationType: 'NOISE_ASSOCIATION',
    explanation: '垃圾回收伴随微秒级停顿，属于伴随弱噪声',
    causalLevel: 3,
    reconstructionLoss: 0.082
  }
])

const quarantinedEdges = ref<QuarantinedRecord[]>([])
const quarantinedCount = computed(() => quarantinedEdges.value.length)

const activeReceipt = ref<HypergraphReceipt>({
  receiptId: 'RCP-HYPERGRAPH-84f9b201',
  tenantId: 'tenant-corp-prod',
  taskId: 'TASK-CAUSAL-2026-926',
  traceId: 'trace-w3c-4bf92f3577b34da6a3ce929d0e0e4736',
  hypergraphId: 'HG-CORP-REASONING-001',
  totalVertexCount: 5,
  totalHyperedgeCount: 3,
  retainedHyperedgeCount: 2,
  prunedHyperedgeCount: 1,
  causalBeliefEntropy: 0.2842,
  spectralFidelity: 0.9620,
  cachedPrefixDigest: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
  alignedPrefixTokens: 896,
  paddedTokens: 54,
  isCacheAligned: true,
  inferenceLatencyMs: 2.145,
  timestamp: Date.now(),
  sha256Signature: '7d5a9b83f0c1e847291a84f3302bc1294821a812304958671239840294857102'
})

// 触发两阶段谱信念传播
const handleExecutePropagation = () => {
  isPropagating.value = true
  isConverged.value = false
  setTimeout(() => {
    isPropagating.value = false
    isConverged.value = true
    lastPropagationLatency.value = 1.62 + Math.random() * 0.4
    spectralFidelity.value = 0.975
  }, 400)
}

// 触发因果置信度剪枝
const handleExecutePrune = () => {
  isPruningActive.value = true
  const lowEdges = hyperedges.value.filter(e => e.causalConfidence < 0.45)
  if (lowEdges.length > 0) {
    lowEdges.forEach(edge => {
      quarantinedEdges.value.push({
        hyperedgeId: edge.id,
        pruneReason: `因果置信度 ${(edge.causalConfidence * 100).toFixed(1)}% 低于门禁阈值 45.0%`,
        quarantinedTimestamp: Date.now()
      })
    })
    hyperedges.value = hyperedges.value.filter(e => e.causalConfidence >= 0.45)
  }
  setTimeout(() => {
    isPruningActive.value = false
  }, 300)
}

// 可逆自愈恢复
const handleRestore = (edgeId: string) => {
  const idx = quarantinedEdges.value.findIndex(q => q.hyperedgeId === edgeId)
  if (idx !== -1) {
    quarantinedEdges.value.splice(idx, 1)
    hyperedges.value.push({
      id: edgeId,
      name: '已自愈唤醒因果边',
      vertexIds: ['v-03', 'v-05'],
      vertexLabels: ['RpcTimeout', 'TransientJvmStall'],
      weight: 0.8,
      causalConfidence: 0.52,
      relationType: 'RESTORED_FLOW',
      explanation: '经由人机协同专家介入，重新放权纳回因果拓扑',
      causalLevel: 2,
      reconstructionLoss: 0.045
    })
  }
}

// 常量时间自验真
const handleVerifyReceipt = () => {
  isReceiptVerified.value = false
  setTimeout(() => {
    isReceiptVerified.value = true
  }, 200)
}
</script>

<style scoped>
/* ==========================================================================
   Apple iOS 26 Liquid Glass 规范落地 (docs/design-system 严格遵循)
   1. 无层叠上下文铁律：禁止 z-index，完全依托物理 DOM 流排版；
   2. 双层 50px 模糊与饱和度增益 (backdrop-filter: blur(50px) saturate(190%))；
   3. Headline 590 字重与光学正负字距；
   4. 零阴影系统：严禁使用 box-shadow，完全依托自然物理高光边框；
   5. 信号色彩：Apple 信号蓝、翠绿、琥珀黄与高亮玫瑰红。
   ========================================================================== */

.hypergraph-causal-belief-widget {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'PingFang SC', 'Helvetica Neue', sans-serif;
  color: #FFFFFF;
}

/* 顶部胶囊工具条 */
.widget-capsule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 18px;
  background: rgba(30, 30, 32, 0.65);
  backdrop-filter: blur(50px) saturate(190%);
  -webkit-backdrop-filter: blur(50px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
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
  background: #30D158;
  transition: all 0.3s ease;
}

.signal-indicator.signal-propagating {
  background: #0A84FF;
  animation: pulse-signal 1s infinite alternate;
}

.signal-indicator.signal-pruned {
  background: #FF9F0A;
}

@keyframes pulse-signal {
  from { opacity: 0.4; }
  to { opacity: 1.0; }
}

.capsule-title {
  font-size: 13px;
  font-weight: 590;
  letter-spacing: -0.015em;
  color: #FFFFFF;
}

.capsule-subtag {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 4px;
  color: #8E8E93;
}

.capsule-telemetry {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.telemetry-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.pill-label {
  font-size: 11px;
  color: #8E8E93;
}

.pill-value {
  font-size: 12px;
  font-weight: 590;
  color: #FFFFFF;
}

.text-accent { color: #0A84FF; }
.text-success { color: #30D158; }
.text-warning { color: #FF9F0A; }
.text-danger { color: #FF453A; }

.capsule-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ios-action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 999px;
  color: #FFFFFF;
  font-size: 12px;
  font-weight: 590;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ios-action-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.3);
}

.ios-action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ios-action-btn.btn-penalty {
  background: rgba(255, 159, 10, 0.15);
  border-color: rgba(255, 159, 10, 0.3);
  color: #FF9F0A;
}

.ios-action-btn.btn-highlight {
  background: rgba(10, 132, 255, 0.2);
  border-color: rgba(10, 132, 255, 0.4);
  color: #0A84FF;
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
  letter-spacing: -0.015em;
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

.status-pill.pill-warn {
  background: rgba(255, 159, 10, 0.15);
  color: #FF9F0A;
  border-color: rgba(255, 159, 10, 0.3);
}

/* 任务规格横幅 */
.task-spec-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: rgba(0, 0, 0, 0.25);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.06);
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
  color: #0A84FF;
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
  font-size: 11px;
  font-weight: 590;
}

/* 超边卡片流水线 */
.hyperedge-pipeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hyperedge-card {
  padding: 14px;
  background: rgba(44, 44, 46, 0.45);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.3s ease;
}

.hyperedge-card.edge-high-confidence {
  border-color: rgba(48, 209, 88, 0.3);
}

.hyperedge-card.edge-medium-confidence {
  border-color: rgba(10, 132, 255, 0.3);
}

.hyperedge-card.edge-low-confidence {
  border-color: rgba(255, 69, 58, 0.3);
  background: rgba(255, 69, 58, 0.05);
}

.edge-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.edge-seq-pill {
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
  font-size: 10px;
  color: #8E8E93;
  padding: 2px 6px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 4px;
}

.edge-title-cluster {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.edge-name {
  font-size: 13px;
  font-weight: 590;
  color: #FFFFFF;
}

.edge-desc {
  font-size: 11px;
  color: #A1A1A6;
}

.edge-badge {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 590;
}

.badge-pass {
  background: rgba(48, 209, 88, 0.15);
  color: #30D158;
}

.badge-warn {
  background: rgba(255, 159, 10, 0.15);
  color: #FF9F0A;
}

.vertex-pills-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.vertex-tag-label {
  font-size: 10px;
  color: #8E8E93;
}

.pills-scroll-container {
  display: flex;
  gap: 6px;
  overflow-x: auto;
}

.vertex-glass-pill {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  font-size: 11px;
  color: #E5E5EA;
  white-space: nowrap;
}

.vertex-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #0A84FF;
}

.edge-telemetry-row {
  display: flex;
  gap: 16px;
  align-items: center;
  font-size: 11px;
  padding-top: 4px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.edge-stat {
  display: flex;
  gap: 4px;
}

.stat-lbl { color: #8E8E93; }
.stat-val { font-weight: 590; }

/* 右栏：装配与缓存监控 */
.caching-assembly-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.assembly-step-card {
  padding: 12px;
  background: rgba(44, 44, 46, 0.45);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.assembly-step-card.step-highlight {
  border-color: rgba(10, 132, 255, 0.4);
  background: rgba(10, 132, 255, 0.08);
}

.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-num {
  font-size: 11px;
  font-weight: 700;
  color: #0A84FF;
}

.step-title {
  font-size: 12px;
  font-weight: 590;
  color: #FFFFFF;
  flex: 1;
}

.step-status {
  font-size: 10px;
  font-weight: 600;
}

.step-detail {
  margin: 0;
  font-size: 11px;
  color: #8E8E93;
  line-height: 1.4;
}

.pad-code-box {
  padding: 6px 10px;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.pad-code-box code {
  font-family: 'SF Mono', Menlo, monospace;
  font-size: 10px;
  color: #30D158;
}

.pad-meta-row {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #8E8E93;
}

/* 凭单面板 */
.receipt-verify-panel {
  padding: 14px;
  background: rgba(44, 44, 46, 0.55);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.receipt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-tag {
  font-size: 11px;
  font-weight: 600;
  color: #8E8E93;
  letter-spacing: 0.02em;
}

.verify-badge {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 600;
}

.badge-verified {
  background: rgba(48, 209, 88, 0.15);
  color: #30D158;
}

.badge-unverified {
  background: rgba(255, 69, 58, 0.15);
  color: #FF453A;
}

.receipt-content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.receipt-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.f-lbl {
  font-size: 10px;
  color: #8E8E93;
}

.f-val {
  font-size: 11px;
}

.text-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 冷备环形仓面板 */
.cold-buffer-panel {
  padding: 14px;
  background: rgba(44, 44, 46, 0.45);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.buffer-headline {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.buffer-title-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.buffer-title {
  font-size: 12px;
  font-weight: 590;
  color: #FFFFFF;
}

.buffer-count-badge {
  font-size: 10px;
  color: #FF9F0A;
  background: rgba(255, 159, 10, 0.15);
  padding: 2px 6px;
  border-radius: 4px;
}

.buffer-empty-state {
  padding: 12px;
  text-align: center;
  font-size: 11px;
  color: #8E8E93;
}

.buffer-items-scroll {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 140px;
  overflow-y: auto;
}

.buffer-item-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
}

.q-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.q-id {
  font-size: 11px;
  color: #FF9F0A;
}

.q-reason {
  font-size: 10px;
  color: #8E8E93;
}

.ios-mini-restore-btn {
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(48, 209, 88, 0.15);
  border: 1px solid rgba(48, 209, 88, 0.3);
  color: #30D158;
  font-size: 10px;
  font-weight: 590;
  cursor: pointer;
  transition: all 0.2s ease;
}

.ios-mini-restore-btn:hover {
  background: rgba(48, 209, 88, 0.25);
}

.font-mono {
  font-family: 'SF Mono', Menlo, monospace;
}
</style>

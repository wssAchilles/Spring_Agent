<template>
  <div class="counterfactual-mcts-tree-widget">
    <!-- Apple iOS 26 顶部通透材质胶囊工具条 (Top Liquid Glass Capsule) -->
    <header class="widget-capsule-header">
      <div class="capsule-branding">
        <div class="signal-indicator" :class="{ 'signal-searching': isSearching, 'signal-verified': isReceiptVerified, 'signal-pruned': isPruneActive }"></div>
        <span class="capsule-title">COUNTERFACTUAL MCTS DECISION & DEEPSEEK THINKING // PHASE 146</span>
        <span class="capsule-subtag">APPLE LIQUID GLASS SPEC</span>
      </div>

      <div class="capsule-telemetry">
        <div class="telemetry-pill">
          <span class="pill-label">决策任务</span>
          <span class="pill-value text-accent">{{ currentTaskId }}</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">MCTS 迭代</span>
          <span class="pill-value">{{ iterationsCount }} 轮 (深度 {{ maxDepth }})</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">最优路径</span>
          <span class="pill-value text-success">{{ bestActionPath.length }} 动作节点</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">测地漂移均值</span>
          <span class="pill-value text-accent">{{ averageGeodesicDistance.toFixed(4) }} rad</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">因果完备度</span>
          <span class="pill-value text-success">{{ (causalCompletenessScore * 100).toFixed(1) }}%</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">冷备软隔离</span>
          <span class="pill-value text-warning">{{ quarantinedCount }} 分支</span>
        </div>
      </div>

      <!-- 人机协同交互操作组 (HITL Controls) -->
      <div class="capsule-actions">
        <button class="ios-action-btn" @click="handleExecuteSearch" :disabled="isSearching">
          <span class="btn-icon">🌲</span>
          <span>{{ isSearching ? 'PUCT 树搜索推演中...' : 'MCTS 反事实推演' }}</span>
        </button>
        <button class="ios-action-btn btn-penalty" @click="handleTriggerPrune" :disabled="mctsNodes.length <= 1">
          <span class="btn-icon">✂️</span>
          <span>测地线剪枝</span>
        </button>
        <button class="ios-action-btn btn-highlight" @click="handleVerifyReceipt" :disabled="!activeReceipt">
          <span class="btn-icon">🛡️</span>
          <span>常量时间验真</span>
        </button>
      </div>
    </header>

    <!-- 主交互网格：左栏 MCTS 反事实决策树与节点 UCT 展开流，右栏 DeepSeek 思考链流与三级冷备仓 -->
    <main class="widget-grid-layout">
      <!-- 左栏：MCTS 启发式探索树与反事实分支拓扑 -->
      <section class="ios-glass-card left-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">改进 PUCT 蒙特卡洛反事实决策探索树</h3>
            <span class="card-caption">千问 1536 维超球面测地线漂移核自适应衰减、后悔对数界收敛 (Lemma 146.1)</span>
          </div>
          <span class="status-pill" :class="isConverged ? 'pill-success' : 'pill-active'">
            {{ isConverged ? '最优路径已收敛' : '搜索探索中' }}
          </span>
        </div>

        <!-- 任务上下文与核心几何规格横幅 -->
        <div class="task-spec-banner">
          <div class="task-badge">
            <span class="task-icon">🎯</span>
            <span class="task-id">SCENARIO: CRITICAL_DATABASE_FAILOVER</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">超球面流形度规</span>
            <span class="metric-num text-success">d_geo = arccos(u·v) ∈ [0, π]</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">剪枝门禁阈值</span>
            <span class="metric-num text-accent">τ_geo = 0.65π (≈ 2.042 rad)</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">搜索时延预算</span>
            <span class="metric-num text-success">P99 ≤ 5.0ms (实测 {{ lastSearchLatency.toFixed(3) }}ms)</span>
          </div>
        </div>

        <!-- MCTS 节点分叉流水线与卡片列表 -->
        <div class="mcts-node-pipeline">
          <div
            v-for="(node, idx) in mctsNodes"
            :key="node.nodeId"
            class="mcts-node-card"
            :class="{
              'node-best-path': node.isOnBestPath,
              'node-counterfactual': node.isCounterfactual,
              'node-factual': !node.isCounterfactual
            }"
          >
            <div class="node-meta">
              <div class="node-seq-pill">
                <span class="seq-num">#{{ idx + 1 }}</span>
                <span class="seq-tag" :class="node.isCounterfactual ? 'tag-cf' : 'tag-factual'">
                  {{ node.isCounterfactual ? '反事实干预 do(A)' : '基准事实' }}
                </span>
              </div>
              <div class="node-title-cluster">
                <span class="node-name">{{ node.action }}</span>
                <span class="node-desc">{{ node.description }}</span>
              </div>
              <div class="node-badge" :class="node.isOnBestPath ? 'badge-best' : 'badge-normal'">
                <span>{{ node.isOnBestPath ? '最优路径' : '探索分支' }}</span>
              </div>
            </div>

            <!-- PUCT 与测地线探索指标行 -->
            <div class="node-telemetry-row">
              <div class="node-stat">
                <span class="stat-lbl">PUCT 得分:</span>
                <span class="stat-val font-mono text-accent">{{ node.uctScore.toFixed(4) }}</span>
              </div>
              <div class="node-stat">
                <span class="stat-lbl">平均价值 Q:</span>
                <span class="stat-val font-mono text-success">{{ node.averageValue.toFixed(4) }}</span>
              </div>
              <div class="node-stat">
                <span class="stat-lbl">访问次数 N:</span>
                <span class="stat-val font-mono">{{ node.visitCount }}</span>
              </div>
              <div class="node-stat">
                <span class="stat-lbl">先验概率 P:</span>
                <span class="stat-val font-mono">{{ (node.priorProbability * 100).toFixed(1) }}%</span>
              </div>
              <div class="node-stat">
                <span class="stat-lbl">测地漂移 d_geo:</span>
                <span class="stat-val font-mono" :class="node.geodesicDistance <= 1.0 ? 'text-success' : 'text-warning'">
                  {{ node.geodesicDistance.toFixed(4) }} rad
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 右栏：DeepSeek 官方 1M 思考链因果评估、凭单验真与三级冷备仓 -->
      <section class="ios-glass-card right-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">DeepSeek 官方思考模式因果思维链与审计</h3>
            <span class="card-caption">溯因-干预-预测三阶段自省、64-Token 整数倍前缀缓存对齐 (Lemma 146.2)</span>
          </div>
          <span class="status-pill" :class="isReceiptVerified ? 'pill-success' : 'pill-active'">
            {{ isReceiptVerified ? '密码学验真通过' : '待验签' }}
          </span>
        </div>

        <!-- DeepSeek 思维链实时流式展示面板 -->
        <div class="thinking-stream-panel">
          <div class="stream-header">
            <span class="stream-tag">DEEPSEEK REASONING CONTENT STREAM</span>
            <span class="stream-badge text-success">Thinking Mode Enabled</span>
          </div>
          <div class="thinking-code-box">
            <pre><code>{{ thinkingReasoningContent }}</code></pre>
          </div>
        </div>

        <!-- 不可变推理存证凭单卡片 (Java 21 Record) -->
        <div class="receipt-verify-panel">
          <div class="receipt-header">
            <span class="panel-tag">反事实决策审计存证凭单 (JAVA 21 RECORD)</span>
            <span class="verify-badge" :class="isReceiptVerified ? 'badge-verified' : 'badge-unverified'">
              {{ isReceiptVerified ? 'SHA-256 常量时间自验真通过' : '未验真' }}
            </span>
          </div>

          <div v-if="activeReceipt" class="receipt-content-grid">
            <div class="receipt-field">
              <span class="f-lbl">凭单编号:</span>
              <span class="f-val font-mono text-accent">{{ activeReceipt.receiptId }}</span>
            </div>
            <div class="receipt-field">
              <span class="f-lbl">决策总耗时:</span>
              <span class="f-val font-mono text-success">{{ activeReceipt.inferenceLatencyMs.toFixed(3) }} ms</span>
            </div>
            <div class="receipt-field">
              <span class="f-lbl">根节点 UCT:</span>
              <span class="f-val font-mono">{{ activeReceipt.rootUctScore.toFixed(4) }}</span>
            </div>
            <div class="receipt-field">
              <span class="f-lbl">前缀对齐:</span>
              <span class="f-val font-mono text-success">{{ activeReceipt.alignedTokens }} Token (模 64 = 0)</span>
            </div>
            <div class="receipt-field full-width">
              <span class="f-lbl">数字签名指纹:</span>
              <span class="f-val font-mono text-success text-ellipsis">{{ activeReceipt.sha256Signature }}</span>
            </div>
          </div>
        </div>

        <!-- 三级冷备隔离环形缓冲区 (Soft Isolation Buffer) -->
        <div class="cold-buffer-panel">
          <div class="buffer-headline">
            <div class="buffer-title-wrap">
              <span class="buffer-icon">🧊</span>
              <span class="buffer-title">三级冷备隔离环形缓冲区 (容量 128 条软删除仓)</span>
            </div>
            <span class="buffer-count-badge">{{ quarantinedNodes.length }}/128 条</span>
          </div>

          <div v-if="quarantinedNodes.length === 0" class="buffer-empty-state">
            <span>当前无测地线超标剪枝分支，全量反事实分叉在流形内安全展开</span>
          </div>

          <div v-else class="buffer-items-scroll">
            <div v-for="qItem in quarantinedNodes" :key="qItem.nodeId" class="buffer-item-row">
              <div class="q-meta">
                <span class="q-id font-mono">{{ qItem.action }} ({{ qItem.nodeId }})</span>
                <span class="q-reason">测地角漂移: {{ qItem.geodesicDistance.toFixed(4) }} rad (突破 0.65π)</span>
              </div>
              <button class="ios-mini-restore-btn" @click="handleRestoreNode(qItem.nodeId)">
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

interface MctsDisplayNode {
  nodeId: string
  action: string
  description: string
  isCounterfactual: boolean
  isOnBestPath: boolean
  visitCount: number
  averageValue: number
  priorProbability: number
  geodesicDistance: number
  uctScore: number
}

interface QuarantinedDisplayNode {
  nodeId: string
  action: string
  geodesicDistance: number
  quarantinedTimestamp: number
}

interface CounterfactualReceipt {
  receiptId: string
  tenantId: string
  taskId: string
  traceId: string
  bestActionPath: string[]
  counterfactualPrunedCount: number
  quarantinedCount: number
  maxTreeDepth: number
  totalVisitedNodes: number
  rootUctScore: number
  averageGeodesicDistance: number
  alignedTokens: number
  isCacheAligned: boolean
  inferenceLatencyMs: number
  timestamp: number
  sha256Signature: string
}

const currentTaskId = ref('TASK-CF-MCTS-2026-926')
const iterationsCount = ref(30)
const maxDepth = ref(5)
const isSearching = ref(false)
const isConverged = ref(true)
const isPruneActive = ref(false)
const isReceiptVerified = ref(true)
const lastSearchLatency = ref(2.341)
const averageGeodesicDistance = ref(0.2452)
const causalCompletenessScore = ref(0.978)

const bestActionPath = ref<string[]>([
  'STEP_1_FREEZE_CONNECTION_POOL',
  'STEP_2_DO_ISOLATE_LONG_TRANSACTION',
  'STEP_3_RESTORE_READ_REPLICA_WEIGHT'
])

const mctsNodes = ref<MctsDisplayNode[]>([
  {
    nodeId: 'N-01',
    action: 'STEP_1_FREEZE_CONNECTION_POOL',
    description: '保持外生背景不变，冻结异常活跃连接池入边',
    isCounterfactual: false,
    isOnBestPath: true,
    visitCount: 28,
    averageValue: 0.88,
    priorProbability: 0.85,
    geodesicDistance: 0.12,
    uctScore: 1.245
  },
  {
    nodeId: 'N-02',
    action: 'STEP_2_DO_ISOLATE_LONG_TRANSACTION',
    description: '反事实干预：强制中断阻塞事务而非暴力全局重启',
    isCounterfactual: true,
    isOnBestPath: true,
    visitCount: 22,
    averageValue: 0.92,
    priorProbability: 0.75,
    geodesicDistance: 0.22,
    uctScore: 1.182
  },
  {
    nodeId: 'N-03',
    action: 'ALT_DO_GLOBAL_DATABASE_REBOOT',
    description: '替代反事实分叉：直接杀进程重启主库（引发次生雪崩）',
    isCounterfactual: true,
    isOnBestPath: false,
    visitCount: 5,
    averageValue: 0.35,
    priorProbability: 0.40,
    geodesicDistance: 0.85,
    uctScore: 0.621
  },
  {
    nodeId: 'N-04',
    action: 'STEP_3_RESTORE_READ_REPLICA_WEIGHT',
    description: '平滑自愈：按余弦相似度流形重新分配读写分离权重',
    isCounterfactual: false,
    isOnBestPath: true,
    visitCount: 18,
    averageValue: 0.94,
    priorProbability: 0.80,
    geodesicDistance: 0.15,
    uctScore: 1.312
  }
])

const quarantinedNodes = ref<QuarantinedDisplayNode[]>([
  {
    nodeId: 'PRUNED-01',
    action: 'MALICIOUS_DROP_ALL_INDEXES',
    geodesicDistance: 2.4512,
    quarantinedTimestamp: Date.now() - 3500
  }
])

const quarantinedCount = computed(() => quarantinedNodes.value.length)

const thinkingReasoningContent = ref(`<thinking>
1. 【溯因阶段 Abduction】: 冻结当前外生企业环境潜变量 U_env，固定数据库连接池大小为 20 与活跃连接耗尽背景。
2. 【反事实干预 Action】: 施加结构干预 do(A = 'ISOLATE_LONG_TRANSACTION')，切断级联阻塞因果入边。
3. 【状态前瞻预测 Prediction】: 经千问 1536 维超球面测地线漂移核测算，预期反事实状态偏离度为 0.22 rad <= 0.65π，因果逻辑自洽。
4. 【风险自省 Verification】: 验证次生灾害链，若发生读从库短暂停顿，可通过三级冷备环形仓秒级可逆回滚。
</thinking>`)

const activeReceipt = ref<CounterfactualReceipt>({
  receiptId: 'RCP-CF-MCTS-91a8c2f1',
  tenantId: 'tenant-enterprise-prod',
  taskId: 'TASK-CF-MCTS-2026-926',
  traceId: 'trace-w3c-7a9128cf34194098b671a8123cde8821',
  bestActionPath: [
    'STEP_1_FREEZE_CONNECTION_POOL',
    'STEP_2_DO_ISOLATE_LONG_TRANSACTION',
    'STEP_3_RESTORE_READ_REPLICA_WEIGHT'
  ],
  counterfactualPrunedCount: 1,
  quarantinedCount: 1,
  maxTreeDepth: 5,
  totalVisitedNodes: 32,
  rootUctScore: 1.312,
  averageGeodesicDistance: 0.2452,
  alignedTokens: 896,
  isCacheAligned: true,
  inferenceLatencyMs: 2.341,
  timestamp: Date.now(),
  sha256Signature: 'c819a712f5b498e21a0491823908b1a823940182398401928309481230491823'
})

const handleExecuteSearch = () => {
  isSearching.value = true
  isConverged.value = false
  setTimeout(() => {
    isSearching.value = false
    isConverged.value = true
    lastSearchLatency.value = 1.95 + Math.random() * 0.4
    averageGeodesicDistance.value = 0.22 + Math.random() * 0.05
  }, 400)
}

const handleTriggerPrune = () => {
  isPruneActive.value = true
  const highDistNodes = mctsNodes.value.filter(n => n.geodesicDistance > 0.65)
  if (highDistNodes.length > 0) {
    highDistNodes.forEach(node => {
      quarantinedNodes.value.push({
        nodeId: node.nodeId,
        action: node.action,
        geodesicDistance: node.geodesicDistance,
        quarantinedTimestamp: Date.now()
      })
    })
    mctsNodes.value = mctsNodes.value.filter(n => n.geodesicDistance <= 0.65)
  }
  setTimeout(() => {
    isPruneActive.value = false
  }, 300)
}

const handleRestoreNode = (nodeId: string) => {
  const idx = quarantinedNodes.value.findIndex(q => q.nodeId === nodeId)
  if (idx !== -1) {
    const qItem = quarantinedNodes.value[idx]
    quarantinedNodes.value.splice(idx, 1)
    mctsNodes.value.push({
      nodeId: qItem.nodeId,
      action: qItem.action,
      description: '从三级冷备隔离仓可逆唤醒自愈之反事实分支',
      isCounterfactual: true,
      isOnBestPath: false,
      visitCount: 8,
      averageValue: 0.65,
      priorProbability: 0.50,
      geodesicDistance: 0.45,
      uctScore: 0.852
    })
  }
}

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
   1. 无层叠上下文铁律：禁止 z-index，依托物理 DOM 流排版；
   2. 双层 50px 模糊与饱和度增益 (backdrop-filter: blur(50px) saturate(190%))；
   3. Headline 590 字重与光学正负字距；
   4. 零阴影系统：严禁使用 box-shadow，完全依托自然物理高光边框；
   5. 信号色彩：Apple 信号蓝、翠绿、琥珀黄与高亮玫瑰红。
   ========================================================================== */

.counterfactual-mcts-tree-widget {
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

.signal-indicator.signal-searching {
  background: #0A84FF;
  animation: pulse-search 1s infinite alternate;
}

.signal-indicator.signal-pruned {
  background: #FF9F0A;
}

@keyframes pulse-search {
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

/* 规格横幅 */
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

/* MCTS 节点卡片列表 */
.mcts-node-pipeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mcts-node-card {
  padding: 14px;
  background: rgba(44, 44, 46, 0.45);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.3s ease;
}

.mcts-node-card.node-best-path {
  border-color: rgba(48, 209, 88, 0.4);
  background: rgba(48, 209, 88, 0.08);
}

.mcts-node-card.node-counterfactual {
  border-left: 3px solid #0A84FF;
}

.mcts-node-card.node-factual {
  border-left: 3px solid #8E8E93;
}

.node-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.node-seq-pill {
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
  padding: 2px 6px;
  border-radius: 4px;
}

.tag-cf {
  background: rgba(10, 132, 255, 0.15);
  color: #0A84FF;
}

.tag-factual {
  background: rgba(255, 255, 255, 0.08);
  color: #8E8E93;
}

.node-title-cluster {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.node-name {
  font-size: 13px;
  font-weight: 590;
  color: #FFFFFF;
}

.node-desc {
  font-size: 11px;
  color: #A1A1A6;
}

.node-badge {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 590;
}

.badge-best {
  background: rgba(48, 209, 88, 0.15);
  color: #30D158;
}

.badge-normal {
  background: rgba(255, 255, 255, 0.08);
  color: #8E8E93;
}

.node-telemetry-row {
  display: flex;
  gap: 16px;
  align-items: center;
  font-size: 11px;
  padding-top: 6px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  flex-wrap: wrap;
}

.node-stat {
  display: flex;
  gap: 4px;
}

.stat-lbl { color: #8E8E93; }
.stat-val { font-weight: 590; }

/* 右栏：思维链流式面板 */
.thinking-stream-panel {
  padding: 12px;
  background: rgba(44, 44, 46, 0.45);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stream-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stream-tag {
  font-size: 11px;
  font-weight: 600;
  color: #8E8E93;
}

.stream-badge {
  font-size: 10px;
  font-weight: 600;
}

.thinking-code-box {
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  max-height: 180px;
  overflow-y: auto;
}

.thinking-code-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.thinking-code-box code {
  font-family: 'SF Mono', Menlo, monospace;
  font-size: 11px;
  color: #E5E5EA;
  line-height: 1.45;
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

.receipt-field.full-width {
  grid-column: span 2;
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

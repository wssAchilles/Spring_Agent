<template>
  <div class="agent-coalition-matrix-widget">
    <!-- Apple iOS 26 顶部通透材质胶囊工具条 (Top Liquid Glass Capsule) -->
    <header class="widget-capsule-header">
      <div class="capsule-branding">
        <div class="signal-indicator" :class="{ 'signal-forming': isForming, 'signal-verified': isReceiptVerified }"></div>
        <span class="capsule-title">AGENT COALITION & SHAPLEY GAME MATRIX // PHASE 143</span>
        <span class="capsule-subtag">APPLE LIQUID GLASS SPEC</span>
      </div>

      <div class="capsule-telemetry">
        <div class="telemetry-pill">
          <span class="pill-label">候选智能体</span>
          <span class="pill-value text-accent">{{ candidatePool.length }} 席</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">核稳定联盟</span>
          <span class="pill-value">{{ selectedMembers.length }} 成员</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">特征价值 v(S)</span>
          <span class="pill-value text-success">{{ coalitionValue.toFixed(4) }}</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">协同增益比</span>
          <span class="pill-value text-success">{{ (synergyRatio * 100).toFixed(1) }}%</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">冷备隔离</span>
          <span class="pill-value text-warning">{{ quarantinedAgents.length }} 节点</span>
        </div>
      </div>

      <!-- 人机协同交互操作组 (HITL Controls) -->
      <div class="capsule-actions">
        <button class="ios-action-btn" @click="handleFormOptimalCoalition" :disabled="isForming">
          <span class="btn-icon">⚡</span>
          <span>{{ isForming ? '次模博弈求解中...' : '次模贪心重构联盟' }}</span>
        </button>
        <button class="ios-action-btn btn-penalty" @click="handleSimulateViolation">
          <span class="btn-icon">⚠️</span>
          <span>注入违约惩罚</span>
        </button>
        <button class="ios-action-btn btn-highlight" @click="verifyReceipt" :disabled="!activeReceipt">
          <span class="btn-icon">🛡️</span>
          <span>常量时间自验真</span>
        </button>
      </div>
    </header>

    <!-- 主交互网格：左栏博弈协同与夏普利分配矩阵，右栏信誉雷达动力学与冷备隔离池 -->
    <main class="widget-grid-layout">
      <!-- 左栏：智能体博弈协同与夏普利收益分配矩阵看板 -->
      <section class="ios-glass-card left-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">千问超球面正交投影与夏普利公平分配矩阵</h3>
            <span class="card-caption">次模特征函数单调性、边际递减率与零搭便车公理 (Lemma 143.1)</span>
          </div>
          <span class="status-pill pill-active">
            分配残差: {{ shapleyResidual.toExponential(2) }}
          </span>
        </div>

        <!-- 任务语义目标与超球面正交子空间指标 -->
        <div class="task-spec-banner">
          <div class="task-badge">
            <span class="task-icon">🎯</span>
            <span class="task-id">{{ currentTaskId }}</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">超球面维度</span>
            <span class="metric-num">1536 维单位超球流形 (||v||₂=1.0)</span>
          </div>
          <div class="task-metric">
            <span class="metric-name">通信阻尼罚项</span>
            <span class="metric-num">λ = 0.02 · |S|</span>
          </div>
        </div>

        <!-- 夏普利分配横条矩阵列表 -->
        <div class="coalition-member-list">
          <div
            v-for="(member, idx) in selectedMembers"
            :key="member.agentId"
            class="member-row-card"
          >
            <div class="member-meta">
              <span class="member-seq">#0{{ idx + 1 }}</span>
              <div class="member-identity">
                <span class="member-name">{{ member.agentRole }}</span>
                <span class="member-id">{{ member.agentId }}</span>
              </div>
            </div>

            <!-- 超球面投影与任务余弦几何指标 -->
            <div class="member-geometry-stats">
              <div class="stat-cell">
                <span class="cell-label">任务余弦</span>
                <span class="cell-val">{{ member.taskCosine.toFixed(3) }}</span>
              </div>
              <div class="stat-cell">
                <span class="cell-label">正交增量</span>
                <span class="cell-val text-accent">{{ member.orthoNorm.toFixed(3) }}</span>
              </div>
              <div class="stat-cell">
                <span class="cell-label">有效信誉</span>
                <span class="cell-val">{{ member.reputation.toFixed(3) }}</span>
              </div>
            </div>

            <!-- 夏普利分配柱状条 -->
            <div class="shapley-bar-container">
              <div class="shapley-bar-track">
                <div
                  class="shapley-bar-fill"
                  :style="{ width: (member.shapleyRatio * 100) + '%' }"
                ></div>
              </div>
              <div class="shapley-val-wrap">
                <span class="shapley-num">{{ member.shapleyPayoff.toFixed(4) }}</span>
                <span class="shapley-pct">{{ (member.shapleyRatio * 100).toFixed(1) }}%</span>
              </div>
            </div>

            <div class="member-tag-slot">
              <span v-if="member.isDummy" class="badge-dummy">虚拟玩家 (0收益)</span>
              <span v-else class="badge-core">核稳定贡献者</span>
            </div>
          </div>
        </div>

        <div class="formula-caption-bar">
          <code>v(S) = ||P_Span(S) q_task||₂ - λ|S| ⊨ Submodular & Core-Stable (Lemma 143.1)</code>
        </div>
      </section>

      <!-- 右栏：对数阻尼信誉雷达与三级冷备自愈隔离区 + 凭单卡片 -->
      <section class="ios-glass-card right-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">对数阻尼信誉动力学与三级冷备隔离池</h3>
            <span class="card-caption">对数饱和防女巫刷单、非对称重惩与零硬删可逆自愈 (Lemma 143.2)</span>
          </div>
          <span class="status-pill pill-neutral">
            全部节点: {{ candidatePool.length }} 席
          </span>
        </div>

        <!-- 候选节点信誉分布态势 -->
        <div class="reputation-grid-list">
          <div
            v-for="agent in candidatePool"
            :key="agent.agentId"
            class="agent-rep-row"
            :class="{
              'row-normal': agent.quarantineLevel === 'LEVEL_0_NORMAL',
              'row-watch': agent.quarantineLevel === 'LEVEL_1_WATCH',
              'row-quarantined': agent.quarantineLevel === 'LEVEL_2_QUARANTINED'
            }"
          >
            <div class="agent-main-info">
              <span class="rep-dot" :class="agent.quarantineLevel.toLowerCase()"></span>
              <span class="agent-role-text">{{ agent.agentRole }}</span>
              <span v-if="agent.isPinned" class="pinned-tag" title="永久免死钉扎">📌 免死</span>
            </div>

            <div class="rep-bar-zone">
              <div class="rep-bar-bg">
                <div
                  class="rep-bar-active"
                  :style="{ width: (agent.currentReputation * 100) + '%' }"
                ></div>
              </div>
              <span class="rep-digit">{{ agent.currentReputation.toFixed(3) }}</span>
            </div>

            <!-- HITL 人工干预自愈通道 -->
            <div class="agent-hitl-actions">
              <button
                v-if="agent.quarantineLevel === 'LEVEL_2_QUARANTINED'"
                class="hitl-btn btn-heal"
                @click="handleSelfHeal(agent.agentId)"
                title="沙盒测试自愈复活出池"
              >
                自愈出池
              </button>
              <button
                class="hitl-btn btn-pin"
                @click="togglePin(agent.agentId)"
                :title="agent.isPinned ? '取消特权钉扎' : '设为免死钉扎'"
              >
                {{ agent.isPinned ? '解钉' : '钉扎' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 密码学存证凭单卡片 (Receipt Card) -->
        <div class="receipt-glass-card" v-if="activeReceipt">
          <div class="receipt-header">
            <div class="receipt-id-tag">
              <span class="shield-icon">🛡️</span>
              <span class="rid">{{ activeReceipt.receiptId }}</span>
            </div>
            <span
              class="verification-badge"
              :class="{ 'badge-valid': isReceiptVerified, 'badge-unverified': !isReceiptVerified }"
            >
              {{ isReceiptVerified ? '✓ SHA-256 常量时间验真通过' : '待验真' }}
            </span>
          </div>

          <div class="receipt-field-grid">
            <div class="field-item">
              <span class="fl">TraceId</span>
              <span class="fv code-font">{{ activeReceipt.traceId }}</span>
            </div>
            <div class="field-item">
              <span class="fl">计算耗时</span>
              <span class="fv">{{ activeReceipt.latencyMs.toFixed(2) }} ms</span>
            </div>
            <div class="field-item">
              <span class="fl">联盟成员</span>
              <span class="fv">{{ activeReceipt.memberAgentIds.length }} 智能体</span>
            </div>
            <div class="field-item">
              <span class="fl">冷备包含</span>
              <span class="fv">{{ activeReceipt.isQuarantined ? '包含观察成员' : '纯净活跃池' }}</span>
            </div>
          </div>

          <div class="signature-box">
            <span class="sig-label">SHA-256 DIGEST (ZERO-TIMING LEAKAGE):</span>
            <span class="sig-hex">{{ activeReceipt.sha256Signature }}</span>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

interface MemberVisual {
  agentId: string
  agentRole: string
  taskCosine: number
  orthoNorm: number
  reputation: number
  shapleyPayoff: number
  shapleyRatio: number
  isDummy: boolean
}

interface AgentCandidate {
  agentId: string
  tenantId: string
  agentRole: string
  currentReputation: number
  quarantineLevel: 'LEVEL_0_NORMAL' | 'LEVEL_1_WATCH' | 'LEVEL_2_QUARANTINED'
  isPinned: boolean
}

interface ReceiptModel {
  receiptId: string
  tenantId: string
  taskId: string
  traceId: string
  coalitionId: string
  memberAgentIds: string[]
  shapleyPayoffs: Record<string, number>
  reputationScores: Record<string, number>
  coalitionValue: number
  synergyRatio: number
  isQuarantined: boolean
  latencyMs: number
  timestamp: number
  sha256Signature: string
}

// 模拟初始任务与候选人状态
const currentTaskId = ref('TASK-PHASE143-CROSS-ORG-REASONING-001')
const isForming = ref(false)
const isReceiptVerified = ref(false)
const coalitionValue = ref(0.8745)
const synergyRatio = ref(1.428)
const shapleyResidual = ref(1.2e-7)

// 候选智能体池
const candidatePool = ref<AgentCandidate[]>([
  { agentId: 'agent-math-deduction-01', tenantId: 'tenant-enterprise', agentRole: '形式化推导专家', currentReputation: 0.945, quarantineLevel: 'LEVEL_0_NORMAL', isPinned: true },
  { agentId: 'agent-code-forensic-02', tenantId: 'tenant-enterprise', agentRole: '代码法医审计专家', currentReputation: 0.892, quarantineLevel: 'LEVEL_0_NORMAL', isPinned: false },
  { agentId: 'agent-sys-architect-03', tenantId: 'tenant-enterprise', agentRole: '系统架构契约专家', currentReputation: 0.915, quarantineLevel: 'LEVEL_0_NORMAL', isPinned: false },
  { agentId: 'agent-rag-retriever-04', tenantId: 'tenant-enterprise', agentRole: '千问向量检索引擎', currentReputation: 0.830, quarantineLevel: 'LEVEL_0_NORMAL', isPinned: false },
  { agentId: 'agent-db-sandbox-05', tenantId: 'tenant-enterprise', agentRole: '只读数据库沙箱', currentReputation: 0.760, quarantineLevel: 'LEVEL_0_NORMAL', isPinned: false },
  { agentId: 'agent-chatty-repeater-06', tenantId: 'tenant-enterprise', agentRole: '套话复读搭便车者', currentReputation: 0.285, quarantineLevel: 'LEVEL_2_QUARANTINED', isPinned: false },
  { agentId: 'agent-flaky-worker-07', tenantId: 'tenant-enterprise', agentRole: '偶发超时降级节点', currentReputation: 0.420, quarantineLevel: 'LEVEL_1_WATCH', isPinned: false }
])

// 入选核稳定联盟成员
const selectedMembers = ref<MemberVisual[]>([
  { agentId: 'agent-math-deduction-01', agentRole: '形式化推导专家', taskCosine: 0.885, orthoNorm: 1.000, reputation: 0.945, shapleyPayoff: 0.3621, shapleyRatio: 0.414, isDummy: false },
  { agentId: 'agent-code-forensic-02', agentRole: '代码法医审计专家', taskCosine: 0.742, orthoNorm: 0.628, reputation: 0.892, shapleyPayoff: 0.2815, shapleyRatio: 0.322, isDummy: false },
  { agentId: 'agent-sys-architect-03', agentRole: '系统架构契约专家', taskCosine: 0.680, orthoNorm: 0.495, reputation: 0.915, shapleyPayoff: 0.2309, shapleyRatio: 0.264, isDummy: false }
])

const quarantinedAgents = computed(() => {
  return candidatePool.value.filter(a => a.quarantineLevel === 'LEVEL_2_QUARANTINED')
})

// 初始活跃凭单
const activeReceipt = ref<ReceiptModel>({
  receiptId: 'RCP-COALITION-1727271890-001',
  tenantId: 'tenant-enterprise',
  taskId: 'TASK-PHASE143-CROSS-ORG-REASONING-001',
  traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
  coalitionId: 'COALITION-1727271890-9921',
  memberAgentIds: ['agent-math-deduction-01', 'agent-code-forensic-02', 'agent-sys-architect-03'],
  shapleyPayoffs: {
    'agent-math-deduction-01': 0.362100,
    'agent-code-forensic-02': 0.281500,
    'agent-sys-architect-03': 0.230900
  },
  reputationScores: {
    'agent-math-deduction-01': 0.948200,
    'agent-code-forensic-02': 0.895100,
    'agent-sys-architect-03': 0.918400
  },
  coalitionValue: 0.874500,
  synergyRatio: 1.428000,
  isQuarantined: false,
  latencyMs: 1.2840,
  timestamp: Date.now(),
  sha256Signature: '7d9fa821c435bb829aef104618d30e445100918ca90038cb51eef38c01d9f821'
})

// 模拟次模贪心重构联盟
const handleFormOptimalCoalition = () => {
  isForming.value = true
  isReceiptVerified.value = false
  setTimeout(() => {
    isForming.value = false
    coalitionValue.value = 0.8812
    synergyRatio.value = 1.442
    isReceiptVerified.value = true
  }, 350)
}

// 模拟违约重惩
const handleSimulateViolation = () => {
  const target = candidatePool.value.find(a => a.agentId === 'agent-flaky-worker-07')
  if (target) {
    target.currentReputation = Math.max(0.0, target.currentReputation - 0.25)
    if (target.currentReputation < 0.30) {
      target.quarantineLevel = 'LEVEL_2_QUARANTINED'
    } else if (target.currentReputation < 0.50) {
      target.quarantineLevel = 'LEVEL_1_WATCH'
    }
  }
}

// 沙盒自愈出池
const handleSelfHeal = (agentId: string) => {
  const target = candidatePool.value.find(a => a.agentId === agentId)
  if (target) {
    target.currentReputation = 0.550
    target.quarantineLevel = 'LEVEL_0_NORMAL'
  }
}

// 切换免死钉扎
const togglePin = (agentId: string) => {
  const target = candidatePool.value.find(a => a.agentId === agentId)
  if (target) {
    target.isPinned = !target.isPinned
    if (target.isPinned && target.currentReputation < 0.80) {
      target.currentReputation = 0.80
      target.quarantineLevel = 'LEVEL_0_NORMAL'
    }
  }
}

// 前端常量时间验真
const verifyReceipt = async () => {
  if (!activeReceipt.value) return
  // 模拟 WebCrypto 常量时间比对
  await new Promise(r => setTimeout(r, 120))
  isReceiptVerified.value = true
}
</script>

<style scoped lang="scss">
/* 严格贯彻 Apple iOS 26 Liquid Glass 顶级设计规范 (docs/design-system/00_MASTER_frontend_guide.md) */
/* 铁律一：无层叠上下文 (严禁 transform、will-change、opacity < 1、isolation、z-index) */
/* 铁律二：双层 50px 模糊配方 (严禁 saturate) */
/* 铁律三：Headline 独占 590 字重，其余统一 400，正负交替光学字距 */
/* 铁律四：零大投影系统，纯净 Apple 信号色 */

.agent-coalition-matrix-widget {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  box-sizing: border-box;
  font-family: system-ui, -apple-system, "SF Pro Text", "SF Pro Display", "PingFang SC", sans-serif;
  color: #1d1d1f;
}

/* 顶部胶囊工具条 */
.widget-capsule-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-radius: 42px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(50px);
  mix-blend-mode: normal;
  border: 1px solid rgba(0, 0, 0, 0.12);
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
  background: #8e8e93;
  transition: background-color 0.2s ease;

  &.signal-forming {
    background: #ff8d28;
  }
  &.signal-verified {
    background: #34c759;
  }
}

.capsule-title {
  font-size: 13px;
  font-weight: 590;
  letter-spacing: -0.08px;
  color: #000000;
}

.capsule-subtag {
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0.06px;
  padding: 2px 6px;
  border-radius: 10px;
  background: #ededed;
  color: #3d3d3d;
}

.capsule-telemetry {
  display: flex;
  align-items: center;
  gap: 14px;
}

.telemetry-pill {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;

  .pill-label {
    font-size: 10px;
    font-weight: 400;
    color: rgba(60, 60, 67, 0.6);
  }

  .pill-value {
    font-size: 12px;
    font-weight: 590;
    color: #1d1d1f;

    &.text-accent {
      color: #0088ff;
    }
    &.text-success {
      color: #34c759;
    }
    &.text-warning {
      color: #ff8d28;
    }
  }
}

.capsule-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ios-action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border-radius: 1000px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: rgba(255, 255, 255, 0.85);
  font-size: 12px;
  font-weight: 590;
  letter-spacing: 0;
  color: #1d1d1f;
  cursor: pointer;
  outline: none;
  transition: background-color 0.15s ease, border-color 0.15s ease;

  &:hover {
    background: #ffffff;
    border-color: rgba(0, 0, 0, 0.25);
  }

  &.btn-penalty:hover {
    border-color: #ff8d28;
  }

  &.btn-highlight {
    background: #0088ff;
    color: #ffffff;
    border-color: #0088ff;

    &:hover {
      background: #0077e6;
    }
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

/* 主网格排版 */
.widget-grid-layout {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 16px;
}

.ios-glass-card {
  padding: 20px;
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(50px);
  border: 1px solid rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-headline-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.title-cluster {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.card-headline {
  margin: 0;
  font-size: 17px;
  font-weight: 590;
  letter-spacing: -0.43px;
  color: #000000;
}

.card-caption {
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0;
  color: rgba(60, 60, 67, 0.6);
}

.status-pill {
  font-size: 11px;
  font-weight: 590;
  letter-spacing: 0.06px;
  padding: 4px 10px;
  border-radius: 12px;

  &.pill-active {
    background: rgba(0, 136, 255, 0.12);
    color: #0088ff;
  }
  &.pill-neutral {
    background: #ededed;
    color: #3d3d3d;
  }
}

/* 任务规格横幅 */
.task-spec-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.03);
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.task-badge {
  display: flex;
  align-items: center;
  gap: 6px;

  .task-id {
    font-size: 12px;
    font-weight: 590;
    color: #000000;
  }
}

.task-metric {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1px;

  .metric-name {
    font-size: 10px;
    color: rgba(60, 60, 67, 0.6);
  }
  .metric-num {
    font-size: 11px;
    font-weight: 590;
    color: #1d1d1f;
  }
}

/* 夏普利分配行列表 */
.coalition-member-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.member-row-card {
  display: grid;
  grid-template-columns: 140px 150px 1fr 100px;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.member-meta {
  display: flex;
  align-items: center;
  gap: 10px;

  .member-seq {
    font-size: 12px;
    font-weight: 590;
    color: rgba(60, 60, 67, 0.4);
  }
  .member-name {
    font-size: 13px;
    font-weight: 590;
    color: #000000;
    display: block;
  }
  .member-id {
    font-size: 10px;
    color: rgba(60, 60, 67, 0.5);
    display: block;
  }
}

.member-geometry-stats {
  display: flex;
  align-items: center;
  gap: 12px;

  .stat-cell {
    display: flex;
    flex-direction: column;
    gap: 1px;

    .cell-label {
      font-size: 9px;
      color: rgba(60, 60, 67, 0.6);
    }
    .cell-val {
      font-size: 11px;
      font-weight: 590;

      &.text-accent {
        color: #0088ff;
      }
    }
  }
}

.shapley-bar-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.shapley-bar-track {
  height: 6px;
  width: 100%;
  border-radius: 3px;
  background: #ededed;
  overflow: hidden;
}

.shapley-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #0088ff, #6155f5);
  transition: width 0.3s ease;
}

.shapley-val-wrap {
  display: flex;
  justify-content: space-between;
  font-size: 11px;

  .shapley-num {
    font-weight: 590;
    color: #0088ff;
  }
  .shapley-pct {
    color: rgba(60, 60, 67, 0.6);
  }
}

.badge-core {
  font-size: 10px;
  font-weight: 590;
  padding: 3px 8px;
  border-radius: 8px;
  background: rgba(52, 199, 89, 0.12);
  color: #34c759;
}

.badge-dummy {
  font-size: 10px;
  font-weight: 590;
  padding: 3px 8px;
  border-radius: 8px;
  background: rgba(255, 56, 60, 0.12);
  color: #ff383c;
}

/* 信誉雷达网格 */
.reputation-grid-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.agent-rep-row {
  display: grid;
  grid-template-columns: 140px 1fr 100px;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(0, 0, 0, 0.06);

  &.row-quarantined {
    background: rgba(255, 141, 40, 0.08);
    border-color: rgba(255, 141, 40, 0.3);
  }
}

.agent-main-info {
  display: flex;
  align-items: center;
  gap: 6px;

  .rep-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;

    &.level_0_normal {
      background: #34c759;
    }
    &.level_1_watch {
      background: #ffcc00;
    }
    &.level_2_quarantined {
      background: #ff8d28;
    }
  }

  .agent-role-text {
    font-size: 12px;
    font-weight: 590;
    color: #1d1d1f;
  }

  .pinned-tag {
    font-size: 10px;
    color: #0088ff;
  }
}

.rep-bar-zone {
  display: flex;
  align-items: center;
  gap: 8px;

  .rep-bar-bg {
    flex: 1;
    height: 5px;
    border-radius: 3px;
    background: #ededed;
    overflow: hidden;
  }

  .rep-bar-active {
    height: 100%;
    background: #34c759;
    border-radius: 3px;
  }

  .rep-digit {
    font-size: 11px;
    font-weight: 590;
    width: 38px;
    text-align: right;
  }
}

.agent-hitl-actions {
  display: flex;
  gap: 4px;
  justify-content: flex-end;
}

.hitl-btn {
  padding: 4px 8px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: #ffffff;
  font-size: 10px;
  font-weight: 590;
  cursor: pointer;

  &.btn-heal {
    color: #ff8d28;
    border-color: rgba(255, 141, 40, 0.4);
    &:hover {
      background: rgba(255, 141, 40, 0.1);
    }
  }

  &.btn-pin {
    color: #0088ff;
    &:hover {
      background: rgba(0, 136, 255, 0.08);
    }
  }
}

/* 凭单卡片 */
.receipt-glass-card {
  padding: 14px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.receipt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.receipt-id-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 590;
  color: #000000;
}

.verification-badge {
  font-size: 11px;
  font-weight: 590;
  padding: 3px 8px;
  border-radius: 10px;

  &.badge-valid {
    background: rgba(52, 199, 89, 0.12);
    color: #34c759;
  }
  &.badge-unverified {
    background: #ededed;
    color: #8e8e93;
  }
}

.receipt-field-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.02);
}

.field-item {
  display: flex;
  flex-direction: column;
  gap: 1px;

  .fl {
    font-size: 9px;
    color: rgba(60, 60, 67, 0.6);
  }
  .fv {
    font-size: 11px;
    font-weight: 590;
    color: #1d1d1f;

    &.code-font {
      font-family: ui-monospace, Menlo, Monaco, monospace;
      font-size: 10px;
    }
  }
}

.signature-box {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.03);

  .sig-label {
    font-size: 9px;
    font-weight: 590;
    color: rgba(60, 60, 67, 0.6);
  }
  .sig-hex {
    font-family: ui-monospace, Menlo, Monaco, monospace;
    font-size: 9.5px;
    color: #3d3d3d;
    word-break: break-all;
  }
}

.formula-caption-bar {
  padding: 6px 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.02);
  text-align: center;

  code {
    font-size: 10.5px;
    color: rgba(60, 60, 67, 0.8);
    font-family: ui-monospace, Menlo, Monaco, monospace;
  }
}
</style>

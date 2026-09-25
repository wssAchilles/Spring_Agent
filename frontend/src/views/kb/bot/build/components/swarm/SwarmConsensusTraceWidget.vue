<template>
  <div class="swarm-consensus-widget">
    <!-- Apple iOS 26 顶部通透材质胶囊工具条 (Top Liquid Glass Capsule) -->
    <header class="widget-capsule-header">
      <div class="capsule-branding">
        <div class="signal-indicator" :class="{ 'signal-danger': isCollusionDetected, 'signal-success': isEpsilonNashConverged }"></div>
        <span class="capsule-title">SWARM CONSENSUS & W3C TRACE // PHASE 140</span>
        <span class="capsule-subtag">APPLE LIQUID GLASS SPEC</span>
      </div>

      <div class="capsule-telemetry">
        <div class="telemetry-pill">
          <span class="pill-label">谄媚趋同度</span>
          <span class="pill-value" :class="{ 'text-danger': sycophancyScore >= 0.85 }">{{ (sycophancyScore * 100).toFixed(1) }}%</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">香农信息熵</span>
          <span class="pill-value">{{ entropyValue.toFixed(3) }}</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">ε-Nash 差分</span>
          <span class="pill-value" :class="{ 'text-success': geodesicDelta <= 0.05 }">Δσ {{ geodesicDelta.toFixed(3) }}</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">博弈轮次</span>
          <span class="pill-value">{{ currentRound }}/5</span>
        </div>
      </div>

      <div class="capsule-actions">
        <button class="ios-action-btn" @click="simulateCollusionAndCounterfactual" :disabled="isProcessing">
          <span class="btn-icon">⚡</span>
          <span>合谋对抗注入</span>
        </button>
        <button class="ios-action-btn" @click="simulateParetoConvergence" :disabled="isProcessing">
          <span class="btn-icon">🎯</span>
          <span>帕累托仲裁</span>
        </button>
        <button class="ios-action-btn btn-highlight" @click="verifyReceiptSignature" :disabled="!receipt">
          <span class="btn-icon">🛡️</span>
          <span>验真凭单</span>
        </button>
      </div>
    </header>

    <!-- 主展示网格：双栏 Apple 现代玻璃卡片布局 (No heavy shadows, multi-tier materials) -->
    <main class="widget-grid-layout">
      <!-- 左栏：合谋信息熵与反事实魔鬼代言人面板 -->
      <section class="ios-glass-card left-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">合谋信息熵与反事实对抗审计</h3>
            <span class="card-caption">香农信息论监控与魔鬼代言人 (CRITIC) 边界注入</span>
          </div>
          <span class="status-pill" :class="isCollusionDetected ? 'pill-alert' : 'pill-neutral'">
            {{ isCollusionDetected ? '合谋风险预警' : '观点分布健康' }}
          </span>
        </div>

        <!-- 谄媚度进度仪表条 -->
        <div class="sycophancy-meter-block">
          <div class="meter-labels">
            <span class="meter-desc">群体谄媚趋同度 (Sycophancy Threshold = 85%)</span>
            <span class="meter-number">{{ (sycophancyScore * 100).toFixed(1) }}%</span>
          </div>
          <div class="meter-track">
            <div
              class="meter-fill"
              :style="{ width: `${Math.min(100, sycophancyScore * 100)}%` }"
              :class="{ 'fill-danger': sycophancyScore >= 0.85 }"
            ></div>
            <div class="meter-threshold-marker" style="left: 85%;"></div>
          </div>
        </div>

        <!-- 多智能体发言观点矩阵 -->
        <div class="proposals-stream-block">
          <div class="section-micro-header">智能体超球面聚类发言流 (阿里千问 1536 维空间)</div>
          <div class="proposals-list">
            <div
              v-for="p in proposals"
              :key="p.agentId"
              class="proposal-item"
              :class="{ 'item-critic': p.role === 'CRITIC' }"
            >
              <div class="proposal-meta">
                <span class="agent-role-tag" :class="`role-${p.role.toLowerCase()}`">{{ p.role }}</span>
                <span class="agent-id-text">{{ p.agentId }}</span>
                <span class="similarity-score">投影内积: {{ p.similarity.toFixed(3) }}</span>
              </div>
              <p class="proposal-text">{{ p.content }}</p>
            </div>
          </div>
        </div>

        <!-- 魔鬼代言人对抗注入卡片 -->
        <div v-if="isDevilAdvocateInjected" class="critic-injection-box">
          <div class="critic-header">
            <span class="critic-pulse-dot"></span>
            <span class="critic-title">魔鬼代言人 (CRITIC) 反事实边界反例已强制注入</span>
            <span class="entropy-rebound-tag">多样性回升 +{{ diversityIncrease.toFixed(1) }}%</span>
          </div>
          <div class="critic-body">
            {{ counterfactualPrompt }}
          </div>
        </div>
      </section>

      <!-- 右栏：ε-Nash 纳什早停与多目标帕累托前沿优选面板 -->
      <section class="ios-glass-card right-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">ε-Nash 早停与多目标帕累托仲裁</h3>
            <span class="card-caption">测地线差分 Δσ ≤ 0.05 早停与三元效用非支配排序</span>
          </div>
          <span class="status-pill" :class="isEpsilonNashConverged ? 'pill-success' : 'pill-progress'">
            {{ isEpsilonNashConverged ? 'ε-Nash 稳态已收敛' : '对抗博弈中' }}
          </span>
        </div>

        <!-- 轮次测地角位移收敛轴 -->
        <div class="convergence-stepper-block">
          <div class="section-micro-header">策略测地线差分位移收敛轴 (上限 ≤ 5 轮)</div>
          <div class="stepper-track">
            <div
              v-for="r in 5"
              :key="r"
              class="step-node"
              :class="{
                'step-done': r <= currentRound,
                'step-current': r === currentRound,
                'step-nash': r === currentRound && isEpsilonNashConverged
              }"
            >
              <div class="step-circle">{{ r }}</div>
              <span class="step-label">R{{ r }}</span>
            </div>
          </div>
        </div>

        <!-- 帕累托方案三元雷达对比表 -->
        <div class="pareto-solutions-block">
          <div class="section-micro-header">候选方案非支配排序 (质量 · 成本 · 安全)</div>
          <div class="solutions-deck">
            <div
              v-for="sol in candidateSolutions"
              :key="sol.id"
              class="solution-card"
              :class="{
                'solution-winner': sol.id === winningSolutionId,
                'solution-dominated': sol.dominated
              }"
            >
              <div class="solution-header">
                <span class="sol-title">{{ sol.title }}</span>
                <span v-if="sol.id === winningSolutionId" class="winner-badge">帕累托最优</span>
                <span v-else-if="sol.dominated" class="dominated-badge">被支配淘汰</span>
                <span v-else class="frontier-badge">前沿候选</span>
              </div>
              <div class="solution-metrics-row">
                <div class="metric-col">
                  <span class="col-k">质量</span>
                  <span class="col-v">{{ (sol.quality * 100).toFixed(0) }}%</span>
                </div>
                <div class="metric-col">
                  <span class="col-k">成本优势</span>
                  <span class="col-v">{{ (sol.cost * 100).toFixed(0) }}%</span>
                </div>
                <div class="metric-col">
                  <span class="col-k">安全稳健</span>
                  <span class="col-v">{{ (sol.risk * 100).toFixed(0) }}%</span>
                </div>
                <div class="metric-col col-composite">
                  <span class="col-k">综合效用</span>
                  <span class="col-v highlight-score">{{ sol.compositeScore.toFixed(3) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>

    <!-- 下半区：W3C 全链路因果 Span 树时序甘特图与数字存证凭单 -->
    <footer class="widget-bottom-dock">
      <!-- W3C 因果拓扑 Span 树 -->
      <section class="ios-glass-card trace-dock-card">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">W3C TraceContext 全链路因果拓扑 Span 树</h3>
            <span class="card-caption">全局 TraceId: <code class="trace-code">{{ activeTraceId }}</code></span>
          </div>
          <div class="span-stats-badge">
            <span>捕获 Span: <strong>{{ traceSpans.length }}</strong> 节点</span>
            <span class="divider">/</span>
            <span>零孤儿链路</span>
          </div>
        </div>

        <div class="trace-spans-timeline">
          <div
            v-for="span in traceSpans"
            :key="span.spanId"
            class="span-row-item"
            :style="{ paddingLeft: `${span.depth * 20 + 12}px` }"
          >
            <div class="span-tree-guide" v-if="span.depth > 0"></div>
            <div class="span-role-badge" :class="`role-${span.role.toLowerCase()}`">{{ span.role }}</div>
            <div class="span-op-name">{{ span.operation }}</div>
            <div class="span-id-tag">span: {{ span.spanId.slice(0, 8) }}...</div>
            <div class="span-bar-wrapper">
              <div class="span-duration-bar" :style="{ width: `${Math.max(15, span.durationMs * 150)}px` }"></div>
              <span class="span-time-text">{{ span.durationMs.toFixed(2) }}ms</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 纯 Java 21 Record 凭单 SHA-256 自验真卡片 -->
      <section class="ios-glass-card receipt-dock-card">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">不可变博弈共识存证凭单</h3>
            <span class="card-caption">纯 Java 21 Record 格式 · 常量时间防篡改</span>
          </div>
          <span
            v-if="verificationStatus"
            class="verification-badge"
            :class="{ 'verify-pass': verificationStatus === 'PASSED', 'verify-fail': verificationStatus === 'FAILED' }"
          >
            {{ verificationStatus === 'PASSED' ? '✓ 常量时间验真通过 100%' : '✗ 凭单校验失败' }}
          </span>
        </div>

        <div v-if="receipt" class="receipt-content-meta">
          <div class="meta-field">
            <span class="meta-label">存证编号 (ReceiptId):</span>
            <span class="meta-value">{{ receipt.receiptId }}</span>
          </div>
          <div class="meta-field">
            <span class="meta-label">胜选方案 (Winner):</span>
            <span class="meta-value text-accent">{{ receipt.winningProposalTitle }}</span>
          </div>
          <div class="meta-field">
            <span class="meta-label">纳什收敛状态:</span>
            <span class="meta-value text-success">ε-Nash Converged (≤ 5 轮)</span>
          </div>
          <div class="meta-field">
            <span class="meta-label">SHA-256 数字签名:</span>
            <code class="sha256-code">{{ receipt.sha256Signature }}</code>
          </div>
        </div>
        <div v-else class="receipt-empty-placeholder">
          <span>等待博弈调度完成后签发不可变存证凭单...</span>
        </div>
      </section>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

// ============================================================================
// 数据结构与接口定义
// ============================================================================
interface SwarmProposalView {
  agentId: string;
  role: 'PRO' | 'CON' | 'ANALYST' | 'LEGAL' | 'CRITIC';
  content: string;
  similarity: number;
}

interface SolutionCandidateView {
  id: string;
  title: string;
  quality: number;
  cost: number;
  risk: number;
  compositeScore: number;
  dominated: boolean;
}

interface TraceSpanView {
  spanId: string;
  parentSpanId: string | null;
  role: string;
  operation: string;
  durationMs: number;
  depth: number;
}

interface ConsensusReceiptView {
  receiptId: string;
  traceId: string;
  rootSpanId: string;
  winningProposalTitle: string;
  rounds: number;
  sycophancyScore: number;
  entropyValue: number;
  sha256Signature: string;
}

// ============================================================================
// 响应式状态定义
// ============================================================================
const isProcessing = ref(false);
const sycophancyScore = ref(0.68);
const entropyValue = ref(1.386);
const geodesicDelta = ref(0.124);
const currentRound = ref(2);
const isCollusionDetected = ref(false);
const isDevilAdvocateInjected = ref(false);
const isEpsilonNashConverged = ref(false);
const diversityIncrease = ref(0);
const counterfactualPrompt = ref('');
const winningSolutionId = ref('sol-01');
const activeTraceId = ref('4bf92f3577b34da6a3ce929d0e0e4736');
const verificationStatus = ref<string | null>(null);

// 提案流列表
const proposals = ref<SwarmProposalView[]>([
  {
    agentId: 'agent-pro-01',
    role: 'PRO',
    content: '提议推进跨境双向业务扩展架构，建立高并发实时同步链路。',
    similarity: 0.72
  },
  {
    agentId: 'agent-con-02',
    role: 'CON',
    content: '双向实时同步存在中间件网络抖动与数据脏写风险，需设置降级只读。',
    similarity: 0.65
  },
  {
    agentId: 'agent-analyst-03',
    role: 'ANALYST',
    content: '综合性能吞吐量与成本核算，推荐引入带 Undo Log 的补偿机制。',
    similarity: 0.68
  }
]);

// 候选方案列表
const candidateSolutions = ref<SolutionCandidateView[]>([
  {
    id: 'sol-01',
    title: '高并发补偿双向事务架构',
    quality: 0.94,
    cost: 0.88,
    risk: 0.95,
    compositeScore: 0.928,
    dominated: false
  },
  {
    id: 'sol-02',
    title: '极致成本微服务单写方案',
    quality: 0.85,
    cost: 0.96,
    risk: 0.78,
    compositeScore: 0.856,
    dominated: false
  },
  {
    id: 'sol-03',
    title: '激进全内存同步无撤销日志方案',
    quality: 0.75,
    cost: 0.70,
    risk: 0.60,
    compositeScore: 0.692,
    dominated: true
  }
]);

// W3C Trace Spans 因果链路
const traceSpans = ref<TraceSpanView[]>([
  {
    spanId: '00f067aa0ba902b7',
    parentSpanId: null,
    role: 'ORCHESTRATOR',
    operation: 'SWARM_SESSION_START',
    durationMs: 0.12,
    depth: 0
  },
  {
    spanId: '5c1798369e061805',
    parentSpanId: '00f067aa0ba902b7',
    role: 'PRO',
    operation: 'PROPOSAL_GENERATION',
    durationMs: 0.45,
    depth: 1
  },
  {
    spanId: '8a2b3c4d5e6f7081',
    parentSpanId: '00f067aa0ba902b7',
    role: 'CON',
    operation: 'DEFENSE_ARGUMENT',
    durationMs: 0.52,
    depth: 1
  },
  {
    spanId: '91a2b3c4d5e6f708',
    parentSpanId: '00f067aa0ba902b7',
    role: 'ANALYST',
    operation: 'ENTROPY_AUDIT',
    durationMs: 0.38,
    depth: 1
  }
]);

// 密码学凭单
const receipt = ref<ConsensusReceiptView | null>({
  receiptId: 'RCP-SWARM-20260925-8891',
  traceId: '4bf92f3577b34da6a3ce929d0e0e4736',
  rootSpanId: '00f067aa0ba902b7',
  winningProposalTitle: '高并发补偿双向事务架构',
  rounds: 3,
  sycophancyScore: 0.68,
  entropyValue: 1.386,
  sha256Signature: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
});

// ============================================================================
// 模拟与交互方法
// ============================================================================

/**
 * 模拟合谋崩塌检测与反事实魔鬼代言人对抗注入
 */
function simulateCollusionAndCounterfactual() {
  isProcessing.value = true;

  // 1. 模拟观点被合谋趋同
  sycophancyScore.value = 0.942;
  entropyValue.value = 0.412;
  isCollusionDetected.value = true;

  proposals.value = [
    {
      agentId: 'agent-pro-01',
      role: 'PRO',
      content: '必须全面推进无约束激进同步，不需要增加复杂回滚机制。',
      similarity: 0.96
    },
    {
      agentId: 'agent-con-02',
      role: 'CON',
      content: '附和前序方案，为了抢占业务先机，同意不增加补偿日志。',
      similarity: 0.95
    },
    {
      agentId: 'agent-legal-04',
      role: 'LEGAL',
      content: '附和前序说明，暂未发现违规硬性条款，同意快速放行。',
      similarity: 0.94
    }
  ];

  // 2. 微秒级触发魔鬼代言人注入
  setTimeout(() => {
    isDevilAdvocateInjected.value = true;
    diversityIncrease.value = 38.6;
    counterfactualPrompt.value =
      '[魔鬼代言人/CRITIC反事实审计警报]：监测到多智能体观点严重谄媚附和(趋同度 94.2% ≥ 85%)！现强制注入对偶边界反例：若在银行月结高峰遭遇网络分区与数据库死锁，缺乏 Undo Log 将导致数千笔脏数据无法溯源，直接面临监管吊销牌照！';

    proposals.value.push({
      agentId: 'agent-critic-devil',
      role: 'CRITIC',
      content: '【反事实魔鬼代言人】：提出极限黑天鹅反例！严禁在无 Undo Log 情况下开放写操作，要求必须保留双向补偿机制。',
      similarity: -0.85
    });

    traceSpans.value.push({
      spanId: 'cc778899aabbccdd',
      parentSpanId: '00f067aa0ba902b7',
      role: 'CRITIC',
      operation: 'COUNTERFACTUAL_INJECTION',
      durationMs: 0.28,
      depth: 1
    });

    isProcessing.value = false;
  }, 300);
}

/**
 * 模拟 ε-Nash 测地早停与帕累托仲裁
 */
function simulateParetoConvergence() {
  isProcessing.value = true;
  setTimeout(() => {
    currentRound.value = 3;
    geodesicDelta.value = 0.038; // <= 0.05
    isEpsilonNashConverged.value = true;
    winningSolutionId.value = 'sol-01';

    traceSpans.value.push({
      spanId: 'ee11223344556677',
      parentSpanId: '00f067aa0ba902b7',
      role: 'ARBITRATOR',
      operation: 'PARETO_ARBITRATION',
      durationMs: 0.18,
      depth: 1
    });

    // 签发新凭单
    receipt.value = {
      receiptId: 'RCP-SWARM-' + Date.now().toString().slice(-6),
      traceId: activeTraceId.value,
      rootSpanId: '00f067aa0ba902b7',
      winningProposalTitle: '高并发补偿双向事务架构',
      rounds: 3,
      sycophancyScore: sycophancyScore.value,
      entropyValue: entropyValue.value,
      sha256Signature: '7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069'
    };

    verificationStatus.value = null;
    isProcessing.value = false;
  }, 350);
}

/**
 * 凭单密码学常量时间自验真
 */
function verifyReceiptSignature() {
  if (!receipt.value) return;
  // 模拟常量时间对比验真
  verificationStatus.value = 'PASSED';
}
</script>

<style scoped lang="scss">
@use '@/assets/system/styles/ios26-liquid-glass.scss' as ios26;

/* ============================================================================
   Apple iOS 26 Liquid Glass & Vibrancy 设计系统实现
   1. 严格遵守铁律：绝不在玻璃容器自身滥用 transform/opacity/filter，杜绝隔离组失效；
   2. 采用双层材质：高光层 + 50px 模糊层与 color-dodge 混合；
   3. Headline 严格为 590，排版靠光学微调；
   4. 零阴影体系，纯靠材质与柔光轮廓表达空间层级；
   ============================================================================ */

.swarm-consensus-widget {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  color: #ededed;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display", "PingFang SC", sans-serif;
  letter-spacing: -0.23px;
  box-sizing: border-box;
}

/* 顶部通透胶囊 Header (Dock/Pill 质感) */
.widget-capsule-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-radius: var(--ios26-radius-pill, 1000px);
  background: rgba(18, 18, 22, 0.72);
  backdrop-filter: blur(50px);
  -webkit-backdrop-filter: blur(50px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  gap: 16px;

  .capsule-branding {
    display: flex;
    align-items: center;
    gap: 10px;

    .signal-indicator {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #0088ff; // iOS 26 系统蓝
      box-shadow: 0 0 8px rgba(0, 136, 255, 0.6);
      transition: background 0.3s ease, box-shadow 0.3s ease;

      &.signal-danger {
        background: #ff383c;
        box-shadow: 0 0 10px rgba(255, 56, 60, 0.8);
      }

      &.signal-success {
        background: #34c759;
        box-shadow: 0 0 10px rgba(52, 199, 89, 0.8);
      }
    }

    .capsule-title {
      font-size: 13px;
      font-weight: 590; // Headline 590 铁律
      letter-spacing: -0.08px;
      color: #ffffff;
    }

    .capsule-subtag {
      font-size: 11px;
      font-weight: 400;
      letter-spacing: 0.06px;
      color: rgba(255, 255, 255, 0.45);
      background: rgba(255, 255, 255, 0.08);
      padding: 2px 8px;
      border-radius: 6px;
    }
  }

  .capsule-telemetry {
    display: flex;
    align-items: center;
    gap: 12px;

    .telemetry-pill {
      display: flex;
      flex-direction: column;
      align-items: center;
      background: rgba(255, 255, 255, 0.04);
      padding: 4px 12px;
      border-radius: 8px;
      border: 1px solid rgba(255, 255, 255, 0.06);

      .pill-label {
        font-size: 10px;
        color: rgba(255, 255, 255, 0.5);
      }

      .pill-value {
        font-size: 12px;
        font-weight: 590;
        color: #ffffff;

        &.text-danger {
          color: #ff383c;
        }

        &.text-success {
          color: #34c759;
        }
      }
    }
  }

  .capsule-actions {
    display: flex;
    align-items: center;
    gap: 8px;

    .ios-action-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border-radius: var(--ios26-radius-control, 12px);
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: #ffffff;
      font-size: 12px;
      font-weight: 590;
      cursor: pointer;
      transition: background 0.15s ease, border-color 0.15s ease;

      &:hover:not(:disabled) {
        background: rgba(255, 255, 255, 0.15);
        border-color: rgba(255, 255, 255, 0.3);
      }

      &:active:not(:disabled) {
        background: rgba(255, 255, 255, 0.22);
      }

      &:disabled {
        opacity: 0.4;
        cursor: not-allowed;
      }

      &.btn-highlight {
        background: rgba(0, 136, 255, 0.2);
        border-color: rgba(0, 136, 255, 0.4);
        color: #60b6ff;

        &:hover:not(:disabled) {
          background: rgba(0, 136, 255, 0.3);
        }
      }
    }
  }
}

/* 主展示双栏卡片网格 */
.widget-grid-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* Apple 玻璃卡片容器 (双层光学质感，无厚重投影) */
.ios-glass-card {
  border-radius: var(--ios26-radius-card, 30px);
  background: rgba(14, 14, 18, 0.75);
  backdrop-filter: blur(50px);
  -webkit-backdrop-filter: blur(50px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-sizing: border-box;

  .card-headline-bar {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;

    .title-cluster {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .card-headline {
        margin: 0;
        font-size: 16px;
        font-weight: 590;
        letter-spacing: -0.31px;
        color: #ffffff;
      }

      .card-caption {
        font-size: 12px;
        color: rgba(255, 255, 255, 0.45);
        letter-spacing: 0;
      }
    }

    .status-pill {
      font-size: 11px;
      font-weight: 590;
      padding: 3px 10px;
      border-radius: 1000px;

      &.pill-neutral {
        background: rgba(255, 255, 255, 0.08);
        color: rgba(255, 255, 255, 0.7);
        border: 1px solid rgba(255, 255, 255, 0.1);
      }

      &.pill-alert {
        background: rgba(255, 56, 60, 0.15);
        color: #ff6467;
        border: 1px solid rgba(255, 56, 60, 0.35);
      }

      &.pill-success {
        background: rgba(52, 199, 89, 0.15);
        color: #55d677;
        border: 1px solid rgba(52, 199, 89, 0.35);
      }

      &.pill-progress {
        background: rgba(0, 136, 255, 0.15);
        color: #4da6ff;
        border: 1px solid rgba(0, 136, 255, 0.35);
      }
    }
  }
}

/* 进度条与指标条 */
.sycophancy-meter-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: rgba(255, 255, 255, 0.03);
  padding: 12px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.05);

  .meter-labels {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.6);

    .meter-number {
      font-weight: 590;
      color: #ffffff;
    }
  }

  .meter-track {
    position: relative;
    height: 8px;
    background: rgba(255, 255, 255, 0.08);
    border-radius: 4px;
    overflow: hidden;

    .meter-fill {
      height: 100%;
      background: #0088ff;
      border-radius: 4px;
      transition: width 0.3s cubic-bezier(0.25, 1, 0.33, 1), background 0.3s ease;

      &.fill-danger {
        background: #ff383c;
      }
    }

    .meter-threshold-marker {
      position: absolute;
      top: 0;
      bottom: 0;
      width: 2px;
      background: rgba(255, 255, 255, 0.8);
    }
  }
}

/* 提案流列表 */
.proposals-stream-block {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .section-micro-header {
    font-size: 11px;
    font-weight: 590;
    color: rgba(255, 255, 255, 0.4);
    text-transform: uppercase;
  }

  .proposals-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: 220px;
    overflow-y: auto;

    .proposal-item {
      background: rgba(255, 255, 255, 0.04);
      padding: 10px 14px;
      border-radius: 12px;
      border: 1px solid rgba(255, 255, 255, 0.06);

      &.item-critic {
        background: rgba(255, 56, 60, 0.08);
        border-color: rgba(255, 56, 60, 0.25);
      }

      .proposal-meta {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 4px;

        .agent-role-tag {
          font-size: 10px;
          font-weight: 590;
          padding: 1px 6px;
          border-radius: 4px;

          &.role-pro { background: rgba(0, 136, 255, 0.2); color: #5eb2ff; }
          &.role-con { background: rgba(255, 141, 40, 0.2); color: #ffa65e; }
          &.role-analyst { background: rgba(97, 85, 245, 0.2); color: #9c94ff; }
          &.role-legal { background: rgba(0, 195, 208, 0.2); color: #4de2ee; }
          &.role-critic { background: rgba(255, 56, 60, 0.25); color: #ff6b6e; }
        }

        .agent-id-text {
          font-size: 11px;
          color: rgba(255, 255, 255, 0.5);
        }

        .similarity-score {
          margin-left: auto;
          font-size: 10px;
          color: rgba(255, 255, 255, 0.35);
        }
      }

      .proposal-text {
        margin: 0;
        font-size: 12px;
        line-height: 1.5;
        color: rgba(255, 255, 255, 0.85);
      }
    }
  }
}

/* 魔鬼代言人注入框 */
.critic-injection-box {
  background: rgba(255, 56, 60, 0.1);
  border: 1px solid rgba(255, 56, 60, 0.3);
  border-radius: 14px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  .critic-header {
    display: flex;
    align-items: center;
    gap: 8px;

    .critic-pulse-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #ff383c;
      box-shadow: 0 0 6px #ff383c;
    }

    .critic-title {
      font-size: 11px;
      font-weight: 590;
      color: #ff6b6e;
    }

    .entropy-rebound-tag {
      margin-left: auto;
      font-size: 10px;
      font-weight: 590;
      color: #34c759;
      background: rgba(52, 199, 89, 0.15);
      padding: 1px 6px;
      border-radius: 4px;
    }
  }

  .critic-body {
    font-size: 12px;
    line-height: 1.4;
    color: rgba(255, 255, 255, 0.9);
  }
}

/* 轮次收敛步进轴 */
.convergence-stepper-block {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .stepper-track {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: rgba(255, 255, 255, 0.03);
    padding: 12px 20px;
    border-radius: 14px;
    border: 1px solid rgba(255, 255, 255, 0.05);

    .step-node {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;

      .step-circle {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 12px;
        font-weight: 590;
        background: rgba(255, 255, 255, 0.08);
        color: rgba(255, 255, 255, 0.4);
      }

      .step-label {
        font-size: 10px;
        color: rgba(255, 255, 255, 0.4);
      }

      &.step-done .step-circle {
        background: rgba(0, 136, 255, 0.2);
        color: #5eb2ff;
      }

      &.step-current .step-circle {
        background: #0088ff;
        color: #ffffff;
        box-shadow: 0 0 10px rgba(0, 136, 255, 0.6);
      }

      &.step-nash .step-circle {
        background: #34c759;
        color: #ffffff;
        box-shadow: 0 0 10px rgba(52, 199, 89, 0.6);
      }
    }
  }
}

/* 帕累托方案列表 */
.pareto-solutions-block {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .solutions-deck {
    display: flex;
    flex-direction: column;
    gap: 8px;

    .solution-card {
      background: rgba(255, 255, 255, 0.04);
      padding: 12px 14px;
      border-radius: 14px;
      border: 1px solid rgba(255, 255, 255, 0.06);

      &.solution-winner {
        background: rgba(97, 85, 245, 0.12);
        border-color: rgba(97, 85, 245, 0.4);
      }

      &.solution-dominated {
        opacity: 0.5;
        background: rgba(0, 0, 0, 0.2);
      }

      .solution-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 8px;

        .sol-title {
          font-size: 13px;
          font-weight: 590;
          color: #ffffff;
        }

        .winner-badge {
          font-size: 10px;
          font-weight: 590;
          padding: 2px 8px;
          border-radius: 6px;
          background: #6155f5;
          color: #ffffff;
        }

        .dominated-badge {
          font-size: 10px;
          padding: 2px 8px;
          border-radius: 6px;
          background: rgba(255, 255, 255, 0.1);
          color: rgba(255, 255, 255, 0.5);
        }

        .frontier-badge {
          font-size: 10px;
          padding: 2px 8px;
          border-radius: 6px;
          background: rgba(0, 136, 255, 0.15);
          color: #60b6ff;
        }
      }

      .solution-metrics-row {
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: 8px;

        .metric-col {
          display: flex;
          flex-direction: column;
          gap: 2px;

          .col-k {
            font-size: 10px;
            color: rgba(255, 255, 255, 0.45);
          }

          .col-v {
            font-size: 12px;
            font-weight: 590;
            color: rgba(255, 255, 255, 0.85);

            &.highlight-score {
              color: #ffffff;
            }
          }
        }
      }
    }
  }
}

/* 下方底座：W3C Span 树与密码学凭单 */
.widget-bottom-dock {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 16px;

  .trace-code {
    font-family: ui-monospace, Menlo, Monaco, Consolas, monospace;
    font-size: 11px;
    color: #60b6ff;
  }

  .span-stats-badge {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.5);
    background: rgba(255, 255, 255, 0.05);
    padding: 3px 10px;
    border-radius: 8px;

    .divider {
      margin: 0 4px;
      opacity: 0.3;
    }
  }

  .trace-spans-timeline {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 180px;
    overflow-y: auto;

    .span-row-item {
      display: flex;
      align-items: center;
      gap: 10px;
      font-size: 11px;
      background: rgba(255, 255, 255, 0.02);
      padding: 6px 12px;
      border-radius: 8px;

      .span-role-badge {
        font-size: 9px;
        font-weight: 590;
        padding: 1px 5px;
        border-radius: 4px;
        background: rgba(255, 255, 255, 0.1);
        color: #ffffff;
      }

      .span-op-name {
        color: rgba(255, 255, 255, 0.85);
        font-weight: 590;
      }

      .span-id-tag {
        font-size: 10px;
        color: rgba(255, 255, 255, 0.35);
        font-family: monospace;
      }

      .span-bar-wrapper {
        margin-left: auto;
        display: flex;
        align-items: center;
        gap: 8px;

        .span-duration-bar {
          height: 4px;
          background: #0088ff;
          border-radius: 2px;
        }

        .span-time-text {
          font-size: 10px;
          color: rgba(255, 255, 255, 0.5);
          min-width: 45px;
          text-align: right;
        }
      }
    }
  }

  .receipt-dock-card {
    .verification-badge {
      font-size: 11px;
      font-weight: 590;
      padding: 3px 10px;
      border-radius: 6px;

      &.verify-pass {
        background: rgba(52, 199, 89, 0.2);
        color: #34c759;
        border: 1px solid rgba(52, 199, 89, 0.4);
      }

      &.verify-fail {
        background: rgba(255, 56, 60, 0.2);
        color: #ff383c;
        border: 1px solid rgba(255, 56, 60, 0.4);
      }
    }

    .receipt-content-meta {
      display: flex;
      flex-direction: column;
      gap: 8px;
      font-size: 12px;

      .meta-field {
        display: flex;
        align-items: baseline;
        gap: 6px;

        .meta-label {
          color: rgba(255, 255, 255, 0.45);
        }

        .meta-value {
          color: #ffffff;
          font-weight: 590;

          &.text-accent {
            color: #9c94ff;
          }
        }

        .sha256-code {
          font-family: monospace;
          font-size: 10px;
          color: #55d677;
          word-break: break-all;
          background: rgba(0, 0, 0, 0.3);
          padding: 2px 6px;
          border-radius: 4px;
        }
      }
    }

    .receipt-empty-placeholder {
      font-size: 12px;
      color: rgba(255, 255, 255, 0.3);
      padding: 20px 0;
      text-align: center;
    }
  }
}
</style>

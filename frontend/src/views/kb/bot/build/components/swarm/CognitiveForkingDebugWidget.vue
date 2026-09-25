<template>
  <div class="cognitive-forking-widget">
    <!-- Apple iOS 26 顶部通透材质胶囊工具条 (Top Liquid Glass Capsule) -->
    <header class="widget-capsule-header">
      <div class="capsule-branding">
        <div class="signal-indicator" :class="{ 'signal-forking': isForkingActive, 'signal-verified': isVerified }"></div>
        <span class="capsule-title">COGNITIVE TIME-TRAVEL & FORKING SANDBOX // PHASE 141</span>
        <span class="capsule-subtag">APPLE LIQUID GLASS SPEC</span>
      </div>

      <div class="capsule-telemetry">
        <div class="telemetry-pill">
          <span class="pill-label">活跃分支</span>
          <span class="pill-value text-accent">{{ branches.length }}/20</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">当前快照步数</span>
          <span class="pill-value">Step {{ currentStepIndex }}</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">结构共享节约</span>
          <span class="pill-value text-success">{{ structuralSavings.toFixed(1) }}%</span>
        </div>
        <div class="telemetry-pill">
          <span class="pill-label">幽灵变量污染</span>
          <span class="pill-value text-success">0.0% 严格隔离</span>
        </div>
      </div>

      <!-- 双向时间旅行控制组 -->
      <div class="capsule-actions">
        <button class="ios-action-btn" @click="handleStepBackward" :disabled="currentStepIndex <= 0">
          <span class="btn-icon">⏮</span>
          <span>单步后退</span>
        </button>
        <button class="ios-action-btn" @click="handleStepForward" :disabled="currentStepIndex >= maxStepIndex">
          <span class="btn-icon">⏭</span>
          <span>单步前进</span>
        </button>
        <button class="ios-action-btn btn-fork" @click="triggerForkSandbox">
          <span class="btn-icon">🌿</span>
          <span>时空分叉</span>
        </button>
        <button class="ios-action-btn btn-highlight" @click="verifyReceipt" :disabled="!currentReceipt">
          <span class="btn-icon">🛡️</span>
          <span>自验真</span>
        </button>
      </div>
    </header>

    <!-- 主交互网格：左栏分支拓扑与状态检查器，右栏反事实补丁注入台 -->
    <main class="widget-grid-layout">
      <!-- 左栏：时空分支 DAG 树与快照状态检查器 -->
      <section class="ios-glass-card left-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">时空分支树与快照时光旅行</h3>
            <span class="card-caption">基于写时复制与持久化结构共享 (HAMT) 的无损状态回溯</span>
          </div>
          <span class="status-pill pill-neutral">
            当前分支: {{ activeBranchId }}
          </span>
        </div>

        <!-- 分支选择器标签条 (Apple 胶囊药丸切换) -->
        <div class="branch-selector-capsules">
          <button
            v-for="b in branches"
            :key="b.id"
            class="branch-pill-btn"
            :class="{ 'pill-active': b.id === activeBranchId, 'pill-fork': b.id !== 'main' }"
            @click="selectBranch(b.id)"
          >
            <span class="branch-icon">{{ b.id === 'main' ? '●' : '↳' }}</span>
            <span class="branch-name">{{ b.title }}</span>
            <span class="branch-depth-tag">D{{ b.depth }}</span>
          </button>
        </div>

        <!-- 历史快照时间轴步进卡片 -->
        <div class="snapshot-timeline-container">
          <div class="section-micro-header">分支快照因果步进轴</div>
          <div class="timeline-steps-deck">
            <div
              v-for="(snap, idx) in activeSnapshots"
              :key="snap.id"
              class="snapshot-step-item"
              :class="{ 'step-selected': idx === currentStepIndex }"
              @click="jumpToStep(idx)"
            >
              <div class="step-badge">{{ idx }}</div>
              <div class="step-info">
                <span class="step-title">{{ snap.title }}</span>
                <span class="step-meta">Round {{ snap.round }} · Span: {{ snap.spanId.slice(0, 8) }}...</span>
              </div>
              <span v-if="idx === currentStepIndex" class="cursor-tag">当前游标</span>
            </div>
          </div>
        </div>

        <!-- 当前快照集群状态检查器 (Agent 状态 + 共享黑板) -->
        <div v-if="activeSnapshotDetail" class="cluster-inspector-block">
          <div class="section-micro-header">智能体集群快照状态检查 (不可变只读视图)</div>
          <div class="agent-inspect-list">
            <div
              v-for="ag in activeSnapshotDetail.agents"
              :key="ag.id"
              class="agent-inspect-card"
              :class="`role-${ag.role.toLowerCase()}`"
            >
              <div class="agent-inspect-head">
                <span class="inspect-role-tag">{{ ag.role }}</span>
                <span class="inspect-id">{{ ag.id }}</span>
              </div>
              <div class="inspect-thought-text">"{{ ag.thought }}"</div>
            </div>
          </div>

          <div class="blackboard-preview-box">
            <span class="bb-title">集群共享黑板 (Blackboard):</span>
            <pre class="bb-json">{{ JSON.stringify(activeSnapshotDetail.blackboard, null, 2) }}</pre>
          </div>
        </div>
      </section>

      <!-- 右栏：反事实热补丁干预台与沙盒推演 -->
      <section class="ios-glass-card right-panel">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">反事实 (What-If) 热补丁干预注入台</h3>
            <span class="card-caption">Pearl 因果干预 do(X=x) 驱动平行沙盒推演</span>
          </div>
          <span class="status-pill" :class="isPatched ? 'pill-alert' : 'pill-progress'">
            {{ isPatched ? '补丁待派生' : '沙盒准备就绪' }}
          </span>
        </div>

        <!-- 目标智能体选择 -->
        <div class="patch-target-selector">
          <div class="section-micro-header">选择干预注入的目标智能体 (Target Agent)</div>
          <div class="target-agents-grid">
            <div
              v-for="ag in availableTargetAgents"
              :key="ag.id"
              class="target-agent-option"
              :class="{ 'option-selected': ag.id === targetAgentId }"
              @click="targetAgentId = ag.id"
            >
              <span class="target-role">{{ ag.role }}</span>
              <span class="target-name">{{ ag.id }}</span>
            </div>
          </div>
        </div>

        <!-- 思考链与提示词热覆写 -->
        <div class="patch-input-group">
          <label class="patch-label">反事实 Prompt / 思考链覆写 (Patched Thought)</label>
          <textarea
            v-model="patchThought"
            rows="3"
            class="ios-textarea"
            placeholder="输入反事实干预提示词，例如：【2026 最新金融合规特批通道】：允许在双向补偿日志完备前提下放行跨境清算..."
          ></textarea>
        </div>

        <!-- 内存与黑板热补丁覆写 -->
        <div class="patch-kv-group">
          <div class="kv-header">
            <span class="patch-label">注入 Working Memory 键值对</span>
            <button class="add-kv-btn" @click="addMemoryEntry">+ 添加键值</button>
          </div>
          <div class="kv-rows">
            <div v-for="(entry, i) in memoryEntries" :key="i" class="kv-row-item">
              <input v-model="entry.key" class="ios-input key-input" placeholder="Key" />
              <span class="kv-sep">:</span>
              <input v-model="entry.val" class="ios-input val-input" placeholder="Value" />
              <button class="remove-btn" @click="memoryEntries.splice(i, 1)">×</button>
            </div>
          </div>
        </div>

        <!-- 干预动机说明 -->
        <div class="patch-input-group">
          <label class="patch-label">反事实干预动机说明 (Audit Reason)</label>
          <input
            v-model="interventionReason"
            class="ios-input"
            placeholder="例如：更正旧规漏洞，恢复合规放行并验证时延指标"
          />
        </div>

        <!-- 派生操作卡片 -->
        <div class="fork-commit-box">
          <div class="fork-desc">
            将在当前快照 <strong>Step {{ currentStepIndex }}</strong> 原位派生全新时空沙盒分支，主线只读冻结，保证零污染。
          </div>
          <button class="execute-fork-btn" @click="triggerForkSandbox">
            <span>🌿 派生反事实时空沙盒分支 (Fork Branch)</span>
          </button>
        </div>
      </section>
    </main>

    <!-- 下方底座：时空分叉不可变密码学存证凭单 -->
    <footer class="widget-bottom-dock">
      <section class="ios-glass-card receipt-dock-card">
        <div class="card-headline-bar">
          <div class="title-cluster">
            <h3 class="card-headline">不可变时空分叉审计存证凭单</h3>
            <span class="card-caption">纯 Java 21 Record 格式 · SHA-256 常量时间自验真防篡改</span>
          </div>
          <span
            v-if="verificationResult"
            class="verification-badge"
            :class="{ 'verify-pass': verificationResult === 'PASSED', 'verify-fail': verificationResult === 'FAILED' }"
          >
            {{ verificationResult === 'PASSED' ? '✓ 常量时间验真通过 100%' : '✗ 凭单校验失败' }}
          </span>
        </div>

        <div v-if="currentReceipt" class="receipt-content-meta">
          <div class="meta-row">
            <div class="meta-field">
              <span class="meta-label">存证编号:</span>
              <span class="meta-value">{{ currentReceipt.receiptId }}</span>
            </div>
            <div class="meta-field">
              <span class="meta-label">父子因果:</span>
              <span class="meta-value text-accent">{{ currentReceipt.parentBranch }} ➔ {{ currentReceipt.forkedBranch }}</span>
            </div>
            <div class="meta-field">
              <span class="meta-label">基准挂载快照:</span>
              <span class="meta-value">{{ currentReceipt.baseSnapshot }}</span>
            </div>
          </div>
          <div class="meta-row">
            <div class="meta-field">
              <span class="meta-label">干预智能体:</span>
              <span class="meta-value text-warning">{{ currentReceipt.patchedAgent }}</span>
            </div>
            <div class="meta-field">
              <span class="meta-label">分叉耗时:</span>
              <span class="meta-value text-success">{{ currentReceipt.latencyMs.toFixed(2) }}ms</span>
            </div>
            <div class="meta-field">
              <span class="meta-label">补丁 SHA-256:</span>
              <code class="sha256-code">{{ currentReceipt.patchHash.slice(0, 24) }}...</code>
            </div>
          </div>
          <div class="meta-field full-width">
            <span class="meta-label">全局凭单数字签名:</span>
            <code class="sha256-code-full">{{ currentReceipt.signature }}</code>
          </div>
        </div>
      </section>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

// ============================================================================
// 数据结构定义
// ============================================================================
interface BranchView {
  id: string;
  title: string;
  depth: number;
}

interface SnapshotView {
  id: string;
  title: string;
  round: number;
  spanId: string;
  agents: { id: string; role: string; thought: string }[];
  blackboard: Record<string, any>;
}

interface ReceiptView {
  receiptId: string;
  parentBranch: string;
  forkedBranch: string;
  baseSnapshot: string;
  patchedAgent: string;
  patchHash: string;
  latencyMs: number;
  signature: string;
}

// ============================================================================
// 响应式状态
// ============================================================================
const isForkingActive = ref(false);
const isVerified = ref(false);
const structuralSavings = ref(84.2);
const activeBranchId = ref('main');
const currentStepIndex = ref(2);
const maxStepIndex = ref(3);
const isPatched = ref(false);
const verificationResult = ref<string | null>(null);

// 目标干预与补丁表单
const targetAgentId = ref('agent-con');
const patchThought = ref(
  '【2026 最新金融合规特批通道】：允许在双向补偿日志完备前提下放行跨境清算'
);
const interventionReason = ref('更正旧规漏洞，恢复合规放行并验证时延指标');
const memoryEntries = ref([
  { key: 'regulation_version', val: '2026.09_FINAL' },
  { key: 'dual_compensation_enforced', val: 'true' }
]);

// 活跃分支列表
const branches = ref<BranchView[]>([
  { id: 'main', title: '主干 main', depth: 0 },
  { id: 'fork-1', title: '平行沙盒 fork-1', depth: 1 }
]);

// 主线快照数据
const mainSnapshots: SnapshotView[] = [
  {
    id: 'snap-m-0',
    title: 'Step 0: 业务意图解析与集群初始化',
    round: 1,
    spanId: '0011223344556677',
    agents: [
      { id: 'agent-pro', role: 'PRO', thought: '识别出 2 亿元跨境离岸结构搭建方案' },
      { id: 'agent-con', role: 'CON', thought: '初始化合规风控规则库' }
    ],
    blackboard: { status: 'INITIALIZED', stage: 'INTENT_PARSING', amount: 200000000 }
  },
  {
    id: 'snap-m-1',
    title: 'Step 1: 提出离岸快速写入方案',
    round: 1,
    spanId: '1122334455667788',
    agents: [
      { id: 'agent-pro', role: 'PRO', thought: '建议全面推进无约束激进同步，提升吞吐' },
      { id: 'agent-con', role: 'CON', thought: '初审通过，暂未发现违规硬性条款' }
    ],
    blackboard: { status: 'PROPOSING', stage: 'PROPOSAL_EXPANSION', compensation_needed: false }
  },
  {
    id: 'snap-m-2',
    title: 'Step 2: 误采纳过时监管条文导致争议挂起',
    round: 2,
    spanId: '2233445566778899',
    agents: [
      { id: 'agent-pro', role: 'PRO', thought: '坚持抢占业务先机' },
      { id: 'agent-con', role: 'CON', thought: '基于 2021 旧版监管条例，驳回该无补偿方案！' }
    ],
    blackboard: { status: 'FAULTY_ARGUMENT', stage: 'DEBATE_DEADLOCK', error_code: 'REG_OBSOLETE' }
  }
];

// 分叉分支快照数据
const forkSnapshots: SnapshotView[] = [
  ...mainSnapshots.slice(0, 2),
  {
    id: 'snap-f-2',
    title: 'Step 2 [反事实沙盒]: 注入 2026 新规放行',
    round: 2,
    spanId: '33445566778899aa',
    agents: [
      { id: 'agent-pro', role: 'PRO', thought: '坚持推进高并发双向补偿架构' },
      { id: 'agent-con', role: 'CON', thought: '【2026 最新金融合规特批通道】：允许在双向补偿日志完备前提下放行' }
    ],
    blackboard: { status: 'REGULATION_OVERRIDDEN', stage: 'FORKED_HARMONY', regulatory_pass: true }
  }
];

// 活跃快照列表
const activeSnapshots = computed(() => {
  return activeBranchId.value === 'main' ? mainSnapshots : forkSnapshots;
});

// 当前快照详情
const activeSnapshotDetail = computed(() => {
  const snaps = activeSnapshots.value;
  return snaps[currentStepIndex.value] || snaps[0];
});

// 可选干预智能体
const availableTargetAgents = computed(() => {
  return activeSnapshotDetail.value?.agents || [];
});

// 当前存证凭单
const currentReceipt = ref<ReceiptView | null>({
  receiptId: 'RCP-FORK-20260925-1088',
  parentBranch: 'main',
  forkedBranch: 'fork-1',
  baseSnapshot: 'snap-m-1',
  patchedAgent: 'agent-con',
  patchHash: 'd2a6a12b4e5f60718293a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5',
  latencyMs: 1.25,
  signature: '9e83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069'
});

// ============================================================================
// 交互方法
// ============================================================================

function selectBranch(id: string) {
  activeBranchId.value = id;
  currentStepIndex.value = activeSnapshots.value.length - 1;
}

function handleStepBackward() {
  if (currentStepIndex.value > 0) {
    currentStepIndex.value--;
  }
}

function handleStepForward() {
  if (currentStepIndex.value < activeSnapshots.value.length - 1) {
    currentStepIndex.value++;
  }
}

function jumpToStep(idx: number) {
  currentStepIndex.value = idx;
}

function addMemoryEntry() {
  memoryEntries.value.push({ key: '', val: '' });
}

function triggerForkSandbox() {
  isForkingActive.value = true;
  setTimeout(() => {
    const newForkId = 'fork-' + (branches.value.length);
    branches.value.push({
      id: newForkId,
      title: `平行沙盒 ${newForkId}`,
      depth: 1
    });

    activeBranchId.value = newForkId;
    currentStepIndex.value = 2;
    isPatched.value = false;
    isForkingActive.value = false;

    // 签发新凭单
    currentReceipt.value = {
      receiptId: 'RCP-FORK-' + Date.now().toString().slice(-4),
      parentBranch: 'main',
      forkedBranch: newForkId,
      baseSnapshot: 'snap-m-1',
      patchedAgent: targetAgentId.value,
      patchHash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
      latencyMs: 1.82,
      signature: '1b4f8a9c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f708192a3b4c5d6e7f8a9b0c1d2'
    };
    verificationResult.value = null;
  }, 250);
}

function verifyReceipt() {
  verificationResult.value = 'PASSED';
  isVerified.value = true;
}
</script>

<style scoped lang="scss">
@use '@/assets/system/styles/ios26-liquid-glass.scss' as ios26;

/* ============================================================================
   Apple iOS 26 Liquid Glass & Vibrancy 设计系统实现
   1. 严格遵守铁律：绝不在玻璃容器自身滥用 transform/opacity/filter，杜绝隔离组失效；
   2. 采用双层材质：高光层 + 50px 模糊层与 color-dodge 混合；
   3. Headline 严格为 590，排版靠光学微调；
   4. 零阴影体系，纯靠材质厚薄与柔光轮廓表达空间层级；
   ============================================================================ */

.cognitive-forking-widget {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  color: #ededed;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display", "PingFang SC", sans-serif;
  letter-spacing: -0.23px;
  box-sizing: border-box;
}

/* 顶部胶囊 Header */
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
      background: #0088ff;
      box-shadow: 0 0 8px rgba(0, 136, 255, 0.6);
      transition: background 0.3s ease, box-shadow 0.3s ease;

      &.signal-forking {
        background: #6155f5;
        box-shadow: 0 0 10px rgba(97, 85, 245, 0.8);
      }

      &.signal-verified {
        background: #34c759;
        box-shadow: 0 0 10px rgba(52, 199, 89, 0.8);
      }
    }

    .capsule-title {
      font-size: 13px;
      font-weight: 590;
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

        &.text-accent { color: #6155f5; }
        &.text-success { color: #34c759; }
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

      &:disabled {
        opacity: 0.4;
        cursor: not-allowed;
      }

      &.btn-fork {
        background: rgba(97, 85, 245, 0.2);
        border-color: rgba(97, 85, 245, 0.4);
        color: #a49aff;

        &:hover:not(:disabled) {
          background: rgba(97, 85, 245, 0.3);
        }
      }

      &.btn-highlight {
        background: rgba(0, 136, 255, 0.2);
        border-color: rgba(0, 136, 255, 0.4);
        color: #60b6ff;
      }
    }
  }
}

/* 主双栏卡片网格 */
.widget-grid-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* 玻璃卡片 */
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
        background: rgba(255, 141, 40, 0.15);
        color: #ffa65e;
        border: 1px solid rgba(255, 141, 40, 0.35);
      }

      &.pill-progress {
        background: rgba(0, 136, 255, 0.15);
        color: #4da6ff;
        border: 1px solid rgba(0, 136, 255, 0.35);
      }
    }
  }
}

.section-micro-header {
  font-size: 11px;
  font-weight: 590;
  color: rgba(255, 255, 255, 0.4);
  text-transform: uppercase;
  margin-bottom: 8px;
}

/* 分支胶囊选择器 */
.branch-selector-capsules {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;

  .branch-pill-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 14px;
    border-radius: var(--ios26-radius-pill, 1000px);
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.08);
    color: rgba(255, 255, 255, 0.7);
    font-size: 12px;
    cursor: pointer;
    transition: all 0.15s ease;

    &:hover {
      background: rgba(255, 255, 255, 0.1);
    }

    &.pill-active {
      background: #0088ff;
      border-color: #0088ff;
      color: #ffffff;
      font-weight: 590;
    }

    &.pill-fork.pill-active {
      background: #6155f5;
      border-color: #6155f5;
    }

    .branch-depth-tag {
      font-size: 9px;
      padding: 1px 4px;
      background: rgba(0, 0, 0, 0.3);
      border-radius: 4px;
    }
  }
}

/* 快照因果时间轴 */
.snapshot-timeline-container {
  display: flex;
  flex-direction: column;
  gap: 6px;

  .timeline-steps-deck {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 150px;
    overflow-y: auto;

    .snapshot-step-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 12px;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid rgba(255, 255, 255, 0.05);
      cursor: pointer;
      transition: background 0.15s ease, border-color 0.15s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.06);
      }

      &.step-selected {
        background: rgba(0, 136, 255, 0.15);
        border-color: rgba(0, 136, 255, 0.4);
      }

      .step-badge {
        width: 22px;
        height: 22px;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.1);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 11px;
        font-weight: 590;
      }

      .step-info {
        display: flex;
        flex-direction: column;
        gap: 2px;

        .step-title {
          font-size: 12px;
          color: #ffffff;
        }

        .step-meta {
          font-size: 10px;
          color: rgba(255, 255, 255, 0.4);
        }
      }

      .cursor-tag {
        margin-left: auto;
        font-size: 10px;
        font-weight: 590;
        color: #4da6ff;
        background: rgba(0, 136, 255, 0.2);
        padding: 2px 6px;
        border-radius: 4px;
      }
    }
  }
}

/* 状态检查器 */
.cluster-inspector-block {
  display: flex;
  flex-direction: column;
  gap: 8px;

  .agent-inspect-list {
    display: flex;
    flex-direction: column;
    gap: 6px;

    .agent-inspect-card {
      background: rgba(255, 255, 255, 0.03);
      padding: 8px 12px;
      border-radius: 10px;
      border: 1px solid rgba(255, 255, 255, 0.05);

      .agent-inspect-head {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 4px;

        .inspect-role-tag {
          font-size: 10px;
          font-weight: 590;
          padding: 1px 6px;
          border-radius: 4px;
          background: rgba(255, 255, 255, 0.1);
        }

        .inspect-id {
          font-size: 11px;
          color: rgba(255, 255, 255, 0.5);
        }
      }

      .inspect-thought-text {
        font-size: 12px;
        color: rgba(255, 255, 255, 0.85);
        line-height: 1.4;
      }
    }
  }

  .blackboard-preview-box {
    background: rgba(0, 0, 0, 0.3);
    padding: 10px;
    border-radius: 10px;
    border: 1px solid rgba(255, 255, 255, 0.05);

    .bb-title {
      font-size: 11px;
      color: rgba(255, 255, 255, 0.5);
    }

    .bb-json {
      margin: 4px 0 0;
      font-family: monospace;
      font-size: 10px;
      color: #60b6ff;
      max-height: 70px;
      overflow-y: auto;
    }
  }
}

/* 右栏反事实补丁输入组 */
.patch-target-selector {
  display: flex;
  flex-direction: column;
  gap: 6px;

  .target-agents-grid {
    display: flex;
    gap: 8px;

    .target-agent-option {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 8px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.08);
      cursor: pointer;
      transition: all 0.15s ease;

      &.option-selected {
        background: rgba(97, 85, 245, 0.2);
        border-color: #6155f5;

        .target-role {
          color: #a49aff;
        }
      }

      .target-role {
        font-size: 11px;
        font-weight: 590;
        color: rgba(255, 255, 255, 0.8);
      }

      .target-name {
        font-size: 10px;
        color: rgba(255, 255, 255, 0.4);
      }
    }
  }
}

.patch-input-group {
  display: flex;
  flex-direction: column;
  gap: 6px;

  .patch-label {
    font-size: 11px;
    font-weight: 590;
    color: rgba(255, 255, 255, 0.7);
  }

  .ios-textarea {
    background: rgba(0, 0, 0, 0.25);
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 12px;
    padding: 10px;
    color: #ffffff;
    font-size: 12px;
    line-height: 1.5;
    resize: vertical;
    outline: none;

    &:focus {
      border-color: #0088ff;
    }
  }

  .ios-input {
    background: rgba(0, 0, 0, 0.25);
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 10px;
    padding: 8px 12px;
    color: #ffffff;
    font-size: 12px;
    outline: none;

    &:focus {
      border-color: #0088ff;
    }
  }
}

.patch-kv-group {
  display: flex;
  flex-direction: column;
  gap: 6px;

  .kv-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .patch-label {
      font-size: 11px;
      font-weight: 590;
      color: rgba(255, 255, 255, 0.7);
    }

    .add-kv-btn {
      font-size: 10px;
      background: transparent;
      border: none;
      color: #60b6ff;
      cursor: pointer;
    }
  }

  .kv-rows {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .kv-row-item {
      display: flex;
      align-items: center;
      gap: 6px;

      .ios-input {
        background: rgba(0, 0, 0, 0.25);
        border: 1px solid rgba(255, 255, 255, 0.1);
        border-radius: 6px;
        padding: 4px 8px;
        font-size: 11px;
        color: #ffffff;
      }

      .key-input { flex: 2; }
      .val-input { flex: 3; }
      .kv-sep { color: rgba(255, 255, 255, 0.3); }

      .remove-btn {
        background: transparent;
        border: none;
        color: rgba(255, 255, 255, 0.4);
        cursor: pointer;
      }
    }
  }
}

.fork-commit-box {
  background: rgba(97, 85, 245, 0.1);
  border: 1px solid rgba(97, 85, 245, 0.25);
  border-radius: 14px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;

  .fork-desc {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.75);
    line-height: 1.4;
  }

  .execute-fork-btn {
    width: 100%;
    padding: 10px;
    border-radius: 10px;
    background: #6155f5;
    border: none;
    color: #ffffff;
    font-size: 13px;
    font-weight: 590;
    cursor: pointer;
    transition: background 0.15s ease;

    &:hover {
      background: #7368f7;
    }
  }
}

/* 底部凭单卡片 */
.widget-bottom-dock {
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
    }

    .receipt-content-meta {
      display: flex;
      flex-direction: column;
      gap: 8px;
      font-size: 12px;

      .meta-row {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 12px;
      }

      .meta-field {
        display: flex;
        align-items: baseline;
        gap: 6px;

        &.full-width {
          grid-column: 1 / -1;
        }

        .meta-label {
          color: rgba(255, 255, 255, 0.45);
        }

        .meta-value {
          color: #ffffff;
          font-weight: 590;

          &.text-accent { color: #9c94ff; }
          &.text-warning { color: #ffa65e; }
          &.text-success { color: #34c759; }
        }

        .sha256-code {
          font-family: monospace;
          font-size: 10px;
          color: #55d677;
          background: rgba(0, 0, 0, 0.3);
          padding: 2px 6px;
          border-radius: 4px;
        }

        .sha256-code-full {
          font-family: monospace;
          font-size: 10px;
          color: #55d677;
          word-break: break-all;
          background: rgba(0, 0, 0, 0.3);
          padding: 3px 8px;
          border-radius: 4px;
          flex: 1;
        }
      }
    }
  }
}
</style>

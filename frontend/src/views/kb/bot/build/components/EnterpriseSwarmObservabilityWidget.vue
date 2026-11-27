<template>
  <div class="phd-ios26-studio">
    <!-- 顶栏：Apple iOS 26 双层光学材质 + Headline 590 唯一字重 -->
    <header class="studio-header ios26-surface-thin">
      <div class="header-left">
        <span class="ios26-pill-tag">H-NSCDS · PhD Dissertation Observatory</span>
        <h2 class="ios26-text-headline studio-headline">
          超球面认知超网与持久化事务实时遥测中枢
        </h2>
      </div>
      <div class="header-right">
        <span class="connection-pill" :class="{ online: backendConnected }">
          <span class="signal-dot" :class="{ online: backendConnected }"></span>
          <span class="ios26-text-caption-1">
            {{ backendConnected ? '真实控制面在线' : '控制面未连接（零伪造离线基线）' }}
          </span>
        </span>
        <span class="connection-pill" :class="{ online: telemetry.hermesRemoteReachable }">
          <span class="signal-dot" :class="{ online: telemetry.hermesRemoteReachable }"></span>
          <span class="ios26-text-caption-1">
            {{ telemetry.hermesRemoteReachable ? 'Hermes 联邦在线' : '本地单点模式' }}
          </span>
        </span>
        <span class="connection-pill" :class="{ online: telemetry.nativeFfmLoaded }">
          <span class="signal-dot" :class="{ online: telemetry.nativeFfmLoaded }"></span>
          <span class="ios26-text-caption-1">
            {{ telemetry.nativeFfmLoaded ? 'Rust FFM SIMD 已激活' : 'JVM 向量回退模式' }}
          </span>
        </span>
        <button type="button" class="ios26-refresh-btn" :disabled="loading" @click="loadTelemetry">
          {{ loading ? '采样中...' : '刷新遥测' }}
        </button>
      </div>
    </header>

    <!-- 四大核心学术支柱监控卡片网格（Strict Apple iOS 26：双层 50px + 零投影 + 无层叠污染） -->
    <div class="pillars-grid">
      <!-- 第 3 章：S^1023 Meta-MDP 认知超网路由 -->
      <section class="ios26-widget-card">
        <div class="ios26-text-subheadline card-title">第 3 章 · S^1023 Meta-MDP 认知超网路由</div>
        <div class="metric-row">
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num num-blue">{{ telemetry.supernet.totalRoutingDecisions }}</span>
            <span class="ios26-text-caption-1 metric-cap">超网选路总决策</span>
          </div>
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num num-orange">{{ telemetry.supernet.totalReplansTriggered }}</span>
            <span class="ios26-text-caption-1 metric-cap">弦漂移重规划次数</span>
          </div>
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num num-green">{{ telemetry.supernet.totalEarlyExits }}</span>
            <span class="ios26-text-caption-1 metric-cap">置信早退 (EARLY_EXIT)</span>
          </div>
        </div>
        <div class="card-footer-line">
          <span class="ios26-text-footnote">
            当前算子: {{ telemetry.supernet.activeTopologyOperator }} · DeepSeek Effort: {{ telemetry.supernet.activeReasoningEffort }} · Δ²={{ formatNumber(telemetry.supernet.lastChordDriftSquared) }}
          </span>
        </div>
      </section>

      <!-- 第 4 章：双时态四层海马体持续记忆超图 -->
      <section class="ios26-widget-card">
        <div class="ios26-text-subheadline card-title">第 4 章 · 双时态海马体持续记忆超图</div>
        <div class="metric-row">
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num">{{ telemetry.hippocampal.vertexCount }}</span>
            <span class="ios26-text-caption-1 metric-cap">四层图谱顶点数</span>
          </div>
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num num-blue">{{ telemetry.hippocampal.activeEdgeCount }}</span>
            <span class="ios26-text-caption-1 metric-cap">当前活跃有效边</span>
          </div>
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num num-orange">{{ telemetry.hippocampal.coldRingCount }}</span>
            <span class="ios26-text-caption-1 metric-cap">冷备软隔离截断边</span>
          </div>
        </div>
        <div class="card-footer-line">
          <span class="ios26-text-footnote">
            {{ telemetry.hippocampal.nativeFfmLoaded ? 'Rust FFM 稀疏 CSR-PPR 已激活' : 'JVM 稀疏 CSR-PPR 回退' }} · 段落重置阻尼 β = {{ telemetry.hippocampal.pprDampingBeta }} · 零硬删除
          </span>
        </div>
      </section>

      <!-- 第 5 章：双向形式化思维 BFoT 守卫与 Code-Mode MCP -->
      <section class="ios26-widget-card">
        <div class="ios26-text-subheadline card-title">第 5 章 · DBM + LTLf 双向形式化守卫</div>
        <div class="metric-row">
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num">{{ telemetry.formalGuard.registeredFormulaCount }}</span>
            <span class="ios26-text-caption-1 metric-cap">形式化规约数</span>
          </div>
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num num-green">{{ telemetry.formalGuard.totalFormalChecks }}</span>
            <span class="ios26-text-caption-1 metric-cap">UNSAT 验签总次</span>
          </div>
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num num-red">{{ telemetry.formalGuard.blockedFormalCount }}</span>
            <span class="ios26-text-caption-1 metric-cap">MUC 反例拦截次</span>
          </div>
        </div>
        <div class="card-footer-line">
          <span class="ios26-text-footnote">
            合约执行 {{ telemetry.formalGuard.totalToolContractsExecuted }} 次 · 64-Token 前缀对齐 {{ ((telemetry.prefixCache.preservationRate || 0) * 100).toFixed(1) }}% ({{ telemetry.prefixCache.lastAligned64TokenBlocks }}块) · Code-Mode 发现 {{ telemetry.codeModeSandbox.totalDiscoveries }} 次
          </span>
        </div>
      </section>

      <!-- 第 6 章：嵌入式 PG Step Journal 与 Frontier 事务 -->
      <section class="ios26-widget-card">
        <div class="ios26-text-subheadline card-title">第 6 章 · PG Step Journal 与 Epoch-Frontier</div>
        <div class="metric-row">
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num">{{ telemetry.stepJournal.committedEntryCount }}</span>
            <span class="ios26-text-caption-1 metric-cap">幂等已提交步数</span>
          </div>
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num num-green">{{ telemetry.frontierTx.committedCount }}</span>
            <span class="ios26-text-caption-1 metric-cap">纪元可串行化提交</span>
          </div>
          <div class="metric-item">
            <span class="ios26-text-title-2 metric-num num-red">{{ telemetry.frontierTx.pendingHitlCount }}</span>
            <span class="ios26-text-caption-1 metric-cap">Plan-as-PR 挂起审批</span>
          </div>
        </div>
        <div class="card-footer-line">
          <span class="ios26-text-footnote">
            最新逻辑纪元 e = {{ telemetry.frontierTx.latestGlobalEpoch }} · 冷备事务环 = {{ telemetry.frontierTx.coldRingCount }}
          </span>
        </div>
      </section>
    </div>

    <footer class="studio-footer">
      <span class="ios26-text-caption-1">
        向量流形: {{ telemetry.embeddingManifold }}
      </span>
      <span class="ios26-text-caption-1">
        生成基线: {{ telemetry.llmBaseline }}
      </span>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { getPhdStudioTelemetry } from '@/api/audit';

const loading = ref(false);
const backendConnected = ref(false);

const createEmptyTelemetry = () => ({
  supernet: {
    totalRoutingDecisions: 0,
    totalReplansTriggered: 0,
    totalEarlyExits: 0,
    lastChordDriftSquared: 0,
    activeTopologyOperator: 'IDLE',
    activeReasoningEffort: 'NONE',
    averageExpectedUtility: 0,
  },
  stepJournal: {
    activeBranchScopeCount: 0,
    committedEntryCount: 0,
    totalJournalEntries: 0,
    baseSnapshotBranchCount: 0,
    coldRingCount: 0,
  },
  frontierTx: {
    activeBranchCount: 0,
    pendingHitlCount: 0,
    committedCount: 0,
    coldRingCount: 0,
    latestGlobalEpoch: 0,
  },
  hippocampal: {
    vertexCount: 0,
    activeEdgeCount: 0,
    historicalEdgeCount: 0,
    coldRingCount: 0,
    pprDampingBeta: 0.05,
    nativeFfmLoaded: false,
    nativeLibPath: '',
  },
  formalGuard: {
    registeredFormulaCount: 0,
    totalFormalChecks: 0,
    blockedFormalCount: 0,
    preconditionGuards: 0,
    totalToolContractsExecuted: 0,
  },
  prefixCache: {
    totalAudits: 0,
    preservedCount: 0,
    driftedCount: 0,
    preservationRate: 0,
    lastAligned64TokenBlocks: 0,
    lastPrefixHash: '',
  },
  codeModeSandbox: {
    staticMetaToolCount: 0,
    deferredExecutorCount: 0,
    totalDiscoveries: 0,
    totalPlanExecutions: 0,
    totalDeferredToolCalls: 0,
    blockedScriptCount: 0,
  },
  nativeFfmLoaded: false,
  hermesRemoteReachable: false,
  embeddingManifold: 'S^1023 (Qwen text-embedding-v4, L2=1.0)',
  llmBaseline: 'DeepSeek API (deepseek-flash, 64-token prefix invariant)',
});

const telemetry = ref(createEmptyTelemetry());

function formatNumber(val) {
  const num = Number(val || 0);
  return Number.isFinite(num) ? num.toFixed(3) : '0.000';
}

async function loadTelemetry() {
  loading.value = true;
  try {
    const res = await getPhdStudioTelemetry();
    const data = res?.data || res;
    const base = createEmptyTelemetry();
    if (data && data.supernet) {
      telemetry.value = {
        ...base,
        ...data,
        supernet: { ...base.supernet, ...(data.supernet || {}) },
        stepJournal: { ...base.stepJournal, ...(data.stepJournal || {}) },
        frontierTx: { ...base.frontierTx, ...(data.frontierTx || {}) },
        hippocampal: { ...base.hippocampal, ...(data.hippocampal || {}) },
        formalGuard: { ...base.formalGuard, ...(data.formalGuard || {}) },
        prefixCache: { ...base.prefixCache, ...(data.prefixCache || {}) },
        codeModeSandbox: { ...base.codeModeSandbox, ...(data.codeModeSandbox || {}) },
        nativeFfmLoaded: Boolean(data.nativeFfmLoaded || data.hippocampal?.nativeFfmLoaded),
        hermesRemoteReachable: Boolean(data.hermesRemoteReachable),
      };
      backendConnected.value = true;
    } else {
      backendConnected.value = false;
    }
  } catch {
    backendConnected.value = false;
    telemetry.value = createEmptyTelemetry();
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadTelemetry();
});
</script>

<style scoped lang="scss">
.phd-ios26-studio {
  width: 100%;
  padding: 20px;
  border-radius: 34px;
  background: #f2f2f7;
  color: #000000;
  font-family: var(--ios26-font-family);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ios26-surface-thin {
  position: relative;
  border-radius: 28px;
  overflow: hidden;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  background: transparent;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 1;
    border-radius: inherit;
    background: rgba(255, 255, 255, 0.05);
    pointer-events: none;
  }

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 2;
    border-radius: inherit;
    backdrop-filter: blur(50px);
    -webkit-backdrop-filter: blur(50px);
    background: rgba(255, 255, 255, 0.4);
    mix-blend-mode: color-dodge;
    pointer-events: none;
  }

  > :where(*) {
    position: relative;
    z-index: 3;
  }
}

.studio-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px 14px;
  padding: 16px 20px;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ios26-pill-tag {
  display: inline-block;
  width: fit-content;
  padding: 2px 10px;
  border-radius: 1000px;
  background: rgba(0, 136, 255, 0.12);
  color: #0088ff;
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0.06px;
  white-space: nowrap;
  flex-shrink: 0;
}

.studio-headline {
  margin: 0;
  font-weight: 590;
  color: #000000;
}

.header-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.connection-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 1000px;
  background: rgba(255, 255, 255, 0.72);
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  white-space: nowrap;
  flex-shrink: 0;
}

.signal-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ff8d28;
  flex-shrink: 0;

  &.online {
    background: #34c759;
  }
}

.ios26-refresh-btn {
  padding: 6px 14px;
  border-radius: 1000px;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  background: #0088ff;
  color: #ffffff;
  font-size: 13px;
  font-weight: 400;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}

.pillars-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

@media (max-width: 900px) {
  .pillars-grid {
    grid-template-columns: 1fr;
  }
}

/* iOS 26 双层 50px + color-dodge 卡片，绝对零投影 */
.ios26-widget-card {
  position: relative;
  border-radius: 28px;
  overflow: hidden;
  padding: 18px 20px;
  background: transparent;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  gap: 14px;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 1;
    border-radius: inherit;
    background: rgba(255, 255, 255, 0.25);
    mix-blend-mode: plus-lighter;
    pointer-events: none;
  }

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 2;
    border-radius: inherit;
    backdrop-filter: blur(50px);
    -webkit-backdrop-filter: blur(50px);
    background: rgba(255, 255, 255, 0.6);
    mix-blend-mode: color-dodge;
    pointer-events: none;
  }

  > :where(*) {
    position: relative;
    z-index: 3;
  }
}

.card-title {
  color: #3d3d3d;
  font-weight: 400;
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-num {
  font-weight: 400;
  color: #000000;

  &.num-blue { color: #0088ff; }
  &.num-green { color: #34c759; }
  &.num-orange { color: #ff8d28; }
  &.num-red { color: #ff383c; }
}

.metric-cap {
  color: rgba(60, 60, 67, 0.6);
  font-weight: 400;
}

.card-footer-line {
  padding-top: 10px;
  border-top: 0.5px solid rgba(0, 0, 0, 0.12);
  color: #3d3d3d;
  font-weight: 400;
}

.studio-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  color: rgba(60, 60, 67, 0.6);
  font-weight: 400;
}
</style>

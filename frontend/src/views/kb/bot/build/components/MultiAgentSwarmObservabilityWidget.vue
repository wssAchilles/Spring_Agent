<template>
  <div class="multi-agent-swarm-widget">
    <!-- Header: 标题与系统状态胶囊 -->
    <div class="widget-header">
      <div class="widget-title">
        <span class="icon-sparkle">🌐</span>
        <span class="title-text">多智能体协同与认知基础设施监控中枢</span>
      </div>
      <div class="header-tools">
        <div class="status-capsule">
          <span class="dot-indicator" :class="{ offline: !backendConnected }"></span>
          <span class="status-label">
            {{ backendConnected ? 'H-NSCDS 实态控制面已同步' : '未连接实时遥测（零假数据）' }}
          </span>
        </div>
        <button class="refresh-pill-btn" :disabled="loading" @click="loadLiveTelemetry">
          {{ loading ? '采样中...' : '刷新实态' }}
        </button>
        <button class="close-pill-btn" title="关闭" @click="$emit('close')">✕</button>
      </div>
    </div>

    <!-- 4 大核心支柱监控网格卡片 -->
    <div class="metrics-grid">
      <!-- 支柱 1: S^1023 认知超网路由与决策算子 -->
      <div class="glass-card">
        <div class="card-header">
          <span class="card-tag">S^1023 认知超网决策链</span>
          <span class="hop-badge">决策 {{ supernetDecisions }} 次</span>
        </div>
        <div class="handoff-lane">
          <template v-if="activeChainNodes.length > 0">
            <div
              v-for="(agent, idx) in activeChainNodes"
              :key="idx"
              class="chain-node"
            >
              <span class="agent-chip" :class="{ 'is-active': idx === activeChainNodes.length - 1 }">
                {{ agent }}
              </span>
              <span v-if="idx < activeChainNodes.length - 1" class="chain-arrow">→</span>
            </div>
          </template>
          <span v-else class="empty-lane-hint">待发生真实认知超网选路</span>
        </div>
        <div class="card-footer">
          <span class="f-label">重规划 / 早退统计:</span>
          <span class="f-val text-green">{{ supernetReplans }} / {{ supernetEarlyExits }}</span>
        </div>
      </div>

      <!-- 支柱 2: DeepSeek 64-Token 缓存块量化对齐 -->
      <div class="glass-card">
        <div class="card-header">
          <span class="card-tag">64-Token 动态前缀树</span>
          <span class="rate-badge">{{ cacheReuseRate }}% 复用率</span>
        </div>
        <div class="cache-progress-container">
          <div class="cache-blocks-bar">
            <div
              v-for="b in Math.max(12, alignedBlocks)"
              :key="b"
              class="cache-block"
              :class="{ 'is-hit': b <= alignedBlocks }"
              :title="`Block ${b}: 64 Tokens`"
            ></div>
          </div>
        </div>
        <div class="card-footer">
          <span class="f-label">实测对齐块 / 审计数:</span>
          <span class="f-val font-mono">{{ alignedBlocks }} Blocks ({{ prefixAudits }} 轮审计)</span>
        </div>
      </div>

      <!-- 支柱 3: 双时态海马体稀疏超图状态 -->
      <div class="glass-card">
        <div class="card-header">
          <span class="card-tag">双时态海马体超图</span>
          <span class="density-badge">{{ nativeFfmLoaded ? 'Rust FFM SIMD' : 'JVM 回退' }}</span>
        </div>
        <div class="spectral-stats">
          <div class="stat-col">
            <span class="num-val text-slate">{{ hippoVertexCount }}</span>
            <span class="num-desc">知识顶点数</span>
          </div>
          <div class="stat-arrow">⟹</div>
          <div class="stat-col">
            <span class="num-val text-blue">{{ hippoActiveEdges }}</span>
            <span class="num-desc">活跃因果边</span>
          </div>
          <div class="stat-col">
            <span class="num-val text-amber">{{ hippoColdCount }}</span>
            <span class="num-desc">冷备软隔离</span>
          </div>
        </div>
        <div class="card-footer">
          <span class="f-label">千问几何约束:</span>
          <span class="f-val font-mono">||v||₂ = 1.0000 (S^1023)</span>
        </div>
      </div>

      <!-- 支柱 4: Frontier 事务与 Code-Mode MCP 网关 -->
      <div class="glass-card">
        <div class="card-header">
          <span class="card-tag">Frontier 事务与 Code-Mode</span>
          <span class="circuit-badge" :class="frontierPendingCount > 0 ? 'is-half' : 'is-closed'">
            {{ frontierPendingCount > 0 ? `${frontierPendingCount} 待审批` : 'NOMINAL' }}
          </span>
        </div>
        <div class="quota-concurrency">
          <div class="waterline-row">
            <span class="w-tenant">Code-Mode 延迟工具发现: {{ codeModeDiscoveries }}</span>
            <span class="w-ratio font-mono">{{ frontierCommittedCount }} 提交 / e={{ frontierEpoch }}</span>
          </div>
          <div class="waterline-bar-bg">
            <div class="waterline-bar-fill" :style="{ width: frontierActivityPercent + '%' }"></div>
          </div>
        </div>
        <div class="card-footer">
          <span class="f-label">PG Step Journal 提交:</span>
          <span class="f-val text-green">{{ stepJournalCommitted }} 步</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from "vue";
import { getPhdStudioTelemetry } from "@/api/audit";

defineEmits(["close"]);

const loading = ref(false);
const backendConnected = ref(false);
const live = ref(null);

const supernetDecisions = computed(() => live.value?.supernet?.totalRoutingDecisions ?? 0);
const supernetReplans = computed(() => live.value?.supernet?.totalReplansTriggered ?? 0);
const supernetEarlyExits = computed(() => live.value?.supernet?.totalEarlyExits ?? 0);
const activeChainNodes = computed(() => {
  const op = live.value?.supernet?.activeTopologyOperator;
  const effort = live.value?.supernet?.activeReasoningEffort;
  if (!op || op === "IDLE") return [];
  return ["SupernetRouter", op, `DeepSeek(${effort || "NONE"})`];
});

const alignedBlocks = computed(() => live.value?.prefixCache?.lastAligned64TokenBlocks ?? 0);
const prefixAudits = computed(() => live.value?.prefixCache?.totalAudits ?? 0);
const cacheReuseRate = computed(() => (((live.value?.prefixCache?.preservationRate) ?? 0) * 100).toFixed(1));

const hippoVertexCount = computed(() => live.value?.hippocampal?.vertexCount ?? 0);
const hippoActiveEdges = computed(() => live.value?.hippocampal?.activeEdgeCount ?? 0);
const hippoColdCount = computed(() => live.value?.hippocampal?.coldRingCount ?? 0);
const nativeFfmLoaded = computed(() => Boolean(live.value?.nativeFfmLoaded || live.value?.hippocampal?.nativeFfmLoaded));

const frontierPendingCount = computed(() => live.value?.frontierTx?.pendingHitlCount ?? 0);
const frontierCommittedCount = computed(() => live.value?.frontierTx?.committedCount ?? 0);
const frontierEpoch = computed(() => live.value?.frontierTx?.latestGlobalEpoch ?? 0);
const codeModeDiscoveries = computed(() => live.value?.codeModeSandbox?.totalDiscoveries ?? 0);
const stepJournalCommitted = computed(() => live.value?.stepJournal?.committedEntryCount ?? 0);

const frontierActivityPercent = computed(() => {
  const total = frontierCommittedCount.value + frontierPendingCount.value;
  if (total === 0) return 0;
  return Math.min(100, Math.round((frontierCommittedCount.value / total) * 100));
});

async function loadLiveTelemetry() {
  loading.value = true;
  try {
    const res = await getPhdStudioTelemetry();
    const data = res?.data || res;
    if (data && data.supernet) {
      live.value = data;
      backendConnected.value = true;
    } else {
      live.value = null;
      backendConnected.value = false;
    }
  } catch {
    live.value = null;
    backendConnected.value = false;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadLiveTelemetry();
});
</script>

<style scoped lang="scss">
.multi-agent-swarm-widget {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  background: rgba(248, 250, 252, 0.65);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 16px;
  margin-top: 14px;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, sans-serif;
}

.widget-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;

  .widget-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 590; // Apple iOS 26 Headline 590 字重
    color: #0f172a;

    .icon-sparkle {
      font-size: 16px;
    }
  }

  .header-tools {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .status-capsule {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 3px 10px;
    background: rgba(241, 245, 249, 0.85);
    border: 0.5px solid rgba(203, 213, 225, 0.7);
    border-radius: 20px;
    font-size: 11px;
    color: #334155;
    white-space: nowrap;
    flex-shrink: 0;

    .dot-indicator {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #10b981;
      box-shadow: 0 0 6px rgba(16, 185, 129, 0.6);
      flex-shrink: 0;

      &.offline {
        background: #f59e0b;
        box-shadow: none;
      }
    }
  }

  .refresh-pill-btn {
    padding: 4px 12px;
    border-radius: 1000px;
    border: 0.5px solid rgba(2, 132, 199, 0.3);
    background: #0284c7;
    color: #ffffff;
    font-size: 11px;
    cursor: pointer;
    white-space: nowrap;
    flex-shrink: 0;
  }

  .close-pill-btn {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    border: 0.5px solid rgba(148, 163, 184, 0.4);
    background: rgba(241, 245, 249, 0.9);
    color: #334155;
    font-size: 11px;
    cursor: pointer;
    flex-shrink: 0;
  }
}

.empty-lane-hint {
  font-size: 11px;
  color: #64748b;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.glass-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(226, 232, 240, 0.7);
  border-radius: 12px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .card-tag {
      font-size: 12px;
      font-weight: 590;
      color: #1e293b;
    }

    .hop-badge,
    .rate-badge,
    .density-badge,
    .circuit-badge {
      font-size: 11px;
      font-weight: 500;
      padding: 2px 7px;
      border-radius: 10px;
      background: rgba(241, 245, 249, 0.9);
      color: #475569;
    }

    .rate-badge {
      background: rgba(14, 165, 233, 0.12);
      color: #0284c7;
      font-weight: 600;
    }

    .circuit-badge {
      &.is-closed {
        background: rgba(16, 185, 129, 0.12);
        color: #059669;
      }
      &.is-half {
        background: rgba(245, 158, 11, 0.12);
        color: #d97706;
      }
      &.is-open {
        background: rgba(239, 68, 68, 0.12);
        color: #dc2626;
      }
    }
  }

  .handoff-lane {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;

    .chain-node {
      display: flex;
      align-items: center;
      gap: 6px;

      .agent-chip {
        font-size: 11px;
        font-family: monospace;
        padding: 3px 8px;
        background: #f1f5f9;
        border-radius: 6px;
        color: #334155;

        &.is-active {
          background: #e0f2fe;
          color: #0369a1;
          font-weight: 600;
          border: 1px solid #bae6fd;
        }
      }

      .chain-arrow {
        color: #94a3b8;
        font-size: 11px;
      }
    }
  }

  .cache-progress-container {
    .cache-blocks-bar {
      display: flex;
      gap: 3px;
      flex-wrap: wrap;

      .cache-block {
        width: 8px;
        height: 12px;
        border-radius: 2px;
        background: #e2e8f0;

        &.is-hit {
          background: #0284c7;
        }
      }
    }
  }

  .spectral-stats {
    display: flex;
    align-items: center;
    justify-content: space-around;
    padding: 4px 0;

    .stat-col {
      display: flex;
      flex-direction: column;
      align-items: center;

      .num-val {
        font-size: 15px;
        font-weight: 600;
        font-family: monospace;
      }

      .num-desc {
        font-size: 10px;
        color: #64748b;
      }
    }

    .stat-arrow {
      color: #94a3b8;
      font-size: 12px;
    }
  }

  .quota-concurrency {
    display: flex;
    flex-direction: column;
    gap: 6px;

    .waterline-row {
      display: flex;
      justify-content: space-between;
      font-size: 11px;
      color: #475569;
    }

    .waterline-bar-bg {
      height: 6px;
      background: #e2e8f0;
      border-radius: 3px;
      overflow: hidden;

      .waterline-bar-fill {
        height: 100%;
        background: #3b82f6;
        border-radius: 3px;
        transition: width 0.3s ease;
      }
    }
  }

  .card-footer {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
    padding-top: 6px;
    border-top: 0.5px solid rgba(226, 232, 240, 0.6);

    .f-label {
      color: #64748b;
    }

    .f-val {
      font-weight: 500;
    }
  }
}

.text-green { color: #059669; }
.text-slate { color: #475569; }
.text-blue { color: #0284c7; }
.text-amber { color: #d97706; }
.font-mono { font-family: monospace; }
</style>

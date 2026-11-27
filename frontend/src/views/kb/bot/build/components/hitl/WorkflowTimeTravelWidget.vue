<template>
  <div class="ios26-light-time-travel-container">
    <!-- 1. Apple iOS 26 顶部纯净高透液态胶囊 Header (Pill 1000px) -->
    <header class="capsule-nav-header">
      <div class="header-left">
        <div class="aurora-pulse-dot" :class="activeSpecBranch ? 'pulse-speculating' : 'pulse-stable'"></div>
        <div class="header-title-group">
          <div class="headline-title">Step Journal 时间旅行与 Frontier 事务中枢</div>
          <div class="sub-headline">PG 预写步骤日志 · DeltaChannel 零幽灵分叉重构</div>
        </div>
        <div class="version-chip font-mono">
          <span class="chip-dot"></span>
          活跃超步: S{{ versionHistory.length > 0 ? activeVersion : 0 }}
        </div>
      </div>

      <div class="header-right">
        <div class="telemetry-capsule" title="双时态海马体图谱实时顶点与边计数">
          <span class="capsule-icon">🧠</span>
          <span class="capsule-key">海马体图谱</span>
          <span class="capsule-val font-mono highlight-blue">V={{ hippoVertexCount }} / E={{ hippoEdgeCount }} / Cold={{ hippoColdCount }}</span>
        </div>

        <div class="telemetry-capsule" title="DeepSeek 64-Token 前缀缓存稳态保留率">
          <span class="capsule-icon">⚡</span>
          <span class="capsule-key">Prefix 复用</span>
          <span class="capsule-val font-mono highlight-green">{{ (promptCacheHitRate * 100).toFixed(1) }}%</span>
        </div>

        <div class="telemetry-capsule" title="PG Step Journal 累计持久化幂等步骤数">
          <span class="capsule-icon">🌿</span>
          <span class="capsule-key">PG 步数</span>
          <span class="capsule-val font-mono">{{ committedStepCount }} 步</span>
        </div>

        <div class="telemetry-capsule" title="S^1023 认知超网累计路由决策">
          <span class="capsule-icon">🧭</span>
          <span class="capsule-key">超网决策</span>
          <span class="capsule-val font-mono highlight-green">{{ totalSupernetDecisions }} 次</span>
        </div>

        <button class="close-capsule-btn" title="关闭中枢" @click="$emit('close')">
          <span>✕</span>
        </button>
      </div>
    </header>

    <!-- 2. 主视口双栏布局：左栏 Step Journal 因果版本轴，右栏 Frontier 审批与时间旅行分叉中枢 -->
    <main class="grid-main-workspace">
      <!-- 左栏：PG Step Journal 真实增量步骤轨道 -->
      <section class="glass-material-card left-timeline-panel">
        <div class="panel-section-header">
          <div class="section-title-wrap">
            <h3 class="section-headline">Step Journal 增量因果轨迹 ({{ currentBranchId }})</h3>
            <span class="section-caption">基于 PG 预写增量通道 · 支持任意历史超步无损分叉</span>
          </div>
          <span class="status-badge-capsule">
            {{ versionHistory.length }} 条实态记录
          </span>
        </div>

        <div class="journal-query-strip">
          <input
            v-model="inputExecutionId"
            type="text"
            class="journal-query-input font-mono"
            placeholder="输入 executionId (例如 debug-bot-3-...)"
          />
          <input
            v-model="currentBranchId"
            type="text"
            class="journal-branch-input font-mono"
            placeholder="分支 (main)"
          />
          <button
            type="button"
            class="apple-liquid-btn btn-query-journal"
            :disabled="loading"
            @click="refreshAllData"
          >
            {{ loading ? '拉取中...' : '拉取实态日志' }}
          </button>
        </div>

        <div v-if="versionHistory.length > 0" class="dag-timeline-scroll">
          <div
            v-for="(item, idx) in versionHistory"
            :key="`${item.idempotencyKey}-${item.version}`"
            class="dag-version-card"
            :class="{
              'is-active-head': item.version === activeVersion,
              'is-selected-revert': item.version === selectedRevertTarget
            }"
            @click="selectVersion(item.version)"
          >
            <div class="dag-axis-col">
              <div class="axis-node-dot" :class="{ 'dot-active': item.version === activeVersion }"></div>
              <div v-if="idx < versionHistory.length - 1" class="axis-line"></div>
            </div>

            <div class="dag-card-body">
              <div class="dag-card-top">
                <div class="version-badge font-mono">S{{ item.version }}</div>
                <div class="node-id-text font-mono">{{ item.nodeId }}</div>
                <div class="memory-paging-chip font-mono page-recall">
                  {{ item.branchId }}
                </div>
                <div class="status-indicator-tag" :class="item.version === activeVersion ? 'tag-head' : 'tag-snapshot'">
                  {{ item.status || 'COMMITTED' }}
                </div>
              </div>

              <div class="delta-diff-viewer font-mono">
                <div v-for="(v, k) in item.deltaState" :key="k" class="delta-diff-row">
                  <span class="delta-prefix">+</span>
                  <span class="delta-param-key">{{ k }}:</span>
                  <span class="delta-param-val">{{ formatValue(v) }}</span>
                </div>
                <div v-if="Object.keys(item.deltaState || {}).length === 0" class="delta-diff-row">
                  <span class="delta-param-key">无增量载荷变更</span>
                </div>
              </div>

              <div class="dag-card-bottom font-mono">
                <span class="hash-tag">Idem: {{ truncateHash(item.idempotencyKey) }}</span>
                <span class="cache-tag">Fence: {{ item.fencingToken }}</span>
                <span class="time-tag">{{ formatTime(item.timestamp) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="empty-spec-standby" style="margin-top: 24px;">
          <span class="standby-icon">📜</span>
          <span class="standby-text">
            {{ inputExecutionId ? `轨迹 [${inputExecutionId}] 分支 [${currentBranchId}] 暂无 Step Journal 记录` : '暂无选中轨迹。请先执行一次工作流调试，或上方输入已有的 executionId 点击拉取。' }}
          </span>
        </div>
      </section>

      <!-- 右栏：Frontier 审批沙盒与时间旅行分叉中枢 -->
      <section class="right-control-panel">
        <!-- 模块 A: Frontier Epoch-Serializable 挂起审批区 -->
        <div class="glass-material-card spec-sandbox-card">
          <div class="panel-section-header">
            <div class="section-title-wrap">
              <h3 class="section-headline">Frontier 事务与 Plan-as-PR 审批</h3>
              <span class="section-caption">不可逆副作用零泄漏 · 真实后端挂起队列轮询</span>
            </div>
            <div class="gate-status-pill" :class="activeSpecBranch ? 'pill-gate-block' : 'pill-gate-pass'">
              <span class="gate-dot"></span>
              {{ activeSpecBranch ? '存在待决不可逆工单' : '无待审挂起动作' }}
            </div>
          </div>

          <div class="spec-telemetry-grid">
            <div class="telemetry-metric-box">
              <span class="metric-title">纪元已提交事务</span>
              <div class="metric-value-row">
                <span class="metric-val highlight-green font-mono">{{ frontierCommittedCount }}</span>
                <span class="metric-sub">可串行化提交</span>
              </div>
            </div>

            <div class="telemetry-metric-box">
              <span class="metric-title">挂起待审批数</span>
              <div class="metric-value-row">
                <span class="metric-val highlight-blue font-mono">{{ frontierPendingCount }}</span>
                <span class="metric-sub">HITL 队列</span>
              </div>
            </div>

            <div class="telemetry-metric-box">
              <span class="metric-title">当前全局纪元</span>
              <div class="metric-value-row">
                <span class="metric-val font-mono">e={{ frontierLatestEpoch }}</span>
                <span class="metric-sub">单调递增</span>
              </div>
            </div>
          </div>

          <div v-if="activeSpecBranch" class="spec-branch-glass-banner">
            <div class="branch-meta-row">
              <div class="branch-icon-tag">⚡ 挂起工单</div>
              <div class="branch-title font-mono">{{ activeSpecBranch.branchName }}</div>
            </div>
            <p class="branch-summary-text">
              {{ activeSpecBranch.reason || `ApprovalKey: ${activeSpecBranch.approvalKey} (${activeSpecBranch.sideEffectTier || 'IRREVERSIBLE'})` }}
            </p>

            <div class="branch-action-buttons">
              <button
                class="apple-liquid-btn btn-commit"
                :disabled="actionLoading"
                @click="handleCommitSpeculation"
              >
                <span class="btn-symbol">✓</span>
                <span>批准并放行事务</span>
              </button>
              <button
                class="apple-liquid-btn btn-discard"
                :disabled="actionLoading"
                @click="handleDiscardSpeculation"
              >
                <span class="btn-symbol">✕</span>
                <span>驳回并冷备隔离</span>
              </button>
            </div>
          </div>

          <div v-else class="empty-spec-standby">
            <span class="standby-icon">🌿</span>
            <span class="standby-text">当前无待决的 Frontier 不可逆审批工单，引擎处于稳态守候</span>
          </div>
        </div>

        <!-- 模块 B: 时间旅行因果分叉中枢 -->
        <div class="glass-material-card time-travel-slider-card">
          <div class="panel-section-header">
            <div class="section-title-wrap">
              <h3 class="section-headline">时间旅行因果分叉 (Incremental Fork)</h3>
              <span class="section-caption">真实调用 PG Step Journal 构建独立零污染派生分支</span>
            </div>
            <div class="latency-budget-pill font-mono">
              {{ lastForkLatencyMs !== null ? `实测分叉耗时 ${lastForkLatencyMs.toFixed(2)}ms` : '实测耗时待采样' }}
            </div>
          </div>

          <div class="slider-interactive-workbench">
            <div class="slider-meta-header font-mono">
              <div class="target-version-display">
                分叉起点: <span class="v-num">S{{ selectedRevertTarget }}</span>
              </div>
              <div class="jump-calc-display">
                预估跳数: <span class="highlight-blue">{{ estimatedJumpSteps }} 步</span>
              </div>
            </div>

            <div class="apple-slider-track-wrap">
              <input
                v-model.number="selectedRevertTarget"
                type="range"
                :min="minSuperstep"
                :max="maxSuperstep"
                :disabled="versionHistory.length === 0"
                class="apple-lux-slider"
              />
              <div class="slider-scale-ticks font-mono">
                <span>起始超步 S{{ minSuperstep }}</span>
                <span>最新超步 S{{ maxSuperstep }}</span>
              </div>
            </div>

            <div class="revert-action-footer">
              <div class="revert-caution-note">
                <span class="caution-icon">ℹ</span>
                <span>从指定超步发起时间旅行分叉将在服务端生成全新派生分支，与原分支完全隔离，绝不污染主干历史。</span>
              </div>
              <button
                class="apple-liquid-btn btn-revert-action"
                :disabled="!inputExecutionId || versionHistory.length === 0 || actionLoading"
                @click="handleExecuteTimeTravel"
              >
                <span class="btn-symbol">↺</span>
                <span>执行真实时间旅行分叉</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 模块 C: 实态交互操作回执凭单 -->
        <div class="glass-material-card audit-receipt-card">
          <div class="panel-section-header">
            <div class="section-title-wrap">
              <h3 class="section-headline">服务端实态操作回执 (Execution Receipt)</h3>
              <span class="section-caption">来自 /hermes/phd-studio 真实响应与实测耗时</span>
            </div>
            <div class="receipt-verified-badge">
              <span class="shield-icon">🛡️</span>
              <span>{{ latestReceipt ? '实态已核验' : '待触发实态操作' }}</span>
            </div>
          </div>

          <div v-if="latestReceipt" class="wallet-pass-body font-mono">
            <div class="pass-row">
              <span class="pass-key">回执标识</span>
              <span class="pass-val">{{ latestReceipt.receiptId }}</span>
            </div>
            <div class="pass-row">
              <span class="pass-key">分叉/动作说明</span>
              <span class="pass-val">{{ latestReceipt.summary }}</span>
            </div>
            <div class="pass-row">
              <span class="pass-key">实测耗时</span>
              <span class="pass-val highlight-blue">{{ latestReceipt.latencyMs.toFixed(2) }} ms</span>
            </div>
            <div class="pass-row pass-signature-row">
              <span class="pass-key">重构状态键数</span>
              <span class="pass-val signature-code">{{ latestReceipt.stateKeysSummary }}</span>
            </div>
          </div>

          <div v-else class="empty-spec-standby">
            <span class="standby-text">执行时间旅行分叉或审批决断后，此处将展示真实后端返回回执</span>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getPhdStudioTelemetry,
  getPhdJournalTimeline,
  forkPhdJournalBranch,
  getPendingApprovals,
  decideFrontierTransaction
} from '@/api/audit'

const props = defineProps({
  executionId: { type: String, default: '' }
})

defineEmits(['close'])

const loading = ref(false)
const actionLoading = ref(false)
const inputExecutionId = ref(props.executionId || '')
const currentBranchId = ref('main')
const telemetrySnapshot = ref(null)
const versionHistory = ref([])
const activeSpecBranch = ref(null)
const latestReceipt = ref(null)
const lastForkLatencyMs = ref(null)
const selectedRevertTarget = ref(1)

const minSuperstep = computed(() => {
  if (versionHistory.value.length === 0) return 1
  return Math.min(...versionHistory.value.map((v) => v.version))
})

const maxSuperstep = computed(() => {
  if (versionHistory.value.length === 0) return 1
  return Math.max(...versionHistory.value.map((v) => v.version))
})

const activeVersion = computed(() => maxSuperstep.value)

const estimatedJumpSteps = computed(() => {
  const diff = Math.abs(activeVersion.value - selectedRevertTarget.value)
  if (diff === 0) return 0
  return Math.ceil(Math.log2(diff + 1))
})

const hippoVertexCount = computed(() => telemetrySnapshot.value?.hippocampal?.vertexCount ?? 0)
const hippoEdgeCount = computed(() => telemetrySnapshot.value?.hippocampal?.activeEdgeCount ?? 0)
const hippoColdCount = computed(() => telemetrySnapshot.value?.hippocampal?.coldRingCount ?? 0)
const promptCacheHitRate = computed(() => telemetrySnapshot.value?.prefixCache?.preservationRate ?? 0)
const committedStepCount = computed(() => telemetrySnapshot.value?.stepJournal?.committedEntryCount ?? 0)
const totalSupernetDecisions = computed(() => telemetrySnapshot.value?.supernet?.totalRoutingDecisions ?? 0)
const frontierCommittedCount = computed(() => telemetrySnapshot.value?.frontierTx?.committedCount ?? 0)
const frontierPendingCount = computed(() => telemetrySnapshot.value?.frontierTx?.pendingHitlCount ?? 0)
const frontierLatestEpoch = computed(() => telemetrySnapshot.value?.frontierTx?.latestGlobalEpoch ?? 0)

function truncateHash(hash) {
  if (!hash) return '--'
  if (hash.length <= 16) return hash
  return hash.substring(0, 8) + '...' + hash.substring(hash.length - 6)
}

function formatValue(v) {
  if (typeof v === 'object' && v !== null) return JSON.stringify(v)
  return String(v)
}

function formatTime(ts) {
  const d = new Date(ts || Date.now())
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`
}

function selectVersion(v) {
  selectedRevertTarget.value = v
}

async function loadTelemetryAndApprovals() {
  try {
    const res = await getPhdStudioTelemetry()
    telemetrySnapshot.value = res?.data || res || null
  } catch {
    telemetrySnapshot.value = null
  }
  try {
    const appRes = await getPendingApprovals()
    const list = appRes?.data || appRes || []
    if (Array.isArray(list) && list.length > 0) {
      const first = list[0]
      activeSpecBranch.value = {
        approvalKey: first.approvalKey,
        branchName: first.nodeId || first.approvalKey || 'frontier_pending_action',
        reason: first.reason,
        sideEffectTier: first.sideEffectTier
      }
    } else {
      activeSpecBranch.value = null
    }
  } catch {
    activeSpecBranch.value = null
  }
}

async function loadJournalEntries() {
  const execId = (inputExecutionId.value || '').trim()
  if (!execId) {
    versionHistory.value = []
    return
  }
  const res = await getPhdJournalTimeline(execId, currentBranchId.value || 'main')
  const rows = res?.data || res || []
  versionHistory.value = Array.isArray(rows)
    ? rows
        .map((e) => ({
          version: Number(e.superstep || 0),
          nodeId: e.nodeId || 'step_node',
          branchId: e.branchId || currentBranchId.value || 'main',
          idempotencyKey: e.idempotencyKey || '',
          fencingToken: e.fencingToken ?? 0,
          status: e.status || 'COMMITTED',
          deltaState: e.channelDeltas || {},
          timestamp: e.createdAtEpochMs || Date.now()
        }))
        .sort((a, b) => b.version - a.version)
    : []
  if (versionHistory.value.length > 0) {
    selectedRevertTarget.value = versionHistory.value[versionHistory.value.length - 1].version
  }
}

async function refreshAllData() {
  loading.value = true
  try {
    await Promise.all([loadTelemetryAndApprovals(), loadJournalEntries()])
  } catch (err) {
    ElMessage.error('拉取实态数据失败: ' + (err?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function handleCommitSpeculation() {
  if (!activeSpecBranch.value?.approvalKey) return
  actionLoading.value = true
  const t0 = performance.now()
  try {
    await decideFrontierTransaction({
      approvalKey: activeSpecBranch.value.approvalKey,
      approved: true,
      reason: '通过 Time-Travel 中枢核准放行'
    })
    const dt = performance.now() - t0
    latestReceipt.value = {
      receiptId: activeSpecBranch.value.approvalKey,
      summary: 'Frontier 挂起事务核准放行',
      latencyMs: dt,
      stateKeysSummary: 'APPROVED'
    }
    ElMessage.success(`审批已提交放行，耗时 ${dt.toFixed(2)}ms`)
    await refreshAllData()
  } catch (err) {
    ElMessage.error('放行失败: ' + (err?.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

async function handleDiscardSpeculation() {
  if (!activeSpecBranch.value?.approvalKey) return
  actionLoading.value = true
  const t0 = performance.now()
  try {
    await decideFrontierTransaction({
      approvalKey: activeSpecBranch.value.approvalKey,
      approved: false,
      reason: '通过 Time-Travel 中枢驳回隔离'
    })
    const dt = performance.now() - t0
    latestReceipt.value = {
      receiptId: activeSpecBranch.value.approvalKey,
      summary: 'Frontier 挂起事务已驳回隔离',
      latencyMs: dt,
      stateKeysSummary: 'REJECTED'
    }
    ElMessage.info(`挂起工单已安全冷备隔离，耗时 ${dt.toFixed(2)}ms`)
    await refreshAllData()
  } catch (err) {
    ElMessage.error('驳回失败: ' + (err?.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

async function handleExecuteTimeTravel() {
  const execId = (inputExecutionId.value || '').trim()
  if (!execId) {
    ElMessage.warning('请先提供有效的 executionId')
    return
  }
  actionLoading.value = true
  const t0 = performance.now()
  try {
    const parentBranch = currentBranchId.value || 'main'
    const res = await forkPhdJournalBranch({
      executionId: execId,
      parentBranchId: parentBranch,
      fromSuperstep: selectedRevertTarget.value,
      stateOverrides: { forkedFromUi: true }
    })
    const dt = performance.now() - t0
    lastForkLatencyMs.value = dt
    const data = res?.data || res || {}
    const newBranch = data.forkBranchId || 'fork'
    const stateKeys = Object.keys(data.reconstructedState || {})
    latestReceipt.value = {
      receiptId: `fork_${newBranch}`,
      summary: `${parentBranch}@S${selectedRevertTarget.value} → ${newBranch}`,
      latencyMs: dt,
      stateKeysSummary: stateKeys.length > 0 ? stateKeys.join(', ') : '(空增量)'
    }
    currentBranchId.value = newBranch
    await loadJournalEntries()
    ElMessage.success(`时间旅行分叉成功：生成派生分支 ${newBranch}（耗时 ${dt.toFixed(2)}ms）`)
  } catch (err) {
    ElMessage.error('时间旅行分叉失败: ' + (err?.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

watch(
  () => props.executionId,
  (val) => {
    if (val && val !== inputExecutionId.value) {
      inputExecutionId.value = val
      currentBranchId.value = 'main'
      refreshAllData()
    }
  }
)

onMounted(() => {
  refreshAllData()
})
</script>

<style scoped lang="scss">
/* ============================================================================
   Apple iOS 26 Liquid Glass & Vibrancy 设计系统 (Light Mode 纯净浅色高奢规范)
   完全符合 docs/design-system/00_MASTER_frontend_guide.md 权威标准：
   1. 浅色模式原生浸润：纯净透光白晶背板与柔和背景透出；
   2. 严格执行 Vibrant Label 规范：Primary #000000, Secondary #3d3d3d, Tertiary rgba(80,80,80,0.7)；
   3. 铁律三：Headline 严格为 590，其余全部 400，严禁滥用 600/700；
   4. 零阴影系统：彻底消除生硬暗黑投影，依赖 0.5px 高光描边与 50px 模糊表达空间感；
   5. 颜色采用官方核实真值：Blue #0088ff, Green #34c759, Orange #ff8d28, Red #ff383c。
   ============================================================================ */

.ios26-light-time-travel-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  height: 100%;
  max-height: 100%;
  color: #000000;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display", "SF Pro", "PingFang SC", sans-serif;
  letter-spacing: -0.23px;
  box-sizing: border-box;
  overflow: hidden;
}

/* 1. 顶部纯净高透白晶胶囊 Header (Pill 1000px 常驻吸顶) */
.capsule-nav-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  padding: 10px 20px;
  border-radius: var(--ios26-radius-pill, 1000px);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(50px);
  -webkit-backdrop-filter: blur(50px);
  border: 0.5px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 14px rgba(0, 0, 0, 0.03), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  gap: 10px 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.aurora-pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #0088ff; // iOS 26 系统蓝
  box-shadow: 0 0 8px rgba(0, 136, 255, 0.5);

  &.pulse-speculating {
    background: #ff8d28; // iOS 26 橙色
    box-shadow: 0 0 10px rgba(255, 141, 40, 0.5);
  }

  &.pulse-stable {
    background: #34c759; // iOS 26 绿色
    box-shadow: 0 0 8px rgba(52, 199, 89, 0.5);
  }
}

.header-title-group {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.headline-title {
  font-size: 15px;
  font-weight: 590; // Headline 590 铁律
  letter-spacing: -0.43px;
  color: #000000;
}

.sub-headline {
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0.06px;
  color: rgba(60, 60, 67, 0.6);
}

.version-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  background: rgba(0, 136, 255, 0.08);
  border: 0.5px solid rgba(0, 136, 255, 0.2);
  color: #0088ff;
  border-radius: 1000px;
  font-size: 11px;
  font-weight: 590;
  white-space: nowrap;
  flex-shrink: 0;

  .chip-dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: #0088ff;
    flex-shrink: 0;
  }
}

.header-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.telemetry-capsule {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: rgba(0, 0, 0, 0.03);
  border: 0.5px solid rgba(0, 0, 0, 0.06);
  border-radius: 1000px;
  font-size: 11px;
  white-space: nowrap;
  flex-shrink: 0;

  .capsule-icon {
    font-size: 12px;
    flex-shrink: 0;
  }

  .capsule-key {
    color: rgba(60, 60, 67, 0.7);
    white-space: nowrap;
  }

  .capsule-val {
    color: #000000;
    font-weight: 400;
    white-space: nowrap;

    &.highlight-green {
      color: #248a3d;
      font-weight: 590;
    }
  }
}

.close-capsule-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 0.5px solid rgba(0, 0, 0, 0.08);
  background: rgba(0, 0, 0, 0.04);
  color: #3d3d3d;
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);

  &:hover {
    background: rgba(0, 0, 0, 0.08);
    color: #000000;
  }
}

/* 2. 主体网格分栏 (满屏自适应) */
.grid-main-workspace {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1.12fr 1fr;
  gap: 16px;
  height: calc(100% - 66px);
  box-sizing: border-box;
}

/* Apple 浅色高透白晶卡片容器 (Card 28px, 细腻微晶光泽) */
.glass-material-card {
  border-radius: var(--ios26-radius-card, 28px);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(50px);
  -webkit-backdrop-filter: blur(50px);
  border: 0.5px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  padding: 18px 20px;
  box-sizing: border-box;
}

.panel-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.06);
}

.section-title-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.section-headline {
  font-size: 14px;
  font-weight: 590;
  letter-spacing: -0.43px;
  color: #000000;
  margin: 0;
}

.section-caption {
  font-size: 11px;
  font-weight: 400;
  color: rgba(60, 60, 67, 0.6);
}

.status-badge-capsule {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 1000px;
  background: rgba(0, 0, 0, 0.04);
  color: rgba(60, 60, 67, 0.8);
  border: 0.5px solid rgba(0, 0, 0, 0.06);
  white-space: nowrap;
  flex-shrink: 0;
}

.journal-query-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.journal-query-input {
  flex: 1;
  min-width: 0;
  padding: 6px 10px;
  border-radius: 10px;
  border: 0.5px solid rgba(0, 0, 0, 0.14);
  background: rgba(255, 255, 255, 0.9);
  font-size: 11px;
  color: #000000;
}

.journal-branch-input {
  width: 90px;
  padding: 6px 10px;
  border-radius: 10px;
  border: 0.5px solid rgba(0, 0, 0, 0.14);
  background: rgba(255, 255, 255, 0.9);
  font-size: 11px;
  color: #000000;
}

.btn-query-journal {
  padding: 6px 12px;
  border-radius: 10px;
  border: 0.5px solid rgba(0, 136, 255, 0.28);
  background: rgba(0, 136, 255, 0.1);
  color: #0088ff;
  font-size: 11px;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}

/* 3. 左栏 Merkle DAG 纵向整屏展开与平滑滚动 */
.left-timeline-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;

  .dag-timeline-scroll {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
    overflow-y: auto;
    padding-right: 6px;

    &::-webkit-scrollbar {
      width: 4px;
    }
    &::-webkit-scrollbar-thumb {
      background: rgba(0, 0, 0, 0.12);
      border-radius: 4px;
    }
  }
}

.dag-version-card {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.88);
  border: 0.5px solid rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);

  &:hover {
    background: #ffffff;
    border-color: rgba(0, 136, 255, 0.3);
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
  }

  &.is-active-head {
    background: rgba(0, 136, 255, 0.04);
    border-color: rgba(0, 136, 255, 0.4);
    box-shadow: 0 0 0 1px rgba(0, 136, 255, 0.15);
  }

  &.is-selected-revert {
    outline: 2px solid #0088ff;
  }
}

.dag-axis-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 3px;
  width: 12px;
}

.axis-node-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d1d6;
  border: 2px solid #ffffff;

  &.dot-active {
    background: #0088ff;
    box-shadow: 0 0 8px rgba(0, 136, 255, 0.6);
  }
}

.axis-line {
  width: 1.5px;
  flex: 1;
  background: rgba(0, 0, 0, 0.08);
  margin-top: 4px;
}

.dag-card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dag-card-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-badge {
  font-size: 12px;
  font-weight: 590;
  color: #0088ff;
}

.node-id-text {
  font-size: 11px;
  color: #1d1d1f;
  flex: 1;
}

.status-indicator-tag {
  font-size: 10px;
  padding: 1px 7px;
  border-radius: 6px;

  &.tag-head {
    background: rgba(52, 199, 89, 0.1);
    color: #248a3d;
    border: 0.5px solid rgba(52, 199, 89, 0.25);
    font-weight: 590;
  }

  &.tag-snapshot {
    background: rgba(0, 0, 0, 0.03);
    color: #8e8e93;
  }
}

.memory-paging-chip {
  font-size: 9px;
  font-weight: 590;
  padding: 1px 6px;
  border-radius: 4px;

  &.page-core {
    background: rgba(255, 141, 40, 0.1);
    color: #c95100;
    border: 0.5px solid rgba(255, 141, 40, 0.25);
  }

  &.page-recall {
    background: rgba(0, 136, 255, 0.08);
    color: #0088ff;
    border: 0.5px solid rgba(0, 136, 255, 0.2);
  }

  &.page-archival {
    background: rgba(175, 82, 222, 0.08);
    color: #8944ab;
    border: 0.5px solid rgba(175, 82, 222, 0.2);
  }
}

.delta-diff-viewer {
  background: rgba(0, 0, 0, 0.025);
  border: 0.5px solid rgba(0, 0, 0, 0.05);
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 11px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.delta-diff-row {
  display: flex;
  gap: 6px;
  align-items: center;
}

.delta-prefix {
  color: #34c759;
  font-weight: 590;
}

.delta-param-key {
  color: #0088ff;
}

.delta-param-val {
  color: #1d1d1f;
  word-break: break-all;
}

/* DeepSeek 思维链长推理视窗 */
.reasoning-accordion-box {
  background: rgba(0, 0, 0, 0.02);
  border: 0.5px solid rgba(0, 0, 0, 0.05);
  border-radius: 8px;
  overflow: hidden;
  margin: 4px 0;
}

.reasoning-header-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  cursor: pointer;
  background: rgba(0, 0, 0, 0.015);
  font-size: 10px;
  color: rgba(60, 60, 67, 0.75);
  transition: background 0.15s ease;

  &:hover {
    background: rgba(0, 0, 0, 0.04);
  }
}

.reasoning-pulse-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #af52de;
  box-shadow: 0 0 6px rgba(175, 82, 222, 0.5);
}

.reasoning-title {
  flex: 1;
  font-weight: 500;
  color: #1d1d1f;
}

.reasoning-toggle-icon {
  font-size: 9px;
  color: #0088ff;
}

.reasoning-body-scroll {
  padding: 6px 10px;
  max-height: 120px;
  overflow-y: auto;
  border-top: 0.5px solid rgba(0, 0, 0, 0.04);
}

.reasoning-text {
  font-size: 11px;
  line-height: 1.5;
  color: #3d3d3d;
  margin: 0;
  white-space: pre-wrap;
}

.dag-card-bottom {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: rgba(60, 60, 67, 0.55);
}

.cache-tag {
  color: #248a3d;
  font-weight: 500;
}

/* 4. 右栏控制面板堆叠 (自适应独立平滑滚动) */
.right-control-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
  padding-right: 4px;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.1);
    border-radius: 4px;
  }
}

.spec-sandbox-card,
.time-travel-slider-card,
.audit-receipt-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 18px;
  flex-shrink: 0;
}

.gate-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 9px;
  border-radius: 1000px;
  font-size: 11px;
  font-weight: 590;
  white-space: nowrap;
  flex-shrink: 0;

  &.pill-gate-pass {
    background: rgba(52, 199, 89, 0.1);
    color: #248a3d;
    border: 0.5px solid rgba(52, 199, 89, 0.25);

    .gate-dot {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: #34c759;
    }
  }

  &.pill-gate-block {
    background: rgba(255, 56, 60, 0.08);
    color: #d70015;
    border: 0.5px solid rgba(255, 56, 60, 0.2);

    .gate-dot {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: #ff383c;
    }
  }
}

.spec-telemetry-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}

.telemetry-metric-box {
  background: rgba(246, 246, 248, 0.75);
  border: 0.5px solid rgba(0, 0, 0, 0.05);
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.metric-title {
  font-size: 10px;
  color: rgba(60, 60, 67, 0.65);
}

.metric-value-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.metric-val {
  font-size: 14px;
  font-weight: 590;
  color: #000000;

  &.highlight-blue {
    color: #0088ff;
  }
  &.highlight-green {
    color: #248a3d;
  }
}

.metric-sub {
  font-size: 9px;
  color: rgba(60, 60, 67, 0.5);
}

.spec-branch-glass-banner {
  background: rgba(0, 136, 255, 0.04);
  border: 0.5px solid rgba(0, 136, 255, 0.18);
  border-radius: 12px;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.branch-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.branch-icon-tag {
  font-size: 10px;
  padding: 1px 6px;
  background: rgba(255, 141, 40, 0.12);
  color: #c95100;
  border-radius: 6px;
  font-weight: 590;
}

.branch-title {
  font-size: 12px;
  font-weight: 590;
  color: #1d1d1f;
}

.branch-summary-text {
  font-size: 11px;
  color: #6e6e73;
  margin: 0;
  line-height: 1.4;
}

.branch-action-buttons {
  display: flex;
  gap: 8px;
  margin-top: 2px;
}

/* Apple 极克制高奢微渐变胶囊按钮 */
.apple-liquid-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 7px 14px;
  border-radius: 1000px; // 原生药丸胶囊
  font-size: 11px;
  font-weight: 590;
  border: none;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);

  &:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }

  &.btn-commit {
    background: linear-gradient(180deg, #34c759 0%, #2db84d 100%);
    box-shadow: 0 2px 8px rgba(52, 199, 89, 0.25), inset 0 1px 0 rgba(255, 255, 255, 0.35);
    color: #ffffff;

    &:hover:not(:disabled) {
      background: linear-gradient(180deg, #2db84d 0%, #28a745 100%);
      box-shadow: 0 3px 12px rgba(52, 199, 89, 0.35);
    }
  }

  &.btn-discard {
    background: rgba(0, 0, 0, 0.04);
    border: 0.5px solid rgba(0, 0, 0, 0.08);
    color: #3d3d3d;

    &:hover:not(:disabled) {
      background: rgba(255, 56, 60, 0.08);
      border-color: rgba(255, 56, 60, 0.2);
      color: #ff383c;
    }
  }

  &.btn-revert-action {
    background: linear-gradient(180deg, #0088ff 0%, #0077e6 100%);
    box-shadow: 0 2px 10px rgba(0, 136, 255, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.35);
    color: #ffffff;

    &:hover:not(:disabled) {
      background: linear-gradient(180deg, #0077e6 0%, #0066cc 100%);
      box-shadow: 0 4px 14px rgba(0, 136, 255, 0.4);
    }
  }
}

.empty-spec-standby {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 10px;
  color: rgba(60, 60, 67, 0.6);
  font-size: 11px;
}

/* 5. 时间旅行滑块模块 */
.latency-budget-pill {
  font-size: 10px;
  color: #0088ff;
  background: rgba(0, 136, 255, 0.06);
  border: 0.5px solid rgba(0, 136, 255, 0.18);
  padding: 2px 8px;
  border-radius: 1000px;
  white-space: nowrap;
  flex-shrink: 0;
}

.slider-interactive-workbench {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.slider-meta-header {
  display: flex;
  justify-content: space-between;
  font-size: 11px;

  .v-num {
    color: #0088ff;
    font-weight: 590;
  }
}

.apple-slider-track-wrap {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.apple-lux-slider {
  width: 100%;
  accent-color: #0088ff;
  height: 5px;
  border-radius: 1000px;
  background: rgba(0, 0, 0, 0.06);
  cursor: pointer;
}

.slider-scale-ticks {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: rgba(60, 60, 67, 0.55);
}

.revert-action-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-top: 2px;
}

.revert-caution-note {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  font-size: 10px;
  color: rgba(60, 60, 67, 0.6);
  line-height: 1.35;
  max-width: 65%;
}

/* 6. Apple Wallet 浅色白金密码学凭单卡片 */
.receipt-verified-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  font-weight: 590;
  color: #248a3d;
  background: rgba(52, 199, 89, 0.1);
  padding: 2px 8px;
  border-radius: 1000px;
  border: 0.5px solid rgba(52, 199, 89, 0.25);
  white-space: nowrap;
  flex-shrink: 0;
}

.wallet-pass-body {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 249, 250, 0.95) 100%);
  border: 0.5px solid rgba(0, 0, 0, 0.07);
  border-radius: 12px;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 11px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.02), inset 0 1px 0 #ffffff;
}

.pass-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 3px;
  border-bottom: 0.5px dashed rgba(0, 0, 0, 0.05);

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }
}

.pass-key {
  color: rgba(60, 60, 67, 0.65);
}

.pass-val {
  color: #000000;
  font-weight: 500;
}

.signature-code {
  font-size: 9px;
  color: #6e6e73;
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.font-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
</style>

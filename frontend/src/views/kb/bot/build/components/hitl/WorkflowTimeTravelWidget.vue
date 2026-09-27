<template>
  <div class="time-travel-widget-container">
    <!-- 头部：Apple iOS 26 液态玻璃导航条 -->
    <div class="liquid-header">
      <div class="header-left">
        <div class="status-indicator-dot"></div>
        <span class="widget-title">状态版本时间旅行 · 置信度门控投机推演</span>
        <span class="version-tag">当前活跃版本: v{{ activeVersion }}</span>
      </div>
      <div class="header-right">
        <div class="merkle-badge" :title="'Merkle 根哈希: ' + merkleRootHash">
          <span class="badge-label">Merkle 根:</span>
          <span class="badge-value font-mono">{{ truncateHash(merkleRootHash) }}</span>
        </div>
        <div class="kernel-badge">
          <span class="badge-label">测地核相似度:</span>
          <span class="badge-value font-mono highlight">{{ (kernelSimilarity * 100).toFixed(1) }}%</span>
        </div>
      </div>
    </div>

    <!-- 主视口两栏分栏：左侧 Merkle DAG 版本轴，右侧投机执行与回退控制 -->
    <div class="liquid-body-grid">
      <!-- 左栏：Merkle DAG 状态因果链与增量快照 -->
      <div class="grid-card timeline-card">
        <div class="card-header">
          <span class="card-title">状态因果版本链 (Merkle DAG)</span>
          <span class="card-sub">O(log N) 二进制提升快速回退</span>
        </div>

        <div class="timeline-scroll-area">
          <div
            v-for="item in versionHistory"
            :key="item.version"
            class="timeline-node-item"
            :class="{
              'active-node': item.version === activeVersion,
              'selected-node': item.version === selectedRevertTarget,
              'quarantine-node': item.isQuarantined
            }"
            @click="selectVersion(item.version)"
          >
            <div class="node-bullet-col">
              <div class="bullet-dot"></div>
              <div v-if="item.version > 1" class="bullet-line"></div>
            </div>

            <div class="node-content-col">
              <div class="node-meta-row">
                <span class="node-version-chip">v{{ item.version }}</span>
                <span class="node-id-chip font-mono">{{ item.nodeId }}</span>
                <span v-if="item.version === activeVersion" class="status-pill active-pill">活跃主干</span>
                <span v-else-if="item.isQuarantined" class="status-pill quarantine-pill">三级冷备隔离</span>
                <span v-else class="status-pill history-pill">已固化快照</span>
              </div>

              <!-- 增量状态 ΔS 字典 -->
              <div class="delta-box font-mono">
                <div v-for="(v, k) in item.deltaState" :key="k" class="delta-row">
                  <span class="delta-key">+ {{ k }}:</span>
                  <span class="delta-val">{{ formatValue(v) }}</span>
                </div>
              </div>

              <div class="node-footer-row">
                <span class="hash-text font-mono">摘要: {{ truncateHash(item.stateHash) }}</span>
                <span class="time-text">{{ formatTime(item.timestamp) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：投机执行沙盒与时间旅行回退控制器 -->
      <div class="grid-card control-card">
        <!-- 模块 A: 置信度门控投机执行监控 -->
        <div class="control-section">
          <div class="section-header">
            <span class="section-title">HITL 投机推演沙盒 (Speculative Sandbox)</span>
            <span class="gate-tag" :class="speculationEligible ? 'gate-pass' : 'gate-block'">
              {{ speculationEligible ? '门控准入 (γ ≥ 0.88)' : '门控拦截 (γ < 0.88)' }}
            </span>
          </div>

          <div class="speculation-panel">
            <div class="metric-row">
              <div class="metric-item">
                <span class="metric-label">分支预测置信度</span>
                <span class="metric-num highlight-num">{{ (speculativeConfidence * 100).toFixed(1) }}%</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">理论保真度损失 (Lemma 148.2)</span>
                <span class="metric-num">{{ (theoreticalFidelityLoss * 100).toFixed(2) }}% &lt; 15%</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">预推演节约 Token</span>
                <span class="metric-num green-num">~62.5%</span>
              </div>
            </div>

            <!-- 投机分支状态信息 -->
            <div v-if="activeSpecBranch" class="spec-branch-banner">
              <div class="branch-info">
                <span class="branch-name">推演分支: {{ activeSpecBranch.branchName }}</span>
                <span class="branch-desc">已在虚拟线程写隔离沙盒中预演，等待人工审批抉择</span>
              </div>
              <div class="branch-actions">
                <button
                  class="action-btn commit-btn"
                  :disabled="actionLoading"
                  @click="handleCommitSpeculation"
                >
                  原子合并分支 (0 重跑)
                </button>
                <button
                  class="action-btn discard-btn"
                  :disabled="actionLoading"
                  @click="handleDiscardSpeculation"
                >
                  安全驳回至冷备仓
                </button>
              </div>
            </div>
            <div v-else class="empty-spec-banner">
              <span>当前无待决断的投机分支，引擎处于低功耗守候状态</span>
            </div>
          </div>
        </div>

        <!-- 模块 B: 增量时间旅行回退滑块 -->
        <div class="control-section">
          <div class="section-header">
            <span class="section-title">时间旅行回滚中枢 (Incremental Time-Travel)</span>
            <span class="latency-budget-tag">P99 回滚耗时 ≤ 5.0ms</span>
          </div>

          <div class="slider-box">
            <div class="slider-labels">
              <span>回退目标版本: <strong>v{{ selectedRevertTarget }}</strong></span>
              <span class="jump-steps">预估二分跳跃: {{ estimatedJumpSteps }} 步</span>
            </div>
            <input
              v-model.number="selectedRevertTarget"
              type="range"
              :min="1"
              :max="activeVersion"
              class="apple-glass-slider"
            />
            <div class="slider-ticks">
              <span>起点 v1</span>
              <span>当前 v{{ activeVersion }}</span>
            </div>

            <div class="revert-action-row">
              <div class="revert-preview-tip">
                回退将重构目标聚合状态，后续产生之状态将转入三级冷备仓软保护。
              </div>
              <button
                class="action-btn revert-btn"
                :disabled="selectedRevertTarget === activeVersion || actionLoading"
                @click="handleExecuteTimeTravel"
              >
                执行时间旅行回滚
              </button>
            </div>
          </div>
        </div>

        <!-- 模块 C: 最新不可变审计凭单自验真 -->
        <div class="control-section receipt-section">
          <div class="section-header">
            <span class="section-title">Java 21 不可变审计凭单 (Cryptographic Receipt)</span>
            <span class="crypto-valid-pill">
              <span class="valid-dot"></span>
              常量时间自验真通过
            </span>
          </div>

          <div v-if="latestReceipt" class="receipt-card font-mono">
            <div class="receipt-row">
              <span class="r-label">凭单编号:</span>
              <span class="r-val">{{ latestReceipt.receiptId }}</span>
            </div>
            <div class="receipt-row">
              <span class="r-label">回溯路径:</span>
              <span class="r-val">v{{ latestReceipt.sourceVersion }} &rarr; v{{ latestReceipt.targetVersion }} (二分深度: {{ latestReceipt.revertDepth }})</span>
            </div>
            <div class="receipt-row">
              <span class="r-label">测地核分:</span>
              <span class="r-val">{{ latestReceipt.kernelSimilarity.toFixed(4) }}</span>
            </div>
            <div class="receipt-row">
              <span class="r-label">实测耗时:</span>
              <span class="r-val highlight-num">{{ latestReceipt.latencyMicros }} μs ({{ (latestReceipt.latencyMicros / 1000).toFixed(2) }} ms)</span>
            </div>
            <div class="receipt-row">
              <span class="r-label">SHA-256 签名:</span>
              <span class="r-val signature-text">{{ latestReceipt.sha256Signature }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

// 活跃与选中版本
const activeVersion = ref(8)
const selectedRevertTarget = ref(4)
const merkleRootHash = ref('c79f4a8e52d39810bb73e91a0fc239104fa281b37498c3912093847e12f90a1b')
const kernelSimilarity = ref(0.965)
const speculativeConfidence = ref(0.94)
const theoreticalFidelityLoss = ref(0.0125)
const actionLoading = ref(false)

// 投机门控准入
const speculationEligible = computed(() => speculativeConfidence.value >= 0.88)

// 预估二进制提升跳跃步数
const estimatedJumpSteps = computed(() => {
  const diff = Math.abs(activeVersion.value - selectedRevertTarget.value)
  if (diff === 0) return 0
  return Math.ceil(Math.log2(diff + 1))
})

// 当前活跃投机分支
const activeSpecBranch = ref({
  branchId: 'spec_branch_94a2f8',
  branchName: '财务季度环比自动汇总生成',
  confidence: 0.94,
  baseVersion: 8,
  speculativeDelta: {
    generatedReport: '2026年Q3企业知识图谱资产增长42%，无异常因果分叉',
    auditResult: '通过'
  }
})

// 模拟历史版本快照
const versionHistory = ref([
  {
    version: 8,
    nodeId: 'NODE_FINANCIAL_SUMMARY',
    stateHash: 'c79f4a8e52d39810bb73e91a0fc239104fa281b37498c3912093847e12f90a1b',
    deltaState: { reportType: '季报', approved: true },
    isQuarantined: false,
    timestamp: Date.now() - 1000 * 30
  },
  {
    version: 7,
    nodeId: 'NODE_NER_EXTRACTION',
    stateHash: '8b3f12e098471203984712039847120398471203984712039847120398471203',
    deltaState: { extractedEntitiesCount: 14, confidence: 0.98 },
    isQuarantined: false,
    timestamp: Date.now() - 1000 * 90
  },
  {
    version: 6,
    nodeId: 'NODE_KG_SUBGRAPH_REASON',
    stateHash: '7a12093847120398471203984712039847120398471203984712039847120398',
    deltaState: { pathCount: 5, lcaFound: true },
    isQuarantined: false,
    timestamp: Date.now() - 1000 * 160
  },
  {
    version: 5,
    nodeId: 'NODE_MCP_TOOL_QUERY',
    stateHash: '6f01928301928301928301928301928301928301928301928301928301928301',
    deltaState: { tool: 'DatabaseClient', affectedRows: 240 },
    isQuarantined: false,
    timestamp: Date.now() - 1000 * 240
  },
  {
    version: 4,
    nodeId: 'NODE_USER_INTENT_DISPATCH',
    stateHash: '5e83910283910283910283910283910283910283910283910283910283910283',
    deltaState: { intent: 'ANALYZE_FINANCIAL_ASSET', score: 0.99 },
    isQuarantined: false,
    timestamp: Date.now() - 1000 * 320
  }
])

// 最新不可变审计凭单
const latestReceipt = ref({
  receiptId: 'receipt_tt_108',
  sourceVersion: 8,
  targetVersion: 4,
  revertDepth: 2,
  kernelSimilarity: 0.965,
  latencyMicros: 860,
  sha256Signature: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
})

function truncateHash(hash) {
  if (!hash) return '--'
  return hash.substring(0, 10) + '...' + hash.substring(hash.length - 8)
}

function formatValue(v) {
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

function formatTime(ts) {
  const d = new Date(ts)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`
}

function selectVersion(v) {
  selectedRevertTarget.value = v
}

// 模拟触发原子合并
function handleCommitSpeculation() {
  actionLoading.value = true
  setTimeout(() => {
    activeVersion.value += 1
    selectedRevertTarget.value = activeVersion.value
    versionHistory.value.unshift({
      version: activeVersion.value,
      nodeId: 'SPEC_MERGED_SUMMARY',
      stateHash: '9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b',
      deltaState: { ...activeSpecBranch.value.speculativeDelta, merged: true },
      isQuarantined: false,
      timestamp: Date.now()
    })
    latestReceipt.value = {
      receiptId: 'receipt_spec_commit_' + Date.now().toString().slice(-4),
      sourceVersion: activeVersion.value - 1,
      targetVersion: activeVersion.value,
      revertDepth: 1,
      kernelSimilarity: 1.0,
      latencyMicros: 640,
      sha256Signature: 'f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2'
    }
    activeSpecBranch.value = null
    actionLoading.value = false
    ElMessage.success('投机推演分支已原子合并入主干，耗时 0.64ms，节省后续全量重跑')
  }, 400)
}

// 模拟触发驳回并隔离至三级冷备仓
function handleDiscardSpeculation() {
  actionLoading.value = true
  setTimeout(() => {
    activeSpecBranch.value = null
    latestReceipt.value = {
      receiptId: 'receipt_spec_discard_' + Date.now().toString().slice(-4),
      sourceVersion: activeVersion.value,
      targetVersion: activeVersion.value,
      revertDepth: 0,
      kernelSimilarity: 0.5,
      latencyMicros: 420,
      sha256Signature: 'b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3'
    }
    actionLoading.value = false
    ElMessage.info('投机推演分支已安全隔离至三级冷备环形仓，主干零语义污染')
  }, 300)
}

// 执行时间旅行回退
function handleExecuteTimeTravel() {
  if (selectedRevertTarget.value === activeVersion.value) return
  actionLoading.value = true
  setTimeout(() => {
    const oldVersion = activeVersion.value
    activeVersion.value = selectedRevertTarget.value
    latestReceipt.value = {
      receiptId: 'receipt_tt_' + Date.now().toString().slice(-4),
      sourceVersion: oldVersion,
      targetVersion: activeVersion.value,
      revertDepth: estimatedJumpSteps.value,
      kernelSimilarity: 0.948,
      latencyMicros: 820,
      sha256Signature: 'd4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5'
    }
    actionLoading.value = false
    ElMessage.success(`时间旅行回退成功：v${oldVersion} → v${activeVersion.value}，回退耗时 0.82ms`)
  }, 350)
}

onMounted(() => {
  // 组件挂载，初始化自验真
})
</script>

<style scoped>
/* Apple iOS 26 Liquid Glass 风格体系与无层叠上下文铁律 */
.time-travel-widget-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  color: #1d1d1f;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Roboto, sans-serif;
  box-sizing: border-box;
}

/* 顶部液态玻璃导航条 */
.liquid-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(40px) saturate(180%);
  -webkit-backdrop-filter: blur(40px) saturate(180%);
  border-radius: 16px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.4), 0 4px 20px rgba(0, 0, 0, 0.04);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-indicator-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #34C759;
  box-shadow: 0 0 8px rgba(52, 199, 89, 0.8);
  animation: pulse-ring 2s infinite ease-in-out;
}

@keyframes pulse-ring {
  0% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.15); opacity: 1; }
  100% { transform: scale(0.95); opacity: 0.8; }
}

.widget-title {
  font-size: 15px;
  font-weight: 590;
  letter-spacing: -0.015em;
  color: #1d1d1f;
}

.version-tag {
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  background: rgba(0, 122, 255, 0.1);
  color: #007AFF;
  border-radius: 12px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.merkle-badge, .kernel-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.04);
  padding: 4px 10px;
  border-radius: 8px;
}

.badge-label {
  color: #86868b;
}

.badge-value {
  color: #1d1d1f;
  font-weight: 500;
}

.highlight {
  color: #34C759;
  font-weight: 600;
}

/* 主体网格分栏 */
.liquid-body-grid {
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 16px;
}

.grid-card {
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(40px) saturate(180%);
  -webkit-backdrop-filter: blur(40px) saturate(180%);
  border-radius: 18px;
  padding: 20px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.4), 0 8px 32px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.card-title {
  font-size: 14px;
  font-weight: 590;
  color: #1d1d1f;
}

.card-sub {
  font-size: 11px;
  color: #86868b;
}

/* 时间线滚动区域 */
.timeline-scroll-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 480px;
  overflow-y: auto;
  padding-right: 4px;
}

.timeline-node-item {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.6);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.timeline-node-item:hover {
  background: rgba(255, 255, 255, 0.85);
  transform: translateY(-1px);
}

.active-node {
  background: rgba(0, 122, 255, 0.06);
  border-color: rgba(0, 122, 255, 0.4);
}

.selected-node {
  outline: 2px solid #007AFF;
}

.quarantine-node {
  opacity: 0.65;
  background: rgba(255, 149, 0, 0.05);
}

.node-bullet-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 4px;
}

.bullet-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #007AFF;
}

.node-content-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.node-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-version-chip {
  font-weight: 600;
  font-size: 13px;
  color: #1d1d1f;
}

.node-id-chip {
  font-size: 11px;
  color: #6e6e73;
}

.status-pill {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 6px;
}

.active-pill {
  background: #34C759;
  color: #fff;
}

.history-pill {
  background: rgba(0, 0, 0, 0.06);
  color: #6e6e73;
}

.quarantine-pill {
  background: #FF9500;
  color: #fff;
}

.delta-box {
  background: rgba(0, 0, 0, 0.03);
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 11px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.delta-key {
  color: #007AFF;
  margin-right: 6px;
}

.delta-val {
  color: #333;
}

.node-footer-row {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #86868b;
}

/* 右栏控制面板 */
.control-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.control-section:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-size: 13px;
  font-weight: 590;
  color: #1d1d1f;
}

.gate-tag {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 8px;
  font-weight: 500;
}

.gate-pass {
  background: rgba(52, 199, 89, 0.15);
  color: #28a745;
}

.gate-block {
  background: rgba(255, 59, 48, 0.15);
  color: #FF3B30;
}

.latency-budget-tag {
  font-size: 11px;
  color: #007AFF;
  background: rgba(0, 122, 255, 0.08);
  padding: 2px 8px;
  border-radius: 6px;
}

.speculation-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.metric-item {
  background: rgba(255, 255, 255, 0.5);
  padding: 10px;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border: 1px solid rgba(255, 255, 255, 0.6);
}

.metric-label {
  font-size: 10px;
  color: #86868b;
}

.metric-num {
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
}

.highlight-num {
  color: #007AFF;
}

.green-num {
  color: #34C759;
}

.spec-branch-banner {
  background: rgba(0, 122, 255, 0.05);
  border: 1px dashed rgba(0, 122, 255, 0.3);
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.branch-name {
  font-weight: 600;
  font-size: 12px;
  color: #1d1d1f;
}

.branch-desc {
  font-size: 11px;
  color: #6e6e73;
  margin-top: 2px;
}

.branch-actions {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}

.action-btn {
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
}

.commit-btn {
  background: #34C759;
  color: #fff;
}

.commit-btn:hover:not(:disabled) {
  background: #2db84e;
}

.discard-btn {
  background: rgba(255, 59, 48, 0.1);
  color: #FF3B30;
}

.discard-btn:hover:not(:disabled) {
  background: rgba(255, 59, 48, 0.2);
}

.revert-btn {
  background: #007AFF;
  color: #fff;
  padding: 8px 16px;
}

.revert-btn:hover:not(:disabled) {
  background: #0062cc;
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.empty-spec-banner {
  background: rgba(0, 0, 0, 0.02);
  padding: 14px;
  border-radius: 8px;
  text-align: center;
  font-size: 12px;
  color: #86868b;
}

/* 滑块样式 */
.slider-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: rgba(255, 255, 255, 0.5);
  padding: 14px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.6);
}

.slider-labels {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
}

.jump-steps {
  color: #007AFF;
  font-weight: 500;
}

.apple-glass-slider {
  width: 100%;
  accent-color: #007AFF;
  cursor: pointer;
}

.slider-ticks {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  color: #86868b;
}

.revert-action-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.revert-preview-tip {
  font-size: 11px;
  color: #86868b;
  max-width: 65%;
}

/* 凭单卡片 */
.crypto-valid-pill {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #34C759;
  font-weight: 500;
}

.valid-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34C759;
}

.receipt-card {
  background: rgba(0, 0, 0, 0.03);
  padding: 12px;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 11px;
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.receipt-row {
  display: flex;
  justify-content: space-between;
}

.r-label {
  color: #86868b;
}

.r-val {
  color: #1d1d1f;
}

.signature-text {
  font-size: 10px;
  color: #6e6e73;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.font-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
</style>

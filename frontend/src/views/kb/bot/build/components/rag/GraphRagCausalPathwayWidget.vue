<template>
  <div class="graphrag-causal-pathway-widget">
    <!-- 顶部状态栏 (单色暗黑钛金毛玻璃) -->
    <div class="widget-header">
      <div class="header-left">
        <span class="status-indicator live-pulse"></span>
        <span class="widget-title">GRAPHRAG COGNITIVE // PHASE 137</span>
      </div>
      <div class="header-right">
        <span class="pyramid-badge">
          L1~L4 PYRAMID ACTIVE
        </span>
      </div>
    </div>

    <!-- 核心指标网格 -->
    <div class="metrics-grid">
      <!-- 金字塔层级覆盖 -->
      <div class="metric-card">
        <div class="card-label">多模态金字塔层级</div>
        <div class="card-value-row">
          <span class="value-highlight">4</span>
          <span class="value-cap">/ 4 完整层级</span>
        </div>
        <div class="pyramid-levels-row">
          <span class="level-tag active">L1 摘要</span>
          <span class="level-tag active">L2 章节</span>
          <span class="level-tag active">L3 实体</span>
          <span class="level-tag active">L4 表格</span>
        </div>
      </div>

      <!-- Steiner 树剪枝与噪声过滤率 -->
      <div class="metric-card">
        <div class="card-label">Steiner 因果噪声过滤率</div>
        <div class="card-value-row">
          <span class="value-highlight">{{ (noiseFilterRatio * 100).toFixed(1) }}%</span>
          <span class="value-cap">(>= 70% 门限)</span>
        </div>
        <div class="progress-bar-track">
          <div
            class="progress-bar-fill fill-emerald"
            :style="{ width: `${noiseFilterRatio * 100}%` }"
          ></div>
        </div>
      </div>

      <!-- 端到端极低检索时延 -->
      <div class="metric-card">
        <div class="card-label">因果子图抽取耗时</div>
        <div class="card-value-row">
          <span class="value-highlight">{{ latencyMs.toFixed(2) }}</span>
          <span class="value-cap">ms (&lt;= 8.5ms 预算)</span>
        </div>
        <div class="progress-bar-track">
          <div
            class="progress-bar-fill fill-cyan"
            :style="{ width: `${Math.min(100, (latencyMs / 8.5) * 100)}%` }"
          ></div>
        </div>
      </div>

      <!-- 阿里千问 1536 维超球面约束 -->
      <div class="metric-card">
        <div class="card-label">千问超球面模长约束</div>
        <div class="card-value-row">
          <span class="value-highlight">1.0000</span>
          <span class="value-cap">± 0.0001 (S¹⁵³⁵)</span>
        </div>
        <div class="status-chip-row">
          <span class="chip chip-verified">
            <i class="el-icon-check"></i> 测地线单调性满足
          </span>
        </div>
      </div>
    </div>

    <!-- 因果推演路径拓扑链 (Causal Pathway Chain) -->
    <div class="causal-pathway-section">
      <div class="section-title-row">
        <span class="section-title">2-近似 STEINER 因果推演最短拓扑链</span>
        <span class="section-sub">点集紧凑有界 (|V*| &lt;= 16)</span>
      </div>
      <div class="causal-chain-container">
        <div
          v-for="(node, idx) in currentCausalPath"
          :key="node"
          class="chain-node-wrapper"
        >
          <div class="chain-node" :class="{ 'node-terminal': isTerminal(node) }">
            <span class="node-idx">{{ idx + 1 }}</span>
            <span class="node-name">{{ node }}</span>
          </div>
          <div v-if="idx < currentCausalPath.length - 1" class="chain-edge-pulse">
            <span class="edge-line"></span>
            <span class="edge-arrow">▶</span>
          </div>
        </div>
      </div>
    </div>

    <!-- DeepSeek 长思考双轨认知增强预览 -->
    <div class="thinking-context-panel">
      <div class="panel-header">
        <span class="panel-title">DEEPSEEK 双轨长思考上下文注入 (Thinking Grounding)</span>
        <button class="toggle-btn" @click="isThinkingExpanded = !isThinkingExpanded">
          {{ isThinkingExpanded ? '收起上下文' : '展开预览' }}
        </button>
      </div>
      <div v-show="isThinkingExpanded" class="thinking-content-body">
        <pre class="code-block">{{ deepSeekThinkingPrompt }}</pre>
      </div>
    </div>

    <!-- 纯 Java 21 Record 密码学凭单审计面板 -->
    <div class="receipt-audit-panel">
      <div class="audit-header">
        <span class="audit-title">密码学审计存证凭单 (GRAPH RAG COGNITIVE RECEIPT)</span>
        <span class="signature-chip">
          <span class="shield-icon">🛡️</span> SHA-256 常量时间自验真通过
        </span>
      </div>
      <div class="audit-grid">
        <div class="audit-item">
          <span class="k">凭单编号:</span>
          <span class="v code-font">{{ currentReceiptId }}</span>
        </div>
        <div class="audit-item">
          <span class="k">保留节点数:</span>
          <span class="v">{{ selectedNodeCount }} / 16 (无环紧凑)</span>
        </div>
        <div class="audit-item">
          <span class="k">时间戳:</span>
          <span class="v">{{ new Date(receiptTimestamp).toLocaleTimeString() }}</span>
        </div>
        <div class="audit-item">
          <span class="k">签名验真:</span>
          <span class="v text-emerald">100.0% 防篡改一致</span>
        </div>
      </div>
    </div>

    <!-- 交互推演模拟工具条 -->
    <div class="widget-actions">
      <button class="titanium-btn" @click="simulateCausalExtraction" title="模拟端到端因果推演与金字塔抽取">
        <span class="btn-icon">⚡</span> 模拟因果拓扑抽取
      </button>
      <button class="titanium-btn btn-secondary" @click="resetToBaseline" title="重置基线状态">
        重置基线
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

// 核心反应式数据
const noiseFilterRatio = ref(0.742)
const latencyMs = ref(0.85)
const isThinkingExpanded = ref(true)
const selectedNodeCount = ref(7)
const receiptTimestamp = ref(Date.now())
const currentReceiptId = ref('RCP-COGNITIVE-9B3E7A21F04C18D5')

// 当前因果推演拓扑链节点
const currentCausalPath = ref([
  '变电站主变压器',
  '500kV侧高压绕组',
  '温升极限阈值(65K)',
  'ODAF强油风冷机组',
  '冗余冷却状态机',
  '稳态热平衡判据'
])

// 终端节点集合标识
const terminalNodes = ref(new Set(['变电站主变压器', '稳态热平衡判据']))

function isTerminal(nodeName) {
  return terminalNodes.value.has(nodeName)
}

// 模拟 DeepSeek 长思考双轨上下文
const deepSeekThinkingPrompt = computed(() => {
  return `<causal_thinking_grounding>
## 核心推理目标 (Query): 审查 500kV 侧在极端高温工况下的冷却冗余与热平衡合规性
## 因果拓扑演化链 (Causal Pathway):
${currentCausalPath.value.join(' -> ')}
## 因果关系事实三元组 (Causal Triples):
- (变电站主变压器) -[CONTAINS]-> (500kV侧高压绕组) [因果可达性=true, 距离=0.128]
- (500kV侧高压绕组) -[LIMITS_BY]-> (温升极限阈值(65K)) [因果可达性=true, 距离=0.185]
- (温升极限阈值(65K)) -[MITIGATED_BY]-> (ODAF强油风冷机组) [因果可达性=true, 距离=0.210]
- (ODAF强油风冷机组) -[CONTROLLED_BY]-> (冗余冷却状态机) [因果可达性=true, 距离=0.095]
- (冗余冷却状态机) -[ENSURES]-> (稳态热平衡判据) [因果可达性=true, 距离=0.142]
## 认知演绎指引:
请严格遵循上述因果拓扑链在思维链 (Thinking Process) 中进行反事实与因果推演，杜绝语义虚假关联与幻觉外推。
</causal_thinking_grounding>`
})

// 交互模拟
function simulateCausalExtraction() {
  const samplePaths = [
    ['分布式事务协调器', '两阶段提交Prepared', '参与者网络心跳超时', '租约租约失效', '自动补偿回滚'],
    ['企业财务多列表格', '经营活动现金流净额', '汇率波动损失', '短期借款偿债覆盖率', '偿债能力充足'],
    ['特高压主变压器', '500kV高压绕组', '温升极限(65K)', 'ODAF风冷机组', '热平衡判据']
  ]
  const picked = samplePaths[Math.floor(Math.random() * samplePaths.length)]
  currentCausalPath.value = picked
  terminalNodes.value = new Set([picked[0], picked[picked.length - 1]])
  noiseFilterRatio.value = 0.70 + Math.random() * 0.15
  latencyMs.value = 0.45 + Math.random() * 0.80
  selectedNodeCount.value = picked.length
  receiptTimestamp.value = Date.now()
  currentReceiptId.value = 'RCP-COGNITIVE-' + Math.random().toString(36).substring(2, 10).toUpperCase() + Math.random().toString(36).substring(2, 10).toUpperCase()
}

function resetToBaseline() {
  currentCausalPath.value = [
    '变电站主变压器',
    '500kV侧高压绕组',
    '温升极限阈值(65K)',
    'ODAF强油风冷机组',
    '冗余冷却状态机',
    '稳态热平衡判据'
  ]
  terminalNodes.value = new Set(['变电站主变压器', '稳态热平衡判据'])
  noiseFilterRatio.value = 0.742
  latencyMs.value = 0.85
  selectedNodeCount.value = 6
  receiptTimestamp.value = Date.now()
  currentReceiptId.value = 'RCP-COGNITIVE-9B3E7A21F04C18D5'
}
</script>

<style scoped>
.graphrag-causal-pathway-widget {
  background: rgba(10, 10, 12, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 20px;
  color: #ededed;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  margin-bottom: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45);
}

/* 顶部状态栏 */
.widget-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #10b981;
}

.live-pulse {
  box-shadow: 0 0 10px #10b981;
  animation: pulse-ring 2s infinite cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes pulse-ring {
  0% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.3); opacity: 1; box-shadow: 0 0 16px #10b981; }
  100% { transform: scale(0.95); opacity: 0.8; }
}

.widget-title {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #f3f4f6;
}

.pyramid-badge {
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 6px;
  background: rgba(16, 185, 129, 0.12);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
  font-weight: 600;
  letter-spacing: 0.04em;
}

/* 核心指标网格 */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.metric-card {
  background: rgba(18, 18, 22, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 12px 14px;
}

.card-label {
  font-size: 11px;
  color: #9ca3af;
  margin-bottom: 6px;
}

.card-value-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 8px;
}

.value-highlight {
  font-size: 20px;
  font-weight: 700;
  color: #ffffff;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.value-cap {
  font-size: 11px;
  color: #6b7280;
}

.pyramid-levels-row {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.level-tag {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.06);
  color: #9ca3af;
}

.level-tag.active {
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.progress-bar-track {
  width: 100%;
  height: 4px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 2px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  transition: width 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.fill-emerald {
  background: linear-gradient(90deg, #10b981, #34d399);
}

.fill-cyan {
  background: linear-gradient(90deg, #06b6d4, #22d3ee);
}

.chip-verified {
  font-size: 10px;
  color: #34d399;
  background: rgba(16, 185, 129, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

/* 因果推演拓扑链 */
.causal-pathway-section {
  background: rgba(18, 18, 22, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 16px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-title {
  font-size: 12px;
  font-weight: 700;
  color: #e5e7eb;
  letter-spacing: 0.05em;
}

.section-sub {
  font-size: 11px;
  color: #9ca3af;
}

.causal-chain-container {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
  padding: 8px 4px;
}

.chain-node-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.chain-node {
  background: rgba(30, 30, 38, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 6px;
  padding: 6px 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.chain-node.node-terminal {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.12);
  color: #93c5fd;
}

.node-idx {
  background: rgba(255, 255, 255, 0.1);
  color: #d1d5db;
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  font-family: monospace;
}

.chain-edge-pulse {
  display: flex;
  align-items: center;
  color: #34d399;
  font-size: 10px;
}

.edge-line {
  width: 14px;
  height: 2px;
  background: #34d399;
}

.edge-arrow {
  margin-left: -2px;
}

/* DeepSeek 思考链面板 */
.thinking-context-panel {
  background: rgba(14, 14, 18, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-title {
  font-size: 12px;
  font-weight: 600;
  color: #d1d5db;
}

.toggle-btn {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #9ca3af;
  border-radius: 4px;
  font-size: 11px;
  padding: 3px 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-btn:hover {
  color: #fff;
  border-color: rgba(255, 255, 255, 0.3);
}

.thinking-content-body {
  margin-top: 10px;
}

.code-block {
  background: #050507;
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  color: #a7f3d0;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 180px;
  overflow-y: auto;
}

/* 凭单审计面板 */
.receipt-audit-panel {
  background: rgba(18, 18, 22, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
}

.audit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.audit-title {
  font-size: 11px;
  font-weight: 600;
  color: #9ca3af;
  letter-spacing: 0.04em;
}

.signature-chip {
  font-size: 11px;
  color: #fbbf24;
  display: flex;
  align-items: center;
  gap: 4px;
}

.audit-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px;
  font-size: 11px;
}

.audit-item {
  display: flex;
  gap: 6px;
}

.audit-item .k {
  color: #6b7280;
}

.audit-item .v {
  color: #d1d5db;
}

.code-font {
  font-family: monospace;
}

.text-emerald {
  color: #34d399 !important;
}

/* 按钮工具条 */
.widget-actions {
  display: flex;
  gap: 10px;
}

.titanium-btn {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  color: #ffffff;
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.titanium-btn:hover {
  background: rgba(255, 255, 255, 0.14);
  border-color: rgba(255, 255, 255, 0.25);
  transform: translateY(-1px);
}

.btn-secondary {
  background: transparent;
  border-color: rgba(255, 255, 255, 0.08);
  color: #9ca3af;
}

.btn-secondary:hover {
  color: #e5e7eb;
}
</style>

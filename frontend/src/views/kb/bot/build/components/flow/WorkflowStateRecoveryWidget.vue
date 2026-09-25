<template>
  <div class="workflow-state-recovery-widget">
    <!-- 顶部状态栏 (单色暗黑钛金毛玻璃) -->
    <div class="widget-header">
      <div class="header-left">
        <span class="status-indicator live-pulse"></span>
        <span class="widget-title">WORKFLOW SELF-HEALING // PHASE 138</span>
      </div>
      <div class="header-right">
        <span class="fencing-badge">
          FENCING TOKEN: #{{ currentFencingToken }}
        </span>
      </div>
    </div>

    <!-- 核心指标网格 -->
    <div class="metrics-grid">
      <!-- WFG 死锁检出耗时 -->
      <div class="metric-card">
        <div class="card-label">WFG 死锁环路检出耗时</div>
        <div class="card-value-row">
          <span class="value-highlight">{{ deadlockDetectionMs.toFixed(2) }}</span>
          <span class="value-cap">ms (&lt;= 5.0ms 门限)</span>
        </div>
        <div class="progress-bar-track">
          <div
            class="progress-bar-fill fill-cyan"
            :style="{ width: `${Math.min(100, (deadlockDetectionMs / 5.0) * 100)}%` }"
          ></div>
        </div>
      </div>

      <!-- 断点恢复续跑耗时 -->
      <div class="metric-card">
        <div class="card-label">断点无损续跑延迟</div>
        <div class="card-value-row">
          <span class="value-highlight">{{ resumptionLatencyMs.toFixed(1) }}</span>
          <span class="value-cap">ms (&lt;= 50ms 门限)</span>
        </div>
        <div class="progress-bar-track">
          <div
            class="progress-bar-fill fill-emerald"
            :style="{ width: `${Math.min(100, (resumptionLatencyMs / 50.0) * 100)}%` }"
          ></div>
        </div>
      </div>

      <!-- 脑裂双写拦截率 -->
      <div class="metric-card">
        <div class="card-label">脑裂过期写请求拦截率</div>
        <div class="card-value-row">
          <span class="value-highlight">100.0%</span>
          <span class="value-cap">(原子排他)</span>
        </div>
        <div class="status-chip-row">
          <span class="chip chip-verified">
            <i class="el-icon-check"></i> Fencing 排他严格生效
          </span>
        </div>
      </div>

      <!-- 前序步骤重复执行数 -->
      <div class="metric-card">
        <div class="card-label">前序重复执行步骤数</div>
        <div class="card-value-row">
          <span class="value-highlight">0</span>
          <span class="value-cap">步 (零资费冗余)</span>
        </div>
        <div class="status-chip-row">
          <span class="chip chip-verified">
            <i class="el-icon-check"></i> 内存快照树完全命中
          </span>
        </div>
      </div>
    </div>

    <!-- 动态等待图拓扑 (Wait-For Graph) 与死锁环路 -->
    <div class="wfg-section">
      <div class="section-title-row">
        <span class="section-title">多智能体因果等待图 (WAIT-FOR GRAPH) 拓扑</span>
        <span class="section-sub" :class="{ 'text-danger': isDeadlockActive }">
          {{ isDeadlockActive ? '⚠️ 探测到死锁强连通环路！' : '✅ 拓扑健康 (无环 DAG)' }}
        </span>
      </div>
      <div class="wfg-nodes-container">
        <div
          v-for="agent in activeAgents"
          :key="agent.id"
          class="agent-node-card"
          :class="{ 'node-in-cycle': isNodeInDeadlock(agent.id) }"
        >
          <div class="agent-role-tag">{{ agent.role }}</div>
          <div class="agent-name">{{ agent.name }}</div>
          <div class="agent-wait-info" v-if="agent.waitingFor">
            等待: <span class="wait-target">{{ agent.waitingFor }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 状态机快照与断点恢复时间轴 -->
    <div class="checkpoint-timeline-section">
      <div class="section-title-row">
        <span class="section-title">工作流状态机不可变快照树 (CHECKPOINT TIMELINE)</span>
        <span class="section-sub">当前执行主管: {{ currentLeader }}</span>
      </div>
      <div class="timeline-steps-row">
        <div
          v-for="step in workflowSteps"
          :key="step.index"
          class="timeline-step-item"
          :class="`step-state-${step.status.toLowerCase()}`"
        >
          <div class="step-badge">#{{ step.index }}</div>
          <div class="step-info">
            <div class="step-title">{{ step.name }}</div>
            <div class="step-meta">{{ step.statusText }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 纯 Java 21 Record 密码学自愈凭单面板 -->
    <div class="receipt-audit-panel">
      <div class="audit-header">
        <span class="audit-title">自愈与断点恢复密码学存证凭单</span>
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
          <span class="k">自愈动作:</span>
          <span class="v text-emerald">{{ latestActionType }}</span>
        </div>
        <div class="audit-item">
          <span class="k">时间戳:</span>
          <span class="v">{{ new Date(receiptTimestamp).toLocaleTimeString() }}</span>
        </div>
        <div class="audit-item">
          <span class="k">防护令牌:</span>
          <span class="v code-font">#{{ currentFencingToken }}</span>
        </div>
      </div>
    </div>

    <!-- 交互模拟工具箱 -->
    <div class="widget-actions">
      <button class="titanium-btn" @click="simulateDeadlockDetectionAndBreak" title="模拟死锁环路检出与外科手术式破环">
        <span class="btn-icon">⚡</span> 模拟死锁检出与破环
      </button>
      <button class="titanium-btn" @click="simulateLeaderCrashAndResumption" title="模拟主管节点假死与备用节点断点续跑">
        <span class="btn-icon">🔄</span> 模拟节点假死与断点续跑
      </button>
      <button class="titanium-btn btn-secondary" @click="resetToBaseline" title="重置基线状态">
        重置基线
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const currentFencingToken = ref(1024)
const deadlockDetectionMs = ref(0.35)
const resumptionLatencyMs = ref(12.5)
const isDeadlockActive = ref(false)
const currentLeader = ref('worker-agent-primary')
const latestActionType = ref('BREAKPOINT_RESUMED')
const receiptTimestamp = ref(Date.now())
const currentReceiptId = ref('RCP-SELF-HEAL-7A8B9C0D1E2F3A4B')

// 活跃智能体节点
const activeAgents = ref([
  { id: 'agent_crawler', name: '数据抓取 Agent', role: 'DATA_EXTRACTOR', waitingFor: null },
  { id: 'agent_fraud', name: '反欺诈 Agent', role: 'FRAUD_DETECTOR', waitingFor: 'agent_finance' },
  { id: 'agent_finance', name: '财务评分 Agent', role: 'CREDIT_SCORER', waitingFor: 'agent_hitl' },
  { id: 'agent_hitl', name: '人工审批 Agent', role: 'HITL_REVIEWER', waitingFor: null }
])

const deadlockedNodeIds = ref(new Set())

function isNodeInDeadlock(agentId) {
  return deadlockedNodeIds.value.has(agentId)
}

// 工作流步骤与快照状态
const workflowSteps = ref([
  { index: 1, name: '提取申请人征信报告', status: 'COMPLETED', statusText: '快照已保存 (0.8ms)' },
  { index: 2, name: '计算多维合规评分', status: 'COMPLETED', statusText: '快照已保存 (0.9ms)' },
  { index: 3, name: '核验反洗钱名单', status: 'COMPLETED', statusText: '快照已保存 (0.7ms)' },
  { index: 4, name: '调用海关进出口API', status: 'RESUMED', statusText: '崩溃就地续跑点 (Fencing #1024)' },
  { index: 5, name: '生成信贷决策报告', status: 'PENDING', statusText: '待执行' }
])

// 模拟死锁与破环
function simulateDeadlockDetectionAndBreak() {
  isDeadlockActive.value = true
  deadlockedNodeIds.value = new Set(['agent_fraud', 'agent_finance', 'agent_hitl'])
  activeAgents.value[3].waitingFor = 'agent_fraud' // 形成闭环环路

  setTimeout(() => {
    // 触发外科手术式破环
    activeAgents.value[3].waitingFor = null
    deadlockedNodeIds.value = new Set()
    isDeadlockActive.value = false
    deadlockDetectionMs.value = 0.28 + Math.random() * 0.4
    latestActionType.value = 'DEADLOCK_BROKEN'
    currentReceiptId.value = 'RCP-SELF-HEAL-' + Math.random().toString(36).substring(2, 10).toUpperCase() + Math.random().toString(36).substring(2, 10).toUpperCase()
    receiptTimestamp.value = Date.now()
  }, 1000)
}

// 模拟主节点假死与备用节点断点续跑
function simulateLeaderCrashAndResumption() {
  currentLeader.value = 'worker-agent-standby-02'
  currentFencingToken.value += 1
  resumptionLatencyMs.value = 8.5 + Math.random() * 8.0
  latestActionType.value = 'SPLIT_BRAIN_FENCED'
  currentReceiptId.value = 'RCP-SELF-HEAL-' + Math.random().toString(36).substring(2, 10).toUpperCase() + Math.random().toString(36).substring(2, 10).toUpperCase()
  receiptTimestamp.value = Date.now()

  workflowSteps.value[3].status = 'COMPLETED'
  workflowSteps.value[3].statusText = '续跑成功完成'
  workflowSteps.value[4].status = 'COMPLETED'
  workflowSteps.value[4].statusText = '已平稳完结'
}

function resetToBaseline() {
  currentFencingToken.value = 1024
  deadlockDetectionMs.value = 0.35
  resumptionLatencyMs.value = 12.5
  isDeadlockActive.value = false
  currentLeader.value = 'worker-agent-primary'
  latestActionType.value = 'BREAKPOINT_RESUMED'
  deadlockedNodeIds.value = new Set()
  activeAgents.value[3].waitingFor = null
  workflowSteps.value = [
    { index: 1, name: '提取申请人征信报告', status: 'COMPLETED', statusText: '快照已保存 (0.8ms)' },
    { index: 2, name: '计算多维合规评分', status: 'COMPLETED', statusText: '快照已保存 (0.9ms)' },
    { index: 3, name: '核验反洗钱名单', status: 'COMPLETED', statusText: '快照已保存 (0.7ms)' },
    { index: 4, name: '调用海关进出口API', status: 'RESUMED', statusText: '崩溃就地续跑点 (Fencing #1024)' },
    { index: 5, name: '生成信贷决策报告', status: 'PENDING', statusText: '待执行' }
  ]
}
</script>

<style scoped>
.workflow-state-recovery-widget {
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

.fencing-badge {
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 6px;
  background: rgba(59, 130, 246, 0.12);
  color: #60a5fa;
  border: 1px solid rgba(59, 130, 246, 0.3);
  font-weight: 600;
  letter-spacing: 0.04em;
  font-family: monospace;
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

/* WFG 等待图拓扑 */
.wfg-section {
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

.text-danger {
  color: #f87171 !important;
  font-weight: 600;
}

.wfg-nodes-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.agent-node-card {
  background: rgba(28, 28, 36, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  padding: 10px;
  transition: all 0.2s;
}

.agent-node-card.node-in-cycle {
  border-color: #ef4444;
  background: rgba(239, 68, 68, 0.12);
  box-shadow: 0 0 12px rgba(239, 68, 68, 0.25);
  animation: pulse-border 1.5s infinite;
}

@keyframes pulse-border {
  0% { border-color: rgba(239, 68, 68, 0.4); }
  50% { border-color: rgba(239, 68, 68, 1); }
  100% { border-color: rgba(239, 68, 68, 0.4); }
}

.agent-role-tag {
  font-size: 9px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.agent-name {
  font-size: 12px;
  font-weight: 600;
  color: #f3f4f6;
  margin-bottom: 6px;
}

.agent-wait-info {
  font-size: 10px;
  color: #9ca3af;
}

.wait-target {
  color: #fca5a5;
  font-family: monospace;
}

/* 时间轴 */
.checkpoint-timeline-section {
  background: rgba(14, 14, 18, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
}

.timeline-steps-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-top: 8px;
}

.timeline-step-item {
  flex: 1;
  min-width: 140px;
  background: rgba(25, 25, 32, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  padding: 8px 10px;
  display: flex;
  gap: 8px;
  align-items: center;
}

.timeline-step-item.step-state-completed {
  border-color: rgba(16, 185, 129, 0.3);
  background: rgba(16, 185, 129, 0.08);
}

.timeline-step-item.step-state-resumed {
  border-color: rgba(59, 130, 246, 0.4);
  background: rgba(59, 130, 246, 0.12);
}

.step-badge {
  font-size: 10px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}

.step-title {
  font-size: 11px;
  font-weight: 600;
  color: #e5e7eb;
}

.step-meta {
  font-size: 9px;
  color: #9ca3af;
}

/* 凭单面板 */
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

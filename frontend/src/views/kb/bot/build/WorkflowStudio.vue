<template>
  <div class="workflow-studio-container">
    <!-- 顶部单色钛金控制栏 -->
    <header class="studio-topbar">
      <div class="topbar-left">
        <div class="brand-badge">
          <span class="pulse-indicator"></span>
          <span class="brand-title">Workflow Studio</span>
          <span class="version-tag">v{{ currentAst?.version || 1 }}</span>
        </div>
        <div class="workflow-meta">
          <span class="meta-item"><span class="meta-label">ID:</span> {{ currentAst?.workflowId || 'wf_visual_studio_01' }}</span>
          <span class="meta-item"><span class="meta-label">纪元:</span> Ep.{{ epochVersion }}</span>
          <span class="meta-item" v-if="isSuspended">
            <span class="status-chip warning">DSL 语法挂起</span>
          </span>
          <span class="meta-item" v-else>
            <span class="status-chip active">双向同步就绪</span>
          </span>
          <span class="meta-item" v-if="activeBranchId">
            <span class="status-chip branch">分叉: {{ activeBranchId }}</span>
          </span>
        </div>
      </div>

      <div class="topbar-center">
        <div class="view-mode-switch">
          <button
            class="mode-btn"
            :class="{ active: viewMode === 'SPLIT' }"
            @click="setViewMode('SPLIT')"
          >
            分屏联动
          </button>
          <button
            class="mode-btn"
            :class="{ active: viewMode === 'CANVAS' }"
            @click="setViewMode('CANVAS')"
          >
            纯画布
          </button>
          <button
            class="mode-btn"
            :class="{ active: viewMode === 'CODE' }"
            @click="setViewMode('CODE')"
          >
            DSL 代码
          </button>
        </div>
      </div>

      <div class="topbar-right">
        <!-- 自动排版按钮 (Sugiyama 布局) -->
        <button class="action-btn" @click="triggerAutoLayout" title="使用 Sugiyama 分层有向图算法自动消除交叉">
          一键排版
        </button>
        <button class="action-btn" @click="triggerFormatDsl">
          格式化
        </button>
        <button class="action-btn" @click="toggleTracePanel">
          {{ showTracePanel ? '收起调试中枢' : '展开调试中枢' }}
        </button>
        <button class="action-btn" @click="simulateDualTrackStream" title="Phase 134: 模拟 SSE 双轨 Token 与拓扑流光垂直同步">
          模拟双轨流
        </button>
        <button class="action-btn warning" @click="simulateHitlSuspension" title="Phase 134: 模拟人机协同高危写操作抽屉挂起">
          模拟 HITL 审批
        </button>
        <button class="action-btn primary" @click="onSaveWorkflow">
          保存并签署
        </button>
      </div>
    </header>

    <!-- 主工作区：分屏 / 单屏 -->
    <main class="studio-main-workspace" :class="`mode-${viewMode.toLowerCase()}`">
      <!-- 左侧 Monaco 代码编辑面板 -->
      <div class="editor-pane" v-show="viewMode === 'SPLIT' || viewMode === 'CODE'">
        <div class="pane-header">
          <span class="pane-title">声明式 DSL (支持 Schema 智能感知)</span>
          <div class="header-tools">
            <span class="error-count-tag" v-if="diagnostics.length > 0">
              {{ diagnostics.length }} 个语法问题
            </span>
          </div>
        </div>
        <div class="editor-body">
          <textarea
            ref="codeTextareaRef"
            class="code-textarea"
            v-model="dslCode"
            @input="onCodeInput"
            spellcheck="false"
            placeholder="在此输入声明式 DSL (YAML 或 JSON 格式)..."
          ></textarea>
        </div>
        <!-- 语法错误诊断栏 -->
        <div class="diagnostics-bar" v-if="diagnostics.length > 0">
          <div v-for="(diag, idx) in diagnostics" :key="idx" class="diag-item error">
            <span class="diag-pos">Line {{ diag.startLineNumber }}:</span>
            <span class="diag-msg">{{ diag.message }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧 VueFlow 可视化图形画布 -->
      <div class="canvas-pane" v-show="viewMode === 'SPLIT' || viewMode === 'CANVAS'">
        <div class="pane-header">
          <span class="pane-title">拓扑交互画布 (六大多态节点)</span>
          <div class="node-palette">
            <button class="palette-chip task" @click="addNode('TASK')">+ TASK</button>
            <button class="palette-chip loop" @click="addNode('STATE_GRAPH_LOOP')">+ LOOP</button>
            <button class="palette-chip swarm" @click="addNode('SWARM_HANDOFF')">+ SWARM</button>
            <button class="palette-chip debate" @click="addNode('DEBATE_ARENA')">+ DEBATE</button>
            <button class="palette-chip hitl" @click="addNode('HITL_APPROVAL')">+ HITL</button>
            <button class="palette-chip mcp" @click="addNode('MCP_TOOL_CALL')">+ MCP</button>
          </div>
        </div>

        <div class="canvas-body" ref="canvasContainerRef">
          <VueFlow
            :nodes="flowNodes"
            :edges="flowEdges"
            class="vue-flow-stage"
            :default-zoom="1"
            :min-zoom="0.2"
            :max-zoom="2"
            :fit-view-on-init="true"
            @nodes-change="onCanvasNodesChange"
            @edges-change="onCanvasEdgesChange"
            @node-click="onNodeClick"
          >
            <!-- 多态节点卡片插槽 (钛金毛玻璃质感) -->
            <template #node-task-node="props">
              <div class="titanium-node task-card" :class="{ 'is-selected': props.id === activeNodeId }">
                <div class="node-head">
                  <span class="node-badge task">TASK</span>
                  <span class="node-id">#{{ props.id }}</span>
                </div>
                <div class="node-title">{{ props.data.name }}</div>
                <div class="node-sub" v-if="props.data.objective">{{ props.data.objective }}</div>
              </div>
            </template>

            <template #node-loop-node="props">
              <div class="titanium-node loop-card" :class="{ 'is-selected': props.id === activeNodeId }">
                <div class="node-head">
                  <span class="node-badge loop">LOOP</span>
                  <span class="loop-iter">Max 10</span>
                </div>
                <div class="node-title">{{ props.data.name }}</div>
                <div class="node-sub">状态图有界循环</div>
              </div>
            </template>

            <template #node-swarm-node="props">
              <div class="titanium-node swarm-card" :class="{ 'is-selected': props.id === activeNodeId }">
                <div class="node-head">
                  <span class="node-badge swarm">SWARM</span>
                  <span class="node-id">1536D</span>
                </div>
                <div class="node-title">{{ props.data.name }}</div>
                <div class="node-sub">动态上下文交接</div>
              </div>
            </template>

            <template #node-debate-node="props">
              <div class="titanium-node debate-card" :class="{ 'is-selected': props.id === activeNodeId }">
                <div class="node-head">
                  <span class="node-badge debate">DEBATE</span>
                  <span class="node-id">多方对抗</span>
                </div>
                <div class="node-title">{{ props.data.name }}</div>
                <div class="node-sub">结构化辩论裁决</div>
              </div>
            </template>

            <template #node-hitl-node="props">
              <div class="titanium-node hitl-card" :class="{ 'is-selected': props.id === activeNodeId }">
                <div class="node-head">
                  <span class="node-badge hitl">HITL</span>
                  <span class="node-id">审批挂起</span>
                </div>
                <div class="node-title">{{ props.data.name }}</div>
                <div class="node-sub">人工安全核验</div>
              </div>
            </template>

            <template #node-mcp-node="props">
              <div class="titanium-node mcp-card" :class="{ 'is-selected': props.id === activeNodeId }">
                <div class="node-head">
                  <span class="node-badge mcp">MCP</span>
                  <span class="node-id">Tool</span>
                </div>
                <div class="node-title">{{ props.data.name }}</div>
                <div class="node-sub" v-if="props.data.mcpToolName">{{ props.data.mcpToolName }}</div>
              </div>
            </template>
          </VueFlow>
        </div>
      </div>
    </main>

    <!-- 底部可折叠时空调试与 OpenTelemetry 瀑布流中枢 -->
    <section class="studio-bottom-debugger" v-if="showTracePanel">
      <div class="debugger-toolbar">
        <span class="debugger-title">时空调试中枢 (Time-Travel Debugger)</span>
        <div class="fork-controls" v-if="activeNodeId">
          <button class="fork-btn" @click="triggerForkExecution">
            在 #{{ activeNodeId }} 处派生分叉执行 (Fork & Resume)
          </button>
        </div>
      </div>
      <TraceWaterfall
        :trace-spans="sampleTraceSpans"
        :active-node-id="activeNodeId"
        @span-click="onTraceSpanClick"
      />
    </section>

    <!-- Phase 134: 单色钛金毛玻璃人机协同决策抽屉 (HITL Metacenter) -->
    <HitlTitaniumCausticDrawer
      :visible="hitlDrawerVisible"
      :workflow-id="currentAst?.workflowId || 'wf_visual_studio_01'"
      :node-id="hitlActiveNodeId"
      :step-index="hitlStepIndex"
      :branch-id="activeBranchId || 'main'"
      operator-id="admin_auditor"
      :original-params="hitlOriginalParams"
      :candidate-params="hitlCandidateParams"
      :reasoning-content="hitlReasoningContent"
      @approved="onHitlApproved"
      @rejected="onHitlRejected"
      @close="hitlDrawerVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { VueFlow, useVueFlow } from '@vue-flow/core';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import {
  DslCanvasBiDirectionalSyncEngine,
  type DslWorkflowAst,
  type MarkerDiagnostic
} from './components/sync/DslCanvasBiDirectionalSyncEngine';
import { SugiyamaLayoutEngine } from './components/layout/SugiyamaLayoutEngine';
import { TimeTravelForkEngine, type StepSnapshot } from './components/debug/engine/TimeTravelForkEngine';
import TraceWaterfall from './components/trace/TraceWaterfall.vue';
import type { RawTraceSpan } from '@/views/kd/observability/engine/WaterfallVirtualTimelineEngine';
import { StreamingDualTrackSyncScheduler } from './components/canvas/engine/StreamingDualTrackSyncScheduler';
import { TimeTravelBranchForkController } from './components/debug/engine/TimeTravelBranchForkController';
import HitlTitaniumCausticDrawer from './components/hitl/HitlTitaniumCausticDrawer.vue';
import type { HitlFrontendAuditReceipt } from './components/hitl/receipt/HitlFrontendAuditReceipt';

const syncEngine = new DslCanvasBiDirectionalSyncEngine();
const layoutEngine = new SugiyamaLayoutEngine();
const forkEngine = new TimeTravelForkEngine();
const dualTrackScheduler = new StreamingDualTrackSyncScheduler();
const timeTravelBranchController = new TimeTravelBranchForkController();
const { fitView } = useVueFlow();

const viewMode = ref<'SPLIT' | 'CANVAS' | 'CODE'>('SPLIT');
const showTracePanel = ref<boolean>(true);
const epochVersion = ref<number>(1);
const isSuspended = ref<boolean>(false);
const activeNodeId = ref<string>('');
const activeBranchId = ref<string>('');
const diagnostics = ref<MarkerDiagnostic[]>([]);
const currentAst = ref<DslWorkflowAst | null>(null);

// Phase 134: HITL 抽屉交互状态
const hitlDrawerVisible = ref<boolean>(false);
const hitlActiveNodeId = ref<string>('node_hitl_01');
const hitlStepIndex = ref<number>(4);
const hitlOriginalParams = ref<Record<string, unknown>>({
  transferAmount: 500000.0,
  targetAccount: "6222021000987654321",
  currency: "CNY",
  traceId: "trace_hitl_001",
  timeout: 30
});
const hitlCandidateParams = ref<Record<string, unknown>>({
  transferAmount: 850000.0, // 变异破坏性写操作参数
  targetAccount: "6222021000987654321",
  currency: "CNY",
  traceId: "trace_hitl_001",
  timeout: 30
});
const hitlReasoningContent = ref<string>(
  "已完成前置风控模型与图谱关联分析，发现当前大额调增指令存在潜在信用敞口，故挂起工单等待人工二次核验。"
);

const flowNodes = ref<any[]>([]);
const flowEdges = ref<any[]>([]);

// 初始示范 DSL
const initialDsl = JSON.stringify({
  workflowId: "wf_agentic_studio_01",
  name: "企业级多智能体协同与知识检索流",
  version: 1,
  nodes: [
    {
      nodeId: "node_task_01",
      name: "需求意图理解",
      nodeType: "TASK",
      objective: "分析用户复杂财务问题",
      x: 80,
      y: 120
    },
    {
      nodeId: "node_mcp_01",
      name: "数据库查询工具",
      nodeType: "MCP_TOOL_CALL",
      mcpToolName: "enterprise_sql_query",
      x: 360,
      y: 120
    },
    {
      nodeId: "node_loop_01",
      name: "迭代反思修正",
      nodeType: "STATE_GRAPH_LOOP",
      x: 640,
      y: 120
    },
    {
      nodeId: "node_hitl_01",
      name: "高危交易审批",
      nodeType: "HITL_APPROVAL",
      x: 920,
      y: 120
    }
  ],
  edges: [
    {
      edgeId: "edge_01_02",
      sourceNodeId: "node_task_01",
      targetNodeId: "node_mcp_01"
    },
    {
      edgeId: "edge_02_03",
      sourceNodeId: "node_mcp_01",
      targetNodeId: "node_loop_01"
    },
    {
      edgeId: "edge_03_04",
      sourceNodeId: "node_loop_01",
      targetNodeId: "node_hitl_01"
    }
  ]
}, null, 2);

const dslCode = ref<string>(initialDsl);

// 示例 Trace Spans
const sampleTraceSpans = ref<RawTraceSpan[]>([
  {
    spanId: "span_root",
    traceId: "trace_20260920_001",
    parentSpanId: null,
    spanName: "WorkflowExecution: wf_agentic_studio_01",
    spanType: "WORKFLOW",
    startNano: 1000000000,
    durationUs: 450000,
    tokenCount: 1420,
    status: "OK",
    summaryInput: "执行全流程",
    summaryOutput: "完成",
    attributes: { "dsl.node_id": "node_task_01" }
  },
  {
    spanId: "span_task_1",
    traceId: "trace_20260920_001",
    parentSpanId: "span_root",
    spanName: "IntentParser: node_task_01",
    spanType: "AGENT",
    startNano: 1050000000,
    durationUs: 120000,
    tokenCount: 450,
    status: "OK",
    summaryInput: "用户 Query",
    summaryOutput: "意图结构化",
    attributes: { "dsl.node_id": "node_task_01" }
  },
  {
    spanId: "span_mcp_1",
    traceId: "trace_20260920_001",
    parentSpanId: "span_root",
    spanName: "McpCall: enterprise_sql_query",
    spanType: "MCP",
    startNano: 1180000000,
    durationUs: 150000,
    tokenCount: 180,
    status: "OK",
    summaryInput: "SELECT * FROM finance",
    summaryOutput: "Rows returned",
    attributes: { "dsl.node_id": "node_mcp_01" }
  },
  {
    spanId: "span_loop_1",
    traceId: "trace_20260920_001",
    parentSpanId: "span_root",
    spanName: "LoopEvaluator: node_loop_01",
    spanType: "AGENT",
    startNano: 1340000000,
    durationUs: 80000,
    tokenCount: 390,
    status: "OK",
    summaryInput: "核算结果",
    summaryOutput: "满足条件",
    attributes: { "dsl.node_id": "node_loop_01" }
  },
  {
    spanId: "span_hitl_1",
    traceId: "trace_20260920_001",
    parentSpanId: "span_root",
    spanName: "HitlWait: node_hitl_01",
    spanType: "HITL",
    startNano: 1430000000,
    durationUs: 20000,
    tokenCount: 0,
    status: "OK",
    summaryInput: "工单挂起",
    summaryOutput: "已批准",
    attributes: { "dsl.node_id": "node_hitl_01" }
  }
]);

onMounted(() => {
  applyCodeToCanvas(dslCode.value);
});

function setViewMode(mode: 'SPLIT' | 'CANVAS' | 'CODE') {
  viewMode.value = mode;
}

function toggleTracePanel() {
  showTracePanel.value = !showTracePanel.value;
}

function onCodeInput() {
  applyCodeToCanvas(dslCode.value);
}

function applyCodeToCanvas(code: string) {
  const result = syncEngine.syncCodeToCanvas(code);
  epochVersion.value = result.epochVersion;
  diagnostics.value = result.diagnostics;
  isSuspended.value = result.isSuspended;

  if (result.success && result.ast) {
    currentAst.value = result.ast;
    flowNodes.value = result.nodes;
    flowEdges.value = result.edges;
  }
}

async function onCanvasNodesChange() {
  if (syncEngine.isCodeSyncing()) return;
  const updatedCode = await syncEngine.syncCanvasToCode(flowNodes.value, flowEdges.value);
  epochVersion.value = syncEngine.getEpochVersion();
  dslCode.value = updatedCode;
}

async function onCanvasEdgesChange() {
  if (syncEngine.isCodeSyncing()) return;
  const updatedCode = await syncEngine.syncCanvasToCode(flowNodes.value, flowEdges.value);
  epochVersion.value = syncEngine.getEpochVersion();
  dslCode.value = updatedCode;
}

function onNodeClick(event: any) {
  const nodeId = event.node?.id;
  if (nodeId) {
    activeNodeId.value = nodeId;
  }
}

function onTraceSpanClick(payload: { nodeId: string | null }) {
  if (payload.nodeId) {
    activeNodeId.value = payload.nodeId;
    try {
      fitView({ nodes: [payload.nodeId], duration: 400 });
    } catch {
      // 容错忽略
    }
  }
}

/**
 * 触发 Sugiyama 分层有向图自动排版
 */
function triggerAutoLayout() {
  if (flowNodes.value.length === 0) return;
  const layoutRes = layoutEngine.layout(flowNodes.value, flowEdges.value, { direction: 'LR' });
  flowNodes.value = layoutRes.nodes;
  onCanvasNodesChange();
  setTimeout(() => {
    try {
      fitView({ duration: 400 });
    } catch {}
  }, 50);
}

/**
 * 触发时空快照现场分叉执行
 */
function triggerForkExecution() {
  if (!activeNodeId.value) return;

  const mockSnapshots: StepSnapshot[] = sampleTraceSpans.value.map((s, idx) => ({
    stepIndex: idx,
    nodeId: s.attributes?.['dsl.node_id'] || `node_${idx}`,
    nodeName: s.spanName,
    inputs: { query: '原始参数' },
    outputs: { result: '原始输出' },
    timestamp: Date.now() - (sampleTraceSpans.value.length - idx) * 1000,
    tokenCount: s.tokenCount
  }));

  const targetIdx = mockSnapshots.findIndex(s => s.nodeId === activeNodeId.value);
  const forkStep = targetIdx >= 0 ? targetIdx : 0;

  const branch = forkEngine.forkFromStep(
    'STU_live_session_receipt',
    mockSnapshots,
    forkStep,
    { inputs: { mutated: true, mockMode: 'ACTIVE' } }
  );

  activeBranchId.value = branch.branchId;

  // 同步在持久化分支树控制器中记录
  timeTravelBranchController.recordSnapshot(
    forkStep,
    activeNodeId.value,
    { mutated: true, mockMode: 'ACTIVE' },
    '分叉快照执行完毕',
    'COMPLETED',
    branch.branchId
  );
}

/**
 * Phase 134: 模拟 SSE 双轨 Token 与拓扑流光垂直同步调度
 */
function simulateDualTrackStream() {
  const targetNodeId = activeNodeId.value || 'node_task_01';
  dualTrackScheduler.start();

  // 模拟以 100 tokens/s 推入 10 个离散双轨帧
  for (let i = 0; i < 10; i++) {
    dualTrackScheduler.pushStreamFrame({
      sequenceId: 1000 + i,
      timestamp: Date.now() + i * 10,
      type: 'TOKEN',
      nodeId: targetNodeId,
      payload: { tokenDelta: `[Token_${i}] ` }
    });
    dualTrackScheduler.pushStreamFrame({
      sequenceId: 1000 + i,
      timestamp: Date.now() + i * 10,
      type: 'TOPOLOGY_EVENT',
      nodeId: targetNodeId,
      payload: { nodeState: 'RUNNING', edgePulseActive: true }
    });
  }

  // 触发一次批量刷新
  const batch = dualTrackScheduler.flushSyncNow();
  console.log(`[Phase 134] 双轨流式刷新批次 #${batch.batchId} 耗时: ${batch.flushDurationMs}ms, 时差: ${batch.maxVisualDisparityMs}ms`);
}

/**
 * Phase 134: 模拟人机协同高危操作抽屉挂起
 */
function simulateHitlSuspension() {
  hitlActiveNodeId.value = activeNodeId.value || 'node_hitl_01';
  hitlDrawerVisible.value = true;
}

function onHitlApproved(payload: { receipt: HitlFrontendAuditReceipt; finalParams: Record<string, unknown> }) {
  console.log('[Phase 134] HITL 审批放行凭单已生成:', payload.receipt.receiptId, '验真:', payload.receipt.verifySignature());
  hitlDrawerVisible.value = false;

  // 记录恢复快照
  timeTravelBranchController.recordSnapshot(
    hitlStepIndex.value + 1,
    hitlActiveNodeId.value,
    payload.finalParams,
    '审批放行恢复执行',
    'COMPLETED'
  );
}

function onHitlRejected(payload: { receipt: HitlFrontendAuditReceipt; reason: string }) {
  console.log('[Phase 134] HITL 审批否决凭单已生成:', payload.receipt.receiptId, '原因:', payload.reason);
  hitlDrawerVisible.value = false;
}

function addNode(type: 'TASK' | 'STATE_GRAPH_LOOP' | 'SWARM_HANDOFF' | 'DEBATE_ARENA' | 'HITL_APPROVAL' | 'MCP_TOOL_CALL') {
  const newId = `node_${type.toLowerCase()}_${Date.now().toString().slice(-4)}`;
  const newNode = {
    id: newId,
    type: mapTypeToVueFlow(type),
    label: `${type} 节点`,
    position: { x: 100 + flowNodes.value.length * 40, y: 150 + (flowNodes.value.length % 3) * 60 },
    data: {
      nodeId: newId,
      name: `新建 ${type}`,
      nodeType: type,
      timeoutSeconds: 30,
      config: {}
    }
  };
  flowNodes.value.push(newNode);
  onCanvasNodesChange();
}

function mapTypeToVueFlow(type: string): string {
  switch (type) {
    case 'STATE_GRAPH_LOOP': return 'loop-node';
    case 'SWARM_HANDOFF': return 'swarm-node';
    case 'DEBATE_ARENA': return 'debate-node';
    case 'HITL_APPROVAL': return 'hitl-node';
    case 'MCP_TOOL_CALL': return 'mcp-node';
    default: return 'task-node';
  }
}

function triggerFormatDsl() {
  if (currentAst.value) {
    dslCode.value = syncEngine.serializeAstToDsl(currentAst.value);
  }
}

function onSaveWorkflow() {
  epochVersion.value++;
}
</script>

<style scoped>
.workflow-studio-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
  background: #020203;
  color: #e4e4e7;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

/* 顶部钛金控制栏 */
.studio-topbar {
  height: 52px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  background: rgba(10, 10, 12, 0.85);
  backdrop-filter: blur(24px) saturate(190%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  z-index: 20;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.brand-badge {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pulse-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 8px #38bdf8;
}

.brand-title {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #ffffff;
}

.version-tag {
  font-size: 11px;
  padding: 1px 6px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 4px;
  color: #a1a1aa;
}

.workflow-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
}

.meta-label {
  color: #71717a;
}

.status-chip {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.status-chip.active {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
}

.status-chip.warning {
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
}

.status-chip.branch {
  background: rgba(168, 85, 247, 0.2);
  color: #c084fc;
  border: 1px solid rgba(168, 85, 247, 0.4);
}

.topbar-center {
  display: flex;
  align-items: center;
}

.view-mode-switch {
  display: flex;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 6px;
  padding: 2px;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.mode-btn {
  padding: 5px 14px;
  font-size: 12px;
  border: none;
  background: transparent;
  color: #a1a1aa;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.mode-btn.active {
  background: rgba(255, 255, 255, 0.15);
  color: #ffffff;
  font-weight: 600;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-btn {
  padding: 6px 14px;
  font-size: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
  color: #e4e4e7;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.action-btn.primary {
  background: #2563eb;
  border-color: #3b82f6;
  color: #ffffff;
}

.action-btn.primary:hover {
  background: #1d4ed8;
}

/* 主工作区 */
.studio-main-workspace {
  flex: 1;
  display: flex;
  position: relative;
  overflow: hidden;
}

.pane-header {
  height: 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px;
  background: rgba(14, 14, 18, 0.8);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 12px;
  font-weight: 600;
  color: #a1a1aa;
}

/* 左侧代码编辑区 */
.editor-pane {
  width: 45%;
  display: flex;
  flex-direction: column;
  background: #09090b;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}

.mode-code .editor-pane {
  width: 100%;
}

.editor-body {
  flex: 1;
  position: relative;
}

.code-textarea {
  width: 100%;
  height: 100%;
  padding: 16px;
  background: transparent;
  color: #f4f4f5;
  font-family: 'JetBrains Mono', Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.5;
  border: none;
  resize: none;
  outline: none;
}

.diagnostics-bar {
  max-height: 100px;
  overflow-y: auto;
  background: rgba(244, 63, 94, 0.1);
  border-top: 1px solid rgba(244, 63, 94, 0.3);
  padding: 6px 12px;
  font-size: 11px;
}

.diag-item.error {
  color: #fda4af;
  display: flex;
  gap: 6px;
  line-height: 1.4;
}

.diag-pos {
  font-weight: bold;
}

.error-count-tag {
  color: #f43f5e;
  font-size: 11px;
}

/* 右侧画布区 */
.canvas-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #050508;
  position: relative;
}

.mode-canvas .canvas-pane {
  width: 100%;
}

.node-palette {
  display: flex;
  gap: 8px;
}

.palette-chip {
  padding: 2px 8px;
  font-size: 11px;
  border-radius: 4px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
  color: #d4d4d8;
  cursor: pointer;
  transition: all 0.15s ease;
}

.palette-chip:hover {
  background: rgba(255, 255, 255, 0.15);
}

.palette-chip.task { border-color: rgba(56, 189, 248, 0.4); }
.palette-chip.loop { border-color: rgba(245, 158, 11, 0.4); }
.palette-chip.swarm { border-color: rgba(168, 85, 247, 0.4); }
.palette-chip.debate { border-color: rgba(244, 63, 94, 0.4); }
.palette-chip.hitl { border-color: rgba(234, 179, 8, 0.4); }
.palette-chip.mcp { border-color: rgba(16, 185, 129, 0.4); }

.canvas-body {
  flex: 1;
  position: relative;
}

.vue-flow-stage {
  width: 100%;
  height: 100%;
}

/* 六大多态节点样式 (钛金毛玻璃) */
.titanium-node {
  min-width: 180px;
  padding: 12px 14px;
  border-radius: 8px;
  background: rgba(15, 15, 20, 0.85);
  backdrop-filter: blur(24px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
  transition: all 0.2s ease;
}

.titanium-node.is-selected {
  border-color: #38bdf8;
  box-shadow: 0 0 16px rgba(56, 189, 248, 0.5);
}

.node-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.node-badge {
  font-size: 9px;
  padding: 1px 4px;
  border-radius: 3px;
  font-weight: 700;
}

.node-badge.task { background: rgba(56, 189, 248, 0.2); color: #38bdf8; }
.node-badge.loop { background: rgba(245, 158, 11, 0.2); color: #fbbf24; }
.node-badge.swarm { background: rgba(168, 85, 247, 0.2); color: #c084fc; }
.node-badge.debate { background: rgba(244, 63, 94, 0.2); color: #fb7185; }
.node-badge.hitl { background: rgba(234, 179, 8, 0.2); color: #facc15; }
.node-badge.mcp { background: rgba(16, 185, 129, 0.2); color: #34d399; }

.node-id {
  font-size: 10px;
  color: #71717a;
}

.loop-iter {
  font-size: 10px;
  color: #fbbf24;
}

.node-title {
  font-size: 13px;
  font-weight: 600;
  color: #f4f4f5;
  margin-bottom: 4px;
}

.node-sub {
  font-size: 11px;
  color: #a1a1aa;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 底部时空调试面板 */
.studio-bottom-debugger {
  height: 240px;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 10;
}

.debugger-toolbar {
  height: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px;
  background: rgba(14, 14, 18, 0.9);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 11px;
}

.debugger-title {
  color: #a1a1aa;
  font-weight: 600;
}

.fork-btn {
  padding: 2px 10px;
  font-size: 11px;
  border-radius: 4px;
  border: 1px solid rgba(168, 85, 247, 0.4);
  background: rgba(168, 85, 247, 0.15);
  color: #c084fc;
  cursor: pointer;
  transition: all 0.2s ease;
}

.fork-btn:hover {
  background: rgba(168, 85, 247, 0.3);
}
</style>

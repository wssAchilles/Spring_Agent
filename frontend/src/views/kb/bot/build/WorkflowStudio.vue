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
        <button class="action-btn" @click="triggerFormatDsl">
          格式化
        </button>
        <button class="action-btn" @click="toggleTracePanel">
          {{ showTracePanel ? '收起调试中枢' : '展开调试中枢' }}
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
          <span class="pane-title">声明式 DSL (YAML / JSON)</span>
          <span class="error-count-tag" v-if="diagnostics.length > 0">
            {{ diagnostics.length }} 个语法问题
          </span>
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
      <TraceWaterfall
        :trace-spans="sampleTraceSpans"
        :active-node-id="activeNodeId"
        @span-click="onTraceSpanClick"
      />
    </section>
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
import TraceWaterfall from './components/trace/TraceWaterfall.vue';
import type { RawTraceSpan } from '@/views/kd/observability/engine/WaterfallVirtualTimelineEngine';

const syncEngine = new DslCanvasBiDirectionalSyncEngine();
const { fitView } = useVueFlow();

const viewMode = ref<'SPLIT' | 'CANVAS' | 'CODE'>('SPLIT');
const showTracePanel = ref<boolean>(true);
const epochVersion = ref<number>(1);
const isSuspended = ref<boolean>(false);
const activeNodeId = ref<string>('');
const diagnostics = ref<MarkerDiagnostic[]>([]);
const currentAst = ref<DslWorkflowAst | null>(null);

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

// 示例 OpenTelemetry Trace Spans (用于瀑布流联动)
const sampleTraceSpans = ref<RawTraceSpan[]>([
  {
    spanId: "span_root",
    traceId: "trace_20260919_001",
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
    traceId: "trace_20260919_001",
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
    traceId: "trace_20260919_001",
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
    traceId: "trace_20260919_001",
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
    traceId: "trace_20260919_001",
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
  // 首次载入初始化
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
  // 生成并触发保存与不可变凭单存证
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
  height: 220px;
  position: relative;
  z-index: 10;
}
</style>

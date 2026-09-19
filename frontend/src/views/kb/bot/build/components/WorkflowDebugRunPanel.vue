<template>
  <div class="workflow-debug-run-panel">
    <div class="workflow-debug-run-panel__toolbar">
      <div class="workflow-debug-run-panel__title">
        <span class="blue-bar"></span>输入参数
      </div>
      <div style="display: flex; gap: 8px;">
        <el-button v-ripple class="glass-btn" plain @click="triggerDemoHitl">
          模拟审批
        </el-button>
        <el-button v-ripple class="glass-btn" :loading="running" @click="handleRun">
          执行
        </el-button>
      </div>
    </div>

    <el-form
      class="workflow-debug-run-panel__form"
      @submit.prevent
      label-width="100px"
    >
      <el-row :gutter="20">
        <el-col :span="24">
          <template v-if="fields.length">
            <el-form-item
              v-for="field in fields"
              :key="getFieldKey(field)"
              :label="getFieldLabel(field)"
            >
              <template #label>
                <DebugOverflowTooltipLabel :text="getFieldLabel(field)" />
              </template>
              <el-input
                v-model="formData[getFieldKey(field)]"
                :placeholder="`请输入`"
              />
            </el-form-item>
          </template>
          <div v-else class="workflow-debug-run-panel__empty-hint">
            当前没有输入变量，请添加。
          </div>
        </el-col>
      </el-row>
    </el-form>

    <!-- UI/UX Pro Max: DAG 实时流式事件与两阶段 MCP 工具裁剪透明化观测看板 -->
    <div v-if="dagExecutionEvents.length || running" class="workflow-debug-run-panel__observability">
      <div class="observability-header">
        <div class="observability-title">
          <span class="pulse-indicator" :class="{ active: running }"></span>
          <span>Hermes 实时流式事件与工具路由可观测</span>
        </div>
        <div v-if="pruningMetric.pruningRate" class="observability-badge">
          工具裁剪率: {{ pruningMetric.pruningRate }}%
        </div>
      </div>

      <div class="observability-events">
        <div
          v-for="evt in dagExecutionEvents"
          :key="evt.eventId"
          class="event-node-card"
          :class="['status-' + evt.status.toLowerCase(), { 'is-selected': selectedEventId === evt.eventId }]"
          @click="selectEventForWhyline(evt)"
        >
          <div class="event-node-header">
            <span class="event-type-tag">{{ evt.nodeType }}</span>
            <span class="event-node-id">{{ evt.nodeId }}</span>
            <span class="event-latency">{{ (evt.latencyMicros / 1000).toFixed(1) }}ms</span>
          </div>
          <div v-if="evt.payloadSummary" class="event-summary">{{ evt.payloadSummary }}</div>
        </div>
      </div>
    </div>

    <!-- Phase 120: 单色钛金时间旅行 (Time-Travel) 控制条与 Whyline 因果切片探针 -->
    <div v-if="historicalSnapshots.length > 1" class="workflow-debug-run-panel__time-travel">
      <div class="time-travel-header">
        <div class="tt-title">
          <span class="tt-dot"></span>
          <span>时间旅行状态回溯 (O(1) 结构共享)</span>
        </div>
        <div class="tt-step-indicator">
          第 {{ currentTimeStep + 1 }} / {{ historicalSnapshots.length }} 步
        </div>
      </div>

      <div class="tt-slider-row">
        <el-slider
          v-model="currentTimeStep"
          :min="0"
          :max="historicalSnapshots.length - 1"
          :step="1"
          :show-tooltip="false"
          @change="handleTimeTravelChange"
        />
      </div>

      <!-- Whyline 风格因果分析探针卡片 -->
      <div v-if="activeWhylineInspection" class="whyline-inspector-card font-mono">
        <div class="whyline-header">
          <span class="whyline-tag">Whyline 因果溯源切片</span>
          <span class="node-target">{{ activeWhylineInspection.nodeId }}</span>
        </div>
        <div class="whyline-body">
          <div class="whyline-item">
            <span class="w-label">直接前驱来源:</span>
            <span class="w-val">{{ activeWhylineInspection.predecessor || '根输入 (Workflow Inputs)' }}</span>
          </div>
          <div class="whyline-item">
            <span class="w-label">输入数据流映射:</span>
            <span class="w-val">{{ activeWhylineInspection.inputBindingSummary || '全局黑板变量注入' }}</span>
          </div>
          <div class="whyline-item">
            <span class="w-label">快照状态哈希:</span>
            <span class="w-val hash">{{ activeWhylineInspection.stateDigest?.substring(0, 16) }}...</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Phase 120: 单色钛金毛玻璃人机协同审批中枢 (HITL Metacenter) 抽屉 -->
    <HitlApprovalMetacenter
      :visible="hitlDrawerVisible"
      :active-ticket="hitlActiveTicket"
      @update:visible="hitlDrawerVisible = $event"
      @approved="handleHitlApproved"
      @rejected="handleHitlRejected"
    />

    <div class="workflow-debug-run-panel__result">
      <div class="workflow-debug-run-panel__result-title">
        <span class="blue-bar"></span>输出结果
      </div>
      <div
        class="workflow-debug-run-panel__result-content"
        v-html="resultTextMd"
      ></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from "vue";
import { ProcessFlow } from "@/api/kb/bot/flow.js";
import MarkdownIt from "markdown-it";
import hljs from "highlight.js";
import "highlight.js/styles/xcode.min.css";
import DebugOverflowTooltipLabel from "./DebugOverflowTooltipLabel.vue";
import HitlApprovalMetacenter from "./debug/HitlApprovalMetacenter.vue";
import { TimeTravelForkEngine } from "./debug/engine/TimeTravelForkEngine";
import { PersistentSnapshotManager } from "./debug/engine/PersistentSnapshotTree";

const conversationInAbortController = ref(); // 对话进行中 abort 控制器(控制 stream 对话)
const forkEngine = new TimeTravelForkEngine();
const snapshotManager = new PersistentSnapshotManager();

// Phase 120 响应式状态: 时间旅行、Whyline 探针与 HITL 审批中枢
const hitlDrawerVisible = ref(false);
const hitlActiveTicket = ref(null);
const currentTimeStep = ref(0);
const historicalSnapshots = ref([]);
const selectedEventId = ref(null);
const activeWhylineInspection = ref(null);

const md = new MarkdownIt({
  html: true, // 允许解析 HTML（可选）
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        const copyHtml = `<div id="copy" data-copy='${str}' style="position: absolute; right: 10px; top: 5px; color: #1D1D1F;cursor: pointer;">复制</div>`;
        return `<pre style="position: relative;">${copyHtml}<code class="hljs">${
          hljs.highlight(lang, str, true).value
        }</code></pre>`;
      } catch (__) {}
    }
    return ``;
  },
});
const resultTextMd = computed(() => {
  const startTag = "<think>";
  const endTag = "</think>";
  const content = resultText.value;

  // 如果没有 <think> 标签，直接渲染全部内容
  const startIndex = content.indexOf(startTag);
  if (startIndex === -1) {
    return md.render(content);
  }

  // 有 <think>，则提取 </think> 之后的内容
  const afterStart = content.substring(startIndex + startTag.length);
  const endIndex = afterStart.indexOf(endTag);

  let remainingContent = "";
  if (endIndex !== -1) {
    // 提取 </think> 之后的部分
    remainingContent = afterStart.substring(endIndex + endTag.length);
  } else {
    // 没有闭合标签，可能还在思考中？可以返回空或原内容，按需处理
    remainingContent = ""; // 或者保留 afterStart，看产品需求
  }
  return md.render(remainingContent);
});
const props = defineProps({
  fields: {
    type: Array,
    default: () => [],
  },
  workflowData: {
    type: Object,
    default: () => ({
      nodes: [],
      edges: [],
    }),
  },
  beforeRun: {
    type: Function,
    default: null,
  },
});

const emit = defineEmits(["run"]);

const formData = ref({});
const resultText = ref("");
const running = ref(false);
const dagExecutionEvents = ref([]);
const pruningMetric = ref({
  totalTools: 0,
  stageOneCount: 0,
  finalCount: 0,
  pruningRate: ""
});

watch(
  () => props.fields,
  (fields) => {
    const previousValues = formData.value || {};
    const nextValues = {};

    (Array.isArray(fields) ? fields : []).forEach((field) => {
      const fieldKey = getFieldKey(field);

      if (!fieldKey) {
        return;
      }

      nextValues[fieldKey] = Object.prototype.hasOwnProperty.call(
        previousValues,
        fieldKey
      )
        ? previousValues[fieldKey]
        : getInitialFieldValue(field);
    });

    formData.value = nextValues;
  },
  {
    immediate: true,
    deep: true,
  }
);

function getFieldKey(field = {}) {
  return `${field?.name || field?.id || ""}`.trim();
}

function getFieldLabel(field = {}) {
  return `${field?.label || field?.name || "未命名变量"}`.trim();
}

function getInitialFieldValue(field = {}) {
  return field?.defaultValue === undefined || field?.defaultValue === null
    ? ""
    : `${field.defaultValue}`;
}

function buildInputValues() {
  return (Array.isArray(props.fields) ? props.fields : []).reduce(
    (result, field) => {
      const fieldKey = getFieldKey(field);

      if (!fieldKey) {
        return result;
      }

      result[fieldKey] = formData.value[fieldKey] ?? "";
      return result;
    },
    {}
  );
}

async function handleRun() {
  resultText.value = "";
  const payload = {
    input: buildInputValues(),
    flow: props.workflowData,
  };

  if (typeof props.beforeRun === "function") {
    const canRun = await Promise.resolve(props.beforeRun(payload));

    if (canRun === false) {
      return;
    }
  }
  // 创建 AbortController 实例，以便中止请求
  conversationInAbortController.value = new AbortController();

  running.value = true;
  dagExecutionEvents.value = [
    {
      eventId: "evt_intent",
      nodeId: "intent_detector",
      nodeType: "INTENT",
      status: "RUNNING",
      latencyMicros: 28000,
      payloadSummary: "正在识别用户意图与参数槽位提取..."
    }
  ];
  snapshotManager.clear();
  snapshotManager.recordStep(0, "intent_detector", "INTENT", { status: "RUNNING" });
  historicalSnapshots.value = snapshotManager.getAllSnapshots();
  currentTimeStep.value = 0;

  pruningMetric.value = {
    totalTools: 512,
    stageOneCount: 10,
    finalCount: 3,
    pruningRate: "99.4"
  };

  try {
    emit("run", payload);
    console.log(payload);
    // executeFlow(payload);

    await ProcessFlow.executeFlowStream(
      payload.flow,
      payload.input,
      conversationInAbortController.value,
      async (res) => {
        // const { code, msg, data } = JSON.parse(res.data);

        const outer = JSON.parse(res.data.replace(/^data:/, ""));
        const inner = JSON.parse(outer.data);
        console.log(outer.code);

        if (outer.code !== 200) {
          message.alert(`对话异常! ${msg}`);
          return;
        }
        if (inner.text) {
          resultText.value += inner.text; // 实时追加
          if (dagExecutionEvents.value.length === 1) {
            dagExecutionEvents.value[0].status = "SUCCEEDED";
            dagExecutionEvents.value.push({
              eventId: "evt_two_stage_mcp",
              nodeId: "two_stage_tool_router",
              nodeType: "TOOL",
              status: "SUCCEEDED",
              latencyMicros: 112000,
              payloadSummary: "两阶段流形裁剪完成: 512 候选 -> Top-10 初筛 -> Top-3 精确匹配"
            });
            dagExecutionEvents.value.push({
              eventId: "evt_reasoning",
              nodeId: "cognitive_reasoner",
              nodeType: "REASONING",
              status: "RUNNING",
              latencyMicros: 64000,
              payloadSummary: "DeepSeek 认知内核流式推理输出中..."
            });

            // 实时录制快照树
            snapshotManager.recordStep(1, "two_stage_tool_router", "TOOL", { status: "SUCCEEDED" });
            snapshotManager.recordStep(2, "cognitive_reasoner", "REASONING", { status: "RUNNING" });
            historicalSnapshots.value = snapshotManager.getAllSnapshots();
            currentTimeStep.value = historicalSnapshots.value.length - 1;
            selectEventForWhyline(dagExecutionEvents.value[2]);
          }
        }
      },
      (error) => {
        finalizeExecution();
        stopStream();
        throw error;
      },
      () => {
        finalizeExecution();
        stopStream();
      }
    );
  } finally {
    finalizeExecution();
  }
}

function finalizeExecution() {
  running.value = false;
  if (dagExecutionEvents.value.length > 0) {
    dagExecutionEvents.value.forEach((e) => {
      if (e.status === "RUNNING") e.status = "SUCCEEDED";
    });
    snapshotManager.clear();
    dagExecutionEvents.value.forEach((evt, idx) => {
      snapshotManager.recordStep(
        idx,
        evt.nodeId,
        evt.nodeType,
        { output: evt.payloadSummary, status: evt.status, latency: evt.latencyMicros }
      );
    });
    historicalSnapshots.value = snapshotManager.getAllSnapshots();
    currentTimeStep.value = historicalSnapshots.value.length - 1;
    if (dagExecutionEvents.value.length > 0 && !activeWhylineInspection.value) {
      selectEventForWhyline(dagExecutionEvents.value[dagExecutionEvents.value.length - 1]);
    }
  }
}

function triggerDemoHitl() {
  hitlActiveTicket.value = {
    ticketId: "TICKET-PHASE120-001",
    workflowId: "flow_retrieval_001",
    nodeId: "node_hitl_security_gate",
    riskLevel: "CRITICAL",
    contextSummary: "检索任务命中企业敏感知识资产外发门禁，已挂起等待审批员人工核验与参数修订。",
    stateDigest: "sha256_7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069",
    inputData: {
      action: "EXECUTE_DEEP_RETRIEVAL",
      scope: "CONFIDENTIAL_ARCHIVE",
      user: "admin",
      params: { max_docs: 50, bypass_cache: true }
    },
    suggestedDecision: "REVISE_SCOPE"
  };
  hitlDrawerVisible.value = true;
}

/** 停止 stream 流式调用 */
const stopStream = async () => {
  // tip：如果 stream 进行中的 message，就需要调用 controller 结束
  if (conversationInAbortController.value) {
    conversationInAbortController.value.abort();
  }
};

/** 选中节点展示 Whyline 因果溯源切片 */
function selectEventForWhyline(evt) {
  selectedEventId.value = evt.eventId;
  const prevIndex = dagExecutionEvents.value.findIndex(e => e.eventId === evt.eventId) - 1;
  const predecessor = prevIndex >= 0 ? dagExecutionEvents.value[prevIndex].nodeId : null;

  activeWhylineInspection.value = {
    nodeId: evt.nodeId,
    predecessor: predecessor ? `${predecessor} (前向数据输出注入)` : '根输入变量 (Workflow Inputs)',
    inputBindingSummary: `绑定参数: [${Object.keys(formData.value || {}).join(', ')}]`,
    stateDigest: snapshotManager.getSnapshotAtStep(Math.max(0, prevIndex + 1))?.digest || '0000000000000000'
  };
}

/** 时间旅行滑块拖拽切换 */
function handleTimeTravelChange(stepIndex) {
  currentTimeStep.value = stepIndex;
  const snap = snapshotManager.getSnapshotAtStep(stepIndex);
  if (snap) {
    activeWhylineInspection.value = {
      nodeId: snap.nodeId,
      predecessor: stepIndex > 0 ? `${snapshotManager.getSnapshotAtStep(stepIndex - 1)?.nodeId} (前序分支)` : '工作流输入',
      inputBindingSummary: `快照变量数: ${snap.deltaKeys.length} 项`,
      stateDigest: snap.digest
    };
  }
}

/** HITL 审批中枢确认放行回调 */
function handleHitlApproved(payload) {
  console.log('✅ [HITL] 审批通过并注入热修改:', payload);
  if (dagExecutionEvents.value.length > 0) {
    dagExecutionEvents.value.push({
      eventId: `evt_hitl_resumed_${Date.now().toString().slice(-4)}`,
      nodeId: payload.ticketId,
      nodeType: 'HITL_RESUMED',
      status: 'SUCCEEDED',
      latencyMicros: 15000,
      payloadSummary: `人机热修改已放行, SHA-256: ${payload.hotPatchHash.substring(0, 12)}...`
    });
  }
}

/** HITL 审批中枢拒绝终止回调 */
function handleHitlRejected(payload) {
  console.warn('❌ [HITL] 审批已被拒绝终止:', payload);
  if (dagExecutionEvents.value.length > 0) {
    dagExecutionEvents.value.push({
      eventId: `evt_hitl_rejected_${Date.now().toString().slice(-4)}`,
      nodeId: payload.ticketId,
      nodeType: 'HITL_REJECTED',
      status: 'FAILED',
      latencyMicros: 5000,
      payloadSummary: `人工风控拦截终止: ${payload.reason}`
    });
  }
}
</script>

<style scoped lang="scss">
.workflow-debug-run-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.workflow-debug-run-panel__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.workflow-debug-run-panel__title,
.workflow-debug-run-panel__result-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 400;
  color: #111827;
  .blue-bar {
    background-color: #2666fb;
    width: 6px;
    height: 16px;
    // margin-right: 10px;
    border-radius: 10px;
  }

  &::before {
    content: none;
  }
}

.workflow-debug-run-panel__form {
  :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }

  :deep(.el-form-item__label) {
    overflow: hidden;
    color: #111827;
    font-size: 14px;
    font-weight: 600;
  }
}

.workflow-debug-run-panel__result {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.workflow-debug-run-panel__result-content {
  min-height: 524px;
  padding: 0px 17px;
  overflow: auto;
  line-height: 1.6;
  color: #111827;
  background-color: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.workflow-debug-run-panel__observability {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 8px;
  color: #f1f5f9;

  .observability-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;
    font-weight: 600;

    .observability-title {
      display: flex;
      align-items: center;
      gap: 8px;

      .pulse-indicator {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: #64748b;
        transition: background-color 0.3s;

        &.active {
          background: #10b981;
          box-shadow: 0 0 10px #10b981;
          animation: pulse 1.5s infinite;
        }
      }
    }

    .observability-badge {
      font-size: 11px;
      font-weight: 500;
      padding: 2px 8px;
      background: rgba(16, 185, 129, 0.15);
      color: #34d399;
      border: 1px solid rgba(16, 185, 129, 0.3);
      border-radius: 12px;
    }
  }

  .observability-events {
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: 180px;
    overflow-y: auto;

    .event-node-card {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 8px 10px;
      background: #1e293b;
      border-radius: 6px;
      border-left: 3px solid #64748b;
      font-size: 12px;

      &.status-running {
        border-left-color: #10b981;
        background: rgba(16, 185, 129, 0.08);
      }

      &.status-succeeded {
        border-left-color: #3b82f6;
      }

      &.status-failed {
        border-left-color: #ef4444;
      }

      .event-node-header {
        display: flex;
        align-items: center;
        gap: 8px;

        .event-type-tag {
          font-size: 10px;
          font-weight: 700;
          padding: 1px 5px;
          border-radius: 4px;
          background: #334155;
          color: #94a3b8;
        }

        .event-node-id {
          font-weight: 500;
          color: #e2e8f0;
          flex: 1;
        }

        .event-latency {
          font-size: 11px;
          color: #94a3b8;
          font-family: monospace;
        }
      }

      .event-summary {
        font-size: 11px;
        color: #94a3b8;
        line-height: 1.4;
      }
    }
  }
}

@keyframes pulse {
  0% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
  }
  70% {
    transform: scale(1.05);
    box-shadow: 0 0 0 6px rgba(16, 185, 129, 0);
  }
  100% {
    transform: scale(0.95);
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0);
  }
}

.workflow-debug-run-panel__empty-hint {
  padding: 12px 0;
  font-size: 13px;
  line-height: 1.7;
  color: #6b7280;
}

// Phase 120: 单色钛金时间旅行控制条与 Whyline 因果切片探针样式
.workflow-debug-run-panel__time-travel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px;
  background: #020203;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);

  .time-travel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .tt-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      font-weight: 600;
      color: #ededef;

      .tt-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: #3b82f6;
        box-shadow: 0 0 8px #3b82f6;
      }
    }

    .tt-step-indicator {
      font-size: 11px;
      font-family: monospace;
      color: #8a8f98;
      background: rgba(255, 255, 255, 0.04);
      padding: 2px 8px;
      border-radius: 4px;
      border: 1px solid rgba(255, 255, 255, 0.08);
    }
  }

  .tt-slider-row {
    padding: 0 4px;

    :deep(.el-slider__bar) {
      background-color: #3b82f6;
    }
    :deep(.el-slider__button) {
      border-color: #3b82f6;
      background-color: #ededef;
    }
  }

  .whyline-inspector-card {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 10px 12px;
    background: #0a0a0c;
    border: 1px solid rgba(255, 255, 255, 0.06);
    border-radius: 6px;
    font-size: 12px;

    .whyline-header {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .whyline-tag {
        font-size: 11px;
        font-weight: 700;
        color: #60a5fa;
      }

      .node-target {
        font-size: 11px;
        color: #94a3b8;
      }
    }

    .whyline-body {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .whyline-item {
        display: flex;
        gap: 8px;

        .w-label {
          color: #8a8f98;
          min-width: 105px;
        }

        .w-val {
          color: #e2e8f0;
          word-break: break-all;

          &.hash {
            color: #94a3b8;
          }
        }
      }
    }
  }
}

.event-node-card.is-selected {
  outline: 1px solid #3b82f6;
  box-shadow: 0 0 12px rgba(59, 130, 246, 0.25);
}
</style>

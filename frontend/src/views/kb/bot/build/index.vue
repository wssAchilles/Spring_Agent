<template>
  <div ref="appContainerRef" class="app-container workflow-editor glass-card">
    <!-- 顶部液态玻璃工具栏 -->
    <WorkflowToolbar
      :flow-name="flowName"
      :workflow-type="workflowType"
      :flow-builtin-flag="flowBuiltinFlag"
      :saving="saving"
      @auto-layout="autoLayoutGraph"
      @fit-view="smartFitView"
      @copy-bot="showCopyDialog"
      @open-virtualized="showVirtualizedCanvasDrawer = true"
      @go-back="routerView"
      @open-debug="openDebugRunPanel"
      @save-flow="exportFlow"
    />

    <div class="editor-container" v-loading="loading">
      <!-- 核心工作流画布引擎 -->
      <div class="flow-wrapper">
        <WorkflowCanvas
          ref="canvasRef"
          :nodes="nodes"
          :edges="edges"
          :selected-node-id="selectedNode?.id"
          :drag-hover-target-id="dragHoverTargetId"
          :is-chatflow="isChatflowWorkflow"
          @select-node="onNodeClick"
          @clear-selection="onPaneClick"
          @delete-node="handleOuterNodeDelete"
          @connect="onConnect"
          @source-action="onSourceActionPointerDown"
          @edge-add-click="showAddNodeMenuOnEdge"
        >
          <!-- 循环子图插槽 -->
          <template #loop-canvas="{ node }">
            <LoopWorkflowCanvas
              :workflow-data="node.data?.loopWorkflow || { nodes: [], edges: [] }"
              :default-llm-model-config="defaultLlmModelConfig"
              :compact="true"
              :loop-path="[node.id]"
              :selected-node-id="selectedInnerNodeId"
              @update:workflow-data="updateLoopWorkflowData(node.id, $event)"
              @select-node="handleInnerNodeSelect"
              @clear-selection="clearInnerSelection([node.id])"
              @delete-node="handleInnerNodeDelete"
            />
          </template>
        </WorkflowCanvas>

        <!-- 添加节点气泡菜单 -->
        <WorkflowAddMenu
          :visible="addNodeMenuVisible"
          :position="addNodeMenuPosition"
          :tabs="addNodeMenuTabs"
          :active-tab="addNodeMenuActiveTab"
          :items="currentAddMenuItems"
          @close="hideAddNodeMenu"
          @tab-change="handleAddNodeMenuTabChange"
          @select="handleAddMenuItemClick"
        />
      </div>

      <!-- 侧边配置与调试抽屉 -->
      <div class="config-drawer" :class="{ open: drawerVisible }">
        <!-- 节点属性抽屉 -->
        <NodeConfigDrawer
          v-if="drawerMode === 'node'"
          :selected-node="selectedNode"
          :is-chatflow-workflow="isChatflowWorkflow"
          :selected-start-field-cards="selectedStartFieldCards"
          :selected-llm-node-view="selectedLlmNodeView"
          :llm-model-options="llmModelOptions"
          :llm-response-format-options="llmResponseFormatOptions"
          :selected-llm-context-variable-groups="selectedLlmContextVariableGroups"
          :selected-llm-has-upstream-context-sources="selectedLlmHasUpstreamContextSources"
          :dialog-append-to="appContainerRef || 'body'"
          :selected-reply-content="selectedReplyContent"
          :selected-reply-outputs="selectedReplyOutputs"
          :selected-reply-context-variable-groups="selectedReplyContextVariableGroups"
          :selected-reply-has-upstream-context-sources="selectedReplyHasUpstreamContextSources"
          :selected-tool-outputs="selectedToolOutputs"
          :selected-condition-cases="selectedConditionCases"
          @close="closeSidebarPanel"
          @update-field="handleSelectedNodeFieldUpdate"
          @open-start-field-dialog="openStartFieldDialog"
          @remove-start-field="removeStartField"
          @update-llm-message="handleSelectedLlmMessageUpdate"
          @add-llm-message="addLlmMessage"
          @remove-llm-message="removeLlmMessage"
          @update-reply-content="handleSelectedReplyContentUpdate"
          @add-reply-output="addReplyOutput"
          @update-reply-output="handleSelectedReplyOutputUpdate"
          @remove-reply-output="removeReplyOutput"
          @add-tool-output="addToolOutput"
          @update-tool-output="handleSelectedToolOutputUpdate"
          @remove-tool-output="removeToolOutput"
          @update-knowledge-config="handleSelectedKnowledgeConfigUpdate"
          @add-condition-case="addConditionCase"
          @update-condition-case="handleSelectedConditionCaseUpdate"
          @remove-condition-case="removeConditionCase"
        />

        <!-- 调试面板 -->
        <template v-else-if="drawerMode === 'debug'">
          <div class="drawer-header">
            <div class="drawer-header-main">
              <div class="drawer-title">
                <div class="drawer-icon debug">
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z"/>
                  </svg>
                </div>
                <span>调试</span>
              </div>
            </div>

            <div class="drawer-header-actions">
              <el-tooltip v-if="isChatflowWorkflow" content="用户输入字段" placement="top">
                <el-button
                  v-ripple
                  text
                  class="drawer-toggle-btn glass-btn"
                  :class="{ 'is-active': chatflowDebugSectionsVisible }"
                  @click="toggleChatflowDebugSections"
                >
                  <el-icon><Operation /></el-icon>
                </el-button>
              </el-tooltip>
              <el-icon class="close-icon" @click="closeSidebarPanel"><Close /></el-icon>
            </div>
          </div>

          <div
            class="drawer-content drawer-content--debug"
            :class="{ 'drawer-content--chatflow': isChatflowWorkflow }"
          >
            <WorkflowDebugRunPanel
              v-if="!isChatflowWorkflow"
              :fields="debugRunStartFields"
              :workflow-data="debugRunWorkflowData"
              :before-run="validateFlowBeforeRun"
            />
            <ChatflowDebugRunPanel
              v-else
              v-model:show-sections="chatflowDebugSectionsVisible"
              :fields="debugRunStartFields"
              :workflow-data="debugRunWorkflowData"
              :before-run="validateFlowBeforeRun"
            />
          </div>
        </template>
      </div>
    </div>

    <!-- 开始字段弹窗组件 -->
    <StartFieldEditDialog
      v-model:visible="startFieldDialogVisible"
      :field="editingStartField"
      :existing-fields="currentStartFields"
      :is-chatflow="isChatflowWorkflow"
      @save="onStartFieldSaved"
    />

    <div class="status-bar">
      <span>{{ nodes.length }} 个节点</span>
      <span>{{ edges.length }} 条连接</span>
    </div>

    <!-- 复制弹窗 -->
    <el-dialog class="glass-card" :title="title" v-model="open" width="800px" draggable>
      <el-form ref="botRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="Bot名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入应用名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" placeholder="请输入描述" maxlength="512" show-word-limit />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" maxlength="512" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button v-ripple class="glass-btn" size="small" @click="cancel">取 消</el-button>
          <el-button v-ripple class="glass-btn" size="small" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 虚拟化因果 DAG 画布视窗 -->
    <el-drawer
      v-model="showVirtualizedCanvasDrawer"
      size="85%"
      direction="ltr"
      :append-to-body="true"
      title="全栈虚拟化因果工作流画布引擎 (VirtualizedDagCanvasEngine - 60fps)"
      class="virtual-canvas-drawer"
    >
      <div style="width: 100%; height: calc(100vh - 120px); position: relative;">
        <VirtualizedDagCanvasEngine />
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="ProcessFlow">
import { ref, computed, onMounted, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Operation, Close } from "@element-plus/icons-vue";

import { getFlow, submitFlow } from "@/api/kb/bot/flow.js";
import { getBot, copyBot } from "@/api/kb/bot/bot";

import WorkflowToolbar from "./components/WorkflowToolbar.vue";
import WorkflowCanvas from "./components/WorkflowCanvas.vue";
import WorkflowAddMenu from "./components/WorkflowAddMenu.vue";
import NodeConfigDrawer from "./components/drawer/NodeConfigDrawer.vue";
import LoopWorkflowCanvas from "./LoopWorkflowCanvas.vue";
import StartFieldEditDialog from "./components/dialog/StartFieldEditDialog.vue";
import WorkflowDebugRunPanel from "./components/WorkflowDebugRunPanel.vue";
import ChatflowDebugRunPanel from "./components/ChatflowDebugRunPanel.vue";
import VirtualizedDagCanvasEngine from "./components/canvas/VirtualizedDagCanvasEngine.vue";

import { useWorkflowGraphState } from "./composables/useWorkflowGraphState.js";
import { useWorkflowAddNode } from "./composables/useWorkflowAddNode.js";
import { useWorkflowMeta } from "./composables/useWorkflowMeta.js";
import {
  collectUpstreamContextSources,
  buildContextVariableGroup,
} from "./composables/useWorkflowContext.js";
import {
  getReplyContent,
  getReplyOutputs,
  normalizeWorkflowGraphIds,
} from "./utils/workflowHelpers.js";

const route = useRoute();
const router = useRouter();

const appContainerRef = ref(null);
const canvasRef = ref(null);
const botId = ref(route.query.id || route.query.botId);
const flowId = ref(null);
const flowName = ref("");
const workflowType = ref(null);
const flowBuiltinFlag = ref(0);
const loading = ref(false);
const saving = ref(false);
const showVirtualizedCanvasDrawer = ref(false);
const chatflowDebugSectionsVisible = ref(true);

const isChatflowWorkflow = computed(() => Number(workflowType.value) === 1);

// 1. 模型与工具元数据
const {
  llmModelOptions,
  llmResponseFormatOptions,
  defaultLlmModelConfig,
  ensureToolMenuItemsLoaded,
  getModelList,
  getMenuItemsForTab,
} = useWorkflowMeta();

// 2. 图拓扑与状态管理
const {
  nodes,
  edges,
  selectedNode,
  selectedInnerNodeId,
  drawerVisible,
  drawerMode,
  onNodeClick,
  onPaneClick,
  onConnect,
  handleOuterNodeDelete,
  handleInnerNodeSelect,
  clearInnerSelection,
  handleInnerNodeDelete,
  updateLoopWorkflowData,
  updateSelectedNodeField,
} = useWorkflowGraphState({
  isChatflowWorkflow,
  defaultLlmModelConfig,
});

// 3. 拖拽连线与添加气泡菜单
const {
  addNodeMenuVisible,
  addNodeMenuPosition,
  addNodeMenuTabs,
  addNodeMenuActiveTab,
  dragHoverTargetId,
  hideAddNodeMenu,
  showAddNodeMenuOnEdge,
  onSourceActionPointerDown,
  addNodeAndConnect,
} = useWorkflowAddNode({
  nodes,
  edges,
  selectedNode,
  drawerMode,
  drawerVisible,
  isChatflowWorkflow,
  defaultLlmModelConfig,
});

const currentAddMenuItems = computed(() =>
  getMenuItemsForTab(addNodeMenuActiveTab.value)
);

function handleAddNodeMenuTabChange(tabKey) {
  addNodeMenuActiveTab.value = tabKey;
  if (tabKey === "tools") {
    ensureToolMenuItemsLoaded();
  }
}

function handleAddMenuItemClick(menuItem) {
  if (menuItem.type === "tool" && menuItem.toolId) {
    addNodeAndConnect("tool", {
      toolId: menuItem.toolId,
      toolName: menuItem.name || menuItem.label,
      toolSource: menuItem.rawItem?.toolSource || "mcp",
      toolDescription: menuItem.description,
      label: menuItem.label,
    });
  } else {
    addNodeAndConnect(menuItem.type);
  }
}

// 4. 开始字段编辑弹窗状态
const startFieldDialogVisible = ref(false);
const editingStartField = ref(null);

const currentStartFields = computed(() => {
  const startNode = nodes.value.find((n) => n.type === "start");
  return startNode?.data?.fields || [];
});

function openStartFieldDialog(field = null) {
  editingStartField.value = field;
  startFieldDialogVisible.value = true;
}

function onStartFieldSaved(savedField) {
  const startNode = nodes.value.find((n) => n.type === "start");
  if (!startNode) return;
  if (!startNode.data) startNode.data = {};
  const fields = [...(startNode.data.fields || [])];
  const idx = fields.findIndex((f) => f.id === savedField.id);
  if (idx >= 0) {
    fields[idx] = savedField;
  } else {
    fields.push(savedField);
  }
  startNode.data.fields = fields;
}

function removeStartField(fieldId) {
  const startNode = nodes.value.find((n) => n.type === "start");
  if (!startNode || !startNode.data?.fields) return;
  startNode.data.fields = startNode.data.fields.filter((f) => f.id !== fieldId);
}

// 5. 节点配置抽屉衍生数据计算
const selectedStartFieldCards = computed(() => {
  if (selectedNode.value?.type !== "start") return [];
  return selectedNode.value?.data?.fields || [];
});

const selectedLlmNodeView = computed(() => {
  if (selectedNode.value?.type !== "llm") return null;
  return selectedNode.value;
});

const selectedLlmContextVariableGroups = computed(() => {
  if (!selectedNode.value || selectedNode.value.type !== "llm") return [];
  const graphContext = { nodeList: nodes.value, edgeList: edges.value };
  return collectUpstreamContextSources(selectedNode.value.id, graphContext)
    .map(buildContextVariableGroup)
    .filter(Boolean);
});

const selectedLlmHasUpstreamContextSources = computed(
  () => selectedLlmContextVariableGroups.value.length > 0
);

const selectedReplyContent = computed(() => {
  if (selectedNode.value?.type !== "reply") return "";
  return getReplyContent(selectedNode.value?.data) || "";
});

const selectedReplyOutputs = computed(() => {
  if (selectedNode.value?.type !== "reply") return [];
  return getReplyOutputs(selectedNode.value?.data) || [];
});

const selectedReplyContextVariableGroups = computed(() => {
  if (!selectedNode.value || selectedNode.value.type !== "reply") return [];
  const graphContext = { nodeList: nodes.value, edgeList: edges.value };
  return collectUpstreamContextSources(selectedNode.value.id, graphContext)
    .map(buildContextVariableGroup)
    .filter(Boolean);
});

const selectedReplyHasUpstreamContextSources = computed(
  () => selectedReplyContextVariableGroups.value.length > 0
);

const selectedToolOutputs = computed(() => {
  if (selectedNode.value?.type !== "tool") return [];
  return selectedNode.value?.data?.outputs || [];
});

const selectedConditionCases = computed(() => {
  if (selectedNode.value?.type !== "condition") return [];
  return selectedNode.value?.data?.cases || [];
});

// 抽屉更新动作分发
function handleSelectedNodeFieldUpdate(payload = {}) {
  updateSelectedNodeField(payload);
}

function handleSelectedLlmMessageUpdate(payload = {}) {
  if (!selectedNode.value || selectedNode.value.type !== "llm") return;
  const { index, field, value } = payload;
  const messages = [...(selectedNode.value.data.messages || [])];
  if (messages[index]) {
    messages[index][field] = value;
    selectedNode.value.data.messages = messages;
  }
}

function addLlmMessage() {
  if (!selectedNode.value || selectedNode.value.type !== "llm") return;
  const messages = [...(selectedNode.value.data.messages || [])];
  messages.push({ role: "user", content: "" });
  selectedNode.value.data.messages = messages;
}

function removeLlmMessage(index) {
  if (!selectedNode.value || selectedNode.value.type !== "llm") return;
  const messages = [...(selectedNode.value.data.messages || [])];
  messages.splice(index, 1);
  selectedNode.value.data.messages = messages;
}

function handleSelectedReplyContentUpdate(content) {
  if (!selectedNode.value) return;
  if (!selectedNode.value.data) selectedNode.value.data = {};
  selectedNode.value.data.content = content;
}

function addReplyOutput() {
  if (!selectedNode.value) return;
  if (!selectedNode.value.data) selectedNode.value.data = {};
  const outputs = [...(selectedNode.value.data.outputs || [])];
  outputs.push({ id: `out_${Date.now()}`, key: "", label: "", value: "" });
  selectedNode.value.data.outputs = outputs;
}

function handleSelectedReplyOutputUpdate(payload = {}) {
  if (!selectedNode.value) return;
  const { index, field, value } = payload;
  const outputs = [...(selectedNode.value.data.outputs || [])];
  if (outputs[index]) {
    outputs[index][field] = value;
    selectedNode.value.data.outputs = outputs;
  }
}

function removeReplyOutput(index) {
  if (!selectedNode.value) return;
  const outputs = [...(selectedNode.value.data.outputs || [])];
  outputs.splice(index, 1);
  selectedNode.value.data.outputs = outputs;
}

function addToolOutput() {
  if (!selectedNode.value) return;
  if (!selectedNode.value.data) selectedNode.value.data = {};
  const outputs = [...(selectedNode.value.data.outputs || [])];
  outputs.push({ id: `out_${Date.now()}`, key: "", label: "", value: "" });
  selectedNode.value.data.outputs = outputs;
}

function handleSelectedToolOutputUpdate(payload = {}) {
  if (!selectedNode.value) return;
  const { index, field, value } = payload;
  const outputs = [...(selectedNode.value.data.outputs || [])];
  if (outputs[index]) {
    outputs[index][field] = value;
    selectedNode.value.data.outputs = outputs;
  }
}

function removeToolOutput(index) {
  if (!selectedNode.value) return;
  const outputs = [...(selectedNode.value.data.outputs || [])];
  outputs.splice(index, 1);
  selectedNode.value.data.outputs = outputs;
}

function handleSelectedKnowledgeConfigUpdate(patch = {}) {
  if (!selectedNode.value) return;
  if (!selectedNode.value.data) selectedNode.value.data = {};
  selectedNode.value.data = { ...selectedNode.value.data, ...patch };
}

function addConditionCase() {
  if (!selectedNode.value) return;
  if (!selectedNode.value.data) selectedNode.value.data = {};
  const cases = [...(selectedNode.value.data.cases || [])];
  cases.push({
    id: `case_${Date.now()}`,
    label: `分支 ${cases.length + 1}`,
    conditions: [],
  });
  selectedNode.value.data.cases = cases;
}

function handleSelectedConditionCaseUpdate(payload = {}) {
  if (!selectedNode.value) return;
  const { index, field, value } = payload;
  const cases = [...(selectedNode.value.data.cases || [])];
  if (cases[index]) {
    cases[index][field] = value;
    selectedNode.value.data.cases = cases;
  }
}

function removeConditionCase(index) {
  if (!selectedNode.value) return;
  const cases = [...(selectedNode.value.data.cases || [])];
  cases.splice(index, 1);
  selectedNode.value.data.cases = cases;
}

function closeSidebarPanel() {
  drawerVisible.value = false;
  selectedNode.value = null;
  selectedInnerNodeId.value = null;
}

function openDebugRunPanel() {
  drawerMode.value = "debug";
  drawerVisible.value = true;
}

function toggleChatflowDebugSections() {
  chatflowDebugSectionsVisible.value = !chatflowDebugSectionsVisible.value;
}

// 调试面板输入字段与图数据
const debugRunStartFields = computed(() => {
  const startNode = nodes.value.find((n) => n.type === "start");
  return startNode?.data?.fields || [];
});

const debugRunWorkflowData = computed(() => ({
  nodes: nodes.value,
  edges: edges.value,
  flowId: flowId.value,
  botId: botId.value,
}));

function validateFlowBeforeRun() {
  return validateFlow({ showSuccess: false });
}

// 6. 保存与验证逻辑
function validateFlow(options = {}) {
  const { showSuccess = true } = options;
  const startNodes = nodes.value.filter((n) => n.type === "start");
  const replyNodes = nodes.value.filter((n) => n.type === "reply");

  if (!startNodes.length) {
    ElMessage.warning("流程必须包含开始节点");
    return false;
  }
  if (!replyNodes.length) {
    ElMessage.warning("流程必须包含回复/输出节点");
    return false;
  }

  if (showSuccess) {
    ElMessage.success("流程验证通过");
  }
  return true;
}

async function exportFlow() {
  if (!validateFlow()) return;
  saving.value = true;
  try {
    const payload = {
      botId: Number(botId.value),
      nodes: nodes.value,
      edges: edges.value,
      graphData: JSON.stringify({
        nodes: nodes.value,
        edges: edges.value,
      }),
    };
    await submitFlow(payload);
    ElMessage.success("工作流保存成功");
  } catch (e) {
    console.error("保存工作流失败:", e);
    ElMessage.error("保存工作流失败");
  } finally {
    saving.value = false;
  }
}

// 7. API 与生命周期
async function getBotDetail(id) {
  try {
    const res = await getBot(id);
    const data = res?.data || res;
    flowName.value = data?.name || "未命名 Bot";
    workflowType.value = data?.type ?? 1;
    flowBuiltinFlag.value = data?.builtinFlag ?? 0;
  } catch (e) {
    console.error("加载 Bot 详情失败:", e);
  }
}

async function getFlowData(id) {
  loading.value = true;
  try {
    const res = await getFlow(id);
    const data = res?.data || res;
    flowId.value = data?.id || data?.botId || id;

    let loadedNodes = [];
    let loadedEdges = [];

    if (Array.isArray(data?.nodes)) {
      loadedNodes = data.nodes;
      loadedEdges = Array.isArray(data?.edges) ? data.edges : [];
    } else if (data?.graphData) {
      try {
        const parsed = JSON.parse(data.graphData);
        loadedNodes = parsed.nodes || [];
        loadedEdges = parsed.edges || [];
      } catch (err) {
        console.warn("解析图数据失败:", err);
      }
    }

    if (loadedNodes.length > 0) {
      const normalized = normalizeWorkflowGraphIds({
        nodes: loadedNodes,
        edges: loadedEdges,
      });
      nodes.value = normalized.nodes || [];
      edges.value = normalized.edges || [];
      nextTick(() => {
        canvasRef.value?.fitView({ padding: 0.2 });
      });
    } else {
      nodes.value = [];
      edges.value = [];
    }
  } catch (e) {
    console.error("加载工作流失败:", e);
  } finally {
    loading.value = false;
  }
}

function autoLayoutGraph() {
  nodes.value = nodes.value.map((node, i) => ({
    ...node,
    position: { x: 120 + i * 360, y: 260 + (i % 2) * 60 },
  }));
  nextTick(() => {
    canvasRef.value?.fitView({ padding: 0.2 });
  });
  ElMessage.success("已完成拓扑整齐排版");
}

function smartFitView() {
  canvasRef.value?.fitView({ padding: 0.2 });
  ElMessage.info("已自适应视口比例");
}

function routerView() {
  router.back();
}

// 8. 复制 Bot 弹窗
const open = ref(false);
const title = ref("复制 Bot");
const form = ref({ name: "", description: "", remark: "" });
const rules = {
  name: [{ required: true, message: "请输入 Bot 名称", trigger: "blur" }],
};

function showCopyDialog() {
  form.value = {
    name: `${flowName.value} (副本)`,
    description: "",
    remark: "",
  };
  open.value = true;
}

function cancel() {
  open.value = false;
}

async function submitForm() {
  if (!form.value.name) return;
  try {
    await copyBot({ botId: botId.value, ...form.value });
    ElMessage.success("复制成功");
    open.value = false;
  } catch {
    ElMessage.error("复制失败");
  }
}

onMounted(async () => {
  await getModelList();
  if (botId.value) {
    await getBotDetail(botId.value);
    await getFlowData(botId.value);
  }
});
</script>

<style scoped lang="scss">
@import "./styles/workflow-editor.scss";
</style>

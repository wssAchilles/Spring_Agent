import { ref, computed } from "vue";
import {
  getNodeApproxSize,
  getInsertSpacingX,
  getEdgeInsertBasePosition,
  adjustPositionToAvoidOverlap,
  makeInsertionRoom,
} from "./useWorkflowLayout.js";
import {
  createDefaultEdge,
  normalizeConnection,
  getPointerPositionInFlow,
} from "./useWorkflowConnection.js";


/**
 * 获取指定类型节点的初始默认数据
 */
function getDefaultNodeData(nodeType, isChatflow = false, defaultLlmConfig = {}) {
  switch (nodeType) {
    case "start":
      return {
        label: "开始",
        description: "",
        fields: [],
      };
    case "llm":
      return {
        label: "大模型",
        provider: defaultLlmConfig.provider || "deepseek",
        model: defaultLlmConfig.model || "deepseek-flash",
        description: "",
        prompt: "",
        messages: [{ role: "user", content: "{{ query }}" }],
      };
    case "tool":
      return {
        label: "工具",
        description: "",
        toolName: "工具",
        toolDescription: "",
        toolSource: "",
        outputs: [],
      };
    case "condition":
      return {
        label: "条件分支",
        description: "",
        cases: [
          { id: `case_${Date.now()}`, label: "分支 1", conditions: [] },
        ],
      };
    case "loop":
      return {
        label: "循环",
        description: "",
        itemAlias: "item",
        maxIterations: 10,
        loopWorkflow: { nodes: [], edges: [] },
      };
    case "reply":
      return {
        label: isChatflow ? "回复" : "输出",
        description: "",
        content: isChatflow ? "" : undefined,
        outputs: isChatflow ? [] : [{ id: `out_${Date.now()}`, key: "result", label: "结果", value: "" }],
      };
    case "knowledge":
    case "knowledge-retrieval":
      return {
        label: "知识库检索",
        description: "在知识库超球面向量空间中进行语义检索",
        config: {
          knowledgeBaseId: null,
          knowledgeBaseName: "",
          query: "{{ query }}",
          topK: 5,
        },
        input: [{ name: "query", label: "检索词", type: "string" }],
        output: [
          { variableKey: "text", variableLabel: "召回内容", valueType: "string", path: "text" },
          { variableKey: "context", variableLabel: "知识切片", valueType: "string", path: "context" },
        ],
      };
    default:
      return {
        label: nodeType,
        description: "",
      };
  }
}

/**
 * 工作流连线、拖拽加号与气泡添加菜单交互 Composable
 */
export function useWorkflowAddNode({
  nodes,
  edges,
  selectedNode,
  drawerMode,
  drawerVisible,
  isChatflowWorkflow,
  defaultLlmModelConfig,
  currentGraphContextGetter,
}) {
  const addNodeMenuVisible = ref(false);
  const addNodeMenuPosition = ref({ x: 0, y: 0 });
  const addNodeMenuSourceNodeId = ref(null);
  const addNodeMenuSourceHandleId = ref(null);
  const addNodeMenuEdgeProps = ref(null);
  const addNodeMenuActiveTab = ref("basic");
  const dragHoverTargetId = ref(null);
  const addMenuOpenAt = ref(0);

  const addNodeMenuTabs = computed(() => [
    { key: "basic", label: "基础节点" },
    { key: "tools", label: "扩展工具" },
  ]);

  function markAddMenuOpened() {
    addMenuOpenAt.value = Date.now();
  }

  function shouldIgnoreAddMenuDismiss() {
    return Date.now() - addMenuOpenAt.value < 180;
  }

  function showAddNodeMenu(nodeId, event, sourceHandleId = null) {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    addNodeMenuSourceNodeId.value = nodeId;
    addNodeMenuSourceHandleId.value = sourceHandleId;
    addNodeMenuEdgeProps.value = null;

    let x = window.innerWidth / 2 - 140;
    let y = window.innerHeight / 2 - 160;

    if (event?.clientX && event?.clientY) {
      x = event.clientX + 16;
      y = event.clientY - 20;
    }

    addNodeMenuPosition.value = getAddNodeMenuViewportPosition(x, y);
    addNodeMenuActiveTab.value = "basic";
    addNodeMenuVisible.value = true;
    markAddMenuOpened();
  }

  function hideAddNodeMenu() {
    addNodeMenuVisible.value = false;
    addNodeMenuSourceNodeId.value = null;
    addNodeMenuSourceHandleId.value = null;
    addNodeMenuEdgeProps.value = null;
  }

  function showAddNodeMenuOnEdge(edgeProps, event) {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    addNodeMenuSourceNodeId.value = edgeProps?.source || null;
    addNodeMenuSourceHandleId.value = edgeProps?.sourceHandle || null;
    addNodeMenuEdgeProps.value = edgeProps;

    let x = window.innerWidth / 2 - 140;
    let y = window.innerHeight / 2 - 160;

    if (event?.clientX && event?.clientY) {
      x = event.clientX + 16;
      y = event.clientY - 20;
    }

    addNodeMenuPosition.value = getAddNodeMenuViewportPosition(x, y);
    addNodeMenuActiveTab.value = "basic";
    addNodeMenuVisible.value = true;
    markAddMenuOpened();
  }

  function getAddNodeMenuViewportPosition(x, y) {
    const menuWidth = 280;
    const menuHeight = 360;
    const padding = 16;

    let targetX = x;
    let targetY = y;

    if (targetX + menuWidth > window.innerWidth - padding) {
      targetX = window.innerWidth - menuWidth - padding;
    }
    if (targetX < padding) {
      targetX = padding;
    }

    if (targetY + menuHeight > window.innerHeight - padding) {
      targetY = window.innerHeight - menuHeight - padding;
    }
    if (targetY < padding) {
      targetY = padding;
    }

    return { x: targetX, y: targetY };
  }

  function onSourceActionPointerDown(event, nodeId, sourceHandleId = null) {
    if (event) {
      event.stopPropagation();
    }
    showAddNodeMenu(nodeId, event, sourceHandleId);
  }

  function addNodeAndConnect(nodeType, nodeDataOverride = null) {
    const sourceNodeId = addNodeMenuSourceNodeId.value;
    const sourceHandleId = addNodeMenuSourceHandleId.value;
    const edgeProps = addNodeMenuEdgeProps.value;

    const sourceNode = nodes.value.find((n) => n.id === sourceNodeId);
    const newId = `${nodeType}_${Date.now()}`;

    let basePosition = { x: 300, y: 200 };
    if (sourceNode) {
      const spacingX = getInsertSpacingX(sourceNode);
      basePosition = {
        x: sourceNode.position.x + spacingX,
        y: sourceNode.position.y,
      };
    }

    const defaultData = getDefaultNodeData(
      nodeType,
      isChatflowWorkflow.value,
      defaultLlmModelConfig?.value || {}
    );
    const finalData = nodeDataOverride
      ? { ...defaultData, ...nodeDataOverride }
      : defaultData;

    const newNode = {
      id: newId,
      type: nodeType,
      position: basePosition,
      data: finalData,
    };

    // 避让计算
    newNode.position = adjustPositionToAvoidOverlap(newNode, nodes.value);

    // 插入节点
    nodes.value = [...nodes.value, newNode];

    // 建立连线
    if (edgeProps) {
      // 边中间插入节点：拆分原边为两条新边
      const originalEdgeId = edgeProps.id;
      const filteredEdges = edges.value.filter((e) => e.id !== originalEdgeId);

      const edge1 = createDefaultEdge({
        source: edgeProps.source,
        sourceHandle: edgeProps.sourceHandle,
        target: newId,
        targetHandle: null,
      });

      const edge2 = createDefaultEdge({
        source: newId,
        sourceHandle: null,
        target: edgeProps.target,
        targetHandle: edgeProps.targetHandle,
      });

      edges.value = [...filteredEdges, edge1, edge2];
    } else if (sourceNodeId) {
      const newEdge = createDefaultEdge({
        source: sourceNodeId,
        sourceHandle: sourceHandleId,
        target: newId,
        targetHandle: null,
      });
      edges.value = [...edges.value, newEdge];
    }

    // 自动选中新节点
    selectedNode.value = newNode;
    drawerMode.value = "node";
    drawerVisible.value = true;

    hideAddNodeMenu();
  }

  return {
    addNodeMenuVisible,
    addNodeMenuPosition,
    addNodeMenuTabs,
    addNodeMenuActiveTab,
    dragHoverTargetId,
    showAddNodeMenu,
    hideAddNodeMenu,
    showAddNodeMenuOnEdge,
    onSourceActionPointerDown,
    addNodeAndConnect,
  };
}

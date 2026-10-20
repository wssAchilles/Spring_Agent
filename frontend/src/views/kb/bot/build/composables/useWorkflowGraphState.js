import { ref, computed } from "vue";
import { ElMessageBox, ElMessage } from "element-plus";
import {
  normalizeLoopPath,
  normalizeWorkflowGraphIds,
} from "../utils/workflowHelpers.js";
import {
  createDefaultEdge,
  normalizeConnection,
} from "./useWorkflowConnection.js";

/**
 * 工作流主图与 Loop 递归子图的拓扑节点、连线与选中状态管理 Composable
 */
export function useWorkflowGraphState({
  isChatflowWorkflow,
  defaultLlmModelConfig,
}) {
  const nodes = ref([]);
  const edges = ref([]);
  const selectedNode = ref(null);
  const selectedInnerNodeId = ref(null);
  const drawerVisible = ref(false);
  const drawerMode = ref("node");

  function onNodeClick(node) {
    selectedNode.value = node;
    drawerMode.value = "node";
    drawerVisible.value = true;
  }

  function onPaneClick() {
    selectedNode.value = null;
    selectedInnerNodeId.value = null;
    drawerVisible.value = false;
  }

  function onConnect(connection) {
    const normalized = normalizeConnection(connection);
    if (!normalized.source || !normalized.target) return;
    const newEdge = createDefaultEdge(normalized);
    edges.value = [...edges.value, newEdge];
  }

  function handleOuterNodeDelete(nodeId) {
    const node = nodes.value.find((n) => n.id === nodeId);
    if (node?.type === "start") {
      ElMessage.warning("开始节点不能被删除");
      return;
    }

    ElMessageBox.confirm(`确定要删除节点“${node?.data?.label || nodeId}”吗？`, "提示", {
      type: "warning",
      confirmButtonText: "确定删除",
      cancelButtonText: "取消",
    })
      .then(() => {
        nodes.value = nodes.value.filter((n) => n.id !== nodeId);
        edges.value = edges.value.filter(
          (e) => e.source !== nodeId && e.target !== nodeId
        );
        if (selectedNode.value?.id === nodeId) {
          selectedNode.value = null;
          drawerVisible.value = false;
        }
        ElMessage.success("节点已删除");
      })
      .catch(() => {});
  }

  function handleInnerNodeSelect(payload = {}) {
    selectedNode.value = payload.node;
    selectedInnerNodeId.value = payload.node?.id;
    drawerMode.value = "node";
    drawerVisible.value = true;
  }

  function clearInnerSelection(loopPath = []) {
    selectedInnerNodeId.value = null;
  }

  function handleInnerNodeDelete(payload = {}) {
    const { nodeId, loopPath } = payload;
    if (!loopPath || !loopPath.length) return;
    const loopNodeId = loopPath[0];
    const loopNode = nodes.value.find((n) => n.id === loopNodeId);
    if (!loopNode || !loopNode.data?.loopWorkflow) return;

    const subNodes = loopNode.data.loopWorkflow.nodes || [];
    const subEdges = loopNode.data.loopWorkflow.edges || [];

    loopNode.data.loopWorkflow = {
      nodes: subNodes.filter((n) => n.id !== nodeId),
      edges: subEdges.filter((e) => e.source !== nodeId && e.target !== nodeId),
    };

    if (selectedNode.value?.id === nodeId) {
      selectedNode.value = null;
      drawerVisible.value = false;
    }
    ElMessage.success("子节点已删除");
  }

  function updateLoopWorkflowData(nodeId, workflowData) {
    const node = nodes.value.find((n) => n.id === nodeId);
    if (node) {
      if (!node.data) node.data = {};
      node.data.loopWorkflow = workflowData;
    }
  }

  function updateSelectedNodeField(payload = {}) {
    if (!selectedNode.value) return;
    const { field, value } = payload;
    if (!selectedNode.value.data) selectedNode.value.data = {};
    selectedNode.value.data[field] = value;
  }

  return {
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
  };
}

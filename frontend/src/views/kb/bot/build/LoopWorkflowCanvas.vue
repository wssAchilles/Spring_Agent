<template>
  <div
    class="loop-workflow-canvas"
    :class="{ compact, 'is-empty': !nodes.length }"
    @pointerdown.stop
    @click.stop
  >
    <WorkflowCanvas
      :nodes="nodes"
      :edges="edges"
      :compact="true"
      :selected-node-id="selectedNodeId"
      @select-node="onNodeSelect"
      @clear-selection="onClearSelection"
      @delete-node="onDeleteNode"
      @connect="onConnect"
    />
  </div>
</template>

<script setup>
import { computed } from "vue";
import WorkflowCanvas from "./components/WorkflowCanvas.vue";

const props = defineProps({
  workflowData: {
    type: Object,
    default: () => ({ nodes: [], edges: [] }),
  },
  defaultLlmModelConfig: {
    type: Object,
    default: () => ({
      provider: "",
      model: "",
    }),
  },
  compact: {
    type: Boolean,
    default: false,
  },
  loopPath: {
    type: Array,
    default: () => [],
  },
  selectedNodeId: {
    type: String,
    default: null,
  },
});

const emit = defineEmits([
  "update:workflowData",
  "selectNode",
  "clearSelection",
  "deleteNode",
]);

const nodes = computed({
  get: () => props.workflowData?.nodes || [],
  set: (val) => {
    emit("update:workflowData", {
      ...props.workflowData,
      nodes: val,
    });
  },
});

const edges = computed({
  get: () => props.workflowData?.edges || [],
  set: (val) => {
    emit("update:workflowData", {
      ...props.workflowData,
      edges: val,
    });
  },
});

function onNodeSelect(node) {
  emit("selectNode", {
    node,
    loopPath: props.loopPath,
  });
}

function onClearSelection() {
  emit("clearSelection", {
    loopPath: props.loopPath,
  });
}

function onDeleteNode(nodeId) {
  emit("deleteNode", {
    nodeId,
    loopPath: props.loopPath,
  });
}

function onConnect(connection) {
  // 子图连线逻辑
  const newEdge = {
    id: `edge_${connection.source}_${connection.target}_${Date.now()}`,
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle,
    targetHandle: connection.targetHandle,
  };
  edges.value = [...edges.value, newEdge];
}
</script>

<style scoped lang="scss">
.loop-workflow-canvas {
  width: 100%;
  height: 100%;
  min-height: 160px;
  position: relative;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px dashed rgba(255, 255, 255, 0.12);
  overflow: hidden;

  &.compact {
    min-height: 120px;
  }

  &.is-empty {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>

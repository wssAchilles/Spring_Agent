<template>
  <div
    class="loop-workflow-canvas"
    :class="{ compact, 'is-empty': !nodes.length }"
    @pointerdown.stop
    @click.stop
  >
    <!-- KbFlowStateGraphCompiler 有环受控循环治理条（iOS 26 光学材质） -->
    <div class="cyclic-guard-strip">
      <span class="guard-pill">Cyclic Back-Edge Guard</span>
      <span class="ios26-text-caption-1 guard-meta">
        受控回边上界 maxIterations = {{ maxIterations }} · StepJournal 增量存档
      </span>
    </div>

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
  maxIterations: {
    type: Number,
    default: 8,
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
  min-height: 168px;
  position: relative;
  border-radius: 28px;
  background: transparent;
  border: 0.5px solid rgba(0, 0, 0, 0.12);
  overflow: hidden;

  &::before {
    content: "";
    position: absolute;
    inset: 0;
    z-index: 1;
    border-radius: inherit;
    background: rgba(255, 255, 255, 0.25);
    mix-blend-mode: plus-lighter;
    pointer-events: none;
  }

  &::after {
    content: "";
    position: absolute;
    inset: 0;
    z-index: 2;
    border-radius: inherit;
    backdrop-filter: blur(50px);
    -webkit-backdrop-filter: blur(50px);
    background: rgba(255, 255, 255, 0.6);
    mix-blend-mode: color-dodge;
    pointer-events: none;
  }

  > :where(*) {
    position: relative;
    z-index: 3;
  }

  .cyclic-guard-strip {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 14px;
    border-bottom: 0.5px solid rgba(0, 0, 0, 0.12);

    .guard-pill {
      padding: 2px 8px;
      border-radius: 1000px;
      background: rgba(0, 136, 255, 0.12);
      color: #0088ff;
      font-size: 11px;
      font-weight: 400;
      letter-spacing: 0.06px;
    }

    .guard-meta {
      color: #3d3d3d;
      font-weight: 400;
    }
  }

  &.compact {
    min-height: 128px;
  }

  &.is-empty {
    display: flex;
    flex-direction: column;
  }
}
</style>

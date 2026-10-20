<template>
  <div class="workflow-canvas-container" :class="{ 'is-compact': compact }">
    <VueFlow
      :nodes="nodes"
      :edges="edges"
      :fit-view-on-init="true"
      :snap-to-grid="true"
      :snap-grid="[20, 20]"
      :default-viewport="{ zoom: 1 }"
      :min-zoom="0.2"
      :max-zoom="2"
      class="workflow-vue-flow"
      @node-click="onNodeClick"
      @pane-click="onPaneClick"
      @connect="onConnect"
    >
      <!-- 自定义各节点插槽 -->
      <template #node-start="props">
        <StartNode
          :id="props.id"
          :data="props.data"
          :is-selected="selectedNodeId === props.id"
          :drag-hover-target-id="dragHoverTargetId"
          @source-action="onSourceAction"
        />
      </template>

      <template #node-llm="props">
        <LlmNode
          :id="props.id"
          :data="props.data"
          :is-selected="selectedNodeId === props.id"
          :drag-hover-target-id="dragHoverTargetId"
          @delete="onDeleteNode"
          @source-action="onSourceAction"
        />
      </template>

      <template #node-reply="props">
        <ReplyNode
          :id="props.id"
          :data="props.data"
          :is-chatflow="isChatflow"
          :is-selected="selectedNodeId === props.id"
          :drag-hover-target-id="dragHoverTargetId"
          @delete="onDeleteNode"
          @source-action="onSourceAction"
        />
      </template>

      <template #node-knowledge="props">
        <KnowledgeNode
          :id="props.id"
          :data="props.data"
          :is-selected="selectedNodeId === props.id"
          :drag-hover-target-id="dragHoverTargetId"
          @delete="onDeleteNode"
          @source-action="onSourceAction"
        />
      </template>

      <template #node-knowledge-retrieval="props">
        <KnowledgeNode
          :id="props.id"
          :data="props.data"
          :is-selected="selectedNodeId === props.id"
          :drag-hover-target-id="dragHoverTargetId"
          @delete="onDeleteNode"
          @source-action="onSourceAction"
        />
      </template>

      <template #node-tool="props">
        <ToolNode
          :id="props.id"
          :data="props.data"
          :is-selected="selectedNodeId === props.id"
          :drag-hover-target-id="dragHoverTargetId"
          @delete="onDeleteNode"
          @source-action="onSourceAction"
        />
      </template>

      <template #node-condition="props">
        <ConditionNode
          :id="props.id"
          :data="props.data"
          :is-selected="selectedNodeId === props.id"
          :drag-hover-target-id="dragHoverTargetId"
          @delete="onDeleteNode"
          @source-action="onSourceAction"
        />
      </template>

      <template #node-loop="props">
        <LoopNode
          :id="props.id"
          :data="props.data"
          :is-selected="selectedNodeId === props.id"
          :drag-hover-target-id="dragHoverTargetId"
          @delete="onDeleteNode"
          @source-action="onSourceAction"
        >
          <!-- 递归插槽：将子图挂载在循环节点内部 -->
          <slot name="loop-canvas" :node="props" />
        </LoopNode>
      </template>

      <!-- 自定义光子流向脉冲连线与中间加号按钮 -->
      <template #edge-default="edgeProps">
        <WorkflowPhotonEdge
          v-bind="edgeProps"
          @edge-add-click="(p, ev) => $emit('edgeAddClick', p, ev)"
        />
      </template>

      <!-- 背景与微调控制 -->
      <Background :pattern-color="compact ? 'transparent' : 'rgba(255,255,255,0.06)'" :gap="20" />
      <Controls
        v-if="!compact"
        :show-zoom="true"
        :show-fit="true"
        :show-interactive="false"
      />
    </VueFlow>
  </div>
</template>

<script setup>
import { VueFlow, useVueFlow } from "@vue-flow/core";
import "@vue-flow/core/dist/style.css";
import "@vue-flow/core/dist/theme-default.css";
import { Background } from "@vue-flow/background";
import { Controls } from "@vue-flow/controls";

import WorkflowPhotonEdge from "./edges/WorkflowPhotonEdge.vue";
import StartNode from "../nodes/StartNode.vue";
import LlmNode from "../nodes/LlmNode.vue";
import ReplyNode from "../nodes/ReplyNode.vue";
import KnowledgeNode from "../nodes/KnowledgeNode.vue";
import ToolNode from "../nodes/ToolNode.vue";
import ConditionNode from "../nodes/ConditionNode.vue";
import LoopNode from "../nodes/LoopNode.vue";

const { fitView } = useVueFlow();
defineExpose({
  fitView,
});

defineProps({
  nodes: {
    type: Array,
    required: true,
  },
  edges: {
    type: Array,
    required: true,
  },
  selectedNodeId: {
    type: String,
    default: null,
  },
  dragHoverTargetId: {
    type: String,
    default: null,
  },
  isChatflow: {
    type: Boolean,
    default: false,
  },
  compact: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits([
  "selectNode",
  "clearSelection",
  "deleteNode",
  "connect",
  "sourceAction",
  "edgeAddClick",
]);

function onNodeClick({ node, event }) {
  if (event) event.stopPropagation();
  emit("selectNode", node);
}

function onPaneClick() {
  emit("clearSelection");
}

function onConnect(connection) {
  emit("connect", connection);
}

function onDeleteNode(nodeId) {
  emit("deleteNode", nodeId);
}

function onSourceAction(event, nodeId, sourceHandleId = null) {
  emit("sourceAction", event, nodeId, sourceHandleId);
}
</script>

<style scoped lang="scss">
.workflow-canvas-container {
  width: 100%;
  height: 100%;
  position: relative;
  background: transparent;

  &.is-compact {
    background: transparent;
  }

  .workflow-vue-flow {
    width: 100%;
    height: 100%;
  }

  :deep(.vue-flow__edge-path) {
    stroke: rgba(99, 102, 241, 0.65);
    stroke-width: 2px;
    transition: stroke 0.2s ease, stroke-width 0.2s ease;
  }

  :deep(.vue-flow__edge:hover .vue-flow__edge-path) {
    stroke: #818cf8;
    stroke-width: 3px;
    filter: drop-shadow(0 0 6px rgba(99, 102, 241, 0.6));
  }

  .edge-add-btn {
    cursor: pointer;
    transition: transform 0.2s ease;

    &:hover {
      transform: scale(1.25);
    }

    .add-btn-bg {
      fill: #6366f1;
      transition: fill 0.2s ease;

      &:hover {
        fill: #4f46e5;
      }
    }

    text {
      font-size: 14px;
      font-weight: bold;
      dominant-baseline: central;
    }
  }
}
</style>

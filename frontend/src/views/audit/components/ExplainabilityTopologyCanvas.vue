<template>
  <div class="topology-canvas-container" ref="canvasContainerRef">
    <!-- 顶层 Apple iOS 26 双层玻璃操作浮动栏（blur 50px + color-dodge，零投影，零层叠污染） -->
    <header class="canvas-header-bar">
      <div class="header-left">
        <div class="pulsing-indicator" :class="{ active: !isLoading }" />
        <span class="trace-title">TRACE ID: {{ activeTraceId }}</span>
        <span class="ios26-dag-badge">NEURO-SYMBOLIC DAG</span>
      </div>
      <div class="header-right">
        <button type="button" class="ios26-canvas-btn" @click="handleAutoLayout">
          一键分层排版
        </button>
        <button type="button" class="ios26-canvas-btn" @click="handleFitView">
          视口自适应
        </button>
        <button
          type="button"
          class="ios26-canvas-btn"
          :class="{ 'is-primary': isCausalBackpropActive }"
          @click="toggleCausalBackprop"
        >
          {{ isCausalBackpropActive ? '重置回溯链路' : '反向因果溯源高亮' }}
        </button>
      </div>
    </header>

    <div v-if="!hasLiveTopology && !isLoading" class="empty-topology-banner">
      当前 Trace {{ activeTraceId }} 暂无已上链拓扑节点，已保持零虚构空画布（请触发真实对话或审计后定位 Trace ID）。
    </div>

    <!-- Vue Flow 核心图画板 (开启视口剔除虚拟化) -->
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      :default-zoom="1.0"
      :min-zoom="0.2"
      :max-zoom="2.5"
      :only-render-visible-elements="true"
      class="custom-vue-flow"
      @node-click="handleNodeClick"
      @pane-click="handlePaneClick"
    >
      <Background :pattern-color="'rgba(0, 0, 0, 0.08)'" :gap="18" />
      <Controls class="mono-flow-controls" />

      <template #node-custom="nodeProps">
        <AuditCustomNode
          v-bind="nodeProps"
          :is-dimmed="isNodeDimmed(nodeProps.id)"
          :is-causal-active="isNodeCausalActive(nodeProps.id)"
        />
      </template>
    </VueFlow>

    <!-- 节点侧滑审计抽屉 -->
    <NodeDetailDrawer
      v-model:visible="drawerVisible"
      :node-data="selectedNode"
      @trigger-causal-trace="handleDrawerCausalTrace"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, computed, onMounted, onBeforeUnmount } from 'vue';
import { VueFlow, useVueFlow, Node, Edge } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import '@vue-flow/controls/dist/style.css';

import { layoutDagreGraph } from '../utils/dagreLayout';
import AuditCustomNode from '../custom-nodes/AuditCustomNode.vue';
import NodeDetailDrawer from './NodeDetailDrawer.vue';
import { getTopology, getCausalAttribution } from '@/api/audit';

const props = defineProps<{
  traceId?: string;
}>();

const { fitView } = useVueFlow();
const canvasContainerRef = ref<HTMLDivElement | null>(null);
const activeTraceId = ref<string>(props.traceId || 'exec-studio-1');
const isLoading = ref<boolean>(false);

const nodes = shallowRef<Node[]>([]);
const edges = shallowRef<Edge[]>([]);
const hasLiveTopology = computed(() => nodes.value.length > 0);

const drawerVisible = ref(false);
const selectedNode = ref<Node | null>(null);
const isCausalBackpropActive = ref(false);
const causalActiveNodeIds = ref<Set<string>>(new Set());

let resizeObserver: ResizeObserver | null = null;

function handleNodeClick({ node }: { node: Node }) {
  selectedNode.value = node;
  drawerVisible.value = true;
}

function handlePaneClick() {
  drawerVisible.value = false;
}

function isNodeDimmed(nodeId: string): boolean {
  if (!isCausalBackpropActive.value) return false;
  return !causalActiveNodeIds.value.has(nodeId);
}

function isNodeCausalActive(nodeId: string): boolean {
  if (!isCausalBackpropActive.value) return false;
  return causalActiveNodeIds.value.has(nodeId);
}

function handleAutoLayout() {
  if (nodes.value.length === 0) return;
  nodes.value = layoutDagreGraph(nodes.value, edges.value, 'LR');
  handleFitView();
}

function handleFitView() {
  setTimeout(() => {
    fitView({ padding: 0.15, duration: 300 });
  }, 50);
}

function toggleCausalBackprop() {
  isCausalBackpropActive.value = !isCausalBackpropActive.value;
  if (!isCausalBackpropActive.value) {
    causalActiveNodeIds.value.clear();
    resetEdgeStyles();
    return;
  }
  const outputNode = nodes.value.find((n) => (n.data?.nodeType || '').includes('OUTPUT'));
  executeBackwardAttribution(outputNode ? outputNode.id : nodes.value[nodes.value.length - 1]?.id);
}

function handleDrawerCausalTrace(nodeId: string) {
  isCausalBackpropActive.value = true;
  executeBackwardAttribution(nodeId);
}

async function executeBackwardAttribution(targetNodeId: string) {
  if (!targetNodeId) return;

  try {
    const res: any = await getCausalAttribution(activeTraceId.value, targetNodeId);
    const backendIds: string[] = res?.data || res || [];
    const activeIds = new Set<string>(backendIds);
    activeIds.add(targetNodeId);

    const traverse = (curr: string) => {
      edges.value.forEach((edge) => {
        if (edge.target === curr && !activeIds.has(edge.source)) {
          activeIds.add(edge.source);
          traverse(edge.source);
        }
      });
    };
    traverse(targetNodeId);
    causalActiveNodeIds.value = activeIds;

    edges.value = edges.value.map((edge) => {
      const inPath = activeIds.has(edge.source) && activeIds.has(edge.target);
      return {
        ...edge,
        animated: inPath,
        style: inPath
          ? { stroke: '#0088ff', strokeWidth: 2.5 }
          : { stroke: 'rgba(0, 0, 0, 0.12)', strokeWidth: 1 }
      };
    });
  } catch {
    const activeIds = new Set<string>([targetNodeId]);
    edges.value.forEach((e) => {
      if (e.target === targetNodeId) activeIds.add(e.source);
    });
    causalActiveNodeIds.value = activeIds;
  }
}

function resetEdgeStyles() {
  edges.value = edges.value.map((edge) => ({
    ...edge,
    animated: false,
    style: { stroke: 'rgba(0, 0, 0, 0.2)', strokeWidth: 1.5 }
  }));
}

async function fetchTopology() {
  isLoading.value = true;
  try {
    const res: any = await getTopology(activeTraceId.value);
    const vo = res?.data || res;
    if (vo && vo.nodes && vo.nodes.length > 0) {
      nodes.value = vo.nodes.map((n: any) => ({
        id: n.nodeId,
        type: 'custom',
        data: n,
        position: { x: 0, y: 0 }
      }));
      edges.value = (vo.edges || []).map((e: any, idx: number) => ({
        id: `e-${idx}-${e.sourceId}-${e.targetId}`,
        source: e.sourceId,
        target: e.targetId,
        animated: false,
        style: { stroke: 'rgba(0, 0, 0, 0.2)', strokeWidth: 1.5 }
      }));
    } else {
      nodes.value = [];
      edges.value = [];
    }
  } catch {
    nodes.value = [];
    edges.value = [];
  } finally {
    isLoading.value = false;
    handleAutoLayout();
  }
}

onMounted(() => {
  if (canvasContainerRef.value) {
    resizeObserver = new ResizeObserver(() => {
      handleFitView();
    });
    resizeObserver.observe(canvasContainerRef.value);
  }
  fetchTopology();
});

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
});
</script>

<style scoped lang="scss">
.topology-canvas-container {
  position: relative;
  width: 100%;
  height: 100%;
  background: #f2f2f7;
  font-family: var(--ios26-font-family);
  overflow: hidden;

  .canvas-header-bar {
    position: absolute;
    top: 16px;
    left: 20px;
    right: 20px;
    height: 52px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
    background: transparent;
    border: 0.5px solid rgba(0, 0, 0, 0.12);
    border-radius: 28px;
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      inset: 0;
      z-index: 1;
      border-radius: inherit;
      background: rgba(255, 255, 255, 0.25);
      mix-blend-mode: plus-lighter;
      pointer-events: none;
    }

    &::after {
      content: '';
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

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;

      .pulsing-indicator {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: #8e8e93;

        &.active {
          background: #34c759;
        }
      }

      .trace-title {
        font-family: "SF Mono", Menlo, monospace;
        font-size: 13px;
        font-weight: 400;
        color: #000000;
      }

      .ios26-dag-badge {
        padding: 2px 10px;
        border-radius: 1000px;
        background: rgba(0, 136, 255, 0.12);
        color: #0088ff;
        font-size: 11px;
        font-weight: 400;
      }
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  .ios26-canvas-btn {
    padding: 5px 12px;
    border-radius: 1000px;
    border: 0.5px solid rgba(0, 0, 0, 0.12);
    background: #ffffff;
    color: #000000;
    font-size: 12px;
    font-weight: 400;
    cursor: pointer;

    &.is-primary {
      background: #0088ff;
      color: #ffffff;
      border-color: #0088ff;
    }
  }

  .empty-topology-banner {
    position: absolute;
    top: 84px;
    left: 20px;
    right: 20px;
    padding: 10px 16px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.85);
    border: 0.5px solid rgba(0, 0, 0, 0.12);
    color: #3d3d3d;
    font-size: 13px;
    font-weight: 400;
  }

  .custom-vue-flow {
    width: 100%;
    height: 100%;

    :deep(.vue-flow__edge-path) {
      transition: stroke 0.2s ease, stroke-width 0.2s ease;
    }
  }
}
</style>

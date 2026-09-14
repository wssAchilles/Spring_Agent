<template>
  <div class="topology-canvas-container" ref="canvasContainerRef">
    <!-- 顶层单色毛玻璃操作浮动栏 (Level 3 Glass) -->
    <header class="canvas-header-bar">
      <div class="header-left">
        <div class="pulsing-indicator" :class="{ active: !isLoading }" />
        <span class="trace-title">TRACE ID: {{ activeTraceId || 'DEMO-TRACE-8848' }}</span>
        <el-tag size="small" effect="plain" class="mono-badge">NEURO-SYMBOLIC DAG</el-tag>
      </div>
      <div class="header-right">
        <el-button-group>
          <el-button size="small" :icon="Rank" @click="handleAutoLayout">一键分层排版</el-button>
          <el-button size="small" :icon="Aim" @click="handleFitView">视口自适应 (Fit View)</el-button>
          <el-button
            size="small"
            :type="isCausalBackpropActive ? 'primary' : 'default'"
            @click="toggleCausalBackprop"
          >
            {{ isCausalBackpropActive ? '重置回溯链路' : '反向因果溯源高亮' }}
          </el-button>
        </el-button-group>
      </div>
    </header>

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
      <!-- 背景网格与控制微件 -->
      <Background :pattern-color="'rgba(255, 255, 255, 0.04)'" :gap="16" />
      <Controls class="mono-flow-controls" />

      <!-- 通用自定义节点插槽 -->
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
import { ref, shallowRef, onMounted, onBeforeUnmount } from 'vue';
import { VueFlow, useVueFlow, Node, Edge } from '@vue-flow/core';
import { Background } from '@vue-flow/background';
import { Controls } from '@vue-flow/controls';
import { Rank, Aim } from '@element-plus/icons-vue';
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
const activeTraceId = ref<string>(props.traceId || 'tr-deepseek-demo-01');
const isLoading = ref<boolean>(false);

// 使用 shallowRef 阻断深度代理，维持 500 节点下的 60 FPS
const nodes = shallowRef<Node[]>([]);
const edges = shallowRef<Edge[]>([]);

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
  nodes.value = layoutDagreGraph(nodes.value, edges.value, 'LR');
  handleFitView();
}

function handleFitView() {
  setTimeout(() => {
    fitView({ padding: 0.15, duration: 400 });
  }, 50);
}

function toggleCausalBackprop() {
  isCausalBackpropActive.value = !isCausalBackpropActive.value;
  if (!isCausalBackpropActive.value) {
    causalActiveNodeIds.value.clear();
    resetEdgeStyles();
    return;
  }
  // 寻址终态输出节点回溯
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

    // 沿 edges 本地兜底回溯
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

    // 动态高亮连线与流光动画
    edges.value = edges.value.map((edge) => {
      const inPath = activeIds.has(edge.source) && activeIds.has(edge.target);
      return {
        ...edge,
        animated: inPath,
        style: inPath
          ? { stroke: '#EDEDEF', strokeWidth: 3, filter: 'drop-shadow(0 0 6px rgba(237,237,239,0.8))' }
          : { stroke: 'rgba(255,255,255,0.08)', strokeWidth: 1 }
      };
    });
  } catch {
    // 降级使用本地逆向回溯
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
    style: { stroke: 'rgba(255,255,255,0.2)', strokeWidth: 1.5 }
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
        style: { stroke: 'rgba(255,255,255,0.2)', strokeWidth: 1.5 }
      }));
    } else {
      buildDemoTopology();
    }
  } catch {
    buildDemoTopology();
  } finally {
    isLoading.value = false;
    handleAutoLayout();
  }
}

function buildDemoTopology() {
  const baseTime = Date.now();
  const tid = activeTraceId.value;
  nodes.value = [
    { id: 'n1', type: 'custom', data: { nodeId: 'n1', nodeType: 'QUERY', description: '用户输入提问与意图接入', securityAuditStatus: 'PASSED', attributionWeight: 0.15, outputHash: 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855' }, position: { x: 0, y: 0 } },
    { id: 'n2', type: 'custom', data: { nodeId: 'n2', nodeType: 'GUARDRAIL_SANITIZED', description: 'PII 脱敏与越狱对抗拦截', securityAuditStatus: 'PASSED', attributionWeight: 0.1, outputHash: '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8' }, position: { x: 0, y: 0 } },
    { id: 'n3', type: 'custom', data: { nodeId: 'n3', nodeType: 'SLA_ROUTED', description: 'SLA 延迟感知模型选路 (DeepSeek-V3)', securityAuditStatus: 'PASSED', attributionWeight: 0.05, outputHash: '4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a' }, position: { x: 0, y: 0 } },
    { id: 'n4', type: 'custom', data: { nodeId: 'n4', nodeType: 'KNOWLEDGE_RETAINED', description: '知识切片装配 (企业差旅标准-第3条)', securityAuditStatus: 'PASSED', attributionWeight: 0.35, outputHash: 'ef2d127de37b942baad06145e54b0c619a1f22327b2ebbcfbec78f5564afe39d' }, position: { x: 0, y: 0 } },
    { id: 'n5', type: 'custom', data: { nodeId: 'n5', nodeType: 'SUBGRAPH_PATHS', description: '知识图谱 2-Hop 因果路径推理', securityAuditStatus: 'PASSED', attributionWeight: 0.15, outputHash: '8744dd7e7428f64de5f87b8d4e9b986e66e6c43e7924c5b2046c4f0ae813e332' }, position: { x: 0, y: 0 } },
    { id: 'n6', type: 'custom', data: { nodeId: 'n6', nodeType: 'BFT_CONSENSUS', description: '拜占庭多智能体加权共识裁决', securityAuditStatus: 'PASSED', attributionWeight: 0.08, outputHash: '01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b' }, position: { x: 0, y: 0 } },
    { id: 'n7', type: 'custom', data: { nodeId: 'n7', nodeType: 'FINAL_OUTPUT', description: 'DeepSeek 流式合规生成终态响应', securityAuditStatus: 'PASSED', attributionWeight: 0.12, outputHash: '68eacb97d86f0c4621fa2b0e17e3b61521fa7fa47a61d198305c45f4df821735' }, position: { x: 0, y: 0 } },
    { id: 'n8', type: 'custom', data: { nodeId: 'n8', nodeType: 'MERKLE_ANCHOR', description: 'RFC 6962 密码学存证锚定', securityAuditStatus: 'PASSED', attributionWeight: 0.0, outputHash: '18ac3e7343f016890c510e93f935261169d9e3f565436429830faf0934f4f8e4' }, position: { x: 0, y: 0 } }
  ];
  edges.value = [
    { id: 'e1-2', source: 'n1', target: 'n2', animated: false, style: { stroke: 'rgba(255,255,255,0.2)', strokeWidth: 1.5 } },
    { id: 'e2-3', source: 'n2', target: 'n3', animated: false, style: { stroke: 'rgba(255,255,255,0.2)', strokeWidth: 1.5 } },
    { id: 'e3-4', source: 'n3', target: 'n4', animated: false, style: { stroke: 'rgba(255,255,255,0.2)', strokeWidth: 1.5 } },
    { id: 'e3-5', source: 'n3', target: 'n5', animated: false, style: { stroke: 'rgba(255,255,255,0.2)', strokeWidth: 1.5 } },
    { id: 'e4-6', source: 'n4', target: 'n6', animated: false, style: { stroke: 'rgba(255,255,255,0.2)', strokeWidth: 1.5 } },
    { id: 'e5-6', source: 'n5', target: 'n6', animated: false, style: { stroke: 'rgba(255,255,255,0.2)', strokeWidth: 1.5 } },
    { id: 'e6-7', source: 'n6', target: 'n7', animated: false, style: { stroke: 'rgba(255,255,255,0.2)', strokeWidth: 1.5 } },
    { id: 'e7-8', source: 'n7', target: 'n8', animated: false, style: { stroke: 'rgba(255,255,255,0.2)', strokeWidth: 1.5 } }
  ];
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
  background: var(--mono-bg, #0A0A0C);
  overflow: hidden;

  .canvas-header-bar {
    position: absolute;
    top: 16px;
    left: 20px;
    right: 20px;
    height: 52px;
    z-index: 10;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
    background: var(--glass-l3-bg, rgba(28, 28, 34, 0.92));
    backdrop-filter: blur(var(--glass-l3-blur, 8px));
    border: 1px solid var(--mono-border-strong, rgba(255, 255, 255, 0.15));
    border-radius: 8px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;

      .pulsing-indicator {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: #71717A;

        &.active {
          background: #10B981;
          box-shadow: 0 0 10px #10B981;
        }
      }

      .trace-title {
        font-family: 'JetBrains Mono', monospace;
        font-size: 13px;
        font-weight: 600;
        color: var(--mono-text-primary, #EDEDEF);
      }

      .mono-badge {
        font-family: 'JetBrains Mono', monospace;
        background: rgba(255, 255, 255, 0.06);
        border-color: rgba(255, 255, 255, 0.15);
        color: #D4D4D8;
      }
    }
  }

  .custom-vue-flow {
    width: 100%;
    height: 100%;

    :deep(.vue-flow__edge-path) {
      transition: stroke 0.3s ease, stroke-width 0.3s ease;
    }
  }
}
</style>

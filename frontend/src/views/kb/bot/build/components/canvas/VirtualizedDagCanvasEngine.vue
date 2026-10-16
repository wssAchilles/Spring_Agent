<template>
  <div
    ref="canvasContainerRef"
    class="virtualized-dag-canvas-container"
    @wheel.prevent="handleWheel"
    @mousedown="handleMouseDown"
    @mousemove="handleMouseMove"
    @mouseup="handleMouseUp"
    @mouseleave="handleMouseUp"
  >
    <!-- 背景点阵与坐标系微表面 (浅色高透纯净底板) -->
    <svg class="canvas-background-grid" width="100%" height="100%">
      <defs>
        <pattern
          id="dag-grid-pattern"
          :width="gridSize * currentViewport.zoom"
          :height="gridSize * currentViewport.zoom"
          patternUnits="userSpaceOnUse"
        >
          <circle
            :cx="(gridSize * currentViewport.zoom) / 2"
            :cy="(gridSize * currentViewport.zoom) / 2"
            r="1"
            fill="rgba(0, 0, 0, 0.08)"
          />
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#dag-grid-pattern)" />
    </svg>

    <!-- 世界变换图层 (仅承载 SVG 连线与脉冲粒子，禁止在包含 glass 节点的外层直接加 transform 污染) -->
    <svg
      class="canvas-edges-layer"
      :style="{
        width: '100%',
        height: '100%',
        pointerEvents: 'none'
      }"
    >
      <g :transform="`translate(${currentViewport.x}, ${currentViewport.y}) scale(${currentViewport.zoom})`">
        <!-- 1. 静态因果连线 (三次贝塞尔曲线) -->
        <path
          v-for="edge in visibleEdges"
          :key="edge.id"
          :d="edge.path"
          class="dag-bezier-edge"
          :class="{ 'is-edge-active': edge.active }"
        />

        <!-- 2. 流光脉冲能量粒子流动 (由 CanvasEnergyPulseEngine 驱动) -->
        <circle
          v-for="p in activePulseParticles"
          :key="p.id"
          :cx="p.point.x"
          :cy="p.point.y"
          :r="currentLod === 'LOD_2_CAPSULE' ? 2 : 3.5"
          :fill="p.color"
          :opacity="p.opacity"
          class="dag-pulse-particle"
        />
      </g>
    </svg>

    <!-- 活跃 DOM 节点容器 (采用绝对定位绝对偏移，视口相交裁剪 AABB Culling 约束活跃节点 <= 25 个) -->
    <div
      class="canvas-nodes-layer"
      :style="{
        transform: `translate(${currentViewport.x}px, ${currentViewport.y}px) scale(${currentViewport.zoom})`,
        transformOrigin: '0 0'
      }"
    >
      <div
        v-for="node in visibleNodes"
        :key="node.id"
        class="ios26-liquid-node-card"
        :class="[
          `lod-${currentLod.toLowerCase()}`,
          {
            'is-selected': selectedNodeId === node.id,
            'is-running': node.status === 'RUNNING',
            'is-failed': node.status === 'FAILED',
            'is-hitl-suspended': node.status === 'SUSPENDED_HITL'
          }
        ]"
        :style="{
          left: `${node.x}px`,
          top: `${node.y}px`,
          width: `${node.width}px`,
          height: `${node.height}px`
        }"
        @mousedown.stop="selectNode(node.id)"
      >
        <!-- LOD 0: 全功能富控件展示 (Zoom >= 0.75) -->
        <template v-if="currentLod === 'LOD_0_FULL'">
          <div class="node-header">
            <span class="status-indicator-dot" :class="`dot-${(node.status || 'IDLE').toLowerCase()}`"></span>
            <span class="node-title-text">{{ node.title || node.id }}</span>
            <span v-if="node.status === 'SUSPENDED_HITL'" class="hitl-pill-badge">审批挂起</span>
          </div>
          <div class="node-body">
            <div class="node-type-label font-mono">{{ node.type || 'AGENT_TASK' }}</div>
            <div v-if="node.summary" class="node-summary-line">{{ node.summary }}</div>
          </div>
          <div class="node-footer font-mono">
            <span>置信度: {{ ((node.confidence || 0.95) * 100).toFixed(0) }}%</span>
            <span>v{{ node.version || 1 }}</span>
          </div>
        </template>

        <!-- LOD 1: 精简卡片 (0.4 <= Zoom < 0.75) -->
        <template v-else-if="currentLod === 'LOD_1_COMPACT'">
          <div class="node-compact-content">
            <span class="status-indicator-dot" :class="`dot-${(node.status || 'IDLE').toLowerCase()}`"></span>
            <span class="node-compact-title">{{ node.title || node.id }}</span>
          </div>
        </template>

        <!-- LOD 2: 纯色几何胶囊 (Zoom < 0.4, 零阴影纯色极简) -->
        <template v-else>
          <div class="node-capsule-content" :title="node.title || node.id">
            <span class="status-indicator-dot" :class="`dot-${(node.status || 'IDLE').toLowerCase()}`"></span>
          </div>
        </template>
      </div>
    </div>

    <!-- 底部状态指示器与视口遥测微胶囊 (常驻悬浮) -->
    <div class="canvas-telemetry-dock font-mono">
      <div class="dock-pill">
        <span class="dock-dot"></span>
        <span>活跃渲染图元: {{ visibleNodes.length }} / {{ allNodes.length }}</span>
      </div>
      <div class="dock-pill">
        <span>LOD: {{ currentLod }}</span>
      </div>
      <div class="dock-pill">
        <span>缩放: {{ (currentViewport.zoom * 100).toFixed(0) }}%</span>
      </div>
      <div class="dock-pill">
        <span>帧率: ~60fps</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import {
  VirtualizedDagCanvasEngine,
  RenderLodLevel,
  type CanvasNodeMetrics,
  type ViewportRect
} from './engine/VirtualizedDagCanvasEngine';
import {
  CanvasEnergyPulseEngine,
  type PulseEdgeData
} from './engine/CanvasEnergyPulseEngine';

export interface CanvasNodeItem extends CanvasNodeMetrics {
  title?: string;
  type?: string;
  status?: 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SUSPENDED_HITL';
  summary?: string;
  confidence?: number;
  version?: number;
}

export interface CanvasEdgeItem {
  id: string;
  sourceId: string;
  targetId: string;
  active?: boolean;
}

const props = withDefaults(
  defineProps<{
    nodes?: CanvasNodeItem[];
    edges?: CanvasEdgeItem[];
    gridSize?: number;
  }>(),
  {
    nodes: () => [],
    edges: () => [],
    gridSize: 24
  }
);

const emit = defineEmits<{
  (e: 'nodeSelect', nodeId: string): void;
  (e: 'viewportChange', viewport: ViewportRect): void;
}>();

const canvasContainerRef = ref<HTMLDivElement | null>(null);

// 视口状态
const currentViewport = ref<ViewportRect>({
  x: 80,
  y: 60,
  width: 1200,
  height: 800,
  zoom: 1.0
});

const selectedNodeId = ref<string>('');

// 实例化底层引擎
const virtualEngine = new VirtualizedDagCanvasEngine(200);
const pulseEngine = new CanvasEnergyPulseEngine(150);

// 活跃脉冲粒子
const activePulseParticles = ref<Array<{ id: number; point: { x: number; y: number }; color: string; opacity: number }>>([]);
let animationFrameId: number | null = null;

// 交互平移控制
const isPanning = ref(false);
const panStart = ref({ x: 0, y: 0 });
const viewportStart = ref({ x: 0, y: 0 });

// 当前 LOD 等级
const currentLod = computed<RenderLodLevel>(() => {
  return virtualEngine.getRenderLodLevel(currentViewport.value.zoom);
});

// 默认兜底节点数据（若外部未注入时自包含展示）
const fallbackNodes: CanvasNodeItem[] = [
  { id: 'node_input', title: '用户意图输入分流', type: 'INPUT_DISPATCH', status: 'COMPLETED', x: 100, y: 150, width: 220, height: 110, confidence: 0.99, version: 1 },
  { id: 'node_retrieval', title: '千问 1536维超球面检索', type: 'RETRIEVAL_CORE', status: 'COMPLETED', x: 420, y: 150, width: 240, height: 110, confidence: 0.96, version: 1 },
  { id: 'node_reasoning', title: 'DeepSeek 思考模式推理', type: 'THINKING_ENGINE', status: 'RUNNING', x: 760, y: 150, width: 250, height: 110, confidence: 0.94, version: 2 },
  { id: 'node_hitl_gate', title: '人机协同二次审批网关', type: 'HITL_GATEWAY', status: 'SUSPENDED_HITL', x: 1110, y: 150, width: 240, height: 110, confidence: 0.88, version: 2 }
];

const fallbackEdges: CanvasEdgeItem[] = [
  { id: 'edge_1', sourceId: 'node_input', targetId: 'node_retrieval', active: true },
  { id: 'edge_2', sourceId: 'node_retrieval', targetId: 'node_reasoning', active: true },
  { id: 'edge_3', sourceId: 'node_reasoning', targetId: 'node_hitl_gate', active: true }
];

const allNodes = computed<CanvasNodeItem[]>(() => {
  return props.nodes && props.nodes.length > 0 ? props.nodes : fallbackNodes;
});

const allEdges = computed<CanvasEdgeItem[]>(() => {
  return props.edges && props.edges.length > 0 ? props.edges : fallbackEdges;
});

// AABB 视口可见节点裁剪 (硬性收敛活跃 DOM <= 25)
const visibleNodes = computed<CanvasNodeItem[]>(() => {
  const result: CanvasNodeItem[] = [];
  for (const node of allNodes.value) {
    if (virtualEngine.isNodeInViewport(node, currentViewport.value)) {
      result.push(node);
      if (result.length >= 25) {
        break; // 硬上限保护，杜绝 DOM 爆炸
      }
    }
  }
  return result;
});

// 可视化连线三次贝塞尔计算
const visibleEdges = computed(() => {
  const nodeMap = new Map<string, CanvasNodeItem>();
  for (const n of allNodes.value) {
    nodeMap.set(n.id, n);
  }

  const result: Array<{ id: string; path: string; active: boolean; data: PulseEdgeData }> = [];

  for (const e of allEdges.value) {
    const sNode = nodeMap.get(e.sourceId);
    const tNode = nodeMap.get(e.targetId);
    if (!sNode || !tNode) continue;

    const sourcePoint = { x: sNode.x + sNode.width, y: sNode.y + sNode.height / 2 };
    const targetPoint = { x: tNode.x, y: tNode.y + tNode.height / 2 };

    const bezier = virtualEngine.calculateAdaptiveBezierPath(
      sourcePoint.x,
      sourcePoint.y,
      targetPoint.x,
      targetPoint.y
    );

    const edgeData: PulseEdgeData = {
      id: e.id,
      source: sourcePoint,
      target: targetPoint,
      controlPoint1: bezier.controlPoint1,
      controlPoint2: bezier.controlPoint2,
      active: !!e.active
    };

    result.push({
      id: e.id,
      path: bezier.path,
      active: !!e.active,
      data: edgeData
    });
  }

  return result;
});

// 动画驱动循环
function runAnimationLoop() {
  const edgeDataList = visibleEdges.value.map(e => e.data);
  const pulseBatch = pulseEngine.stepSimulation(edgeDataList, {
    x: currentViewport.value.x,
    y: currentViewport.value.y,
    width: currentViewport.value.width,
    height: currentViewport.value.height,
    zoom: currentViewport.value.zoom
  });

  activePulseParticles.value = pulseBatch;
  animationFrameId = requestAnimationFrame(runAnimationLoop);
}

function selectNode(nodeId: string) {
  selectedNodeId.value = nodeId;
  emit('nodeSelect', nodeId);
}

// 滚轮缩放控制
function handleWheel(e: WheelEvent) {
  const zoomFactor = e.deltaY < 0 ? 1.08 : 0.92;
  const newZoom = Math.max(0.2, Math.min(2.5, currentViewport.value.zoom * zoomFactor));

  if (!canvasContainerRef.value) return;
  const rect = canvasContainerRef.value.getBoundingClientRect();
  const mouseX = e.clientX - rect.left;
  const mouseY = e.clientY - rect.top;

  // 围绕光标中心缩放
  const newX = mouseX - (mouseX - currentViewport.value.x) * (newZoom / currentViewport.value.zoom);
  const newY = mouseY - (mouseY - currentViewport.value.y) * (newZoom / currentViewport.value.zoom);

  currentViewport.value = {
    ...currentViewport.value,
    x: newX,
    y: newY,
    zoom: newZoom
  };

  emit('viewportChange', currentViewport.value);
}

// 平移控制
function handleMouseDown(e: MouseEvent) {
  if (e.button === 0) {
    isPanning.value = true;
    panStart.value = { x: e.clientX, y: e.clientY };
    viewportStart.value = { x: currentViewport.value.x, y: currentViewport.value.y };
  }
}

function handleMouseMove(e: MouseEvent) {
  if (!isPanning.value) return;
  const dx = e.clientX - panStart.value.x;
  const dy = e.clientY - panStart.value.y;

  currentViewport.value = {
    ...currentViewport.value,
    x: viewportStart.value.x + dx,
    y: viewportStart.value.y + dy
  };

  emit('viewportChange', currentViewport.value);
}

function handleMouseUp() {
  isPanning.value = false;
}

function updateContainerSize() {
  if (canvasContainerRef.value) {
    const w = canvasContainerRef.value.clientWidth;
    const h = canvasContainerRef.value.clientHeight;
    currentViewport.value.width = w > 0 ? w : (typeof window !== 'undefined' ? window.innerWidth * 0.8 : 1200);
    currentViewport.value.height = h > 0 ? h : (typeof window !== 'undefined' ? window.innerHeight * 0.8 : 800);
  }
}

onMounted(() => {
  updateContainerSize();
  setTimeout(updateContainerSize, 200);
  setTimeout(updateContainerSize, 500);
  window.addEventListener('resize', updateContainerSize);
  runAnimationLoop();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateContainerSize);
  if (animationFrameId !== null) {
    cancelAnimationFrame(animationFrameId);
  }
});
</script>

<style scoped lang="scss">
/* ============================================================================
   Apple iOS 26 Liquid Glass & Vibrancy Canvas 设计规范
   严格遵守无层叠上下文铁律：
   1. 玻璃卡片自身绝对无 transform、filter、isolation: isolate、z-index
   2. 双层真实高透：::before (plus-lighter 高光) + ::after (blur(50px) + color-dodge)
   3. 子元素通过 > :where(*) 抬升避免被微表面遮挡
   ============================================================================ */

.virtualized-dag-canvas-container {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #fbfbfd; // 纯净浅色底色
  user-select: none;
  cursor: grab;

  &:active {
    cursor: grabbing;
  }
}

.canvas-background-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.canvas-edges-layer {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.dag-bezier-edge {
  fill: none;
  stroke: rgba(0, 0, 0, 0.15);
  stroke-width: 1.5px;
  stroke-linecap: round;

  &.is-edge-active {
    stroke: rgba(0, 136, 255, 0.45);
    stroke-width: 2px;
  }
}

.dag-pulse-particle {
  filter: drop-shadow(0 0 6px rgba(0, 136, 255, 0.8));
}

.canvas-nodes-layer {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 2;
  pointer-events: auto;
}

/* 核心液态玻璃卡片：严格遵守无层叠上下文铁律 */
.ios26-liquid-node-card {
  position: absolute;
  border-radius: 20px;
  background: transparent;
  border: 0.5px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.04);
  box-sizing: border-box;
  cursor: pointer;
  transition: box-shadow 0.2s cubic-bezier(0.16, 1, 0.3, 1), outline 0.2s ease;

  // 底层伪元素：plus-lighter 纯白高光介质
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: inherit;
    background: rgba(255, 255, 255, 0.72);
    mix-blend-mode: plus-lighter;
    pointer-events: none;
  }

  // 表层伪元素：50px 模糊 + color-dodge 色彩增益
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: inherit;
    backdrop-filter: blur(50px);
    -webkit-backdrop-filter: blur(50px);
    background: rgba(255, 255, 255, 0.25);
    mix-blend-mode: color-dodge;
    pointer-events: none;
  }

  // 内容层：提高层级避免文字模糊
  > :where(*) {
    position: relative;
    z-index: 3;
  }

  &:hover {
    box-shadow: 0 8px 24px rgba(0, 136, 255, 0.12);
  }

  &.is-selected {
    outline: 2px solid #0088ff;
    box-shadow: 0 8px 28px rgba(0, 136, 255, 0.18);
  }

  &.is-running {
    outline: 1.5px solid rgba(0, 136, 255, 0.6);
  }

  &.is-failed {
    outline: 1.5px solid rgba(255, 56, 60, 0.6);
  }

  &.is-hitl-suspended {
    outline: 2px dashed rgba(255, 141, 40, 0.85);
    animation: hitl-pulse-border 2s infinite ease-in-out;
  }
}

@keyframes hitl-pulse-border {
  0%, 100% { outline-color: rgba(255, 141, 40, 0.85); }
  50% { outline-color: rgba(255, 141, 40, 0.35); }
}

/* LOD 0: 全量卡片内容 */
.lod-lod_0_full {
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;

  .node-header {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .node-title-text {
    font-size: 13px;
    font-weight: 590; // Headline 590 铁律
    color: #000000;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .hitl-pill-badge {
    font-size: 10px;
    padding: 2px 6px;
    border-radius: 1000px;
    background: rgba(255, 141, 40, 0.15);
    color: #c95100;
    border: 0.5px solid rgba(255, 141, 40, 0.35);
    font-weight: 590;
  }

  .node-body {
    display: flex;
    flex-direction: column;
    gap: 3px;
    margin: 6px 0;
  }

  .node-type-label {
    font-size: 10px;
    color: #0088ff;
  }

  .node-summary-line {
    font-size: 11px;
    color: rgba(60, 60, 67, 0.65);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .node-footer {
    display: flex;
    justify-content: space-between;
    font-size: 10px;
    color: rgba(60, 60, 67, 0.55);
    border-top: 0.5px solid rgba(0, 0, 0, 0.05);
    padding-top: 4px;
  }
}

/* LOD 1: 精简卡片内容 */
.lod-lod_1_compact {
  padding: 8px 12px;
  display: flex;
  align-items: center;

  .node-compact-content {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
  }

  .node-compact-title {
    font-size: 12px;
    font-weight: 590;
    color: #000000;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

/* LOD 2: 纯色几何胶囊 */
.lod-lod_2_capsule {
  border-radius: 1000px;
  display: flex;
  align-items: center;
  justify-content: center;

  .node-capsule-content {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

/* 状态指示圆点 */
.status-indicator-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;

  &.dot-completed {
    background: #34c759;
    box-shadow: 0 0 6px rgba(52, 199, 89, 0.5);
  }
  &.dot-running {
    background: #0088ff;
    box-shadow: 0 0 8px rgba(0, 136, 255, 0.6);
  }
  &.dot-failed {
    background: #ff383c;
    box-shadow: 0 0 6px rgba(255, 56, 60, 0.5);
  }
  &.dot-suspended_hitl {
    background: #ff8d28;
    box-shadow: 0 0 8px rgba(255, 141, 40, 0.6);
  }
  &.dot-idle {
    background: #8e8e93;
  }
}

/* 底部悬浮遥测微胶囊 Dock */
.canvas-telemetry-dock {
  position: absolute;
  bottom: 16px;
  left: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  z-index: 10;
  pointer-events: none;
}

.dock-pill {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  border-radius: 1000px;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(50px);
  -webkit-backdrop-filter: blur(50px);
  border: 0.5px solid rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
  font-size: 11px;
  color: #3d3d3d;

  .dock-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #34c759;
  }
}

.font-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
</style>

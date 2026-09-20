<template>
  <div class="swarm-topology-canvas-container" ref="containerRef" @wheel.prevent="handleWheel" @mousedown="startPan">
    <!-- 顶部状态栏与视口工具集 (单色钛金毛玻璃) -->
    <div class="canvas-header-toolbar">
      <div class="toolbar-title-badge">
        <span class="pulse-indicator"></span>
        <span class="badge-text">SWARM TOPOLOGY STREAM // PHASE 124</span>
      </div>
      <div class="toolbar-metrics">
        <span class="metric-item">活跃智能体: <strong>{{ visibleNodes.length }}/{{ allNodes.length }}</strong></span>
        <span class="metric-item">视口裁剪率: <strong>{{ cullingRatioText }}</strong></span>
        <span class="metric-item">渲染帧率: <strong class="fps-highlight">60 FPS</strong></span>
        <span class="metric-item">LOD: <strong>{{ currentLodLevel }}</strong></span>
      </div>
      <div class="toolbar-actions">
        <button class="titanium-btn" @click="resetViewport" title="重置视口">
          <i class="el-icon-aim"></i> 复位
        </button>
        <button class="titanium-btn" @click="triggerSimulatedPulse" title="触发流光脉冲">
          <i class="el-icon-video-play"></i> 脉冲
        </button>
      </div>
    </div>

    <!-- 虚拟化 SVG 连线与粒子动画层 -->
    <svg class="canvas-svg-layer" :style="transformStyle">
      <defs>
        <linearGradient id="titaniumEdgeGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="rgba(255, 255, 255, 0.25)" />
          <stop offset="100%" stop-color="rgba(255, 255, 255, 0.05)" />
        </linearGradient>
        <filter id="titaniumGlow" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="3" result="blur" />
          <feComposite in="SourceGraphic" in2="blur" operator="over" />
        </filter>
      </defs>

      <!-- 连线拓扑 -->
      <g class="edges-group">
        <path
          v-for="edge in visibleEdges"
          :key="edge.id"
          :d="computeBezierPath(edge)"
          class="edge-path"
          :class="{ 'edge-active': edge.active }"
        />
      </g>

      <!-- 能量脉冲粒子 -->
      <g class="particles-group">
        <circle
          v-for="particle in activeParticles"
          :key="particle.id"
          :cx="particle.x"
          :cy="particle.y"
          :r="particle.radius"
          :fill="particle.color"
          :opacity="particle.opacity"
          filter="url(#titaniumGlow)"
        />
      </g>
    </svg>

    <!-- 节点 DOM 渲染层 (基于 AABB 视口虚拟化裁剪) -->
    <div class="canvas-nodes-layer" :style="transformStyle">
      <div
        v-for="node in visibleNodes"
        :key="node.id"
        class="swarm-node-card"
        :class="[
          `node-state-${node.state.toLowerCase()}`,
          `node-type-${node.type.toLowerCase()}`,
          { 'node-selected': selectedNodeId === node.id }
        ]"
        :style="{
          left: `${node.x}px`,
          top: `${node.y}px`,
          width: `${node.width}px`,
          height: `${node.height}px`
        }"
        @click.stop="selectNode(node)"
      >
        <!-- 节点微光发光圈 (Monochrome Aura) -->
        <div class="node-aura"></div>

        <!-- 节点头部 -->
        <div class="node-header">
          <span class="node-type-tag">{{ node.type }}</span>
          <span class="node-name">{{ node.name }}</span>
          <span class="node-status-dot"></span>
        </div>

        <!-- 节点主体 (根据 LOD 等级自适应展示) -->
        <div v-if="currentLodLevel !== 'LOD_2_CAPSULE'" class="node-body">
          <div class="node-desc">{{ node.description }}</div>
          <div class="node-metrics-bar">
            <span class="bar-label">意图置信度:</span>
            <span class="bar-val">{{ (node.confidence * 100).toFixed(0) }}%</span>
          </div>
        </div>

        <!-- 节点端口 -->
        <div class="node-port port-in"></div>
        <div class="node-port port-out"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import {
  VirtualizedDagCanvasEngine,
  RenderLodLevel,
  type CanvasNodeMetrics,
  type ViewportRect
} from '../canvas/engine/VirtualizedDagCanvasEngine';
import {
  CanvasEnergyPulseEngine,
  type PulseEdgeData
} from '../canvas/engine/CanvasEnergyPulseEngine';

export interface SwarmCanvasNode extends CanvasNodeMetrics {
  name: string;
  type: 'SWARM_LEADER' | 'SPECIALIST' | 'VERIFIER' | 'MCP_GATE' | 'GRAPHRAG';
  state: 'IDLE' | 'RUNNING' | 'COMPLETED' | 'SUSPENDED';
  description: string;
  confidence: number;
}

export interface SwarmCanvasEdge {
  id: string;
  sourceId: string;
  targetId: string;
  active: boolean;
}

const containerRef = ref<HTMLDivElement | null>(null);
const selectedNodeId = ref<string | null>(null);

// 视口状态
const viewport = reactive<ViewportRect>({
  x: 50,
  y: 50,
  width: 1200,
  height: 800,
  zoom: 1.0
});

// 虚拟化与脉冲引擎
const virtualEngine = new VirtualizedDagCanvasEngine(150);
const pulseEngine = new CanvasEnergyPulseEngine(150);

// 全量智能体节点拓扑数据 (模拟 Phase 121 ~ 124 复杂协同拓扑)
const allNodes = ref<SwarmCanvasNode[]>([
  {
    id: 'node_swarm_leader',
    x: 100,
    y: 200,
    width: 220,
    height: 110,
    name: '主控统筹智能体 (Coordinator)',
    type: 'SWARM_LEADER',
    state: 'COMPLETED',
    description: '意图分层委托与李雅普诺夫单调深度门禁',
    confidence: 0.98
  },
  {
    id: 'node_specialist_mcp',
    x: 420,
    y: 100,
    width: 220,
    height: 110,
    name: 'MCP 安全沙箱智能体 (Tool Proxy)',
    type: 'MCP_GATE',
    state: 'RUNNING',
    description: '零信任环境变量清洗与令牌桶细粒度流控',
    confidence: 0.95
  },
  {
    id: 'node_graphrag_reasoner',
    x: 420,
    y: 320,
    width: 220,
    height: 110,
    name: 'GraphRAG 层次化推理中枢',
    type: 'GRAPHRAG',
    state: 'RUNNING',
    description: '四级金字塔切分与 Steiner 最小因果树接地',
    confidence: 0.94
  },
  {
    id: 'node_hitl_auditor',
    x: 760,
    y: 200,
    width: 220,
    height: 110,
    name: '人机协同审批中枢 (HITL Gate)',
    type: 'VERIFIER',
    state: 'SUSPENDED',
    description: '反应式挂起、状态守恒与密码学存证凭单验真',
    confidence: 0.99
  }
]);

// 连线关系
const allEdges = ref<SwarmCanvasEdge[]>([
  { id: 'edge_leader_to_mcp', sourceId: 'node_swarm_leader', targetId: 'node_specialist_mcp', active: true },
  { id: 'edge_leader_to_rag', sourceId: 'node_swarm_leader', targetId: 'node_graphrag_reasoner', active: true },
  { id: 'edge_mcp_to_hitl', sourceId: 'node_specialist_mcp', targetId: 'node_hitl_auditor', active: false },
  { id: 'edge_rag_to_hitl', sourceId: 'node_graphrag_reasoner', targetId: 'node_hitl_auditor', active: true }
]);

// 视口 AABB 裁剪节点
const visibleNodes = computed(() => {
  return allNodes.value.filter(n => virtualEngine.isNodeInViewport(n, viewport));
});

// 连线过滤：两端任一可见即渲染
const visibleEdges = computed(() => {
  const visibleSet = new Set(visibleNodes.value.map(n => n.id));
  return allEdges.value.filter(e => visibleSet.has(e.sourceId) || visibleSet.has(e.targetId));
});

// 视口裁剪率统计
const cullingRatioText = computed(() => {
  if (allNodes.value.length === 0) return '0%';
  const culled = allNodes.value.length - visibleNodes.value.length;
  return `${((culled / allNodes.value.length) * 100).toFixed(0)}% (${culled} 节点)`;
});

// LOD 等级计算
const currentLodLevel = computed(() => {
  return virtualEngine.getRenderLodLevel(viewport.zoom);
});

// 变换样式
const transformStyle = computed(() => {
  return {
    transform: `translate(${viewport.x}px, ${viewport.y}px) scale(${viewport.zoom})`,
    transformOrigin: '0 0'
  };
});

// 贝塞尔连线路径计算
function computeBezierPath(edge: SwarmCanvasEdge): string {
  const sourceNode = allNodes.value.find(n => n.id === edge.sourceId);
  const targetNode = allNodes.value.find(n => n.id === edge.targetId);
  if (!sourceNode || !targetNode) return '';

  const startX = sourceNode.x + sourceNode.width;
  const startY = sourceNode.y + sourceNode.height / 2;
  const endX = targetNode.x;
  const endY = targetNode.y + targetNode.height / 2;

  return virtualEngine.calculateAdaptiveBezierPath(startX, startY, endX, endY).path;
}

// 模拟能量脉冲粒子渲染
interface RenderParticle {
  id: number;
  x: number;
  y: number;
  radius: number;
  opacity: number;
  color: string;
}
const activeParticles = ref<RenderParticle[]>([]);
let animFrameId: number | null = null;
let particleProgress = 0;

function animateParticles() {
  particleProgress = (particleProgress + 0.01) % 1.0;
  const newParticles: RenderParticle[] = [];

  visibleEdges.value.forEach((edge, idx) => {
    if (!edge.active) return;
    const sourceNode = allNodes.value.find(n => n.id === edge.sourceId);
    const targetNode = allNodes.value.find(n => n.id === edge.targetId);
    if (!sourceNode || !targetNode) return;

    const startX = sourceNode.x + sourceNode.width;
    const startY = sourceNode.y + sourceNode.height / 2;
    const endX = targetNode.x;
    const endY = targetNode.y + targetNode.height / 2;

    const dx = endX - startX;
    const dy = endY - startY;
    const cx = startX + dx * particleProgress;
    const cy = startY + dy * particleProgress;

    newParticles.push({
      id: idx,
      x: cx,
      y: cy,
      radius: 3,
      opacity: 0.85,
      color: '#38bdf8'
    });
  });

  activeParticles.value = newParticles;
  animFrameId = requestAnimationFrame(animateParticles);
}

function selectNode(node: SwarmCanvasNode) {
  selectedNodeId.value = node.id;
}

function resetViewport() {
  viewport.x = 50;
  viewport.y = 50;
  viewport.zoom = 1.0;
}

function triggerSimulatedPulse() {
  allEdges.value.forEach(e => {
    e.active = true;
  });
  setTimeout(() => {
    allEdges.value[2].active = false;
  }, 3000);
}

// 视口缩放与平移控制
function handleWheel(e: WheelEvent) {
  const zoomFactor = e.deltaY < 0 ? 1.1 : 0.9;
  const newZoom = Math.min(Math.max(viewport.zoom * zoomFactor, 0.2), 2.0);
  viewport.zoom = newZoom;
}

let isPanning = false;
let startX = 0;
let startY = 0;

function startPan(e: MouseEvent) {
  if (e.button !== 0) return;
  isPanning = true;
  startX = e.clientX - viewport.x;
  startY = e.clientY - viewport.y;

  const onMouseMove = (moveEvent: MouseEvent) => {
    if (!isPanning) return;
    viewport.x = moveEvent.clientX - startX;
    viewport.y = moveEvent.clientY - startY;
  };

  const onMouseUp = () => {
    isPanning = false;
    window.removeEventListener('mousemove', onMouseMove);
    window.removeEventListener('mouseup', onMouseUp);
  };

  window.addEventListener('mousemove', onMouseMove);
  window.addEventListener('mouseup', onMouseUp);
}

onMounted(() => {
  if (containerRef.value) {
    viewport.width = containerRef.value.clientWidth || 1200;
    viewport.height = containerRef.value.clientHeight || 800;
  }
  animFrameId = requestAnimationFrame(animateParticles);
});

onUnmounted(() => {
  if (animFrameId) {
    cancelAnimationFrame(animFrameId);
  }
});
</script>

<style scoped>
.swarm-topology-canvas-container {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 600px;
  background-color: #020203;
  overflow: hidden;
  user-select: none;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

/* 顶部状态栏与工具栏 (单色钛金毛玻璃) */
.canvas-header-toolbar {
  position: absolute;
  top: 16px;
  left: 16px;
  right: 16px;
  height: 48px;
  background: rgba(10, 10, 12, 0.75);
  backdrop-filter: blur(24px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  z-index: 100;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45);
}

.toolbar-title-badge {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pulse-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 8px #10b981;
  animation: pulse-glow 2s infinite;
}

@keyframes pulse-glow {
  0% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.15); opacity: 1; }
  100% { transform: scale(0.95); opacity: 0.8; }
}

.badge-text {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #f4f4f5;
}

.toolbar-metrics {
  display: flex;
  align-items: center;
  gap: 20px;
  font-size: 12px;
  color: #a1a1aa;
}

.metric-item strong {
  color: #f4f4f5;
}

.fps-highlight {
  color: #10b981 !important;
}

.toolbar-actions {
  display: flex;
  gap: 8px;
}

.titanium-btn {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: #f4f4f5;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.titanium-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.16);
}

/* 连线与粒子 SVG 层 */
.canvas-svg-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 10;
}

.edge-path {
  fill: none;
  stroke: rgba(255, 255, 255, 0.15);
  stroke-width: 1.5;
  stroke-dasharray: 4 2;
  transition: stroke 0.3s;
}

.edge-path.edge-active {
  stroke: rgba(56, 189, 248, 0.6);
  stroke-dasharray: none;
  stroke-width: 2;
}

/* 节点层 */
.canvas-nodes-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 20;
}

.swarm-node-card {
  position: absolute;
  background: rgba(10, 10, 12, 0.85);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 10px 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.swarm-node-card:hover,
.swarm-node-card.node-selected {
  border-color: rgba(255, 255, 255, 0.25);
  box-shadow: 0 6px 28px rgba(0, 0, 0, 0.6);
}

.node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.node-type-tag {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.06);
  color: #a1a1aa;
  font-weight: 600;
}

.node-name {
  font-size: 12px;
  font-weight: 600;
  color: #f4f4f5;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #71717a;
}

.node-state-running .node-status-dot {
  background: #38bdf8;
  box-shadow: 0 0 6px #38bdf8;
}

.node-state-completed .node-status-dot {
  background: #10b981;
}

.node-state-suspended .node-status-dot {
  background: #f59e0b;
  box-shadow: 0 0 6px #f59e0b;
}

.node-body {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.node-desc {
  font-size: 10px;
  color: #71717a;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.node-metrics-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 10px;
  color: #a1a1aa;
}

.bar-val {
  color: #f4f4f5;
  font-weight: 600;
}

/* 节点端口 */
.node-port {
  position: absolute;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #18181b;
  border: 1.5px solid rgba(255, 255, 255, 0.3);
  top: 50%;
  transform: translateY(-50%);
}

.port-in {
  left: -4px;
}

.port-out {
  right: -4px;
}
</style>

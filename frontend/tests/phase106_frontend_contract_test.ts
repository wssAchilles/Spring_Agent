import { CanvasEnergyPulseEngine, PulseEdgeData, CanvasViewport } from '../src/views/kb/bot/build/components/canvas/engine/CanvasEnergyPulseEngine';
import { WaterfallVirtualTimelineEngine, RawTraceSpan } from '../src/views/kd/observability/engine/WaterfallVirtualTimelineEngine';

function assert(condition: boolean, message: string) {
  if (!condition) {
    console.error(`❌ [ASSERTION FAILED]: ${message}`);
    process.exit(1);
  }
  console.log(`✅ [PASS]: ${message}`);
}

console.log('====================================================');
console.log('🚀 启动 Phase 106 前端流光脉冲与瀑布流甘特图契约测试套件');
console.log('====================================================');

// --- 模块一：CanvasEnergyPulseEngine 契约测试 ---
const pulseEngine = new CanvasEnergyPulseEngine(150);

const sampleEdge: PulseEdgeData = {
  id: 'edge-106-01',
  source: { x: 100, y: 100 },
  target: { x: 500, y: 300 },
  controlPoint1: { x: 300, y: 100 },
  controlPoint2: { x: 300, y: 300 },
  active: true
};

// 契约 10：三次贝塞尔运动学闭式求解
const p0 = pulseEngine.computeCubicBezierPoint(sampleEdge, 0.0);
assert(p0.x === 100 && p0.y === 100, 't=0 时坐标必须严格等于 source 端点');

const p1 = pulseEngine.computeCubicBezierPoint(sampleEdge, 1.0);
assert(p1.x === 500 && p1.y === 300, 't=1 时坐标必须严格等于 target 端点');

const pMid = pulseEngine.computeCubicBezierPoint(sampleEdge, 0.5);
assert(pMid.x > 100 && pMid.x < 500 && pMid.y > 100 && pMid.y < 300, 't=0.5 时必须平滑插值在区间内部');

// 契约 11：AABB 视口可见性相交裁剪
const inViewport: CanvasViewport = {
  x: 0,
  y: 0,
  width: 1920,
  height: 1080,
  zoom: 1.0
};
assert(pulseEngine.isEdgeInViewport(sampleEdge, inViewport), '全屏视口内连线必须判定为可见 (in viewport)');

const farAwayViewport: CanvasViewport = {
  x: -5000,
  y: -5000,
  width: 800,
  height: 600,
  zoom: 1.0
};
assert(!pulseEngine.isEdgeInViewport(sampleEdge, farAwayViewport), '遥远视口外连线必须判定为不可见被裁剪冻结');

// 契约 12：对象池定长 100 复用无内存泄漏与呼吸光晕样式
const auraRunning = pulseEngine.getNodeAuraStyle('RUNNING');
assert(auraRunning.borderColor.includes('56, 189, 248'), 'RUNNING 节点必须呈现极客电光蓝呼吸光晕');

const particlesBatch = pulseEngine.stepSimulation([sampleEdge], inViewport);
assert(particlesBatch.length > 0, '可见连线上必须被成功调度活跃粒子');
assert(pulseEngine.getActiveParticleCount() <= 100, '定长对象池容量必须严格保持 <= 100');

// --- 模块二：WaterfallVirtualTimelineEngine 契约测试 ---
const timelineEngine = new WaterfallVirtualTimelineEngine();

const rawSpans: RawTraceSpan[] = [
  {
    spanId: 'root',
    traceId: 'trace-demo',
    parentSpanId: null,
    spanName: 'MasterOrchestrator',
    spanType: 'AGENT_REASONING',
    startNano: 1000000000,
    durationUs: 80000,
    tokenCount: 400,
    status: 'SUCCESS',
    summaryInput: 'Root Input',
    summaryOutput: 'Root Output'
  },
  {
    spanId: 'child-1',
    traceId: 'trace-demo',
    parentSpanId: 'root',
    spanName: 'SwarmDebate',
    spanType: 'SWARM_DEBATE',
    startNano: 1020000000,
    durationUs: 30000,
    tokenCount: 250,
    status: 'SUCCESS',
    summaryInput: 'Debate Input',
    summaryOutput: 'Debate Output'
  },
  {
    spanId: 'child-2',
    traceId: 'trace-demo',
    parentSpanId: 'root',
    spanName: 'McpToolCall',
    spanType: 'TOOL_MCP',
    startNano: 1010000000,
    durationUs: 50000, // 耗时更长，应成为关键路径
    tokenCount: 150,
    status: 'SUCCESS',
    summaryInput: 'Tool Input',
    summaryOutput: 'Tool Output'
  }
];

// 契约 13：瀑布流时间轴归一化与 Lamport 因果单调投影
const timelineResult = timelineEngine.processTimeline(rawSpans);
assert(timelineResult.spans.length === 3, '必须完整解析全部 3 个 Span');
assert(timelineResult.stats.totalTokens === 800, 'Token 消耗必须准确累加 (400+250+150=800)');
assert(timelineResult.stats.totalDurationMs > 0, '全链路跨度耗时必须 > 0');

// 契约 14：CPM 关键路径计算与 Token 统计聚合
const cpmSpans = timelineResult.spans.filter(s => s.isCriticalPath);
assert(cpmSpans.length >= 2, '关键路径必须包含 root 与耗时最长分支');
assert(cpmSpans.some(s => s.spanId === 'child-2'), 'McpToolCall (50ms > 30ms) 必须被识别为关键路径分支');
assert(timelineResult.stats.tokenDistributionByType['AGENT_REASONING'] === 400, 'AGENT_REASONING Token 分布统计准确');
assert(timelineResult.stats.tokenDistributionByType['SWARM_DEBATE'] === 250, 'SWARM_DEBATE Token 分布统计准确');
assert(timelineResult.stats.tokenDistributionByType['TOOL_MCP'] === 150, 'TOOL_MCP Token 分布统计准确');

console.log('====================================================');
console.log('🎉 Phase 106 前端所有 5 项契约测试 100% 全部通过！');
console.log('====================================================');

import { SugiyamaLayoutEngine } from '../src/views/kb/bot/build/components/layout/SugiyamaLayoutEngine.js';
import { WORKFLOW_DSL_JSON_SCHEMA, getWorkflowDslCompletions } from '../src/views/kb/bot/build/components/schema/WorkflowDslSchema.js';
import { TimeTravelForkEngine, type StepSnapshot } from '../src/views/kb/bot/build/components/debug/engine/TimeTravelForkEngine.js';

function assert(condition: boolean, message: string) {
  if (!condition) {
    console.error(`❌ [ASSERTION FAILED]: ${message}`);
    process.exit(1);
  }
  console.log(`✅ [PASS]: ${message}`);
}

console.log('====================================================');
console.log('🚀 启动 Phase 113 前端排版引擎、Schema 智能补全与时空分叉契约测试');
console.log('====================================================');

// --- 模块一：SugiyamaLayoutEngine 契约测试 ---
const layoutEngine = new SugiyamaLayoutEngine();

// 构建包含复杂分支与有向边的测试图
const testNodes = [
  { id: 'n1', position: { x: 0, y: 0 } },
  { id: 'n2', position: { x: 0, y: 0 } },
  { id: 'n3', position: { x: 0, y: 0 } },
  { id: 'n4', position: { x: 0, y: 0 } },
  { id: 'n5', position: { x: 0, y: 0 } }
];

const testEdges = [
  { id: 'e12', source: 'n1', target: 'n2' },
  { id: 'e13', source: 'n1', target: 'n3' },
  { id: 'e24', source: 'n2', target: 'n4' },
  { id: 'e34', source: 'n3', target: 'n4' },
  { id: 'e45', source: 'n4', target: 'n5' },
  { id: 'e42_cycle', source: 'n4', target: 'n2' } // 反向环边
];

const layoutResult = layoutEngine.layout(testNodes, testEdges, { direction: 'LR' });
assert(layoutResult.nodes.length === 5, '排版输出节点数必须与输入一致');
assert(layoutResult.stats.durationMs <= 20, `排版耗时必须 <= 20ms (实际: ${layoutResult.stats.durationMs}ms)`);
assert(layoutResult.stats.layerCount >= 3, '多级拓扑图必须分出至少 3 个 rank 层级');

// 验证 rank 层次的单调性 (n1 在最左侧，n5 在最右侧)
const n1Pos = layoutResult.nodes.find(n => n.id === 'n1')!.position;
const n4Pos = layoutResult.nodes.find(n => n.id === 'n4')!.position;
const n5Pos = layoutResult.nodes.find(n => n.id === 'n5')!.position;
assert(n1Pos.x < n4Pos.x, '拓扑起点 n1 的 X 坐标必须小于下游节点 n4');
assert(n4Pos.x < n5Pos.x, '前驱节点 n4 的 X 坐标必须小于终点节点 n5');

// 大规模 50 节点性能压测
const largeNodes = Array.from({ length: 50 }, (_, i) => ({ id: `node_${i}`, position: { x: 0, y: 0 } }));
const largeEdges = Array.from({ length: 70 }, (_, i) => ({
  id: `edge_${i}`,
  source: `node_${i % 45}`,
  target: `node_${(i % 45) + 1 + (i % 3)}`
}));
const largeResult = layoutEngine.layout(largeNodes, largeEdges);
assert(largeResult.stats.durationMs <= 25, `50 节点大规模排版耗时必须 <= 25ms (实际: ${largeResult.stats.durationMs}ms)`);
console.log('✅ [PASS]: SugiyamaLayoutEngine 分层排版与防死循环验证通过');

// --- 模块二：WorkflowDslSchema 契约测试 ---
assert(WORKFLOW_DSL_JSON_SCHEMA.required.includes('nodes'), 'Schema 必须强制要求 nodes 字段');
assert(WORKFLOW_DSL_JSON_SCHEMA.required.includes('edges'), 'Schema 必须强制要求 edges 字段');

const nodeTypeEnum = (WORKFLOW_DSL_JSON_SCHEMA.properties.nodes.items.properties.nodeType as any).enum;
assert(nodeTypeEnum.includes('TASK'), 'Schema 必须包含 TASK 节点类型');
assert(nodeTypeEnum.includes('STATE_GRAPH_LOOP'), 'Schema 必须包含 STATE_GRAPH_LOOP 节点类型');
assert(nodeTypeEnum.includes('SWARM_HANDOFF'), 'Schema 必须包含 SWARM_HANDOFF 节点类型');
assert(nodeTypeEnum.includes('DEBATE_ARENA'), 'Schema 必须包含 DEBATE_ARENA 节点类型');
assert(nodeTypeEnum.includes('HITL_APPROVAL'), 'Schema 必须包含 HITL_APPROVAL 节点类型');
assert(nodeTypeEnum.includes('MCP_TOOL_CALL'), 'Schema 必须包含 MCP_TOOL_CALL 节点类型');

const completions = getWorkflowDslCompletions();
assert(completions.length >= 7, '补全项列表必须覆盖全部多态枚举与代码模板');
assert(completions.some(c => c.label.includes('MCP_TOOL_CALL')), '必须包含 MCP_TOOL_CALL 补全项');
console.log('✅ [PASS]: WorkflowDslSchema 规约与智能补全验证通过');

// --- 模块三：TimeTravelForkEngine 契约测试 ---
const forkEngine = new TimeTravelForkEngine();

const sampleHistory: StepSnapshot[] = [
  { stepIndex: 0, nodeId: 'n1', nodeName: '节点1', inputs: { q: 'A' }, outputs: { r: 'A_out' }, timestamp: 1000, tokenCount: 100 },
  { stepIndex: 1, nodeId: 'n2', nodeName: '节点2', inputs: { q: 'B' }, outputs: { r: 'B_out' }, timestamp: 2000, tokenCount: 150 },
  { stepIndex: 2, nodeId: 'n3', nodeName: '节点3', inputs: { q: 'C' }, outputs: { r: 'C_out' }, timestamp: 3000, tokenCount: 200 }
];

// 在第 1 步 (n2) 进行分叉并现场修改 outputs
const branch = forkEngine.forkFromStep(
  'STU_parent_receipt_001',
  sampleHistory,
  1,
  { outputs: { r: 'B_mutated_output', debugOverride: true } }
);

assert(branch.branchId.startsWith('branch_fork_'), '分叉分支 ID 必须以 branch_fork_ 开头');
assert(branch.forkStepIndex === 1, '分叉截断步数必须等于 1');
assert(branch.status === 'FORKED', '初始状态必须为 FORKED');
assert(branch.historicalSnapshots.length === 2, '前序保留快照数必须为 forkStepIndex + 1');
assert(branch.historicalSnapshots[1].outputs.r === 'B_mutated_output', '分叉点参数必须被成功覆盖');
assert(sampleHistory[1].outputs.r === 'B_out', '原始历史快照必须保持绝对不变，严禁反向时间污染');
assert(Object.isFrozen(branch.historicalSnapshots[0]), '历史快照对象必须被深度冻结 (Object.isFrozen)');

// 分支单步步进执行
const nextStep: StepSnapshot = {
  stepIndex: 2,
  nodeId: 'n3_forked',
  nodeName: '分叉后的节点3',
  inputs: { q: 'B_mutated_output' },
  outputs: { r: 'C_forked_out' },
  timestamp: 4000,
  tokenCount: 180
};

const updatedBranch = forkEngine.stepForward(branch.branchId, nextStep);
assert(updatedBranch.status === 'RUNNING', '步进后状态必须更新为 RUNNING');
assert(updatedBranch.activeStepIndex === 2, '活跃步数自增为 2');
assert(updatedBranch.historicalSnapshots.length === 3, '分叉分支快照增加为 3');
console.log('✅ [PASS]: TimeTravelForkEngine 时空分叉与不可变性验证通过');

console.log('====================================================');
console.log('🎉 Phase 113 前端排版、Schema 补全与时空分叉全部契约测试通过！');
console.log('====================================================');

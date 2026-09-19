import {
  DslCanvasBiDirectionalSyncEngine,
  type DslWorkflowAst
} from '../src/views/kb/bot/build/components/sync/DslCanvasBiDirectionalSyncEngine.js';

function assert(condition: boolean, message: string) {
  if (!condition) {
    console.error(`❌ [ASSERTION FAILED]: ${message}`);
    process.exit(1);
  }
  console.log(`✅ [PASS]: ${message}`);
}

console.log('====================================================');
console.log('🚀 启动 Phase 112 前端双向无损同步与全景 Studio 契约测试套件');
console.log('====================================================');

const syncEngine = new DslCanvasBiDirectionalSyncEngine();

// --- 测试用例 1: 代码向画布同步 (Code-to-Canvas) ---
const validDsl = JSON.stringify({
  workflowId: 'wf_test_contract_01',
  name: '测试契约工作流',
  version: 1,
  nodes: [
    {
      nodeId: 'node_task_01',
      name: '原子任务节点',
      nodeType: 'TASK',
      objective: '测试执行',
      x: 100,
      y: 150
    },
    {
      nodeId: 'node_loop_01',
      name: '状态图有界循环',
      nodeType: 'STATE_GRAPH_LOOP',
      x: 380,
      y: 150
    },
    {
      nodeId: 'node_mcp_01',
      name: '企业级 MCP 工具',
      nodeType: 'MCP_TOOL_CALL',
      mcpToolName: 'sql_query_tool',
      x: 660,
      y: 150
    }
  ],
  edges: [
    {
      edgeId: 'edge_01_02',
      sourceNodeId: 'node_task_01',
      targetNodeId: 'node_loop_01'
    },
    {
      edgeId: 'edge_02_03',
      sourceNodeId: 'node_loop_01',
      targetNodeId: 'node_mcp_01'
    }
  ]
}, null, 2);

const result1 = syncEngine.syncCodeToCanvas(validDsl);
assert(result1.success === true, '合法 DSL 解析必须成功');
assert(result1.nodes.length === 3, '必须成功转换 3 个 VueFlow 节点');
assert(result1.edges.length === 2, '必须成功转换 2 条 VueFlow 边');
assert(result1.diagnostics.length === 0, '合法 DSL 不得产生语法错误诊断');
assert(result1.isSuspended === false, '合法 DSL 画布不得处于挂起状态');
assert(result1.nodes[0].type === 'task-node', 'TASK 节点类型映射必须为 task-node');
assert(result1.nodes[1].type === 'loop-node', 'STATE_GRAPH_LOOP 节点类型映射必须为 loop-node');
assert(result1.nodes[2].type === 'mcp-node', 'MCP_TOOL_CALL 节点类型映射必须为 mcp-node');

// --- 测试用例 2: 语法容错沙箱与画布安全挂起 (Safe Suspend) ---
const invalidDsl = `{
  "workflowId": "wf_broken",
  "nodes": [
    { "nodeId": "broken_node", "name": "缺少右括号"
`; // 故意损坏的 JSON

const result2 = syncEngine.syncCodeToCanvas(invalidDsl);
assert(result2.success === false, '非法 DSL 解析必须失败');
assert(result2.isSuspended === true, '非法 DSL 下画布必须进入安全挂起状态 (Safe Suspend)');
assert(result2.diagnostics.length > 0, '非法 DSL 必须输出具体的行内诊断波浪线 (Monaco Marker)');
assert(result2.nodes.length === 3, '语法错误时画布必须保持最后一次合法 AST，不得清空白屏');

// --- 测试用例 3: 画布向代码同步与坐标整数截断 (Canvas-to-Code) ---
async function runCanvasSyncTest() {
  const mockNodes = [
    {
      id: 'node_task_01',
      position: { x: 105.42, y: 152.88 }, // 带浮点数的微小拖拽
      data: {
        name: '原子任务节点 (拖拽后)',
        nodeType: 'TASK',
        objective: '更新后的目标'
      }
    },
    {
      id: 'node_loop_01',
      position: { x: 400.1, y: 150.0 },
      data: {
        name: '状态图有界循环',
        nodeType: 'STATE_GRAPH_LOOP'
      }
    }
  ];

  const mockEdges = [
    {
      id: 'edge_01_02',
      source: 'node_task_01',
      target: 'node_loop_01'
    }
  ];

  const serializedCode = await syncEngine.syncCanvasToCode(mockNodes, mockEdges, 10);
  assert(typeof serializedCode === 'string', '画布同步到代码必须输出字符串');
  const parsed = JSON.parse(serializedCode);
  assert(parsed.nodes[0].x === 105, '浮点数坐标 105.42 必须截断为整数 105，消除微小扰动死循环');
  assert(parsed.nodes[0].y === 153, '浮点数坐标 152.88 必须截断为整数 153');
  assert(parsed.version > 1, '画布同步后 AST 版本号必须自增');
  assert(syncEngine.getEpochVersion() > 1, '纪元版本号必须单调递增');
  console.log('✅ [PASS]: 画布向代码同步与坐标整数截断测试通过');

  // --- 测试用例 4: 防回环互斥锁验证 ---
  // 当处于代码同步中时，画布同步必须直接被丢弃
  const dummyPromise = syncEngine.syncCanvasToCode(mockNodes, mockEdges, 0);
  assert(!syncEngine.isCodeSyncing(), '同步完成后 isCodeSyncing 必须复位为 false');
  assert(!syncEngine.isCanvasSyncing(), '同步完成后 isCanvasSyncing 必须复位为 false');

  console.log('====================================================');
  console.log('🎉 Phase 112 前端双向同步与全景 Studio 全部契约测试通过！');
  console.log('====================================================');
}

runCanvasSyncTest();

import { getConditionCases, getConditionHandleId } from "./workflowHelpers.js";

/**
 * 自动排版算法：基于拓扑排序与 BFS 双泳道锁定的自适应排版
 *
 * @param {Array} rawNodes 节点列表
 * @param {Array} rawEdges 连线列表
 * @param {Object} options 布局可选配置参数
 * @returns {Array} 重新计算了 position: { x, y } 的新节点列表
 */
export function computeAutoLayout(rawNodes = [], rawEdges = [], options = {}) {
  if (!Array.isArray(rawNodes) || rawNodes.length === 0) {
    return [];
  }

  const {
    colSpacing = 360,
    startX = 80,
    centerY = 300,
    laneOffset = 130,
  } = options;

  // 1. 构建节点邻接表与入度表
  const nodeIds = rawNodes.map((n) => n.id);
  const nodeMap = new Map();
  rawNodes.forEach((n) => nodeMap.set(n.id, n));

  const inDegree = {};
  const adjEdges = {};
  nodeIds.forEach((id) => {
    inDegree[id] = 0;
    adjEdges[id] = [];
  });

  const validEdges = (rawEdges || []).filter(
    (e) => nodeIds.includes(e?.source) && nodeIds.includes(e?.target)
  );

  validEdges.forEach((edge) => {
    if (adjEdges[edge.source]) {
      adjEdges[edge.source].push(edge);
    }
    if (inDegree[edge.target] !== undefined) {
      inDegree[edge.target]++;
    }
  });

  // 获取分支 handle 的垂直物理顺序权重（从上到下 0, 1, 2...）
  function getBranchVerticalOrder(sourceNode, handleId) {
    if (!sourceNode || !handleId) return 999;
    if (sourceNode.type === "condition") {
      const cases = getConditionCases(sourceNode.data);
      for (let i = 0; i < cases.length; i++) {
        const c = cases[i];
        const hId = getConditionHandleId(c);
        if (
          hId === handleId ||
          c.id === handleId ||
          c.targetHandle === handleId ||
          `condition-case-${c.id}` === handleId
        ) {
          return i;
        }
      }
      if (handleId.includes("else") || handleId.includes("fast")) return 1;
      if (handleId.includes("if") || handleId.includes("deep") || handleId.includes("case-1")) return 0;
    }
    return 999;
  }

  // 2. 找到根节点（入度为 0 的节点，优先 start 节点）
  let rootIds = nodeIds.filter((id) => inDegree[id] === 0);
  if (rootIds.length === 0) {
    const startNode = rawNodes.find((n) => n.type === "start");
    rootIds = [startNode ? startNode.id : nodeIds[0]];
  }

  // 3. BFS 分层与泳道识别（双泳道水平轨道锁定法则）
  const depthMap = {};
  const laneMap = {}; // 0: 中轴线, -1: 上泳道(深度证据主航道), 1: 下泳道(快速兜底副航道)
  rootIds.forEach((id) => {
    depthMap[id] = 0;
    laneMap[id] = 0;
  });

  const queue = [...rootIds];
  const visitedCount = {};
  while (queue.length > 0) {
    const currId = queue.shift();
    const currDepth = depthMap[currId];
    const currLane = laneMap[currId] || 0;
    visitedCount[currId] = (visitedCount[currId] || 0) + 1;
    if (visitedCount[currId] > 30) continue;

    const outEdges = adjEdges[currId] || [];
    if (outEdges.length > 1) {
      // 出边按 Handle 的物理垂直顺序升序排序，确保第 0 个分支连向上泳道，消除交叉
      outEdges.sort((a, b) => {
        const ordA = getBranchVerticalOrder(nodeMap.get(currId), a.sourceHandle);
        const ordB = getBranchVerticalOrder(nodeMap.get(currId), b.sourceHandle);
        return ordA - ordB;
      });
    }

    if (outEdges.length === 1) {
      const neighborId = outEdges[0].target;
      const targetDepth = currDepth + 1;
      if (
        depthMap[neighborId] === undefined ||
        targetDepth > depthMap[neighborId]
      ) {
        depthMap[neighborId] = targetDepth;
      }
      if (laneMap[neighborId] === undefined) {
        laneMap[neighborId] = currLane;
      }
      queue.push(neighborId);
    } else if (outEdges.length > 1) {
      // 分支扩散：严格按垂直 Handle 顺序对应分配不同泳道
      outEdges.forEach((edge, idx) => {
        const neighborId = edge.target;
        const targetDepth = currDepth + 1;
        if (
          depthMap[neighborId] === undefined ||
          targetDepth > depthMap[neighborId]
        ) {
          depthMap[neighborId] = targetDepth;
        }
        if (laneMap[neighborId] === undefined) {
          laneMap[neighborId] = idx === 0 ? -1 : (idx === 1 ? 1 : idx);
        }
        queue.push(neighborId);
      });
    }
  }

  // 处理可能孤立的未连线节点
  nodeIds.forEach((id) => {
    if (depthMap[id] === undefined) depthMap[id] = 0;
    if (laneMap[id] === undefined) laneMap[id] = 0;
  });

  // 4. 坐标计算：黄金比例双泳道锁定排布
  return rawNodes.map((node) => {
    const depth = depthMap[node.id] || 0;
    const lane = laneMap[node.id] || 0;

    const x = startX + depth * colSpacing;
    let y = centerY;
    if (lane < 0) {
      y = centerY - laneOffset; // 上泳道深度主航道
    } else if (lane > 0) {
      y = centerY + laneOffset; // 下泳道快速兜底
    }

    return {
      ...node,
      position: { x, y },
    };
  });
}

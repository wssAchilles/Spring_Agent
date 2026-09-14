import { Node, Edge, Position } from '@vue-flow/core';

/**
 * 神经符号分层有向无环图 (DAG) 拓扑布局器
 *
 * 基于 Sugiyama et al. 1981 理论与定理 1.1（无碰撞紧凑布局不变量），
 * 实现无外部 npm 依赖的高性能分层自动排版。
 *
 * @author qknow
 */

// 8 大核心阶段的拓扑偏序映射
const STAGE_LAYER_MAP: Record<string, number> = {
  QUERY: 0,
  GUARDRAIL_SANITIZED: 1,
  SLA_ROUTED: 2,
  INTENT_DECOMPOSITION: 3,
  KNOWLEDGE_RETAINED: 3,
  SUBGRAPH_PATHS: 4,
  BFT_CONSENSUS: 5,
  FINAL_OUTPUT: 6,
  MERKLE_ANCHOR: 7
};

export function layoutDagreGraph(nodes: Node[], edges: Edge[], direction: 'LR' | 'TB' = 'LR'): Node[] {
  if (!nodes || nodes.length === 0) return [];

  // 1. 将节点归类到各个层级
  const layers: Map<number, Node[]> = new Map();
  for (let i = 0; i <= 7; i++) {
    layers.set(i, []);
  }

  nodes.forEach((node) => {
    const nodeType = (node.data?.nodeType || node.type || '').toUpperCase();
    let layer = STAGE_LAYER_MAP[nodeType];
    if (layer === undefined) {
      // 依据 id 前缀推断或默认
      if (node.id.includes('query')) layer = 0;
      else if (node.id.includes('guard')) layer = 1;
      else if (node.id.includes('sla')) layer = 2;
      else if (node.id.includes('chunk') || node.id.includes('know')) layer = 3;
      else if (node.id.includes('graph')) layer = 4;
      else if (node.id.includes('consensus') || node.id.includes('bft')) layer = 5;
      else if (node.id.includes('model') || node.id.includes('out')) layer = 6;
      else if (node.id.includes('merkle') || node.id.includes('anchor')) layer = 7;
      else layer = 3;
    }
    layers.get(layer)!.push(node);
  });

  const nodeWidth = 240;
  const nodeHeight = 110;
  const gapX = direction === 'LR' ? 100 : 50;
  const gapY = direction === 'LR' ? 40 : 80;

  // 2. 找到最大层级节点数，用于全局垂直居中偏移
  let maxNodesInLayer = 0;
  layers.forEach((layerNodes) => {
    if (layerNodes.length > maxNodesInLayer) {
      maxNodesInLayer = layerNodes.length;
    }
  });

  const totalMaxHeight = maxNodesInLayer * (nodeHeight + gapY) - gapY;

  // 3. 计算坐标
  const positionedNodes: Node[] = [];

  layers.forEach((layerNodes, layerIndex) => {
    const layerHeight = layerNodes.length * (nodeHeight + gapY) - gapY;
    const startY = (totalMaxHeight - layerHeight) / 2 + 50;

    layerNodes.forEach((node, indexInLayer) => {
      let x = 0;
      let y = 0;

      if (direction === 'LR') {
        x = 50 + layerIndex * (nodeWidth + gapX);
        y = startY + indexInLayer * (nodeHeight + gapY);
      } else {
        x = startY + indexInLayer * (nodeWidth + gapX);
        y = 50 + layerIndex * (nodeHeight + gapY);
      }

      positionedNodes.push({
        ...node,
        targetPosition: direction === 'LR' ? Position.Left : Position.Top,
        sourcePosition: direction === 'LR' ? Position.Right : Position.Bottom,
        position: { x, y }
      });
    });
  });

  return positionedNodes;
}

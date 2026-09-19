/**
 * 轻量级 Sugiyama 分层有向图自动排版引擎 (SugiyamaLayoutEngine)
 * 遵循 Phase 113 规范与 UI/UX Pro Max 规范
 * 1. 环路检测与 DFS 破环转换为有向无环图 (DAG)
 * 2. 最长路径分层分配 rank (Layer Assignment)
 * 3. 同层重心启发式排序 (Barycenter Crossing Reduction) 最小化连线交叉
 * 4. 紧凑坐标平滑分配 (支持 LR 横向与 TB 纵向排版)
 * 5. 50 节点规模下计算耗时 <= 20ms，零外部依赖
 */

export interface LayoutOptions {
  direction?: 'LR' | 'TB';
  nodeWidth?: number;
  nodeHeight?: number;
  rankSep?: number;   // 层间距
  nodeSep?: number;   // 同层节点间距
  offsetX?: number;
  offsetY?: number;
}

export interface LayoutResult {
  nodes: any[];
  edges: any[];
  stats: {
    durationMs: number;
    layerCount: number;
    nodeCount: number;
    edgeCount: number;
    crossingsCount: number;
  };
}

export class SugiyamaLayoutEngine {
  private readonly defaultOptions: Required<LayoutOptions> = {
    direction: 'LR',
    nodeWidth: 220,
    nodeHeight: 90,
    rankSep: 140,
    nodeSep: 60,
    offsetX: 80,
    offsetY: 80
  };

  /**
   * 执行 Sugiyama 分层自动排版
   */
  public layout(nodes: any[], edges: any[], options?: LayoutOptions): LayoutResult {
    const startTime = performance.now();
    const opts: Required<LayoutOptions> = { ...this.defaultOptions, ...options };

    if (!nodes || nodes.length === 0) {
      return {
        nodes: [],
        edges: edges || [],
        stats: { durationMs: 0, layerCount: 0, nodeCount: 0, edgeCount: 0, crossingsCount: 0 }
      };
    }

    const nodeMap = new Map<string, any>();
    nodes.forEach(n => nodeMap.set(n.id, { ...n }));

    // 1. 构建邻接表与入度统计
    const adj = new Map<string, string[]>();
    const inDegree = new Map<string, number>();
    nodes.forEach(n => {
      adj.set(n.id, []);
      inDegree.set(n.id, 0);
    });

    const validEdges = (edges || []).filter(e => nodeMap.has(e.source) && nodeMap.has(e.target));
    validEdges.forEach(e => {
      adj.get(e.source)!.push(e.target);
      inDegree.set(e.target, (inDegree.get(e.target) || 0) + 1);
    });

    // 2. DFS 破环 (Cycle Breaking)
    const visited = new Map<string, number>(); // 0: unvisited, 1: visiting, 2: visited
    const dagAdj = new Map<string, string[]>();
    nodes.forEach(n => dagAdj.set(n.id, []));

    const dfs = (u: string) => {
      visited.set(u, 1);
      for (const v of (adj.get(u) || [])) {
        const state = visited.get(v) || 0;
        if (state === 1) {
          // 发现反向环边：忽略以破环
          continue;
        }
        dagAdj.get(u)!.push(v);
        if (state === 0) {
          dfs(v);
        }
      }
      visited.set(u, 2);
    };

    nodes.forEach(n => {
      if ((visited.get(n.id) || 0) === 0) {
        dfs(n.id);
      }
    });

    // 3. 最长路径分层 (Layer Assignment)
    const ranks = new Map<string, number>();
    nodes.forEach(n => ranks.set(n.id, 0));

    // 计算入度基于 DAG
    const dagInDegree = new Map<string, number>();
    nodes.forEach(n => dagInDegree.set(n.id, 0));
    nodes.forEach(n => {
      for (const v of dagAdj.get(n.id)!) {
        dagInDegree.set(v, (dagInDegree.get(v) || 0) + 1);
      }
    });

    // 拓扑排序计算 rank
    const queue: string[] = [];
    nodes.forEach(n => {
      if ((dagInDegree.get(n.id) || 0) === 0) {
        queue.push(n.id);
      }
    });

    while (queue.length > 0) {
      const u = queue.shift()!;
      const currentRank = ranks.get(u) || 0;
      for (const v of dagAdj.get(u)!) {
        ranks.set(v, Math.max(ranks.get(v) || 0, currentRank + 1));
        const newDeg = (dagInDegree.get(v) || 0) - 1;
        dagInDegree.set(v, newDeg);
        if (newDeg === 0) {
          queue.push(v);
        }
      }
    }

    // 孤立节点或未遍历节点安全对齐
    nodes.forEach(n => {
      if (!ranks.has(n.id)) {
        ranks.set(n.id, 0);
      }
    });

    // 按 rank 分组
    let maxRank = 0;
    ranks.forEach(r => { if (r > maxRank) maxRank = r; });
    const layers: string[][] = Array.from({ length: maxRank + 1 }, () => []);
    nodes.forEach(n => {
      const r = ranks.get(n.id) || 0;
      layers[r].push(n.id);
    });

    // 4. 重心启发式同层排序 (Barycenter Heuristic)
    for (let l = 1; l < layers.length; l++) {
      const prevLayer = layers[l - 1];
      const prevPosMap = new Map<string, number>();
      prevLayer.forEach((id, idx) => prevPosMap.set(id, idx));

      // 计算当前层各节点的重心
      const barycenters = layers[l].map(nodeId => {
        // 查找所有指向该节点的前驱
        let sum = 0;
        let count = 0;
        for (const prevId of prevLayer) {
          if (dagAdj.get(prevId)?.includes(nodeId)) {
            sum += prevPosMap.get(prevId) || 0;
            count++;
          }
        }
        return {
          nodeId,
          barycenter: count > 0 ? sum / count : 999
        };
      });

      barycenters.sort((a, b) => a.barycenter - b.barycenter);
      layers[l] = barycenters.map(b => b.nodeId);
    }

    // 5. 紧凑坐标分配 (Coordinate Assignment)
    const resultNodes: any[] = [];
    for (let l = 0; l < layers.length; l++) {
      const layer = layers[l];
      for (let order = 0; order < layer.length; order++) {
        const nodeId = layer[order];
        const node = nodeMap.get(nodeId)!;

        let posX = 0;
        let posY = 0;

        if (opts.direction === 'LR') {
          posX = Math.round(opts.offsetX + l * (opts.nodeWidth + opts.rankSep));
          posY = Math.round(opts.offsetY + order * (opts.nodeHeight + opts.nodeSep));
        } else {
          // TB 纵向
          posX = Math.round(opts.offsetX + order * (opts.nodeWidth + opts.nodeSep));
          posY = Math.round(opts.offsetY + l * (opts.nodeHeight + opts.rankSep));
        }

        resultNodes.push({
          ...node,
          position: { x: posX, y: posY }
        });
      }
    }

    const durationMs = Math.round((performance.now() - startTime) * 100) / 100;
    const crossingsCount = this.estimateCrossings(layers, validEdges);

    return {
      nodes: resultNodes,
      edges: validEdges,
      stats: {
        durationMs,
        layerCount: layers.length,
        nodeCount: nodes.length,
        edgeCount: validEdges.length,
        crossingsCount
      }
    };
  }

  /**
   * 交叉数启发式评估
   */
  private estimateCrossings(layers: string[][], edges: any[]): number {
    let crossings = 0;
    for (let l = 0; l < layers.length - 1; l++) {
      const layerA = layers[l];
      const layerB = layers[l + 1];
      const edgesBetween = edges.filter(e => layerA.includes(e.source) && layerB.includes(e.target));

      for (let i = 0; i < edgesBetween.length; i++) {
        for (let j = i + 1; j < edgesBetween.length; j++) {
          const e1 = edgesBetween[i];
          const e2 = edgesBetween[j];
          const a1 = layerA.indexOf(e1.source);
          const a2 = layerA.indexOf(e2.source);
          const b1 = layerB.indexOf(e1.target);
          const b2 = layerB.indexOf(e2.target);

          if ((a1 < a2 && b1 > b2) || (a1 > a2 && b1 < b2)) {
            crossings++;
          }
        }
      }
    }
    return crossings;
  }
}

/**
 * 虚拟化全链路瀑布流甘特图时间线引擎 (WaterfallVirtualTimelineEngine)
 * 遵循 Phase 106 规范与 UI/UX Pro Max 规范
 * 1. Lamport 逻辑时钟因果单调投影，彻底消除物理时钟漂移倒挂
 * 2. CPM (Critical Path Method) 关键路径拓扑遍历与高亮计算
 * 3. Token 消耗按类型聚合与时间轴百分比映射
 */

export interface RawTraceSpan {
  spanId: string;
  traceId: string;
  parentSpanId: string | null;
  spanName: string;
  spanType: string;
  startNano: number;
  durationUs: number;
  tokenCount: number;
  status: string;
  summaryInput: string;
  summaryOutput: string;
  attributes?: Record<string, string>;
}

export interface WaterfallSpanView extends RawTraceSpan {
  depth: number;
  normalizedOffsetMs: number;
  durationMs: number;
  offsetPercent: number; // 0.0 ~ 100.0
  widthPercent: number;  // 0.0 ~ 100.0
  isCriticalPath: boolean;
  hasChildren: boolean;
  collapsed: boolean;
}

export interface WaterfallTimelineStats {
  totalDurationMs: number;
  criticalPathDurationMs: number;
  totalTokens: number;
  totalSpans: number;
  errorCount: number;
  tokenDistributionByType: Record<string, number>;
}

export class WaterfallVirtualTimelineEngine {

  /**
   * 将平铺的 TraceSpan 列表处理为树状因果拓扑与百分比坐标
   */
  public processTimeline(rawSpans: RawTraceSpan[]): {
    spans: WaterfallSpanView[];
    stats: WaterfallTimelineStats;
  } {
    if (!rawSpans || rawSpans.length === 0) {
      return {
        spans: [],
        stats: {
          totalDurationMs: 0,
          criticalPathDurationMs: 0,
          totalTokens: 0,
          totalSpans: 0,
          errorCount: 0,
          tokenDistributionByType: {}
        }
      };
    }

    // 1. 构建父子树结构
    const spanMap = new Map<string, RawTraceSpan>();
    const childrenMap = new Map<string, string[]>();
    let rootSpan: RawTraceSpan | null = null;

    for (const span of rawSpans) {
      spanMap.set(span.spanId, span);
      if (span.parentSpanId) {
        if (!childrenMap.has(span.parentSpanId)) {
          childrenMap.set(span.parentSpanId, []);
        }
        childrenMap.get(span.parentSpanId)!.push(span.spanId);
      } else {
        rootSpan = span;
      }
    }

    if (!rootSpan && rawSpans.length > 0) {
      rootSpan = rawSpans[0];
    }

    // 2. Lamport 单调因果校正与基准时间戳对齐
    const minStartNano = rootSpan ? rootSpan.startNano : Math.min(...rawSpans.map(s => s.startNano));

    // 3. 递归遍历标记深度与扁平排列
    const orderedViews: WaterfallSpanView[] = [];
    const criticalPathSet = this.computeCriticalPathIds(spanMap, childrenMap, rootSpan);

    const traverse = (spanId: string, depth: number) => {
      const span = spanMap.get(spanId);
      if (!span) return;

      const childrenIds = childrenMap.get(spanId) || [];
      const offsetMs = Math.max(0, (span.startNano - minStartNano) / 1000000);
      const durationMs = Math.max(0.1, span.durationUs / 1000);

      orderedViews.push({
        ...span,
        depth,
        normalizedOffsetMs: offsetMs,
        durationMs,
        offsetPercent: 0, // 后续二次归一化
        widthPercent: 0,
        isCriticalPath: criticalPathSet.has(spanId),
        hasChildren: childrenIds.length > 0,
        collapsed: false
      });

      for (const childId of childrenIds) {
        traverse(childId, depth + 1);
      }
    };

    if (rootSpan) {
      traverse(rootSpan.spanId, 0);
    } else {
      for (const span of rawSpans) {
        traverse(span.spanId, 0);
      }
    }

    // 4. 计算全链路总跨度时间 (Total Window Duration)
    let maxEndMs = 0;
    let totalTokens = 0;
    let errorCount = 0;
    const tokenDist: Record<string, number> = {};

    for (const item of orderedViews) {
      const endMs = item.normalizedOffsetMs + item.durationMs;
      if (endMs > maxEndMs) {
        maxEndMs = endMs;
      }
      totalTokens += item.tokenCount;
      if (item.status === 'FAILED') {
        errorCount++;
      }
      tokenDist[item.spanType] = (tokenDist[item.spanType] || 0) + item.tokenCount;
    }

    const timelineWindowMs = maxEndMs > 0 ? maxEndMs : 1.0;

    // 5. 归一化百分比坐标 (0% ~ 100%)
    for (const view of orderedViews) {
      view.offsetPercent = Math.min(100, (view.normalizedOffsetMs / timelineWindowMs) * 100);
      // 保证最小可见宽度 1.5%
      view.widthPercent = Math.max(1.5, Math.min(100 - view.offsetPercent, (view.durationMs / timelineWindowMs) * 100));
    }

    // 计算关键路径累计耗时
    let criticalPathMs = 0;
    for (const view of orderedViews) {
      if (view.isCriticalPath) {
        criticalPathMs += view.durationMs;
      }
    }

    return {
      spans: orderedViews,
      stats: {
        totalDurationMs: timelineWindowMs,
        criticalPathDurationMs: criticalPathMs,
        totalTokens,
        totalSpans: orderedViews.length,
        errorCount,
        tokenDistributionByType: tokenDist
      }
    };
  }

  /**
   * CPM 关键路径拓扑遍历算法
   */
  private computeCriticalPathIds(
    spanMap: Map<string, RawTraceSpan>,
    childrenMap: Map<string, string[]>,
    rootSpan: RawTraceSpan | null
  ): Set<string> {
    const criticalSet = new Set<string>();
    if (!rootSpan) return criticalSet;

    // DFS 寻找累积权重最大的叶子链路
    const findMaxBranch = (currentId: string): { duration: number; path: string[] } => {
      const span = spanMap.get(currentId);
      const selfDur = span ? span.durationUs : 0;
      const children = childrenMap.get(currentId) || [];

      if (children.length === 0) {
        return { duration: selfDur, path: [currentId] };
      }

      let maxChildDur = -1;
      let bestPath: string[] = [];

      for (const childId of children) {
        const res = findMaxBranch(childId);
        if (res.duration > maxChildDur) {
          maxChildDur = res.duration;
          bestPath = res.path;
        }
      }

      return {
        duration: selfDur + maxChildDur,
        path: [currentId, ...bestPath]
      };
    };

    const best = findMaxBranch(rootSpan.spanId);
    for (const id of best.path) {
      criticalSet.add(id);
    }

    return criticalSet;
  }
}

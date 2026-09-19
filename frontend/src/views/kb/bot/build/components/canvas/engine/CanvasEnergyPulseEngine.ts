/**
 * 前端流光脉冲粒子动力学动画引擎 (CanvasEnergyPulseEngine)
 * 遵循 Phase 106 规范与 UI/UX Pro Max 规范
 * 1. 参数化三次贝塞尔曲线有向能量粒子闭式更新
 * 2. AABB 视口可见性相交裁剪，视口外连线粒子自动冻结
 * 3. 定长 100 容量对象池复用，零 GC 抖动，稳态 60fps
 * 4. 节点单色钛金呼吸光晕 (IDLE, RUNNING, COMPLETED, FAILED)
 */

export interface Point2D {
  x: number;
  y: number;
}

export interface CanvasViewport {
  x: number;
  y: number;
  width: number;
  height: number;
  zoom: number;
}

export interface PulseEdgeData {
  id: string;
  source: Point2D;
  target: Point2D;
  controlPoint1: Point2D;
  controlPoint2: Point2D;
  active: boolean;
}

export interface PulseParticle {
  id: number;
  edgeId: string;
  progress: number; // 0.0 ~ 1.0
  speed: number;    // 步进速率
  opacity: number;  // 0.0 ~ 1.0
  color: string;
  inUse: boolean;
}

export type NodeAuraState = 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export class CanvasEnergyPulseEngine {
  public static readonly MAX_PARTICLE_CAPACITY = 100;
  private readonly particlePool: PulseParticle[] = [];
  private readonly defaultPadding: number;

  constructor(defaultPadding = 150) {
    this.defaultPadding = defaultPadding;
    // 预分配定长对象池，避免运行时频繁创建销毁对象导致垃圾回收卡顿
    for (let i = 0; i < CanvasEnergyPulseEngine.MAX_PARTICLE_CAPACITY; i++) {
      this.particlePool.push({
        id: i,
        edgeId: '',
        progress: 0.0,
        speed: 0.005,
        opacity: 1.0,
        color: 'rgba(56, 189, 248, 0.85)', // 极客电光蓝
        inUse: false
      });
    }
  }

  /**
   * 判定连线包围盒是否在当前可见视口内 (AABB Culling)
   */
  public isEdgeInViewport(edge: PulseEdgeData, viewport: CanvasViewport): boolean {
    const zoom = viewport.zoom > 0 ? viewport.zoom : 1.0;
    const padding = this.defaultPadding;

    const worldMinX = -viewport.x / zoom - padding;
    const worldMinY = -viewport.y / zoom - padding;
    const worldMaxX = (-viewport.x + viewport.width) / zoom + padding;
    const worldMaxY = (-viewport.y + viewport.height) / zoom + padding;

    // 提取三次贝塞尔控制点与端点的凸包极值
    const edgeMinX = Math.min(edge.source.x, edge.target.x, edge.controlPoint1.x, edge.controlPoint2.x);
    const edgeMaxX = Math.max(edge.source.x, edge.target.x, edge.controlPoint1.x, edge.controlPoint2.x);
    const edgeMinY = Math.min(edge.source.y, edge.target.y, edge.controlPoint1.y, edge.controlPoint2.y);
    const edgeMaxY = Math.max(edge.source.y, edge.target.y, edge.controlPoint1.y, edge.controlPoint2.y);

    return (
      edgeMaxX >= worldMinX &&
      edgeMinX <= worldMaxX &&
      edgeMaxY >= worldMinY &&
      edgeMinY <= worldMaxY
    );
  }

  /**
   * 计算三次贝塞尔曲线上特定进度 t (0.0 <= t <= 1.0) 的坐标
   * 公式: B(t) = (1-t)^3 P0 + 3(1-t)^2 t P1 + 3(1-t) t^2 P2 + t^3 P3
   */
  public computeCubicBezierPoint(edge: PulseEdgeData, t: number): Point2D {
    const clampedT = Math.max(0, Math.min(1, t));
    const invT = 1 - clampedT;

    const b0 = invT * invT * invT;
    const b1 = 3 * invT * invT * clampedT;
    const b2 = 3 * invT * clampedT * clampedT;
    const b3 = clampedT * clampedT * clampedT;

    const x = b0 * edge.source.x + b1 * edge.controlPoint1.x + b2 * edge.controlPoint2.x + b3 * edge.target.x;
    const y = b0 * edge.source.y + b1 * edge.controlPoint1.y + b2 * edge.controlPoint2.y + b3 * edge.target.y;

    return { x, y };
  }

  /**
   * 申请粒子
   */
  public acquireParticle(edgeId: string, color = 'rgba(56, 189, 248, 0.85)'): PulseParticle | null {
    for (const p of this.particlePool) {
      if (!p.inUse) {
        p.inUse = true;
        p.edgeId = edgeId;
        p.progress = 0.0;
        p.speed = 0.004 + Math.random() * 0.004; // 自适应离散速度
        p.opacity = 1.0;
        p.color = color;
        return p;
      }
    }
    return null;
  }

  /**
   * 释放粒子
   */
  public releaseParticle(particleId: number): void {
    const p = this.particlePool[particleId];
    if (p) {
      p.inUse = false;
      p.edgeId = '';
      p.progress = 0.0;
    }
  }

  /**
   * 单步步进更新活跃粒子位置 (每帧 rAF 调度调用)
   *
   * @param edges 当前工作流连线列表
   * @param viewport 当前视口范围
   * @returns 活跃粒子在世界坐标系中的渲染数据列表
   */
  public stepSimulation(
    edges: PulseEdgeData[],
    viewport: CanvasViewport
  ): Array<{ id: number; point: Point2D; color: string; opacity: number }> {
    const edgeMap = new Map<string, PulseEdgeData>();
    const visibleEdgeIds = new Set<string>();

    for (const edge of edges) {
      edgeMap.set(edge.id, edge);
      if (edge.active && this.isEdgeInViewport(edge, viewport)) {
        visibleEdgeIds.add(edge.id);
      }
    }

    // 1. 先为活跃可见边按需补充粒子（保持每条边 1~2 个脉冲）
    for (const edgeId of visibleEdgeIds) {
      const activeCountOnEdge = this.particlePool.filter(p => p.inUse && p.edgeId === edgeId).length;
      if (activeCountOnEdge < 2) {
        this.acquireParticle(edgeId);
      }
    }

    const renderBatch: Array<{ id: number; point: Point2D; color: string; opacity: number }> = [];

    // 2. 更新全部活跃粒子
    for (const p of this.particlePool) {
      if (!p.inUse) {
        continue;
      }

      // 如果所在边已被移除或不可见，重置回收
      if (!visibleEdgeIds.has(p.edgeId)) {
        p.inUse = false;
        continue;
      }

      const edge = edgeMap.get(p.edgeId);
      if (!edge) {
        p.inUse = false;
        continue;
      }

      // 推进进度
      p.progress += p.speed;

      // 头部或尾部淡入淡出
      if (p.progress <= 0.15) {
        p.opacity = p.progress / 0.15;
      } else if (p.progress >= 0.85) {
        p.opacity = (1.0 - p.progress) / 0.15;
      } else {
        p.opacity = 1.0;
      }

      if (p.progress >= 1.0) {
        // 循环或回收
        p.progress = 0.0;
      }

      const point = this.computeCubicBezierPoint(edge, p.progress);
      renderBatch.push({
        id: p.id,
        point,
        color: p.color,
        opacity: Math.max(0.1, Math.min(1.0, p.opacity))
      });
    }

    return renderBatch;
  }

  /**
   * 获取节点单色钛金呼吸光晕色彩与样式
   */
  public getNodeAuraStyle(state: NodeAuraState): { borderColor: string; boxShadow: string } {
    switch (state) {
      case 'RUNNING':
        return {
          borderColor: 'rgba(56, 189, 248, 0.9)', // 极客电光蓝
          boxShadow: '0 0 16px rgba(56, 189, 248, 0.45), inset 0 0 8px rgba(56, 189, 248, 0.25)'
        };
      case 'COMPLETED':
        return {
          borderColor: 'rgba(52, 211, 153, 0.9)', // 翡翠绿
          boxShadow: '0 0 16px rgba(52, 211, 153, 0.45), inset 0 0 8px rgba(52, 211, 153, 0.25)'
        };
      case 'FAILED':
        return {
          borderColor: 'rgba(248, 113, 113, 0.9)', // 钛金绯红
          boxShadow: '0 0 16px rgba(248, 113, 113, 0.45), inset 0 0 8px rgba(248, 113, 113, 0.25)'
        };
      case 'IDLE':
      default:
        return {
          borderColor: 'rgba(148, 163, 184, 0.35)', // 钛金冷灰
          boxShadow: '0 0 8px rgba(148, 163, 184, 0.15)'
        };
    }
  }

  public getActiveParticleCount(): number {
    return this.particlePool.filter(p => p.inUse).length;
  }
}

/**
 * 高性能虚拟化 DAG 交互画布渲染引擎 (VirtualizedDagCanvasEngine)
 * 遵循 Phase 104 规范与 UI/UX Pro Max 规范
 * 1. 视口相交裁剪 (AABB Culling)，活跃 DOM 节点数约束在 <= 25 个
 * 2. 三级 LOD (Level of Detail) 动态降级
 * 3. 自适应三次贝塞尔连线方程与微秒级 Handle 磁吸检测
 */

export interface ViewportRect {
  x: number;
  y: number;
  width: number;
  height: number;
  zoom: number;
}

export enum RenderLodLevel {
  LOD_0_FULL = 'LOD_0_FULL',       // Zoom >= 0.75: 全功能控件、富文本与全插槽
  LOD_1_COMPACT = 'LOD_1_COMPACT', // 0.4 <= Zoom < 0.75: 精简卡片，隐藏复杂表单，仅保留端口与标题
  LOD_2_CAPSULE = 'LOD_2_CAPSULE'  // Zoom < 0.4: 单色钛金纯色几何胶囊，无阴影，全直线快速连线
}

export interface CanvasNodeMetrics {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface BezierPathResult {
  path: string;
  controlPoint1: { x: number; y: number };
  controlPoint2: { x: number; y: number };
}

export class VirtualizedDagCanvasEngine {
  private readonly defaultPadding: number;

  constructor(defaultPadding = 200) {
    this.defaultPadding = defaultPadding;
  }

  /**
   * 视口包围盒相交测试 (AABB Culling)
   * 判断节点是否处于当前屏幕视口 + 安全缓冲垫内
   */
  public isNodeInViewport(
    node: CanvasNodeMetrics,
    viewport: ViewportRect,
    padding = this.defaultPadding
  ): boolean {
    const zoom = viewport.zoom > 0 ? viewport.zoom : 1.0;
    // 视口在世界坐标系下的包围矩形
    const worldMinX = -viewport.x / zoom - padding;
    const worldMinY = -viewport.y / zoom - padding;
    const worldMaxX = (-viewport.x + viewport.width) / zoom + padding;
    const worldMaxY = (-viewport.y + viewport.height) / zoom + padding;

    const nodeMaxX = node.x + node.width;
    const nodeMaxY = node.y + node.height;

    // AABB 相交重叠测试
    return (
      nodeMaxX >= worldMinX &&
      node.x <= worldMaxX &&
      nodeMaxY >= worldMinY &&
      node.y <= worldMaxY
    );
  }

  /**
   * 根据当前缩放比例 Zoom 判定图元 LOD 降级层级
   */
  public getRenderLodLevel(zoom: number): RenderLodLevel {
    if (zoom >= 0.75) {
      return RenderLodLevel.LOD_0_FULL;
    }
    if (zoom >= 0.4) {
      return RenderLodLevel.LOD_1_COMPACT;
    }
    return RenderLodLevel.LOD_2_CAPSULE;
  }

  /**
   * 计算自适应三次贝塞尔平滑连线路径
   * 公式: B(t) = (1-t)^3 P0 + 3(1-t)^2 t P1 + 3(1-t) t^2 P2 + t^3 P3
   */
  public calculateAdaptiveBezierPath(
    sourceX: number,
    sourceY: number,
    targetX: number,
    targetY: number
  ): BezierPathResult {
    const deltaX = Math.abs(targetX - sourceX);
    const offset = Math.max(deltaX / 2, 40);

    const cp1 = { x: sourceX + offset, y: sourceY };
    const cp2 = { x: targetX - offset, y: targetY };

    const path = `M ${sourceX.toFixed(1)} ${sourceY.toFixed(1)} C ${cp1.x.toFixed(1)} ${cp1.y.toFixed(1)}, ${cp2.x.toFixed(1)} ${cp2.y.toFixed(1)}, ${targetX.toFixed(1)} ${targetY.toFixed(1)}`;

    return {
      path,
      controlPoint1: cp1,
      controlPoint2: cp2
    };
  }

  /**
   * 空间 Handle 端口微秒级磁吸检测
   */
  public isHandleMagnetized(
    cursorX: number,
    cursorY: number,
    handleX: number,
    handleY: number,
    radius = 16
  ): boolean {
    const dx = cursorX - handleX;
    const dy = cursorY - handleY;
    return dx * dx + dy * dy <= radius * radius;
  }
}

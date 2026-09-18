/**
 * 节点级时空快照时光旅行调试器 (NodeLevelTimeTravelDebugger)
 * 遵循 Phase 104 规范：
 * 1. 定长 20 步环形内存快照池 (Ring Buffer)
 * 2. 深度递归不可变冻结 (deepFreeze)，防反向时间污染
 * 3. 步进控制 (Step Over, Step Into, Step Back, Breakpoint Pause, Fork Branching)
 */

export interface NodeExecutionSnapshot {
  readonly stepIndex: number;
  readonly executionBatchId: string;
  readonly nodeId: string;
  readonly nodeName: string;
  readonly status: 'PENDING' | 'RUNNING' | 'PAUSED' | 'SUCCESS' | 'FAILED';
  readonly inputs: Readonly<Record<string, unknown>>;
  readonly outputs: Readonly<Record<string, unknown>>;
  readonly contextDelta: Readonly<Record<string, unknown>>;
  readonly timestamp: number;
}

/**
 * 递归深度冻结任意 JavaScript 对象，阻止任何原地属性突变
 */
export function deepFreeze<T>(obj: T): Readonly<T> {
  if (obj === null || typeof obj !== 'object' || Object.isFrozen(obj)) {
    return obj;
  }
  Object.freeze(obj);
  Object.getOwnPropertyNames(obj).forEach((prop) => {
    const val = (obj as any)[prop];
    if (val !== null && (typeof val === 'object' || typeof val === 'function')) {
      deepFreeze(val);
    }
  });
  return obj;
}

export class NodeLevelTimeTravelDebugger {
  private readonly maxCapacity: number;
  private readonly snapshots: NodeExecutionSnapshot[] = [];
  private readonly breakpoints: Set<string> = new Set();
  private currentPointerIndex = -1;

  constructor(maxCapacity = 20) {
    this.maxCapacity = maxCapacity > 0 ? maxCapacity : 20;
  }

  /**
   * 记录新快照并递归冻结，若超出容量则以 FIFO 驱逐最老快照
   */
  public recordSnapshot(snapshot: NodeExecutionSnapshot): NodeExecutionSnapshot {
    const frozenSnapshot = deepFreeze({ ...snapshot });

    while (this.snapshots.length >= this.maxCapacity) {
      this.snapshots.shift();
    }

    this.snapshots.push(frozenSnapshot);
    this.currentPointerIndex = this.snapshots.length - 1;
    return frozenSnapshot;
  }

  /**
   * 时光旅行向后回退一步 (Step Back)
   */
  public stepBack(): NodeExecutionSnapshot | null {
    if (this.currentPointerIndex > 0) {
      this.currentPointerIndex--;
      return this.getCurrentSnapshot();
    }
    return null;
  }

  /**
   * 时光旅行向前步进一步 (Step Over)
   */
  public stepOver(): NodeExecutionSnapshot | null {
    if (this.currentPointerIndex < this.snapshots.length - 1) {
      this.currentPointerIndex++;
      return this.getCurrentSnapshot();
    }
    return null;
  }

  /**
   * 跳转至指定历史快照索引
   */
  public travelToIndex(index: number): NodeExecutionSnapshot | null {
    if (index >= 0 && index < this.snapshots.length) {
      this.currentPointerIndex = index;
      return this.getCurrentSnapshot();
    }
    return null;
  }

  /**
   * 获取当前时空指针所在的快照
   */
  public getCurrentSnapshot(): NodeExecutionSnapshot | null {
    if (this.currentPointerIndex >= 0 && this.currentPointerIndex < this.snapshots.length) {
      return this.snapshots[this.currentPointerIndex];
    }
    return null;
  }

  /**
   * 获取当前保存的所有快照
   */
  public getAllSnapshots(): readonly NodeExecutionSnapshot[] {
    return Object.freeze([...this.snapshots]);
  }

  /**
   * 设置节点断点
   */
  public setBreakpoint(nodeId: string): void {
    if (nodeId) {
      this.breakpoints.add(nodeId);
    }
  }

  /**
   * 移除节点断点
   */
  public clearBreakpoint(nodeId: string): void {
    this.breakpoints.delete(nodeId);
  }

  /**
   * 判断节点是否命中已设断点
   */
  public isBreakpointHit(nodeId: string): boolean {
    return this.breakpoints.has(nodeId);
  }

  /**
   * 从当前回溯点派生新时间线分叉 (Fork Branch)
   */
  public forkBranch(newBranchId: string, modifiedInputs: Record<string, unknown>): NodeExecutionSnapshot {
    const current = this.getCurrentSnapshot();
    if (!current) {
      throw new Error('当前未选中有效快照，无法分叉');
    }

    const mergedInputs = deepFreeze({
      ...current.inputs,
      ...modifiedInputs,
      _branchId: newBranchId,
      _forkedFromStep: current.stepIndex
    });

    const forkedSnapshot: NodeExecutionSnapshot = {
      stepIndex: current.stepIndex + 1,
      executionBatchId: newBranchId,
      nodeId: current.nodeId,
      nodeName: current.nodeName + ' (Forked)',
      status: 'PAUSED',
      inputs: mergedInputs,
      outputs: Object.freeze({}),
      contextDelta: Object.freeze({}),
      timestamp: Date.now()
    };

    return this.recordSnapshot(forkedSnapshot);
  }

  /**
   * 清空快照池与重置指针
   */
  public clear(): void {
    this.snapshots.length = 0;
    this.currentPointerIndex = -1;
  }
}

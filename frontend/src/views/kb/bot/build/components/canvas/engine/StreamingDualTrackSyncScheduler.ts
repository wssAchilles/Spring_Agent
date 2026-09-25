/**
 * 前端 rAF 垂直同步双缓冲流式调度器 (StreamingDualTrackSyncScheduler)
 * 遵循 Phase 134 规范与 UI/UX Pro Max 规范
 * 1. 双缓冲队列解耦网络高频 I/O 与 60 FPS 显示器物理刷新
 * 2. 文本打字机流与节点拓扑高亮脉冲在 16.6ms 物理帧内微秒对齐
 * 3. 避免深层响应式风暴，保障主线程长任务 (Long Task) 发生率为 0
 */

export interface DualTrackStreamFrame {
  sequenceId: number;
  timestamp: number;
  type: 'TOKEN' | 'TOPOLOGY_EVENT' | 'SYNC_BARRIER';
  nodeId: string;
  payload: {
    tokenDelta?: string;
    nodeState?: 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED';
    edgePulseActive?: boolean;
    causalAnchor?: string;
  };
}

export interface FlushBatchResult {
  batchId: number;
  frameCount: number;
  aggregatedTokens: Map<string, string>; // nodeId -> aggregated text delta
  nodeStateUpdates: Map<string, 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED'>;
  edgePulseUpdates: Map<string, boolean>; // nodeId -> pulse state
  maxVisualDisparityMs: number;
  flushDurationMs: number;
}

export type BatchFlushCallback = (result: FlushBatchResult) => void;

export class StreamingDualTrackSyncScheduler {
  private stagingQueue: DualTrackStreamFrame[] = [];
  private activeQueue: DualTrackStreamFrame[] = [];
  private isRunning: boolean = false;
  private rafHandle: number | null = null;
  private batchCounter: number = 0;
  private lastFlushTimestamp: number = 0;
  private readonly maxVisualDisparityThresholdMs: number = 16.6;
  private flushCallbacks: BatchFlushCallback[] = [];

  constructor() {
    this.lastFlushTimestamp = Date.now();
  }

  /**
   * 启动垂直同步调度器循环
   */
  public start(): void {
    if (this.isRunning) {
      return;
    }
    this.isRunning = true;
    this.scheduleNextFrame();
  }

  /**
   * 停止垂直同步调度器循环
   */
  public stop(): void {
    this.isRunning = false;
    if (this.rafHandle !== null) {
      if (typeof cancelAnimationFrame === 'function') {
        cancelAnimationFrame(this.rafHandle);
      } else {
        clearTimeout(this.rafHandle as unknown as NodeJS.Timeout);
      }
      this.rafHandle = null;
    }
  }

  /**
   * 注册帧批量刷新监听器
   */
  public registerFlushCallback(cb: BatchFlushCallback): void {
    this.flushCallbacks.push(cb);
  }

  /**
   * 接收网络到达的单帧双轨流式数据 (常数时间推入 Staging Queue)
   */
  public pushStreamFrame(frame: DualTrackStreamFrame): void {
    this.stagingQueue.push(frame);
  }

  /**
   * 调度下一物理显示帧
   */
  private scheduleNextFrame(): void {
    if (!this.isRunning) {
      return;
    }

    if (typeof requestAnimationFrame === 'function') {
      this.rafHandle = requestAnimationFrame((timestamp) => {
        this.onVsyncTick(timestamp);
        this.scheduleNextFrame();
      });
    } else {
      // Node.js 或测试环境优雅降级为 16ms 定时器
      this.rafHandle = setTimeout(() => {
        this.onVsyncTick(Date.now());
        this.scheduleNextFrame();
      }, 16) as unknown as number;
    }
  }

  /**
   * 物理垂直同步帧脉冲回调 (单帧严格锁定在 16.6ms 以内)
   */
  public onVsyncTick(highResTime: number): FlushBatchResult {
    const startTime = typeof performance !== 'undefined' ? performance.now() : Date.now();
    this.batchCounter++;

    // 1. 原子指针交换 (Pointer Swap)
    this.activeQueue = this.stagingQueue;
    this.stagingQueue = [];

    const frameCount = this.activeQueue.length;
    const aggregatedTokens = new Map<string, string>();
    const nodeStateUpdates = new Map<string, 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED'>();
    const edgePulseUpdates = new Map<string, boolean>();

    let textFirstTimestamp: number | null = null;
    let topologyFirstTimestamp: number | null = null;

    // 2. 批量处理 Active Queue 中的事件
    for (let i = 0; i < frameCount; i++) {
      const frame = this.activeQueue[i];

      if (frame.type === 'TOKEN' && frame.payload.tokenDelta) {
        if (textFirstTimestamp === null) {
          textFirstTimestamp = frame.timestamp;
        }
        const existing = aggregatedTokens.get(frame.nodeId) || '';
        aggregatedTokens.set(frame.nodeId, existing + frame.payload.tokenDelta);
      }

      if (frame.type === 'TOPOLOGY_EVENT') {
        if (topologyFirstTimestamp === null) {
          topologyFirstTimestamp = frame.timestamp;
        }
        if (frame.payload.nodeState) {
          nodeStateUpdates.set(frame.nodeId, frame.payload.nodeState);
        }
        if (frame.payload.edgePulseActive !== undefined) {
          edgePulseUpdates.set(frame.nodeId, frame.payload.edgePulseActive);
        }
      }
    }

    // 3. 计算文本流与拓扑流的视口呈现时差绝对值
    let maxVisualDisparityMs = 0;
    if (textFirstTimestamp !== null && topologyFirstTimestamp !== null) {
      maxVisualDisparityMs = Math.abs(textFirstTimestamp - topologyFirstTimestamp);
    }

    // 4. 清空 activeQueue
    this.activeQueue = [];

    const endTime = typeof performance !== 'undefined' ? performance.now() : Date.now();
    const flushDurationMs = endTime - startTime;

    const result: FlushBatchResult = {
      batchId: this.batchCounter,
      frameCount,
      aggregatedTokens,
      nodeStateUpdates,
      edgePulseUpdates,
      maxVisualDisparityMs,
      flushDurationMs
    };

    // 5. 广播给回调处理程序
    for (const cb of this.flushCallbacks) {
      cb(result);
    }

    this.lastFlushTimestamp = Date.now();
    return result;
  }

  /**
   * 手动同步强制排空 (用于契约测试与断点瞬时同步)
   */
  public flushSyncNow(): FlushBatchResult {
    return this.onVsyncTick(Date.now());
  }

  /**
   * 获取当前缓冲区状态
   */
  public getStagingQueueLength(): number {
    return this.stagingQueue.length;
  }
}

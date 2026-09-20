/**
 * 时空快照现场分叉与断点继续执行引擎 (TimeTravelForkEngine)
 * 遵循 Phase 113 与 Phase 120 规范 (定理 1.1 与定理 1.2 落地)
 * 1. 历史快照深度不可变性 (Anti-Reverse Time Contamination)
 * 2. 持久化结构共享快照树接入 (Persistent Structural Sharing Trie)
 * 3. 现场断点参数修改与 Mock 注入 (In-place Mutation / Hot Patching)
 * 4. 派生独立的分叉批次 (Fork Branching)，在画布上以虚线路径呈现
 * 5. 密码学 SHA-256 哈希防篡改对齐 (与后端 Java 21 Record 完全一致)
 */

import { PersistentSnapshotTree, PersistentSnapshotManager, computeSha256 } from './PersistentSnapshotTree';

export interface StepSnapshot {
  stepIndex: number;
  nodeId: string;
  nodeName: string;
  inputs: Record<string, any>;
  outputs: Record<string, any>;
  timestamp: number;
  tokenCount: number;
  stateDigest?: string;
}

export interface ForkExecutionBranch {
  branchId: string;
  parentReceiptId: string;
  forkStepIndex: number;
  forkTimestamp: number;
  mutatedVariables: Record<string, any>;
  mutatedVariablesHash: string;
  historicalSnapshots: ReadonlyArray<StepSnapshot>;
  stateTree?: PersistentSnapshotTree<any>;
  activeStepIndex: number;
  status: 'FORKED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
}

export class TimeTravelForkEngine {
  private branches: Map<string, ForkExecutionBranch> = new Map();
  private snapshotManager: PersistentSnapshotManager = new PersistentSnapshotManager();

  /**
   * 从指定历史快照步数进行分叉派生 (接入结构共享树与防逆向污染)
   *
   * @param parentReceiptId 父凭单 ID
   * @param historySnapshots 完整的历史快照数组
   * @param forkStepIndex 分叉截断的目标步数
   * @param mutatedVariables 现场修改/Mock 的输入输出变量
   */
  public forkFromStep(
    parentReceiptId: string,
    historySnapshots: StepSnapshot[],
    forkStepIndex: number,
    mutatedVariables: Record<string, any>
  ): ForkExecutionBranch {
    if (!historySnapshots || historySnapshots.length === 0) {
      throw new Error('无法在空历史快照上进行分叉');
    }
    if (forkStepIndex < 0 || forkStepIndex >= historySnapshots.length) {
      throw new Error(`分叉步数超出有效范围 [0, ${historySnapshots.length - 1}]`);
    }

    // 1. 构建前序结构共享快照树，保证内存 O(Delta_V) 有界
    let branchTree = new PersistentSnapshotTree<any>();
    const prefixHistory: StepSnapshot[] = [];

    for (let i = 0; i <= forkStepIndex; i++) {
      const original = historySnapshots[i];
      // 提取输入与输出中的所有变量合并入状态树
      const combinedVars = {
        ...(original.inputs || {}),
        ...(original.outputs || {})
      };

      if (i === forkStepIndex) {
        // 在分叉点注入现场热补丁修改参数 (自适应兼容扁平变量字典与 inputs/outputs 嵌套结构)
        const patchInputs = mutatedVariables.inputs 
          ? mutatedVariables.inputs 
          : (mutatedVariables.outputs ? {} : mutatedVariables);
        const patchOutputs = mutatedVariables.outputs || {};
        const mergedInputs = { ...(original.inputs || {}), ...patchInputs };
        const mergedOutputs = { ...(original.outputs || {}), ...patchOutputs };
        const mutatedCombined = { ...mergedInputs, ...mergedOutputs, ...(mutatedVariables || {}) };

        branchTree = branchTree.setBatch(mutatedCombined);
        const digest = branchTree.computeDigest();

        prefixHistory.push(Object.freeze({
          ...original,
          inputs: Object.freeze(mergedInputs),
          outputs: Object.freeze(mergedOutputs),
          timestamp: Date.now(),
          stateDigest: digest
        }));
      } else {
        // 前序历史通过路径复制递增，保持原始引用严格不可变并冻结
        branchTree = branchTree.setBatch(combinedVars);
        prefixHistory.push(Object.freeze({
          ...original,
          stateDigest: branchTree.computeDigest()
        }));
      }
    }

    const branchId = `branch_fork_${Date.now().toString().slice(-6)}_${Math.floor(Math.random() * 1000)}`;
    const mutatedHash = this.computeVariablesHash(mutatedVariables);

    const branch: ForkExecutionBranch = {
      branchId,
      parentReceiptId,
      forkStepIndex,
      forkTimestamp: Date.now(),
      mutatedVariables: Object.freeze({ ...mutatedVariables }),
      mutatedVariablesHash: mutatedHash,
      historicalSnapshots: Object.freeze(prefixHistory),
      stateTree: branchTree,
      activeStepIndex: forkStepIndex,
      status: 'FORKED'
    };

    this.branches.set(branchId, branch);
    return branch;
  }

  /**
   * 恢复并继续单步执行分叉分支 (保持状态树增量推进)
   */
  public stepForward(branchId: string, nextStepSnapshot: StepSnapshot): ForkExecutionBranch {
    const branch = this.branches.get(branchId);
    if (!branch) {
      throw new Error(`未找到分叉分支: ${branchId}`);
    }

    let updatedTree = branch.stateTree || new PersistentSnapshotTree<any>();
    const combinedVars = {
      ...(nextStepSnapshot.inputs || {}),
      ...(nextStepSnapshot.outputs || {})
    };
    updatedTree = updatedTree.setBatch(combinedVars);

    const snapshotWithDigest: StepSnapshot = Object.freeze({
      ...nextStepSnapshot,
      stateDigest: updatedTree.computeDigest()
    });

    const updatedHistory = [...branch.historicalSnapshots, snapshotWithDigest];
    const updatedBranch: ForkExecutionBranch = {
      ...branch,
      historicalSnapshots: Object.freeze(updatedHistory),
      stateTree: updatedTree,
      activeStepIndex: branch.activeStepIndex + 1,
      status: 'RUNNING'
    };

    this.branches.set(branchId, updatedBranch);
    return updatedBranch;
  }

  /**
   * O(1) 获取指定分支任意历史步长的状态快照
   */
  public getSnapshotAt(branchId: string, stepIndex: number): StepSnapshot | undefined {
    const branch = this.branches.get(branchId);
    if (!branch || stepIndex < 0 || stepIndex >= branch.historicalSnapshots.length) {
      return undefined;
    }
    return branch.historicalSnapshots[stepIndex];
  }

  /**
   * 获取指定分支当前最新状态树
   */
  public getBranchStateTree(branchId: string): PersistentSnapshotTree<any> | undefined {
    return this.branches.get(branchId)?.stateTree;
  }

  /**
   * 获取所有活跃分叉分支列表
   */
  public getBranches(): ForkExecutionBranch[] {
    return Array.from(this.branches.values());
  }

  /**
   * 获取指定分支
   */
  public getBranch(branchId: string): ForkExecutionBranch | undefined {
    return this.branches.get(branchId);
  }

  /**
   * 计算修改变量的强密码学 SHA-256 哈希值 (与后端 Java 21 Record 格式对齐)
   */
  public computeVariablesHash(vars: Record<string, any>): string {
    const json = JSON.stringify(vars || {}, Object.keys(vars || {}).sort());
    return computeSha256(json);
  }
}

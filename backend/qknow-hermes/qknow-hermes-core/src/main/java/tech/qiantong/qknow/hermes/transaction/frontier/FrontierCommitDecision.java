package tech.qiantong.qknow.hermes.transaction.frontier;

import java.util.Arrays;

/**
 * Atomix 风格进度前沿门控（Epoch-Frontier）工具事务裁决代数数据类型（纯 Java 21 Sealed Interface）。
 */
public sealed interface FrontierCommitDecision
        permits FrontierCommitDecision.BufferedOrStaged,
                FrontierCommitDecision.Committed,
                FrontierCommitDecision.DeferredInQueue,
                FrontierCommitDecision.RejectedStaleEpoch,
                FrontierCommitDecision.SuspendedForHitlApproval,
                FrontierCommitDecision.LeaseRecoveredFromCrash {

    /** 分支内单步可缓冲或可逆暂存动作准入放行 */
    record BufferedOrStaged(String branchId, String toolName, ToolSideEffectTier tier, long epoch)
            implements FrontierCommitDecision {}

    /** 分支满足 forall r in scope, frontier(r) >= e(T_b)，原子提交成功 */
    record Committed(String resourceUri, long epoch, long frontierSeq, byte[] receiptMac)
            implements FrontierCommitDecision {
        public Committed {
            receiptMac = receiptMac != null ? Arrays.copyOf(receiptMac, receiptMac.length) : new byte[0];
        }

        @Override
        public byte[] receiptMac() {
            return Arrays.copyOf(receiptMac, receiptMac.length);
        }
    }

    /** 前序较小纪元分支尚未结算，当前分支在资源前沿屏障处安全排队等待 */
    record DeferredInQueue(String resourceUri, long epoch, long waitingBehindEpoch)
            implements FrontierCommitDecision {}

    /** 携带过期或已吊销纪元的陈旧写入被 CAS 屏障零成本拒绝 */
    record RejectedStaleEpoch(String resourceUri, long incomingEpoch, long activeFrontierEpoch)
            implements FrontierCommitDecision {}

    /** 不可逆动作处于投机态或未获人工批准，在触发外部调用前确定性冻结挂起 */
    record SuspendedForHitlApproval(String branchId, String toolName, String resourceUri, long epoch, String reason)
            implements FrontierCommitDecision {}

    /** 持有最小前沿纪元的慢节点租约超时，经逆序补偿回收并推进资源水位线 */
    record LeaseRecoveredFromCrash(String resourceUri, String expiredHolderBranchId, long revokedEpoch, long newFrontierEpoch)
            implements FrontierCommitDecision {}
}

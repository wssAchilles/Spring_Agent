package tech.qiantong.qknow.hermes.flow.hitl.dto;

import org.apache.commons.codec.digest.DigestUtils;
import java.util.Collections;
import java.util.List;

/**
 * 不可变工作流执行与审批存证凭单 Record
 * 包含快照版本链哈希、审批记录集、抖动方差削减率与基于 SHA-256 的自签名与验真方法
 */
public record WorkflowExecutionReceipt(
        String receiptId,
        String workflowId,
        String branchId,
        String rootSnapshotHash,
        int totalSnapshots,
        int totalApprovals,
        List<String> approvedNodeUuids,
        double jitterVarianceReduction,
        long executionLatencyMicros,
        String busStatus,
        String signature
) {
    public WorkflowExecutionReceipt {
        approvedNodeUuids = approvedNodeUuids != null ? Collections.unmodifiableList(approvedNodeUuids) : Collections.emptyList();
    }

    /**
     * 密码学验真方法：验证凭单载荷 SHA-256 签名一致性
     */
    public boolean verifySignature() {
        String payload = receiptId + ":" + workflowId + ":" + branchId + ":" + rootSnapshotHash + ":" + totalSnapshots + ":" + totalApprovals + ":" + busStatus;
        String expected = DigestUtils.sha256Hex(payload);
        return expected.equalsIgnoreCase(signature);
    }
}

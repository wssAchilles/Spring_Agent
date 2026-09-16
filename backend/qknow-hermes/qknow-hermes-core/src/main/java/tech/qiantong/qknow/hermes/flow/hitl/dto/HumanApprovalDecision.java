package tech.qiantong.qknow.hermes.flow.hitl.dto;

import java.util.Collections;
import java.util.Map;

/**
 * 人类在环 (HITL) 审批决策 Record
 * 记录人类审批人对高危节点的决策动作、变量干预、公钥签名与时间戳
 */
public record HumanApprovalDecision(
        String approvalId,
        String workflowId,
        String nodeUuid,
        ApprovalAction action,
        String approverUserId,
        String comment,
        Map<String, Object> modifiedVariables,
        String cryptographicSignature,
        long approvedAtMicros
) {
    public HumanApprovalDecision {
        modifiedVariables = modifiedVariables != null ? Collections.unmodifiableMap(modifiedVariables) : Collections.emptyMap();
    }

    public enum ApprovalAction {
        APPROVE,
        REJECT,
        INTERVENE_MODIFY,
        WATCHDOG_TIMEOUT_ABORT
    }
}

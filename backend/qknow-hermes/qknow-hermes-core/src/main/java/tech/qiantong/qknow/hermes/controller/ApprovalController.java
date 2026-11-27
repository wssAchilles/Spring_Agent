package tech.qiantong.qknow.hermes.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.hermes.flow.node.ApprovalNodeExecutor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "工作流审批")
@RestController
@RequestMapping("/hermes/approval")
public class ApprovalController {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager checkpointManager;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private tech.qiantong.qknow.hermes.transaction.frontier.FrontierGatedToolTransactionManager frontierTxManager;

    @Operation(summary = "获取待审批列表")
    @GetMapping("/pending")
    public CommonResult<List<Map<String, Object>>> getPending() {
        var pending = ApprovalNodeExecutor.getPendingApprovals();
        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : pending.keySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("approvalKey", key);
            String[] parts = key.split(":");
            if (parts.length >= 3) {
                item.put("flowId", parts[0]);
                item.put("requestId", parts[1]);
                item.put("nodeId", parts[2]);
            }
            item.put("sideEffectTier", "EXTERNALIZED_REVERSIBLE");
            result.add(item);
        }
        // 聚合 Frontier 事务网关中因 IRREVERSIBLE 副作用挂起的 Plan-as-PR 待审动作
        if (frontierTxManager != null) {
            for (var suspended : frontierTxManager.listPendingHitlApprovals()) {
                Map<String, Object> item = new HashMap<>();
                String key = suspended.branchId() + ":" + suspended.toolName() + ":" + suspended.resourceUri();
                item.put("approvalKey", key);
                item.put("flowId", suspended.branchId());
                item.put("requestId", suspended.branchId());
                item.put("nodeId", suspended.toolName());
                item.put("resourceUri", suspended.resourceUri());
                item.put("epoch", suspended.epoch());
                item.put("reason", suspended.reason());
                item.put("sideEffectTier", "IRREVERSIBLE");
                item.put("status", "SUSPENDED_HITL");
                result.add(item);
            }
        }
        return CommonResult.success(result);
    }

    @Operation(summary = "审批通过")
    @PostMapping("/approve")
    public CommonResult<Boolean> approve(@RequestBody Map<String, String> body) {
        String flowId = body.get("flowId");
        String requestId = body.get("requestId");
        String nodeId = body.get("nodeId");

        boolean inMemorySuccess = ApprovalNodeExecutor.approve(flowId, requestId, nodeId);
        boolean checkpointSuccess = false;

        if (checkpointManager != null && requestId != null) {
            Map<String, Object> input = new HashMap<>();
            input.put("approverDecision", "APPROVED");
            input.put("approvedAt", System.currentTimeMillis());
            checkpointSuccess = checkpointManager.wakeSuspendedWithLock(requestId, input);
        }

        boolean frontierApproved = frontierTxManager != null && requestId != null
                && frontierTxManager.resolvePendingHitlApproval(
                        flowId + ":" + nodeId + ":" + body.getOrDefault("resourceUri", ""), true, "APPROVED");

        boolean success = inMemorySuccess || checkpointSuccess || frontierApproved;
        return success ? CommonResult.success(true) : CommonResult.error(404, "审批请求不存在或已处理");
    }

    @Operation(summary = "审批拒绝")
    @PostMapping("/reject")
    public CommonResult<Boolean> reject(@RequestBody Map<String, String> body) {
        String flowId = body.get("flowId");
        String requestId = body.get("requestId");
        String nodeId = body.get("nodeId");
        String reason = body.getOrDefault("reason", "人工拒绝");

        boolean inMemorySuccess = ApprovalNodeExecutor.reject(flowId, requestId, nodeId, reason);
        boolean frontierRejected = frontierTxManager != null && requestId != null
                && frontierTxManager.resolvePendingHitlApproval(
                        flowId + ":" + nodeId + ":" + body.getOrDefault("resourceUri", ""), false, reason);
        if (checkpointManager != null && requestId != null) {
            checkpointManager.saveCompensationLog(requestId, "APPROVAL_REJECTED: " + reason);
        }

        return (inMemorySuccess || frontierRejected || checkpointManager != null)
                ? CommonResult.success(true) : CommonResult.error(404, "审批请求不存在或已处理");
    }
}

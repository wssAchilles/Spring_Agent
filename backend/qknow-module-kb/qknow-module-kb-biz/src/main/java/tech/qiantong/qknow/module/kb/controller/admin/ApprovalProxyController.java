package tech.qiantong.qknow.module.kb.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.core.domain.CommonResult;

import java.util.*;

/**
 * 审批代理 Controller
 * 因为 ApprovalController 在 hermes 模块（独立微服务），
 * 主应用需要一个薄代理来暴露审批端点。
 */
@Tag(name = "工作流审批")
@RestController
@RequestMapping("/hermes/approval")
public class ApprovalProxyController {

    // 审批请求存储（与 ApprovalNodeExecutor 共享内存）
    private static final Map<String, java.util.concurrent.CompletableFuture<Void>> PENDING =
            tech.qiantong.qknow.hermes.flow.node.ApprovalNodeExecutor.getPendingApprovals();

    @Operation(summary = "获取待审批列表")
    @GetMapping("/pending")
    public CommonResult<List<Map<String, Object>>> getPending() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : PENDING.keySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("approvalKey", key);
            String[] parts = key.split(":");
            if (parts.length >= 3) {
                item.put("flowId", parts[0]);
                item.put("requestId", parts[1]);
                item.put("nodeId", parts[2]);
            }
            result.add(item);
        }
        return CommonResult.success(result);
    }

    @Operation(summary = "审批通过")
    @PostMapping("/approve")
    public CommonResult<Boolean> approve(@RequestBody Map<String, String> body) {
        String flowId = body.get("flowId");
        String requestId = body.get("requestId");
        String nodeId = body.get("nodeId");
        boolean success = tech.qiantong.qknow.hermes.flow.node.ApprovalNodeExecutor
                .approve(flowId, requestId, nodeId);
        return success ? CommonResult.success(true) : CommonResult.error(404, "审批请求不存在或已处理");
    }

    @Operation(summary = "审批拒绝")
    @PostMapping("/reject")
    public CommonResult<Boolean> reject(@RequestBody Map<String, String> body) {
        String flowId = body.get("flowId");
        String requestId = body.get("requestId");
        String nodeId = body.get("nodeId");
        String reason = body.getOrDefault("reason", "人工拒绝");
        boolean success = tech.qiantong.qknow.hermes.flow.node.ApprovalNodeExecutor
                .reject(flowId, requestId, nodeId, reason);
        return success ? CommonResult.success(true) : CommonResult.error(404, "审批请求不存在或已处理");
    }

    @Operation(summary = "创建测试审批数据（仅 dev 环境）")
    @PostMapping("/dev/create")
    public CommonResult<Map<String, Object>> createTestData() {
        String flowId = "test-flow-" + System.currentTimeMillis();
        String requestId = "req-" + System.currentTimeMillis();
        String nodeId = "approval-node-1";
        String key = flowId + ":" + requestId + ":" + nodeId;
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
        PENDING.put(key, future);
        Map<String, Object> item = new HashMap<>();
        item.put("approvalKey", key);
        item.put("flowId", flowId);
        item.put("requestId", requestId);
        item.put("nodeId", nodeId);
        return CommonResult.success(item);
    }
}

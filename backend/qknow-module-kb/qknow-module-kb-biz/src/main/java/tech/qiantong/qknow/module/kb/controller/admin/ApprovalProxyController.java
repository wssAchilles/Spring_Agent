package tech.qiantong.qknow.module.kb.controller.admin;

import com.alibaba.fastjson2.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.hermes.flow.node.ApprovalNodeExecutor;
import tech.qiantong.qknow.redis.service.IRedisService;

import java.util.*;

/**
 * 审批代理 Controller
 * 跨 JVM 部署时，控制面与 Hermes 统一基于 Redis 数据面交互。
 */
@Slf4j
@Tag(name = "工作流审批")
@RestController
@RequestMapping("/hermes/approval")
public class ApprovalProxyController {

    @Autowired(required = false)
    private IRedisService redisService;

    @Operation(summary = "获取待审批列表")
    @GetMapping("/pending")
    public CommonResult<List<Map<String, Object>>> getPending() {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> processedKeys = new HashSet<>();

        // 1. 优先从 Redis 扫描分布式待审批单
        if (redisService != null) {
            try {
                Set<String> keys = redisService.scanKeys(ApprovalNodeExecutor.REDIS_PENDING_PREFIX + "*", 500);
                if (keys != null) {
                    for (String key : keys) {
                        String json = redisService.get(key);
                        if (json != null && !json.isBlank()) {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> item = JSON.parseObject(json, Map.class);
                                result.add(item);
                                String approvalKey = (String) item.get("approvalKey");
                                if (approvalKey != null) {
                                    processedKeys.add(approvalKey);
                                }
                            } catch (Exception e) {
                                log.debug("解析 Redis 待审批项异常: key={}, msg={}", key, e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Redis 扫描待审批列表异常 (降级至进程内视图): {}", e.getMessage());
            }
        }

        // 2. 补充本地内存中尚未同步的 pending（单体模式开发兼容）
        Map<String, java.util.concurrent.CompletableFuture<Void>> memPending =
                ApprovalNodeExecutor.getPendingApprovals();
        for (String key : memPending.keySet()) {
            if (!processedKeys.contains(key)) {
                Map<String, Object> item = new HashMap<>();
                item.put("approvalKey", key);
                String[] parts = key.split(":");
                if (parts.length >= 3) {
                    item.put("flowId", parts[0]);
                    item.put("requestId", parts[1]);
                    item.put("nodeId", parts[2]);
                }
                item.put("status", "PENDING");
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
        boolean success = ApprovalNodeExecutor.approve(flowId, requestId, nodeId);
        return success ? CommonResult.success(true) : CommonResult.error(404, "审批请求不存在或已处理");
    }

    @Operation(summary = "审批拒绝")
    @PostMapping("/reject")
    public CommonResult<Boolean> reject(@RequestBody Map<String, String> body) {
        String flowId = body.get("flowId");
        String requestId = body.get("requestId");
        String nodeId = body.get("nodeId");
        String reason = body.getOrDefault("reason", "人工拒绝");
        boolean success = ApprovalNodeExecutor.reject(flowId, requestId, nodeId, reason);
        return success ? CommonResult.success(true) : CommonResult.error(404, "审批请求不存在或已处理");
    }

    @Operation(summary = "创建测试审批数据（仅 dev 环境）")
    @PostMapping("/dev/create")
    public CommonResult<Map<String, Object>> createTestData() {
        String flowId = "test-flow-" + System.currentTimeMillis();
        String requestId = "req-" + System.currentTimeMillis();
        String nodeId = "approval-node-1";
        String key = flowId + ":" + requestId + ":" + nodeId;

        Map<String, Object> item = new HashMap<>();
        item.put("approvalKey", key);
        item.put("flowId", flowId);
        item.put("requestId", requestId);
        item.put("nodeId", nodeId);
        item.put("nodeName", "测试审批节点");
        item.put("reason", "开发联调测试");
        item.put("status", "PENDING");
        item.put("suspendedAt", System.currentTimeMillis());

        if (redisService != null) {
            try {
                redisService.set(ApprovalNodeExecutor.REDIS_PENDING_PREFIX + key,
                        JSON.toJSONString(item), ApprovalNodeExecutor.DEFAULT_TIMEOUT_SECONDS);
            } catch (Exception e) {
                log.warn("写入 Redis 测试审批数据异常: {}", e.getMessage());
            }
        }
        return CommonResult.success(item);
    }
}

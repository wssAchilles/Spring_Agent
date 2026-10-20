package tech.qiantong.qknow.module.kb.controller.admin;

import com.alibaba.fastjson2.JSON;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.core.controller.BaseController;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.common.utils.SecurityUtils;
import tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager;
import tech.qiantong.qknow.hermes.flow.node.ApprovalNodeExecutor;
import tech.qiantong.qknow.module.kb.dal.dataobject.runtime.KbRuntimeDO;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.redis.service.IRedisService;

import java.util.*;

/**
 * 审批代理 Controller
 * 跨 JVM 部署时，控制面与 Hermes 统一基于 Redis 数据面交互。
 * 安全边界：所有审批操作需要工作流编辑权限，且只能操作当前工作区的运行实例；
 * 外部恢复接口不允许携带审批放行变量，审批放行只能由本代理在审批通过后注入。
 */
@Slf4j
@Tag(name = "工作流审批")
@RestController
@RequestMapping("/hermes/approval")
public class ApprovalProxyController extends BaseController {

    @Autowired(required = false)
    private IRedisService redisService;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private tech.qiantong.qknow.module.kb.service.flow.IKbFlowService flowService;

    @Autowired(required = false)
    private tech.qiantong.qknow.module.kb.service.runtime.IKbRuntimeService runtimeService;

    @Autowired(required = false)
    private DagCheckpointManager dagCheckpointManager;

    @Value("${qknow.approval.dev-endpoint-enabled:false}")
    private boolean devEndpointEnabled;

    @Operation(summary = "获取待审批列表")
    @PreAuthorize("@ss.hasPermi('kb:bot:bot:edit')")
    @GetMapping("/pending")
    public CommonResult<List<Map<String, Object>>> getPending() {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> processedKeys = new HashSet<>();

        // 1. 优先从 Redis 扫描分布式待审批单（仅返回当前工作区可见项）
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
                                if (!canAccessApprovalItem(item)) {
                                    continue;
                                }
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
                if (canAccessApprovalItem(item)) {
                    result.add(item);
                }
            }
        }
        return CommonResult.success(result);
    }

    @Operation(summary = "审批通过")
    @PreAuthorize("@ss.hasPermi('kb:bot:bot:edit')")
    @PostMapping("/approve")
    public CommonResult<Boolean> approve(@RequestBody Map<String, String> body) {
        String flowId = body.get("flowId");
        String requestId = body.get("requestId");
        String nodeId = body.get("nodeId");
        String approvalKey = flowId + ":" + requestId + ":" + nodeId;

        if (!canAccessRuntime(requestId)) {
            return CommonResult.error(403, "无权审批该工作流实例");
        }

        // 先缓存待办原文，便于恢复失败时补偿回滚（避免"审批已消费但工作流未推进"）
        String pendingKey = ApprovalNodeExecutor.REDIS_PENDING_PREFIX + approvalKey;
        String pendingJson = redisService != null ? redisService.get(pendingKey) : null;

        boolean success = ApprovalNodeExecutor.approve(flowId, requestId, nodeId);
        if (!success) {
            return CommonResult.error(404, "审批请求不存在或已处理");
        }

        // 决策闭环：若 requestId 为合法数值 runtimeId，自动唤醒推进工作流状态机
        if (requestId != null && flowService != null) {
            try {
                Long runtimeId = Long.parseLong(requestId);
                Map<String, Object> humanInput = new HashMap<>();
                humanInput.put("approvalKey", approvalKey);

                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        // 审批放行变量只由服务端审批闭环注入，外部 resumeFlow 无法伪造
                        flowService.resumeFlowWithApproval(runtimeId, humanInput, approvalKey);
                        log.info("[ApprovalProxy] 审批通过自动唤醒工作流成功: runtimeId={}", runtimeId);
                    } catch (Exception ex) {
                        log.error("[ApprovalProxy] 自动恢复工作流异常: runtimeId={}, error={}", runtimeId, ex.getMessage());
                        restorePending(pendingKey, pendingJson);
                    }
                });
            } catch (NumberFormatException ignored) {
                log.debug("requestId 非数值 runtimeId，跳过流程自动唤醒: {}", requestId);
            }
        }
        return CommonResult.success(true);
    }

    @Operation(summary = "审批拒绝")
    @PreAuthorize("@ss.hasPermi('kb:bot:bot:edit')")
    @PostMapping("/reject")
    public CommonResult<Boolean> reject(@RequestBody Map<String, String> body) {
        String flowId = body.get("flowId");
        String requestId = body.get("requestId");
        String nodeId = body.get("nodeId");
        String reason = body.getOrDefault("reason", "人工拒绝");

        if (!canAccessRuntime(requestId)) {
            return CommonResult.error(403, "无权审批该工作流实例");
        }

        boolean success = ApprovalNodeExecutor.reject(flowId, requestId, nodeId, reason);
        if (!success) {
            return CommonResult.error(404, "审批请求不存在或已处理");
        }

        // 闭环拒绝终态：更新运行实例为 ERROR，并清理挂起检查点，防止后续被重复恢复
        if (requestId != null && runtimeService != null) {
            try {
                Long runtimeId = Long.parseLong(requestId);
                KbRuntimeDO runDO = runtimeService.getById(runtimeId);
                if (runDO != null) {
                    runDO.setStatus(RuntimeStatusEnums.ERROR.getCode());
                    runDO.setOutput("人工审批已拒绝: " + reason);
                    runtimeService.updateById(runDO);
                }
                if (dagCheckpointManager != null) {
                    dagCheckpointManager.deleteCheckpoint(String.valueOf(runtimeId));
                }
            } catch (Exception ex) {
                log.warn("[ApprovalProxy] 拒绝审批更新运行实例异常: {}", ex.getMessage());
            }
        }
        return CommonResult.success(true);
    }

    @Operation(summary = "创建测试审批数据（仅显式开启 dev 开关时可用）")
    @PreAuthorize("@ss.hasPermi('kb:bot:bot:edit')")
    @PostMapping("/dev/create")
    public CommonResult<Map<String, Object>> createTestData() {
        if (!devEndpointEnabled) {
            return CommonResult.error(403, "测试审批数据端点未启用");
        }
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

    private boolean canAccessApprovalItem(Map<String, Object> item) {
        if (item == null) {
            return false;
        }
        Object requestId = item.get("requestId");
        if (requestId == null) {
            return false;
        }
        return canAccessRuntime(String.valueOf(requestId));
    }

    /**
     * 运行实例租户校验：仅允许当前工作区或超级管理员操作；开发/测试数据无运行实例时放行（由权限注解兜底）。
     */
    private boolean canAccessRuntime(String runtimeIdText) {
        if (runtimeIdText == null || runtimeService == null) {
            return true;
        }
        Long runtimeId;
        try {
            runtimeId = Long.parseLong(runtimeIdText.trim());
        } catch (NumberFormatException e) {
            return true;
        }
        KbRuntimeDO runtime = runtimeService.getById(runtimeId);
        if (runtime == null) {
            return true;
        }
        try {
            if (SecurityUtils.isAdmin(SecurityUtils.getUserId())) {
                return true;
            }
        } catch (Exception ignored) {
            // 无认证上下文：按工作区严格校验
        }
        Long currentWorkspace = getWorkSpaceId();
        return Objects.equals(runtime.getWorkspaceId(), currentWorkspace);
    }

    private void restorePending(String pendingKey, String pendingJson) {
        if (redisService == null || pendingJson == null || pendingJson.isBlank()) {
            return;
        }
        try {
            redisService.set(pendingKey, pendingJson, ApprovalNodeExecutor.DEFAULT_TIMEOUT_SECONDS);
            log.warn("[ApprovalProxy] 自动恢复失败，已补偿回滚审批待办: key={}", pendingKey);
        } catch (Exception e) {
            log.error("[ApprovalProxy] 补偿回滚审批待办失败: key={}, error={}", pendingKey, e.getMessage());
        }
    }
}

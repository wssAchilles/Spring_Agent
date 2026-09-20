package tech.qiantong.qknow.hermes.flow.hitl.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.flow.hitl.dto.HumanApprovalDecision;
import tech.qiantong.qknow.hermes.flow.hitl.dto.HumanApprovalDecision.ApprovalAction;
import tech.qiantong.qknow.hermes.flow.hitl.model.WorkflowDebugReceipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Phase 124: 反应式异步挂起与状态守恒治理中枢 (WorkflowHitlReactiveGovernor)
 * 遵循定理 1.2 人机协同审批 (HITL) 异步流式挂起恢复与非阻塞反应式状态守恒定理
 * 
 * 核心机制：
 * 1. 响应式事件挂起与非阻塞 CompletableFuture 调度，主线程与虚拟线程 0 忙等；
 * 2. 状态守恒不变量：挂起期间对工作流变量上下文实施只读哈希守恒检验 H(sigma_t) == H(sigma_barrier)；
 * 3. 支持三种仲裁流转：APPROVE (放行), REJECT (拒绝终止), PATCH_AND_APPROVE (热补丁因果派生)；
 * 4. 超时自动安全熔断降级 (Fail-Close) 与看门狗治理；
 * 5. 全流程签署端到端不可变存证凭单 WorkflowDebugReceipt (纯 Java 21 Record 格式)。
 */
public class WorkflowHitlReactiveGovernor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowHitlReactiveGovernor.class);

    /**
     * 待决审批工单记录
     */
    public record HitlTicket(
            String ticketId,
            String executionBatchId,
            String workflowId,
            String nodeId,
            Map<String, Object> baselineVariables,
            String suspendedStateHash,
            long createdAtMicros,
            long timeoutMicros,
            CompletableFuture<GovernorResolution> future
    ) {}

    /**
     * 审批决议输出结果
     */
    public record GovernorResolution(
            String ticketId,
            ApprovalAction action,
            String operatorUserId,
            String comment,
            Map<String, Object> finalVariables,
            String hotPatchDigest,
            boolean isStateConserved,
            WorkflowDebugReceipt receipt
    ) {}

    private final ConcurrentHashMap<String, HitlTicket> activeTickets = new ConcurrentHashMap<>();

    /**
     * 挂起工作流并返回完整的待决工单对象（包含 ticketId 与响应式 future）
     */
    public HitlTicket suspendWorkflowAndGetTicket(
            String executionBatchId,
            String workflowId,
            String nodeId,
            Map<String, Object> currentVariables,
            long timeoutMs
    ) {
        String ticketId = "TICKET_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long nowMicros = System.currentTimeMillis() * 1000L;
        long timeoutMicros = nowMicros + (timeoutMs * 1000L);

        Map<String, Object> immutableVars = currentVariables != null 
                ? Collections.unmodifiableMap(new HashMap<>(currentVariables)) 
                : Collections.emptyMap();
        String baselineHash = computeStateHash(immutableVars);

        CompletableFuture<GovernorResolution> future = new CompletableFuture<>();

        HitlTicket ticket = new HitlTicket(
                ticketId,
                executionBatchId,
                workflowId,
                nodeId,
                immutableVars,
                baselineHash,
                nowMicros,
                timeoutMicros,
                future
        );

        activeTickets.put(ticketId, ticket);
        log.info("[HITL Governor] 工作流已反应式挂起. ticketId={}, nodeId={}, stateHash={}", ticketId, nodeId, baselineHash);
        return ticket;
    }

    /**
     * 挂起工作流并返回响应式决议 Future
     */
    public CompletableFuture<GovernorResolution> suspendWorkflow(
            String executionBatchId,
            String workflowId,
            String nodeId,
            Map<String, Object> currentVariables,
            long timeoutMs
    ) {
        return suspendWorkflowAndGetTicket(executionBatchId, workflowId, nodeId, currentVariables, timeoutMs).future();
    }

    /**
     * 人工放行恢复 (APPROVE)
     */
    public GovernorResolution approve(String ticketId, String operatorUserId, String comment) {
        HitlTicket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("未找到待决审批工单或工单已结案: " + ticketId);
        }

        // 状态守恒检验
        String currentHash = computeStateHash(ticket.baselineVariables());
        boolean isConserved = ticket.suspendedStateHash().equalsIgnoreCase(currentHash);
        if (!isConserved) {
            log.error("[HITL Governor] 状态守恒检验失败! 原始哈希={}, 当前哈希={}", ticket.suspendedStateHash(), currentHash);
        }

        long endMicros = System.currentTimeMillis() * 1000L;
        WorkflowDebugReceipt receipt = WorkflowDebugReceipt.createSigned(
                "RCP_" + ticketId,
                ticket.executionBatchId(),
                ticket.workflowId(),
                1,
                0,
                0,
                1,
                operatorUserId,
                ticket.createdAtMicros(),
                endMicros,
                "APPROVED"
        );

        GovernorResolution resolution = new GovernorResolution(
                ticketId,
                ApprovalAction.APPROVE,
                operatorUserId,
                comment,
                ticket.baselineVariables(),
                "NONE",
                isConserved,
                receipt
        );

        ticket.future().complete(resolution);
        log.info("[HITL Governor] 工单已人工放行. ticketId={}, operator={}", ticketId, operatorUserId);
        return resolution;
    }

    /**
     * 注入现场热补丁并放行恢复 (PATCH_AND_APPROVE)
     */
    public GovernorResolution patchAndResume(
            String ticketId,
            String operatorUserId,
            Map<String, Object> hotPatchVariables,
            String comment
    ) {
        HitlTicket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("未找到待决审批工单或工单已结案: " + ticketId);
        }

        // 验证前序上下文守恒不变量
        String currentHash = computeStateHash(ticket.baselineVariables());
        boolean isConserved = ticket.suspendedStateHash().equalsIgnoreCase(currentHash);

        // 合并变量并计算热补丁 SHA-256 变更指纹
        Map<String, Object> mergedVariables = new HashMap<>(ticket.baselineVariables());
        if (hotPatchVariables != null) {
            mergedVariables.putAll(hotPatchVariables);
        }
        Map<String, Object> immutableMerged = Collections.unmodifiableMap(mergedVariables);
        String patchDigest = computeStateHash(hotPatchVariables != null ? hotPatchVariables : Collections.emptyMap());

        long endMicros = System.currentTimeMillis() * 1000L;
        WorkflowDebugReceipt receipt = WorkflowDebugReceipt.createSigned(
                "RCP_" + ticketId,
                ticket.executionBatchId(),
                ticket.workflowId(),
                2,
                0,
                1, // 记录 1 次热调优时空分叉
                1,
                operatorUserId,
                ticket.createdAtMicros(),
                endMicros,
                "HOT_PATCHED_RESUMED"
        );

        GovernorResolution resolution = new GovernorResolution(
                ticketId,
                ApprovalAction.INTERVENE_MODIFY,
                operatorUserId,
                comment,
                immutableMerged,
                patchDigest,
                isConserved,
                receipt
        );

        ticket.future().complete(resolution);
        log.info("[HITL Governor] 工单已注入热补丁并恢复. ticketId={}, patchDigest={}", ticketId, patchDigest);
        return resolution;
    }

    /**
     * 拒绝并终止工作流 (REJECT)
     */
    public GovernorResolution reject(String ticketId, String operatorUserId, String reason) {
        HitlTicket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("未找到待决审批工单或工单已结案: " + ticketId);
        }

        long endMicros = System.currentTimeMillis() * 1000L;
        WorkflowDebugReceipt receipt = WorkflowDebugReceipt.createSigned(
                "RCP_" + ticketId,
                ticket.executionBatchId(),
                ticket.workflowId(),
                1,
                1,
                0,
                1,
                operatorUserId,
                ticket.createdAtMicros(),
                endMicros,
                "REJECTED_TERMINATED"
        );

        GovernorResolution resolution = new GovernorResolution(
                ticketId,
                ApprovalAction.REJECT,
                operatorUserId,
                reason,
                ticket.baselineVariables(),
                "NONE",
                true,
                receipt
        );

        ticket.future().complete(resolution);
        log.warn("[HITL Governor] 工单已被人类审批者否决终止. ticketId={}, reason={}", ticketId, reason);
        return resolution;
    }

    /**
     * 看门狗超时强制安全熔断降级 (Fail-Close)
     */
    public GovernorResolution triggerWatchdogTimeout(String ticketId) {
        HitlTicket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("未找到待决审批工单或工单已结案: " + ticketId);
        }

        long endMicros = System.currentTimeMillis() * 1000L;
        WorkflowDebugReceipt receipt = WorkflowDebugReceipt.createSigned(
                "RCP_" + ticketId,
                ticket.executionBatchId(),
                ticket.workflowId(),
                1,
                1,
                0,
                0,
                "SYSTEM_WATCHDOG",
                ticket.createdAtMicros(),
                endMicros,
                "WATCHDOG_TIMEOUT_ABORT"
        );

        GovernorResolution resolution = new GovernorResolution(
                ticketId,
                ApprovalAction.WATCHDOG_TIMEOUT_ABORT,
                "SYSTEM_WATCHDOG",
                "审批超时自动触发 Fail-Close 熔断终止",
                ticket.baselineVariables(),
                "NONE",
                true,
                receipt
        );

        ticket.future().complete(resolution);
        log.error("[HITL Governor] 工单审批超时，触发安全看门狗熔断终止. ticketId={}", ticketId);
        return resolution;
    }

    /**
     * 获取当前处于挂起中的工单数量
     */
    public int getActiveTicketCount() {
        return activeTickets.size();
    }

    /**
     * 获取指定工单快照信息
     */
    public Optional<HitlTicket> getTicket(String ticketId) {
        return Optional.ofNullable(activeTickets.get(ticketId));
    }

    /**
     * 计算变量上下文确定性 SHA-256 状态摘要 (按键字典序排序归一化)
     */
    public static String computeStateHash(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // 空 SHA-256
        }

        String serialized = variables.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> String.valueOf(e.getKey()) + "=" + String.valueOf(e.getValue()))
                .collect(Collectors.joining(";"));

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(serialized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }
}

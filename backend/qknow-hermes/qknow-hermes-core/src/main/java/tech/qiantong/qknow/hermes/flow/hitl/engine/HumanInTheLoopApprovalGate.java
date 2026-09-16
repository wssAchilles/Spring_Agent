package tech.qiantong.qknow.hermes.flow.hitl.engine;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.hitl.dto.HumanApprovalDecision;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

/**
 * 人机协同 (HITL) 动态审批中断与安全干预门禁
 * 负责高危节点的原子阻断挂起、看门狗超时硬熔断与决策恢复流转
 */
@Slf4j
@Component
public class HumanInTheLoopApprovalGate {

    private final Map<String, CompletableFuture<HumanApprovalDecision>> pendingFutures = new ConcurrentHashMap<>();
    private final Map<String, PendingTaskInfo> pendingTasks = new ConcurrentHashMap<>();
    private final Map<String, HumanApprovalDecision> completedApprovals = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdogScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hitl-watchdog-worker");
        t.setDaemon(true);
        return t;
    });

    private final Set<String> defaultRiskNodeTypes = Set.of(
            "DATABASE_DROP",
            "SQL_EXECUTE",
            "EXTERNAL_HTTP_MUTATION",
            "EXTERNAL_PAYMENT",
            "PRODUCTION_DEPLOY",
            "PRIVILEGED_CLI"
    );

    record PendingTaskInfo(
            String workflowId,
            String branchId,
            String nodeUuid,
            Map<String, Object> currentVars,
            ScheduledFuture<?> timeoutTask
    ) {}

    /**
     * 判断节点是否为高危需要人机审批门禁
     */
    public boolean isRiskNode(String nodeUuid, String nodeType, Map<String, Object> nodeConfig) {
        if (nodeType != null && defaultRiskNodeTypes.contains(nodeType.toUpperCase())) {
            return true;
        }
        if (nodeConfig != null && Boolean.TRUE.equals(nodeConfig.get("requiresApproval"))) {
            return true;
        }
        return false;
    }

    /**
     * 挂起执行线程，等待人机审批或超时
     */
    public CompletableFuture<HumanApprovalDecision> interceptAndSuspend(
            String workflowId,
            String branchId,
            String nodeUuid,
            Map<String, Object> currentVars,
            long timeoutSeconds
    ) {
        String taskKey = workflowId + ":" + nodeUuid;
        CompletableFuture<HumanApprovalDecision> future = new CompletableFuture<>();
        pendingFutures.put(taskKey, future);

        // 启动看门狗定时器：超时自动 Fail-Close 终止
        ScheduledFuture<?> timeoutTask = watchdogScheduler.schedule(() -> {
            log.warn("HITL 审批看门狗超时 ({}s): workflowId={}, nodeUuid={}，触发 Fail-Close",
                    timeoutSeconds, workflowId, nodeUuid);
            HumanApprovalDecision abortDecision = new HumanApprovalDecision(
                    "appr_timeout_" + UUID.randomUUID().toString().substring(0, 8),
                    workflowId,
                    nodeUuid,
                    HumanApprovalDecision.ApprovalAction.WATCHDOG_TIMEOUT_ABORT,
                    "SYSTEM_WATCHDOG",
                    "审批等待超时，系统强制安全终止",
                    Collections.emptyMap(),
                    DigestUtils.sha256Hex("WATCHDOG_TIMEOUT:" + taskKey),
                    System.currentTimeMillis() * 1000L
            );
            submitApproval(abortDecision);
        }, timeoutSeconds, TimeUnit.SECONDS);

        pendingTasks.put(taskKey, new PendingTaskInfo(workflowId, branchId, nodeUuid, currentVars, timeoutTask));
        log.info("HITL 审批门禁已挂起节点: taskKey={}, timeout={}s", taskKey, timeoutSeconds);
        return future;
    }

    /**
     * 人类或外部审批系统提交决策
     */
    public boolean submitApproval(HumanApprovalDecision decision) {
        String taskKey = decision.workflowId() + ":" + decision.nodeUuid();
        CompletableFuture<HumanApprovalDecision> future = pendingFutures.remove(taskKey);
        PendingTaskInfo info = pendingTasks.remove(taskKey);

        if (info != null && info.timeoutTask() != null) {
            info.timeoutTask().cancel(false);
        }

        completedApprovals.put(taskKey, decision);
        if (future != null && !future.isDone()) {
            future.complete(decision);
            log.info("HITL 审批决策已处理: taskKey={}, action={}", taskKey, decision.action());
            return true;
        }
        return false;
    }

    public HumanApprovalDecision getApprovalDecision(String workflowId, String nodeUuid) {
        return completedApprovals.get(workflowId + ":" + nodeUuid);
    }

    public boolean isPending(String workflowId, String nodeUuid) {
        return pendingFutures.containsKey(workflowId + ":" + nodeUuid);
    }

    public int getPendingCount() {
        return pendingFutures.size();
    }

    public int getCompletedCount() {
        return completedApprovals.size();
    }

    @PreDestroy
    public void shutdown() {
        watchdogScheduler.shutdownNow();
    }
}

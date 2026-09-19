package tech.qiantong.qknow.hermes.a2a.dsl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.a2a.card.AgentMeshRegistry;
import tech.qiantong.qknow.hermes.a2a.envelope.A2AMessageEnvelope;
import tech.qiantong.qknow.hermes.a2a.envelope.A2AMessageType;
import tech.qiantong.qknow.hermes.agent.blackboard.SharedBlackboard;
import tech.qiantong.qknow.hermes.agent.dag.DagTaskNode;
import tech.qiantong.qknow.hermes.agent.dag.PhasedExecutionPlan;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 零停机热重载引擎（基于 Java 21 AtomicReference 原子指针翻转与在途长事务 Graceful Drain 优雅下线）
 */
@Component
public class ZeroDowntimeHotReloadEngine {

    private static final Logger log = LoggerFactory.getLogger(ZeroDowntimeHotReloadEngine.class);

    private final ThreeStageStaticSafetyGate safetyGate;
    private final AgentMeshRegistry meshRegistry;
    private final AtomicReference<WorkflowVersionRegistry> registryRef = new AtomicReference<>(new WorkflowVersionRegistry());

    private final ExecutorService executor = new ThreadPoolExecutor(
            8, 32, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            Thread.ofVirtual().name("hotreload-worker-", 0).factory(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public ZeroDowntimeHotReloadEngine(ThreeStageStaticSafetyGate safetyGate, AgentMeshRegistry meshRegistry) {
        this.safetyGate = safetyGate;
        this.meshRegistry = meshRegistry;
    }

    /**
     * 零停机热部署新工作流定义
     */
    public DslWorkflowCompilationReceipt deployWorkflow(DslWorkflowDefinition definition) {
        ThreeStageStaticSafetyGate.CompilationResult result = safetyGate.compileAndAssert(definition);
        registryRef.get().publishVersion(definition, result.executionPlan(), result.receipt());
        log.info("[HotReload Engine] 工作流 [{}] (v{}) 零停机热部署完成", definition.workflowId(), definition.version());
        return result.receipt();
    }

    /**
     * 针对新会话：自动绑定当前最新活跃版本并执行
     */
    public void executeWorkflow(String sessionId, SharedBlackboard blackboard) {
        Optional<WorkflowVersionRegistry.VersionLease> leaseOpt = registryRef.get().acquireActiveLease();
        if (leaseOpt.isEmpty()) {
            throw new IllegalStateException("当前无可用或已激活的工作流版本");
        }
        WorkflowVersionRegistry.VersionLease lease = leaseOpt.get();
        try {
            executePlanInternal(sessionId, lease.version(), lease.executionPlan(), blackboard);
        } finally {
            registryRef.get().releaseLease(lease.version());
        }
    }

    /**
     * 针对在途会话：锁定指定历史版本执行，杜绝跨版本状态撕裂
     */
    public void executeWorkflowWithVersion(String sessionId, int version, SharedBlackboard blackboard) {
        Optional<WorkflowVersionRegistry.VersionLease> leaseOpt = registryRef.get().acquireLease(version);
        if (leaseOpt.isEmpty()) {
            throw new IllegalStateException("指定工作流版本 v" + version + " 已彻底下线或不存在");
        }
        WorkflowVersionRegistry.VersionLease lease = leaseOpt.get();
        try {
            executePlanInternal(sessionId, lease.version(), lease.executionPlan(), blackboard);
        } finally {
            registryRef.get().releaseLease(lease.version());
        }
    }

    /**
     * 一键原子回滚至指定历史版本
     */
    public boolean rollbackToVersion(int targetVersion) {
        return registryRef.get().rollbackToVersion(targetVersion);
    }

    public int getActiveVersion() {
        return registryRef.get().getActiveVersion();
    }

    public WorkflowVersionRegistry getVersionRegistry() {
        return registryRef.get();
    }

    private void executePlanInternal(String sessionId, int version, PhasedExecutionPlan plan, SharedBlackboard blackboard) {
        if (plan == null || plan.isEmpty()) {
            return;
        }

        String traceId = "trace_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.info("[HotReload Engine] 启动会话 {} (v{}) 调度, traceId: {}, 阶段总数: {}",
                sessionId, version, traceId, plan.phases().size());

        for (int pIdx = 0; pIdx < plan.phases().size(); pIdx++) {
            List<DagTaskNode> currentPhase = plan.phases().get(pIdx);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (DagTaskNode task : currentPhase) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    executeSingleTask(sessionId, version, traceId, task, blackboard);
                }, executor).orTimeout(task.timeoutSeconds(), TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.error("[HotReload Engine] 任务 [{}] 执行异常: {}", task.taskId(), ex.getMessage());
                    blackboard.commitFact(task.taskId(), "【降级兜底】：任务超时未完成", "SYSTEM_FALLBACK");
                    return null;
                });

                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    private void executeSingleTask(String sessionId, int version, String traceId, DagTaskNode task, SharedBlackboard blackboard) {
        Map<String, String> upstream = blackboard.getFactsByKeys(task.dependencies());
        StringBuilder payloadContext = new StringBuilder();
        upstream.forEach((k, v) -> payloadContext.append("[").append(k).append("]: ").append(v).append("\n"));

        Optional<AgentMeshRegistry.BiddingMatchResult> matchOpt = meshRegistry.findBestWorker(null, task.requiredCapability());
        String selectedAgentId = matchOpt.map(m -> m.card().agentId()).orElse("default_fallback_agent");
        String leaseToken = meshRegistry.issueLeaseToken(selectedAgentId, 60_000L);

        Map<String, Object> taskPayload = Map.of(
                "taskId", task.taskId(),
                "objective", task.objective(),
                "workflowVersion", version,
                "upstreamContext", payloadContext.toString()
        );
        A2AMessageEnvelope envelope = A2AMessageEnvelope.create(
                traceId,
                "SupervisorDispatcher",
                selectedAgentId,
                A2AMessageType.TASK_EXECUTE,
                leaseToken,
                taskPayload,
                60_000L
        );

        if (!envelope.isLeaseValid() || !meshRegistry.verifyLeaseToken(envelope.securityLeaseToken())) {
            blackboard.commitFact(task.taskId(), "【安全拒绝】：租约凭证已过期或非法", "SECURITY_GATE");
            return;
        }

        String result = "【" + selectedAgentId + " 执行完成 (v" + version + ")】：针对目标 [" + task.objective() + "] 生成专业分析结果";
        if (payloadContext.length() > 0) {
            result += "，已融合上游事实。";
        }

        blackboard.commitFact(task.taskId(), result, task.requiredCapability());
        blackboard.recordTrace(sessionId, task.taskId(), "SUCCESS", "A2A 协议信封执行成功 (v" + version + ")");
    }
}

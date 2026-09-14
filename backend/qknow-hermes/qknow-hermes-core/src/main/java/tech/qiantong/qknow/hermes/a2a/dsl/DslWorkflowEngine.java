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
 * 声明式多智能体工作流执行引擎（集成 A2A 协议信封、AgentCard 智能竞标与并发调度）
 */
@Component
public class DslWorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(DslWorkflowEngine.class);

    private final DslWorkflowCompiler compiler;
    private final AgentMeshRegistry meshRegistry;
    private final AtomicReference<WorkflowDefinition> activeWorkflowRef = new AtomicReference<>();

    private final ExecutorService executor = new ThreadPoolExecutor(
            8, 32, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            Thread.ofVirtual().name("dsl-agent-worker-", 0).factory(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public DslWorkflowEngine(DslWorkflowCompiler compiler, AgentMeshRegistry meshRegistry) {
        this.compiler = compiler;
        this.meshRegistry = meshRegistry;
    }

    /**
     * 零停机原子热更新工作流定义
     */
    public void deployWorkflow(WorkflowDefinition definition) {
        compiler.compile(definition); // 预编译校验
        activeWorkflowRef.set(definition);
        log.info("[DSL Engine] 热部署工作流定义成功: {} (v{})", definition.workflowId(), definition.version());
    }

    public Optional<WorkflowDefinition> getActiveWorkflow() {
        return Optional.ofNullable(activeWorkflowRef.get());
    }

    /**
     * 端到端编译并调度执行 DSL 工作流
     */
    public void executeWorkflow(String sessionId, WorkflowDefinition definition, SharedBlackboard blackboard) {
        PhasedExecutionPlan plan = compiler.compile(definition);
        executePlan(sessionId, plan, blackboard);
    }

    public void executePlan(String sessionId, PhasedExecutionPlan plan, SharedBlackboard blackboard) {
        if (plan == null || plan.isEmpty()) {
            return;
        }

        String traceId = "trace_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.info("[DSL Engine] 启动会话 {} 的 A2A 分布式调度, traceId: {}, 阶段总数: {}", sessionId, traceId, plan.phases().size());

        for (int pIdx = 0; pIdx < plan.phases().size(); pIdx++) {
            List<DagTaskNode> currentPhase = plan.phases().get(pIdx);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (DagTaskNode task : currentPhase) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    executeSingleA2ATask(sessionId, traceId, task, blackboard);
                }, executor).orTimeout(task.timeoutSeconds(), TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.error("[DSL Engine] 任务 [{}] 超时或执行异常: {}", task.taskId(), ex.getMessage());
                    blackboard.commitFact(task.taskId(), "【降级兜底】：任务超时未完成", "SYSTEM_FALLBACK");
                    return null;
                });

                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("[DSL Engine] Phase [{}] 并发执行栅栏已通过", pIdx);
        }
    }

    private void executeSingleA2ATask(String sessionId, String traceId, DagTaskNode task, SharedBlackboard blackboard) {
        // 1. 上游上下文抽取
        Map<String, String> upstream = blackboard.getFactsByKeys(task.dependencies());
        StringBuilder payloadContext = new StringBuilder();
        upstream.forEach((k, v) -> payloadContext.append("[").append(k).append("]: ").append(v).append("\n"));

        // 2. 动态竞标选拔最佳 Worker
        Optional<AgentMeshRegistry.BiddingMatchResult> matchOpt = meshRegistry.findBestWorker(null, task.requiredCapability());
        String selectedAgentId = matchOpt.map(m -> m.card().agentId()).orElse("default_fallback_agent");

        // 3. 签发时效安全租约
        String leaseToken = meshRegistry.issueLeaseToken(selectedAgentId, 60_000L);

        // 4. 封装标准 A2A 协议信封
        Map<String, Object> taskPayload = Map.of(
                "taskId", task.taskId(),
                "objective", task.objective(),
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

        // 5. 租约核验与模拟执行
        if (!envelope.isLeaseValid() || !meshRegistry.verifyLeaseToken(envelope.securityLeaseToken())) {
            log.warn("[DSL Engine] 租约失效，拒绝执行: {}", envelope.messageId());
            blackboard.commitFact(task.taskId(), "【安全拒绝】：租约凭证已过期或非法", "SECURITY_GATE");
            return;
        }

        // 模拟智能体根据目标执行并产出
        String result = "【" + selectedAgentId + " 执行完成】：针对目标 [" + task.objective() + "] 生成专业分析结果";
        if (payloadContext.length() > 0) {
            result += "，已融合上游事实。";
        }

        // 6. CAS 提交到共享黑板
        blackboard.commitFact(task.taskId(), result, task.requiredCapability());
        blackboard.recordTrace(sessionId, task.taskId(), "SUCCESS", "A2A 协议信封执行成功");
    }
}

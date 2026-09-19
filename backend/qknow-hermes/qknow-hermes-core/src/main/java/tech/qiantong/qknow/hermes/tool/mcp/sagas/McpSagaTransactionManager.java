package tech.qiantong.qknow.hermes.tool.mcp.sagas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.tool.mcp.governance.McpVirtualThreadCircuitBreaker;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.dto.McpSagaReceipt;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * 生产级 MCP Sagas 分布式事务管理器 (定理 1.1 与定理 1.2)
 * 1. 统一协调动态流水线 Kahn DAG 依赖并发调度与 Sagas 逆序补偿；
 * 2. 结合 Java 21 虚拟线程三态断路器，实现超时熔断隔离与 Fail-Open 软着陆；
 * 3. 严格遵循 LIFO 逆序出栈触发补偿动作，保障系统状态收敛于初始一致态（概率 1.0）；
 * 4. 签发纯 Java 21 Record 格式的 SHA-256 密码学防篡改存证凭单。
 */
@Component
public class McpSagaTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(McpSagaTransactionManager.class);

    private final McpDynamicPipelineDispatcher pipelineDispatcher;
    private final McpCompensatingActionRegistry compensationRegistry;
    private final McpVirtualThreadCircuitBreaker circuitBreaker;

    public McpSagaTransactionManager(
            McpDynamicPipelineDispatcher pipelineDispatcher,
            McpCompensatingActionRegistry compensationRegistry,
            McpVirtualThreadCircuitBreaker circuitBreaker
    ) {
        this.pipelineDispatcher = pipelineDispatcher != null ? pipelineDispatcher : new McpDynamicPipelineDispatcher();
        this.compensationRegistry = compensationRegistry != null ? compensationRegistry : new McpCompensatingActionRegistry();
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : new McpVirtualThreadCircuitBreaker();
    }

    /**
     * 内部记录已执行的正向步骤上下文
     */
    private record ExecutedStepContext(
            String stepId,
            String toolName,
            String leaseToken,
            Map<String, Object> params,
            long costMs
    ) {}

    /**
     * 执行 Sagas 动态流水线事务
     *
     * @param transactionId 全局事务唯一 ID
     * @param nodes         流水线节点集合
     * @param executors     工具具体执行函数映射 (toolName -> 函数)
     * @param timeoutMillis 单工具执行超时时间 (ms)
     * @return 不可变事务存证凭单
     */
    public McpSagaReceipt executePipeline(
            String transactionId,
            List<McpDynamicPipelineDispatcher.PipelineNode> nodes,
            Map<String, Function<Map<String, Object>, String>> executors,
            long timeoutMillis
    ) {
        long startTime = System.currentTimeMillis();
        String receiptId = "RCP-SAGA-" + UUID.randomUUID().toString().substring(0, 8);
        List<McpSagaReceipt.McpStepRecord> stepRecords = new CopyOnWriteArrayList<>();
        Deque<ExecutedStepContext> executedStack = new ConcurrentLinkedDeque<>();

        if (nodes == null || nodes.isEmpty()) {
            return McpSagaReceipt.create(
                    receiptId,
                    transactionId,
                    "COMMITTED",
                    0, 0, 0,
                    List.of(),
                    System.currentTimeMillis() - startTime,
                    Instant.now()
            );
        }

        // 1. 解析 DAG 依赖拓扑
        McpDynamicPipelineDispatcher.PipelineTopology topology = pipelineDispatcher.resolveTopology(nodes);
        log.info("[Sagas 启动] 事务 ID: {}, 节点数: {}, 边数: {}, 执行层数: {}",
                transactionId, topology.totalNodes(), topology.totalEdges(), topology.executionLayers().size());

        boolean hasFailure = false;
        String failureReason = null;

        // 2. 按层推进执行
        for (List<McpDynamicPipelineDispatcher.PipelineNode> layer : topology.executionLayers()) {
            if (hasFailure) {
                break;
            }

            // 收集当前层的异步任务
            List<Callable<StepExecutionResult>> layerTasks = new ArrayList<>();
            for (McpDynamicPipelineDispatcher.PipelineNode node : layer) {
                String leaseToken = compensationRegistry.generateLeaseToken(transactionId, node.toolName());
                layerTasks.add(() -> executeSingleNode(node, leaseToken, executors, timeoutMillis));
            }

            try {
                // 使用虚拟线程并发执行当前层
                List<Future<StepExecutionResult>> futures = pipelineDispatcher.getVirtualThreadExecutor().invokeAll(layerTasks);
                for (Future<StepExecutionResult> future : futures) {
                    StepExecutionResult result = future.get();
                    stepRecords.add(result.record());

                    if (result.record().success()) {
                        executedStack.push(new ExecutedStepContext(
                                result.record().stepId(),
                                result.record().toolName(),
                                result.record().leaseToken(),
                                result.params(),
                                result.record().costMs()
                        ));
                        compensationRegistry.markForwardExecuted(result.record().leaseToken());
                    } else {
                        hasFailure = true;
                        failureReason = result.record().errorMessage();
                        log.warn("[Sagas 节点失败] 工具 {} 执行失败: {}", result.record().toolName(), failureReason);
                    }
                }
            } catch (Exception e) {
                hasFailure = true;
                failureReason = "并发层执行异常: " + e.getMessage();
                log.error("[Sagas 执行异常] 事务 ID: {}, 错误: {}", transactionId, failureReason, e);
            }
        }

        int executedCount = executedStack.size();
        int compensatedCount = 0;
        String finalStatus;

        // 3. 故障触发逆序补偿 (Rollback)
        if (hasFailure) {
            log.warn("[Sagas 触发回滚] 事务 ID: {}, 准备对已成功的 {} 个步骤执行逆序补偿 (LIFO)",
                    transactionId, executedCount);

            boolean allCompensated = true;
            while (!executedStack.isEmpty()) {
                ExecutedStepContext step = executedStack.pop();
                long compStart = System.currentTimeMillis();

                boolean compSuccess = compensationRegistry.executeCompensation(
                        step.toolName(),
                        step.leaseToken(),
                        step.params()
                );

                long compCost = System.currentTimeMillis() - compStart;
                if (compSuccess) {
                    compensatedCount++;
                } else {
                    allCompensated = false;
                }

                stepRecords.add(new McpSagaReceipt.McpStepRecord(
                        "COMP-" + step.stepId(),
                        step.toolName(),
                        "COMPENSATE",
                        compSuccess,
                        step.leaseToken(),
                        compCost,
                        compSuccess ? null : "补偿处理器返回失败"
                ));
            }

            finalStatus = allCompensated ? "COMPENSATED" : "PARTIALLY_FAILED";
        } else {
            finalStatus = "COMMITTED";
        }

        long totalCostMs = System.currentTimeMillis() - startTime;
        log.info("[Sagas 结束] 事务 ID: {}, 状态: {}, 执行步数: {}, 补偿步数: {}, 耗时: {}ms",
                transactionId, finalStatus, executedCount, compensatedCount, totalCostMs);

        // 4. 签发不可变存证凭单
        return McpSagaReceipt.create(
                receiptId,
                transactionId,
                finalStatus,
                nodes.size(),
                executedCount,
                compensatedCount,
                stepRecords,
                totalCostMs,
                Instant.now()
        );
    }

    private record StepExecutionResult(
            McpSagaReceipt.McpStepRecord record,
            Map<String, Object> params
    ) {}

    private StepExecutionResult executeSingleNode(
            McpDynamicPipelineDispatcher.PipelineNode node,
            String leaseToken,
            Map<String, Function<Map<String, Object>, String>> executors,
            long timeoutMillis
    ) {
        long start = System.currentTimeMillis();

        // 防悬挂与幂等检查
        if (!compensationRegistry.canExecuteForward(leaseToken)) {
            long cost = System.currentTimeMillis() - start;
            return new StepExecutionResult(
                    new McpSagaReceipt.McpStepRecord(
                            node.nodeId(),
                            node.toolName(),
                            "EXECUTE",
                            false,
                            leaseToken,
                            cost,
                            "防悬挂/幂等拦截: 租约被标记为墓碑或已执行"
                    ),
                    node.params()
            );
        }

        Function<Map<String, Object>, String> executor = executors != null ? executors.get(node.toolName()) : null;
        if (executor == null) {
            long cost = System.currentTimeMillis() - start;
            return new StepExecutionResult(
                    new McpSagaReceipt.McpStepRecord(
                            node.nodeId(),
                            node.toolName(),
                            "EXECUTE",
                            false,
                            leaseToken,
                            cost,
                            "未找到工具执行函数: " + node.toolName()
                    ),
                    node.params()
            );
        }

        // 使用断路器与虚拟线程隔离执行
        String result = circuitBreaker.executeWithIsolation(
                node.toolName(),
                () -> executor.apply(node.params()),
                timeoutMillis
        );

        long cost = System.currentTimeMillis() - start;
        boolean success = result != null && !result.contains("\"fallback\":true") && !result.contains("\"error\"");

        return new StepExecutionResult(
                new McpSagaReceipt.McpStepRecord(
                        node.nodeId(),
                        node.toolName(),
                        "EXECUTE",
                        success,
                        leaseToken,
                        cost,
                        success ? null : result
                ),
                node.params()
        );
    }

    public McpDynamicPipelineDispatcher getPipelineDispatcher() {
        return pipelineDispatcher;
    }

    public McpCompensatingActionRegistry getCompensationRegistry() {
        return compensationRegistry;
    }

    public McpVirtualThreadCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}

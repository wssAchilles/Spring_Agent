package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphExecutionReceipt;
import tech.qiantong.qknow.hermes.flow.stategraph.enums.LoopDecisionType;
import tech.qiantong.qknow.hermes.flow.stategraph.enums.StateGraphEdgeType;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraph;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphEdge;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphNode;
import tech.qiantong.qknow.hermes.flow.stategraph.validator.StateGraphValidator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 状态图核心超步调度器 (StateGraphScheduler)
 * 基于 Google Pregel 同步超步模型推进，集成收敛门禁、局部自愈与批处理子图引擎
 */
@Slf4j
@Component
public class StateGraphScheduler {

    @Getter
    private final StateGraphValidator validator;
    @Getter
    private final ConvergenceLoopGuard loopGuard;
    @Getter
    private final NodeSelfHealingRouter selfHealingRouter;
    @Getter
    private final BatchIterationSubgraphEngine iterationEngine;
    @Getter
    private final StateGraphHypersphereProjector hypersphereProjector;

    public StateGraphScheduler() {
        this(new StateGraphValidator(),
                new ConvergenceLoopGuard(50),
                new NodeSelfHealingRouter(3, 10, 2000),
                new BatchIterationSubgraphEngine(1024, 16),
                new StateGraphHypersphereProjector(1536));
    }

    public StateGraphScheduler(
            StateGraphValidator validator,
            ConvergenceLoopGuard loopGuard,
            NodeSelfHealingRouter selfHealingRouter,
            BatchIterationSubgraphEngine iterationEngine,
            StateGraphHypersphereProjector hypersphereProjector) {
        this.validator = validator != null ? validator : new StateGraphValidator();
        this.loopGuard = loopGuard != null ? loopGuard : new ConvergenceLoopGuard(50);
        this.selfHealingRouter = selfHealingRouter != null ? selfHealingRouter : new NodeSelfHealingRouter();
        this.iterationEngine = iterationEngine != null ? iterationEngine : new BatchIterationSubgraphEngine();
        this.hypersphereProjector = hypersphereProjector != null ? hypersphereProjector : new StateGraphHypersphereProjector();
    }

    /**
     * 执行状态图
     *
     * @param graph 状态图拓扑定义
     * @param context 运行上下文
     * @return 包含自签名的不可变执行凭单
     */
    public StateGraphExecutionReceipt execute(StateGraph graph, StateGraphContext context) {
        long startNano = System.nanoTime();
        // 1. 静态拓扑合法性校验
        validator.validate(graph);

        // 注册图内所有节点到自愈路由器的注册表中
        if (graph.getNodeMap() != null) {
            graph.getNodeMap().values().forEach(selfHealingRouter::registerNode);
        }

        Set<String> completedNodes = Collections.synchronizedSet(new LinkedHashSet<>());
        Set<String> activeNodes = new LinkedHashSet<>(graph.getStartNodeUuids());
        int maxSupersteps = graph.getMaxSupersteps() > 0 ? graph.getMaxSupersteps() : 50;

        log.info("[Scheduler] 状态图 [{}] 开始超步调度, 初始活跃节点: {}",
                graph.getGraphId(), activeNodes);

        // 2. Pregel 同步超步主循环推进
        while (!activeNodes.isEmpty()) {
            int step = context.incrementSuperstep();
            if (step > maxSupersteps) {
                log.warn("[Scheduler] 全局超步达到硬熔断上限 {} (ERR_GRAPH_SUPERSTEP_EXCEEDED), 切入 DEGRADED_BREAK 逃逸",
                        maxSupersteps);
                context.getDegradedBreakOccurred().set(true);
                break;
            }

            log.debug("[Scheduler] 执行第 [{}] 超步, 当前活跃节点数: {}", step, activeNodes.size());
            Set<String> nextActiveNodes = new LinkedHashSet<>();

            for (String nodeUuid : activeNodes) {
                StateGraphNode node = graph.getNode(nodeUuid);
                if (node == null) {
                    log.warn("[Scheduler] 节点 [{}] 不存在, 跳过", nodeUuid);
                    continue;
                }

                // 执行节点逻辑（区分普通节点与批处理子图节点）
                NodeRunResultBO result;
                if (node.isIterationSubgraph() && node.getSubGraph() != null) {
                    result = executeSubgraphNode(node, context);
                } else {
                    result = selfHealingRouter.executeWithHealing(node, context);
                }

                context.putNodeResult(nodeUuid, result);
                completedNodes.add(nodeUuid);

                // 若发生不可恢复的节点失败且未配置 Fallback，且非容错模式，终止后续出边派发
                if (result.getStatus() != null && result.getStatus() == 2) {
                    log.error("[Scheduler] 节点 [{}] 执行失败, 阻断后续出边流转", nodeUuid);
                    continue;
                }

                // 评估出边
                List<StateGraphEdge> outEdges = graph.getOutgoingEdges(nodeUuid);
                for (StateGraphEdge edge : outEdges) {
                    if (edge.getEdgeType() == StateGraphEdgeType.FORWARD) {
                        nextActiveNodes.add(edge.getTargetNodeUuid());
                    } else if (edge.getEdgeType() == StateGraphEdgeType.CONDITIONAL) {
                        // 条件分支判定
                        if (evaluateEdgeCondition(edge, context, result)) {
                            nextActiveNodes.add(edge.getTargetNodeUuid());
                        }
                    } else if (edge.getEdgeType() == StateGraphEdgeType.LOOP_BACK) {
                        // 循环门禁决策
                        LoopDecisionType decision = loopGuard.evaluateLoopDecision(edge, context, result);
                        if (decision == LoopDecisionType.CONTINUE_LOOP) {
                            nextActiveNodes.add(edge.getTargetNodeUuid());
                        } else if (decision == LoopDecisionType.DEGRADED_BREAK) {
                            log.warn("[Scheduler] 循环边 [{}] 触发 DEGRADED_BREAK, 截断回跳", edge.getEdgeId());
                            // 降级逃逸时不再回跳，允许可能存在的向前边流转
                        }
                        // CONVERGED_EXIT 时不回跳
                    }
                }
            }

            activeNodes = nextActiveNodes;
        }

        long durationNs = System.nanoTime() - startNano;
        long latencyUs = durationNs / 1000L;

        // 3. 千问 1536 维超球面流形快照投影
        double[] stateVector = hypersphereProjector.projectState(context);
        context.setHypersphereStateVector(stateVector);

        // 4. 生成不可变自签名执行凭单
        Map<String, Integer> loopCountMap = new HashMap<>();
        context.getLoopCounters().forEach((k, v) -> loopCountMap.put(k, v.get()));

        StateGraphExecutionReceipt receipt = StateGraphExecutionReceipt.createSigned(
                context.getExecutionId(),
                graph.getGraphId(),
                context.getCurrentSuperstep().get(),
                completedNodes,
                activeNodes,
                loopCountMap,
                context.getDegradedBreakOccurred().get(),
                context.getSelfHealedOccurred().get(),
                latencyUs,
                System.currentTimeMillis()
        );

        log.info("[Scheduler] 状态图 [{}] 调度完成, 总超步: {}, 耗时: {} us, 凭单签名: {}",
                graph.getGraphId(), receipt.totalSupersteps(), latencyUs, receipt.sha256Signature());
        return receipt;
    }

    private NodeRunResultBO executeSubgraphNode(StateGraphNode node, StateGraphContext context) {
        String key = node.getBatchItemsKey() != null ? node.getBatchItemsKey() : "items";
        Object val = context.getSharedState().get(key);
        List<Object> items = new ArrayList<>();
        if (val instanceof List<?> listVal) {
            items.addAll(listVal);
        }

        List<NodeRunResultBO> subResults = iterationEngine.executeMapReduce(
                items,
                context,
                (item, idx, ctx) -> {
                    // 执行子图单项
                    StateGraphContext subCtx = new StateGraphContext(
                            context.getExecutionId() + "-sub-" + idx,
                            node.getSubGraph().getGraphId(),
                            5
                    );
                    subCtx.getSharedState().put("item", item);
                    subCtx.getSharedState().put("index", idx);
                    execute(node.getSubGraph(), subCtx);

                    NodeRunResultBO itemBO = new NodeRunResultBO();
                    itemBO.setNodeUuid("sub-item-" + idx);
                    itemBO.setStatus(1);
                    itemBO.setOutput(Map.of("item", item, "index", idx));
                    return itemBO;
                },
                true
        );

        NodeRunResultBO resultBO = new NodeRunResultBO();
        resultBO.setNodeUuid(node.getNodeUuid());
        resultBO.setStatus(1);
        resultBO.setOutput(Map.of("subgraphCount", subResults.size(), "items", subResults));
        return resultBO;
    }

    private boolean evaluateEdgeCondition(StateGraphEdge edge, StateGraphContext context, NodeRunResultBO result) {
        String expr = edge.getConditionExpression();
        if (expr == null || expr.isBlank()) {
            return true;
        }
        // 简易布尔谓词或 SpEL 解析
        try {
            org.springframework.expression.ExpressionParser parser = new org.springframework.expression.spel.standard.SpelExpressionParser();
            org.springframework.expression.spel.support.StandardEvaluationContext evalCtx = new org.springframework.expression.spel.support.StandardEvaluationContext();
            evalCtx.setVariable("result", result);
            evalCtx.setVariable("context", context);
            Boolean val = parser.parseExpression(expr).getValue(evalCtx, Boolean.class);
            return Boolean.TRUE.equals(val);
        } catch (Exception e) {
            log.error("[Scheduler] 评估边 [{}] 条件表达式 [{}] 失败: {}", edge.getEdgeId(), expr, e.getMessage());
            return false;
        }
    }
}

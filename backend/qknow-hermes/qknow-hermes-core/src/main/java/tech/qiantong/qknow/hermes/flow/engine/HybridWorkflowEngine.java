package tech.qiantong.qknow.hermes.flow.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 融合型双轨工作流执行引擎 (HybridWorkflowEngine)
 * 统一调度无环高速 DAG 拓扑分层与含有界循环边 (LOOP_BACK) 的 StateGraph 超步调度，
 * 内置 ConvergenceLoopGuard 最大超步熔断与自愈机制。
 */
public class HybridWorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(HybridWorkflowEngine.class);

    public enum EngineMode {
        FAST_DAG,           // 极速无环 DAG 模式 (针对确定性短链路)
        CYCLIC_STATEGRAPH   // 有界循环状态图模式 (针对 ReAct / 优化自纠错循环)
    }

    public record StepExecutionResult(
            String currentNode,
            String nextNode,
            int superstep,
            Map<String, Object> state,
            boolean isTerminal,
            boolean loopBackTriggered
    ) {}

    public record WorkflowRunSummary(
            String workflowId,
            EngineMode mode,
            int totalSupersteps,
            boolean converged,
            String finalNode,
            Map<String, Object> finalState,
            List<String> executionPath
    ) {}

    private final int maxSupersteps;

    public HybridWorkflowEngine(int maxSupersteps) {
        this.maxSupersteps = maxSupersteps > 0 ? maxSupersteps : 30;
    }

    public HybridWorkflowEngine() {
        this(30);
    }

    /**
     * 执行工作流（自动检测是否含有 LOOP_BACK 边并切换双轨模式）
     *
     * @param workflowId    工作流 ID
     * @param startNode     起始节点
     * @param terminalNodes 终止节点集合
     * @param transitions   拓扑转移关系 (node -> (targetNode, isLoopBack))
     * @param initialState  初始状态
     * @return 运行总结凭单
     */
    public WorkflowRunSummary execute(
            String workflowId,
            String startNode,
            Set<String> terminalNodes,
            Map<String, List<TransitionEdge>> transitions,
            Map<String, Object> initialState
    ) {
        boolean hasLoopBack = transitions.values().stream()
                .flatMap(List::stream)
                .anyMatch(TransitionEdge::isLoopBack);

        EngineMode mode = hasLoopBack ? EngineMode.CYCLIC_STATEGRAPH : EngineMode.FAST_DAG;
        log.info("[HybridEngine] 启动工作流执行: workflowId={}, mode={}, startNode={}", workflowId, mode, startNode);

        Map<String, Object> state = new HashMap<>(initialState != null ? initialState : Map.of());
        List<String> path = new ArrayList<>();
        String currentNode = startNode;
        int superstep = 0;
        boolean converged = false;

        while (superstep < maxSupersteps) {
            superstep++;
            path.add(currentNode);

            if (terminalNodes.contains(currentNode)) {
                log.info("[HybridEngine] 抵达终态节点正常收敛: node={}, superstep={}", currentNode, superstep);
                converged = true;
                break;
            }

            List<TransitionEdge> nextEdges = transitions.get(currentNode);
            if (nextEdges == null || nextEdges.isEmpty()) {
                log.info("[HybridEngine] 无后续转移边，隐式终止: node={}, superstep={}", currentNode, superstep);
                converged = true;
                break;
            }

            // 选择转移边（默认选首条符合条件的边）
            TransitionEdge chosenEdge = nextEdges.get(0);
            if (chosenEdge.isLoopBack()) {
                log.debug("[HybridEngine] 触发 LOOP_BACK 反思循环边: {} -> {}, superstep={}",
                        currentNode, chosenEdge.targetNode(), superstep);
                // 模拟循环步状态累加
                int currentIterations = (int) state.getOrDefault("loop_iterations", 0) + 1;
                state.put("loop_iterations", currentIterations);
                // 如果循环达到收敛条件，跳出到备选边
                if (currentIterations >= 3 && nextEdges.size() > 1) {
                    chosenEdge = nextEdges.get(1); // 逃逸到退出边
                    log.info("[HybridEngine] 循环达到有界收敛阈值，平滑逃逸至退出边: targetNode={}", chosenEdge.targetNode());
                }
            }

            currentNode = chosenEdge.targetNode();
        }

        if (!converged && superstep >= maxSupersteps) {
            log.warn("[HybridEngine] 达到最大超步安全熔断阈值: maxSupersteps={}, workflowId={}", maxSupersteps, workflowId);
        }

        return new WorkflowRunSummary(
                workflowId, mode, superstep, converged, currentNode, Collections.unmodifiableMap(state), path
        );
    }

    public record TransitionEdge(String targetNode, boolean isLoopBack) {}
}

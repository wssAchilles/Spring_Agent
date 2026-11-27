package tech.qiantong.qknow.module.kb.service.flow.compiler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.DurableStateGraphEngine;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.DynamicSendPacket;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.NodeExecutionEnvelope;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;

import java.util.*;
import java.util.function.BiFunction;

/**
 * 有界受控环路状态图编译器（KbFlowStateGraphCompiler）。
 * <p>
 * 消除 {@code KbFlowServiceImpl}（一刀切禁止任何环路）与 {@code DurableStateGraphEngine} 的双轨割裂：
 * 1. 基于三色深度优先搜索（Three-Color DFS）精确识别有向回边（Back-Edges）；
 * 2. 若检测到回边但源审查节点未声明正整数 {@code maxLoopIterations} 有界终止契约，则在编译期立即阻断以防死锁；
 * 3. 若声明了合法的有界迭代上限与通过谓词键（{@code passKey}），则将回边与跳出边编译为 {@link DynamicSendPacket} 条件派发节点，
 *    无缝驱动反思自愈（Reflexion）与多步迭代检索（IRCoT）闭环。
 * </p>
 */
public class KbFlowStateGraphCompiler {

    public static final int HARD_MAX_LOOP_CEILING = 16;

    /**
     * 编译产物不可变契约
     */
    public record CompiledFlowExecution(
            String flowId,
            DurableStateGraphEngine engine,
            List<String> entryNodeUuids,
            Set<String> detectedBackEdges,
            int totalNodes
    ) {}

    @FunctionalInterface
    public interface FlowNodeStepHandler extends BiFunction<KbFlowNodeDO, Map<String, Object>, Map<String, Object>> {
    }

    /**
     * 编译低代码工作流节点与连线（含受控反思/检索回边）并注册至 {@link DurableStateGraphEngine}。
     */
    public CompiledFlowExecution compileAndRegister(
            String flowId,
            List<KbFlowNodeDO> nodes,
            List<KbFlowEdgeDO> edges,
            DurableStateGraphEngine engine,
            FlowNodeStepHandler stepHandler
    ) {
        Objects.requireNonNull(flowId, "flowId 不能为空");
        Objects.requireNonNull(nodes, "nodes 不能为空");
        Objects.requireNonNull(engine, "engine 不能为空");
        Objects.requireNonNull(stepHandler, "stepHandler 不能为空");

        Map<String, KbFlowNodeDO> nodeMap = new LinkedHashMap<>();
        Map<String, List<KbFlowEdgeDO>> outgoingByNode = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (KbFlowNodeDO n : nodes) {
            nodeMap.put(n.getUuid(), n);
            outgoingByNode.put(n.getUuid(), new ArrayList<>());
            inDegree.put(n.getUuid(), 0);
        }

        List<KbFlowEdgeDO> safeEdges = edges != null ? edges : List.of();
        for (KbFlowEdgeDO e : safeEdges) {
            if (nodeMap.containsKey(e.getSourceNodeUuid()) && nodeMap.containsKey(e.getTargetNodeUuid())) {
                outgoingByNode.get(e.getSourceNodeUuid()).add(e);
                inDegree.merge(e.getTargetNodeUuid(), 1, Integer::sum);
            }
        }

        // 1. 确定入口根节点（入度为 0 的节点）
        List<String> entryNodes = inDegree.entrySet().stream()
                .filter(en -> en.getValue() == 0)
                .map(Map.Entry::getKey)
                .toList();
        if (entryNodes.isEmpty() && !nodes.isEmpty()) {
            entryNodes = List.of(nodes.get(0).getUuid());
        }

        // 2. 三色 DFS 精确识别所有回边 (u -> v) 并强制校验有界循环契约
        Set<String> backEdgeKeys = detectBackEdges(entryNodes, nodeMap.keySet(), outgoingByNode);
        for (String edgeKey : backEdgeKeys) {
            String sourceUuid = edgeKey.split("->")[0];
            KbFlowNodeDO loopController = nodeMap.get(sourceUuid);
            int maxIter = parseMaxLoopIterations(loopController != null ? loopController.getConfig() : null);
            if (maxIter <= 0 || maxIter > HARD_MAX_LOOP_CEILING) {
                throw new IllegalArgumentException(
                        "检测到无界工作流环路回边 [" + edgeKey + "]：控制器节点 [" + sourceUuid
                                + "] 必须在 config 中显式声明 1.." + HARD_MAX_LOOP_CEILING + " 的 maxLoopIterations！");
            }
        }

        // 3. 将每个节点注册为状态图引擎的 Envelope 节点
        for (KbFlowNodeDO node : nodes) {
            String nodeId = node.getUuid();
            List<KbFlowEdgeDO> outEdges = outgoingByNode.getOrDefault(nodeId, List.of());
            boolean hasOutgoingBackEdge = outEdges.stream()
                    .anyMatch(e -> backEdgeKeys.contains(edgeKey(e.getSourceNodeUuid(), e.getTargetNodeUuid())));

            if (!hasOutgoingBackEdge) {
                List<String> staticTargets = outEdges.stream().map(KbFlowEdgeDO::getTargetNodeUuid).toList();
                engine.registerEnvelopeNode(
                        nodeId,
                        state -> true,
                        state -> {
                            Map<String, Object> updates = stepHandler.apply(node, state);
                            return NodeExecutionEnvelope.of(nodeId, updates != null ? updates : Map.of());
                        },
                        staticTargets
                );
            } else {
                // 带回边的受控循环审查节点：动态决策走 pass 正向出口还是 retry 回边
                int maxIterations = parseMaxLoopIterations(node.getConfig());
                String passKey = parsePassKey(node.getConfig());
                String loopCounterKey = "__loop_count_" + nodeId;

                List<String> retryTargets = outEdges.stream()
                        .filter(e -> backEdgeKeys.contains(edgeKey(e.getSourceNodeUuid(), e.getTargetNodeUuid()))
                                || "retry".equalsIgnoreCase(e.getSourceHandle()))
                        .map(KbFlowEdgeDO::getTargetNodeUuid)
                        .distinct()
                        .toList();
                List<String> forwardTargets = outEdges.stream()
                        .filter(e -> !backEdgeKeys.contains(edgeKey(e.getSourceNodeUuid(), e.getTargetNodeUuid()))
                                && !"retry".equalsIgnoreCase(e.getSourceHandle()))
                        .map(KbFlowEdgeDO::getTargetNodeUuid)
                        .distinct()
                        .toList();

                engine.registerEnvelopeNode(
                        nodeId,
                        state -> true,
                        state -> {
                            Map<String, Object> rawOut = stepHandler.apply(node, state);
                            Map<String, Object> mergedUpdates = new LinkedHashMap<>(rawOut != null ? rawOut : Map.of());
                            int prevIter = ((Number) state.getOrDefault(loopCounterKey, 0)).intValue();
                            int currentIter = prevIter + 1;
                            mergedUpdates.put(loopCounterKey, currentIter);

                            Object passVal = mergedUpdates.containsKey(passKey)
                                    ? mergedUpdates.get(passKey)
                                    : state.get(passKey);
                            boolean passed = Boolean.TRUE.equals(passVal);

                            List<String> chosenTargets = (passed || currentIter >= maxIterations)
                                    ? forwardTargets
                                    : retryTargets;
                            List<DynamicSendPacket> sends = chosenTargets.stream()
                                    .map(target -> DynamicSendPacket.of(target, Map.of()))
                                    .toList();
                            return NodeExecutionEnvelope.of(nodeId, mergedUpdates, sends);
                        },
                        List.of()
                );
            }
        }

        return new CompiledFlowExecution(flowId, engine, entryNodes, Collections.unmodifiableSet(backEdgeKeys), nodes.size());
    }

    /**
     * 驱动已编译的有环受控状态图运行至终态（带全局超步安全上限防逃逸）。
     */
    public Map<String, Object> runToCompletion(
            CompiledFlowExecution compiled,
            String executionId,
            Map<String, Object> initialState,
            int maxSupersteps
    ) {
        return runToCompletion(compiled.engine(), compiled, executionId, initialState, maxSupersteps);
    }

    public Map<String, Object> runToCompletion(
            DurableStateGraphEngine engine,
            CompiledFlowExecution compiled,
            String executionId,
            Map<String, Object> initialState,
            int maxSupersteps
    ) {
        engine.initializeExecution(executionId, "main", initialState, compiled.entryNodeUuids());
        int limit = Math.max(1, maxSupersteps);
        DurableStateGraphEngine.SuperstepExecutionResult last = null;
        for (int step = 1; step <= limit; step++) {
            last = engine.advanceSuperstep(executionId, "main");
            if (last.completed()) {
                return last.mutatedState();
            }
        }
        return last != null ? last.mutatedState() : Map.of();
    }

    private static Set<String> detectBackEdges(
            List<String> roots,
            Set<String> allNodes,
            Map<String, List<KbFlowEdgeDO>> adj
    ) {
        // 0 = WHITE, 1 = GRAY (in recursion stack), 2 = BLACK
        Map<String, Integer> color = new HashMap<>();
        allNodes.forEach(n -> color.put(n, 0));
        Set<String> backEdges = new LinkedHashSet<>();

        for (String root : roots) {
            if (color.getOrDefault(root, 0) == 0) {
                dfs(root, adj, color, backEdges);
            }
        }
        for (String n : allNodes) {
            if (color.getOrDefault(n, 0) == 0) {
                dfs(n, adj, color, backEdges);
            }
        }
        return backEdges;
    }

    private static void dfs(
            String u,
            Map<String, List<KbFlowEdgeDO>> adj,
            Map<String, Integer> color,
            Set<String> backEdges
    ) {
        color.put(u, 1);
        for (KbFlowEdgeDO e : adj.getOrDefault(u, List.of())) {
            String v = e.getTargetNodeUuid();
            int c = color.getOrDefault(v, 0);
            if (c == 1) {
                backEdges.add(edgeKey(u, v));
            } else if (c == 0) {
                dfs(v, adj, color, backEdges);
            }
        }
        color.put(u, 2);
    }

    private static String edgeKey(String u, String v) {
        return u + "->" + v;
    }

    private static int parseMaxLoopIterations(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return 0;
        }
        try {
            JSONObject obj = JSON.parseObject(configJson);
            return obj != null ? obj.getIntValue("maxLoopIterations", 0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String parsePassKey(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return "loopPassed";
        }
        try {
            JSONObject obj = JSON.parseObject(configJson);
            String key = obj != null ? obj.getString("passKey") : null;
            return (key == null || key.isBlank()) ? "loopPassed" : key;
        } catch (Exception e) {
            return "loopPassed";
        }
    }
}

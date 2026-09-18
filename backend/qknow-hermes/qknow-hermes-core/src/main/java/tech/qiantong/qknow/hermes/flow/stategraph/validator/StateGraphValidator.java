package tech.qiantong.qknow.hermes.flow.stategraph.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.stategraph.enums.StateGraphEdgeType;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraph;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphEdge;
import tech.qiantong.qknow.hermes.flow.stategraph.model.StateGraphNode;

import java.util.*;

/**
 * 状态图静态拓扑校验器 (StateGraphValidator)
 * 拦截隐式未声明的非法循环环路，校验显式回跳边收敛参数
 */
@Slf4j
@Component
public class StateGraphValidator {

    /**
     * 校验状态图合法性
     *
     * @param graph 状态图拓扑定义
     * @throws IllegalArgumentException 拓扑非法时抛出
     */
    public void validate(StateGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("[Validator] 状态图不能为 null (ERR_GRAPH_INVALID_TOPOLOGY)");
        }
        if (graph.getNodeMap() == null || graph.getNodeMap().isEmpty()) {
            throw new IllegalArgumentException("[Validator] 状态图节点集合不能为空 (ERR_GRAPH_INVALID_TOPOLOGY)");
        }
        if (graph.getStartNodeUuids() == null || graph.getStartNodeUuids().isEmpty()) {
            throw new IllegalArgumentException("[Validator] 状态图必须声明至少一个起始节点 (ERR_GRAPH_INVALID_TOPOLOGY)");
        }

        // 1. 校验起始节点存在性
        for (String startId : graph.getStartNodeUuids()) {
            if (!graph.getNodeMap().containsKey(startId)) {
                throw new IllegalArgumentException(String.format(
                        "[Validator] 起始节点 [%s] 未在节点集合中定义 (ERR_GRAPH_INVALID_TOPOLOGY)", startId));
            }
        }

        // 2. 校验边引用的节点有效性与 LOOP_BACK 参数
        List<StateGraphEdge> forwardEdges = new ArrayList<>();
        if (graph.getEdges() != null) {
            for (StateGraphEdge edge : graph.getEdges()) {
                if (!graph.getNodeMap().containsKey(edge.getSourceNodeUuid())) {
                    throw new IllegalArgumentException(String.format(
                            "[Validator] 边 [%s] 的源节点 [%s] 不存在", edge.getEdgeId(), edge.getSourceNodeUuid()));
                }
                if (!graph.getNodeMap().containsKey(edge.getTargetNodeUuid())) {
                    throw new IllegalArgumentException(String.format(
                            "[Validator] 边 [%s] 的目标节点 [%s] 不存在", edge.getEdgeId(), edge.getTargetNodeUuid()));
                }

                if (edge.getEdgeType() == StateGraphEdgeType.LOOP_BACK) {
                    if (edge.getMaxIterations() < 1 || edge.getMaxIterations() > 50) {
                        throw new IllegalArgumentException(String.format(
                                "[Validator] 回跳边 [%s] 的 maxIterations 参数 [%d] 必须在 [1, 50] 之间",
                                edge.getEdgeId(), edge.getMaxIterations()));
                    }
                } else {
                    forwardEdges.add(edge);
                }
            }
        }

        // 3. 拦截隐式环路：前向边集合必须构成严格有向无环图 (DAG)
        if (hasCycleInForwardEdges(graph.getNodeMap().keySet(), forwardEdges)) {
            throw new IllegalArgumentException(
                    "[Validator] 图中存在未显式声明为 LOOP_BACK 的隐式环路拓扑 (ERR_GRAPH_INVALID_TOPOLOGY)");
        }

        log.debug("[Validator] 状态图 [{}] 拓扑结构校验通过, 包含 {} 个节点, {} 条边",
                graph.getGraphId(), graph.getNodeMap().size(), graph.getEdges().size());
    }

    /**
     * 基于 Kahn 算法检测前向边集合是否存在环路
     */
    private boolean hasCycleInForwardEdges(Set<String> nodeIds, List<StateGraphEdge> edges) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjList = new HashMap<>();

        for (String id : nodeIds) {
            inDegree.put(id, 0);
            adjList.put(id, new ArrayList<>());
        }

        for (StateGraphEdge edge : edges) {
            String src = edge.getSourceNodeUuid();
            String tgt = edge.getTargetNodeUuid();
            inDegree.put(tgt, inDegree.getOrDefault(tgt, 0) + 1);
            adjList.get(src).add(tgt);
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        int visitedCount = 0;
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            visitedCount++;

            for (String neighbor : adjList.get(curr)) {
                int newDeg = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDeg);
                if (newDeg == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        return visitedCount != nodeIds.size();
    }
}

package tech.qiantong.qknow.ai.audit.causal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全链路因果可解释性拓扑溯源图核心引擎 (CausalAttributionGraph)
 *
 * 贯穿 Query -> Intent Decomposition -> Knowledge Retained -> BFT Consensus -> Final Output，
 * 支持逆向因果回溯 (Backward Attribution Traversal) 与知识切片归因权重计算。
 *
 * @author qknow
 */
@Slf4j
@Component
public class CausalAttributionGraph {

    private final Map<String, CausalTraceNode> nodes = new ConcurrentHashMap<>();
    private final List<CausalEdge> edges = Collections.synchronizedList(new ArrayList<>());

    public void addNode(CausalTraceNode node) {
        if (node != null) {
            nodes.put(node.nodeId(), node);
        }
    }

    public void addEdge(String sourceId, String targetId, String relationType, double weight) {
        edges.add(new CausalEdge(sourceId, targetId, relationType, weight));
    }

    public CausalTraceNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    /**
     * 从指定目标节点反向逆向回溯完整的因果依赖路径
     */
    public List<CausalTraceNode> getBackwardAttributionPath(String targetNodeId) {
        List<CausalTraceNode> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();

        queue.add(targetNodeId);
        visited.add(targetNodeId);

        while (!queue.isEmpty()) {
            String currId = queue.poll();
            CausalTraceNode node = nodes.get(currId);
            if (node != null) {
                path.add(node);
            }

            for (CausalEdge edge : edges) {
                if (edge.targetId().equals(currId) && !visited.contains(edge.sourceId())) {
                    visited.add(edge.sourceId());
                    queue.add(edge.sourceId());
                }
            }
        }
        return path;
    }

    /**
     * 导出标准轻量级拓扑可视化字典
     */
    public Map<String, Object> exportTopologyJson() {
        Map<String, Object> json = new HashMap<>();
        json.put("nodes", new ArrayList<>(nodes.values()));
        json.put("edges", new ArrayList<>(edges));
        json.put("totalNodes", nodes.size());
        json.put("totalEdges", edges.size());
        return json;
    }

    /**
     * 清空当前拓扑状态
     */
    public void clear() {
        nodes.clear();
        edges.clear();
    }
}

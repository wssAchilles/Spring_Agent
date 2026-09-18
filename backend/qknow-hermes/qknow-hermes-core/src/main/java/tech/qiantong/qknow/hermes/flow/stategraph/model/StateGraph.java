package tech.qiantong.qknow.hermes.flow.stategraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * 状态图完整拓扑模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateGraph {

    /**
     * 图 ID
     */
    private String graphId;

    /**
     * 图名称
     */
    private String graphName;

    /**
     * 全局最大超步限制（防发散与全局死锁硬熔断，默认 50 步）
     */
    @Builder.Default
    private int maxSupersteps = 50;

    /**
     * 节点映射：nodeUuid -> StateGraphNode
     */
    @Builder.Default
    private Map<String, StateGraphNode> nodeMap = new LinkedHashMap<>();

    /**
     * 边列表
     */
    @Builder.Default
    private List<StateGraphEdge> edges = new ArrayList<>();

    /**
     * 起始节点 UUID 集合
     */
    @Builder.Default
    private Set<String> startNodeUuids = new LinkedHashSet<>();

    public void addNode(StateGraphNode node) {
        if (nodeMap == null) {
            nodeMap = new LinkedHashMap<>();
        }
        nodeMap.put(node.getNodeUuid(), node);
    }

    public void addEdge(StateGraphEdge edge) {
        if (edges == null) {
            edges = new ArrayList<>();
        }
        edges.add(edge);
    }

    public void addStartNode(String nodeUuid) {
        if (startNodeUuids == null) {
            startNodeUuids = new LinkedHashSet<>();
        }
        startNodeUuids.add(nodeUuid);
    }

    public StateGraphNode getNode(String nodeUuid) {
        return nodeMap != null ? nodeMap.get(nodeUuid) : null;
    }

    public List<StateGraphEdge> getOutgoingEdges(String nodeUuid) {
        if (edges == null || edges.isEmpty()) {
            return Collections.emptyList();
        }
        return edges.stream()
                .filter(e -> Objects.equals(e.getSourceNodeUuid(), nodeUuid))
                .toList();
    }
}

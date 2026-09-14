package tech.qiantong.qknow.hermes.memory.graph;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.memory.model.EpisodicEdge;
import tech.qiantong.qknow.hermes.memory.model.MemoryNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 时序情境图谱服务实现
 * 严格落实 Theorem 2.1 时态因果一致性与版本偏序无环定理
 */
@Slf4j
public class EpisodicGraphServiceImpl implements EpisodicGraphService {

    private final Map<String, MemoryNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, List<EpisodicEdge>> outgoingEdges = new ConcurrentHashMap<>();
    private final Map<String, List<EpisodicEdge>> incomingEdges = new ConcurrentHashMap<>();

    @Override
    public void addNode(MemoryNode node) {
        if (node != null && node.getId() != null) {
            nodes.put(node.getId(), node);
        }
    }

    @Override
    public void addEdge(EpisodicEdge edge) {
        if (edge == null || edge.getSourceId() == null || edge.getTargetId() == null) {
            return;
        }
        outgoingEdges.computeIfAbsent(edge.getSourceId(), k -> new CopyOnWriteArrayList<>()).add(edge);
        incomingEdges.computeIfAbsent(edge.getTargetId(), k -> new CopyOnWriteArrayList<>()).add(edge);
    }

    @Override
    public void recordSupersede(String oldMemoryId, String newMemoryId, long timestamp) {
        if (oldMemoryId == null || newMemoryId == null) {
            return;
        }

        // 1. 标记旧记忆状态并截断终止时间
        MemoryNode oldNode = nodes.get(oldMemoryId);
        if (oldNode != null) {
            oldNode.getMetadata().put("status", "SUPERSEDED");
            oldNode.getMetadata().put("validTo", timestamp);
            oldNode.getMetadata().put("supersededBy", newMemoryId);
        }

        // 2. 截断关联旧边的 validTo
        List<EpisodicEdge> oldOuts = outgoingEdges.get(oldMemoryId);
        if (oldOuts != null) {
            for (EpisodicEdge e : oldOuts) {
                if (e.getValidTo() == null || e.getValidTo() == 0L || e.getValidTo() > timestamp) {
                    e.setValidTo(timestamp);
                }
            }
        }

        // 3. 建立从新偏好指向旧偏好的 [:SUPERSEDES] 因果边
        EpisodicEdge supersedeEdge = EpisodicEdge.builder()
                .sourceId(newMemoryId)
                .targetId(oldMemoryId)
                .relationType(EpisodicEdge.RelationType.SUPERSEDES)
                .validFrom(timestamp)
                .validTo(null)
                .weight(1.0)
                .build();
        addEdge(supersedeEdge);

        log.info("Recorded supersede: {} -> {} at timestamp {}", newMemoryId, oldMemoryId, timestamp);
    }

    @Override
    public List<MemoryNode> queryActiveNodes(String userId, long queryTime) {
        if (userId == null) {
            return Collections.emptyList();
        }

        List<MemoryNode> activeList = new ArrayList<>();
        for (MemoryNode node : nodes.values()) {
            if (!userId.equals(node.getUserId())) {
                continue;
            }
            if (node.getTimestamp() > queryTime) {
                continue; // 尚未发生
            }

            // 检查在 queryTime 是否已被 SUPERSEDES 废弃
            boolean isSuperseded = false;
            List<EpisodicEdge> inc = incomingEdges.get(node.getId());
            if (inc != null) {
                for (EpisodicEdge edge : inc) {
                    if (edge.getRelationType() == EpisodicEdge.RelationType.SUPERSEDES
                            && edge.getValidFrom() <= queryTime) {
                        isSuperseded = true;
                        break;
                    }
                }
            }

            if (!isSuperseded) {
                activeList.add(node);
            }
        }

        return activeList;
    }

    @Override
    public boolean hasCycleInSupersedesChain() {
        // 构建仅包含 SUPERSEDES 边的子图进行拓扑环路检测
        Map<String, List<String>> adj = new HashMap<>();
        Set<String> allNodes = new HashSet<>();

        for (List<EpisodicEdge> edges : outgoingEdges.values()) {
            for (EpisodicEdge edge : edges) {
                if (edge.getRelationType() == EpisodicEdge.RelationType.SUPERSEDES) {
                    adj.computeIfAbsent(edge.getSourceId(), k -> new ArrayList<>()).add(edge.getTargetId());
                    allNodes.add(edge.getSourceId());
                    allNodes.add(edge.getTargetId());
                }
            }
        }

        Map<String, Integer> visited = new HashMap<>(); // 0: unvisited, 1: visiting, 2: visited
        for (String node : allNodes) {
            visited.put(node, 0);
        }

        for (String node : allNodes) {
            if (visited.get(node) == 0) {
                if (dfsCycle(node, adj, visited)) {
                    return true; // 发现环
                }
            }
        }
        return false;
    }

    private boolean dfsCycle(String curr, Map<String, List<String>> adj, Map<String, Integer> visited) {
        visited.put(curr, 1); // visiting
        List<String> neighbors = adj.get(curr);
        if (neighbors != null) {
            for (String nxt : neighbors) {
                Integer state = visited.get(nxt);
                if (state != null && state == 1) {
                    return true; // 遇到回边，成环
                }
                if (state != null && state == 0) {
                    if (dfsCycle(nxt, adj, visited)) {
                        return true;
                    }
                }
            }
        }
        visited.put(curr, 2); // visited
        return false;
    }

    @Override
    public List<MemoryNode> traverse2HopWithDegreeCutoff(String seedNodeId, int hop1Limit, int hop2Limit, long timeoutMs) {
        long startTime = System.currentTimeMillis();
        if (seedNodeId == null || !nodes.containsKey(seedNodeId)) {
            return Collections.emptyList();
        }

        Set<String> visited = new HashSet<>();
        visited.add(seedNodeId);
        List<MemoryNode> results = new ArrayList<>();

        // 第 1 跳遍历与截断
        List<EpisodicEdge> hop1Edges = outgoingEdges.get(seedNodeId);
        List<String> hop1Nodes = new ArrayList<>();
        if (hop1Edges != null) {
            int count = 0;
            for (EpisodicEdge edge : hop1Edges) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    log.warn("Traverse 2-Hop timed out at Hop 1 (> {} ms)", timeoutMs);
                    return results;
                }
                if (count >= hop1Limit) {
                    break; // 度数截断
                }
                String target = edge.getTargetId();
                if (visited.add(target)) {
                    hop1Nodes.add(target);
                    MemoryNode n = nodes.get(target);
                    if (n != null) {
                        results.add(n);
                    }
                    count++;
                }
            }
        }

        // 第 2 跳遍历与截断
        for (String h1Node : hop1Nodes) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                log.warn("Traverse 2-Hop timed out at Hop 2 (> {} ms)", timeoutMs);
                return results;
            }
            List<EpisodicEdge> hop2Edges = outgoingEdges.get(h1Node);
            if (hop2Edges != null) {
                int count = 0;
                for (EpisodicEdge edge : hop2Edges) {
                    if (System.currentTimeMillis() - startTime > timeoutMs) {
                        log.warn("Traverse 2-Hop timed out during Hop 2 expansion (> {} ms)", timeoutMs);
                        return results;
                    }
                    if (count >= hop2Limit) {
                        break; // 度数截断
                    }
                    String target = edge.getTargetId();
                    if (visited.add(target)) {
                        MemoryNode n = nodes.get(target);
                        if (n != null) {
                            results.add(n);
                        }
                        count++;
                    }
                }
            }
        }

        return results;
    }

    @Override
    public MemoryNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    @Override
    public List<EpisodicEdge> getOutgoingEdges(String nodeId) {
        List<EpisodicEdge> list = outgoingEdges.get(nodeId);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    @Override
    public void removeNode(String nodeId) {
        if (nodeId == null) {
            return;
        }
        nodes.remove(nodeId);
        outgoingEdges.remove(nodeId);
        incomingEdges.remove(nodeId);
    }
}

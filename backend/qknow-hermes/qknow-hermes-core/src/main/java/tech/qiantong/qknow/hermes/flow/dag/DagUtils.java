package tech.qiantong.qknow.hermes.flow.dag;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowEdgeDO;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowNodeDO;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DAG 工具类
 * 提供拓扑排序、环检测、并行分组等功能
 */
@Slf4j
public class DagUtils {

    /**
     * 拓扑排序 (Kahn 算法)
     *
     * @param nodes 节点列表
     * @param edges 边列表
     * @return 拓扑排序后的节点 UUID 列表
     * @throws IllegalStateException 如果存在环
     */
    public static List<String> topologicalSort(List<KbFlowNodeDO> nodes, List<KbFlowEdgeDO> edges) {
        // 构建邻接表和入度表
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        // 初始化所有节点
        for (KbFlowNodeDO node : nodes) {
            adjacency.put(node.getUuid(), new LinkedHashSet<>());
            inDegree.put(node.getUuid(), 0);
        }

        // 构建边
        for (KbFlowEdgeDO edge : edges) {
            String source = edge.getSourceNodeUuid();
            String target = edge.getTargetNodeUuid();
            if (adjacency.containsKey(source) && adjacency.containsKey(target)) {
                adjacency.get(source).add(target);
                inDegree.put(target, inDegree.get(target) + 1);
            }
        }

        // Kahn 算法
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);

            for (String neighbor : adjacency.get(node)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        // 检测环
        if (sorted.size() != nodes.size()) {
            Set<String> cycleNodes = new HashSet<>(inDegree.keySet());
            cycleNodes.removeAll(sorted);
            throw new IllegalStateException("DAG 中存在环，涉及节点: " + cycleNodes);
        }

        return sorted;
    }

    /**
     * 检测 DAG 是否存在环
     */
    public static boolean hasCycle(List<KbFlowNodeDO> nodes, List<KbFlowEdgeDO> edges) {
        try {
            topologicalSort(nodes, edges);
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /**
     * 获取并行执行分组
     * 同一组的节点可以并行执行，不同组之间有依赖关系
     *
     * @param nodes 节点列表
     * @param edges 边列表
     * @return 分组列表，每组包含可以并行执行的节点 UUID
     */
    public static List<List<String>> getParallelGroups(List<KbFlowNodeDO> nodes, List<KbFlowEdgeDO> edges) {
        // 构建入度表
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();

        for (KbFlowNodeDO node : nodes) {
            adjacency.put(node.getUuid(), new LinkedHashSet<>());
            inDegree.put(node.getUuid(), 0);
        }

        for (KbFlowEdgeDO edge : edges) {
            String source = edge.getSourceNodeUuid();
            String target = edge.getTargetNodeUuid();
            if (adjacency.containsKey(source) && adjacency.containsKey(target)) {
                adjacency.get(source).add(target);
                inDegree.put(target, inDegree.get(target) + 1);
            }
        }

        // BFS 分层
        List<List<String>> groups = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();

        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        while (!queue.isEmpty()) {
            int size = queue.size();
            List<String> group = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                String node = queue.poll();
                group.add(node);

                for (String neighbor : adjacency.get(node)) {
                    int newDegree = inDegree.get(neighbor) - 1;
                    inDegree.put(neighbor, newDegree);
                    if (newDegree == 0) {
                        queue.offer(neighbor);
                    }
                }
            }
            if (!group.isEmpty()) {
                groups.add(group);
            }
        }

        return groups;
    }

    /**
     * 获取节点的前驱节点
     */
    public static Set<String> getPredecessors(String nodeUuid, List<KbFlowEdgeDO> edges) {
        return edges.stream()
                .filter(e -> e.getTargetNodeUuid().equals(nodeUuid))
                .map(KbFlowEdgeDO::getSourceNodeUuid)
                .collect(Collectors.toSet());
    }

    /**
     * 获取节点的后继节点
     */
    public static Set<String> getSuccessors(String nodeUuid, List<KbFlowEdgeDO> edges) {
        return edges.stream()
                .filter(e -> e.getSourceNodeUuid().equals(nodeUuid))
                .map(KbFlowEdgeDO::getTargetNodeUuid)
                .collect(Collectors.toSet());
    }

    /**
     * 递归计算条件分支未命中时应剪枝（SKIPPED）的下游节点集合
     *
     * @param conditionNodeUuid 条件节点 UUID
     * @param selectedNextNodeIds 选中的后继节点 UUID 集合
     * @param edges 边列表
     * @return 应当被标记为 SKIPPED 的节点 UUID 集合
     */
    public static Set<String> computePrunedNodes(String conditionNodeUuid,
                                                List<String> selectedNextNodeIds,
                                                List<KbFlowEdgeDO> edges) {
        Set<String> selectedSet = selectedNextNodeIds != null ? new HashSet<>(selectedNextNodeIds) : Collections.emptySet();

        // 1. 获取条件节点的所有直接后继节点
        Set<String> allDirectSuccessors = getSuccessors(conditionNodeUuid, edges);

        // 2. 未选中的直接后继节点作为剪枝根
        Set<String> unselectedDirectSuccessors = new HashSet<>(allDirectSuccessors);
        unselectedDirectSuccessors.removeAll(selectedSet);

        if (unselectedDirectSuccessors.isEmpty()) {
            return Collections.emptySet();
        }

        // 3. 从未选中的分支根节点开始，递归向下标记下游可达节点
        Set<String> prunedNodes = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>(unselectedDirectSuccessors);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (prunedNodes.add(current)) {
                Set<String> nextSuccessors = getSuccessors(current, edges);
                for (String next : nextSuccessors) {
                    if (!selectedSet.contains(next)) {
                        queue.offer(next);
                    }
                }
            }
        }

        return prunedNodes;
    }
}

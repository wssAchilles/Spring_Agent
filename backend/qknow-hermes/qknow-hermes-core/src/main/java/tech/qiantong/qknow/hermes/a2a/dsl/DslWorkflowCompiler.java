package tech.qiantong.qknow.hermes.a2a.dsl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.agent.dag.DagTaskNode;
import tech.qiantong.qknow.hermes.agent.dag.PhasedExecutionPlan;

import java.util.*;

/**
 * 声明式工作流三阶编译门禁器（定理 1.1 Kahn 算法拓扑排查与分层规划）
 */
@Component
public class DslWorkflowCompiler {

    private static final Logger log = LoggerFactory.getLogger(DslWorkflowCompiler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkflowDefinition parseJson(String json) throws Exception {
        return objectMapper.readValue(json, WorkflowDefinition.class);
    }

    /**
     * 核心编译入口：执行三阶门禁并输出拓扑分层执行计划
     */
    public PhasedExecutionPlan compile(WorkflowDefinition definition) {
        if (definition == null || definition.nodes().isEmpty()) {
            return new PhasedExecutionPlan(List.of(), 0);
        }

        // 门禁 1: Schema 校验（节点非空与唯一性）
        Map<String, WorkflowNode> nodeMap = new LinkedHashMap<>();
        for (WorkflowNode node : definition.nodes()) {
            if (node.nodeId() == null || node.nodeId().isBlank()) {
                throw new IllegalArgumentException("Workflow 包含空 nodeId 的节点");
            }
            if (nodeMap.containsKey(node.nodeId())) {
                throw new IllegalArgumentException("Workflow 包含重复 nodeId: " + node.nodeId());
            }
            nodeMap.put(node.nodeId(), node);
        }

        // 构建邻接表与入度表
        Map<String, List<String>> adjList = new HashMap<>();
        Map<String, Set<String>> reverseDeps = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (String id : nodeMap.keySet()) {
            adjList.put(id, new ArrayList<>());
            reverseDeps.put(id, new LinkedHashSet<>());
            inDegree.put(id, 0);
        }

        for (WorkflowEdge edge : definition.edges()) {
            if (!nodeMap.containsKey(edge.fromNodeId()) || !nodeMap.containsKey(edge.toNodeId())) {
                throw new IllegalArgumentException("Edge 引用了未定义的节点: " + edge.fromNodeId() + " -> " + edge.toNodeId());
            }
            adjList.get(edge.fromNodeId()).add(edge.toNodeId());
            reverseDeps.get(edge.toNodeId()).add(edge.fromNodeId());
            inDegree.put(edge.toNodeId(), inDegree.get(edge.toNodeId()) + 1);
        }

        // 门禁 2: 定理 1.1 Kahn 拓扑排序无环排查与多层分级提取
        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<List<DagTaskNode>> phases = new ArrayList<>();
        int visitedCount = 0;

        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            List<DagTaskNode> currentPhaseNodes = new ArrayList<>();

            List<String> currentLevelIds = new ArrayList<>();
            for (int i = 0; i < levelSize; i++) {
                String u = queue.poll();
                currentLevelIds.add(u);
                visitedCount++;

                WorkflowNode wn = nodeMap.get(u);
                DagTaskNode dagNode = new DagTaskNode(
                        wn.nodeId(),
                        wn.objective() != null ? wn.objective() : wn.name(),
                        wn.requiredCapability() != null ? wn.requiredCapability() : "GENERAL",
                        List.copyOf(reverseDeps.get(u)),
                        wn.timeoutSeconds(),
                        Map.of()
                );
                currentPhaseNodes.add(dagNode);
            }

            phases.add(currentPhaseNodes);

            // 消除当前层的边，触发下一层入度为 0 节点入队
            for (String u : currentLevelIds) {
                for (String v : adjList.get(u)) {
                    int updated = inDegree.get(v) - 1;
                    inDegree.put(v, updated);
                    if (updated == 0) {
                        queue.add(v);
                    }
                }
            }
        }

        // 拓扑环路判定（定理 1.1 必要性）
        if (visitedCount < nodeMap.size()) {
            List<String> cycleNodes = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() > 0) {
                    cycleNodes.add(entry.getKey());
                }
            }
            log.error("[DSL Compiler] 检测到拓扑依赖环路死锁! 涉案节点: {}", cycleNodes);
            throw new DslCyclicDependencyException("工作流拓扑中存在循环依赖死锁 (Cycle detected in nodes: " + cycleNodes + ")", cycleNodes);
        }

        log.info("[DSL Compiler] 工作流 [{}] 编译成功，分层阶段数: {}, 总节点数: {}",
                definition.workflowId(), phases.size(), visitedCount);
        return new PhasedExecutionPlan(phases, visitedCount);
    }
}

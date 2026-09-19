package tech.qiantong.qknow.hermes.a2a.dsl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.a2a.card.AgentCard;
import tech.qiantong.qknow.hermes.a2a.card.AgentMeshRegistry;
import tech.qiantong.qknow.hermes.a2a.dsl.exception.DependencyUnsatisfiedException;
import tech.qiantong.qknow.hermes.a2a.dsl.exception.DslSyntaxValidationException;
import tech.qiantong.qknow.hermes.a2a.dsl.exception.UnboundedCycleException;
import tech.qiantong.qknow.hermes.agent.dag.DagTaskNode;
import tech.qiantong.qknow.hermes.agent.dag.PhasedExecutionPlan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 声明式工作流三阶静态安全门禁器
 * 1. 一级门禁：语法结构与 JSON/YAML Schema 约束
 * 2. 二级门禁：Tarjan 强连通分量 (SCC) 分析与有界循环硬熔断 (max_iterations <= 10)
 * 3. 三级门禁：外部 MCP 工具在线存活与千问 1536 维超球面流形几何断言
 */
@Component
public class ThreeStageStaticSafetyGate {

    private static final Logger log = LoggerFactory.getLogger(ThreeStageStaticSafetyGate.class);
    private static final Pattern NODE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final AgentMeshRegistry meshRegistry;
    private McpToolAvailabilityChecker mcpChecker = toolName -> true; // 默认放行，支持外部重设/注入

    public interface McpToolAvailabilityChecker {
        boolean isToolAvailable(String toolName);
    }

    public record CompilationResult(
            PhasedExecutionPlan executionPlan,
            DslWorkflowCompilationReceipt receipt
    ) {}

    public ThreeStageStaticSafetyGate(AgentMeshRegistry meshRegistry) {
        this.meshRegistry = meshRegistry;
    }

    public void setMcpToolAvailabilityChecker(McpToolAvailabilityChecker checker) {
        if (checker != null) {
            this.mcpChecker = checker;
        }
    }

    /**
     * 执行完整三阶门禁编译，返回不可变执行计划与带有 SHA-256 签名的编译凭单
     */
    public CompilationResult compileAndAssert(DslWorkflowDefinition definition) {
        List<String> gateReports = new ArrayList<>();

        // 门禁一：一级语法与 Schema 约束
        validateStage1Syntax(definition);
        gateReports.add("STAGE_1_SYNTAX_PASS: Schema合法, 节点总数=" + definition.nodes().size() + ", 边总数=" + definition.edges().size());

        // 门禁二：二级有界环路与拓扑合法性校验（Tarjan SCC 分析）
        PhasedExecutionPlan plan = validateStage2Topology(definition);
        gateReports.add("STAGE_2_TOPOLOGY_PASS: Tarjan SCC拓扑分析完成, 有界循环已校验, 执行阶段数=" + plan.phases().size());

        // 门禁三：三级外部依赖与能力存活断言
        validateStage3Dependencies(definition);
        gateReports.add("STAGE_3_DEPENDENCY_PASS: MCP工具存活核验通过, AgentMesh能力与千问1536维超球面流形核验通过");

        // 计算不可变凭单签名元数据
        String astDigest = computeDigest(definition.workflowId() + ":" + definition.version() + ":" + definition.nodes().size());
        String topologyHash = computeDigest(definition.edges().stream().map(e -> e.fromNodeId() + "->" + e.toNodeId()).sorted().toList().toString());

        DslWorkflowCompilationReceipt receipt = DslWorkflowCompilationReceipt.create(
                definition.workflowId(),
                definition.version(),
                astDigest,
                topologyHash,
                gateReports
        );

        log.info("[Static Safety Gate] 工作流 [{}] (v{}) 三阶门禁全量通过, 凭单 ID: {}",
                definition.workflowId(), definition.version(), receipt.receiptId());

        return new CompilationResult(plan, receipt);
    }

    /**
     * 门禁一：语法结构合法性校验
     */
    public void validateStage1Syntax(DslWorkflowDefinition def) {
        if (def == null) {
            throw new DslSyntaxValidationException("工作流规约定义不能为空 (null)");
        }
        if (def.workflowId() == null || def.workflowId().isBlank()) {
            throw new DslSyntaxValidationException("工作流 workflowId 不能为空");
        }
        if (def.nodes() == null || def.nodes().isEmpty()) {
            throw new DslSyntaxValidationException("工作流必须包含至少一个节点");
        }

        Set<String> nodeIds = new HashSet<>();
        for (DslWorkflowNode node : def.nodes()) {
            if (node.nodeId() == null || node.nodeId().isBlank()) {
                throw new DslSyntaxValidationException("节点包含空 nodeId");
            }
            if (!NODE_ID_PATTERN.matcher(node.nodeId()).matches()) {
                throw new DslSyntaxValidationException("节点 nodeId 格式不符合规范 ^[a-zA-Z0-9_-]{1,64}$: " + node.nodeId());
            }
            if (!nodeIds.add(node.nodeId())) {
                throw new DslSyntaxValidationException("工作流包含重复的 nodeId: " + node.nodeId());
            }
            if (node.timeoutSeconds() > 3600) {
                throw new DslSyntaxValidationException("节点超时时间不能超过 3600 秒: " + node.nodeId());
            }
        }

        for (DslWorkflowEdge edge : def.edges()) {
            if (edge.fromNodeId() == null || edge.toNodeId() == null) {
                throw new DslSyntaxValidationException("边定义包含空节点引用: " + edge.edgeId());
            }
            if (!nodeIds.contains(edge.fromNodeId())) {
                throw new DslSyntaxValidationException("边引用了不存在的起始节点: " + edge.fromNodeId());
            }
            if (!nodeIds.contains(edge.toNodeId())) {
                throw new DslSyntaxValidationException("边引用了不存在的目标节点: " + edge.toNodeId());
            }
        }
    }

    /**
     * 门禁二：Tarjan 强连通分量 (SCC) 分析与有界循环硬熔断
     */
    public PhasedExecutionPlan validateStage2Topology(DslWorkflowDefinition def) {
        Map<String, DslWorkflowNode> nodeMap = new LinkedHashMap<>();
        for (DslWorkflowNode n : def.nodes()) {
            nodeMap.put(n.nodeId(), n);
        }

        // 构建邻接表
        Map<String, List<DslWorkflowEdge>> outEdges = new HashMap<>();
        for (String id : nodeMap.keySet()) {
            outEdges.put(id, new ArrayList<>());
        }
        for (DslWorkflowEdge edge : def.edges()) {
            outEdges.get(edge.fromNodeId()).add(edge);
        }

        // 1. Tarjan SCC 算法
        TarjanSccDetector detector = new TarjanSccDetector(nodeMap.keySet(), outEdges);
        List<List<String>> sccs = detector.findSccs();

        for (List<String> scc : sccs) {
            boolean isCycle = false;
            if (scc.size() > 1) {
                isCycle = true;
            } else if (scc.size() == 1) {
                String u = scc.get(0);
                for (DslWorkflowEdge edge : outEdges.get(u)) {
                    if (edge.toNodeId().equals(u)) {
                        isCycle = true;
                        break;
                    }
                }
            }

            if (isCycle) {
                // 校验 SCC 内的有向边：区分前向边与回环边 (Loop-Back Edge)
                Set<String> sccSet = new HashSet<>(scc);
                List<DslWorkflowEdge> sccEdges = new ArrayList<>();
                List<DslWorkflowEdge> loopEdges = new ArrayList<>();
                List<DslWorkflowEdge> forwardEdges = new ArrayList<>();

                for (String u : scc) {
                    for (DslWorkflowEdge edge : outEdges.get(u)) {
                        if (sccSet.contains(edge.toNodeId())) {
                            sccEdges.add(edge);
                            if (edge.isLoopEdge()) {
                                loopEdges.add(edge);
                            } else {
                                forwardEdges.add(edge);
                            }
                        }
                    }
                }

                // 1. 环路中必须显式声明至少一条 isLoopEdge=true 回环边
                if (loopEdges.isEmpty()) {
                    throw new UnboundedCycleException(
                            "检测到隐式循环依赖死锁! 环路中未声明任何 isLoopEdge=true 回环边, 涉案节点: " + scc,
                            scc
                    );
                }

                // 2. 校验回环边的步数上限与退出断言
                for (DslWorkflowEdge edge : loopEdges) {
                    if (edge.maxIterations() <= 0 || edge.maxIterations() > 10) {
                        throw new UnboundedCycleException(
                                "循环边 " + edge.edgeId() + " 的 maxIterations 必须在 [1, 10] 闭区间内, 实际为: " + edge.maxIterations() + ", 涉案节点: " + scc,
                                scc
                        );
                    }
                    if (edge.exitCondition() == null || edge.exitCondition().isBlank()) {
                        throw new UnboundedCycleException(
                                "循环边 " + edge.edgeId() + " 缺失 exitCondition 退出断言, 涉案节点: " + scc,
                                scc
                        );
                    }
                }

                // 3. 校验移除回环边后的前向边子图是否依然存在隐式环路
                Map<String, Integer> fwdInDegree = new HashMap<>();
                Map<String, List<String>> fwdAdj = new HashMap<>();
                for (String node : scc) {
                    fwdInDegree.put(node, 0);
                    fwdAdj.put(node, new ArrayList<>());
                }
                for (DslWorkflowEdge edge : forwardEdges) {
                    fwdAdj.get(edge.fromNodeId()).add(edge.toNodeId());
                    fwdInDegree.put(edge.toNodeId(), fwdInDegree.get(edge.toNodeId()) + 1);
                }
                Queue<String> fwdQueue = new ArrayDeque<>();
                for (Map.Entry<String, Integer> entry : fwdInDegree.entrySet()) {
                    if (entry.getValue() == 0) {
                        fwdQueue.add(entry.getKey());
                    }
                }
                int fwdVisited = 0;
                while (!fwdQueue.isEmpty()) {
                    String u = fwdQueue.poll();
                    fwdVisited++;
                    for (String v : fwdAdj.get(u)) {
                        int updated = fwdInDegree.get(v) - 1;
                        fwdInDegree.put(v, updated);
                        if (updated == 0) {
                            fwdQueue.add(v);
                        }
                    }
                }
                if (fwdVisited < scc.size()) {
                    throw new UnboundedCycleException(
                            "检测到未受控的前向循环依赖死锁! 涉案节点: " + scc,
                            scc
                    );
                }
            }
        }

        // 2. Kahn 拓扑排序生成分层计划（排除 isLoopEdge 的反向边）
        Map<String, List<String>> forwardAdj = new HashMap<>();
        Map<String, Set<String>> reverseDeps = new HashMap<>();
        Map<String, Integer> forwardInDegree = new HashMap<>();

        for (String id : nodeMap.keySet()) {
            forwardAdj.put(id, new ArrayList<>());
            reverseDeps.put(id, new LinkedHashSet<>());
            forwardInDegree.put(id, 0);
        }

        for (DslWorkflowEdge edge : def.edges()) {
            if (!edge.isLoopEdge()) {
                forwardAdj.get(edge.fromNodeId()).add(edge.toNodeId());
                reverseDeps.get(edge.toNodeId()).add(edge.fromNodeId());
                forwardInDegree.put(edge.toNodeId(), forwardInDegree.get(edge.toNodeId()) + 1);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : forwardInDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<List<DagTaskNode>> phases = new ArrayList<>();
        int visited = 0;

        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            List<DagTaskNode> currentPhaseNodes = new ArrayList<>();
            List<String> currentLevelIds = new ArrayList<>();

            for (int i = 0; i < levelSize; i++) {
                String u = queue.poll();
                currentLevelIds.add(u);
                visited++;

                DslWorkflowNode wn = nodeMap.get(u);
                DagTaskNode dagNode = new DagTaskNode(
                        wn.nodeId(),
                        wn.objective() != null ? wn.objective() : wn.name(),
                        wn.requiredCapability() != null ? wn.requiredCapability() : "GENERAL",
                        List.copyOf(reverseDeps.get(u)),
                        wn.timeoutSeconds(),
                        wn.config()
                );
                currentPhaseNodes.add(dagNode);
            }

            phases.add(currentPhaseNodes);

            for (String u : currentLevelIds) {
                for (String v : forwardAdj.get(u)) {
                    int updated = forwardInDegree.get(v) - 1;
                    forwardInDegree.put(v, updated);
                    if (updated == 0) {
                        queue.add(v);
                    }
                }
            }
        }

        if (visited < nodeMap.size()) {
            List<String> unvisited = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : forwardInDegree.entrySet()) {
                if (entry.getValue() > 0) {
                    unvisited.add(entry.getKey());
                }
            }
            throw new UnboundedCycleException("前向图存在无法解析的前向拓扑环路, 涉案节点: " + unvisited, unvisited);
        }

        return new PhasedExecutionPlan(phases, visited);
    }

    /**
     * 门禁三：外部 MCP 工具与千问 1536 维超球面流形存活断言
     */
    public void validateStage3Dependencies(DslWorkflowDefinition def) {
        for (DslWorkflowNode node : def.nodes()) {
            // 1. MCP 工具在线断言
            if (node.nodeType() == DslNodeType.MCP_TOOL_CALL) {
                if (node.mcpToolName() == null || node.mcpToolName().isBlank()) {
                    throw new DependencyUnsatisfiedException("MCP 工具调用节点 [" + node.nodeId() + "] 必须指定 mcpToolName");
                }
                if (!mcpChecker.isToolAvailable(node.mcpToolName())) {
                    throw new DependencyUnsatisfiedException("节点 [" + node.nodeId() + "] 引用的 MCP 工具 [" + node.mcpToolName() + "] 处于离线或未注册状态");
                }
            }

            // 2. AgentMesh 能力与千问 1536 维超球面流形断言
            String cap = node.requiredCapability();
            if (cap != null && !cap.isBlank()) {
                List<AgentCard> onlineCards = meshRegistry.listOnlineCards();
                List<AgentCard> matchedCards = onlineCards.stream()
                        .filter(AgentCard::online)
                        .filter(c -> c.capabilities() != null && c.capabilities().contains(cap))
                        .toList();

                if (matchedCards.isEmpty()) {
                    throw new DependencyUnsatisfiedException("节点 [" + node.nodeId() + "] 所需能力 [" + cap + "] 在 AgentMesh 中无匹配的在线 AgentCard");
                }

                // 核验千问 1536 维超球面单位向量流形几何约束
                for (AgentCard card : matchedCards) {
                    float[] emb = card.embedding1536();
                    if (emb == null || emb.length != 1536) {
                        throw new DependencyUnsatisfiedException("AgentCard [" + card.agentId() + "] 向量维度异常 (必须为 1536 维)");
                    }
                    double norm = 0.0;
                    for (float v : emb) {
                        norm += v * v;
                    }
                    norm = Math.sqrt(norm);
                    if (Math.abs(norm - 1.0) > 1e-4) {
                        throw new DependencyUnsatisfiedException("AgentCard [" + card.agentId() + "] 偏离千问 1536 维超球面单位范数流形: norm=" + norm);
                    }
                }
            }
        }
    }

    private static String computeDigest(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * Tarjan 强连通分量 (SCC) 算法探测器
     */
    private static class TarjanSccDetector {
        private final Set<String> allNodes;
        private final Map<String, List<DslWorkflowEdge>> outEdges;
        private int index = 0;
        private final Map<String, Integer> indices = new HashMap<>();
        private final Map<String, Integer> lowlinks = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<List<String>> sccs = new ArrayList<>();

        public TarjanSccDetector(Set<String> allNodes, Map<String, List<DslWorkflowEdge>> outEdges) {
            this.allNodes = allNodes;
            this.outEdges = outEdges;
        }

        public List<List<String>> findSccs() {
            for (String node : allNodes) {
                if (!indices.containsKey(node)) {
                    strongConnect(node);
                }
            }
            return sccs;
        }

        private void strongConnect(String v) {
            indices.put(v, index);
            lowlinks.put(v, index);
            index++;
            stack.push(v);
            onStack.add(v);

            List<DslWorkflowEdge> edges = outEdges.getOrDefault(v, List.of());
            for (DslWorkflowEdge edge : edges) {
                String w = edge.toNodeId();
                if (!indices.containsKey(w)) {
                    strongConnect(w);
                    lowlinks.put(v, Math.min(lowlinks.get(v), lowlinks.get(w)));
                } else if (onStack.contains(w)) {
                    lowlinks.put(v, Math.min(lowlinks.get(v), indices.get(w)));
                }
            }

            if (lowlinks.get(v).equals(indices.get(v))) {
                List<String> scc = new ArrayList<>();
                String w;
                do {
                    w = stack.pop();
                    onStack.remove(w);
                    scc.add(w);
                } while (!v.equals(w));
                sccs.add(scc);
            }
        }
    }
}

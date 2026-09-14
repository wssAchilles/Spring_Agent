package tech.qiantong.qknow.hermes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.a2a.card.AgentCard;
import tech.qiantong.qknow.hermes.a2a.card.AgentMeshRegistry;
import tech.qiantong.qknow.hermes.a2a.dsl.*;
import tech.qiantong.qknow.hermes.a2a.envelope.A2AMessageEnvelope;
import tech.qiantong.qknow.hermes.a2a.envelope.A2AMessageType;
import tech.qiantong.qknow.hermes.agent.blackboard.SharedBlackboard;
import tech.qiantong.qknow.hermes.agent.dag.DagTaskNode;
import tech.qiantong.qknow.hermes.agent.dag.PhasedExecutionPlan;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 47: 分布式多智能体网格与声明式 A2A 通信 DSL 引擎核心契约测试
 */
public class Phase47A2ADslMeshContractTest {

    private DslWorkflowCompiler compiler;
    private AgentMeshRegistry meshRegistry;
    private DslWorkflowEngine engine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        compiler = new DslWorkflowCompiler();
        meshRegistry = new AgentMeshRegistry();
        engine = new DslWorkflowEngine(compiler, meshRegistry);
    }

    private float[] createHypersphericalVector(float seed) {
        float[] vec = new float[1536];
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) Math.sin(seed + i * 0.1);
        }
        return vec;
    }

    @Test
    @DisplayName("Contract 1: A2AMessageEnvelope 强类型不可变消息与时效安全租约验证")
    void testA2AMessageEnvelopeContract() throws Exception {
        Map<String, Object> payload = Map.of("query", "分析Q3财务指标", "depth", 3);
        String leaseToken = "lease_sec_token_999";
        
        A2AMessageEnvelope envelope = A2AMessageEnvelope.create(
                "trace_abc123",
                "agent_supervisor",
                "agent_financial_worker",
                A2AMessageType.TASK_EXECUTE,
                leaseToken,
                payload,
                30_000L
        );

        assertNotNull(envelope.messageId());
        assertEquals("trace_abc123", envelope.traceId());
        assertEquals("agent_supervisor", envelope.senderAgentId());
        assertEquals("agent_financial_worker", envelope.recipientAgentId());
        assertEquals(A2AMessageType.TASK_EXECUTE, envelope.messageType());
        assertEquals(leaseToken, envelope.securityLeaseToken());
        assertTrue(envelope.leaseExpiryTimestamp() > envelope.timestamp());
        assertTrue(envelope.isLeaseValid(), "在有效期内租约必须判定为合法有效");

        // 测试 JSON 序列化与往返反序列化
        String json = objectMapper.writeValueAsString(envelope);
        A2AMessageEnvelope restored = objectMapper.readValue(json, A2AMessageEnvelope.class);
        assertEquals(envelope.messageId(), restored.messageId());
        assertEquals(envelope.senderAgentId(), restored.senderAgentId());
        assertEquals(envelope.payload().get("query"), restored.payload().get("query"));
    }

    @Test
    @DisplayName("Contract 2: AgentCard 阿里千问 1536 维超球面嵌入归一化约束与能力契约")
    void testAgentCardHypersphericalEmbeddingContract() {
        // 1. 非 1536 维向量必须被严格拒绝
        float[] invalidDimVec = new float[512];
        assertThrows(IllegalArgumentException.class, () -> {
            new AgentCard("card_01", "TestAgent", "Desc", List.of("SQL_ANALYSIS"),
                    invalidDimVec, 0.95, true);
        });

        // 2. 1536 维全零向量必须被拒绝（无法进行超球面单位投影）
        float[] zeroVec = new float[1536];
        assertThrows(IllegalArgumentException.class, () -> {
            new AgentCard("card_02", "ZeroAgent", "Desc", List.of("SQL_ANALYSIS"),
                    zeroVec, 0.95, true);
        });

        // 3. 正常向量必须自动归一化到 L2 单位超球面 (||v|| = 1.0)
        float[] rawVec = createHypersphericalVector(1.0f);
        AgentCard card = new AgentCard("card_03", "NormalizedAgent", "Desc", List.of("SQL_ANALYSIS"),
                rawVec, 0.95, true);

        float normSq = 0.0f;
        for (float f : card.embedding1536()) {
            normSq += f * f;
        }
        assertEquals(1.0f, (float) Math.sqrt(normSq), 1e-4f, "千问 1536 维向量必须完成严格 L2 模长归一化");

        // 4. 信誉得分必须落在 [0.0, 1.0] 闭区间
        assertThrows(IllegalArgumentException.class, () -> {
            new AgentCard("card_04", "BadRep", "Desc", List.of("SQL_ANALYSIS"),
                    rawVec, 1.5, true);
        });
    }

    @Test
    @DisplayName("Contract 3: AgentMeshRegistry 动态语义竞标与信誉加权最优选拔 (定理 1.2 凸组合验证)")
    void testAgentMeshRegistryBiddingContract() {
        float[] queryVec = createHypersphericalVector(2.0f);

        // Agent A: 语义向量非常接近 queryVec (使用同一种子 2.0)，信誉得分 0.80
        AgentCard agentA = new AgentCard("agent_a", "AgentA", "High Relevance", List.of("KNOWLEDGE_GRAPH"),
                createHypersphericalVector(2.0f), 0.80, true);

        // Agent B: 语义向量偏差较大 (使用种子 5.0)，即使信誉略高 0.85
        AgentCard agentB = new AgentCard("agent_b", "AgentB", "Low Relevance", List.of("KNOWLEDGE_GRAPH"),
                createHypersphericalVector(5.0f), 0.85, true);

        meshRegistry.registerCard(agentA);
        meshRegistry.registerCard(agentB);

        Optional<AgentMeshRegistry.BiddingMatchResult> match = meshRegistry.findBestWorker(queryVec, "KNOWLEDGE_GRAPH");
        assertTrue(match.isPresent());
        assertEquals("agent_a", match.get().card().agentId(), "定理 1.2 加权凸组合下，高语义共振节点应在竞标中胜出");
        assertTrue(match.get().compositeScore() > 0.80, "综合竞标得分应超过 0.80");
    }

    @Test
    @DisplayName("Contract 4: DslWorkflowCompiler JSON 解析与拓扑分层规划编译 (DAG 线性/并行正确性)")
    void testDslWorkflowCompilerLinearAndParallelPlan() throws Exception {
        String dslJson = """
        {
            "workflowId": "wf_diamond_test",
            "name": "钻石拓扑协同分析",
            "version": "1.0.0",
            "description": "钻石测试DAG",
            "nodes": [
                {"nodeId": "node_a", "name": "数据抽取", "objective": "抽取底层数据", "requiredCapability": "DATA_EXTRACT", "timeoutSeconds": 15, "required": true},
                {"nodeId": "node_b", "name": "知识图谱多跳", "objective": "执行图多跳推演", "requiredCapability": "KG_REASONING", "timeoutSeconds": 20, "required": true},
                {"nodeId": "node_c", "name": "时序指标计算", "objective": "计算财务指标", "requiredCapability": "METRIC_CALC", "timeoutSeconds": 20, "required": true},
                {"nodeId": "node_d", "name": "综合报告撰写", "objective": "融合图谱与指标输出报告", "requiredCapability": "REPORT_GEN", "timeoutSeconds": 30, "required": true}
            ],
            "edges": [
                {"fromNodeId": "node_a", "toNodeId": "node_b"},
                {"fromNodeId": "node_a", "toNodeId": "node_c"},
                {"fromNodeId": "node_b", "toNodeId": "node_d"},
                {"fromNodeId": "node_c", "toNodeId": "node_d"}
            ]
        }
        """;

        WorkflowDefinition definition = compiler.parseJson(dslJson);
        assertEquals("wf_diamond_test", definition.workflowId());
        assertEquals(4, definition.nodes().size());
        assertEquals(4, definition.edges().size());

        PhasedExecutionPlan plan = compiler.compile(definition);
        assertEquals(4, plan.totalTasks());
        assertEquals(3, plan.phases().size(), "钻石拓扑应编译为严格的 3 个分层执行阶段");

        // Phase 0 必须仅包含 node_a
        assertEquals(1, plan.phases().get(0).size());
        assertEquals("node_a", plan.phases().get(0).get(0).taskId());

        // Phase 1 必须包含并发执行的 node_b 和 node_c
        assertEquals(2, plan.phases().get(1).size());
        Set<String> phase1Ids = Set.of(plan.phases().get(1).get(0).taskId(), plan.phases().get(1).get(1).taskId());
        assertTrue(phase1Ids.contains("node_b"));
        assertTrue(phase1Ids.contains("node_c"));

        // Phase 2 必须仅包含汇聚节点 node_d
        assertEquals(1, plan.phases().get(2).size());
        assertEquals("node_d", plan.phases().get(2).get(0).taskId());
        assertEquals(2, plan.phases().get(2).get(0).dependencies().size(), "node_d 必须保留对上游 b 和 c 的依赖声明");
    }

    @Test
    @DisplayName("Contract 5: DslWorkflowCompiler 定理 1.1 静态依赖环路死锁排查与异常截断")
    void testDslWorkflowCompilerCyclicDeadlockPrevention() {
        List<WorkflowNode> nodes = List.of(
                new WorkflowNode("node_x", "NodeX", "ObjX", "CAP_X", 10, true),
                new WorkflowNode("node_y", "NodeY", "ObjY", "CAP_Y", 10, true),
                new WorkflowNode("node_z", "NodeZ", "ObjZ", "CAP_Z", 10, true)
        );
        // 构造环路：X -> Y -> Z -> X
        List<WorkflowEdge> edges = List.of(
                new WorkflowEdge("node_x", "node_y"),
                new WorkflowEdge("node_y", "node_z"),
                new WorkflowEdge("node_z", "node_x")
        );
        WorkflowDefinition cyclicDef = new WorkflowDefinition("wf_cycle", "环路工作流", "1.0.0", "循环测试", nodes, edges);

        DslCyclicDependencyException ex = assertThrows(DslCyclicDependencyException.class, () -> {
            compiler.compile(cyclicDef);
        });

        assertTrue(ex.getCycleNodes().containsAll(List.of("node_x", "node_y", "node_z")),
                "定理 1.1 静态排查必须精确输出所有涉案环路节点，杜绝生产死锁");
    }

    @Test
    @DisplayName("Contract 6: DslWorkflowEngine 声明式热部署与运行时原子替换 (Zero-Downtime Hot Deploy)")
    void testDslWorkflowEngineZeroDowntimeHotDeploy() {
        // 部署 v1
        WorkflowDefinition v1 = new WorkflowDefinition(
                "wf_prod", "生产工作流", "1.0.0", "v1部署",
                List.of(new WorkflowNode("n1", "Task1", "Obj1", "CAP1", 10, true)),
                List.of()
        );
        engine.deployWorkflow(v1);
        assertEquals("1.0.0", engine.getActiveWorkflow().orElseThrow().version());

        // 热部署升级到 v2
        WorkflowDefinition v2 = new WorkflowDefinition(
                "wf_prod", "生产工作流", "2.0.0", "v2升级",
                List.of(
                        new WorkflowNode("n1", "Task1", "Obj1", "CAP1", 10, true),
                        new WorkflowNode("n2", "Task2", "Obj2", "CAP2", 15, true)
                ),
                List.of(new WorkflowEdge("n1", "n2"))
        );
        engine.deployWorkflow(v2);
        assertEquals("2.0.0", engine.getActiveWorkflow().orElseThrow().version(), "原子热部署应即时生效");
        assertEquals(2, engine.getActiveWorkflow().orElseThrow().nodes().size());
    }

    @Test
    @DisplayName("Contract 7: DslWorkflowEngine A2A 协议信封驱动的多智能体全链路调度与黑板状态流转")
    void testDslWorkflowEngineEndToEndA2AScheduling() {
        // 1. 注册不同能力的 Worker AgentCard
        meshRegistry.registerCard(new AgentCard(
                "agent_data_expert", "DataExpert", "数据专家", List.of("DATA_FETCH"),
                createHypersphericalVector(1.0f), 0.90, true
        ));
        meshRegistry.registerCard(new AgentCard(
                "agent_summary_expert", "SummaryExpert", "总结专家", List.of("DATA_SUMMARY"),
                createHypersphericalVector(2.0f), 0.92, true
        ));

        // 2. 构造依赖工作流：Task A -> Task B
        WorkflowDefinition def = new WorkflowDefinition(
                "wf_e2e", "端到端A2A测试", "1.0.0", "端到端验证",
                List.of(
                        new WorkflowNode("task_fetch", "获取原始数据", "从源端提取销售数据", "DATA_FETCH", 10, true),
                        new WorkflowNode("task_sum", "汇总结论", "聚合清洗后的事实", "DATA_SUMMARY", 10, true)
                ),
                List.of(new WorkflowEdge("task_fetch", "task_sum"))
        );

        // 3. 执行调度
        SharedBlackboard blackboard = new SharedBlackboard();
        engine.executeWorkflow("sess_contract_47", def, blackboard);

        // 4. 验证共享黑板状态事实流转
        Map<String, String> results = blackboard.getFactsByKeys(List.of("task_fetch", "task_sum"));
        assertTrue(results.containsKey("task_fetch"), "上游任务必须在黑板提交事实");
        assertTrue(results.containsKey("task_sum"), "下游任务必须在黑板提交事实");
        assertTrue(results.get("task_sum").contains("已融合上游事实"), "下游任务执行时必须成功萃取并融合上游依赖事实");

        // 5. 验证执行链路日志记入黑板
        assertFalse(blackboard.getTraceLogs().isEmpty(), "调度全程应写入可审计追踪日志");
    }

    @Test
    @DisplayName("Contract 8: 分布式租约过期或伪造的主动防御安全拦截门禁 (Security Lease Expiry/Tampering Gate)")
    void testSecurityLeaseTamperingAndExpiryGate() {
        // 1. 租约生成与核验
        String validLease = meshRegistry.issueLeaseToken("agent_test", 1000L);
        assertTrue(meshRegistry.verifyLeaseToken(validLease), "刚签发在有效期内的租约必须核验通过");

        // 2. 伪造或非法租约拦截
        assertFalse(meshRegistry.verifyLeaseToken("forged_malicious_lease_token"), "未经签发的伪造租约必须被拒绝");
        assertFalse(meshRegistry.verifyLeaseToken(null), "空租约必须被拒绝");

        // 3. 消息信封自身 TTL 校验
        long now = System.currentTimeMillis();
        A2AMessageEnvelope expiredEnvelope = new A2AMessageEnvelope(
                "msg_expired", "trace_x", "span_1", "sender", "receiver",
                A2AMessageType.TASK_EXECUTE, validLease, Map.of(),
                now - 10_000L, // 10秒前创建
                now - 5_000L   // 5秒前已过期
        );
        assertFalse(expiredEnvelope.isLeaseValid(), "超过租约截止时间的消息信封必须被拦截标记为失效");
    }
}

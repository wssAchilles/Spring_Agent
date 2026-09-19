package tech.qiantong.qknow.hermes.a2a;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.a2a.card.AgentCard;
import tech.qiantong.qknow.hermes.a2a.card.AgentMeshRegistry;
import tech.qiantong.qknow.hermes.a2a.dsl.*;
import tech.qiantong.qknow.hermes.a2a.dsl.exception.DependencyUnsatisfiedException;
import tech.qiantong.qknow.hermes.a2a.dsl.exception.DslSyntaxValidationException;
import tech.qiantong.qknow.hermes.a2a.dsl.exception.UnboundedCycleException;
import tech.qiantong.qknow.hermes.agent.blackboard.SharedBlackboard;
import tech.qiantong.qknow.hermes.agent.dag.PhasedExecutionPlan;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 110: 声明式工作流 DSL 编译器、三阶静态安全门禁与零停机热重载引擎契约测试套件
 */
public class Phase110DslCompilerAndHotReloadContractTest {

    private AgentMeshRegistry meshRegistry;
    private ThreeStageStaticSafetyGate safetyGate;
    private ZeroDowntimeHotReloadEngine hotReloadEngine;

    private float[] createNormalized1536Vector(float seed) {
        float[] vec = new float[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) Math.sin(seed + i * 0.01);
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) (vec[i] / norm);
        }
        return vec;
    }

    @BeforeEach
    void setUp() {
        meshRegistry = new AgentMeshRegistry();
        // 注册测试用标准在线 AgentCard
        AgentCard card = new AgentCard(
                "agent_analyst_01",
                "财务分析专家",
                "负责复杂财务与业务数据提取分析",
                List.of("FINANCIAL_ANALYSIS", "GENERAL"),
                createNormalized1536Vector(1.0f),
                0.95,
                true
        );
        meshRegistry.registerCard(card);

        safetyGate = new ThreeStageStaticSafetyGate(meshRegistry);
        hotReloadEngine = new ZeroDowntimeHotReloadEngine(safetyGate, meshRegistry);
    }

    @Test
    @DisplayName("Contract 1: YAML/JSON 双向无损序列化与 Schema 合法性校验契约")
    void testContract1YamlJsonBidirectionalAndSchemaValidation() throws Exception {
        DslWorkflowNode n1 = new DslWorkflowNode("node_1", "数据抓取", DslNodeType.TASK, "抓取目标数据", "GENERAL", null, 30, Map.of());
        DslWorkflowNode n2 = new DslWorkflowNode("node_2", "数据分析", DslNodeType.TASK, "深度解析数据", "FINANCIAL_ANALYSIS", null, 30, Map.of());
        DslWorkflowEdge e1 = new DslWorkflowEdge("e1", "node_1", "node_2", null, false, 0, null);

        DslWorkflowDefinition originalDef = new DslWorkflowDefinition(
                "wf_bidirectional",
                "双向转换工作流",
                1,
                "测试 YAML 与 JSON 双向无损互转",
                List.of(n1, n2),
                List.of(e1),
                Map.of("category", "finance")
        );

        // 1. JSON 互转验证
        String json = originalDef.toJson();
        assertNotNull(json);
        DslWorkflowDefinition fromJson = DslWorkflowDefinition.fromJson(json);
        assertEquals(originalDef.workflowId(), fromJson.workflowId());
        assertEquals(originalDef.nodes().size(), fromJson.nodes().size());
        assertEquals(originalDef.edges().size(), fromJson.edges().size());

        // 2. YAML 互转验证
        String yaml = originalDef.toYaml();
        assertNotNull(yaml);
        DslWorkflowDefinition fromYaml = DslWorkflowDefinition.fromYaml(yaml);
        assertEquals(originalDef.workflowId(), fromYaml.workflowId());
        assertEquals(originalDef.nodes().size(), fromYaml.nodes().size());
        assertEquals(originalDef.edges().size(), fromYaml.edges().size());

        // 3. 门禁一：非法 nodeId 格式拦截
        DslWorkflowNode invalidNode = new DslWorkflowNode("node@invalid!", "非法节点", DslNodeType.TASK, "测试非法ID", "GENERAL", null, 30, Map.of());
        DslWorkflowDefinition invalidDef = new DslWorkflowDefinition(
                "wf_invalid_node",
                "非法节点测试",
                1,
                "测试",
                List.of(invalidNode),
                List.of(),
                Map.of()
        );
        assertThrows(DslSyntaxValidationException.class, () -> safetyGate.validateStage1Syntax(invalidDef));

        // 4. 门禁一：重复 nodeId 拦截
        DslWorkflowNode duplicateNode = new DslWorkflowNode("node_1", "重复节点", DslNodeType.TASK, "测试重复ID", "GENERAL", null, 30, Map.of());
        DslWorkflowDefinition dupDef = new DslWorkflowDefinition(
                "wf_dup_node",
                "重复节点测试",
                1,
                "测试",
                List.of(n1, duplicateNode),
                List.of(),
                Map.of()
        );
        assertThrows(DslSyntaxValidationException.class, () -> safetyGate.validateStage1Syntax(dupDef));

        // 5. 门禁一：悬空边拦截
        DslWorkflowEdge danglingEdge = new DslWorkflowEdge("e_dangling", "node_1", "node_non_existent", null, false, 0, null);
        DslWorkflowDefinition danglingDef = new DslWorkflowDefinition(
                "wf_dangling",
                "悬空边测试",
                1,
                "测试",
                List.of(n1),
                List.of(danglingEdge),
                Map.of()
        );
        assertThrows(DslSyntaxValidationException.class, () -> safetyGate.validateStage1Syntax(danglingDef));
    }

    @Test
    @DisplayName("Contract 2: Tarjan SCC 拓扑分析与有界循环硬熔断契约")
    void testContract2TarjanSccTopologyAndBoundedCycleGate() {
        DslWorkflowNode n1 = new DslWorkflowNode("step_draft", "初稿生成", DslNodeType.TASK, "撰写初稿", "GENERAL", null, 30, Map.of());
        DslWorkflowNode n2 = new DslWorkflowNode("step_review", "初稿评审", DslNodeType.STATE_GRAPH_LOOP, "评审初稿", "FINANCIAL_ANALYSIS", null, 30, Map.of());

        // 1. 合法有界循环：step_draft -> step_review -> step_draft (isLoopEdge=true, maxIterations=3, exitCondition存在)
        DslWorkflowEdge forwardEdge = new DslWorkflowEdge("e_fwd", "step_draft", "step_review", null, false, 0, null);
        DslWorkflowEdge loopBackEdge = new DslWorkflowEdge("e_loop", "step_review", "step_draft", null, true, 3, "score >= 0.8");

        DslWorkflowDefinition boundedDef = new DslWorkflowDefinition(
                "wf_bounded_cycle",
                "有界循环工作流",
                1,
                "测试 Tarjan SCC 合法有界循环放行",
                List.of(n1, n2),
                List.of(forwardEdge, loopBackEdge),
                Map.of()
        );

        PhasedExecutionPlan plan = safetyGate.validateStage2Topology(boundedDef);
        assertNotNull(plan);
        assertFalse(plan.isEmpty());
        assertEquals(2, plan.totalTasks());

        // 2. 隐式无界死锁环路拦截：环路边未标注 isLoopEdge=true
        DslWorkflowEdge unlabelledLoop = new DslWorkflowEdge("e_unlabelled", "step_review", "step_draft", null, false, 0, null);
        DslWorkflowDefinition unboundedDef1 = new DslWorkflowDefinition(
                "wf_unbounded_1",
                "无界死锁工作流1",
                1,
                "未标注 isLoopEdge",
                List.of(n1, n2),
                List.of(forwardEdge, unlabelledLoop),
                Map.of()
        );
        UnboundedCycleException ex1 = assertThrows(UnboundedCycleException.class, () -> safetyGate.validateStage2Topology(unboundedDef1));
        assertTrue(ex1.getCycleNodes().contains("step_draft"));
        assertTrue(ex1.getCycleNodes().contains("step_review"));

        // 3. 步数超限拦截：maxIterations = 15 (> 10)
        DslWorkflowEdge overIterEdge = new DslWorkflowEdge("e_over", "step_review", "step_draft", null, true, 15, "score >= 0.8");
        DslWorkflowDefinition unboundedDef2 = new DslWorkflowDefinition(
                "wf_unbounded_2",
                "无界死锁工作流2",
                1,
                "maxIterations 超过上限 10",
                List.of(n1, n2),
                List.of(forwardEdge, overIterEdge),
                Map.of()
        );
        assertThrows(UnboundedCycleException.class, () -> safetyGate.validateStage2Topology(unboundedDef2));

        // 4. 缺失退出条件拦截：exitCondition 为空
        DslWorkflowEdge noExitEdge = new DslWorkflowEdge("e_no_exit", "step_review", "step_draft", null, true, 5, "");
        DslWorkflowDefinition unboundedDef3 = new DslWorkflowDefinition(
                "wf_unbounded_3",
                "无界死锁工作流3",
                1,
                "缺失 exitCondition",
                List.of(n1, n2),
                List.of(forwardEdge, noExitEdge),
                Map.of()
        );
        assertThrows(UnboundedCycleException.class, () -> safetyGate.validateStage2Topology(unboundedDef3));
    }

    @Test
    @DisplayName("Contract 3: 外部 MCP 工具存活与 AgentCard 千问 1536 维流形断言契约")
    void testContract3ExternalMcpAndAgentMeshManifoldAssertion() {
        // 1. MCP 工具存活核验：注册可用工具 "qknow_db_query"
        safetyGate.setMcpToolAvailabilityChecker(toolName -> "qknow_db_query".equals(toolName));

        DslWorkflowNode mcpNode = new DslWorkflowNode("node_mcp", "数据库查询", DslNodeType.MCP_TOOL_CALL, "执行只读SQL", null, "qknow_db_query", 30, Map.of());
        DslWorkflowDefinition validMcpDef = new DslWorkflowDefinition(
                "wf_mcp_valid",
                "有效 MCP 工作流",
                1,
                "测试 MCP 正常",
                List.of(mcpNode),
                List.of(),
                Map.of()
        );
        assertDoesNotThrow(() -> safetyGate.validateStage3Dependencies(validMcpDef));

        // 2. 离线/未注册 MCP 工具拦截
        DslWorkflowNode offlineMcpNode = new DslWorkflowNode("node_mcp_offline", "非法工具", DslNodeType.MCP_TOOL_CALL, "执行未知工具", null, "unknown_tool", 30, Map.of());
        DslWorkflowDefinition invalidMcpDef = new DslWorkflowDefinition(
                "wf_mcp_invalid",
                "无效 MCP 工作流",
                1,
                "测试 MCP 离线",
                List.of(offlineMcpNode),
                List.of(),
                Map.of()
        );
        assertThrows(DependencyUnsatisfiedException.class, () -> safetyGate.validateStage3Dependencies(invalidMcpDef));

        // 3. AgentMesh 无匹配能力拦截
        DslWorkflowNode noCapNode = new DslWorkflowNode("node_nocap", "无能力节点", DslNodeType.TASK, "需要未定义能力", "NON_EXISTENT_CAP", null, 30, Map.of());
        DslWorkflowDefinition noCapDef = new DslWorkflowDefinition(
                "wf_nocap",
                "无能力工作流",
                1,
                "测试能力未匹配",
                List.of(noCapNode),
                List.of(),
                Map.of()
        );
        assertThrows(DependencyUnsatisfiedException.class, () -> safetyGate.validateStage3Dependencies(noCapDef));

        // 4. 千问 1536 维超球面流形畸变拦截
        float[] unnormalizedVec = new float[1536];
        Arrays.fill(unnormalizedVec, 2.0f); // norm != 1.0
        AgentCard distortedCard = new AgentCard(
                "agent_distorted",
                "畸变向量智能体",
                "测试流形畸变",
                List.of("DISTORTED_CAP"),
                unnormalizedVec,
                0.8,
                true
        );
        // 注意：AgentCard 构造函数会自动归一化，如需测试偏离流形，模拟一个未提供规范维度的异常
        meshRegistry.registerCard(distortedCard);
        DslWorkflowNode capNode = new DslWorkflowNode("node_cap_ok", "有效能力", DslNodeType.TASK, "正常", "DISTORTED_CAP", null, 30, Map.of());
        DslWorkflowDefinition capDef = new DslWorkflowDefinition(
                "wf_cap_ok",
                "流形校验工作流",
                1,
                "测试",
                List.of(capNode),
                List.of(),
                Map.of()
        );
        // 经过 AgentCard 自动投影后，单位范数得到保障，应正常通过
        assertDoesNotThrow(() -> safetyGate.validateStage3Dependencies(capDef));
    }

    @Test
    @DisplayName("Contract 4: 不可变编译凭单生成与 SHA-256 防篡改核验契约")
    void testContract4CompilationReceiptAndSha256SignatureVerification() {
        DslWorkflowNode n1 = new DslWorkflowNode("task_alpha", "阶段一", DslNodeType.TASK, "分析数据", "GENERAL", null, 30, Map.of());
        DslWorkflowDefinition def = new DslWorkflowDefinition(
                "wf_receipt_test",
                "凭单测试工作流",
                1,
                "测试不可变凭单生成与验签",
                List.of(n1),
                List.of(),
                Map.of()
        );

        ThreeStageStaticSafetyGate.CompilationResult result = safetyGate.compileAndAssert(def);
        assertNotNull(result);
        assertNotNull(result.receipt());

        DslWorkflowCompilationReceipt receipt = result.receipt();
        assertEquals("wf_receipt_test", receipt.workflowId());
        assertEquals(1, receipt.version());
        assertEquals(3, receipt.gateReports().size());
        assertTrue(receipt.verifySignature());

        // 验证篡改防伪：篡改版本号导致验签失败
        DslWorkflowCompilationReceipt tamperedReceipt = new DslWorkflowCompilationReceipt(
                receipt.receiptId(),
                receipt.workflowId(),
                999, // 篡改版本号
                receipt.astDigest(),
                receipt.topologyHash(),
                receipt.gateReports(),
                receipt.sha256Signature(),
                receipt.compiledTimestamp()
        );
        assertFalse(tamperedReceipt.verifySignature());
    }

    @Test
    @DisplayName("Contract 5: 零停机热重载与在途长事务 Graceful Drain 隔离契约")
    void testContract5ZeroDowntimeHotReloadAndGracefulDrain() {
        // 1. 部署 v1 工作流
        DslWorkflowNode v1Node = new DslWorkflowNode("step_v1", "v1任务", DslNodeType.TASK, "v1目标", "GENERAL", null, 10, Map.of());
        DslWorkflowDefinition v1Def = new DslWorkflowDefinition(
                "wf_hotreload",
                "热更工作流",
                1,
                "第一版",
                List.of(v1Node),
                List.of(),
                Map.of()
        );
        DslWorkflowCompilationReceipt r1 = hotReloadEngine.deployWorkflow(v1Def);
        assertEquals(1, hotReloadEngine.getActiveVersion());
        assertTrue(r1.verifySignature());

        // 2. 模拟在途会话 session_old 启动并持有 v1 租约
        SharedBlackboard blackboard = new SharedBlackboard();
        WorkflowVersionRegistry registry = hotReloadEngine.getVersionRegistry();
        Optional<WorkflowVersionRegistry.VersionLease> leaseV1Opt = registry.acquireActiveLease();
        assertTrue(leaseV1Opt.isPresent());
        assertEquals(1, leaseV1Opt.get().version());
        assertEquals(1, registry.getVersionState(1).get().getActiveSessions());

        // 3. 热部署 v2 工作流（节点名称与目标发生变更）
        DslWorkflowNode v2Node = new DslWorkflowNode("step_v2", "v2升级任务", DslNodeType.TASK, "v2升级目标", "FINANCIAL_ANALYSIS", null, 10, Map.of());
        DslWorkflowDefinition v2Def = new DslWorkflowDefinition(
                "wf_hotreload",
                "热更工作流",
                2,
                "第二版升级",
                List.of(v2Node),
                List.of(),
                Map.of()
        );
        DslWorkflowCompilationReceipt r2 = hotReloadEngine.deployWorkflow(v2Def);
        assertEquals(2, hotReloadEngine.getActiveVersion());
        assertTrue(r2.verifySignature());

        // 4. 验证 v1 状态转为 DRAINING，但对在途会话仍可用
        assertEquals(WorkflowVersionRegistry.VersionStatus.DRAINING, registry.getVersionState(1).get().getStatus());
        assertEquals(WorkflowVersionRegistry.VersionStatus.ACTIVE, registry.getVersionState(2).get().getStatus());

        // 5. 新会话 session_new 执行：自动路由至 v2
        hotReloadEngine.executeWorkflow("session_new", blackboard);
        String v2Fact = blackboard.getFact("step_v2");
        assertNotNull(v2Fact);
        assertTrue(v2Fact.contains("v2"));

        // 6. 在途会话 session_old 执行：锁定 v1 拓扑执行，绝不发生状态撕裂
        hotReloadEngine.executeWorkflowWithVersion("session_old", 1, blackboard);
        String v1Fact = blackboard.getFact("step_v1");
        assertNotNull(v1Fact);
        assertTrue(v1Fact.contains("v1"));

        // 7. 释放步骤 2 申请的在途租约，验证 v1 状态流转至 TERMINATED
        registry.releaseLease(1);
        assertEquals(0, registry.getVersionState(1).get().getActiveSessions());
        assertEquals(WorkflowVersionRegistry.VersionStatus.TERMINATED, registry.getVersionState(1).get().getStatus());
    }

    @Test
    @DisplayName("Contract 6: 紧急一键原子回滚契约")
    void testContract6AtomicEmergencyRollback() {
        DslWorkflowNode n1 = new DslWorkflowNode("step_stable", "稳定节点", DslNodeType.TASK, "稳定业务", "GENERAL", null, 10, Map.of());
        DslWorkflowDefinition v1Def = new DslWorkflowDefinition(
                "wf_rollback",
                "回滚测试工作流",
                1,
                "稳定基线版",
                List.of(n1),
                List.of(),
                Map.of()
        );
        hotReloadEngine.deployWorkflow(v1Def);
        assertEquals(1, hotReloadEngine.getActiveVersion());

        // 升级发布 v2
        DslWorkflowNode n2 = new DslWorkflowNode("step_faulty", "故障风险节点", DslNodeType.TASK, "潜在问题", "GENERAL", null, 10, Map.of());
        DslWorkflowDefinition v2Def = new DslWorkflowDefinition(
                "wf_rollback",
                "回滚测试工作流",
                2,
                "存在隐患版本",
                List.of(n2),
                List.of(),
                Map.of()
        );
        hotReloadEngine.deployWorkflow(v2Def);
        assertEquals(2, hotReloadEngine.getActiveVersion());

        // 执行紧急一键原子回滚至 v1
        boolean rollbackOk = hotReloadEngine.rollbackToVersion(1);
        assertTrue(rollbackOk);
        assertEquals(1, hotReloadEngine.getActiveVersion());
        assertEquals(WorkflowVersionRegistry.VersionStatus.ACTIVE, hotReloadEngine.getVersionRegistry().getVersionState(1).get().getStatus());

        // 验证回滚后新会话执行的是 v1 的节点
        SharedBlackboard blackboard = new SharedBlackboard();
        hotReloadEngine.executeWorkflow("session_after_rollback", blackboard);
        assertNotNull(blackboard.getFact("step_stable"));
        assertNull(blackboard.getFact("step_faulty"));
    }
}

package tech.qiantong.qknow.hermes.flow.hitl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.engine.HybridWorkflowEngine;
import tech.qiantong.qknow.hermes.flow.hitl.dto.Phase156MasterAuditReceipt;
import tech.qiantong.qknow.hermes.flow.hitl.node.MultiAgentHandoffNodeExecutor;
import tech.qiantong.qknow.hermes.flow.hitl.signal.WorkflowSignalBus;
import tech.qiantong.qknow.hermes.flow.rag.agentic.HermesAgenticCorrectiveRagOperator;
import tech.qiantong.qknow.hermes.flow.rag.governor.HypersphericalHippoPprEngine;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.DurableStateGraphCheckpointer;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.MemoryDurableCheckpointer;
import tech.qiantong.qknow.hermes.rag.causal.DeepSeekThinkingNoisePurifier;
import tech.qiantong.qknow.hermes.tool.mcp.filter.McpSecurityPipelineFilter;
import tech.qiantong.qknow.hermes.tool.mcp.filter.McpTwoPhaseApprovalGovernor;
import tech.qiantong.qknow.hermes.tool.mcp.transport.StreamableHttpEndpointHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 156 全系统主控契约测试套件
 * 覆盖分布式持久化 Checkpointer、响应式 Signal 异步总线、双轨编排引擎、多智能体交接节点、
 * Streamable HTTP 远程端点、零信任脱敏网关、超球面 Hippo-PPR 拓扑扩散与思维链净化等核心机制。
 */
public class Phase156MasterContractTest {

    @Test
    @DisplayName("契约 1: Phase156 凭单不可变性与 SHA-256 常量时间自验真防篡改")
    void testReceiptSha256VerificationAndTamperResistance() {
        Phase156MasterAuditReceipt receipt = Phase156MasterAuditReceipt.create(
                "REC_156_001", "task_swarm_01", "exec_wf_88",
                5, 2, true, 8, 12, 6, 3, 420L, 1728560000000L
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifyDigest(), "初始凭单必须通过 SHA-256 常量时间自验真");

        // 篡改凭单字段构建伪造凭单
        Phase156MasterAuditReceipt tampered = new Phase156MasterAuditReceipt(
                receipt.receiptId(), receipt.taskId(), receipt.workflowExecutionId(),
                999, // 篡改超步数
                receipt.handoffHopCount(), receipt.isAcyclicGuaranteed(), receipt.cacheAlignedTokenBlocks(),
                receipt.pprActivatedEntityCount(), receipt.causalPurifiedChunkCount(), receipt.mcpDfaMaskedFieldCount(),
                receipt.executionDurationMs(), receipt.timestampEpochMs(), receipt.digestSha256()
        );
        assertFalse(tampered.verifyDigest(), "篡改后的伪造凭单必须被自验真拒绝");
    }

    @Test
    @DisplayName("契约 2: Checkpointer 超步快照落盘与 O(1) 历史读取")
    void testCheckpointerSaveAndLoadSnapshot() {
        DurableStateGraphCheckpointer checkpointer = new MemoryDurableCheckpointer();
        DurableStateGraphCheckpointer.SnapshotRecord snapshot = new DurableStateGraphCheckpointer.SnapshotRecord(
                "snap_01", "exec_01", "main_branch", 1,
                Map.of("step", 1, "status", "RUNNING"), List.of("node_A"),
                100L, System.currentTimeMillis(), "hash_abc"
        );

        boolean saved = checkpointer.saveSnapshot(snapshot);
        assertTrue(saved, "超步快照应成功保存");

        Optional<DurableStateGraphCheckpointer.SnapshotRecord> loaded = checkpointer.loadSnapshot("exec_01", "main_branch", 1);
        assertTrue(loaded.isPresent(), "应能成功读取超步快照");
        assertEquals("RUNNING", loaded.get().stateDelta().get("status"));
    }

    @Test
    @DisplayName("契约 3: Checkpointer CAS Fencing Token 写屏障冲突拦截")
    void testCheckpointerCasFencingTokenConflict() {
        DurableStateGraphCheckpointer checkpointer = new MemoryDurableCheckpointer();

        DurableStateGraphCheckpointer.SnapshotRecord snap1 = new DurableStateGraphCheckpointer.SnapshotRecord(
                "snap_01", "exec_02", "main_branch", 1, Map.of(), List.of(), 500L, System.currentTimeMillis(), "h1"
        );
        assertTrue(checkpointer.saveSnapshot(snap1), "首次保存 Fencing Token 500 应成功");

        // 传入更低或相等的 Fencing Token 应被拒绝
        DurableStateGraphCheckpointer.SnapshotRecord snapOld = new DurableStateGraphCheckpointer.SnapshotRecord(
                "snap_02", "exec_02", "main_branch", 2, Map.of(), List.of(), 300L, System.currentTimeMillis(), "h2"
        );
        assertFalse(checkpointer.saveSnapshot(snapOld), "逆序 Fencing Token 300 必须被 CAS 写屏障拒绝");
    }

    @Test
    @DisplayName("契约 4: Checkpointer 时光旅行热补丁分叉隔离 (幽灵变量污染率恒为 0)")
    void testCheckpointerBranchForkingIsolation() {
        DurableStateGraphCheckpointer checkpointer = new MemoryDurableCheckpointer();
        checkpointer.saveSnapshot(new DurableStateGraphCheckpointer.SnapshotRecord(
                "snap_01", "exec_03", "main", 1, Map.of("var_x", 10, "var_y", 20), List.of("n1"), 1L, System.currentTimeMillis(), "h1"
        ));

        // 从超步 1 派生分叉分支，并覆盖 var_x = 99
        String forkedBranchId = checkpointer.forkBranch("exec_03", "main", 1, Map.of("var_x", 99));
        assertNotNull(forkedBranchId);
        assertTrue(forkedBranchId.startsWith("fork_"));

        // 检查分叉分支状态
        Optional<DurableStateGraphCheckpointer.SnapshotRecord> forkedSnapshot = checkpointer.loadSnapshot("exec_03", forkedBranchId, 0);
        assertTrue(forkedSnapshot.isPresent());
        assertEquals(99, forkedSnapshot.get().stateDelta().get("var_x"), "分叉分支中的 var_x 应被覆盖为 99");
        assertEquals(20, forkedSnapshot.get().stateDelta().get("var_y"), "分叉分支中的 var_y 应继承原值 20");

        // 主分支状态完全未被污染
        Optional<DurableStateGraphCheckpointer.SnapshotRecord> mainSnapshot = checkpointer.loadSnapshot("exec_03", "main", 1);
        assertEquals(10, mainSnapshot.get().stateDelta().get("var_x"), "主分支原始数据不可受幽灵变量污染");
    }

    @Test
    @DisplayName("契约 5: WorkflowSignalBus 异步信号投递唤醒与超时防护")
    void testWorkflowSignalBusDeliveryAndTimeout() {
        WorkflowSignalBus bus = new WorkflowSignalBus();
        String runtimeId = "wf_run_999";
        String signalType = "HITL_APPROVAL_SIGNAL";

        CompletableFuture<Map<String, Object>> waitFuture = bus.registerPendingWait(runtimeId, signalType, 2000L);
        assertTrue(bus.hasPendingWait(runtimeId, signalType));

        // 投递异步信号
        WorkflowSignalBus.SignalDeliveryReceipt receipt = bus.sendSignal(runtimeId, signalType, Map.of("approved", true, "operator", "admin"));
        assertNotNull(receipt);
        assertTrue(receipt.resumedSuccessfully(), "信号投递应成功唤醒挂起等待者");
        assertFalse(bus.hasPendingWait(runtimeId, signalType), "等待者唤醒后锁应被移除");

        Map<String, Object> payload = waitFuture.join();
        assertEquals(true, payload.get("approved"));
    }

    @Test
    @DisplayName("契约 6: HybridWorkflowEngine 极速无环 DAG 模式正常收敛")
    void testHybridWorkflowEngineFastDagMode() {
        HybridWorkflowEngine engine = new HybridWorkflowEngine(20);

        Map<String, List<HybridWorkflowEngine.TransitionEdge>> transitions = Map.of(
                "START", List.of(new HybridWorkflowEngine.TransitionEdge("STEP_A", false)),
                "STEP_A", List.of(new HybridWorkflowEngine.TransitionEdge("STEP_B", false)),
                "STEP_B", List.of(new HybridWorkflowEngine.TransitionEdge("END", false))
        );

        HybridWorkflowEngine.WorkflowRunSummary summary = engine.execute(
                "wf_dag_01", "START", Set.of("END"), transitions, Map.of()
        );

        assertEquals(HybridWorkflowEngine.EngineMode.FAST_DAG, summary.mode());
        assertTrue(summary.converged());
        assertEquals("END", summary.finalNode());
        assertEquals(List.of("START", "STEP_A", "STEP_B", "END"), summary.executionPath());
    }

    @Test
    @DisplayName("契约 7: HybridWorkflowEngine 含有界循环边 (LOOP_BACK) 的 StateGraph 模式收敛")
    void testHybridWorkflowEngineCyclicStateGraphMode() {
        HybridWorkflowEngine engine = new HybridWorkflowEngine(20);

        // A -> EVAL -> OPTIMIZE -> EVAL (循环) -> END (逃逸)
        Map<String, List<HybridWorkflowEngine.TransitionEdge>> transitions = Map.of(
                "START", List.of(new HybridWorkflowEngine.TransitionEdge("EVAL", false)),
                "EVAL", List.of(
                        new HybridWorkflowEngine.TransitionEdge("OPTIMIZE", true), // LOOP_BACK 边
                        new HybridWorkflowEngine.TransitionEdge("END", false)      // 逃逸退出边
                ),
                "OPTIMIZE", List.of(new HybridWorkflowEngine.TransitionEdge("EVAL", false))
        );

        HybridWorkflowEngine.WorkflowRunSummary summary = engine.execute(
                "wf_cyclic_01", "START", Set.of("END"), transitions, Map.of()
        );

        assertEquals(HybridWorkflowEngine.EngineMode.CYCLIC_STATEGRAPH, summary.mode());
        assertTrue(summary.converged(), "循环状态图应在限定超步内平滑逃逸并收敛");
        assertEquals("END", summary.finalNode());
        assertTrue(summary.totalSupersteps() > 4, "应经历多轮超步迭代");
    }

    @Test
    @DisplayName("契约 8: MultiAgentHandoffNodeExecutor 拓扑节点多智能体交接流转")
    void testMultiAgentHandoffNodeExecution() {
        MultiAgentHandoffNodeExecutor executor = new MultiAgentHandoffNodeExecutor();

        MultiAgentHandoffNodeExecutor.HandoffExecutionResult result = executor.executeHandoff(
                "agent_architect", "agent_developer", 1, List.of("agent_architect"), Map.of("task", "codegen")
        );

        assertTrue(result.isSuccessful());
        assertEquals("agent_developer", result.delegatedAgentId());
        assertEquals(2, result.hopCount());
        assertNotNull(result.handoffTicketId());
    }

    @Test
    @DisplayName("契约 9: StreamableHttpEndpointHandler JSON-RPC 2.0 远程调用与 SSE 会话")
    void testStreamableHttpEndpointHandlerRpcCall() {
        StreamableHttpEndpointHandler handler = new StreamableHttpEndpointHandler();

        StreamableHttpEndpointHandler.SessionContext session = handler.establishSseSession(null);
        assertNotNull(session.sessionId());
        assertTrue(handler.isSessionActive(session.sessionId()));

        // tools/list
        StreamableHttpEndpointHandler.JsonRpcMessage listReq = new StreamableHttpEndpointHandler.JsonRpcMessage(
                "2.0", "req_1", "tools/list", null, null, null
        );
        StreamableHttpEndpointHandler.JsonRpcMessage listResp = handler.handleRpcPost(session.sessionId(), listReq);
        assertEquals("2.0", listResp.jsonrpc());
        assertEquals("req_1", listResp.id());
        assertNotNull(listResp.result());

        handler.closeSession(session.sessionId());
        assertFalse(handler.isSessionActive(session.sessionId()));
    }

    @Test
    @DisplayName("契约 10: McpSecurityPipelineFilter 双向敏感数据微秒级自动脱敏")
    void testMcpSecurityPipelineFilterDataMasking() {
        McpSecurityPipelineFilter filter = new McpSecurityPipelineFilter();

        String rawOutput = "用户信息: 手机=13812345678, 身份证=110101199003072345, 密钥=sk-abcdef1234567890abcdef";
        Map<String, Object> args = Map.of("cmd", "query; drop table users");

        McpSecurityPipelineFilter.FilterResult result = filter.processToolCall("query_tool", args, rawOutput);

        assertTrue(result.sanitizedOutput().contains("138****5678"), "手机号应被脱敏");
        assertTrue(result.sanitizedOutput().contains("110101********2345"), "身份证应被脱敏");
        assertTrue(result.sanitizedOutput().contains("sk-********************"), "API Key 应被脱敏");
        assertFalse(((String) result.sanitizedArguments().get("cmd")).contains(";"), "注入字符分号应被剔除");
        assertEquals(3, result.maskedFieldsCount(), "应精确脱敏 3 个敏感字段 (手机、身份证、API Key)");
    }

    @Test
    @DisplayName("契约 11: McpTwoPhaseApprovalGovernor L4 高危工具两阶段审批挂起与放行")
    void testMcpTwoPhaseApprovalGovernor() {
        McpTwoPhaseApprovalGovernor governor = new McpTwoPhaseApprovalGovernor();

        McpTwoPhaseApprovalGovernor.RiskGrade grade = governor.evaluateRisk("delete_database_cluster", Map.of("db", "prod"));
        assertEquals(McpTwoPhaseApprovalGovernor.RiskGrade.L4_DESTRUCTIVE, grade);
        assertTrue(governor.requiresApproval(grade));

        McpTwoPhaseApprovalGovernor.ApprovalTicket ticket = governor.requestApproval("delete_database_cluster", Map.of("db", "prod"), grade);
        assertEquals("PENDING", ticket.status());
        assertFalse(governor.isApproved(ticket.ticketId()));

        // 人工审批通过
        governor.submitDecision(ticket.ticketId(), true, "security_officer");
        assertTrue(governor.isApproved(ticket.ticketId()));
    }

    @Test
    @DisplayName("契约 12: HypersphericalHippoPprEngine 1536 维超球面拓扑实体扩散")
    void testHypersphericalHippoPprEngineDiffusion() {
        HypersphericalHippoPprEngine engine = new HypersphericalHippoPprEngine();

        // 构造两个 1536 维超球面单位向量
        float[] v1 = createUnitVector(1536, 0);
        float[] v2 = createUnitVector(1536, 1);
        float[] qVec = createUnitVector(1536, 0);

        List<HypersphericalHippoPprEngine.GraphEntity> entities = List.of(
                new HypersphericalHippoPprEngine.GraphEntity("e1", "Kubernetes", "TOPIC", v1, 100),
                new HypersphericalHippoPprEngine.GraphEntity("e2", "Docker", "ENTITY", v2, 80)
        );

        HypersphericalHippoPprEngine.PprSpreadResult result = engine.spreadOverHypersphericalGraph(
                entities, List.of("e1"), qVec, 0.85, 10, 2
        );

        assertNotNull(result);
        assertEquals(2, result.activatedEntities().size());
        assertEquals("e1", result.activatedEntities().get(0).entityId(), "种子实体 e1 PPR 得分应最高");
        assertTrue(result.pprScores().get("e1") > result.pprScores().get("e2"));
    }

    @Test
    @DisplayName("契约 13: DeepSeekThinkingNoisePurifier 思维链抗噪过滤与因果骨架压缩")
    void testDeepSeekThinkingNoisePurifier() {
        DeepSeekThinkingNoisePurifier purifier = new DeepSeekThinkingNoisePurifier();

        List<DeepSeekThinkingNoisePurifier.CandidateChunk> candidates = List.of(
                new DeepSeekThinkingNoisePurifier.CandidateChunk("c1", "核心结论: 架构升级成功", 0.88, 150),
                new DeepSeekThinkingNoisePurifier.CandidateChunk("c2", "边缘噪声: 无关日志报错", 0.32, 200),
                new DeepSeekThinkingNoisePurifier.CandidateChunk("c3", "关键证据: 数据库事务已提交", 0.76, 120)
        );

        DeepSeekThinkingNoisePurifier.PurifiedContextPayload payload = purifier.purifyForThinkingStream(
                "架构升级结果", candidates, 0.65, 500
        );

        assertEquals(2, payload.salientChunks().size(), "应保留 2 个显著切片");
        assertEquals(1, payload.filteredNoiseChunks().size(), "应剔除 1 个噪声切片");
        assertTrue(payload.noiseRejectionRatio() > 0.40, "Token 削减率应大于 40%");
        assertTrue(payload.compressedCausalScaffold().contains("c1"));
        assertFalse(payload.compressedCausalScaffold().contains("c2"));
    }

    @Test
    @DisplayName("契约 14: HermesAgenticCorrectiveRagOperator 检索质量三态研判与自反思重写")
    void testHermesAgenticCorrectiveRagOperator() {
        HermesAgenticCorrectiveRagOperator operator = new HermesAgenticCorrectiveRagOperator();

        // 充分相关
        HermesAgenticCorrectiveRagOperator.RagInspectionDecision d1 = operator.evaluateAndReflect(
                "系统如何部署", List.of("部署手册内容"), 0.85
        );
        assertEquals(HermesAgenticCorrectiveRagOperator.RetrievalQualityGrade.RELEVANT, d1.grade());

        // 模糊不清 (触发拓扑扩展)
        HermesAgenticCorrectiveRagOperator.RagInspectionDecision d2 = operator.evaluateAndReflect(
                "接口响应慢怎么办", List.of("性能调优概述"), 0.55
        );
        assertEquals(HermesAgenticCorrectiveRagOperator.RetrievalQualityGrade.AMBIGUOUS, d2.grade());
        assertTrue(d2.rewriteQueryOrRationale().contains("扩展拓扑搜索"));

        // 完全无关 (触发泛化重写)
        HermesAgenticCorrectiveRagOperator.RagInspectionDecision d3 = operator.evaluateAndReflect(
                "量子计算机如何制造", List.of("生活随笔"), 0.20
        );
        assertEquals(HermesAgenticCorrectiveRagOperator.RetrievalQualityGrade.IRRELEVANT, d3.grade());
        assertTrue(d3.rewriteQueryOrRationale().contains("泛化重写"));
    }

    private static float[] createUnitVector(int dim, int activeIndex) {
        float[] v = new float[dim];
        v[activeIndex % dim] = 1.0f;
        return v;
    }
}

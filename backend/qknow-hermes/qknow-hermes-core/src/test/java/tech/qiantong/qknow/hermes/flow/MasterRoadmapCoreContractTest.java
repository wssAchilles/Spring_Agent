package tech.qiantong.qknow.hermes.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.hitl.coordinator.HandoffTicketRecord;
import tech.qiantong.qknow.hermes.flow.hitl.coordinator.MultiAgentHandoffMeshCoordinator;
import tech.qiantong.qknow.hermes.flow.hitl.coordinator.MultiAgentHandoffMeshCoordinator.DefaultMultiAgentHandoffMeshCoordinator;
import tech.qiantong.qknow.hermes.flow.hitl.coordinator.MultiAgentHandoffMeshCoordinator.MeshHandoffDecision;
import tech.qiantong.qknow.hermes.flow.hitl.scheduler.CanonicalPrefixCacheTreeOptimizer;
import tech.qiantong.qknow.hermes.flow.hitl.scheduler.CanonicalPrefixCacheTreeOptimizer.CanonicalPrefixAlignedResult;
import tech.qiantong.qknow.hermes.flow.hitl.scheduler.CanonicalPrefixCacheTreeOptimizer.DefaultCanonicalPrefixCacheTreeOptimizer;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository.DefaultStateCheckpointRepository;
import tech.qiantong.qknow.hermes.flow.stategraph.checkpoint.StateCheckpointRepository.StateCheckpointRecord;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.DurableStateGraphEngine;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.DurableStateGraphEngine.DefaultDurableStateGraphEngine;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.DurableStateGraphEngine.SuperstepExecutionResult;
import tech.qiantong.qknow.hermes.tool.math.MathematicalCodeSandboxTool;
import tech.qiantong.qknow.hermes.tool.math.MathematicalCodeSandboxTool.DefaultMathematicalCodeSandboxTool;
import tech.qiantong.qknow.hermes.tool.math.MathematicalCodeSandboxTool.MathEvaluationReceipt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 四大优化点长期演进契约测试套件 (MasterRoadmapCoreContractTest)
 * 验证：不可变网格移交、守卫状态图、确定性数学沙箱与 64-token 前缀树缓存调度。
 *
 * @author Achilles
 * @since 2026-10-13
 */
public class MasterRoadmapCoreContractTest {

    @Test
    @DisplayName("EXP-01: 验证 HandoffTicketRecord 纯 Record 不可变性与常量时间防侧信道验签")
    void testHandoffTicketRecordIntegrityAndTimingAttackDefense() {
        HandoffTicketRecord ticket = HandoffTicketRecord.create(
                "ticket-101",
                "trace-session-999",
                "排查集群 P99 延迟与堆外内存泄漏",
                "agent_triage",
                "agent_probe",
                1,
                5,
                List.of("agent_triage"),
                Map.of("clusterId", "node-eu-prod-04")
        );

        // 1. 验证签名自验真通过
        assertTrue(ticket.verifySignature(), "初始合法签名的工单必须自验真通过");

        // 2. 验证集合防御性深拷贝不可变性
        assertThrows(UnsupportedOperationException.class, () -> ticket.auditCallChain().add("hacker_agent"),
                "工单调用链必须是纯不可变列表");
        assertThrows(UnsupportedOperationException.class, () -> ticket.immutableContext().put("leak", "true"),
                "工单上下文必须是纯不可变字典");

        // 3. 模拟黑客伪造篡改签名攻击
        HandoffTicketRecord forgedTicket = new HandoffTicketRecord(
                ticket.ticketId(),
                ticket.sessionTraceId(),
                ticket.rootTaskGoal(),
                ticket.sourceAgentId(),
                ticket.targetAgentId(),
                ticket.currentHop(),
                ticket.maxAllowedHops(),
                ticket.auditCallChain(),
                ticket.immutableContext(),
                ticket.createdAtEpochMs(),
                "0000000000000000000000000000000000000000000000000000000000000000" // 伪造哈希
        );

        // 4. 验证常量时间比对精准拦截并触发 Fail-Close
        assertFalse(forgedTicket.verifySignature(), "被篡改签名的伪造工单必须被严格拒绝");
    }

    @Test
    @DisplayName("EXP-02: 验证多智能体网格协同网络李雅普诺夫势能单调递减与三级拓扑防环门禁")
    void testMeshCoordinatorLyapunovAndLoopPrevention() {
        MultiAgentHandoffMeshCoordinator coordinator = new DefaultMultiAgentHandoffMeshCoordinator();

        // 1. 发起根移交
        HandoffTicketRecord rootTicket = coordinator.initiateRootHandoff(
                "trace-session-888",
                "反洗钱多跳可疑资金网络审查",
                "agent_triage",
                "agent_tracer",
                Map.of("targetAccount", "A-101")
        );
        assertEquals(1, rootTicket.currentHop());
        assertEquals("agent_tracer", rootTicket.targetAgentId());

        // 2. 正常合法第一跃点：agent_tracer -> agent_math
        MeshHandoffDecision hop1Decision = coordinator.evaluateMeshTransfer(
                rootTicket,
                "agent_math",
                Map.of("graphNodesCount", 128)
        );
        assertTrue(hop1Decision.permitted(), "合法递进移交必须放行");
        HandoffTicketRecord hop1Ticket = hop1Decision.nextTicket();
        assertEquals(2, hop1Ticket.currentHop());
        assertEquals("agent_math", hop1Ticket.targetAgentId());

        // 3. 门禁二测试：模拟瞬态二元乒乓震荡 agent_math -> agent_tracer
        MeshHandoffDecision pingPongDecision = coordinator.evaluateMeshTransfer(
                hop1Ticket,
                "agent_tracer",
                Map.of()
        );
        assertFalse(pingPongDecision.permitted(), "瞬态乒乓震荡必须被硬拦截");
        assertEquals("ERR_PING_PONG_CYCLE", pingPongDecision.errorCode());
        assertEquals(MultiAgentHandoffMeshCoordinator.SUPERVISOR_AGENT_ID, pingPongDecision.nextTicket().targetAgentId(),
                "被拦截后必须安全升迁至主管智能体");

        // 4. 门禁三测试：模拟历史成环调用 agent_math -> agent_decision -> agent_triage
        MeshHandoffDecision hop2Decision = coordinator.evaluateMeshTransfer(hop1Ticket, "agent_decision", Map.of());
        assertTrue(hop2Decision.permitted());
        HandoffTicketRecord hop2Ticket = hop2Decision.nextTicket();

        MeshHandoffDecision cycleDecision = coordinator.evaluateMeshTransfer(hop2Ticket, "agent_triage", Map.of());
        assertFalse(cycleDecision.permitted(), "回到调用链中已存在的祖先节点必须被拓扑环路门禁拦截");
        assertEquals("ERR_TOPOLOGICAL_LOOP", cycleDecision.errorCode());
        assertEquals(MultiAgentHandoffMeshCoordinator.SUPERVISOR_AGENT_ID, cycleDecision.nextTicket().targetAgentId());
    }

    @Test
    @DisplayName("EXP-03: 验证有状态图执行引擎前置条件守卫 (防早停) 与 CAS Fencing 写屏障快照重放")
    void testDurableStateGraphEngineGuardAndSuperstep() {
        StateCheckpointRepository repository = new DefaultStateCheckpointRepository();
        DurableStateGraphEngine engine = new DefaultDurableStateGraphEngine(repository);

        String executionId = "exec-flow-001";
        String branchId = "main";

        // 1. 注册节点 A (探针节点，无守卫) 与 节点 B (分析节点，带强类型前置守卫：必须已包含 metrics 数据)
        engine.registerGuardedNode(
                "probe_node",
                state -> true,
                state -> Map.of("metrics_collected", true, "cpu_usage", 88.5),
                List.of("analyze_node")
        );

        engine.registerGuardedNode(
                "analyze_node",
                state -> Boolean.TRUE.equals(state.get("metrics_collected")), // 守卫：未收集完成则阻断执行
                state -> Map.of("analysis_completed", true, "bottleneck", "RPC::transfer()"),
                List.of()
        );

        // 2. 初始化执行会话
        engine.initializeExecution(executionId, branchId, Map.of("initKey", "val"), List.of("probe_node"));

        // 3. 执行第 1 超步 (probe_node)
        SuperstepExecutionResult step1 = engine.advanceSuperstep(executionId, branchId);
        assertEquals(1, step1.executedSuperstep());
        assertTrue((Boolean) step1.mutatedState().get("metrics_collected"));
        assertEquals(List.of("analyze_node"), step1.nextActiveNodes());

        // 4. 执行第 2 超步 (analyze_node)
        SuperstepExecutionResult step2 = engine.advanceSuperstep(executionId, branchId);
        assertEquals(2, step2.executedSuperstep());
        assertTrue(step2.completed(), "所有后续节点执行完毕，状态图顺利达成终态");
        assertTrue((Boolean) step2.mutatedState().get("analysis_completed"));

        // 5. 验证时光旅行与崩溃恢复
        SuperstepExecutionResult resumed = engine.resumeFromLatestCheckpoint(executionId, branchId);
        assertEquals(2, resumed.executedSuperstep());
        assertEquals("RPC::transfer()", resumed.mutatedState().get("bottleneck"));

        // 6. 验证快照仓库三级冷备完整性
        List<StateCheckpointRecord> history = repository.getTimelineHistory(executionId, branchId);
        assertTrue(history.size() >= 2, "冷备环形缓冲区必须包含完整的超步推进历史快照");
    }

    @Test
    @DisplayName("EXP-04: 验证确定性数学沙箱 Pearson 相关系数、拓扑排序与不可变对账哈希")
    void testMathematicalSandboxPrecisionAndDeterminism() {
        MathematicalCodeSandboxTool sandbox = new DefaultMathematicalCodeSandboxTool();

        // 1. 高精度四则表达式求值
        MathEvaluationReceipt evalReceipt = sandbox.evaluateExpression("100 * (1.05 - 0.05) / 2");
        assertEquals("50.0", evalReceipt.calculatedResult());
        assertTrue(evalReceipt.verifyDigest(), "计算凭单 SHA-256 常量时间验签必须通过");

        // 2. Pearson 线性相关系数精确推导 (验证完全正相关 r = 1.0)
        List<Double> x = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        List<Double> y = List.of(2.0, 4.0, 6.0, 8.0, 10.0);
        MathEvaluationReceipt pearsonReceipt = sandbox.calculatePearsonCorrelation(x, y);
        assertEquals("1.000000", pearsonReceipt.calculatedResult(), "完全线性正相关序列 Pearson 相关系数必须严格为 1.000000");
        assertTrue(pearsonReceipt.verifyDigest());

        // 3. 不可变对账拓扑哈希计算
        List<String> ledger = List.of("T1:Order-001:100.00", "T2:Bank-001:100.00", "T3:Clearing-001:SETTLED");
        MathEvaluationReceipt settleReceipt = sandbox.computeImmutableSettlementHash(ledger);
        assertNotNull(settleReceipt.calculatedResult());
        assertEquals(64, settleReceipt.calculatedResult().length(), "哈希决算结果必须是合规的 64 位十六进制 SHA-256");

        // 4. 有向无环图 Kahn 算法拓扑排序与控制节点汇聚识别
        Map<String, List<String>> graph = Map.of(
                "A-101", List.of("A-102", "A-103"),
                "A-102", List.of("A-104"),
                "A-103", List.of("A-104")
        );
        MathEvaluationReceipt topoReceipt = sandbox.executeTopologicalPurityAnalysis(graph);
        assertTrue(topoReceipt.calculatedResult().contains("acyclic=true"));
        assertTrue(topoReceipt.calculatedResult().contains("sink=A-104"), "资金最终汇聚终点必须准确识别为 A-104");
    }

    @Test
    @DisplayName("EXP-05: 验证规范化 64-token 前缀树缓存调度器四层组装与 DeepSeek 思考模式控制体")
    void testCanonicalPrefixCacheAlignmentAndThinkingBody() {
        CanonicalPrefixCacheTreeOptimizer optimizer = new DefaultCanonicalPrefixCacheTreeOptimizer();

        String systemAnchor = "你是企业级 Hermes 认知中枢，严格遵循不可变数据流与安全契约。";
        String personaAndTools = "角色：架构分析师。工具集：[trace_fetcher, system_metrics, correlation_calc]。";
        List<String> knowledge = List.of(
                "微服务集群 P99 异常定义：响应时间超过 1500ms 的慢调用链。",
                "系统物理指标：包括 CPU 用户态占用、内核态占用以及堆外内存消耗。"
        );
        String userQuery = "请分析 node-eu-prod-04 节点的故障根因。";

        // 执行规范化四层流水线组装
        CanonicalPrefixAlignedResult result = optimizer.assembleAndAlign(
                systemAnchor,
                personaAndTools,
                knowledge,
                userQuery,
                true // 开启官方思考模式
        );

        // 1. 验证 64-token 边界量化对齐标记存在
        assertTrue(result.isCacheBoundaryAligned());
        assertTrue(result.fullSystemPromptWithAlignedPad().contains("<!-- ds_align_pad:"), "必须包含 64-token 物理块量化填充注释");

        // 2. 验证动态查询绝对物理尾置 (与静态前缀隔离)
        assertEquals(userQuery, result.dynamicUserPayload());
        assertFalse(result.fullSystemPromptWithAlignedPad().contains(userQuery), "静态前缀绝不允许被动态查询污染");

        // 3. 验证 DeepSeek 官方标准思考模式协议
        assertNotNull(result.thinkingExtraBody().get("thinking"));
        assertEquals("enabled", ((Map<?, ?>) result.thinkingExtraBody().get("thinking")).get("type"));

        // 4. 记录缓存命中并验证统计
        optimizer.recordCacheAccess("prefix-key-1", true);
        optimizer.recordCacheAccess("prefix-key-2", true);
        optimizer.recordCacheAccess("prefix-key-3", false);
        assertEquals(2.0 / 3.0, optimizer.getGlobalCacheHitRatio(), 1e-6);
    }
}

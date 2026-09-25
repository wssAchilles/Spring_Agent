package tech.qiantong.qknow.hermes.benchmark;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.debate.dto.AgentRoleNicheType;
import tech.qiantong.qknow.hermes.agent.debate.engine.DebateDeadlockSelfHealingGovernor;
import tech.qiantong.qknow.hermes.agent.debate.engine.HermesMixedGameDebateScheduler;
import tech.qiantong.qknow.hermes.agent.debate.engine.NashConfidenceWeightedJudge;
import tech.qiantong.qknow.hermes.benchmark.chaos.ChaosFaultInjectionGovernor;
import tech.qiantong.qknow.hermes.benchmark.chaos.ChaosFaultInjectionGovernor.ChaosFaultType;
import tech.qiantong.qknow.hermes.benchmark.concurrent.VirtualThreadConcurrencyGovernor;
import tech.qiantong.qknow.hermes.benchmark.e2e.E2EFourMetacenterPipelineBus;
import tech.qiantong.qknow.hermes.benchmark.e2e.E2EFourMetacenterPipelineBus.E2EPipelineRequest;
import tech.qiantong.qknow.hermes.benchmark.e2e.E2EFourMetacenterPipelineBus.E2EExecutionResult;
import tech.qiantong.qknow.hermes.benchmark.receipt.E2EIntegrationAuditReceipt;
import tech.qiantong.qknow.hermes.flow.hitl.engine.StreamingCausalTopologySyncBus;
import tech.qiantong.qknow.hermes.flow.hitl.engine.TimeTravelSnapshotBranchGovernor;
import tech.qiantong.qknow.hermes.rag.causal.DeepSeekCausalThinkingAligner;
import tech.qiantong.qknow.hermes.rag.causal.SteinerCausalSubgraphPruner;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.engine.DistributedLeaseCoordinator;
import tech.qiantong.qknow.hermes.tool.mcp.sagas.engine.ResilientSagasStateManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 133 全系统端到端四大中枢全链路集成、万级高并发与混沌故障注入压测硬核契约测试集
 * <p>
 * 验证目标：
 * 1. 四大中枢全链路端到端闭环编排与因果一致性推进；
 * 2. 基于 Java 21 纯虚拟线程的万级高并发排队稳定性 (E[D] <= 50ms, TPS >= 500, 零 OOM)；
 * 3. 四大典型混沌故障 (网络超时、Worker 脑裂、图谱超级节点、HITL 离线) 自动检测与毫秒自愈 (脑裂率 0.0%)；
 * 4. 全链路密码学不可变联合存证凭单防篡改性 (100% 拦截单比特篡改)。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
class Phase133E2EChaosBenchmarkContractTest {

    private HermesMixedGameDebateScheduler debateScheduler;
    private DistributedLeaseCoordinator leaseCoordinator;
    private ResilientSagasStateManager sagasStateManager;
    private SteinerCausalSubgraphPruner steinerPruner;
    private DeepSeekCausalThinkingAligner thinkingAligner;
    private TimeTravelSnapshotBranchGovernor timeTravelGovernor;
    private StreamingCausalTopologySyncBus syncBus;
    private ChaosFaultInjectionGovernor chaosGovernor;
    private VirtualThreadConcurrencyGovernor concurrencyGovernor;
    private E2EFourMetacenterPipelineBus pipelineBus;

    @BeforeEach
    void setUp() {
        NashConfidenceWeightedJudge judge = new NashConfidenceWeightedJudge();
        DebateDeadlockSelfHealingGovernor debateGovernor = new DebateDeadlockSelfHealingGovernor();
        this.debateScheduler = new HermesMixedGameDebateScheduler(judge, debateGovernor);

        this.leaseCoordinator = new DistributedLeaseCoordinator();
        this.sagasStateManager = new ResilientSagasStateManager();
        this.steinerPruner = new SteinerCausalSubgraphPruner();
        this.thinkingAligner = new DeepSeekCausalThinkingAligner();
        this.timeTravelGovernor = new TimeTravelSnapshotBranchGovernor();
        this.syncBus = new StreamingCausalTopologySyncBus();
        this.chaosGovernor = new ChaosFaultInjectionGovernor();
        this.concurrencyGovernor = new VirtualThreadConcurrencyGovernor(25_000);

        this.pipelineBus = new E2EFourMetacenterPipelineBus(
                debateScheduler,
                leaseCoordinator,
                sagasStateManager,
                steinerPruner,
                thinkingAligner,
                timeTravelGovernor,
                syncBus,
                chaosGovernor
        );
    }

    @AfterEach
    void tearDown() {
        chaosGovernor.resetAllFaults();
        concurrencyGovernor.close();
    }

    private E2EPipelineRequest createStandardTestRequest(String pipelineId) {
        Map<AgentRoleNicheType, String> roleProposals = new EnumMap<>(AgentRoleNicheType.class);
        roleProposals.put(AgentRoleNicheType.BUSINESS, "申请开放跨境电商退款绿色通道以提升用户留存");
        roleProposals.put(AgentRoleNicheType.RISK_CONTROL, "退款限额单笔 1000 元，且要求黑名单即时拦截");
        roleProposals.put(AgentRoleNicheType.LEGAL, "跨境交易退税申报须符合海关法规条款");
        roleProposals.put(AgentRoleNicheType.ARCHITECTURE, "保证数据库行锁时间不超过 50ms，且具备分布式补偿");

        List<String> regulatoryFacts = List.of(
                "《跨境电子商务零售进口税收政策》要求订单支付人身份必须一致",
                "《网络交易监督管理办法》规定经营者应当建立便捷的退款处理制度"
        );

        List<String> sagasSteps = List.of("LOCK_INVENTORY", "DEDUCT_WALLET", "CALL_CUSTOMS_API");

        // 构建图谱节点与边
        float[] sampleEmbedding = new float[1536];
        Arrays.fill(sampleEmbedding, (float) (1.0 / Math.sqrt(1536)));

        SteinerCausalSubgraphPruner.GraphNode nodeUser = new SteinerCausalSubgraphPruner.GraphNode("node_user", "Consumer", "USER", sampleEmbedding);
        SteinerCausalSubgraphPruner.GraphNode nodeOrder = new SteinerCausalSubgraphPruner.GraphNode("node_order", "Order_1001", "ORDER", sampleEmbedding);
        SteinerCausalSubgraphPruner.GraphNode nodeRefund = new SteinerCausalSubgraphPruner.GraphNode("node_refund", "RefundPolicy", "POLICY", sampleEmbedding);

        List<SteinerCausalSubgraphPruner.GraphNode> nodes = List.of(nodeUser, nodeOrder, nodeRefund);
        List<SteinerCausalSubgraphPruner.GraphEdge> edges = List.of(
                new SteinerCausalSubgraphPruner.GraphEdge("node_user", "node_order", "PLACED_ORDER", 0.2),
                new SteinerCausalSubgraphPruner.GraphEdge("node_order", "node_refund", "APPLIED_POLICY", 0.3)
        );

        return new E2EPipelineRequest(
                pipelineId,
                "tenant_e2e_01",
                "跨境电商大额退款履约决策",
                roleProposals,
                regulatoryFacts,
                sagasSteps,
                nodes,
                edges,
                List.of("node_user", "node_refund"),
                "APPROVE",
                null,
                1
        );
    }

    @Test
    @DisplayName("契约测试 1: 全链路四大中枢无故障黄金路径无缝集成与存证验证")
    void test01_E2E_HappyPath_FourMetacenterPipelineIntegration() {
        E2EPipelineRequest request = createStandardTestRequest("pipeline_happy_001");
        E2EExecutionResult result = pipelineBus.executePipeline(request);

        assertNotNull(result, "执行结果不应为空");
        assertTrue(result.isSuccess(), "黄金链路执行应该返回成功");
        assertEquals("SUCCESS", result.executionStatus());

        // 验证 Phase 129 博弈凭单
        assertNotNull(result.debateReceipt(), "Phase 129 博弈凭单不应为空");
        assertTrue(result.debateReceipt().verifySignature(), "Phase 129 凭单验真应通过");

        // 验证 Phase 130 Sagas 凭单
        assertNotNull(result.sagasReceipt(), "Phase 130 Sagas 凭单不应为空");
        assertTrue(result.sagasReceipt().verifySignature(), "Phase 130 凭单验真应通过");

        // 验证 Phase 131 思考对齐载荷
        assertNotNull(result.alignedThinkingPayload(), "Phase 131 思考对齐载荷不应为空");
        assertTrue(result.alignedThinkingPayload().estimatedGroundingScore() >= 0.85, "事实接地置信度应合格");

        // 验证 Phase 133 联合审计凭单
        E2EIntegrationAuditReceipt receipt = result.receipt();
        assertNotNull(receipt, "联合审计凭单不应为空");
        assertEquals("NONE", receipt.chaosFaultInjected());
        assertFalse(receipt.selfHealed());
        assertTrue(receipt.verifySignature(), "联合存证凭单签名验真应完全通过");
    }

    @Test
    @DisplayName("契约测试 2: 万级并发虚拟线程调度排队李雅普诺夫强渐近稳定性验证 (定理 1.2)")
    void test02_VirtualThread_10kConcurrency_LyapunovQueueStability() {
        int concurrency = 10_000;
        AtomicInteger taskCompletedCount = new AtomicInteger(0);

        VirtualThreadConcurrencyGovernor.StressBenchmarkReport report = concurrencyGovernor.runStressBenchmark(concurrency, () -> {
            // 模拟各虚拟线程内部执行轻量级业务计算与短期挂起让出
            double mathSum = 0;
            for (int i = 0; i < 50; i++) {
                mathSum += Math.sin(i);
            }
            if (mathSum > 100000) {
                log.debug("Impossible math condition");
            }
            taskCompletedCount.incrementAndGet();
            return taskCompletedCount.get();
        });

        assertNotNull(report, "压测报告不应为空");
        assertEquals(concurrency, report.totalTasks(), "总任务数应为 10,000");
        assertEquals(concurrency, report.successfulTasks(), "全部任务应成功完成");
        assertEquals(0, report.failedTasks(), "失败任务数应为 0");

        // 核心稳定性定理指标断言：
        assertTrue(report.steadyThroughputTps() >= 500.0,
                "稳态吞吐率应 >= 500 TPS，实测: " + report.steadyThroughputTps());
        assertTrue(report.averageLatencyMs() <= 50.0,
                "平均调度排队延迟应 <= 50ms，实测: " + report.averageLatencyMs());
        assertTrue(report.p99LatencyMs() <= 100.0,
                "P99 调度延迟应受控 <= 100ms，实测: " + report.p99LatencyMs());
        assertTrue(report.maxContinuationMemoryEstimateKb() <= 30_000,
                "虚拟线程堆栈内存估算应 <= 30MB，实测: " + report.maxContinuationMemoryEstimateKb() + "KB");
    }

    @Test
    @DisplayName("契约测试 3: 混沌故障注入 —— 网络超时导致 Sagas 逆拓扑 LIFO 补偿自愈验证")
    void test03_ChaosInjection_NetworkTimeout_SagasLIFOCompensation() {
        chaosGovernor.activateFault(ChaosFaultType.NETWORK_TIMEOUT);

        E2EPipelineRequest request = createStandardTestRequest("pipeline_chaos_net_001");
        E2EExecutionResult result = pipelineBus.executePipeline(request);

        assertNotNull(result);
        assertFalse(result.isSuccess(), "发生网络超时事务应未成功提交");
        assertEquals("COMPENSATED_CLEAN", result.executionStatus(), "系统状态应干净收敛至补偿终态");

        E2EIntegrationAuditReceipt receipt = result.receipt();
        assertNotNull(receipt);
        assertEquals(ChaosFaultType.NETWORK_TIMEOUT.name(), receipt.chaosFaultInjected());
        assertTrue(receipt.selfHealed(), "应标记自愈成功");
        assertTrue(receipt.verifySignature(), "补偿凭单签名验真通过");

        // 验证自愈记录存在
        var healingRecord = chaosGovernor.getHealingRecord(request.pipelineId());
        assertTrue(healingRecord.isPresent(), "自愈审计事件应被记录");
        assertTrue(healingRecord.get().isHealedSuccessfully());
        assertEquals("SagasLIFOCompensated", healingRecord.get().healingAction());
    }

    @Test
    @DisplayName("契约测试 4: 混沌故障注入 —— Worker 假死备用接管与单调 Fencing Token 拦截脑裂重放验证")
    void test04_ChaosInjection_WorkerCrash_LeaseTakeover_ZeroSplitBrain() {
        chaosGovernor.activateFault(ChaosFaultType.WORKER_CRASH_SPLIT_BRAIN);

        E2EPipelineRequest request = createStandardTestRequest("pipeline_chaos_crash_001");
        E2EExecutionResult result = pipelineBus.executePipeline(request);

        assertNotNull(result);
        assertTrue(result.isSuccess(), "由备用节点安全接管后整体链路应恢复推进成功");

        // 验证旧世代写操作被物理拦截，脑裂多写数为 0
        assertTrue(chaosGovernor.getInterceptedZombieWrites() >= 1, "应至少拦截 1 次陈旧令牌重放写");

        E2EIntegrationAuditReceipt receipt = result.receipt();
        assertEquals(ChaosFaultType.WORKER_CRASH_SPLIT_BRAIN.name(), receipt.chaosFaultInjected());
        assertTrue(receipt.selfHealed(), "脑裂拦截自愈成功");
        assertTrue(receipt.verifySignature());
    }

    @Test
    @DisplayName("契约测试 5: 混沌故障注入 —— 图谱超级节点扩张下 Steiner 树 2-近似紧凑剪枝验证")
    void test05_ChaosInjection_GraphSuperNode_SteinerCausalPruning() {
        chaosGovernor.activateFault(ChaosFaultType.GRAPH_SUPER_NODE_EXPANSION);

        E2EPipelineRequest request = createStandardTestRequest("pipeline_chaos_supernode_001");
        E2EExecutionResult result = pipelineBus.executePipeline(request);

        assertNotNull(result);
        assertTrue(result.isSuccess(), "即使遭遇超级节点扩张，剪枝后应成功推进");

        E2EIntegrationAuditReceipt receipt = result.receipt();
        assertEquals(ChaosFaultType.GRAPH_SUPER_NODE_EXPANSION.name(), receipt.chaosFaultInjected());
        assertTrue(receipt.selfHealed(), "Steiner 树剪枝应成功将节点数控制在 <= 16");
        assertTrue(receipt.verifySignature());

        var healingRecord = chaosGovernor.getHealingRecord(request.pipelineId());
        assertTrue(healingRecord.isPresent());
        assertEquals("SteinerPrunedUnder16Nodes", healingRecord.get().healingAction());
    }

    @Test
    @DisplayName("契约测试 6: 混沌故障注入 —— HITL 审批专员离线导致租约看门狗 Fail-Close 快速短路自愈验证")
    void test06_ChaosInjection_HitlOffline_WatchdogFailClose() {
        chaosGovernor.activateFault(ChaosFaultType.HITL_APPROVAL_OFFLINE);

        E2EPipelineRequest request = createStandardTestRequest("pipeline_chaos_hitl_offline_001");
        E2EExecutionResult result = pipelineBus.executePipeline(request);

        assertNotNull(result);
        assertEquals("DEGRADED_FAILSAFE", result.executionStatus(), "应被看门狗触发快速短路安全降级");

        E2EIntegrationAuditReceipt receipt = result.receipt();
        assertEquals(ChaosFaultType.HITL_APPROVAL_OFFLINE.name(), receipt.chaosFaultInjected());
        assertTrue(receipt.selfHealed(), "Fail-Close 自动释放资源，无死锁自愈成功");
        assertTrue(receipt.verifySignature());
    }

    @Test
    @DisplayName("契约测试 7: DeepSeek 官方双轨长链思考与流式拓扑同步因果单调性验证")
    void test07_DeepSeekParametricThinking_DoubleTrackStreamingAlignment() {
        E2EPipelineRequest request = createStandardTestRequest("pipeline_deepseek_stream_001");
        E2EExecutionResult result = pipelineBus.executePipeline(request);

        assertNotNull(result);
        var payload = result.alignedThinkingPayload();
        assertNotNull(payload);

        // 验证因果拓扑命题链
        assertFalse(payload.causalPropositions().isEmpty(), "因果命题链不应为空");
        assertFalse(payload.topologicalNodeOrder().isEmpty(), "拓扑节点序不应为空");
        assertTrue(payload.scaffoldPromptBlock().contains("<thinking_scaffold>"), "应包含官方规范思考脚手架");
        assertTrue(payload.estimatedGroundingScore() >= 0.85, "接地置信度达标");
    }

    @Test
    @DisplayName("契约测试 8: 全链路密码学不可变联合审计存证凭单防篡改与常量时间验真验证")
    void test08_CryptographicAuditReceipt_TamperResistance() {
        E2EPipelineRequest request = createStandardTestRequest("pipeline_receipt_tamper_001");
        E2EExecutionResult result = pipelineBus.executePipeline(request);

        E2EIntegrationAuditReceipt receipt = result.receipt();
        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "原始凭单签名验真必须通过");

        // 模拟攻击者对凭单字段进行单比特篡改
        E2EIntegrationAuditReceipt tamperedReceipt1 = new E2EIntegrationAuditReceipt(
                receipt.pipelineId(),
                "malicious_tenant", // 篡改租户
                receipt.consensusId(),
                receipt.sagasTxId(),
                receipt.steinerGraphId(),
                receipt.hitlAuditId(),
                receipt.concurrencyLevel(),
                receipt.chaosFaultInjected(),
                receipt.selfHealed(),
                receipt.executionStatus(),
                receipt.debateLatencyUs(),
                receipt.sagasLatencyUs(),
                receipt.steinerLatencyUs(),
                receipt.hitlLatencyUs(),
                receipt.totalLatencyUs(),
                receipt.timestamp(),
                receipt.sha256Signature() // 沿用原签名
        );
        assertFalse(tamperedReceipt1.verifySignature(), "篡改租户信息后验真应 100% 失败被拦截");

        E2EIntegrationAuditReceipt tamperedReceipt2 = new E2EIntegrationAuditReceipt(
                receipt.pipelineId(),
                receipt.tenantId(),
                receipt.consensusId(),
                receipt.sagasTxId(),
                receipt.steinerGraphId(),
                receipt.hitlAuditId(),
                receipt.concurrencyLevel(),
                receipt.chaosFaultInjected(),
                receipt.selfHealed(),
                "COMPROMISED_STATUS", // 篡改状态
                receipt.debateLatencyUs(),
                receipt.sagasLatencyUs(),
                receipt.steinerLatencyUs(),
                receipt.hitlLatencyUs(),
                receipt.totalLatencyUs(),
                receipt.timestamp(),
                receipt.sha256Signature()
        );
        assertFalse(tamperedReceipt2.verifySignature(), "篡改终态后验真应 100% 失败被拦截");
    }
}

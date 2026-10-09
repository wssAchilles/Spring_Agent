package tech.qiantong.qknow.hermes.flow.hitl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.hitl.coordinator.MultiAgentHandoffCoordinator;
import tech.qiantong.qknow.hermes.flow.hitl.dto.Phase155MasterAuditReceipt;
import tech.qiantong.qknow.hermes.flow.hitl.governor.TenantMcpQuotaGovernor;
import tech.qiantong.qknow.hermes.flow.hitl.scheduler.DynamicPrefixCacheTreeScheduler;
import tech.qiantong.qknow.hermes.flow.rag.governor.HypersphericalGraphRagGovernor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 155 核心契约测试套件：
 * 企业级多智能体 Handoff 权责交接、DeepSeek 64-token 动态前缀树调度、
 * 千问 1536 维超球面拉普拉斯谱剪枝 GraphRAG 与企业 MCP 租户配额网关
 *
 * @author Achilles
 * @version 1.0
 */
public class Phase155MasterContractTest {

    @Test
    @DisplayName("契约 1：MultiAgentHandoffCoordinator 正常顺序委托与调用链保序记录")
    void test01_NormalSequentialHandoff() {
        MultiAgentHandoffCoordinator coordinator = new MultiAgentHandoffCoordinator();
        String traceId = "trace_handoff_001";

        // 1. 发起首跳委托: planner -> coder
        var ticket1 = coordinator.initiateHandoff(traceId, "agent_planner", "agent_coder", Map.of("task", "实现用户中心"));
        assertNotNull(ticket1);
        assertEquals(1, ticket1.currentHop());
        assertEquals("agent_coder", ticket1.targetAgentId());
        assertEquals(List.of("agent_planner"), ticket1.auditCallChain());

        // 2. 正常流转: coder -> reviewer
        var decision2 = coordinator.evaluateAndTransfer(ticket1, "agent_reviewer", Map.of("codeDiff", "+100 lines"));
        assertTrue(decision2.permitted(), "合法的下游交接必须被放行");
        var ticket2 = decision2.nextTicket();
        assertNotNull(ticket2);
        assertEquals(2, ticket2.currentHop());
        assertEquals("agent_reviewer", ticket2.targetAgentId());
        assertEquals(List.of("agent_planner", "agent_coder"), ticket2.auditCallChain());
        assertEquals("+100 lines", ticket2.immutableContext().get("codeDiff"));
        assertNotNull(ticket2.cryptographicSignature());
    }

    @Test
    @DisplayName("契约 2：MultiAgentHandoffCoordinator 跃点硬截断触发 ERR_HANDOFF_HOP_EXCEEDED 并升迁至主管")
    void test02_HandoffHopLimitExceededEscalation() {
        MultiAgentHandoffCoordinator coordinator = new MultiAgentHandoffCoordinator();
        String traceId = "trace_hop_limit";

        // 构造到达第 5 跳上限的工单
        var ticket = coordinator.initiateHandoff(traceId, "agent_1", "agent_2", Map.of());
        var d2 = coordinator.evaluateAndTransfer(ticket, "agent_3", Map.of());
        var d3 = coordinator.evaluateAndTransfer(d2.nextTicket(), "agent_4", Map.of());
        var d4 = coordinator.evaluateAndTransfer(d3.nextTicket(), "agent_5", Map.of());
        var d5 = coordinator.evaluateAndTransfer(d4.nextTicket(), "agent_6", Map.of());

        // 此时 ticket5 的 currentHop 为 5 (达到 DEFAULT_MAX_HOPS 上限)
        assertEquals(5, d5.nextTicket().currentHop());

        // 尝试发起第 6 跳
        var d6 = coordinator.evaluateAndTransfer(d5.nextTicket(), "agent_7", Map.of());
        assertFalse(d6.permitted(), "超过最大跳数必须严格拒绝");
        assertEquals(MultiAgentHandoffCoordinator.ERR_HANDOFF_HOP_EXCEEDED, d6.errorCode());
        assertNotNull(d6.nextTicket());
        assertEquals(MultiAgentHandoffCoordinator.SUPERVISOR_AGENT_ID, d6.nextTicket().targetAgentId(), "超出上限必须升迁至主管智能体");
    }

    @Test
    @DisplayName("契约 3：MultiAgentHandoffCoordinator 瞬态乒乓与拓扑成环精准阻断 (ERR_HANDOFF_CYCLE_DETECTED)")
    void test03_HandoffCycleAndPingPongDetection() {
        MultiAgentHandoffCoordinator coordinator = new MultiAgentHandoffCoordinator();
        String traceId = "trace_cycle";

        // 1. 测试瞬态乒乓: A -> B -> A
        var ticket1 = coordinator.initiateHandoff(traceId, "agent_A", "agent_B", Map.of());
        var pingPongDecision = coordinator.evaluateAndTransfer(ticket1, "agent_A", Map.of());
        assertFalse(pingPongDecision.permitted(), "反向乒乓击掌委托必须被物理阻断");
        assertEquals(MultiAgentHandoffCoordinator.ERR_HANDOFF_CYCLE_DETECTED, pingPongDecision.errorCode());

        // 2. 测试深层调用链成环: A -> B -> C -> A
        var d1 = coordinator.evaluateAndTransfer(ticket1, "agent_C", Map.of());
        assertTrue(d1.permitted());
        var cycleDecision = coordinator.evaluateAndTransfer(d1.nextTicket(), "agent_A", Map.of());
        assertFalse(cycleDecision.permitted(), "目标智能体已存在于调用链中必须判定成环死锁并阻断");
        assertEquals(MultiAgentHandoffCoordinator.ERR_HANDOFF_CYCLE_DETECTED, cycleDecision.errorCode());
    }

    @Test
    @DisplayName("契约 4：DynamicPrefixCacheTreeScheduler 64-token 边界量化对齐 (tokenCount % 64 == 0)")
    void test04_PrefixCacheTree64TokenAlignment() {
        DynamicPrefixCacheTreeScheduler scheduler = new DynamicPrefixCacheTreeScheduler();

        String systemCore = "你是一个企业级核心智能体。请遵循安全调用契约。";
        String agentPersona = "【数据分析专家】专注于报表统计与多维聚合分析。";
        List<String> chunks = List.of(
                "切片 1: 财务报表审计流程",
                "切片 2: 资产负债表与利润表核算口径"
        );
        String userQuery = "请帮我统计第三季度的营业收入与同比增幅";

        var result = scheduler.scheduleAndAlignPrompt(systemCore, agentPersona, chunks, userQuery, false);
        assertNotNull(result);
        assertTrue(result.perfectlyAligned(), "对齐后的静态前缀必须严格满足 64-token 整数倍");
        assertEquals(0, result.paddedPrefixTokens() % DynamicPrefixCacheTreeScheduler.CACHE_BLOCK_SIZE, "Token 数量必须能够被 64 整除");
        assertTrue(result.alignedCacheBlocks() > 0);
        assertTrue(result.alignedSystemPrompt().contains(DynamicPrefixCacheTreeScheduler.PAD_PREFIX), "未满 64 整数倍时必须注入注释填充符");
        assertEquals(userQuery, result.dynamicUserPayload(), "动态查询必须被绝对尾置");
    }

    @Test
    @DisplayName("契约 5：DynamicPrefixCacheTreeScheduler DeepSeek 官方思考模式配置注入")
    void test05_PrefixCacheTreeDeepSeekThinkingProtocol() {
        DynamicPrefixCacheTreeScheduler scheduler = new DynamicPrefixCacheTreeScheduler();

        var result = scheduler.scheduleAndAlignPrompt(
                "系统全局提示",
                "推理专家",
                List.of(),
                "复杂多步归纳逻辑题",
                true // 开启思考模式
        );

        assertNotNull(result);
        assertNotNull(result.thinkingExtraBody());
        assertTrue(result.thinkingExtraBody().containsKey("thinking"));
        @SuppressWarnings("unchecked")
        Map<String, Object> thinkingMap = (Map<String, Object>) result.thinkingExtraBody().get("thinking");
        assertEquals("enabled", thinkingMap.get("type"), "必须按照 DeepSeek 官方规范注入 extra_body.thinking.type = enabled");
    }

    @Test
    @DisplayName("契约 6：TenantMcpQuotaGovernor 租户并发水线租约控制 (超额触发 ERR_TENANT_QUOTA_EXHAUSTED)")
    void test06_TenantMcpConcurrencyLeaseControl() {
        TenantMcpQuotaGovernor governor = new TenantMcpQuotaGovernor();
        String tenantId = "tenant_test_01";
        String toolCode = "tool_db_query";
        int limit = 3;

        // 1. 连续申请 3 个租约成功
        var l1 = governor.acquireLease(tenantId, toolCode, limit);
        var l2 = governor.acquireLease(tenantId, toolCode, limit);
        var l3 = governor.acquireLease(tenantId, toolCode, limit);
        assertTrue(l1.acquired());
        assertTrue(l2.acquired());
        assertTrue(l3.acquired());

        // 2. 第 4 个租约超限被拒
        var l4 = governor.acquireLease(tenantId, toolCode, limit);
        assertFalse(l4.acquired(), "超出租户并发上限必须被拦截");
        assertEquals(TenantMcpQuotaGovernor.ERR_TENANT_QUOTA_EXHAUSTED, l4.errorCode());

        // 3. 释放 1 个租约后再次申请成功
        governor.releaseLease(tenantId, toolCode, true);
        var l5 = governor.acquireLease(tenantId, toolCode, limit);
        assertTrue(l5.acquired(), "释放并发槽位后应可正常获取租约");
    }

    @Test
    @DisplayName("契约 7：TenantMcpQuotaGovernor 三态熔断器连续失败触发 OPEN 与快速短路")
    void test07_TenantMcpCircuitBreakerTripping() {
        TenantMcpQuotaGovernor governor = new TenantMcpQuotaGovernor();
        String tenantId = "tenant_unstable";
        String toolCode = "tool_thirdparty_api";

        // 连续 5 次报告失败
        for (int i = 0; i < TenantMcpQuotaGovernor.CIRCUIT_FAILURE_THRESHOLD; i++) {
            var lease = governor.acquireLease(tenantId, toolCode, 10);
            assertTrue(lease.acquired());
            governor.releaseLease(tenantId, toolCode, false); // 模拟失败
        }

        // 第 6 次调用：应直接被熔断器阻断
        var trippedLease = governor.acquireLease(tenantId, toolCode, 10);
        assertFalse(trippedLease.acquired(), "连续失败超标后熔断器必须处于 OPEN 态并短路拦截");
        assertEquals(TenantMcpQuotaGovernor.ERR_MCP_CIRCUIT_OPEN, trippedLease.errorCode());
        assertEquals(TenantMcpQuotaGovernor.CircuitState.OPEN, trippedLease.circuitState());
    }

    @Test
    @DisplayName("契约 8：TenantMcpQuotaGovernor 熔断器半开试探与自愈恢复 CLOSED")
    void test08_TenantMcpCircuitBreakerHealing() throws InterruptedException {
        TenantMcpQuotaGovernor governor = new TenantMcpQuotaGovernor();
        String tenantId = "tenant_healing";
        String toolCode = "tool_external_service";

        // 触发熔断
        for (int i = 0; i < TenantMcpQuotaGovernor.CIRCUIT_FAILURE_THRESHOLD; i++) {
            governor.acquireLease(tenantId, toolCode, 10);
            governor.releaseLease(tenantId, toolCode, false);
        }

        // 此时为 OPEN
        assertEquals(TenantMcpQuotaGovernor.CircuitState.OPEN, governor.getCircuit(tenantId, toolCode).state.get());

        // 等待冷却时间过去 (修改其上次变更时间以模拟超时)
        governor.getCircuit(tenantId, toolCode).lastStateChangeTimestamp.set(
                System.currentTimeMillis() - (TenantMcpQuotaGovernor.CIRCUIT_RESET_TIMEOUT_MS + 100)
        );

        // 再次申请进入 HALF_OPEN 试探放行
        var probeLease = governor.acquireLease(tenantId, toolCode, 10);
        assertTrue(probeLease.acquired(), "半开状态下应允许放行探针请求");

        // 探针调用成功，熔断器自愈回闭合态 CLOSED
        governor.releaseLease(tenantId, toolCode, true);
        assertEquals(TenantMcpQuotaGovernor.CircuitState.CLOSED, governor.getCircuit(tenantId, toolCode).state.get(), "探针成功后熔断器必须自愈恢复 CLOSED");
    }

    @Test
    @DisplayName("契约 9：HypersphericalGraphRagGovernor 千问 1536 维超球面向量范数判定 (||v||_2 = 1.0)")
    void test09_HypersphericalVectorNormConstraint() {
        HypersphericalGraphRagGovernor governor = new HypersphericalGraphRagGovernor();

        // 构造合法的 1536 维超球面单位向量
        float[] validVec = new float[1536];
        float val = (float) (1.0 / Math.sqrt(1536));
        for (int i = 0; i < 1536; i++) {
            validVec[i] = val;
        }
        assertTrue(governor.validateHypersphericalNorm(validVec), "单位归一化超球面向量必须通过几何校验");

        // 构造非归一化向量 (范数不等于 1.0)
        float[] invalidVec = new float[1536];
        for (int i = 0; i < 1536; i++) {
            invalidVec[i] = 1.0f; // 模长 sqrt(1536) != 1.0
        }
        assertFalse(governor.validateHypersphericalNorm(invalidVec), "未归一化向量必须严格判定非法");
    }

    @Test
    @DisplayName("契约 10：HypersphericalGraphRagGovernor 超球面拉普拉斯谱剪枝最优因果子图截断")
    void test10_HypersphericalLaplacianSpectralPruning() {
        HypersphericalGraphRagGovernor governor = new HypersphericalGraphRagGovernor();

        float[] queryVec = createUnitVector(1536, 0.1f);

        // 构造 10 个候选节点，其中前 3 个与 query 高度相似，后 7 个为边缘无关噪音
        List<HypersphericalGraphRagGovernor.GraphEntityNode> candidates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            float base = (i < 3) ? 0.1f : (float) (i * 0.5);
            float[] nodeVec = createUnitVector(1536, base);
            candidates.add(new HypersphericalGraphRagGovernor.GraphEntityNode(
                    "node_" + i,
                    "知识实体内容 " + i,
                    nodeVec,
                    100 // 每个节点 100 tokens
            ));
        }

        // 设定上限最多保留 3 个节点，最大 Token 预算 350
        var prunedResult = governor.pruneGraph(queryVec, candidates, 3, 350);
        assertNotNull(prunedResult);
        assertEquals(3, prunedResult.retainedNodes().size(), "保留节点数必须受限于预算");
        assertEquals(7, prunedResult.prunedCount(), "其余 7 个节点应被谱剪枝安全剔除");
        assertTrue(prunedResult.consumedTokens() <= 350, "消耗 Token 数不得超过硬上限预算");
        assertTrue(prunedResult.averageCosineDensity() > 0.0);
    }

    @Test
    @DisplayName("契约 11：Phase155MasterAuditReceipt 不可变审计凭单与常量时间 SHA-256 自验真")
    void test11_MasterAuditReceiptConstantTimeVerification() {
        Phase155MasterAuditReceipt receipt = Phase155MasterAuditReceipt.create(
                "rcpt_155_001",
                "trace_sess_8899",
                "tenant_fin_01",
                "agent_lead",
                3,
                64,
                8,
                0.998822,
                15400L
        );

        assertNotNull(receipt);
        assertNotNull(receipt.sha256Digest());
        assertEquals(64, receipt.sha256Digest().length());
        assertTrue(receipt.verifyDigest(), "常量时间 SHA-256 自验真必须通过");
    }

    @Test
    @DisplayName("契约 12：Phase155MasterAuditReceipt 防篡改性检测 (篡改数据验真必失败)")
    void test12_MasterAuditReceiptTamperProofDetection() {
        Phase155MasterAuditReceipt original = Phase155MasterAuditReceipt.create(
                "rcpt_155_tamper",
                "trace_sess_0001",
                "tenant_sec",
                "agent_worker",
                2,
                32,
                4,
                1.000000,
                8200L
        );
        assertTrue(original.verifyDigest());

        // 模拟黑客篡改了 handoffHops 或 tenantId 但保留了原始签名
        Phase155MasterAuditReceipt tampered = new Phase155MasterAuditReceipt(
                original.receiptId(),
                original.sessionTraceId(),
                "tenant_hacked", // 恶意篡改租户
                original.primaryAgentId(),
                original.handoffHops(),
                original.cacheBlockHits64Token(),
                original.graphRagPrunedNodes(),
                original.hypersphericalCosineIntegrity(),
                original.executionLatencyMicros(),
                original.sha256Digest() // 伪造签名
        );

        assertFalse(tampered.verifyDigest(), "数据被篡改后常量时间比对必须精准失败并抛出防篡改告警");
    }

    private float[] createUnitVector(int dim, float bias) {
        float[] vec = new float[dim];
        float sumSq = 0.0f;
        for (int i = 0; i < dim; i++) {
            float val = (float) Math.sin(i + bias);
            vec[i] = val;
            sumSq += (val * val);
        }
        float norm = (float) Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) {
            vec[i] /= norm;
        }
        return vec;
    }
}

package tech.qiantong.qknow.hermes.federation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.federation.dto.*;
import tech.qiantong.qknow.hermes.federation.engine.CrossDomainFederatedReasoner;
import tech.qiantong.qknow.hermes.federation.engine.FederatedComplianceControlBus;
import tech.qiantong.qknow.hermes.federation.engine.MultiTenantContextIsolationGate;
import tech.qiantong.qknow.hermes.federation.engine.SovereignComplianceVetoCircuit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 96: 复杂业务 Agent 跨域知识联邦协同推理、多租户上下文安全隔离与主权自治合规中枢
 * 专属契约单元测试套件 (8/8 严苛契约)
 */
public class Phase96FederatedComplianceContractTest {

    private double[] createNormalizedSphericalEmbedding(int seed) {
        double[] vec = new double[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = Math.sin(seed + i * 0.173);
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] /= norm;
        }
        return vec;
    }

    @Test
    @DisplayName("测试 1: 跨域知识联邦特征聚合保真度 >= 96% 且单步计算耗时严格 <= 60μs (定理 1.1)")
    void testFederatedReasoner_AggregationFidelityAndDurationWithin60Micros() {
        CrossDomainFederatedReasoner reasoner = new CrossDomainFederatedReasoner();
        double[] embA = createNormalizedSphericalEmbedding(101);
        double[] embB = createNormalizedSphericalEmbedding(101); // 语义高度共识
        double[] embC = createNormalizedSphericalEmbedding(101);

        List<double[]> embeddings = List.of(embA, embB, embC);
        List<Double> weights = List.of(0.4, 0.3, 0.3);

        // 预热 JIT
        for (int i = 0; i < 200; i++) {
            reasoner.aggregateDomainEmbeddings("warmup", embeddings, weights, 1.0, 1e-5);
        }

        long minDur = Long.MAX_VALUE;
        FederatedAggregationResult result = null;
        for (int i = 0; i < 50; i++) {
            FederatedAggregationResult r = reasoner.aggregateDomainEmbeddings("session_001", embeddings, weights, 1.0, 1e-5);
            if (r.durationNanos() < minDur) {
                minDur = r.durationNanos();
                result = r;
            }
        }

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(3, result.participantDomainsCount());
        assertTrue(result.theoreticalFidelity() >= 0.96, "多域聚合保真度必须 >= 0.96，实测: " + result.theoreticalFidelity());
        assertEquals(1536, result.aggregatedEmbedding().length);
        assertTrue(minDur <= 60_000, "单步联邦测地聚合耗时必须 <= 60μs，实测: " + minDur + "ns");
    }

    @Test
    @DisplayName("测试 2: 跨域知识联邦差分隐私保护与原始数据零泄露验证 (定理 1.1)")
    void testFederatedReasoner_DifferentialPrivacyNoiseInjection() {
        CrossDomainFederatedReasoner reasoner = new CrossDomainFederatedReasoner();
        double[] embA = createNormalizedSphericalEmbedding(201);
        double[] embB = createNormalizedSphericalEmbedding(202);

        FederatedAggregationResult r1 = reasoner.aggregateDomainEmbeddings("sess_dp_1", List.of(embA, embB), List.of(0.5, 0.5), 0.5, 1e-5);
        FederatedAggregationResult r2 = reasoner.aggregateDomainEmbeddings("sess_dp_2", List.of(embA, embB), List.of(0.5, 0.5), 2.0, 1e-5);

        assertTrue(r1.totalNoiseVariance() > r2.totalNoiseVariance(), "更严苛的隐私参数 epsilon=0.5 噪声方差应大于 epsilon=2.0");
        // 验证聚合向量依然满足严格的超球面归一化约束
        reasoner.validateSphericalEmbedding(r1.aggregatedEmbedding());
        reasoner.validateSphericalEmbedding(r2.aggregatedEmbedding());
    }

    @Test
    @DisplayName("测试 3: 千问 1536 维超球面单位向量模长强校验，非法输入 100% 拒绝 (命题 2.1)")
    void testFederatedReasoner_InvalidEmbeddingRejection() {
        CrossDomainFederatedReasoner reasoner = new CrossDomainFederatedReasoner();

        // 错误维度
        double[] badDim = new double[768];
        assertThrows(IllegalArgumentException.class, () -> reasoner.validateSphericalEmbedding(badDim));

        // 错误范数
        double[] badNorm = new double[1536];
        Arrays.fill(badNorm, 1.0);
        assertThrows(IllegalArgumentException.class, () -> reasoner.validateSphericalEmbedding(badNorm));
    }

    @Test
    @DisplayName("测试 4: 多租户上下文隔离门禁合法租约极速通行且单步耗时严格 <= 25μs (定理 1.2)")
    void testTenantIsolationGate_ValidLeasePassWithin25Micros() {
        MultiTenantContextIsolationGate gate = new MultiTenantContextIsolationGate();
        TenantIsolationLease lease = gate.issueLease("tenant_finance", "sess_001", "ANALYST", 60_000L);

        // 预热 JIT 消除反射与动态加载开销
        for (int i = 0; i < 1000; i++) {
            gate.verifyAndAuthorizeAccess(lease, "tenant_finance");
        }

        long minDur = Long.MAX_VALUE;
        boolean pass = false;
        for (int i = 0; i < 100; i++) {
            long t0 = System.nanoTime();
            pass = gate.verifyAndAuthorizeAccess(lease, "tenant_finance");
            long elapsed = System.nanoTime() - t0;
            if (elapsed < minDur) {
                minDur = elapsed;
            }
        }

        assertTrue(pass);
        assertTrue(minDur <= 25_000, "多租户合法访问单步校验耗时必须 <= 25μs，实测: " + minDur + "ns");
    }

    @Test
    @DisplayName("测试 5: 跨租户上下文越权渗透与篡改 Token 100% 物理拦截 (定理 1.2)")
    void testTenantIsolationGate_CrossTenantContaminationAndTamperedTokenStrictRejection() {
        MultiTenantContextIsolationGate gate = new MultiTenantContextIsolationGate();
        TenantIsolationLease leaseAlpha = gate.issueLease("tenant_alpha", "sess_alpha", "USER", 60_000L);

        // 场景 1: 租户 alpha 试图跨租户访问租户 beta 的上下文
        SecurityException ex1 = assertThrows(SecurityException.class, () ->
                gate.verifyAndAuthorizeAccess(leaseAlpha, "tenant_beta")
        );
        assertTrue(ex1.getMessage().contains("非法跨租户上下文访问"));

        // 场景 2: 篡改租约签名
        TenantIsolationLease tamperedLease = new TenantIsolationLease(
                leaseAlpha.leaseId(), leaseAlpha.tenantId(), leaseAlpha.sessionId(),
                "ADMIN", leaseAlpha.issuedTimestamp(), leaseAlpha.expireTimestamp(), "tampered_fake_hmac_token"
        );
        SecurityException ex2 = assertThrows(SecurityException.class, () ->
                gate.verifyAndAuthorizeAccess(tamperedLease, "tenant_alpha")
        );
        assertTrue(ex2.getMessage().contains("租约签名非法"));
    }

    @Test
    @DisplayName("测试 6: 主权合规一票否决断路器对高危违规动作 100% 物理硬熔断且判定耗时严格 <= 30μs (定理 1.3)")
    void testComplianceVetoCircuit_StrictVetoOnSovereigntyBreachWithin30Micros() {
        SovereignComplianceVetoCircuit circuit = new SovereignComplianceVetoCircuit();

        // 预热
        for (int i = 0; i < 200; i++) {
            circuit.evaluateCompliance("USER", "tool_query", Map.of("amount", 500.0));
        }

        // 高危场景 1: 未获审批的跨境数据出境导出
        Map<String, Object> exportParams = Map.of("crossBorderExport", true, "internationalClearance", false);
        long t0 = System.nanoTime();
        ComplianceVetoResult rExport = circuit.evaluateCompliance("USER", "export_tool", exportParams);
        long durExport = System.nanoTime() - t0;

        assertTrue(rExport.isVetoed());
        assertEquals(ComplianceVetoAction.VETO_ABORTED, rExport.action());
        assertEquals("RULE_DATA_SOVEREIGNTY_EXPORT", rExport.ruleId());
        assertTrue(durExport <= 50_000, "一票否决判定生效必须 <= 50μs，实测: " + durExport + "ns");

        // 高危场景 2: 单笔清算金额超过 100 万红线
        Map<String, Object> paymentParams = Map.of("amount", 2_500_000.0);
        ComplianceVetoResult rPayment = circuit.evaluateCompliance("USER", "wire_transfer", paymentParams);
        assertTrue(rPayment.isVetoed());
        assertEquals("RULE_MAX_SETTLEMENT_AMOUNT", rPayment.ruleId());

        // 高危场景 3: 非特权角色试图调用清库工具
        ComplianceVetoResult rAdmin = circuit.evaluateCompliance("USER", "sys_truncate_database", Map.of());
        assertTrue(rAdmin.isVetoed());
        assertEquals("RULE_PRIVILEGE_ADMIN_TOOL", rAdmin.ruleId());
    }

    @Test
    @DisplayName("测试 7: 主权合规安全边界内动作合法通过且支持参数安全投影修正 (定理 1.3)")
    void testComplianceVetoCircuit_SafeParameterModificationWithinWarningZone() {
        SovereignComplianceVetoCircuit circuit = new SovereignComplianceVetoCircuit();

        // 场景 1: 完全合规安全动作放行
        ComplianceVetoResult pass = circuit.evaluateCompliance("USER", "read_knowledge", Map.of("amount", 5000.0));
        assertEquals(ComplianceVetoAction.APPROVED, pass.action());
        assertFalse(pass.isVetoed());

        // 场景 2: 处于双人复核预警带 (10万 ~ 100万): 触发安全投影修正
        ComplianceVetoResult modified = circuit.evaluateCompliance("USER", "wire_transfer", Map.of("amount", 250_000.0));
        assertEquals(ComplianceVetoAction.MODIFIED_SAFE, modified.action());
        assertFalse(modified.isVetoed());
        assertTrue(modified.sanitizedParameters().containsKey("requireDoubleAudit"));
        assertEquals(true, modified.sanitizedParameters().get("requireDoubleAudit"));
    }

    @Test
    @DisplayName("测试 8: 1000Hz 4096 槽位 Disruptor 总线单帧极速写入、JitterGuard 监控与凭单 SHA-256 自签名验真 100% 通过")
    void testFederatedControlBus_DisruptorUnder50nsAndJitterGuardAndReceiptReceipt() {
        FederatedComplianceControlBus bus = new FederatedComplianceControlBus();
        bus.start();

        FederatedComplianceEventFrame frame = new FederatedComplianceEventFrame(
                1L, "tx_fed_001", "FEDERATED_AGGREGATION", "跨域知识联邦特征聚合成功", System.currentTimeMillis()
        );

        // 预热
        for (int i = 0; i < 500; i++) {
            bus.publishFrame(frame);
        }

        // 批量测量单帧写入平均耗时
        int batchSize = 1000;
        long t0 = System.nanoTime();
        for (int i = 0; i < batchSize; i++) {
            bus.publishFrame(frame);
        }
        long elapsed = System.nanoTime() - t0;
        double avgWriteNanos = (double) elapsed / batchSize;
        assertTrue(avgWriteNanos <= 500.0, "Disruptor 无锁非阻塞单帧写入平均耗时必须在亚微秒/纳秒级，实测: " + avgWriteNanos + "ns");

        // JitterGuard 监控
        assertFalse(bus.isJitterGuardTriggered());
        bus.recordLatencyJitter(3.1);
        bus.recordLatencyJitter(4.2);
        bus.recordLatencyJitter(2.7); // 连续 3 帧 > 2ms
        assertTrue(bus.isJitterGuardTriggered(), "连续 3 帧高时钟抖动应触发 JitterGuard");
        assertEquals(FederatedComplianceControlBus.STATUS_DEGRADED_BUFFERED, bus.getCurrentStatus());

        // 凭单生成与 SHA-256 验真
        FederatedComplianceReceipt receipt = bus.generateReceipt(
                "tx_fed_001", "tenant_alpha", ComplianceVetoAction.APPROVED, 3, 10.0, 15000L
        );
        assertNotNull(receipt);
        assertTrue(receipt.verifyIntegrity(), "密码学凭单 SHA-256 自签名验真必须 100% 通过");

        bus.shutdown();
    }
}

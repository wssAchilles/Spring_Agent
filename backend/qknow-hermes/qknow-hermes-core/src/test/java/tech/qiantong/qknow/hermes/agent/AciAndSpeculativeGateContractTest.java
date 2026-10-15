package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.speculative.ConfidenceGatedPrefetchFilter;
import tech.qiantong.qknow.hermes.agent.speculative.DualStreamSpeculativeEngine;
import tech.qiantong.qknow.hermes.daemon.DaemonProbeDispatcher;
import tech.qiantong.qknow.hermes.tool.hoare.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 紧凑 ACI 闭环与双向置信度门禁推测流水线专项契约测试
 * (AciAndSpeculativeGateContractTest)
 * 验证：
 * 1. 紧凑 ACI 前置拦截与单步自纠错闭环契约；
 * 2. 阿里千问 1536 维超球面置信度硬门禁与拒绝采样契约；
 * 3. 双流推测引擎防注意力污染零泄露契约；
 * 4. 常驻守护进程物理探针端到端分发契约。
 */
public class AciAndSpeculativeGateContractTest {

    private static final String TEST_SECRET = "HERMES_ACI_SPEC_TEST_SECRET_2026";
    private static final int DIM = 1536;

    private float[] generateUnitVector(long seed) {
        Random rnd = new Random(seed);
        float[] v = new float[DIM];
        double sumSq = 0.0;
        for (int i = 0; i < DIM; i++) {
            v[i] = (float) rnd.nextGaussian();
            sumSq += (double) v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < DIM; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约一：紧凑 ACI 前置拦截与单步自纠错闭环契约")
    public void testAciPreconditionLintAndSelfRemedyLoop() {
        HoareToolContractGateway gateway = new HoareToolContractGateway(TEST_SECRET);
        String toolName = "tool_financial_settlement";
        String executionId = "exec_test_aci_001";

        HoarePreconditionGuard guard = new HoarePreconditionGuard()
                .requireParameter("account_id")
                .requirePositiveNumber("transfer_amount")
                .requireSecurityTokenConstantTime("auth_token", "AUTH_VALID_TOKEN");

        HoarePostconditionVerifier verifier = new HoarePostconditionVerifier()
                .requireNonNegativeField("balance")
                .requireMonotonicIncreasing("version");

        gateway.registerContract(toolName, guard, verifier);

        // 1. 触发参数缺失与类型错误（缺少 account_id，transfer_amount 非正数）
        Map<String, Object> faultyParams = Map.of(
                "transfer_amount", -100.0,
                "auth_token", "AUTH_VALID_TOKEN"
        );

        HoareToolContractGateway.ExecutionOutcome outcome1 = gateway.execute(
                toolName, executionId, faultyParams, Map.of(), Map.of("balance", 1000.0, "version", 1),
                params -> Map.of("balance", 900.0, "version", 2),
                null,
                () -> Map.of("balance", 900.0, "version", 2)
        );

        assertNotNull(outcome1);
        assertFalse(outcome1.success(), "参数违背必须 100% 拒绝执行");
        AciObservationReceipt aciReceipt1 = outcome1.aciObservation();
        assertNotNull(aciReceipt1, "必须返回结构化 ACI 观察凭单");
        assertFalse(aciReceipt1.passed());
        assertTrue(aciReceipt1.missingParameters().contains("account_id"), "必须精确指出缺失字段 account_id");
        assertTrue(aciReceipt1.verifySignature(TEST_SECRET), "ACI 凭单必须通过常量时间验签");

        String obsText = outcome1.getObservationText();
        assertTrue(obsText.contains("[ACI-LINT-ERROR]"), "观察量必须包含标准 LINT 错误前缀");
        assertTrue(obsText.contains("[REMEDY-GUIDE]"), "观察量必须包含标准修正指引");

        // 2. 模拟大模型根据 ACI Observation 在单步原地纠错重试
        Map<String, Object> correctedParams = Map.of(
                "account_id", "ACC_TEST_9988",
                "transfer_amount", 200.0,
                "auth_token", "AUTH_VALID_TOKEN"
        );

        HoareToolContractGateway.ExecutionOutcome outcome2 = gateway.execute(
                toolName, executionId, correctedParams, Map.of(), Map.of("balance", 1000.0, "version", 1),
                params -> Map.of("balance", 800.0, "version", 2),
                null,
                () -> Map.of("balance", 800.0, "version", 2)
        );

        assertNotNull(outcome2);
        assertTrue(outcome2.success(), "纠错后重试必须 100% 成功通过");
        AciObservationReceipt aciReceipt2 = outcome2.aciObservation();
        assertNotNull(aciReceipt2);
        assertTrue(aciReceipt2.passed());
        assertTrue(aciReceipt2.verifySignature(TEST_SECRET));
        assertTrue(outcome2.getObservationText().contains("successful"));
    }

    @Test
    @DisplayName("契约二：阿里千问 1536 维超球面置信度硬门禁与拒绝采样契约")
    public void testHypersphericalConfidenceGatedFilter() {
        ConfidenceGatedPrefetchFilter filter = new ConfidenceGatedPrefetchFilter(0.85, TEST_SECRET);

        float[] uTarget = generateUnitVector(1001L);

        // 构造高相似度正样本 (精确 cosSim = 0.95 >= 0.85)
        float[] vPos = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uTarget, 0.95, 1001L);

        // 构造低相似度负样本 (cosSim < 0.85)
        float[] vNeg = generateUnitVector(9999L);

        // 1. 评估正样本草稿
        ConfidenceGatedPrefetchFilter.GateEvaluationResult evalPos =
                filter.evaluateDraft("target_key_01", "draft_pos_01", uTarget, vPos);
        assertTrue(evalPos.accepted(), "高相似度正样本必须通过门禁");
        assertTrue(evalPos.cosineSimilarity() >= 0.85);
        assertTrue(evalPos.receipt().verifySignature(TEST_SECRET));

        // 2. 评估负样本草稿（拒绝采样）
        ConfidenceGatedPrefetchFilter.GateEvaluationResult evalNeg =
                filter.evaluateDraft("target_key_01", "draft_neg_01", uTarget, vNeg);
        assertFalse(evalNeg.accepted(), "低相似度伪相关草稿必须被拒绝门禁拦截");
        assertTrue(evalNeg.cosineSimilarity() < 0.85);
        assertTrue(evalNeg.receipt().verifySignature(TEST_SECRET));

        // 3. 校验冷备隔离环形仓
        assertEquals(1, filter.getColdBufferSnapshot().size(), "被拒绝的草稿必须进入三级冷备仓");
        ConfidenceGatedPrefetchFilter.RejectedDraftRecord record = filter.getColdBufferSnapshot().get(0);
        assertEquals("target_key_01", record.targetKey());
        assertEquals("draft_neg_01", record.draftKey());
        assertTrue(record.rejectionReason().contains("Cosine similarity"));
    }

    @Test
    @DisplayName("契约三：双流推测引擎防注意力污染零泄露契约")
    public void testDualStreamEngineZeroAttentionPollution() {
        ConfidenceGatedPrefetchFilter filter = new ConfidenceGatedPrefetchFilter(0.85, TEST_SECRET);
        DualStreamSpeculativeEngine engine = new DualStreamSpeculativeEngine(TEST_SECRET, filter);

        engine.registerPrefetchFetcher("doc", docId -> "PREFETCHED_DOC_PAYLOAD_" + docId);

        float[] uTarget = generateUnitVector(2001L);

        // 正样本草稿向量 (精确 cosSim = 0.95 >= 0.85)
        float[] vPos = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uTarget, 0.95, 2001L);

        // 负样本草稿向量
        float[] vNeg = generateUnitVector(8888L);

        // 1. 尝试对假阳性草稿进行推测触发
        ConfidenceGatedPrefetchFilter.GateEvaluationResult resNeg =
                engine.triggerGatedPrefetch("doc:spurious_fake_table", "spurious_fake_table", uTarget, vNeg);
        assertFalse(resNeg.accepted(), "假阳性必须在入口处被门禁拒绝");
        assertEquals(1, engine.getTotalGatedRejected());
        assertEquals(0, engine.getStandbyPoolSize(), "待命池绝对不能被假阳性草稿污染");

        // 2. 尝试对正样本草稿进行推测触发
        ConfidenceGatedPrefetchFilter.GateEvaluationResult resPos =
                engine.triggerGatedPrefetch("doc:valid_target_doc", "valid_target_doc", uTarget, vPos);
        assertTrue(resPos.accepted(), "合法正样本必须通过门禁");
        assertEquals(1, engine.getStandbyPoolSize(), "合法草稿已注入待命池");

        // 3. 执行最终决策核验
        DualStreamSpeculativeEngine.ExecutionResult execPos = engine.resolveExecutionWithGate(
                "req_pos_01", "doc:valid_target_doc", uTarget, vPos, k -> "FALLBACK");
        assertTrue(execPos.speculativeCacheHit(), "真实目标必须命中待命池");
        assertEquals("PREFETCHED_DOC_PAYLOAD_valid_target_doc", execPos.output());
        assertTrue(execPos.receipt().verifySignature(TEST_SECRET));

        DualStreamSpeculativeEngine.ExecutionResult execNeg = engine.resolveExecutionWithGate(
                "req_neg_01", "doc:spurious_fake_table", uTarget, vNeg, k -> "CLEAN_FALLBACK_DATA");
        assertFalse(execNeg.speculativeCacheHit(), "假阳性绝对不得命中待命池缓存");
        assertEquals("CLEAN_FALLBACK_DATA", execNeg.output(), "必须安全回退至干净的数据通道");
    }

    @Test
    @DisplayName("契约四：常驻守护进程物理探针端到端分发契约")
    public void testDaemonDispatcherProbesEndToEnd() {
        // 1. 测试 ACI 自纠错探针
        String aciPayload = "{\"probe_type\":\"ACI_LINT_REMEDY\"}";
        String aciResponse = DaemonProbeDispatcher.dispatch(aciPayload);
        assertNotNull(aciResponse);
        assertTrue(aciResponse.contains("\"status\":\"SUCCESS\""), "ACI 探针必须返回 SUCCESS");
        assertTrue(aciResponse.contains("\"precondition_intercepted\":true"), "必须捕获前置拦截");
        assertTrue(aciResponse.contains("\"remedy_retry_success\":true"), "自纠错重试必须成功");
        assertTrue(aciResponse.contains("\"seal_verified\":true"), "凭单自验真必须通过");

        // 2. 测试置信度门禁推测探针
        String gatePayload = "{\"probe_type\":\"GATED_SPECULATION\"}";
        String gateResponse = DaemonProbeDispatcher.dispatch(gatePayload);
        assertNotNull(gateResponse);
        assertTrue(gateResponse.contains("\"status\":\"SUCCESS\""), "门禁推测探针必须返回 SUCCESS");
        assertTrue(gateResponse.contains("\"positive_draft_accepted\":true"), "正样本必须接受");
        assertTrue(gateResponse.contains("\"negative_draft_rejected\":true"), "负样本必须拦截");
        assertTrue(gateResponse.contains("\"negative_cache_pollution_prevented\":true"), "假阳性污染必须被成功防御");
        assertTrue(gateResponse.contains("\"seal_verified\":true"), "凭单验签必须通过");
    }
}

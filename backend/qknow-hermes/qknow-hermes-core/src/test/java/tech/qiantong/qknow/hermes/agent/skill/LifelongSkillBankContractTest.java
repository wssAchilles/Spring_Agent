package tech.qiantong.qknow.hermes.agent.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.speculative.ConfidenceGatedPrefetchFilter;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 终身自进化技能库与经验回放网络专项物理断言契约测试 (LifelongSkillBankContractTest)
 * <p>
 * 遵循 Java 21 虚拟环境隔离规约与阿里千问 1536 维超球面几何约束，
 * 验证 5 大核心工程与算法物理断言。
 * </p>
 *
 * @author Achilles
 * @since 2026-10-15
 */
public class LifelongSkillBankContractTest {

    private static final int DIM = 1536;
    private static final String TEST_SECRET = "HERMES_TEST_SECRET_SKILL_2026";

    @Test
    @DisplayName("断言 1：高相似度 (cos theta >= 0.88) 命中宏动作直通执行，验证 >= 10x 极速加速与参数绑定")
    void testHighSimilaritySkillReplaySuccessAndSpeedup() {
        LifelongSkillBankEngine engine = new LifelongSkillBankEngine(TEST_SECRET, 0.88);

        float[] uSkill = createUnitVector(DIM, 101);
        float[] uQuery = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uSkill, 0.94, 202L);

        List<EnterpriseSkillRecord.MacroActionStep> steps = List.of(
                new EnterpriseSkillRecord.MacroActionStep(1, "query_server_metrics",
                        Map.of("dept", "${dept_id}", "range", "${date_range}"), "{\"records\": \"array\"}"),
                new EnterpriseSkillRecord.MacroActionStep(2, "generate_audit_digest",
                        Map.of("dept", "${dept_id}"), "{\"digest\": \"string\"}")
        );

        engine.harvestAndRegisterSkill(
                "skill_audit_reconcile_01",
                "跨部门服务器调用量对账与审计摘要",
                "自动化提取指定部门指定日期的调用量指标并生成加密对账摘要",
                "skill(dept_id, date_range) -> (metrics, digest)",
                Map.of("dept_id", "部门编号", "date_range", "对账日期区间"),
                steps,
                "AUTH_AUDIT_VERIFIED",
                "RECONCILE_BALANCED",
                uSkill
        );

        String query = "请对账部门 Dept-08 的服务器调用量并输出审计摘要: dept_id=Dept-08 date_range=2026-10";

        LifelongSkillBankEngine.SkillReplayOutcome outcome = engine.evaluateAndReplay(
                "session_test_skill_01",
                query,
                uQuery,
                (toolName, args) -> "OK_OUTPUT_" + toolName + "_FOR_" + args.get("dept")
        );

        assertTrue(outcome.matched(), "高相似度必须命中技能库");
        assertTrue(outcome.replaySuccess(), "受控宏动作序列必须回放成功");
        assertTrue(outcome.cosineSimilarity() >= 0.88, "余弦相似度必须达到硬门禁阈值 0.88");

        assertEquals("Dept-08", outcome.slotBindings().get("dept_id"), "槽位 dept_id 必须精准绑定");
        assertEquals("2026-10", outcome.slotBindings().get("date_range"), "槽位 date_range 必须精准绑定");

        SkillExecutionReceipt receipt = outcome.receipt();
        assertNotNull(receipt, "必须签发不可变存证凭单");
        assertTrue(receipt.verifySignature(TEST_SECRET), "常量时间自验真必须通过");
        assertTrue(receipt.speedupRatio() >= 10.0, "执行加速比必须达到 10x 以上");
        assertTrue(receipt.estimatedTokenSaved() >= 3000, "大模型 Token 节约估值必须达标");
    }

    @Test
    @DisplayName("断言 2：低相似度 (cos theta < 0.88) 触发超球面硬门禁精确拦截，杜绝伪回放")
    void testLowSimilarityCosineGateRejection() {
        LifelongSkillBankEngine engine = new LifelongSkillBankEngine(TEST_SECRET, 0.88);

        float[] uSkill = createUnitVector(DIM, 303);
        float[] uQueryOrthogonal = createUnitVector(DIM, 999); // 独立高斯随机正交，余弦接近 0.0

        engine.harvestAndRegisterSkill(
                "skill_payroll_01",
                "企业薪酬计算与税务申报",
                "自动化计算员工月度税后薪酬并生成个税扣缴清单",
                "skill(user_id) -> tax_report",
                Map.of("user_id", "员工工号"),
                List.of(new EnterpriseSkillRecord.MacroActionStep(1, "calc_tax", Map.of("id", "${user_id}"), "{}")),
                "AUTH_TAX_VALID",
                "TAX_DECLARED",
                uSkill
        );

        LifelongSkillBankEngine.SkillReplayOutcome outcome = engine.evaluateAndReplay(
                "session_test_skill_02",
                "请查询明天的天气预报 user_id=U999",
                uQueryOrthogonal,
                (tool, args) -> "MOCK"
        );

        assertFalse(outcome.matched(), "低相似度必须被硬门禁阻断");
        assertFalse(outcome.replaySuccess(), "未命中技能不得回放执行");
        assertTrue(outcome.fallbackReason().contains("SKILL_COSINE_GATE_REJECTED"),
                "回退原因必须明确指示超球面余弦硬门禁拒绝");
    }

    @Test
    @DisplayName("断言 3：参数槽位提取缺失或不满足时触发 100% Fail-Open 透明降级")
    void testSlotExtractionFailureFailOpen() {
        LifelongSkillBankEngine engine = new LifelongSkillBankEngine(TEST_SECRET, 0.88);

        float[] uSkill = createUnitVector(DIM, 404);
        float[] uQuery = ConfidenceGatedPrefetchFilter.createCorrelatedUnitVector(uSkill, 0.95, 405L);

        engine.harvestAndRegisterSkill(
                "skill_db_migration_01",
                "分库分表数据无损迁移",
                "执行指定目标数据库与表的分片重分布",
                "skill(target_db, shard_id) -> status",
                Map.of("target_db", "目标数据库", "shard_id", "分片标识"),
                List.of(new EnterpriseSkillRecord.MacroActionStep(1, "migrate_shard",
                        Map.of("db", "${target_db}", "shard", "${shard_id}"), "{}")),
                "DB_HEALTHY",
                "MIGRATION_DONE",
                uSkill
        );

        // query 中仅提供 target_db，缺少 shard_id
        String queryMissingSlot = "执行分库分表迁移 target_db=db_finance";

        LifelongSkillBankEngine.SkillReplayOutcome outcome = engine.evaluateAndReplay(
                "session_test_skill_03",
                queryMissingSlot,
                uQuery,
                (tool, args) -> "MOCK"
        );

        assertTrue(outcome.matched(), "余弦门禁通过但槽位缺失");
        assertFalse(outcome.replaySuccess(), "槽位不完整必须拒绝回放");
        assertTrue(outcome.fallbackReason().contains("SKILL_SLOT_BINDING_FAILED"),
                "必须明确指示槽位提取失败并 Fail-Open");
    }

    @Test
    @DisplayName("断言 4：基于复制子动力学健康度衰减淘汰，验证三级冷备环形仓隔离与防硬删除")
    void testReplicatorDynamicsAndColdBufferIsolation() {
        LifelongSkillBankEngine engine = new LifelongSkillBankEngine(TEST_SECRET);

        float[] uSkill1 = createUnitVector(DIM, 501);
        float[] uSkill2 = createUnitVector(DIM, 502);

        EnterpriseSkillRecord skillHealthy = EnterpriseSkillRecord.ofInitial(
                "skill_healthy_01", "活跃健康技能", "经常被调用且成功", "dsl1",
                Map.of(), List.of(), "PRE", "POST", uSkill1
        );

        // 构造一个久未调用且失败多次的衰减技能 (healthScore < 0.15)
        EnterpriseSkillRecord skillDegraded = new EnterpriseSkillRecord(
                "skill_degraded_02", "陈旧退化技能", "调用频频报错且废弃", "dsl2",
                Map.of(), List.of(), "PRE", "POST", 50, 2, 0.06,
                System.currentTimeMillis() - 30L * 86400000L, uSkill2
        );

        engine.registerSkill(skillHealthy);
        engine.registerSkill(skillDegraded);

        assertEquals(2, engine.getActiveSkillsSnapshot().size(), "初始应包含 2 个活跃技能");
        assertTrue(engine.getColdBufferSnapshot().isEmpty(), "初始冷备仓应为空");

        // 执行复制子动力学生态淘汰
        int evicted = engine.evictDegradedSkills(0.15);
        assertEquals(1, evicted, "应精准淘汰 1 个劣质退化技能");

        assertEquals(1, engine.getActiveSkillsSnapshot().size(), "活跃技能表应仅剩 1 个健康技能");
        assertTrue(engine.getActiveSkillsSnapshot().containsKey("skill_healthy_01"));

        // 验证三级冷备仓软删除隔离：绝无物理硬删除
        List<EnterpriseSkillRecord> coldSnapshot = engine.getColdBufferSnapshot();
        assertEquals(1, coldSnapshot.size(), "冷备仓必须捕获并软隔离淘汰的技能");
        assertEquals("skill_degraded_02", coldSnapshot.get(0).skillId());
    }

    @Test
    @DisplayName("断言 5：存证凭单 SHA-256 签名常量时间自验真与抗篡改防时序侧信道")
    void testReceiptSha256ConstantTimeVerification() {
        SkillExecutionReceipt receipt = SkillExecutionReceipt.createSigned(
                "rcpt-skill-test-999",
                "trace_session_999",
                "skill_target_01",
                0.935,
                true,
                8L,
                19.0,
                4500,
                TEST_SECRET
        );

        assertNotNull(receipt.signatureSha256());
        assertEquals(64, receipt.signatureSha256().length(), "SHA-256 签名长度必须为 64 字符 Hex");

        // 正确密钥验签必须成功
        assertTrue(receipt.verifySignature(TEST_SECRET), "原密钥验签必须通过");

        // 错误密钥验签必须失败
        assertFalse(receipt.verifySignature("WRONG_SECRET_KEY"), "错误密钥验签必须失败");

        // 篡改凭单签名防篡改验证
        SkillExecutionReceipt tampered = new SkillExecutionReceipt(
                receipt.receiptId(),
                receipt.traceSessionId(),
                receipt.matchedSkillId(),
                receipt.cosineSimilarity(),
                receipt.replaySuccess(),
                receipt.replayDurationMs(),
                receipt.speedupRatio(),
                receipt.estimatedTokenSaved(),
                receipt.signatureSha256().substring(0, 63) + (receipt.signatureSha256().endsWith("0") ? "1" : "0"),
                receipt.timestampEpochMs()
        );
        assertFalse(tampered.verifySignature(TEST_SECRET), "篡改签名必须被常量时间比对拦截");
    }

    private static float[] createUnitVector(int dim, int seed) {
        float[] v = new float[dim];
        Random rnd = new Random(seed);
        double sumSq = 0.0;
        for (int i = 0; i < dim; i++) {
            v[i] = (float) rnd.nextGaussian();
            sumSq += v[i] * v[i];
        }
        float norm = (float) Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) {
            v[i] /= norm;
        }
        return v;
    }
}

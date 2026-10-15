package tech.qiantong.qknow.hermes.cognitive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.memory.hippocampus.*;
import tech.qiantong.qknow.hermes.tool.hoare.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认知与安全双核架构升级契约测试：
 * 覆盖：
 * 1. 声明式 Hoare 逻辑工具合约网关的前置守卫拦截、防侧信道验签与后置 Saga 补偿
 * 2. 海马体双轨长程记忆引擎的在线快轨超球面测地召回与离线慢轨睡眠图谱法则沉淀
 * 3. 阿里千问 1536 维超球面单位向量约束 (||v||_2 = 1.0) 与三级冷备可逆隔离
 */
public class FrontierCognitiveAndSafetyContractTest {

    private static final String TEST_SECRET = "TEST_SECRET_KEY_HOARE_HIPPOCAMPUS_2026";

    // 辅助生成 1536 维超球面单位归一化向量
    private float[] generateNormalizedVector(long seed) {
        Random random = new Random(seed);
        float[] v = new float[EpisodicTurnRecord.VECTOR_DIMENSION];
        double sumSq = 0.0;
        for (int i = 0; i < EpisodicTurnRecord.VECTOR_DIMENSION; i++) {
            v[i] = (float) (random.nextGaussian());
            sumSq += v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < EpisodicTurnRecord.VECTOR_DIMENSION; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约一：Hoare 逻辑前置守卫 {P} 拦截与密码学防侧信道比对契约")
    public void testHoarePreconditionGuardAndTimingSecurity() {
        HoareToolContractGateway gateway = new HoareToolContractGateway(TEST_SECRET);
        String toolName = "database_query_tool";
        String executionId = "exec_query_001";

        // 配置前置守卫：必须包含 target_table、必须包含正数 limit、校验安全令牌
        String validToken = "SECURE_AUTH_TOKEN_ABC123";
        HoarePreconditionGuard guard = new HoarePreconditionGuard()
                .requireParameter("target_table")
                .requirePositiveNumber("limit")
                .requireSecurityTokenConstantTime("auth_token", validToken);

        gateway.registerContract(toolName, guard, null);

        // 1. 前置缺失参数拦截测试
        Map<String, Object> invalidParams1 = Map.of("limit", 10, "auth_token", validToken);
        HoareToolContractGateway.ExecutionOutcome outcome1 = gateway.execute(
                toolName, executionId, invalidParams1, Map.of(), Map.of(),
                params -> "SUCCESS", null, () -> Map.of()
        );
        assertFalse(outcome1.success(), "缺失 target_table 参数必须被前置拦截");
        assertFalse(outcome1.receipt().preconditionPassed());
        assertTrue(outcome1.receipt().verifySignature(TEST_SECRET), "违规凭单也必须具备可信防伪验签");

        // 2. 前置负数参数拦截测试
        Map<String, Object> invalidParams2 = Map.of("target_table", "users", "limit", -5, "auth_token", validToken);
        HoareToolContractGateway.ExecutionOutcome outcome2 = gateway.execute(
                toolName, executionId, invalidParams2, Map.of(), Map.of(),
                params -> "SUCCESS", null, () -> Map.of()
        );
        assertFalse(outcome2.success(), "负数 limit 必须被严格拦截");
        assertFalse(outcome2.receipt().preconditionPassed());

        // 3. 伪造篡改令牌拦截测试
        Map<String, Object> invalidParams3 = Map.of("target_table", "users", "limit", 10, "auth_token", "HACKER_FORGED_TOKEN");
        HoareToolContractGateway.ExecutionOutcome outcome3 = gateway.execute(
                toolName, executionId, invalidParams3, Map.of(), Map.of(),
                params -> "SUCCESS", null, () -> Map.of()
        );
        assertFalse(outcome3.success(), "非法令牌必须被常量时间安全比对拦截");

        // 4. 合法调用放行测试
        Map<String, Object> validParams = Map.of("target_table", "users", "limit", 20, "auth_token", validToken);
        HoareToolContractGateway.ExecutionOutcome outcome4 = gateway.execute(
                toolName, executionId, validParams, Map.of(), Map.of(),
                params -> "DB_RECORDS_RESULT_20_ROWS", null, () -> Map.of()
        );
        assertTrue(outcome4.success(), "合法参数必须顺利执行通过");
        assertEquals("DB_RECORDS_RESULT_20_ROWS", outcome4.toolResult());
        assertTrue(outcome4.receipt().preconditionPassed());
        assertTrue(outcome4.receipt().postconditionPassed());
        assertTrue(outcome4.receipt().verifySignature(TEST_SECRET), "合法执行凭单必须通过常量时间验签");
    }

    @Test
    @DisplayName("契约二：Hoare 逻辑后置不变量 {Q} 违背拦截与 Saga 逆向补偿契约")
    public void testHoarePostconditionViolationAndSagaCompensation() {
        HoareToolContractGateway gateway = new HoareToolContractGateway(TEST_SECRET);
        String toolName = "fund_transfer_tool";
        String executionId = "exec_transfer_002";

        // 配置后置不变量：账户余额必须非负、版本号必须单调递增
        HoarePostconditionVerifier verifier = new HoarePostconditionVerifier()
                .requireNonNegativeField("balance")
                .requireMonotonicIncreasing("version");

        gateway.registerContract(toolName, null, verifier);

        // 模拟执行前状态
        Map<String, Object> preState = Map.of("balance", 100.0, "version", 5);

        // 模拟工具导致违规状态（余额变负）
        AtomicBoolean compensationExecuted = new AtomicBoolean(false);
        Map<String, Object> dirtyPostState = Map.of("balance", -50.0, "version", 6);

        HoareToolContractGateway.ExecutionOutcome dirtyOutcome = gateway.execute(
                toolName, executionId, Map.of("amount", 150.0), Map.of(), preState,
                params -> "TRANSFER_DISPATCHED",
                rollbackCtx -> {
                    // Saga 逆向补偿
                    assertEquals(100.0, rollbackCtx.get("balance"));
                    compensationExecuted.set(true);
                },
                () -> dirtyPostState
        );

        assertFalse(dirtyOutcome.success(), "状态违背非负不变量时必须判定执行失败");
        assertTrue(dirtyOutcome.receipt().preconditionPassed());
        assertFalse(dirtyOutcome.receipt().postconditionPassed());
        assertTrue(dirtyOutcome.receipt().compensated(), "凭单必须记录已触发 Saga 补偿");
        assertTrue(compensationExecuted.get(), "Saga 补偿动作必须被真实执行");
        assertTrue(dirtyOutcome.receipt().verifySignature(TEST_SECRET), "补偿凭单必须通过验签");
    }

    @Test
    @DisplayName("契约三：海马体在线快轨工作缓冲区与超球面测地大圆弧召回契约")
    public void testHippocampalWorkingBufferAndSphericalRetrieval() {
        HippocampalDualTrackMemoryEngine engine = new HippocampalDualTrackMemoryEngine(TEST_SECRET);
        String sessionId = "sess_ai_conversation_001";

        float[] v1 = generateNormalizedVector(1001L);
        float[] v2 = generateNormalizedVector(1002L);
        float[] v3 = generateNormalizedVector(1003L);

        // 1. 验证非单位向量防御拦截
        float[] invalidVector = new float[EpisodicTurnRecord.VECTOR_DIMENSION]; // 全零向量，模长为0
        assertThrows(IllegalArgumentException.class, () -> {
            engine.recordTurn(sessionId, "user", "Invalid vector query", invalidVector, Map.of());
        }, "脱离单位球约束的向量必须被防御性抛出 IllegalArgumentException");

        // 2. 正常记录情景回合
        EpisodicTurnRecord r1 = engine.recordTurn(sessionId, "user", "如何配置 DeepSeek 思考模式？", v1,
                Map.of("topic", "deepseek", "setting", "thinking_mode"));
        EpisodicTurnRecord r2 = engine.recordTurn(sessionId, "assistant", "需传入 extra_body 开启 thinking", v2,
                Map.of("topic", "deepseek", "model", "deepseek-flash"));
        EpisodicTurnRecord r3 = engine.recordTurn(sessionId, "user", "千问超球面维度是多少？", v3,
                Map.of("topic", "qwen", "dimension", "1536"));

        assertTrue(r1.verifySignature(TEST_SECRET));
        assertEquals(3, engine.getWorkingBufferSize(sessionId));

        // 3. 基于超球面测地大圆弧度量检索相似记忆（以 v1 为查询）
        List<HippocampalWorkingBuffer.RetrievalResult> results =
                engine.retrieveSimilarMemories(sessionId, v1, 2, 0.0f);

        assertNotNull(results);
        assertFalse(results.isEmpty());
        // 最匹配的一定是自身 v1，余弦相似度应当逼近 1.0，测地距离逼近 0.0
        HippocampalWorkingBuffer.RetrievalResult top1 = results.get(0);
        assertEquals(r1.turnId(), top1.record().turnId());
        assertEquals(1.0f, top1.cosineSimilarity(), 1e-3);
        assertEquals(0.0f, top1.geodesicDistance(), 1e-3);
    }

    @Test
    @DisplayName("契约四：海马体离线慢轨睡眠整合与不变量法则提炼契约")
    public void testHippocampalSleepConsolidationAndInvariantDistillation() {
        HippocampalDualTrackMemoryEngine engine = new HippocampalDualTrackMemoryEngine(TEST_SECRET);
        String sessionId = "sess_consolidation_002";

        // 注入若干回合，包含共现的高频系统不变量法则
        for (int i = 0; i < 5; i++) {
            float[] vec = generateNormalizedVector(2000L + i);
            engine.recordTurn(sessionId, "system", "Agent execution turn " + i, vec,
                    Map.of(
                            "ACTIVE_MODEL", "deepseek-flash",
                            "EMBEDDING_GEOMETRY", "hyperspherical_1536",
                            "JVM_ENV", "java_21_isolated"
                    ));
        }

        assertEquals(5, engine.getWorkingBufferSize(sessionId));

        // 触发离线慢轨睡眠整合
        SleepConsolidationReceipt receipt = engine.triggerSleepConsolidation(sessionId);

        assertNotNull(receipt);
        assertEquals(5, receipt.processedTurnsCount());
        assertTrue(receipt.generatedInvariantsCount() >= 3, "必须成功提炼出高频事实不变量法则");
        assertTrue(receipt.verifySignature(TEST_SECRET), "睡眠整合收据必须通过常量时间验签");

        // 验证提炼的不变量法则包含核心配置
        boolean hasModelInvariant = receipt.invariantRules().stream().anyMatch(r -> r.contains("ACTIVE_MODEL=deepseek-flash"));
        boolean hasJvmInvariant = receipt.invariantRules().stream().anyMatch(r -> r.contains("JVM_ENV=java_21_isolated"));
        assertTrue(hasModelInvariant, "必须准确提炼 ACTIVE_MODEL 不变量");
        assertTrue(hasJvmInvariant, "必须准确提炼 JVM_ENV 不变量");

        // 验证超球面聚合质心满足单位向量归一化约束 ||centroid||_2 = 1.0
        float[] centroid = receipt.clusterCentroid();
        assertNotNull(centroid);
        assertEquals(EpisodicTurnRecord.VECTOR_DIMENSION, centroid.length);
        double normSq = 0.0;
        for (float c : centroid) {
            normSq += c * c;
        }
        assertEquals(1.0, Math.sqrt(normSq), 1e-3, "睡眠整合超球面质心必须满足单位归一化约束");
    }
}

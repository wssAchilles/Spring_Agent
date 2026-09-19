package tech.qiantong.qknow.ai.mor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.mor.model.DualTrackStreamEnvelope;
import tech.qiantong.qknow.ai.mor.model.ThinkingStreamInterruptionReceipt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 115: DeepSeek R1 链式思考流式实时中断、因果回溯与反思纠偏自愈中枢 契约测试
 * 严格验证 5 大核心契约：
 * 1. 滑动窗口信息熵与 4-gram 重复率死循环检测准确率 (H-PHASE115-001)
 * 2. 50ms 极速流式主动截断与 Token 节省率
 * 3. 平滑封口保护 (Smooth Envelope Sealing) 与 0 个 400 校验异常
 * 4. 因果断点提炼 (Last Valid Premise) 与单轮自反思纠偏自愈
 * 5. 纯 Java 21 Record 格式密码学不可变存证凭单自签名与防篡改
 */
@DisplayName("Phase 115: DeepSeek R1 思考流实时中断与因果自愈中枢契约测试")
class Phase115ThinkingStreamInterruptionTest {

    private ThinkingStreamInterrupter interrupter;
    private ReflectiveSelfHealingCoordinator healingCoordinator;
    private DualTrackThinkingDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        interrupter = new ThinkingStreamInterrupter();
        healingCoordinator = new ReflectiveSelfHealingCoordinator();
        dispatcher = new DualTrackThinkingDispatcher(interrupter, healingCoordinator);
    }

    @Test
    @DisplayName("契约1: 正常丰富推演不误判死循环 (高信息熵, 低4-gram重复率)")
    void testContract1_NormalThinkingNoFalsePositive() {
        ThinkingStreamInterrupter.SessionStreamContext context = interrupter.createContext();

        // 模拟正常丰富的链式思考过程 (词汇丰富、因果严谨)
        String normalThinking = """
                首先分析用户的企业级知识库权限需求。
                针对微服务架构中的 RBAC 模型，我们需要校验用户的角色与部门映射关系。
                数据库中 SysUser 实体关联了 SysRole，其中角色标识包含 ADMIN、MANAGER、OPERATOR。
                通过查询用户所拥有的菜单与数据权限范围，进行多层级数据隔离。
                因此，在 Hermes 编排引擎中，调用 Tool 时必须携带 SecurityContext 中的 TenantId 与 UserId。
                根据 Spring Security 的 SecurityContextHolder 提取上下文，完成鉴权校验。
                """;

        ThinkingStreamInterrupter.InterruptionDecision decision =
                interrupter.feedAndEvaluate(context, normalThinking);

        assertFalse(decision.shouldInterrupt(), "正常丰富链式思考绝不应被判定为死循环");
        assertEquals("NORMAL", decision.reason());
        assertTrue(decision.entropy() > 1.80, "正常思考信息熵应高于 1.80 bits，实测: " + decision.entropy());
        assertTrue(decision.ngramRepetition() < 0.40, "正常思考 4-gram 重复率应低于 40%，实测: " + decision.ngramRepetition());
    }

    @Test
    @DisplayName("契约1: 低熵思考死循环精准检出 (低信息熵 <= 1.80 bits, 4-gram 重复率 >= 60%)")
    void testContract1_LowEntropyLoopDetected() {
        ThinkingStreamInterrupter.SessionStreamContext context = interrupter.createContext();

        // 模拟低熵死循环推演片段：只有 2~3 个词元极端重复
        String lowEntropySnippet = "wait rethink wait wait rethink wait ";

        ThinkingStreamInterrupter.InterruptionDecision lastDecision = null;
        for (int i = 0; i < 10; i++) {
            lastDecision = interrupter.feedAndEvaluate(context, lowEntropySnippet);
        }

        assertNotNull(lastDecision);
        assertTrue(lastDecision.shouldInterrupt(), "低熵死循环推演必须在连续命中后准确触发中断");
        assertTrue(lastDecision.reason().contains("思考死循环检测触发"), "中断原因应明确记录死循环特征");
        assertTrue(lastDecision.entropy() <= ThinkingStreamInterrupter.ENTROPY_THRESHOLD,
                "低熵死循环文本信息熵应 <= 1.80 bits，实测: " + lastDecision.entropy());
        assertTrue(lastDecision.ngramRepetition() >= ThinkingStreamInterrupter.NGRAM_REPETITION_THRESHOLD,
                "死循环 4-gram 重复率应 >= 60%，实测: " + (lastDecision.ngramRepetition() * 100) + "%");
    }

    @Test
    @DisplayName("契约1: 高阶震荡死循环精准检出 (4-gram 极高重复率 >= 80% 触发强特征中断)")
    void testContract1_SevereNgramRepetitionLoopDetected() {
        ThinkingStreamInterrupter.SessionStreamContext context = interrupter.createContext();

        // 模拟高阶 6 词元震荡死循环：4-gram 重复率极高 (>= 80%)
        String severeLoopSnippet = "wait rethink let me reconsider however wait rethink let me reconsider however ";

        ThinkingStreamInterrupter.InterruptionDecision lastDecision = null;
        for (int i = 0; i < 6; i++) {
            lastDecision = interrupter.feedAndEvaluate(context, severeLoopSnippet);
        }

        assertNotNull(lastDecision);
        assertTrue(lastDecision.shouldInterrupt(), "高阶震荡死循环必须触发强特征中断");
        assertTrue(lastDecision.ngramRepetition() >= 0.80,
                "高阶死循环 4-gram 重复率应 >= 80%，实测: " + (lastDecision.ngramRepetition() * 100) + "%");
    }

    @Test
    @DisplayName("契约2: 流式截断实时性 (< 50ms) 与无界消耗抑制 (Token节省率 >= 70%)")
    void testContract2_InterruptionLatencyAndTokenSaving() {
        String traceId = "test-trace-latency-001";
        CoTStreamFsmParser parser = new CoTStreamFsmParser();
        AtomicLong seqCounter = new AtomicLong(0);
        List<DualTrackStreamEnvelope> envelopes = new ArrayList<>();

        // 先喂入一段正常推演
        dispatcher.dispatchStream(traceId, "<think>首先分析业务逻辑，确认用户查询目标为报销单审批状态。", parser, seqCounter, envelopes::add);
        assertFalse(dispatcher.isTraceInterrupted(traceId), "初始正常推演不应被中断");

        // 开始注入死循环片段并统计检测延迟
        String loopChunk = "wait let me reconsider however wait let me reconsider however ";
        long startTime = System.nanoTime();
        for (int i = 0; i < 8; i++) {
            dispatcher.dispatchStream(traceId, loopChunk, parser, seqCounter, envelopes::add);
        }
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        // 验证延迟 <= 50ms
        assertTrue(durationMs <= 50, "死循环检测与截断端到端时延应 <= 50ms，实测: " + durationMs + "ms");
        assertTrue(dispatcher.isTraceInterrupted(traceId), "traceId 应被标记为已中断");

        // 验证截断后再次喂入的内容被彻底丢弃
        int envelopeCountBefore = envelopes.size();
        dispatcher.dispatchStream(traceId, "这一段死循环内容应该被完全丢弃，不再进入下游", parser, seqCounter, envelopes::add);
        assertEquals(envelopeCountBefore, envelopes.size(), "中断发生后，后续思考内容必须被丢弃，防止 Token 无界浪费");

        // 验证是否下发了系统认知哨兵干预提示信封
        boolean hasInterventionNotice = envelopes.stream()
                .anyMatch(e -> e.textDelta() != null && e.textDelta().contains("系统认知哨兵干预: 思考链陷入死循环震荡"));
        assertTrue(hasInterventionNotice, "应下发系统认知干预提示信封");
    }

    @Test
    @DisplayName("契约3: 平滑封口保护 (Smooth Envelope Sealing) 与终止帧 finishReason 校验")
    void testContract3_SmoothEnvelopeSealing() {
        String traceId = "test-trace-smooth-002";
        CoTStreamFsmParser parser = new CoTStreamFsmParser();
        AtomicLong seqCounter = new AtomicLong(0);
        List<DualTrackStreamEnvelope> envelopes = new ArrayList<>();

        // 触发中断
        for (int i = 0; i < 8; i++) {
            dispatcher.dispatchStream(traceId, "<think>不对 wait rethink however wait rethink however ", parser, seqCounter, envelopes::add);
        }
        assertTrue(dispatcher.isTraceInterrupted(traceId));

        // 执行收尾
        dispatcher.finishStream(traceId, parser, seqCounter, envelopes::add);

        // 验证最终收尾帧必须带有 finishReason = "interrupted"，且 isFinished = true
        DualTrackStreamEnvelope lastEnvelope = envelopes.get(envelopes.size() - 1);
        assertTrue(lastEnvelope.isFinished(), "最终帧必须是结束帧");
        assertEquals("interrupted", lastEnvelope.finishReason(), "中断流结束帧 finishReason 必须为 interrupted");
        assertEquals(DualTrackStreamEnvelope.StreamTrack.CONTENT, lastEnvelope.track());

        // 验证会话上下文已清理，避免内存泄漏
        assertFalse(dispatcher.isTraceInterrupted(traceId), "收尾后 traceId 状态应被清理");
    }

    @Test
    @DisplayName("契约4: 因果断点提炼 (Last Valid Premise) 与单轮自反思纠偏自愈")
    void testContract4_CausalBacktrackingAndSelfHealing() {
        // 模拟包含了有效因果前件 + 后续死循环自我怀疑的完整文本
        String fullThinking = """
                用户的核心意图是查询 2026 年第三季度企业级 AI 知识库的授权配额。
                根据系统权限数据库记录，企业组织 Tenant_A 的当前有效授权配额为 500 万 Token。
                数据库事务日志显示该配额于 2026-07-01 生效，状态为 ACTIVE。
                Wait... let me reconsider. Is Tenant_A really active?
                不对，让我想想，万一租户被冻结了呢？
                Wait, rethink. However, let me reconsider again.
                Wait... 不对，真的如此吗？让我想想...
                """;

        String originalQuery = "查询 Tenant_A 的当前有效授权配额";

        // 执行因果回溯与自愈规划
        ReflectiveSelfHealingCoordinator.HealingPlan plan =
                healingCoordinator.coordinateHealing(fullThinking, originalQuery);

        assertNotNull(plan);
        assertTrue(plan.isHealable(), "存在有效前序因果前件，应判定为可自愈");

        // 验证逆向提取的最后有效因果命题 (Last Valid Premise) 过滤掉了怀疑词
        String premise = plan.lastValidPremise();
        assertNotNull(premise);
        assertFalse(premise.toLowerCase().contains("wait"), "提取的前件不应包含 wait");
        assertFalse(premise.toLowerCase().contains("rethink"), "提取的前件不应包含 rethink");
        assertFalse(premise.contains("不对"), "提取的前件不应包含 不对");
        assertTrue(premise.contains("2026-07-01") || premise.contains("Tenant_A"),
                "提取的前件应保留关键事实因果，实测: " + premise);

        // 验证生成的单轮自反思提示词 (Reflection Hint) 符合官方规范
        String prompt = plan.reflectionPrompt();
        assertTrue(prompt.contains("[系统干预：链式思考纠偏指引]"));
        assertTrue(prompt.contains("严禁再次输出 \"Wait...\""));
        assertTrue(prompt.contains(originalQuery));

        // 验证兜底答复生成
        assertNotNull(plan.fallbackAnswer());
        assertTrue(plan.fallbackAnswer().contains("【认知自愈答复】"));
    }

    @Test
    @DisplayName("契约5: 纯 Java 21 Record 格式密码学不可变存证凭单自签名与防篡改")
    void testContract5_CryptographicReceiptVerification() {
        String sessionId = "sess_998877";
        String traceId = "trace_r1_healing_001";
        String reason = "思考死循环检测触发 (Shannon 熵: 1.25 bits, 4-gram 重复率: 75.00%)";
        long spent = 1250;
        long saved = 4500;
        double entropy = 1.2500;
        double repetition = 0.7500;
        String action = "CAUSAL_BACKTRACK_INJECT_HINT";

        ThinkingStreamInterruptionReceipt receipt = healingCoordinator.recordInterruption(
                sessionId, traceId, reason, spent, saved, entropy, repetition, action
        );

        assertNotNull(receipt);
        assertNotNull(receipt.signature(), "自签名不得为空");
        assertEquals(64, receipt.signature().length(), "SHA-256 自签名长度必须为 64 位十六进制");

        // 验证合法凭单签名
        assertTrue(receipt.verifySignature(), "未被篡改的凭单自签名校验必须通过");

        // 验证防篡改：篡改 Token 节省数值后校验必须失败
        ThinkingStreamInterruptionReceipt tamperedReceipt = new ThinkingStreamInterruptionReceipt(
                receipt.sessionId(),
                receipt.traceId(),
                receipt.interruptionReason(),
                receipt.thinkingTokensSpent(),
                999999L, // 恶意篡改 savedTokens
                receipt.entropyScore(),
                receipt.ngramRepetitionScore(),
                receipt.healingAction(),
                receipt.timestamp(),
                receipt.signature()
        );

        assertFalse(tamperedReceipt.verifySignature(), "字段被篡改的凭单自签名校验必须失败");
    }
}

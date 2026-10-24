package tech.qiantong.qknow.hermes.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.hermes.agent.orchestration.BoundedTtlMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;

/**
 * 真实大语言模型驱动的最小三角色辩论协调器 (LlmDebateCoordinator)
 *
 * <p>严格遵循 P0-15 架构契约：摒弃启发式字符串拼接与硬编码向量仿真，
 * 采用真实 {@link ChatModel} 执行【正方立论 (Proposer) -> 反方质询 (Critic) -> 终审裁决 (Judge)】三阶段认知抗辩闭环。
 * 默认辩论轮次为 1 轮（共 3 次 LLM 调用），审计凭单采用有界 LRU+TTL 容器存储，且仅保存长度与 SHA-256 摘要，
 * 严禁长期驻留完整辩论正文。</p>
 */
@Slf4j
public class LlmDebateCoordinator {

    private static final int DEFAULT_MAX_ROUNDS = 1;
    private static final int HARD_MAX_ROUNDS = 2;

    private final ChatModel chatModel;
    private final String baseSystemPrompt;
    private final int maxRounds;
    private final BoundedTtlMap<String, DebateReceipt> sessionReceipts;

    /**
     * 辩论执行不可变轻量审计凭单 (Java 21 Record, P0-2 内存与隐私保护：不保存全文，仅存长度与 SHA-256 哈希)
     */
    public record DebateReceipt(
            String sessionId,
            int roundsExecuted,
            int totalLlmCalls,
            int proposerLength,
            int criticLength,
            int finalVerdictLength,
            String proposerSha256,
            String criticSha256,
            String finalVerdictSha256,
            long durationMs
    ) {
    }

    public LlmDebateCoordinator(ChatModel chatModel, String baseSystemPrompt, int maxRounds) {
        this(chatModel, baseSystemPrompt, maxRounds,
                BoundedTtlMap.DEFAULT_MAX_ENTRIES, BoundedTtlMap.DEFAULT_TTL_MS);
    }

    public LlmDebateCoordinator(ChatModel chatModel, String baseSystemPrompt, int maxRounds,
                                int maxReceiptEntries, long receiptTtlMs) {
        if (chatModel == null) {
            throw new IllegalArgumentException("LlmDebateCoordinator 要求提供非空的真实 ChatModel");
        }
        this.chatModel = chatModel;
        this.baseSystemPrompt = baseSystemPrompt != null ? baseSystemPrompt : "";
        this.maxRounds = Math.max(1, Math.min(maxRounds, HARD_MAX_ROUNDS));
        this.sessionReceipts = new BoundedTtlMap<>(maxReceiptEntries, receiptTtlMs);
    }

    public LlmDebateCoordinator(ChatModel chatModel, String baseSystemPrompt) {
        this(chatModel, baseSystemPrompt, DEFAULT_MAX_ROUNDS);
    }

    /**
     * 执行多角色真实 LLM 辩论并返回最终裁决答案
     *
     * @param sessionId 会话或请求标识
     * @param question  包含 RAG 上下文的用户问题
     * @return 终审裁决答复
     */
    public String coordinateDebate(String sessionId, String question) {
        String effectiveSessionId = (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : UUID.randomUUID().toString();
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("辩论问题不能为空");
        }
        checkNotInterrupted();

        long startMs = System.currentTimeMillis();
        int llmCalls = 0;

        // 1. 正方立论 (Proposer)：基于问题与检索证据给出详实初稿
        String proposerPrompt = String.format("""
                【角色：正方立论专家 (Proposer)】
                请针对以下用户问题与参考资料，给出结构清晰、论据充分、逻辑严密的候选解答。
                如果涉及多个子问题或步骤，请逐一展开推导并明确给出结论。

                用户问题与背景材料：
                %s
                """, question);
        String currentProposal = invokeLlm(proposerPrompt);
        llmCalls++;

        String latestCritique = "";
        int rounds = 0;
        for (int round = 1; round <= maxRounds; round++) {
            checkNotInterrupted();
            rounds = round;
            // 2. 反方质询 (Critic)：严格挑刺事实错误、逻辑漏洞、遗漏条件或幻觉风险
            String criticPrompt = String.format("""
                    【角色：反方审校专家 (Critic - 第 %d 轮)】
                    请以严谨批判的视角审查【正方候选解答】，重点核查：
                    1. 是否存在与已知背景事实矛盾或无依据编造的断言；
                    2. 是否遗漏了用户问题中的关键子问题、边界条件或约束；
                    3. 推导过程是否存在逻辑跳跃或计算偏差。
                    若正方解答已完全准确完备，请明确输出“【无实质缺陷】”并简述理由；否则请逐条指出具体缺陷与修正建议。

                    用户问题与背景材料：
                    %s

                    【正方候选解答】：
                    %s
                    """, round, question, currentProposal);
            latestCritique = invokeLlm(criticPrompt);
            llmCalls++;

            // 若配置了多轮且反方已确认无实质缺陷，则提前收敛终止后续轮次
            if (round < maxRounds && latestCritique.contains("【无实质缺陷】")) {
                log.info("[LlmDebateCoordinator] 会话 {} 在第 {} 轮达成共识提前收敛", effectiveSessionId, round);
                break;
            }

            // 若还有下一轮，由正方根据反方质询修正提案
            if (round < maxRounds) {
                checkNotInterrupted();
                String refinePrompt = String.format("""
                        【角色：正方立论专家 (Proposer - 第 %d 轮修正)】
                        请结合反方审校专家的质询意见，核实并修正你的候选解答。保留正确部分，修正真实缺陷，驳回不合理的过度挑刺。

                        用户问题与背景材料：
                        %s

                        【上一版正方解答】：
                        %s

                        【反方质询意见】：
                        %s
                        """, round + 1, question, currentProposal, latestCritique);
                currentProposal = invokeLlm(refinePrompt);
                llmCalls++;
            }
        }

        checkNotInterrupted();
        // 3. 终审裁决 (Judge)：综合正方立论与反方质询，输出最终面向用户的权威答复
        String judgePrompt = String.format("""
                【角色：终审裁决官 (Judge)】
                请综合用户原始问题、正方候选解答以及反方审校意见，去伪存真，直接输出面向用户的最终高质量答复。
                要求：
                1. 采纳反方指出的合理事实修正与边界补充，剔除无依据的臆测；
                2. 直接给出完整、清晰、结构化的最终答案，不要在正文中暴露“正方”、“反方”、“终审”等内部辩论过程字样。

                用户问题与背景材料：
                %s

                【正方候选解答】：
                %s

                【反方审校意见】：
                %s
                """, question, currentProposal, latestCritique);
        String finalVerdict = invokeLlm(judgePrompt);
        llmCalls++;

        long durationMs = System.currentTimeMillis() - startMs;
        DebateReceipt receipt = new DebateReceipt(
                effectiveSessionId,
                rounds,
                llmCalls,
                currentProposal.length(),
                latestCritique.length(),
                finalVerdict.length(),
                sha256Hex(currentProposal),
                sha256Hex(latestCritique),
                sha256Hex(finalVerdict),
                durationMs
        );
        sessionReceipts.put(effectiveSessionId, receipt);
        log.info("[LlmDebateCoordinator] 会话 {} 辩论完成: rounds={}, llmCalls={}, duration={}ms",
                effectiveSessionId, rounds, llmCalls, durationMs);
        return finalVerdict;
    }

    public Optional<DebateReceipt> getLastReceipt(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionReceipts.get(sessionId));
    }

    public int getReceiptCacheSize() {
        return sessionReceipts.size();
    }

    private String invokeLlm(String roleUserPrompt) {
        checkNotInterrupted();
        List<Message> messages = new ArrayList<>();
        if (!baseSystemPrompt.isBlank()) {
            messages.add(new SystemMessage(baseSystemPrompt));
        }
        messages.add(new UserMessage(roleUserPrompt));
        ChatResponse response = chatModel.call(new Prompt(messages));
        checkNotInterrupted();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("LLM 辩论节点返回空响应");
        }
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("LLM 辩论节点返回空白文本");
        }
        return text;
    }

    private void checkNotInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("LlmDebateCoordinator 辩论任务已被超时或取消信号中断");
        }
    }

    static String sha256Hex(String value) {
        if (value == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}

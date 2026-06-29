package tech.qiantong.qknow.hermes.hermes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.hermes.judge.AiJudgeService;
import tech.qiantong.qknow.hermes.judge.JudgeResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Hermes 认知内核
 * 实现反思循环: 生成 → 自我评估 → 修正
 * 增强: fail-plausible 检测（self-consistency 检测流畅但不一致的回答）
 */
@Slf4j
public class HermesKernel {

    private static final double CONSISTENCY_THRESHOLD = 0.6;
    private static final int CONSISTENCY_SAMPLES = 3;

    private final AiJudgeService aiJudgeService;

    public HermesKernel(AiJudgeService aiJudgeService) {
        this.aiJudgeService = aiJudgeService;
    }

    /**
     * 执行反思循环
     *
     * @param chatModel    LLM 模型
     * @param systemPrompt 系统提示词
     * @param userQuery    用户问题
     * @param context      RAG 检索到的上下文
     * @param maxRetries   最大重试次数
     * @return 反思循环结果
     */
    public ReflectionResult reflect(ChatModel chatModel, String systemPrompt, String userQuery,
                                    String context, int maxRetries) {
        List<ReflectionAttempt> attempts = new ArrayList<>();

        String currentAnswer = null;
        JudgeResult lastJudgeResult = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            log.info("Hermes 反思循环 - 第 {} 次尝试", attempt + 1);

            // 1. 生成回答
            String promptContent = buildPrompt(systemPrompt, userQuery, context, currentAnswer, lastJudgeResult);
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(promptContent));
            messages.add(new UserMessage(userQuery));

            ChatResponse response = chatModel.call(new Prompt(messages));
            currentAnswer = response.getResult().getOutput().getText();

            // 2. AI Judge 评分
            JudgeResult judgeResult = aiJudgeService.judge(userQuery, context, currentAnswer);

            // 3. 记录尝试
            attempts.add(new ReflectionAttempt(attempt + 1, currentAnswer, judgeResult, 1.0, false));

            // 4. fail-plausible 检测: self-consistency check
            double consistency = checkSelfConsistency(chatModel, promptContent, userQuery);
            if (consistency < CONSISTENCY_THRESHOLD) {
                log.warn("fail-plausible 检测: consistency={} < {}, 回答可能不可靠", consistency, CONSISTENCY_THRESHOLD);
                attempts.get(attempts.size() - 1).setConsistencyScore(consistency);
                attempts.get(attempts.size() - 1).setFailPlausibleDetected(true);
            }

            // 5. 检查是否通过
            if (judgeResult.isPassed() && consistency >= CONSISTENCY_THRESHOLD) {
                log.info("Hermes 反思循环 - 第 {} 次尝试通过评分", attempt + 1);
                return new ReflectionResult(currentAnswer, attempts, true, attempt + 1);
            }

            log.info("Hermes 反思循环 - 第 {} 次尝试未通过评分，继续反思", attempt + 1);
            lastJudgeResult = judgeResult;
        }

        // 达到最大重试次数，返回最后一次的回答
        log.warn("Hermes 反思循环 - 达到最大重试次数 {}，返回最后一次回答", maxRetries);
        return new ReflectionResult(currentAnswer, attempts, false, maxRetries);
    }

    /**
     * 构建提示词
     * 如果是重试，会包含上次的评分反馈
     */
    private String buildPrompt(String systemPrompt, String userQuery, String context,
                                String previousAnswer, JudgeResult lastJudgeResult) {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt).append("\n\n");

        if (context != null && !context.isEmpty()) {
            sb.append("## 参考知识\n").append(context).append("\n\n");
        }

        if (previousAnswer != null && lastJudgeResult != null) {
            sb.append("## 上次回答的评分反馈\n");
            sb.append("- 事实性评分: ").append(lastJudgeResult.getFactualityScore()).append("\n");
            sb.append("- 相关性评分: ").append(lastJudgeResult.getRelevanceScore()).append("\n");
            sb.append("- 指令遵循度: ").append(lastJudgeResult.getInstructionScore()).append("\n");
            sb.append("- 综合评分: ").append(lastJudgeResult.getOverallScore()).append("\n");
            if (lastJudgeResult.getFeedback() != null) {
                sb.append("- 改进建议: ").append(lastJudgeResult.getFeedback()).append("\n");
            }
            sb.append("\n请根据以上反馈改进你的回答。\n");
        }

        return sb.toString();
    }

    /**
     * fail-plausible 检测: self-consistency check
     * 多次采样同一 prompt，检查回答的一致性
     * 一致性低 + 回答流畅 = fail-plausible（静默失败）
     */
    private double checkSelfConsistency(ChatModel chatModel, String systemPrompt, String userQuery) {
        try {
            List<String> samples = IntStream.range(0, CONSISTENCY_SAMPLES)
                    .mapToObj(i -> {
                        try {
                            ChatResponse resp = chatModel.call(new Prompt(List.of(
                                    new SystemMessage(systemPrompt),
                                    new UserMessage(userQuery)
                            )));
                            return resp.getResult().getOutput().getText();
                        } catch (Exception e) {
                            return "";
                        }
                    })
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toList());

            if (samples.size() < 2) return 1.0;

            // 计算 pairwise similarity (简单的词级 Jaccard)
            double totalSimilarity = 0;
            int pairs = 0;
            for (int i = 0; i < samples.size(); i++) {
                for (int j = i + 1; j < samples.size(); j++) {
                    totalSimilarity += jaccardSimilarity(samples.get(i), samples.get(j));
                    pairs++;
                }
            }
            return pairs > 0 ? totalSimilarity / pairs : 1.0;
        } catch (Exception e) {
            log.debug("Self-consistency check failed", e);
            return 1.0; // 失败时假设一致
        }
    }

    private double jaccardSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        java.util.Set<String> setA = java.util.Set.of(a.split("\\s+"));
        java.util.Set<String> setB = java.util.Set.of(b.split("\\s+"));
        java.util.Set<String> intersection = new java.util.HashSet<>(setA);
        intersection.retainAll(setB);
        java.util.Set<String> union = new java.util.HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReflectionResult {
        private String finalAnswer;
        private List<ReflectionAttempt> attempts;
        private boolean passed;
        private int totalAttempts;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReflectionAttempt {
        private int attemptNumber;
        private String answer;
        private JudgeResult judgeResult;
        private double consistencyScore;
        private boolean failPlausibleDetected;
    }
}

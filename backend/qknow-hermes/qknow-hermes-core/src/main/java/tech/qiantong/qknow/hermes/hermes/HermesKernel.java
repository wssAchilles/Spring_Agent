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

/**
 * Hermes 认知内核
 * 实现反思循环: 生成 → 自我评估 → 修正
 *
 * 借鉴 claw-code 的 agent 循环设计
 */
@Slf4j
public class HermesKernel {

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
            attempts.add(new ReflectionAttempt(attempt + 1, currentAnswer, judgeResult));

            // 4. 检查是否通过
            if (judgeResult.isPassed()) {
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
    }
}

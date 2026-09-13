package tech.qiantong.qknow.hermes.agent;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.hermes.judge.AiJudgeService;
import tech.qiantong.qknow.hermes.judge.JudgeResult;
import tech.qiantong.qknow.hermes.proto.*;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 反思智能体
 * 在 AgentOrchestrator 输出基础上增加反思循环：
 * 生成 -> AI Judge 评分 -> 不通过则重试
 */
@Slf4j
public class ReflectiveAgent {

    private final AgentOrchestrator orchestrator;
    private final AiJudgeService judgeService;
    private final ReflectiveConfig config;

    public ReflectiveAgent(AgentOrchestrator orchestrator, AiJudgeService judgeService, ReflectiveConfig config) {
        this.orchestrator = orchestrator;
        this.judgeService = judgeService;
        this.config = config;
    }

    /**
     * 执行对话（带反思循环）
     */
    public Flux<ChatEvent> chat(ChatRequest request) {
        if (!config.isEnabled()) {
            return orchestrator.chat(request);
        }

        return Flux.create(emitter -> {
            int maxRetries = config.getMaxRetries();
            int totalRounds = 0;
            boolean passed = false;

            ChatRequest currentRequest = request;

            for (int round = 1; round <= maxRetries + 1; round++) {
                totalRounds = round;

                // 1. 发射 ReflectionStart
                emitter.next(ChatEvent.newBuilder()
                        .setRequestId(request.getRequestId())
                        .setReflectionStart(ReflectionStart.newBuilder()
                                .setRound(round)
                                .setMaxRetries(maxRetries)
                                .build())
                        .build());

                // 2. 调用 orchestrator，收集完整回答
                AtomicReference<String> fullText = new AtomicReference<>("");
                AtomicReference<Boolean> hasError = new AtomicReference<>(false);

                orchestrator.chat(currentRequest).toIterable().forEach(event -> {
                    // 收集完整文本
                    if (event.hasFinished()) {
                        fullText.set(event.getFinished().getFullText());
                    }
                    if (event.hasError()) {
                        hasError.set(true);
                    }

                    // 转发流式事件（跳过编排器的 DoneSignal，反思循环自行管理结束信号）
                    if (!event.hasDone()) {
                        emitter.next(event);
                    }
                });

                // 如果有错误，直接结束
                if (hasError.get()) {
                    break;
                }

                // 3. AI Judge 评分
                String systemPrompt = currentRequest.getSystemPrompt();
                String question = currentRequest.getQuestion();
                String answer = fullText.get();

                JudgeResult judgeResult = judgeService.judge(systemPrompt, question, answer);

                // 4. 发射 ReflectionScore
                emitter.next(ChatEvent.newBuilder()
                        .setRequestId(request.getRequestId())
                        .setReflectionScore(ReflectionScore.newBuilder()
                                .setFactuality(judgeResult.getFactualityScore())
                                .setRelevance(judgeResult.getRelevanceScore())
                                .setInstruction(judgeResult.getInstructionScore())
                                .setOverall(judgeResult.getOverallScore())
                                .setPassed(judgeResult.isPassed())
                                .setFeedback(judgeResult.getFeedback() != null ? judgeResult.getFeedback() : "")
                                .build())
                        .build());

                // 5. 检查是否通过
                if (judgeResult.isPassed()) {
                    passed = true;
                    break;
                }

                // 6. 未通过但还有重试次数 -> 回填反馈并重试
                if (round > maxRetries) {
                    break;
                }

                log.info("反思循环第 {} 轮未通过评分 (score={}), 注入反思意见并重试...",
                        round, judgeResult.getOverallScore());

                // 结构化回填上一轮的评审意见与改进建议
                String feedback = judgeResult.getFeedback() != null ? judgeResult.getFeedback() : "";
                String critiqueNotice = "\n\n[reflection_critique: 上一轮回答未通过评估。评审意见与改进建议: " + feedback + "。请在本次回答中针对性修正该问题。]";
                String enhancedSystemPrompt = (request.getSystemPrompt() != null ? request.getSystemPrompt() : "") + critiqueNotice;

                currentRequest = request.toBuilder()
                        .setSystemPrompt(enhancedSystemPrompt)
                        .build();
            }

            // 发射 ReflectionComplete
            emitter.next(ChatEvent.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setReflectionComplete(ReflectionComplete.newBuilder()
                            .setTotalRounds(totalRounds)
                            .setPassed(passed)
                            .build())
                    .build());

            // 发射 DoneSignal
            emitter.next(ChatEvent.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setDone(DoneSignal.newBuilder().build())
                    .build());

            emitter.complete();
        });
    }
}

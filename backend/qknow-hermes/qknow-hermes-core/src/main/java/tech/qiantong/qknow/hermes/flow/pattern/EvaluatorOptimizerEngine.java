package tech.qiantong.qknow.hermes.flow.pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Anthropic Evaluator-Optimizer 核心工作流引擎
 * <p>
 * 参考 Anthropic《Building Effective Agents》权威架构：
 * 1. 采用 Generator - Evaluator 两阶段反思闭环；
 * 2. 基于评分准则 (Rubric) 进行多轮客观打分与挑刺建议 (Critique)；
 * 3. 严格设置最大迭代轮数与及格阈值熔断控制，杜绝无休止震荡与 Token 浪费；
 * 4. 产出包含完整迭代历史与最终高分方案的不可变凭单。
 * </p>
 */
@Slf4j
@Component
public class EvaluatorOptimizerEngine {

    /**
     * 默认最大迭代轮数
     */
    public static final int DEFAULT_MAX_ITERATIONS = 3;

    /**
     * 默认目标通过分值 (百分制)
     */
    public static final int DEFAULT_TARGET_SCORE = 85;

    /**
     * 评估结果 Record
     */
    public record EvaluationResult(
            boolean passed,
            int score,
            String critique,
            List<String> suggestions
    ) {
        public static EvaluationResult pass(int score, String critique) {
            return new EvaluationResult(true, score, critique, Collections.emptyList());
        }

        public static EvaluationResult fail(int score, String critique, List<String> suggestions) {
            return new EvaluationResult(false, score, critique, suggestions != null ? suggestions : Collections.emptyList());
        }
    }

    /**
     * 单轮迭代过程记录 Record
     */
    public record OptimizationIteration(
            int iterationRound,
            String solution,
            EvaluationResult evaluation,
            long durationMillis
    ) {}

    /**
     * 全流程优化最终结果凭单 Record
     */
    public record OptimizationRunResult(
            boolean success,
            String finalSolution,
            int finalScore,
            int totalIterations,
            List<OptimizationIteration> iterationHistory,
            String terminationReason
    ) {}

    /**
     * 生成器/优化器函数接口
     */
    @FunctionalInterface
    public interface GeneratorFunction {
        String generate(String taskPrompt, String previousSolution, EvaluationResult lastFeedback);
    }

    /**
     * 评估器函数接口
     */
    @FunctionalInterface
    public interface EvaluatorFunction {
        EvaluationResult evaluate(String taskPrompt, String currentSolution, String rubric);
    }

    /**
     * 执行 Evaluator-Optimizer 闭环自反思优化
     *
     * @param taskPrompt      用户或业务核心任务指令
     * @param rubric          严格评估评分准则
     * @param generator       解法生成/优化器
     * @param evaluator       评估裁判器
     * @return 最终优化结果
     */
    public OptimizationRunResult optimize(
            String taskPrompt,
            String rubric,
            GeneratorFunction generator,
            EvaluatorFunction evaluator
    ) {
        return optimize(taskPrompt, rubric, DEFAULT_TARGET_SCORE, DEFAULT_MAX_ITERATIONS, generator, evaluator);
    }

    /**
     * 执行 Evaluator-Optimizer 闭环自反思优化 (指定目标分与最大轮数)
     *
     * @param taskPrompt      用户或业务核心任务指令
     * @param rubric          严格评估评分准则
     * @param targetScore     目标及格分值 (通常 80~90)
     * @param maxIterations   最大允许迭代轮数 (1~5)
     * @param generator       解法生成/优化器
     * @param evaluator       评估裁判器
     * @return 最终优化结果
     */
    public OptimizationRunResult optimize(
            String taskPrompt,
            String rubric,
            int targetScore,
            int maxIterations,
            GeneratorFunction generator,
            EvaluatorFunction evaluator
    ) {
        Objects.requireNonNull(taskPrompt, "taskPrompt 不能为空");
        Objects.requireNonNull(generator, "generator 不能为空");
        Objects.requireNonNull(evaluator, "evaluator 不能为空");

        int safeMaxIterations = Math.max(1, Math.min(maxIterations, 5));
        int safeTargetScore = Math.max(1, Math.min(targetScore, 100));
        String safeRubric = (rubric != null && !rubric.isBlank()) ? rubric : "标准质量与完整性评估准则";

        List<OptimizationIteration> history = new ArrayList<>();
        String currentSolution = null;
        EvaluationResult lastFeedback = null;

        String bestSolution = null;
        int bestScore = -1;
        String terminationReason = "MAX_ITERATIONS_REACHED";

        log.info("[Evaluator-Optimizer] 启动自反思工作流: targetScore={}, maxIterations={}", safeTargetScore, safeMaxIterations);

        for (int round = 1; round <= safeMaxIterations; round++) {
            long startTime = System.currentTimeMillis();

            // 1. 生成或针对前轮批评进行优化
            currentSolution = generator.generate(taskPrompt, currentSolution, lastFeedback);
            if (currentSolution == null) {
                currentSolution = "";
            }

            // 2. 评估器进行客观打分与改进建议
            EvaluationResult eval = evaluator.evaluate(taskPrompt, currentSolution, safeRubric);
            if (eval == null) {
                eval = EvaluationResult.pass(safeTargetScore, "默认通过");
            }

            long duration = System.currentTimeMillis() - startTime;
            history.add(new OptimizationIteration(round, currentSolution, eval, duration));

            log.info("[Evaluator-Optimizer] 轮次 {}/{}: score={}, passed={}, duration={}ms",
                    round, safeMaxIterations, eval.score(), eval.passed(), duration);

            // 更新历史最优解
            if (eval.score() > bestScore) {
                bestScore = eval.score();
                bestSolution = currentSolution;
            }

            // 3. 达标判定：达到目标分或判定通过即刻跳出收敛
            if (eval.score() >= safeTargetScore || eval.passed()) {
                terminationReason = "REACHED_TARGET_SCORE";
                bestScore = eval.score();
                bestSolution = currentSolution;
                break;
            }

            lastFeedback = eval;
        }

        boolean success = "REACHED_TARGET_SCORE".equals(terminationReason) || (bestScore >= safeTargetScore);
        log.info("[Evaluator-Optimizer] 闭环结束: success={}, finalScore={}, totalRounds={}, reason={}",
                success, bestScore, history.size(), terminationReason);

        return new OptimizationRunResult(
                success,
                bestSolution,
                bestScore,
                history.size(),
                Collections.unmodifiableList(history),
                terminationReason
        );
    }
}

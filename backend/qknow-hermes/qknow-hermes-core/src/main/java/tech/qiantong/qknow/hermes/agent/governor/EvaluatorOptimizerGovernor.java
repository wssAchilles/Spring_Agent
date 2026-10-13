package tech.qiantong.qknow.hermes.agent.governor;

import java.util.List;

/**
 * 双环质量评估与自愈优化治理器契约接口 (EvaluatorOptimizerGovernor)
 * 遵循 Anthropic Evaluator-Optimizer 范式与李雅普诺夫有界收敛引理。
 */
public interface EvaluatorOptimizerGovernor {

    /**
     * 评估产物不可变结果 Record
     */
    record EvaluationScore(
            double score,
            boolean passed,
            List<String> critiques,
            String reasoningTrace
    ) {
        public EvaluationScore {
            critiques = critiques != null ? List.copyOf(critiques) : List.of();
            reasoningTrace = reasoningTrace != null ? reasoningTrace : "";
        }
    }

    /**
     * 执行外环评估打分
     */
    EvaluationScore evaluate(String originalTask, String candidateOutput);

    /**
     * 根据诊断批评生成优化迭代候选
     */
    String optimize(String originalTask, String currentCandidate, EvaluationScore lastScore);

    /**
     * 执行完整的双环优化闭环流水线
     *
     * @param sessionId 会话标识
     * @param originalTask 原始指令/任务
     * @param initialCandidate 初始候选生成内容
     * @return 最终双环质检凭单与优化内容包装
     */
    OptimizationExecutionResult executeOptimizationLoop(String sessionId, String originalTask, String initialCandidate);

    /**
     * 优化执行最终打包 Record
     */
    record OptimizationExecutionResult(
            String finalOutput,
            DualLoopQualityReceipt receipt
    ) {}
}

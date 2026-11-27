package tech.qiantong.qknow.hermes.cognitive.control;

/**
 * 变分效用后验计算器（对应论文第 3 章真实后验目标函数）。
 * <p>
 * 数学定义：
 * {@code U_t = clip_[0,1](w_acc * S_consis + w_tool * R_hoare - lambda_tok * (C_uncached / B_max) - lambda_lat * (L_t / L_max))}
 * 彻底消除硬编码 {@code 0.92 / 0.35}，以真实一致性评分、工具契约通过率、未缓存 Token 成本与端到端延迟反馈闭环至超网 NIG 后验。
 * </p>
 */
public class VariationalUtilityEvaluator {

    private final double weightAccuracy;
    private final double weightTool;
    private final double lambdaToken;
    private final double lambdaLatency;
    private final String hmacSecretKey;

    public VariationalUtilityEvaluator() {
        this(0.60, 0.30, 0.15, 0.10, "hermes-utility-default-secret");
    }

    public VariationalUtilityEvaluator(
            double weightAccuracy,
            double weightTool,
            double lambdaToken,
            double lambdaLatency,
            String hmacSecretKey
    ) {
        this.weightAccuracy = weightAccuracy;
        this.weightTool = weightTool;
        this.lambdaToken = lambdaToken;
        this.lambdaLatency = lambdaLatency;
        this.hmacSecretKey = (hmacSecretKey == null || hmacSecretKey.isBlank())
                ? "hermes-utility-default-secret"
                : hmacSecretKey;
    }

    /**
     * 计算当前请求的真实变分观测效用 {@code U_t in [0, 1]} 并签发不可变凭单。
     */
    public VariationalUtilityReceipt evaluate(
            String requestId,
            boolean completedNormally,
            double consistencyScore,
            double hoarePassRate,
            long promptTokens,
            long cacheHitTokens,
            long completionTokens,
            long latencyMs,
            BudgetVector6D budget
    ) {
        BudgetVector6D safeBudget = budget == null ? BudgetVector6D.defaults() : budget;
        double safeConsis = clamp01(completedNormally ? consistencyScore : Math.min(consistencyScore, 0.25));
        double safeHoare = clamp01(hoarePassRate);
        long uncachedPrompt = Math.max(0L, promptTokens - Math.max(0L, cacheHitTokens));
        long uncachedTotal = Math.max(0L, uncachedPrompt + Math.max(0L, completionTokens));

        double tokenPenaltyRatio = Math.min(2.0, (double) uncachedTotal / Math.max(1L, safeBudget.maxTotalTokens()));
        double latencyPenaltyRatio = Math.min(2.0, (double) Math.max(0L, latencyMs) / Math.max(1L, safeBudget.maxLatencyMs()));

        double rawUtility = weightAccuracy * safeConsis
                + weightTool * safeHoare
                - lambdaToken * tokenPenaltyRatio
                - lambdaLatency * latencyPenaltyRatio;
        if (!completedNormally) {
            rawUtility = Math.min(rawUtility, 0.30);
        }
        double realized = clamp01(rawUtility);

        return VariationalUtilityReceipt.createSigned(
                requestId == null ? "req-utility" : requestId,
                safeConsis,
                safeHoare,
                uncachedTotal,
                safeBudget.maxTotalTokens(),
                Math.max(0L, latencyMs),
                safeBudget.maxLatencyMs(),
                realized,
                System.currentTimeMillis(),
                hmacSecretKey
        );
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || value <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, value);
    }
}

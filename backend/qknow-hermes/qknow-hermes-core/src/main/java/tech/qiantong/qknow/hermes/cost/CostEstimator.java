package tech.qiantong.qknow.hermes.cost;

import java.util.Map;

/**
 * 模型费用估算器。
 */
public class CostEstimator {

    private static final double DEFAULT_INPUT_PRICE_PER_MILLION = 1.0;
    private static final double DEFAULT_OUTPUT_PRICE_PER_MILLION = 2.0;

    private static final Map<String, double[]> PRICING = Map.of(
            "deepseek-chat", new double[]{1.0, 2.0},
            "gpt-4o", new double[]{25.0, 47.0}
    );

    public CostEstimator() {
    }

    /**
     * 估算指定模型的 token 使用费用（美元）。
     *
     * @param modelName       模型名称
     * @param promptTokens     输入 token 数
     * @param completionTokens 输出 token 数
     * @return 估算费用
     */
    public double estimate(String modelName, long promptTokens, long completionTokens) {
        double[] prices = PRICING.getOrDefault(modelName,
                new double[]{DEFAULT_INPUT_PRICE_PER_MILLION, DEFAULT_OUTPUT_PRICE_PER_MILLION});

        double inputCost = (promptTokens / 1_000_000.0) * prices[0];
        double outputCost = (completionTokens / 1_000_000.0) * prices[1];

        return inputCost + outputCost;
    }
}

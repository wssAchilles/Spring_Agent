package tech.qiantong.qknow.hermes.cognitive.supernet;

/**
 * DeepSeek 官方思考深度等级枚举。
 * <p>
 * 严格对齐 DeepSeek API 最新规范（{@code thinking.type} 与 {@code reasoning_effort}）：
 * 当处于开启思考等级（{@code LOW / MEDIUM / HIGH}）时，必须置 {@code temperature = null} 并钳制 {@code top_p in [0.95, 1.0]}。
 * </p>
 */
public enum DeepSeekReasoningEffort {

    /** 关闭长思维链，极速直答输出 */
    NONE("none", false, 0.05),

    /** 轻度思考努力度 */
    LOW("low", true, 0.20),

    /** 中度思考努力度 */
    MEDIUM("medium", true, 0.50),

    /** 深度长思维链思考努力度 */
    HIGH("high", true, 1.00);

    private final String wireValue;
    private final boolean thinkingEnabled;
    private final double normalizedCost;

    DeepSeekReasoningEffort(String wireValue, boolean thinkingEnabled, double normalizedCost) {
        this.wireValue = wireValue;
        this.thinkingEnabled = thinkingEnabled;
        this.normalizedCost = normalizedCost;
    }

    public String wireValue() {
        return wireValue;
    }

    public boolean thinkingEnabled() {
        return thinkingEnabled;
    }

    public double normalizedCost() {
        return normalizedCost;
    }
}

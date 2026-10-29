package tech.qiantong.qknow.hermes.agent.harness;

/**
 * ReAct 与成本守卫阈值契约。
 */
public record GuardrailsSpecificationRecord(
        int maxCycleSteps,
        int loopThreshold,
        long maxTokenBudget,
        double maxCostCny
) {
    public GuardrailsSpecificationRecord {
        if (maxCycleSteps <= 0) {
            throw new IllegalArgumentException("maxCycleSteps 必须大于 0");
        }
        if (loopThreshold <= 0) {
            throw new IllegalArgumentException("loopThreshold 必须大于 0");
        }
        if (maxTokenBudget <= 0L) {
            throw new IllegalArgumentException("maxTokenBudget 必须大于 0");
        }
        if (!Double.isFinite(maxCostCny) || maxCostCny <= 0.0D) {
            throw new IllegalArgumentException("maxCostCny 必须为有限正数");
        }
    }

    public String canonical() {
        return maxCycleSteps + "\u001f" + loopThreshold + "\u001f" + maxTokenBudget + "\u001f" + maxCostCny;
    }

    public static GuardrailsSpecificationRecord defaults() {
        return new GuardrailsSpecificationRecord(10, 3, 100_000L, 0.05D);
    }
}

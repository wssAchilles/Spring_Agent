package tech.qiantong.qknow.ai.presentation.enums;

/**
 * 用户认知负荷等级枚举 (Sweller CLT & Cowan 4±1 定理)
 */
public enum CognitiveLoadLevel {
    /** 低负荷 (0.0 ~ 0.35)：全量开放视图，输出丰富图表与深度 Markdown */
    LOW(4, true, "全量开放视图"),
    /** 中负荷 (0.35 ~ 0.65)：平衡摘要视图，聚焦关键图表与指标，次要文本默认折叠 */
    MEDIUM(3, true, "平衡摘要视图"),
    /** 高负荷 (0.65 ~ 0.85)：关键指标视图，抑制耗时图表，仅呈现 2 个高光指标与要点 */
    HIGH(2, false, "关键指标视图"),
    /** 极端负荷 (0.85 ~ 1.00)：绝对阻断极简模式，严格只呈现 1 项核心行动横幅 */
    CRITICAL(1, false, "极简行动单");

    private final int maxActiveChunks;
    private final boolean allowComplexCharts;
    private final String description;

    CognitiveLoadLevel(int maxActiveChunks, boolean allowComplexCharts, String description) {
        this.maxActiveChunks = maxActiveChunks;
        this.allowComplexCharts = allowComplexCharts;
        this.description = description;
    }

    public int getMaxActiveChunks() { return maxActiveChunks; }
    public boolean isAllowComplexCharts() { return allowComplexCharts; }
    public String getDescription() { return description; }
}

package tech.qiantong.qknow.ai.presentation.enums;

/**
 * 结构化多模态展示块类型枚举
 */
public enum PresentationBlockType {
    /** 排版精良的 Markdown 核心要点 */
    TEXT_MARKDOWN,
    /** 单色钛金高光指标卡 (数值、单位、环比) */
    METRIC_CARD,
    /** 自适应 ECharts JSON 配置 (折线、柱状、饼图) */
    ECHART_SPEC,
    /** 排查或执行步骤流 (步骤号、状态、描述) */
    STEP_FLOW,
    /** 高对比度单色警报与确认单 */
    ACTION_BANNER
}

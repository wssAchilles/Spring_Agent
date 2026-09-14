package tech.qiantong.qknow.ai.immune.enums;

/**
 * 免疫系统判定与防御处置行为枚举
 *
 * @author Achilles
 * @since Phase 45
 */
public enum ImmuneAction {
    /**
     * 高风险确认：阻断请求并隔离审查
     */
    BLOCK_AND_ISOLATE,

    /**
     * 中风险疑似：清洗危险语义与指令脱敏重写
     */
    SANITIZE_AND_REWRITE,

    /**
     * 安全基准：良性通过放行
     */
    SAFE_PASS
}

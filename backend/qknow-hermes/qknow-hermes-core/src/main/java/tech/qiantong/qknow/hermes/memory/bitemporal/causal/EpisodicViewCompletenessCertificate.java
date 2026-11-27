package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import java.util.Objects;

/**
 * 情景-关系动态物化视图的三元组完备性证书（Phase 10.3 CC-E2R，定理 3）。
 * <p>
 * 防止半结构化抽取漏抽时诱发大模型“封闭世界假设”错误排除未入表实体，依据实体具名绑定率、
 * 货币提及召回率与展示截断率显式划分 {@link CompletenessGrade} 权威等级与警示横幅。
 * </p>
 */
public record EpisodicViewCompletenessCertificate(
        CompletenessGrade grade,
        double entityBindingCoverage,
        double numericMentionRecall,
        double displayCoverageRatio,
        int totalCurrencyMentionsInSource,
        int boundMerchantSpendRows,
        int anonymousUserSpendRows,
        boolean hasRelativeTemporalAmbiguity,
        String advisoryBanner
) {
    public enum CompletenessGrade {
        COMPLETE_AUTHORITATIVE,
        PARTIAL_SUPPLEMENTARY,
        UNRELIABLE_SUPPRESS
    }

    public EpisodicViewCompletenessCertificate {
        grade = Objects.requireNonNullElse(grade, CompletenessGrade.COMPLETE_AUTHORITATIVE);
        advisoryBanner = advisoryBanner != null ? advisoryBanner : "";
    }
}

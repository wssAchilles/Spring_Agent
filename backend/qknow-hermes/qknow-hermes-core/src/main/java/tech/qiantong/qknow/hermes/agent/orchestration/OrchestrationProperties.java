package tech.qiantong.qknow.hermes.agent.orchestration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Hermes 多智能体编排受控配置属性 (P0-15)
 *
 * <p>严格遵循 fail-safe 治理原则：全局编排开关 {@code hermes.orchestration.enabled} 默认锁定为 {@code false}，
 * 仅在显式配置开启且对应 Bot 的 {@code parameters.orchestrationMode} 设置为 {@code SWARM} 或 {@code DEBATE} 时生效。
 * 成本预算模型严格遵循 DeepSeek 官方定价表。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "hermes.orchestration")
public class OrchestrationProperties {

    public static final long DEFAULT_TIMEOUT_MS = 15_000L;
    public static final int DEFAULT_MAX_WORKERS = 3;
    public static final int DEFAULT_DEBATE_ROUNDS = 1;
    public static final double DEFAULT_MAX_COST_CNY = 0.05D;
    public static final int DEFAULT_MAX_CONCURRENT_LLM_CALLS = 8;
    public static final int DEFAULT_ESTIMATED_OUTPUT_TOKENS_PER_CALL = 256;

    /**
     * 全局多智能体编排总开关（默认关闭，确保默认路径 100% 零回归与 fail-safe）
     */
    private boolean enabled = false;

    /**
     * 单次多智能体编排硬超时上限（毫秒），超时后立即中断底层任务并回退 SINGLE
     */
    private long timeoutMs = DEFAULT_TIMEOUT_MS;

    /**
     * SWARM 模式单次请求最大并行 Worker 数上限
     */
    private int maxWorkers = DEFAULT_MAX_WORKERS;

    /**
     * DEBATE 模式默认辩论轮次（1 轮对应 Proposer -> Critic -> Judge 共 3 次调用）
     */
    private int debateRounds = DEFAULT_DEBATE_ROUNDS;

    /**
     * 单次多智能体编排最大人民币成本预算上限（元）
     */
    private double maxCostCny = DEFAULT_MAX_COST_CNY;

    /**
     * 多智能体 LLM 调用最大并发信号量许可数
     */
    private int maxConcurrentLlmCalls = DEFAULT_MAX_CONCURRENT_LLM_CALLS;

    /**
     * 单次多智能体子调用预估输出 Token 数（用于前置成本预算核算）
     */
    private int estimatedOutputTokensPerCall = DEFAULT_ESTIMATED_OUTPUT_TOKENS_PER_CALL;

    /**
     * 审计凭单与黑板快照有界缓存最大条目数
     */
    private int receiptCacheMaxEntries = BoundedTtlMap.DEFAULT_MAX_ENTRIES;

    /**
     * 审计凭单与黑板快照有界缓存过期时间（毫秒，默认 30 分钟）
     */
    private long receiptCacheTtlMs = BoundedTtlMap.DEFAULT_TTL_MS;

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs > 0L ? Math.min(timeoutMs, 120_000L) : DEFAULT_TIMEOUT_MS;
    }

    public void setMaxWorkers(int maxWorkers) {
        this.maxWorkers = maxWorkers > 0 ? Math.min(maxWorkers, 5) : DEFAULT_MAX_WORKERS;
    }

    public void setDebateRounds(int debateRounds) {
        this.debateRounds = debateRounds > 0 ? Math.min(debateRounds, 2) : DEFAULT_DEBATE_ROUNDS;
    }

    public void setMaxCostCny(double maxCostCny) {
        this.maxCostCny = (Double.isFinite(maxCostCny) && maxCostCny > 0.0D) ? maxCostCny : DEFAULT_MAX_COST_CNY;
    }

    public void setMaxConcurrentLlmCalls(int maxConcurrentLlmCalls) {
        this.maxConcurrentLlmCalls = maxConcurrentLlmCalls > 0 ? Math.min(maxConcurrentLlmCalls, 64) : DEFAULT_MAX_CONCURRENT_LLM_CALLS;
    }

    public void setEstimatedOutputTokensPerCall(int estimatedOutputTokensPerCall) {
        this.estimatedOutputTokensPerCall = estimatedOutputTokensPerCall > 0 ? estimatedOutputTokensPerCall : DEFAULT_ESTIMATED_OUTPUT_TOKENS_PER_CALL;
    }

    public void setReceiptCacheMaxEntries(int receiptCacheMaxEntries) {
        this.receiptCacheMaxEntries = receiptCacheMaxEntries > 0 ? receiptCacheMaxEntries : BoundedTtlMap.DEFAULT_MAX_ENTRIES;
    }

    public void setReceiptCacheTtlMs(long receiptCacheTtlMs) {
        this.receiptCacheTtlMs = receiptCacheTtlMs > 0L ? receiptCacheTtlMs : BoundedTtlMap.DEFAULT_TTL_MS;
    }

    /**
     * 是否默认采用高峰期（标准时段）保守单价进行预算门禁核算
     */
    private boolean usePeakPricingConservative = true;

    /**
     * DeepSeek 官方定价：高峰期（08:30-00:30）缓存命中输入单价（元 / 百万 Tokens）
     */
    private double peakInputCacheHitPricePerMillionCny = 0.04D;

    /**
     * DeepSeek 官方定价：高峰期（08:30-00:30）缓存未命中输入单价（元 / 百万 Tokens）
     */
    private double peakInputCacheMissPricePerMillionCny = 2.00D;

    /**
     * DeepSeek 官方定价：高峰期（08:30-00:30）输出单价（元 / 百万 Tokens）
     */
    private double peakOutputPricePerMillionCny = 8.00D;

    /**
     * DeepSeek 官方定价：空闲期（00:30-08:30）缓存命中输入单价（元 / 百万 Tokens）
     */
    private double offPeakInputCacheHitPricePerMillionCny = 0.02D;

    /**
     * DeepSeek 官方定价：空闲期（00:30-08:30）缓存未命中输入单价（元 / 百万 Tokens）
     */
    private double offPeakInputCacheMissPricePerMillionCny = 1.00D;

    /**
     * DeepSeek 官方定价：空闲期（00:30-08:30）输出单价（元 / 百万 Tokens）
     */
    private double offPeakOutputPricePerMillionCny = 4.00D;

    public double effectiveInputCacheHitPricePerMillionCny() {
        return usePeakPricingConservative ? peakInputCacheHitPricePerMillionCny : offPeakInputCacheHitPricePerMillionCny;
    }

    public double effectiveInputCacheMissPricePerMillionCny() {
        return usePeakPricingConservative ? peakInputCacheMissPricePerMillionCny : offPeakInputCacheMissPricePerMillionCny;
    }

    public double effectiveOutputPricePerMillionCny() {
        return usePeakPricingConservative ? peakOutputPricePerMillionCny : offPeakOutputPricePerMillionCny;
    }
}

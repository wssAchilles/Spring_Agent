package tech.qiantong.qknow.hermes.cost;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 金融级无锁 DeepSeek 成本与 Token 治理引擎
 * 采用纳元（Nano-CNY: 10^-9 元）定点整数与 Striped LongAdder 分段无锁累加，100% 消除浮点截断误差与锁竞争
 */
@Component
public class DeepSeekCostGovernor {

    private static final long NANO_PER_CNY = 1_000_000_000L;

    /**
     * 模型价格配置（每 Token 对应的纳元数）
     */
    public record ModelPricingNano(long hitPromptNano, long missPromptNano, long completionNano) {}

    private static final Map<String, ModelPricingNano> PRICING_TABLE = Map.of(
            "deepseek-chat", new ModelPricingNano(500L, 2_000L, 8_000L),
            "deepseek-reasoner", new ModelPricingNano(1_000L, 4_000L, 16_000L)
    );
    private static final ModelPricingNano DEFAULT_PRICING = new ModelPricingNano(500L, 2_000L, 8_000L);

    /**
     * 模型级分段累加器容器
     */
    public static class ModelAccumulator {
        public final LongAdder promptCacheHitTokens = new LongAdder();
        public final LongAdder promptCacheMissTokens = new LongAdder();
        public final LongAdder completionTokens = new LongAdder();
        public final LongAdder reasoningTokens = new LongAdder();
        public final LongAdder costNanoYuan = new LongAdder();
    }

    private final Map<String, ModelAccumulator> accumulators = new ConcurrentHashMap<>();
    private final AtomicInteger activeLlmRequests = new AtomicInteger(0);
    private final MeterRegistry registry;

    public DeepSeekCostGovernor(MeterRegistry registry) {
        this.registry = registry;

        if (registry != null) {
            // 全局活跃请求数 Gauge
            Gauge.builder("llm.active.requests", activeLlmRequests, AtomicInteger::get)
                    .description("当前正在执行的 LLM 并发请求数")
                    .register(registry);

            // 预注册默认模型的 Prometheus 指标
            registerModelMetrics("deepseek-chat");
            registerModelMetrics("deepseek-reasoner");
        }
    }

    private ModelAccumulator getOrCreate(String model) {
        return accumulators.computeIfAbsent(model, m -> {
            if (registry != null) {
                registerModelMetrics(m);
            }
            return new ModelAccumulator();
        });
    }

    public ModelAccumulator getAccumulator(String modelName) {
        return accumulators.get(modelName);
    }

    private void registerModelMetrics(String model) {
        ModelAccumulator acc = accumulators.computeIfAbsent(model, k -> new ModelAccumulator());

        if (registry != null) {
            // 注册 Token 累加 Gauge
            Gauge.builder("llm.tokens.total", acc.promptCacheHitTokens, LongAdder::doubleValue)
                    .tag("model", model).tag("type", "prompt_cache_hit").register(registry);
            Gauge.builder("llm.tokens.total", acc.promptCacheMissTokens, LongAdder::doubleValue)
                    .tag("model", model).tag("type", "prompt_cache_miss").register(registry);
            Gauge.builder("llm.tokens.total", acc.completionTokens, LongAdder::doubleValue)
                    .tag("model", model).tag("type", "completion").register(registry);
            Gauge.builder("llm.tokens.total", acc.reasoningTokens, LongAdder::doubleValue)
                    .tag("model", model).tag("type", "reasoning").register(registry);

            // 注册法定货币累计费用 Gauge (单位: 元)
            Gauge.builder("llm.cost.cny.total", acc.costNanoYuan, nano -> (double) nano.sum() / NANO_PER_CNY)
                    .tag("model", model)
                    .description("按 DeepSeek 官方计价规则累计的真实开销（元）")
                    .register(registry);
        }
    }

    public void markRequestStart() {
        activeLlmRequests.incrementAndGet();
    }

    public void markRequestEnd() {
        activeLlmRequests.decrementAndGet();
    }

    /**
     * 核心记账方法：纳元精确累加与无锁计数
     */
    public void recordUsage(String modelName, long cacheHitPrompt, long cacheMissPrompt,
                            long completion, long reasoning) {
        String effectiveModel = (modelName != null && !modelName.isBlank()) ? modelName : "deepseek-chat";
        ModelAccumulator acc = getOrCreate(effectiveModel);
        acc.promptCacheHitTokens.add(cacheHitPrompt);
        acc.promptCacheMissTokens.add(cacheMissPrompt);
        acc.completionTokens.add(completion);
        acc.reasoningTokens.add(reasoning);

        ModelPricingNano pricing = PRICING_TABLE.getOrDefault(effectiveModel, DEFAULT_PRICING);
        long requestCostNano = (cacheHitPrompt * pricing.hitPromptNano)
                + (cacheMissPrompt * pricing.missPromptNano)
                + (completion * pricing.completionNano);

        acc.costNanoYuan.add(requestCostNano);
    }

    /**
     * 获取指定模型当前消费金额（元，精确至小数）
     */
    public double getCostInYuan(String modelName) {
        ModelAccumulator acc = accumulators.get(modelName);
        return acc != null ? (double) acc.costNanoYuan.sum() / NANO_PER_CNY : 0.0;
    }
}

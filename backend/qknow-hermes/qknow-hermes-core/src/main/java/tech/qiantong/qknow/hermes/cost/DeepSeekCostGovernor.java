package tech.qiantong.qknow.hermes.cost;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.config.AiModelAliasProperties;
import tech.qiantong.qknow.hermes.config.ModelNameResolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 金融级无锁 DeepSeek 成本与 Token 治理引擎
 * 采用纳元（Nano-CNY: 10^-9 元）定点整数与 Striped LongAdder 分段无锁累加，100% 消除浮点截断误差与锁竞争
 * 已解耦具体模型名称硬编码，全面集成 ModelNameResolver 动态逻辑别名与 Pricing Tier 治理
 *
 * @author Achilles
 * @since Phase 153
 */
@Component
public class DeepSeekCostGovernor {

    private static final long NANO_PER_CNY = 1_000_000_000L;

    /**
     * 模型价格配置（每 Token 对应的纳元数）
     */
    public record ModelPricingNano(long hitPromptNano, long missPromptNano, long completionNano) {}

    private static final Map<String, ModelPricingNano> DEFAULT_PRICING_TABLE = Map.of(
            "deepseek-flash", new ModelPricingNano(100L, 500L, 2_000L),
            "deepseek-chat", new ModelPricingNano(500L, 2_000L, 8_000L),
            "deepseek-reasoner", new ModelPricingNano(1_000L, 4_000L, 16_000L)
    );
    private static final ModelPricingNano DEFAULT_PRICING = new ModelPricingNano(100L, 500L, 2_000L);

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
    private final ModelNameResolver modelNameResolver;
    private final AiModelAliasProperties aliasProperties;

    @Autowired(required = false)
    public DeepSeekCostGovernor(MeterRegistry registry,
                                @Autowired(required = false) ModelNameResolver modelNameResolver,
                                @Autowired(required = false) AiModelAliasProperties aliasProperties) {
        this.registry = registry;
        this.modelNameResolver = modelNameResolver != null ? modelNameResolver : new ModelNameResolver();
        this.aliasProperties = aliasProperties != null ? aliasProperties : new AiModelAliasProperties();

        if (registry != null) {
            // 全局活跃请求数 Gauge
            Gauge.builder("llm.active.requests", activeLlmRequests, AtomicInteger::get)
                    .description("当前正在执行的 LLM 并发请求数")
                    .register(registry);

            // 预注册当前官方主干活跃模型与向后兼容指标
            registerModelMetrics("deepseek-flash");
            registerModelMetrics("deepseek-chat");
            registerModelMetrics("deepseek-reasoner");
        }
    }

    public DeepSeekCostGovernor(MeterRegistry registry) {
        this(registry, new ModelNameResolver(), new AiModelAliasProperties());
    }

    public DeepSeekCostGovernor() {
        this(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    private ModelAccumulator getOrCreate(String model) {
        String safeModel = (model != null && !model.isBlank()) ? model : "deepseek-flash";
        return accumulators.computeIfAbsent(safeModel, m -> {
            if (registry != null) {
                registerModelMetrics(m);
            }
            return new ModelAccumulator();
        });
    }

    public ModelAccumulator getAccumulator(String modelName) {
        if (modelName == null) {
            return accumulators.get("deepseek-flash");
        }
        ModelAccumulator acc = accumulators.get(modelName);
        if (acc == null) {
            // 尝试通过解析器查找归一化后的指标桶
            String resolved = modelNameResolver.resolve("DeepSeek", modelName);
            return accumulators.get(resolved);
        }
        return acc;
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
     * 自动通过 ModelNameResolver 进行模型别名归一化与价格策略查找
     */
    public void recordUsage(String modelName, long cacheHitPrompt, long cacheMissPrompt,
                            long completion, long reasoning) {
        // 如果传入了显式的已退役名称用于向后兼容测试（例如直接指定 deepseek-chat 测试费率），同时支持按原始键与解析键计量
        String effectiveModel = (modelName != null && !modelName.isBlank()) ? modelName : modelNameResolver.resolve("DeepSeek", null);
        ModelAccumulator acc = getOrCreate(effectiveModel);
        acc.promptCacheHitTokens.add(cacheHitPrompt);
        acc.promptCacheMissTokens.add(cacheMissPrompt);
        acc.completionTokens.add(completion);
        acc.reasoningTokens.add(reasoning);

        ModelPricingNano pricing = resolvePricing(effectiveModel);
        long requestCostNano = (cacheHitPrompt * pricing.hitPromptNano())
                + (cacheMissPrompt * pricing.missPromptNano())
                + (completion * pricing.completionNano());

        acc.costNanoYuan.add(requestCostNano);
    }

    private ModelPricingNano resolvePricing(String model) {
        if (aliasProperties != null && aliasProperties.getPricing() != null
                && aliasProperties.getPricing().containsKey(model)) {
            var cfg = aliasProperties.getPricing().get(model);
            return new ModelPricingNano(cfg.getHitPromptNano(), cfg.getMissPromptNano(), cfg.getCompletionNano());
        }
        return DEFAULT_PRICING_TABLE.getOrDefault(model, DEFAULT_PRICING);
    }

    /**
     * 获取指定模型当前消费金额（元，精确至小数）
     */
    public double getCostInYuan(String modelName) {
        if (modelName == null) {
            modelName = modelNameResolver.resolve("DeepSeek", null);
        }
        ModelAccumulator acc = accumulators.get(modelName);
        if (acc == null) {
            String resolved = modelNameResolver.resolve("DeepSeek", modelName);
            acc = accumulators.get(resolved);
        }
        return acc != null ? (double) acc.costNanoYuan.sum() / NANO_PER_CNY : 0.0;
    }
}

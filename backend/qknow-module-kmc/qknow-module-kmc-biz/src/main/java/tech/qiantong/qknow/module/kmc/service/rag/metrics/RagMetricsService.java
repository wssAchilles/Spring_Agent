package tech.qiantong.qknow.module.kmc.service.rag.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * RAG 检索流水线微观可观测性治理服务
 * 严格控制标签基数（Cardinality Control），所有 Tag 均为已知封闭枚举
 */
@Component
public class RagMetricsService {

    public enum RagStage {
        CACHE, VECTOR, KEYWORD, GRAPH, METADATA, RRF, RERANK, PARENT_CHILD
    }

    public enum StageOutcome {
        OK, ERROR, TIMEOUT, BYPASS
    }

    private final MeterRegistry registry;

    // 预热缓存：Stage -> Outcome -> Timer 避免运行期重复创建对象
    private final Map<RagStage, Map<StageOutcome, Timer>> stageTimers = new EnumMap<>(RagStage.class);

    // 关键门控计数器
    private final Counter cacheHitExactCounter;
    private final Counter cacheHitSemanticCounter;
    private final Counter cacheMissCounter;
    private final Counter cacheBypassCounter;

    private final Counter rerankGateInvokedCounter;
    private final Counter rerankGateBypassCounter;

    private final Counter zeroHitSimilarityCounter;
    private final Counter zeroHitPermissionCounter;

    public RagMetricsService(MeterRegistry registry) {
        this.registry = registry;

        // 1. 初始化预注册阶段 Timer（8 Stages * 4 Outcomes = 32 Timers）
        for (RagStage stage : RagStage.values()) {
            Map<StageOutcome, Timer> outcomeMap = new EnumMap<>(StageOutcome.class);
            for (StageOutcome outcome : StageOutcome.values()) {
                Timer timer = Timer.builder("rag.stage.duration")
                        .description("RAG 检索流水线细粒度阶段执行耗时")
                        .tag("stage", stage.name().toLowerCase())
                        .tag("outcome", outcome.name().toLowerCase())
                        .register(registry);
                outcomeMap.put(outcome, timer);
            }
            stageTimers.put(stage, outcomeMap);
        }

        // 2. 初始化语义缓存门控计数器
        this.cacheHitExactCounter = Counter.builder("rag.cache.lookup.total")
                .description("语义缓存查找精确命中计数")
                .tag("status", "hit_exact").register(registry);
        this.cacheHitSemanticCounter = Counter.builder("rag.cache.lookup.total")
                .description("语义缓存查找语义相似命中计数")
                .tag("status", "hit_semantic").register(registry);
        this.cacheMissCounter = Counter.builder("rag.cache.lookup.total")
                .description("语义缓存未命中计数")
                .tag("status", "miss").register(registry);
        this.cacheBypassCounter = Counter.builder("rag.cache.lookup.total")
                .description("语义缓存旁路跳过计数")
                .tag("status", "bypass").register(registry);

        // 3. 初始化重排动态门控计数器
        this.rerankGateInvokedCounter = Counter.builder("rag.rerank.gate.total")
                .description("重排门控实际触发模型推理计数")
                .tag("action", "invoked").register(registry);
        this.rerankGateBypassCounter = Counter.builder("rag.rerank.gate.total")
                .description("重排门控自适应判定旁路跳过计数")
                .tag("action", "bypassed").register(registry);

        // 4. 初始化零命中/冷启动计数器
        this.zeroHitSimilarityCounter = Counter.builder("rag.zero.hit.total")
                .description("检索全无相似度命中计数")
                .tag("reason", "zero_similarity").register(registry);
        this.zeroHitPermissionCounter = Counter.builder("rag.zero.hit.total")
                .description("无知识库权限访问计数")
                .tag("reason", "permission_denied").register(registry);
    }

    /**
     * 极低开销记录阶段执行耗时
     */
    public void recordStage(RagStage stage, StageOutcome outcome, long durationNanos) {
        if (registry == null) {
            return;
        }
        Map<StageOutcome, Timer> outcomeMap = stageTimers.get(stage);
        if (outcomeMap != null) {
            Timer timer = outcomeMap.get(outcome);
            if (timer != null) {
                timer.record(durationNanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    public void recordCacheHitExact() {
        if (cacheHitExactCounter != null) cacheHitExactCounter.increment();
    }

    public void recordCacheHitSemantic() {
        if (cacheHitSemanticCounter != null) cacheHitSemanticCounter.increment();
    }

    public void recordCacheMiss() {
        if (cacheMissCounter != null) cacheMissCounter.increment();
    }

    public void recordCacheBypass() {
        if (cacheBypassCounter != null) cacheBypassCounter.increment();
    }

    public void recordRerankGate(boolean bypassed) {
        if (bypassed) {
            if (rerankGateBypassCounter != null) rerankGateBypassCounter.increment();
        } else {
            if (rerankGateInvokedCounter != null) rerankGateInvokedCounter.increment();
        }
    }

    public void recordZeroHit(boolean isPermissionDenied) {
        if (isPermissionDenied) {
            if (zeroHitPermissionCounter != null) zeroHitPermissionCounter.increment();
        } else {
            if (zeroHitSimilarityCounter != null) zeroHitSimilarityCounter.increment();
        }
    }
}

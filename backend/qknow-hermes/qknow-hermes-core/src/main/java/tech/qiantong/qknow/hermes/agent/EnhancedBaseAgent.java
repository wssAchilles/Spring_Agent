package tech.qiantong.qknow.hermes.agent;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.agent.bidding.TaskCfp;
import tech.qiantong.qknow.hermes.agent.bidding.WorkerBid;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 具备能力画像、负载感知与动态竞标能力的智能体抽象基类
 */
@Slf4j
public abstract class EnhancedBaseAgent extends BaseAgent {

    private final Set<String> capabilities;
    private final int maxConcurrentTasks;
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    // 历史可靠性评分（默认 0.85）
    private volatile double historicalReliability = 0.85;

    protected EnhancedBaseAgent(String name, String description, Set<String> capabilities, int maxConcurrentTasks) {
        super(name, description);
        this.capabilities = capabilities != null ? new HashSet<>(capabilities) : new HashSet<>();
        this.maxConcurrentTasks = maxConcurrentTasks > 0 ? maxConcurrentTasks : 4;
    }

    protected EnhancedBaseAgent(String name, String description) {
        this(name, description, Set.of("GENERAL"), 4);
    }

    public Set<String> getCapabilities() {
        return Collections.unmodifiableSet(capabilities);
    }

    public int getActiveTaskCount() {
        return activeTaskCount.get();
    }

    public double getCurrentLoadRate() {
        return (double) activeTaskCount.get() / maxConcurrentTasks;
    }

    /**
     * 评估并提交投标单
     */
    public Optional<WorkerBid> evaluateAndBid(TaskCfp cfp) {
        if (activeTaskCount.get() >= maxConcurrentTasks) {
            log.debug("[Agent-{}] 算力已饱和 (活跃任务: {}/{})，放弃竞标", getName(), activeTaskCount.get(), maxConcurrentTasks);
            return Optional.empty();
        }

        double relevance = calculateCapabilityRelevance(cfp.requiredCapability());
        double loadRate = getCurrentLoadRate();
        double reliability = historicalReliability;

        // 综合加权评分公式：0.5 * 匹配度 + 0.3 * (1 - 负载率) + 0.2 * 历史置信度
        double score = 0.5 * relevance + 0.3 * (1.0 - loadRate) + 0.2 * reliability;
        score = Math.max(0.0, Math.min(1.0, score));

        return Optional.of(new WorkerBid(getName(), score, relevance, loadRate, reliability));
    }

    protected double calculateCapabilityRelevance(String requiredCapability) {
        if (requiredCapability == null || requiredCapability.isBlank() || "GENERAL".equalsIgnoreCase(requiredCapability)) {
            return 0.70;
        }
        for (String cap : capabilities) {
            if (cap.equalsIgnoreCase(requiredCapability)) {
                return 0.95;
            }
        }
        return 0.40;
    }

    /**
     * 执行具体子任务（带负载计数跟踪）
     */
    public String executeTask(String objective, String contextPayload) {
        activeTaskCount.incrementAndGet();
        try {
            Map<String, Object> ctx = new HashMap<>();
            if (contextPayload != null) {
                ctx.put("contextPayload", contextPayload);
            }
            return chat(objective, ctx);
        } finally {
            activeTaskCount.decrementAndGet();
        }
    }
}

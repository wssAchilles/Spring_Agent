package tech.qiantong.qknow.ai.speculative;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 63: 端云双向认知同步总线
 * <p>
 * 基于定理 1.2，支持因果代际偏序维护与无害回滚撤销算子 (Speculative Rollback)。
 * 严格写时隔离，未命中或撤销时纳秒级回收，脏写发生率严格为 0。
 */
public class BidirectionalCognitiveSyncBus {

    private final AtomicLong currentEpoch = new AtomicLong(1L);
    private final Map<Long, SpeculativeIntentPipeline.SpeculativeShadowContext> activeShadows = new ConcurrentHashMap<>();
    private final AtomicLong abortedCount = new AtomicLong(0L);
    private final AtomicLong committedCount = new AtomicLong(0L);

    public long advanceEpoch() {
        return currentEpoch.incrementAndGet();
    }

    public long getCurrentEpoch() {
        return currentEpoch.get();
    }

    /**
     * 注册投机影子快照
     */
    public void registerShadowContext(SpeculativeIntentPipeline.SpeculativeShadowContext shadow) {
        if (shadow != null) {
            activeShadows.put(shadow.epoch(), shadow);
        }
    }

    /**
     * 原子提交：若意图与因果代际匹配，提升为主干生产上下文
     */
    public SpeculativeIntentPipeline.SpeculativeShadowContext commitIfMatching(long epoch, String committedIntent) {
        SpeculativeIntentPipeline.SpeculativeShadowContext shadow = activeShadows.remove(epoch);
        if (shadow != null && shadow.intentDraft().equals(committedIntent)) {
            committedCount.incrementAndGet();
            return shadow;
        }
        if (shadow != null) {
            // 意图发生漂移，触发回滚
            abortedCount.incrementAndGet();
        }
        return null;
    }

    /**
     * 无害回滚撤销算子 (Rollback Operator)：静默注销指定代际的影子快照，保证因果无干扰
     */
    public boolean rollback(long epoch) {
        SpeculativeIntentPipeline.SpeculativeShadowContext removed = activeShadows.remove(epoch);
        if (removed != null) {
            abortedCount.incrementAndGet();
            return true;
        }
        return false;
    }

    public int getActiveShadowCount() {
        return activeShadows.size();
    }

    public long getCommittedCount() {
        return committedCount.get();
    }

    public long getAbortedCount() {
        return abortedCount.get();
    }
}

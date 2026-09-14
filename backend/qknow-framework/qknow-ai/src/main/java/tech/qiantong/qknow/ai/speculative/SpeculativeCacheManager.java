package tech.qiantong.qknow.ai.speculative;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 投机预取上下文环形内存暂存器 (SpeculativeCacheManager)
 *
 * 核心特性：
 * 1. CAS 无锁原子状态翻转与会话级环形暂存；
 * 2. 严格的 3000ms 默认 TTL 硬超时驱逐机制；
 * 3. 严格遵循 Lemma 3.1（无干扰注销引理），用户改写输入时执行静默清空，绝对杜绝脏上下文泄漏至全局。
 *
 * @author qknow
 */
@Slf4j
@Component
public class SpeculativeCacheManager {

    public enum SpeculativeState {
        SPECULATING,    // 投机正在异步执行中
        READY,          // 投机上下文已准备就绪
        HIT,            // 已被正式请求命中消费
        MISS,           // 预测失效已静默丢弃
        EXPIRED         // 超时已作废
    }

    @Getter
    @Builder
    public static class SpeculativeEntry {
        private final String sessionId;
        private final String prefixQuery;
        private final List<String> contexts;
        private final float[] queryVector;
        private final long createTimeMs;
        private final long expireTimeMs;
        private final AtomicReference<SpeculativeState> state;

        public SpeculativeState getState() {
            return state.get();
        }

        public boolean compareAndSetState(SpeculativeState expect, SpeculativeState update) {
            return state.compareAndSet(expect, update);
        }
    }

    private final Map<String, SpeculativeEntry> sessionCache = new ConcurrentHashMap<>();

    /**
     * 注册投机预检索作业（初始状态 SPECULATING）
     */
    public SpeculativeEntry registerSpeculation(String sessionId, String prefixQuery, long ttlMs) {
        long now = System.currentTimeMillis();
        SpeculativeEntry entry = SpeculativeEntry.builder()
                .sessionId(sessionId)
                .prefixQuery(prefixQuery)
                .contexts(Collections.emptyList())
                .queryVector(null)
                .createTimeMs(now)
                .expireTimeMs(now + ttlMs)
                .state(new AtomicReference<>(SpeculativeState.SPECULATING))
                .build();
        sessionCache.put(sessionId, entry);
        return entry;
    }

    /**
     * 完成投机上下文填充（转为 READY）
     */
    public boolean completeSpeculation(String sessionId, String prefixQuery, List<String> contexts, float[] queryVector, long ttlMs) {
        long now = System.currentTimeMillis();
        SpeculativeEntry entry = SpeculativeEntry.builder()
                .sessionId(sessionId)
                .prefixQuery(prefixQuery)
                .contexts(contexts != null ? List.copyOf(contexts) : Collections.emptyList())
                .queryVector(queryVector)
                .createTimeMs(now)
                .expireTimeMs(now + ttlMs)
                .state(new AtomicReference<>(SpeculativeState.READY))
                .build();
        sessionCache.put(sessionId, entry);
        return true;
    }

    /**
     * 尝试匹配最终 Query，若匹配成功以 CAS 翻转为 HIT 并返回上下文
     */
    public Optional<List<String>> matchAndConsume(String sessionId, String finalQuery) {
        SpeculativeEntry entry = sessionCache.get(sessionId);
        if (entry == null) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        if (now > entry.getExpireTimeMs()) {
            entry.compareAndSetState(SpeculativeState.READY, SpeculativeState.EXPIRED);
            sessionCache.remove(sessionId);
            return Optional.empty();
        }

        // 前缀匹配或完全相等判定
        String p = entry.getPrefixQuery().trim();
        String q = finalQuery.trim();
        boolean matched = q.equalsIgnoreCase(p) || q.startsWith(p) || (q.length() >= p.length() && q.contains(p));

        if (matched) {
            if (entry.compareAndSetState(SpeculativeState.READY, SpeculativeState.HIT) ||
                entry.compareAndSetState(SpeculativeState.SPECULATING, SpeculativeState.HIT)) {
                log.debug("[SpeculativeCache] 命中投机预取上下文: session={}, prefix='{}', final='{}'", sessionId, p, q);
                sessionCache.remove(sessionId); // 一次性消费，防脏读
                return Optional.of(entry.getContexts());
            }
        }

        // 匹配失败转为 MISS
        entry.compareAndSetState(SpeculativeState.READY, SpeculativeState.MISS);
        sessionCache.remove(sessionId);
        return Optional.empty();
    }

    /**
     * 静默注销作废（用户输入改写或退格时调用）
     */
    public void invalidate(String sessionId) {
        SpeculativeEntry entry = sessionCache.remove(sessionId);
        if (entry != null) {
            entry.compareAndSetState(SpeculativeState.SPECULATING, SpeculativeState.MISS);
            entry.compareAndSetState(SpeculativeState.READY, SpeculativeState.MISS);
        }
    }

    /**
     * 定时清除过期项
     */
    public int cleanExpired() {
        long now = System.currentTimeMillis();
        int cleaned = 0;
        for (Iterator<Map.Entry<String, SpeculativeEntry>> it = sessionCache.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, SpeculativeEntry> e = it.next();
            if (now > e.getValue().getExpireTimeMs()) {
                e.getValue().compareAndSetState(SpeculativeState.READY, SpeculativeState.EXPIRED);
                it.remove();
                cleaned++;
            }
        }
        return cleaned;
    }

    public int size() {
        return sessionCache.size();
    }

    public SpeculativeEntry getEntry(String sessionId) {
        return sessionCache.get(sessionId);
    }
}

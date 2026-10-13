package tech.qiantong.qknow.hermes.flow.stategraph.reducer;

import java.util.*;

/**
 * 工业级内置状态规约器集合 (StandardStateReducers)
 * 提供 6 大开箱即用、代数性质严格的不可变规约器。
 */
public final class StandardStateReducers {

    private StandardStateReducers() {}

    /**
     * 1. 最后写入优先 (LWW, Last-Write-Wins)
     */
    public static <T> StateChannelReducer<T> overwrite() {
        return (current, update) -> update;
    }

    /**
     * 2. 列表追加规约器 (满足结合律)
     */
    public static <E> StateChannelReducer<List<E>> appendList() {
        return StateChannelReducer.appendList();
    }

    /**
     * 3. 字典合并规约器 (满足结合律)
     */
    public static <K, V> StateChannelReducer<Map<K, V>> mergeMap() {
        return StateChannelReducer.mergeMap();
    }

    /**
     * 4. 64位长整型原子累加规约器 (阿贝尔交换半群)
     */
    public static StateChannelReducer<Long> arithmeticSumLong() {
        return (current, update) -> {
            long c = current != null ? current : 0L;
            long u = update != null ? update : 0L;
            return c + u;
        };
    }

    /**
     * 5. 双精度浮点原子累加规约器 (阿贝尔交换半群)
     */
    public static StateChannelReducer<Double> arithmeticSumDouble() {
        return (current, update) -> {
            double c = current != null ? current : 0.0;
            double u = update != null ? update : 0.0;
            return c + u;
        };
    }

    /**
     * 浮点累加别名
     */
    public static StateChannelReducer<Double> sumDouble() {
        return arithmeticSumDouble();
    }

    /**
     * 6. 排他冲突检测规约器 (Conflict-Reject 写屏障)
     * 若同一超步内存在多个非空写入冲突，抛出并发冲突异常阻断脏写
     */
    public static <T> StateChannelReducer<T> conflictReject() {
        return (current, update) -> {
            if (current != null && update != null && !Objects.equals(current, update)) {
                throw new IllegalStateException("【状态规约冲突】检测到排他通道并发多写冲突: 当前值=" + current + ", 传入值=" + update);
            }
            return update != null ? update : current;
        };
    }

    /**
     * 排他写屏障别名
     */
    public static <T> StateChannelReducer<T> exclusiveWriteBarrier() {
        return conflictReject();
    }
}

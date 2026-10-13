package tech.qiantong.qknow.hermes.flow.stategraph.reducer;

import java.util.*;

/**
 * 状态通道规约器函数式接口 (StateChannelReducer)
 * 定义同一通道上既有状态值与新输入增量值的代数合并策略。
 * 遵循半群结合律：(S ⊕ Δ1) ⊕ Δ2 = S ⊕ (Δ1 ⊕ Δ2)，确保并发无序执行下的强汇聚性。
 *
 * @param <T> 通道数据类型
 */
@FunctionalInterface
public interface StateChannelReducer<T> {

    /**
     * 合并旧值与新增量值
     *
     * @param currentValue 当前通道已存数据（首轮可为 null）
     * @param update       本超步产生的更新增量
     * @return 规约后的新数据
     */
    T reduce(T currentValue, T update);

    /**
     * 默认覆写规约器（Last-Write-Wins 语义）
     */
    static <V> StateChannelReducer<V> overwrite() {
        return (prev, update) -> update;
    }

    /**
     * 列表安全追加规约器（保序累加，杜绝丢失）
     */
    @SuppressWarnings("unchecked")
    static <E> StateChannelReducer<List<E>> appendList() {
        return (prev, update) -> {
            List<E> result = new ArrayList<>();
            if (prev != null) {
                result.addAll(prev);
            }
            if (update != null) {
                result.addAll(update);
            }
            return Collections.unmodifiableList(result);
        };
    }

    /**
     * Map 键值合并规约器（支持嵌套字典增量合流）
     */
    @SuppressWarnings("unchecked")
    static <K, V> StateChannelReducer<Map<K, V>> mergeMap() {
        return (prev, update) -> {
            Map<K, V> result = new LinkedHashMap<>();
            if (prev != null) {
                result.putAll(prev);
            }
            if (update != null) {
                result.putAll(update);
            }
            return Collections.unmodifiableMap(result);
        };
    }
}

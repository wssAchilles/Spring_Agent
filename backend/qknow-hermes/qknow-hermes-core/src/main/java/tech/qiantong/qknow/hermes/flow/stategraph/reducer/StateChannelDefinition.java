package tech.qiantong.qknow.hermes.flow.stategraph.reducer;

import java.io.Serializable;
import java.util.Objects;

/**
 * 状态通道元数据定义 Record (StateChannelDefinition)
 * 声明通道类型、默认值以及对应的 StateChannelReducer 规约器。
 *
 * @param <T> 通道类型
 */
public record StateChannelDefinition<T>(
        String channelKey,
        Class<T> valueType,
        StateChannelReducer<T> reducer,
        T defaultValue,
        boolean isImmutable
) implements Serializable {

    public StateChannelDefinition {
        Objects.requireNonNull(channelKey, "channelKey 不能为空");
        Objects.requireNonNull(reducer, "reducer 不能为空");
    }

    public static <T> StateChannelDefinition<T> of(String key, Class<T> type, StateChannelReducer<T> reducer, T defaultValue) {
        return new StateChannelDefinition<>(key, type, reducer, defaultValue, false);
    }

    public static <T> StateChannelDefinition<T> of(String key, Class<T> type, StateChannelReducer<T> reducer) {
        return new StateChannelDefinition<>(key, type, reducer, null, false);
    }

    @SuppressWarnings("unchecked")
    public static <T> StateChannelDefinition<T> of(String key, StateChannelReducer<T> reducer) {
        return new StateChannelDefinition<>(key, (Class<T>) Object.class, reducer, null, false);
    }

    @SuppressWarnings("unchecked")
    public static <T> StateChannelDefinition<T> of(String key, StateChannelReducer<T> reducer, T defaultValue) {
        Class<T> clazz = defaultValue != null ? (Class<T>) defaultValue.getClass() : (Class<T>) Object.class;
        return new StateChannelDefinition<>(key, clazz, reducer, defaultValue, false);
    }
}

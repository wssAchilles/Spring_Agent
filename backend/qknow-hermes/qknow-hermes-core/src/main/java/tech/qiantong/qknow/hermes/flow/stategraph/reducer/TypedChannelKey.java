package tech.qiantong.qknow.hermes.flow.stategraph.reducer;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;

/**
 * 强类型状态图通道键（Phase 9.2 PA-TSG，消除弱类型 {@code Map<String, Object>} 隐性转换风险）。
 *
 * @param <T> 通道值类型
 */
public record TypedChannelKey<T>(
        String channelName,
        Class<T> valueType
) implements Serializable {

    public TypedChannelKey {
        if (channelName == null || channelName.isBlank()) {
            throw new IllegalArgumentException("channelName 不能为空");
        }
        Objects.requireNonNull(valueType, "valueType 不能为空");
    }

    public static <T> TypedChannelKey<T> of(String channelName, Class<T> valueType) {
        return new TypedChannelKey<>(channelName, valueType);
    }

    /**
     * 从弱类型底层状态映射中执行带类型校验的安全读取。
     */
    public T readFrom(Map<String, Object> state) {
        if (state == null) {
            return null;
        }
        Object raw = state.get(channelName);
        if (raw == null) {
            return null;
        }
        if (!valueType.isInstance(raw)) {
            throw new IllegalStateException("通道 [" + channelName + "] 类型不匹配: 期望 "
                    + valueType.getName() + ", 实际 " + raw.getClass().getName());
        }
        return valueType.cast(raw);
    }

    /**
     * 构造带运行期类型检查的状态增量键值对。
     */
    public Map.Entry<String, Object> writeEntry(T value) {
        if (value != null && !valueType.isInstance(value)) {
            throw new IllegalArgumentException("写入通道 [" + channelName + "] 的值类型非法: "
                    + value.getClass().getName());
        }
        return new AbstractMap.SimpleImmutableEntry<>(channelName, value);
    }
}

package tech.qiantong.qknow.hermes.flow.stategraph.reducer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全图状态 Schema 管理器 (StateGraphSchema)
 * 维护状态通道与规约算子的映射，为超步屏障提供强类型规范支撑。
 */
public class StateGraphSchema {

    private final Map<String, StateChannelDefinition<?>> channelDefinitions = new ConcurrentHashMap<>();

    public StateGraphSchema() {}

    public static StateGraphSchema defaultSchema() {
        StateGraphSchema schema = new StateGraphSchema();
        schema.registerChannel(StateChannelDefinition.of("messages", StandardStateReducers.appendList(), List.of()));
        schema.registerChannel(StateChannelDefinition.of("logs", StandardStateReducers.appendList(), List.of()));
        schema.registerChannel(StateChannelDefinition.of("metrics", StandardStateReducers.mergeMap(), Map.of()));
        schema.registerChannel(StateChannelDefinition.of("totalCost", StandardStateReducers.arithmeticSumLong(), 0L));
        return schema;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final StateGraphSchema schema = new StateGraphSchema();

        public <T> Builder channel(StateChannelDefinition<T> definition) {
            schema.registerChannel(definition);
            return this;
        }

        public StateGraphSchema build() {
            return schema;
        }
    }

    public <T> StateGraphSchema registerChannel(StateChannelDefinition<T> definition) {
        Objects.requireNonNull(definition, "definition 不能为空");
        channelDefinitions.put(definition.channelKey(), definition);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> StateChannelDefinition<T> getChannel(String key) {
        return (StateChannelDefinition<T>) channelDefinitions.get(key);
    }

    public boolean hasChannel(String key) {
        return channelDefinitions.containsKey(key);
    }

    public Map<String, StateChannelDefinition<?>> getAllChannels() {
        return Collections.unmodifiableMap(channelDefinitions);
    }
}

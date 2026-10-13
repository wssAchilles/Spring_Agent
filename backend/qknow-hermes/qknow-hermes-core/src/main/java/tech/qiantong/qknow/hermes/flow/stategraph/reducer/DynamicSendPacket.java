package tech.qiantong.qknow.hermes.flow.stategraph.reducer;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * 动态分支路由与数据投递包 (DynamicSendPacket)
 * 纯 Java 21 不可变 Record，支持节点在运行时动态扇出（Dynamic Fan-out）派发子任务。
 */
public record DynamicSendPacket(
        String targetNodeId,
        Map<String, Object> nodePayload,
        long sendTimestampEpochMs
) implements Serializable {

    public DynamicSendPacket {
        Objects.requireNonNull(targetNodeId, "targetNodeId 不能为空");
        nodePayload = Map.copyOf(nodePayload != null ? nodePayload : Map.of());
    }

    public static DynamicSendPacket of(String targetNode, Map<String, Object> payload) {
        return new DynamicSendPacket(targetNode, payload, System.currentTimeMillis());
    }
}

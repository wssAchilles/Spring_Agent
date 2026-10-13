package tech.qiantong.qknow.hermes.flow.stategraph.reducer;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 节点执行产物封包 (NodeExecutionEnvelope)
 * 纯 Java 21 Record，同时承载节点身份、通道增量更新、动态路由指令与显式增量序列。
 */
public record NodeExecutionEnvelope(
        String nodeId,
        Map<String, Object> stateUpdates,
        List<DynamicSendPacket> dynamicSends,
        List<BranchStateDelta> explicitDeltas,
        long timestamp
) implements Serializable {

    public NodeExecutionEnvelope {
        nodeId = nodeId != null ? nodeId : "anonymousNode";
        stateUpdates = Map.copyOf(stateUpdates != null ? stateUpdates : Map.of());
        dynamicSends = List.copyOf(dynamicSends != null ? dynamicSends : List.of());
        explicitDeltas = List.copyOf(explicitDeltas != null ? explicitDeltas : List.of());
    }

    public static NodeExecutionEnvelope of(String nodeId, Map<String, Object> updates) {
        return new NodeExecutionEnvelope(nodeId, updates, List.of(), List.of(), System.currentTimeMillis());
    }

    public static NodeExecutionEnvelope ofUpdates(Map<String, Object> updates) {
        return new NodeExecutionEnvelope("node", updates, List.of(), List.of(), System.currentTimeMillis());
    }

    public static NodeExecutionEnvelope of(Map<String, Object> updates, List<DynamicSendPacket> sends) {
        return new NodeExecutionEnvelope("node", updates, sends, List.of(), System.currentTimeMillis());
    }

    public static NodeExecutionEnvelope of(String nodeId, Map<String, Object> updates, List<DynamicSendPacket> sends) {
        return new NodeExecutionEnvelope(nodeId, updates, sends, List.of(), System.currentTimeMillis());
    }
}

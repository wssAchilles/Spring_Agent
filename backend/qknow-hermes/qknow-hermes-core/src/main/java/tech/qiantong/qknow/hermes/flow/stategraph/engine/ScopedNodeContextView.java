package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import tech.qiantong.qknow.hermes.flow.stategraph.reducer.TypedChannelKey;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 并行子图节点私有隔离上下文视角（Phase 9.2 PA-TSG 定理 1 核心机制）。
 * <p>
 * 对标 ADK {@code isolation_scope} + {@code DYNAMIC_INSTRUCTION_ROUTING} 与 LangGraph {@code input_schema} 隔离：
 * 每个虚拟线程并发分支仅可读取授权的通道快照与自身 {@code DynamicSendPacket.nodePayload()}，
 * 与兄弟分支的原始检索脏文本物理隔离，且共享不可变 Zone-0 64-token 锚点前缀防缓存抖动。
 * </p>
 */
public record ScopedNodeContextView(
        String executionId,
        String branchId,
        String nodeId,
        String dispatchPathKey,
        int superstep,
        String zone0AnchorPrefix,
        String zone0AnchorSha256,
        Map<String, Object> readableChannelsSnapshot,
        Map<String, Object> sendPayload,
        List<String> localFailureLedger
) implements Serializable {

    public ScopedNodeContextView {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        branchId = (branchId == null || branchId.isBlank()) ? "main" : branchId;
        dispatchPathKey = (dispatchPathKey == null || dispatchPathKey.isBlank()) ? nodeId : dispatchPathKey;
        zone0AnchorPrefix = zone0AnchorPrefix != null ? zone0AnchorPrefix : "";
        zone0AnchorSha256 = (zone0AnchorSha256 != null && !zone0AnchorSha256.isBlank())
                ? zone0AnchorSha256
                : computeSha256(zone0AnchorPrefix);
        readableChannelsSnapshot = Map.copyOf(readableChannelsSnapshot != null ? readableChannelsSnapshot : Map.of());
        sendPayload = Map.copyOf(sendPayload != null ? sendPayload : Map.of());
        localFailureLedger = List.copyOf(localFailureLedger != null ? localFailureLedger : List.of());
    }

    /**
     * 类型安全读取授权通道值。
     */
    public <T> T get(TypedChannelKey<T> key) {
        Objects.requireNonNull(key, "key 不能为空");
        return key.readFrom(readableChannelsSnapshot);
    }

    /**
     * 校验 Zone-0 共享前缀非空且与锚点摘要常量时间匹配（用于 DeepSeek Prefix Cache 零抖动守卫）。
     */
    public boolean isZone0AnchorPreserved(String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            return false;
        }
        String actual = computeSha256(zone0AnchorPrefix);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expectedSha256.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((text != null ? text : "").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算 Zone-0 SHA-256 失败", e);
        }
    }
}

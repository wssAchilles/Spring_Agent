package tech.qiantong.qknow.hermes.flow.stategraph.journal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 嵌入式预写步骤日志凭单（DBOS {@code @Step} 幂等语义 + LangGraph Checkpoint v4 {@code DeltaChannel}）。
 * <p>
 * 纯 Java 21 不可变 Record，仅持久化当前节点产生的增量通道变更（{@code channelDeltas}）与已消费通道版本向量（{@code versionsSeen}），
 * 消除每超步全量深拷贝放大，并通过 {@link MessageDigest#isEqual(byte[], byte[])} 提供常量时间防篡改验真。
 * </p>
 */
public record WriteAheadStepJournalEntry(
        String executionId,
        String branchId,
        int superstep,
        String nodeId,
        String idempotencyKey,
        long fencingToken,
        Map<String, Object> channelDeltas,
        Map<String, Long> versionsSeen,
        StepLifecycleStatus status,
        long createdAtEpochMs,
        byte[] macSignature
) {
    public WriteAheadStepJournalEntry {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Objects.requireNonNull(branchId, "branchId 不能为空");
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(macSignature, "macSignature 不能为空");
        channelDeltas = Map.copyOf(channelDeltas != null ? channelDeltas : Map.of());
        versionsSeen = Map.copyOf(versionsSeen != null ? versionsSeen : Map.of());
        macSignature = Arrays.copyOf(macSignature, macSignature.length);
    }

    public static WriteAheadStepJournalEntry createSigned(
            String executionId,
            String branchId,
            int superstep,
            String nodeId,
            String idempotencyKey,
            long fencingToken,
            Map<String, Object> channelDeltas,
            Map<String, Long> versionsSeen,
            StepLifecycleStatus status,
            String secretKey
    ) {
        long now = System.currentTimeMillis();
        Map<String, Object> safeDeltas = Map.copyOf(channelDeltas != null ? channelDeltas : Map.of());
        Map<String, Long> safeVersions = Map.copyOf(versionsSeen != null ? versionsSeen : Map.of());
        byte[] mac = computeHmac(
                executionId, branchId, superstep, nodeId, idempotencyKey,
                fencingToken, safeDeltas, safeVersions, status, now, secretKey
        );
        return new WriteAheadStepJournalEntry(
                executionId, branchId, superstep, nodeId, idempotencyKey,
                fencingToken, safeDeltas, safeVersions, status, now, mac
        );
    }

    /**
     * 常量时间校验步骤日志完整性签名，杜绝时序侧信道风险。
     */
    public boolean verifyMac(String secretKey) {
        byte[] expected = computeHmac(
                executionId, branchId, superstep, nodeId, idempotencyKey,
                fencingToken, channelDeltas, versionsSeen, status, createdAtEpochMs, secretKey
        );
        return MessageDigest.isEqual(expected, macSignature);
    }

    @Override
    public byte[] macSignature() {
        return Arrays.copyOf(macSignature, macSignature.length);
    }

    private static byte[] computeHmac(
            String executionId,
            String branchId,
            int superstep,
            String nodeId,
            String idempotencyKey,
            long fencingToken,
            Map<String, Object> channelDeltas,
            Map<String, Long> versionsSeen,
            StepLifecycleStatus status,
            long createdAtEpochMs,
            String secretKey
    ) {
        String effectiveSecret = (secretKey == null || secretKey.isBlank())
                ? "hermes-default-step-journal-secret"
                : secretKey;
        StringBuilder sb = new StringBuilder(256);
        sb.append(executionId).append('|')
                .append(branchId).append('|')
                .append(superstep).append('|')
                .append(nodeId).append('|')
                .append(idempotencyKey).append('|')
                .append(fencingToken).append('|')
                .append(status.name()).append('|')
                .append(createdAtEpochMs).append("|{");
        new TreeMap<>(channelDeltas).forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        sb.append("}|[");
        new TreeMap<>(versionsSeen).forEach((k, v) -> sb.append(k).append(':').append(v).append(','));
        sb.append(']');

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(effectiveSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("步骤日志 HmacSHA256 计算失败", e);
        }
    }
}

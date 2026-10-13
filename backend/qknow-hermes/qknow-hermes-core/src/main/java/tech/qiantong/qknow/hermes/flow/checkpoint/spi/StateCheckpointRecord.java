package tech.qiantong.qknow.hermes.flow.checkpoint.spi;

import java.io.Serializable;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * 不可变状态检查点凭单 Record
 * 封装运行时快照、单调自增 Fencing Token 与密码学完整性摘要
 */
public record StateCheckpointRecord(
        String runtimeId,
        String flowId,
        int groupIndex,
        String completedResultsJson,
        String variablesJson,
        String status,
        String compensationLog,
        String leaseOwnerId,
        long leaseExpireAtMs,
        long fencingToken,
        int version,
        long createdAtMs,
        long updatedAtMs,
        byte[] integrityHmac
) implements Serializable {

    public StateCheckpointRecord {
        Objects.requireNonNull(runtimeId, "runtimeId 不能为空");
        Objects.requireNonNull(flowId, "flowId 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        if (integrityHmac != null) {
            integrityHmac = integrityHmac.clone();
        }
    }

    @Override
    public byte[] integrityHmac() {
        return integrityHmac == null ? null : integrityHmac.clone();
    }

    /**
     * 常量时间防侧信道比对 HMAC 摘要
     */
    public boolean verifyIntegrity(byte[] expectedHmac) {
        if (integrityHmac == null || expectedHmac == null) {
            return false;
        }
        return MessageDigest.isEqual(integrityHmac, expectedHmac);
    }
}

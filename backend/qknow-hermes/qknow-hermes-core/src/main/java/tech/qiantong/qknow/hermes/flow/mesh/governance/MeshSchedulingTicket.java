package tech.qiantong.qknow.hermes.flow.mesh.governance;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 网格调度执行凭单 (不可变 Record)
 * 封装槽位授权、租约超时与密码学防侧信道签名
 */
public record MeshSchedulingTicket(
        String ticketId,
        String runtimeId,
        String nodeId,
        long issuedTimestampMs,
        int grantedSlot,
        long leaseExpireTimestampMs,
        byte[] cryptographicSignature
) implements Serializable {

    public MeshSchedulingTicket {
        Objects.requireNonNull(ticketId, "ticketId 不能为空");
        Objects.requireNonNull(runtimeId, "runtimeId 不能为空");
        Objects.requireNonNull(nodeId, "nodeId 不能为空");
        if (cryptographicSignature != null) {
            cryptographicSignature = cryptographicSignature.clone();
        }
    }

    @Override
    public byte[] cryptographicSignature() {
        return cryptographicSignature == null ? null : cryptographicSignature.clone();
    }

    /**
     * 判断凭单租约是否已超时
     */
    public boolean isExpired(long nowMs) {
        return nowMs > leaseExpireTimestampMs;
    }

    /**
     * 常量时间防侧信道比对凭单签名
     */
    public boolean verifySignature(byte[] secret) {
        if (cryptographicSignature == null || secret == null) {
            return false;
        }
        byte[] expected = computeHmac(secret);
        return MessageDigest.isEqual(cryptographicSignature, expected);
    }

    /**
     * 计算凭单 HMAC-SHA256 签名摘要
     */
    public byte[] computeHmac(byte[] secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String payload = ticketId + ":" + runtimeId + ":" + nodeId + ":" + issuedTimestampMs + ":" + grantedSlot;
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("凭单验签散列初始化失败", e);
        }
    }
}

package tech.qiantong.qknow.hermes.a2a.envelope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * 标准不可变 A2A (Agent-to-Agent) 通信协议信封（Java 21 Record）
 * 遵循 Phase 109 规范：内嵌 W3C Trace、Fencing 时效租约、HMAC-SHA256 签名与防环跳数
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record A2AMessageEnvelope(
        String messageId,
        String traceId,
        String spanId,
        String parentSpanId,
        String senderAgentId,
        String recipientAgentId,
        A2AMessageType messageType,
        String securityLeaseToken,
        Map<String, Object> payload,
        long timestamp,
        long leaseExpiryTimestamp,
        int hopCount,
        String signature
) {
    public static final int MAX_HOP_COUNT = 8;

    /**
     * 兼容性构造器：保持原有 10 参数构造器向下兼容
     */
    public A2AMessageEnvelope(
            String messageId,
            String traceId,
            String spanId,
            String senderAgentId,
            String recipientAgentId,
            A2AMessageType messageType,
            String securityLeaseToken,
            Map<String, Object> payload,
            long timestamp,
            long leaseExpiryTimestamp
    ) {
        this(messageId, traceId, spanId, null, senderAgentId, recipientAgentId,
                messageType, securityLeaseToken, payload, timestamp, leaseExpiryTimestamp, 0, null);
    }

    /**
     * 工厂方法创建新信封
     */
    public static A2AMessageEnvelope create(
            String traceId,
            String senderAgentId,
            String recipientAgentId,
            A2AMessageType messageType,
            String securityLeaseToken,
            Map<String, Object> payload,
            long leaseDurationMs
    ) {
        long now = Instant.now().toEpochMilli();
        return new A2AMessageEnvelope(
                UUID.randomUUID().toString(),
                traceId != null ? traceId : UUID.randomUUID().toString(),
                UUID.randomUUID().toString().substring(0, 8),
                null,
                senderAgentId,
                recipientAgentId,
                messageType,
                securityLeaseToken,
                payload != null ? payload : Map.of(),
                now,
                now + Math.max(1000L, leaseDurationMs),
                0,
                null
        );
    }

    /**
     * 校验安全租约是否有效（时效性与非空校验）
     */
    @JsonIgnore
    public boolean isLeaseValid() {
        if (securityLeaseToken == null || securityLeaseToken.isBlank()) {
            return false;
        }
        long now = Instant.now().toEpochMilli();
        return now <= leaseExpiryTimestamp;
    }

    /**
     * 校验消息跳数是否在允许范围内 (hopCount <= 8)
     */
    @JsonIgnore
    public boolean isHopCountValid() {
        return hopCount <= MAX_HOP_COUNT;
    }

    /**
     * 转发时生成递增跳数的新信封
     */
    public A2AMessageEnvelope withIncrementedHop() {
        return new A2AMessageEnvelope(
                this.messageId,
                this.traceId,
                UUID.randomUUID().toString().substring(0, 8),
                this.spanId,
                this.senderAgentId,
                this.recipientAgentId,
                this.messageType,
                this.securityLeaseToken,
                this.payload,
                this.timestamp,
                this.leaseExpiryTimestamp,
                this.hopCount + 1,
                this.signature
        );
    }

    /**
     * 使用 HMAC-SHA256 算法对信封签名并生成新信封
     */
    public A2AMessageEnvelope withHmacSignature(String secretKey) {
        String sig = calculateHmacSignature(secretKey);
        return new A2AMessageEnvelope(
                this.messageId,
                this.traceId,
                this.spanId,
                this.parentSpanId,
                this.senderAgentId,
                this.recipientAgentId,
                this.messageType,
                this.securityLeaseToken,
                this.payload,
                this.timestamp,
                this.leaseExpiryTimestamp,
                this.hopCount,
                sig
        );
    }

    /**
     * 验证当前信封的 HMAC-SHA256 签名是否合法
     */
    public boolean verifyHmacSignature(String secretKey) {
        if (this.signature == null || this.signature.isBlank()) {
            return false;
        }
        String expectedSig = calculateHmacSignature(secretKey);
        return MessageDigest.isEqual(
                this.signature.getBytes(StandardCharsets.UTF_8),
                expectedSig.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String calculateHmacSignature(String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);

            // 规范化待签名字符串
            String raw = String.format("%s|%s|%s|%s|%s|%s|%d|%d|%d|%s",
                    messageId,
                    traceId,
                    senderAgentId,
                    recipientAgentId,
                    messageType != null ? messageType.name() : "",
                    securityLeaseToken != null ? securityLeaseToken : "",
                    timestamp,
                    leaseExpiryTimestamp,
                    hopCount,
                    payload != null ? payload.toString() : "{}"
            );

            byte[] hmacBytes = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("计算 HMAC-SHA256 签名失败: " + e.getMessage(), e);
        }
    }
}

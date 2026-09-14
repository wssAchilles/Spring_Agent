package tech.qiantong.qknow.hermes.a2a.envelope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 标准不可变 A2A (Agent-to-Agent) 通信协议信封（Java 21 Record）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record A2AMessageEnvelope(
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
                senderAgentId,
                recipientAgentId,
                messageType,
                securityLeaseToken,
                payload != null ? payload : Map.of(),
                now,
                now + Math.max(1000L, leaseDurationMs)
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
}

package tech.qiantong.qknow.mcp.server.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 瞬态操作时效租约管理器 (Transient LeaseToken Manager)
 * 严格限定 60 秒生命周期，单次核销 (Consume-Once)，杜绝高危工具越权与重放攻击
 */
public class McpTransientLeaseManager {

    private static final Logger log = LoggerFactory.getLogger(McpTransientLeaseManager.class);
    public static final long LEASE_TTL_MS = 60 * 1000L; // 严格 60 秒有效

    public record TransientLease(
            String leaseToken,
            String targetToolName,
            long issuedTimeMs,
            long expireTimeMs,
            AtomicBoolean consumed
    ) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTimeMs;
        }
    }

    private final Map<String, TransientLease> activeLeases = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 为高危工具签发一个 60 秒瞬态时效租约
     */
    public String issueLease(String toolName) {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        String token = "lease_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        long now = System.currentTimeMillis();
        TransientLease lease = new TransientLease(
                token,
                toolName,
                now,
                now + LEASE_TTL_MS,
                new AtomicBoolean(false)
        );

        activeLeases.put(token, lease);
        log.info("[租约管理器] 为高危工具 {} 成功签发 60s 瞬态租约: {}", toolName, token);
        cleanExpiredLeases();
        return token;
    }

    /**
     * 消费并核销租约 (CAS 保证仅能消费一次)
     */
    public boolean consumeLease(String toolName, String leaseToken) {
        if (leaseToken == null || !activeLeases.containsKey(leaseToken)) {
            return false;
        }

        TransientLease lease = activeLeases.get(leaseToken);
        if (lease.isExpired()) {
            activeLeases.remove(leaseToken);
            log.warn("[租约管理器] 租约 {} 已超时失效", leaseToken);
            return false;
        }

        if (!lease.targetToolName().equalsIgnoreCase(toolName)) {
            log.warn("[租约管理器] 租约 {} 目标工具不匹配 (声明: {}, 请求: {})", leaseToken, lease.targetToolName(), toolName);
            return false;
        }

        // CAS 原子保证单次消费
        boolean acquired = lease.consumed().compareAndSet(false, true);
        if (acquired) {
            activeLeases.remove(leaseToken);
            return true;
        } else {
            log.warn("[租约管理器] 租约 {} 发生重复消费重放", leaseToken);
            return false;
        }
    }

    private void cleanExpiredLeases() {
        long now = System.currentTimeMillis();
        activeLeases.entrySet().removeIf(entry -> entry.getValue().expireTimeMs() < now);
    }
}

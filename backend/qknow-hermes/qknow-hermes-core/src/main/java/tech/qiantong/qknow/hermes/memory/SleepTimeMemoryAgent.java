package tech.qiantong.qknow.hermes.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SleepTimeMemoryAgent {

    private final MemoryManager memoryManager;
    private final long idleThresholdMs;
    private final int scanCount;

    public SleepTimeMemoryAgent(MemoryManager memoryManager,
                                @Value("${hermes.memory.sleep-agent.idle-threshold-ms:1800000}") long idleThresholdMs,
                                @Value("${hermes.memory.sleep-agent.scan-count:500}") int scanCount) {
        this.memoryManager = memoryManager;
        this.idleThresholdMs = idleThresholdMs;
        this.scanCount = scanCount;
    }

    @Scheduled(fixedDelayString = "${hermes.memory.sleep-agent.fixed-delay-ms:300000}")
    public void consolidateIdleConversations() {
        long now = System.currentTimeMillis();
        ShortTermMemory shortTerm = memoryManager.getShortTerm();
        for (String sessionId : shortTerm.listSessionIds(scanCount)) {
            long lastActiveAt = shortTerm.getLastActivityAt(sessionId);
            if (lastActiveAt <= 0 || now - lastActiveAt < idleThresholdMs) {
                continue;
            }
            String userId = defaultString(shortTerm.getSessionUserId(sessionId), "unknown");
            String scope = defaultString(shortTerm.getSessionScope(sessionId), "default");
            try {
                memoryManager.onConversationEnd(sessionId, userId, scope);
                shortTerm.clearSession(sessionId);
                log.info("Sleep-time memory consolidated: sessionId={}, userId={}, scope={}", sessionId, userId, scope);
            } catch (Exception e) {
                log.warn("Sleep-time memory consolidation failed: sessionId={}", sessionId, e);
            }
        }
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

package tech.qiantong.qknow.hermes.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

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

    @Value("${hermes.memory.sleep-agent.enabled:true}")
    private boolean enabled = true;

    @Scheduled(fixedDelayString = "${hermes.memory.sleep-agent.fixed-delay-ms:300000}")
    public void consolidateIdleConversations() {
        if (!enabled) {
            log.debug("SleepTimeMemoryAgent is disabled via configuration.");
            return;
        }

        // [溯源] 算法优化指南 §4.3: OOM 保护 — JVM 内存使用率 > 85% 时跳过本轮
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double usageRatio = (double) usedMemory / maxMemory;
        if (usageRatio > 0.85) {
            log.warn("Sleep-time memory agent skipped: JVM memory usage {:.1f}% > 85%", usageRatio * 100);
            return;
        }

        long now = System.currentTimeMillis();
        ShortTermMemory shortTerm = memoryManager.getShortTerm();
        // 分批处理，避免 SCAN 全量加载到内存
        int batchSize = 50;
        int processed = 0;
        List<String> sessionIds = shortTerm.listSessionIds(scanCount);
        for (int i = 0; i < sessionIds.size(); i++) {
            String sessionId = sessionIds.get(i);
            long lastActiveAt = shortTerm.getLastActivityAt(sessionId);
            if (lastActiveAt <= 0 || now - lastActiveAt < idleThresholdMs) {
                continue;
            }
            String userId = shortTerm.getSessionUserId(sessionId);
            String scope = shortTerm.getSessionScope(sessionId);
            if (!hasCompleteIdentity(sessionId, userId, scope)) {
                log.warn("Sleep-time memory skipped incomplete identity: sessionId={}", sessionId);
                continue;
            }
            try {
                memoryManager.onConversationEnd(sessionId, userId, scope);
                shortTerm.clearSession(sessionId);
                processed++;
                log.info("Sleep-time memory consolidated: sessionId={}, userId={}, scope={}", sessionId, userId, scope);
            } catch (Exception e) {
                log.warn("Sleep-time memory consolidation failed: sessionId={}", sessionId, e);
            }
            // 每批处理后让出 CPU，避免长时间阻塞
            if (processed >= batchSize) {
                processed = 0;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Sleep-time memory agent interrupted");
                    return;
                }
            }
        }
    }

    private boolean hasCompleteIdentity(String sessionId, String userId, String scope) {
        return sessionId != null && sessionId.matches("[1-9]\\d*")
                && userId != null && userId.matches("[1-9]\\d*")
                && scope != null && scope.matches("workspace:[1-9]\\d*:bot:[1-9]\\d*");
    }
}

package tech.qiantong.qknow.ai.alignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 不可变对齐审计证据账本 (SHA-256 密码学链式哈希存证)
 */
@Component
public class AlignmentAuditLedger {

    private static final Logger log = LoggerFactory.getLogger(AlignmentAuditLedger.class);

    public record AlignmentAuditEntry(
            long entryIndex,
            String eventId,
            String agentId,
            String actionName,
            boolean approved,
            String verdictCode,
            double shapedReward,
            String previousHash,
            String entryHash,
            long timestampMs
    ) {}

    private final AtomicLong indexSequence = new AtomicLong(0);
    private final ConcurrentLinkedDeque<AlignmentAuditEntry> auditChain = new ConcurrentLinkedDeque<>();
    private final AtomicReference<String> latestHashRef = new AtomicReference<>("00000000000000000000000000000000");

    /**
     * 记录不可变对齐审计条目
     */
    public AlignmentAuditEntry recordEntry(
            String agentId,
            String actionName,
            boolean approved,
            String verdictCode,
            double shapedReward
    ) {
        long idx = indexSequence.incrementAndGet();
        long now = System.currentTimeMillis();
        String eventId = "EVT_ALIGN_" + UUID.randomUUID().toString().substring(0, 8);
        String prevHash = latestHashRef.get();

        String entryHash = computeHash(idx, eventId, agentId, actionName, approved, verdictCode, shapedReward, prevHash, now);
        latestHashRef.set(entryHash);

        AlignmentAuditEntry entry = new AlignmentAuditEntry(
                idx, eventId, agentId, actionName, approved, verdictCode, shapedReward, prevHash, entryHash, now
        );
        auditChain.addLast(entry);

        // 维护有界审计窗口
        while (auditChain.size() > 1000) {
            auditChain.pollFirst();
        }

        return entry;
    }

    /**
     * 校验整条审计链的密码学完整性
     */
    public boolean verifyIntegrity() {
        String expectedPrev = "00000000000000000000000000000000";
        for (AlignmentAuditEntry e : auditChain) {
            if (e.entryIndex() == 1L) {
                expectedPrev = "00000000000000000000000000000000";
            }
            if (!e.previousHash().equals(expectedPrev)) {
                return false;
            }
            String calculated = computeHash(
                    e.entryIndex(), e.eventId(), e.agentId(), e.actionName(),
                    e.approved(), e.verdictCode(), e.shapedReward(), e.previousHash(), e.timestampMs()
            );
            if (!calculated.equals(e.entryHash())) {
                return false;
            }
            expectedPrev = e.entryHash();
        }
        return true;
    }

    public long getTotalEntries() {
        return indexSequence.get();
    }

    private static String computeHash(
            long idx, String eventId, String agentId, String action,
            boolean approved, String verdict, double reward, String prevHash, long ts
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = idx + ":" + eventId + ":" + agentId + ":" + action + ":" + approved + ":" + verdict + ":" + reward + ":" + prevHash + ":" + ts;
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "HASH_ERR_" + idx;
        }
    }
}

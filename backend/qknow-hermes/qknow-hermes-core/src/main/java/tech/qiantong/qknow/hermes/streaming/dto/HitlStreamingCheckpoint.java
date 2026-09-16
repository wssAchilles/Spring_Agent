package tech.qiantong.qknow.hermes.streaming.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 人机协同审批 (HITL) 挂起断点快照 Java 21 Record
 * 封装挂起时刻的 Token 偏移量、已发出文本、中间思考链状态与待审工具调用
 */
public record HitlStreamingCheckpoint(
        String checkpointId,
        String sessionId,
        int suspendedTokenOffset,
        String emittedText,
        String partialCoTThought,
        String pendingToolName,
        String pendingToolArguments,
        long createdTimestamp
) {
    /**
     * 计算快照状态完整性摘要哈希 (SHA-256)
     */
    public String computeStateHash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String raw = checkpointId + "|" + sessionId + "|" + suspendedTokenOffset + "|"
                    + (emittedText != null ? emittedText : "") + "|"
                    + (pendingToolName != null ? pendingToolName : "") + "|"
                    + (pendingToolArguments != null ? pendingToolArguments : "");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "hash_error";
        }
    }
}

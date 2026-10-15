package tech.qiantong.qknow.hermes.infra.checkpoint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * 智能体状态快照不可变记录
 * 严格遵循 Java 21 Record 规范，提供时光倒流与分支状态回溯拓扑能力
 */
public record AgentCheckpointSnapshot(
        String taskId,
        String checkpointId,
        String parentCheckpointId,
        long version,
        String activeNodeId,
        Map<String, Object> stateVariables,
        List<String> auditLogs,
        long timestampMicros,
        String stateDigest
) {
    public static AgentCheckpointSnapshot create(
            String taskId,
            String checkpointId,
            String parentCheckpointId,
            long version,
            String activeNodeId,
            Map<String, Object> stateVariables,
            List<String> auditLogs,
            long timestampMicros
    ) {
        Map<String, Object> immutableState = stateVariables != null ? Map.copyOf(stateVariables) : Map.of();
        List<String> immutableLogs = auditLogs != null ? List.copyOf(auditLogs) : List.of();
        String digest = computeStateDigest(taskId, checkpointId, version, activeNodeId, immutableState);
        return new AgentCheckpointSnapshot(
                taskId,
                checkpointId,
                parentCheckpointId,
                version,
                activeNodeId,
                immutableState,
                immutableLogs,
                timestampMicros,
                digest
        );
    }

    private static String computeStateDigest(
            String taskId,
            String checkpointId,
            long version,
            String activeNodeId,
            Map<String, Object> state
    ) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String payload = String.format("%s|%s|%d|%s|%d|%s",
                    taskId, checkpointId, version, activeNodeId, state.size(), state.toString());
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }
}

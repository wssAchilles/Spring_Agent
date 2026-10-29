package tech.qiantong.qknow.hermes.agent.harness;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Hermes 阶段二不可变轻量执行摘要证据凭单 (Lightweight Execution Summary Evidence Record)。
 *
 * <p>吸收 Harness² ledger.py 与 runmark.py 的设计思想，
 * 记录单次智能体执行的轻量物理证据（含输入摘要、RAG 检索置信度、工具耗时与入参出参摘要哈希、Token 估算成本），
 * 并支持确定性 SHA-256 摘要签名防篡改。</p>
 */
public record HarnessEvidenceRecord(
        String evidenceId,
        String requestId,
        Long botId,
        String voucherFingerprint,
        String voucherVersion,
        Instant timestamp,
        String userInputSummary,
        int ragDocumentCount,
        double ragTopScore,
        List<ToolExecutionEntry> toolExecutions,
        long estimatedTokens,
        double estimatedCostCny,
        long executionDurationMs,
        String finalOutputSummary
) {
    /**
     * 单个工具执行的物理证据条目。
     */
    public record ToolExecutionEntry(
            String toolName,
            long durationMs,
            boolean success,
            String inputDigest,
            String outputDigest
    ) {
        public ToolExecutionEntry {
            toolName = Objects.requireNonNull(toolName, "toolName");
            inputDigest = inputDigest == null ? "" : inputDigest;
            outputDigest = outputDigest == null ? "" : outputDigest;
        }

        public String canonical() {
            return toolName + ":" + durationMs + ":" + success + ":" + inputDigest + ":" + outputDigest;
        }
    }

    public HarnessEvidenceRecord {
        evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        requestId = Objects.requireNonNull(requestId, "requestId");
        botId = botId == null ? 0L : botId;
        voucherFingerprint = Objects.requireNonNull(voucherFingerprint, "voucherFingerprint");
        voucherVersion = Objects.requireNonNull(voucherVersion, "voucherVersion");
        timestamp = timestamp == null ? Instant.EPOCH : timestamp;
        userInputSummary = userInputSummary == null ? "" : userInputSummary;
        toolExecutions = toolExecutions == null ? List.of() : List.copyOf(toolExecutions);
        finalOutputSummary = finalOutputSummary == null ? "" : finalOutputSummary;
    }

    /**
     * 计算证据防篡改确定性 SHA-256 签名 (Runmark Digest)。
     */
    public String computeDigest() {
        StringBuilder sb = new StringBuilder();
        sb.append(evidenceId).append("\u001f")
          .append(requestId).append("\u001f")
          .append(botId).append("\u001f")
          .append(voucherFingerprint).append("\u001f")
          .append(voucherVersion).append("\u001f")
          .append(timestamp.toEpochMilli()).append("\u001f")
          .append(userInputSummary).append("\u001f")
          .append(ragDocumentCount).append("\u001f")
          .append(ragTopScore).append("\u001f")
          .append(estimatedTokens).append("\u001f")
          .append(estimatedCostCny).append("\u001f")
          .append(executionDurationMs).append("\u001f")
          .append(finalOutputSummary);

        for (ToolExecutionEntry te : toolExecutions) {
            sb.append("\u001e").append(te.canonical());
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 常量时间校验防篡改签名。
     */
    public boolean verifyDigest(String expectedDigest) {
        if (expectedDigest == null) {
            return false;
        }
        byte[] actual = computeDigest().getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedDigest.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }
}

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
 * 记录单次智能体执行的轻量物理证据（含输入摘要、RAG 检索置信度、工具耗时与入参出参摘要哈希、Token 估算成本及执行模式），
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
        String finalOutputSummary,
        ExecutionMode executionMode,
        UsageStatus usageStatus,
        String capabilityTag,
        boolean guardBlocked,
        int guardBlockedCount,
        RagAttributionSummary ragAttribution
) {
    /**
     * 证据执行模式枚举：严格区分真实生产探针、确定性测试替身与异常保底凭单。
     */
    public enum ExecutionMode {
        REAL_PRODUCTION,
        CONTROLLED_FIXTURE,
        DETERMINISTIC_TEST_DOUBLE,
        FAIL_SAFE
    }

    /**
     * 单个工具执行的物理证据条目。
     */
    public record ToolExecutionEntry(
            String toolName,
            long durationMs,
            boolean success,
            boolean guardBlocked,
            String violationReason,
            String inputDigest,
            String outputDigest
    ) {
        public ToolExecutionEntry {
            toolName = Objects.requireNonNull(toolName, "toolName");
            violationReason = violationReason == null ? "" : violationReason;
            inputDigest = inputDigest == null ? "" : inputDigest;
            outputDigest = outputDigest == null ? "" : outputDigest;
            if (guardBlocked && success) {
                throw new IllegalArgumentException("Guard-blocked tool execution must not be marked as success");
            }
        }

        public ToolExecutionEntry(String toolName, long durationMs, boolean success,
                                  String inputDigest, String outputDigest) {
            this(toolName, durationMs, success, false, "", inputDigest, outputDigest);
        }

        public String canonical() {
            return toolName + ":" + durationMs + ":" + success + ":" + guardBlocked + ":"
                    + violationReason + ":" + inputDigest + ":" + outputDigest;
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
        executionMode = executionMode == null ? ExecutionMode.REAL_PRODUCTION : executionMode;
        usageStatus = usageStatus == null ? UsageStatus.UNKNOWN : usageStatus;
        capabilityTag = capabilityTag == null || capabilityTag.isBlank() ? "PROMPT_ONLY" : capabilityTag;
        guardBlockedCount = Math.max(0, guardBlockedCount);
        ragAttribution = ragAttribution == null
                ? RagAttributionSummary.of(ragDocumentCount, 0, ragTopScore) : ragAttribution;
    }

    public HarnessEvidenceRecord(
            String evidenceId, String requestId, Long botId, String voucherFingerprint,
            String voucherVersion, Instant timestamp, String userInputSummary,
            int ragDocumentCount, double ragTopScore, List<ToolExecutionEntry> toolExecutions,
            long estimatedTokens, double estimatedCostCny, long executionDurationMs,
            String finalOutputSummary, ExecutionMode executionMode
    ) {
        this(evidenceId, requestId, botId, voucherFingerprint, voucherVersion, timestamp,
                userInputSummary, ragDocumentCount, ragTopScore, toolExecutions,
                estimatedTokens, estimatedCostCny, executionDurationMs, finalOutputSummary,
                executionMode, UsageStatus.UNKNOWN, "PROMPT_ONLY",
                blockedCount(toolExecutions) > 0, blockedCount(toolExecutions),
                RagAttributionSummary.of(ragDocumentCount, 0, ragTopScore));
    }

    private static int blockedCount(List<ToolExecutionEntry> entries) {
        return entries == null ? 0 : (int) entries.stream().filter(ToolExecutionEntry::guardBlocked).count();
    }

    /**
     * 兼容 14 参数构造器（默认标记为 REAL_PRODUCTION）。
     */
    public HarnessEvidenceRecord(
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
        this(
                evidenceId,
                requestId,
                botId,
                voucherFingerprint,
                voucherVersion,
                timestamp,
                userInputSummary,
                ragDocumentCount,
                ragTopScore,
                toolExecutions,
                estimatedTokens,
                estimatedCostCny,
                executionDurationMs,
                finalOutputSummary,
                ExecutionMode.REAL_PRODUCTION
        );
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
          .append(finalOutputSummary).append("\u001f")
          .append(executionMode.name()).append("\u001f")
          .append(usageStatus.name()).append("\u001f")
          .append(capabilityTag).append("\u001f")
          .append(guardBlocked).append("\u001f")
          .append(guardBlockedCount).append("\u001f")
          .append(ragAttribution.canonical());

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

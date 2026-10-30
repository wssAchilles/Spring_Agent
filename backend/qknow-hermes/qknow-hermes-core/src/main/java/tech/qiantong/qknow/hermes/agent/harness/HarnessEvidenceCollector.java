package tech.qiantong.qknow.hermes.agent.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 阶段二轻量执行摘要证据采集器 (Lightweight Execution Summary Evidence Collector)。
 *
 * <p>吸收 Harness² ledger 与 context 的设计思想，
 * 采用同步纯内存、非阻塞方式收集单次请求的轻量执行物理证据，零阻塞流式通道，
 * 最终输出不可变凭单 {@link HarnessEvidenceRecord}。</p>
 */
public class HarnessEvidenceCollector {

    private static final Logger log = LoggerFactory.getLogger(HarnessEvidenceCollector.class);

    private final String evidenceId;
    private final String requestId;
    private final Long botId;
    private final String voucherFingerprint;
    private final String voucherVersion;
    private final Instant startTime;

    private volatile String userInputSummary = "";
    private volatile int ragDocumentCount = 0;
    private volatile double ragTopScore = 0.0;
    private final List<HarnessEvidenceRecord.ToolExecutionEntry> toolExecutions = new CopyOnWriteArrayList<>();
    private volatile long estimatedTokens = 0L;
    private volatile double estimatedCostCny = 0.0;
    private volatile String finalOutputSummary = "";

    // 观察者回调列表（只读监听，用于离线评测与测试验证）
    private final List<Consumer<HarnessEvidenceRecord>> completionListeners = new CopyOnWriteArrayList<>();

    public HarnessEvidenceCollector(String requestId, Long botId, HermesHarnessVoucher voucher) {
        this.evidenceId = UUID.randomUUID().toString();
        this.requestId = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        this.botId = botId == null ? 0L : botId;
        this.voucherFingerprint = voucher == null ? "NONE" : voucher.computeFingerprint();
        this.voucherVersion = voucher == null ? "UNKNOWN" : voucher.version();
        this.startTime = Instant.now();
    }

    public void recordUserInput(String input) {
        if (input != null) {
            String trimmed = input.trim();
            this.userInputSummary = trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
        }
    }

    public void recordRagEvidence(int documentCount, double topScore) {
        this.ragDocumentCount = Math.max(0, documentCount);
        this.ragTopScore = Math.max(0.0, topScore);
    }

    public void recordToolExecution(String toolName, long durationMs, boolean success, String inputRaw, String outputRaw) {
        String inDigest = digestText(inputRaw);
        String outDigest = digestText(outputRaw);
        toolExecutions.add(new HarnessEvidenceRecord.ToolExecutionEntry(
                toolName == null ? "unknown_tool" : toolName,
                Math.max(0, durationMs),
                success,
                inDigest,
                outDigest
        ));
    }

    public void recordCostAndTokens(long tokens, double costCny) {
        this.estimatedTokens = Math.max(0L, tokens);
        this.estimatedCostCny = Math.max(0.0, costCny);
    }

    public void recordFinalOutput(String output) {
        if (output != null) {
            String trimmed = output.trim();
            this.finalOutputSummary = trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
        }
    }

    public void addCompletionListener(Consumer<HarnessEvidenceRecord> listener) {
        if (listener != null) {
            this.completionListeners.add(listener);
        }
    }

    /**
     * 终结收集并产出不可变凭单，同时触发监听回调。
     */
    public HarnessEvidenceRecord finalizeEvidence() {
        return finalizeEvidence(HarnessEvidenceRecord.ExecutionMode.REAL_PRODUCTION);
    }

    public HarnessEvidenceRecord finalizeEvidence(HarnessEvidenceRecord.ExecutionMode executionMode) {
        long durationMs = Math.max(0, Instant.now().toEpochMilli() - startTime.toEpochMilli());
        HarnessEvidenceRecord record = new HarnessEvidenceRecord(
                evidenceId,
                requestId,
                botId,
                voucherFingerprint,
                voucherVersion,
                startTime,
                userInputSummary,
                ragDocumentCount,
                ragTopScore,
                List.copyOf(toolExecutions),
                estimatedTokens,
                estimatedCostCny,
                durationMs,
                finalOutputSummary,
                Objects.requireNonNull(executionMode, "executionMode")
        );

        for (Consumer<HarnessEvidenceRecord> listener : completionListeners) {
            try {
                listener.accept(record);
            } catch (Exception e) {
                log.warn("证据完成监听器执行异常: {}", e.getMessage());
            }
        }
        return record;
    }

    private static String digestText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16); // 取前 16 位作为紧凑指纹
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public Long getBotId() {
        return botId;
    }

    public String getVoucherFingerprint() {
        return voucherFingerprint;
    }

    public String getVoucherVersion() {
        return voucherVersion;
    }

    public Instant getStartTime() {
        return startTime;
    }
}

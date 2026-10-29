package tech.qiantong.qknow.hermes.agent.harness.offline.probe;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.offline.OfflineBenchmarkTestCase;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.ModelConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 离线探针执行器标准实现 (Default Offline Probe Executor)。
 *
 * <p>吸收 Google Research Harness² 的真实探针调度机制，
 * 在安全隔离环境中驱动探针运行，收集真实的物理证据并持久化为 JSON 资产文件。</p>
 */
public class DefaultOfflineProbeExecutor implements OfflineProbeExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultOfflineProbeExecutor.class);

    private final HarnessExecutionDriver driver;
    private final AgentOrchestrator orchestratorOrNull; // 如果是生产驱动，持有 orchestrator 用于凭单租约挂载

    public DefaultOfflineProbeExecutor(AgentOrchestrator orchestrator) {
        this.orchestratorOrNull = Objects.requireNonNull(orchestrator, "orchestrator 不能为空");
        this.driver = new ProductionAgentOrchestratorDriver(orchestrator);
    }

    public DefaultOfflineProbeExecutor(HarnessExecutionDriver driver, AgentOrchestrator orchestratorOrNull) {
        this.driver = Objects.requireNonNull(driver, "driver 不能为空");
        this.orchestratorOrNull = orchestratorOrNull;
    }

    @Override
    public ProbeExecutionReport executeSingleProbe(
            String runId,
            String variantName,
            OfflineBenchmarkTestCase testCase,
            HermesHarnessVoucher voucher,
            ProbeExecutionOptions options) {

        Objects.requireNonNull(runId, "runId 不能为空");
        Objects.requireNonNull(variantName, "variantName 不能为空");
        Objects.requireNonNull(testCase, "testCase 不能为空");
        Objects.requireNonNull(voucher, "voucher 不能为空");
        ProbeExecutionOptions opt = options == null ? ProbeExecutionOptions.defaults() : options;

        long startTime = System.currentTimeMillis();
        String probeRequestId = String.format("probe-%s-%s-%s-%s",
                runId, variantName, testCase.taskId(), UUID.randomUUID().toString().substring(0, 8));

        ChatRequest chatRequest = ChatRequest.newBuilder()
                .setRequestId(probeRequestId)
                .setWorkspaceId(9999L)
                .setBotId(voucher.botId())
                .setUserId(8888L)
                .setConversationId(7777L)
                .setQuestion(testCase.question())
                .setSystemPrompt(voucher.invariantSystemPrompt() != null ? voucher.invariantSystemPrompt().canonical() : "")
                .setModelConfig(ModelConfig.newBuilder()
                        .setModelId(1L)
                        .setPlatform(opt.modelPlatform())
                        .setBaseUrl("https://api.deepseek.com")
                        .setModelName(opt.modelName())
                        .build())
                .build();

        StringBuilder fullAnswer = new StringBuilder();
        AtomicReference<Throwable> executionError = new AtomicReference<>();
        AtomicReference<HarnessEvidenceRecord> evidenceRef = new AtomicReference<>();

        ScopedVoucherLease lease = null;
        if (orchestratorOrNull != null) {
            lease = new ScopedVoucherLease(orchestratorOrNull, probeRequestId, voucher);
        }

        try {
            driver.execute(chatRequest)
                    .timeout(Duration.ofMillis(opt.timeoutMs()))
                    .doOnNext(event -> {
                        if (event.hasChunk() && event.getChunk().getText() != null) {
                            fullAnswer.append(event.getChunk().getText());
                        }
                    })
                    .doOnError(executionError::set)
                    .blockLast(Duration.ofMillis(opt.timeoutMs() + 2000L));

            HarnessEvidenceRecord recorded = driver.getLatestEvidence(probeRequestId);
            evidenceRef.set(recorded);
        } catch (Exception e) {
            log.warn("[DefaultOfflineProbeExecutor] 探针执行异常: taskId={}, reason={}", testCase.taskId(), e.getMessage());
            executionError.set(e);
        } finally {
            if (lease != null) {
                lease.close();
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        Throwable error = executionError.get();
        HarnessEvidenceRecord finalEvidence = evidenceRef.get();
        boolean isSuccess = (error == null) && (finalEvidence != null);
        String errorCode = isSuccess ? "NONE" : (error != null ? error.getClass().getSimpleName() : "MISSING_EVIDENCE");

        if (finalEvidence == null) {
            finalEvidence = synthesizeFailSafeEvidence(
                    probeRequestId, voucher, testCase, durationMs, errorCode,
                    error != null ? error.getMessage() : "探针执行未产生证据记录"
            );
        }

        Path persistedPath = null;
        if (opt.persistEvidenceJson()) {
            persistedPath = persistEvidenceToFile(opt.outputBaseDir(), runId, variantName, testCase.taskId(), finalEvidence);
        }

        return new ProbeExecutionReport(
                runId,
                variantName,
                testCase.taskId(),
                probeRequestId,
                voucher.version(),
                voucher.computeFingerprint(),
                isSuccess,
                errorCode,
                durationMs,
                finalEvidence,
                persistedPath,
                fullAnswer.length() > 0 ? fullAnswer.toString() : finalEvidence.finalOutputSummary()
        );
    }

    @Override
    public List<ProbeExecutionReport> executeBatchProbe(
            String runId,
            String variantName,
            List<OfflineBenchmarkTestCase> testSuite,
            HermesHarnessVoucher voucher,
            ProbeExecutionOptions options) {

        Objects.requireNonNull(testSuite, "testSuite 不能为空");
        List<ProbeExecutionReport> reports = new ArrayList<>(testSuite.size());
        for (OfflineBenchmarkTestCase testCase : testSuite) {
            reports.add(executeSingleProbe(runId, variantName, testCase, voucher, options));
        }
        return List.copyOf(reports);
    }

    private Path persistEvidenceToFile(
            Path baseDir, String runId, String variantName, String taskId, HarnessEvidenceRecord evidence) {
        try {
            Path targetDir = baseDir.resolve(runId).resolve(variantName);
            Files.createDirectories(targetDir);

            Path targetFile = targetDir.resolve("evidence_" + taskId + ".json");
            Path tempFile = targetDir.resolve("evidence_" + taskId + ".json.tmp." + UUID.randomUUID());

            String jsonText = JSON.toJSONString(evidence, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteEnumsUsingName);
            Files.writeString(tempFile, jsonText, StandardCharsets.UTF_8);

            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return targetFile;
        } catch (IOException e) {
            log.error("[DefaultOfflineProbeExecutor] 证据物理落盘失败: taskId={}, error={}", taskId, e.getMessage());
            return null;
        }
    }

    private static HarnessEvidenceRecord synthesizeFailSafeEvidence(
            String requestId, HermesHarnessVoucher voucher, OfflineBenchmarkTestCase testCase,
            long durationMs, String errorCode, String detailMsg) {
        return new HarnessEvidenceRecord(
                "ev-failsafe-" + requestId,
                requestId,
                voucher.botId(),
                voucher.computeFingerprint(),
                voucher.version(),
                Instant.now(),
                testCase.question(),
                0,
                0.0,
                List.of(),
                0L,
                0.0,
                durationMs,
                "FAIL_SAFE [" + errorCode + "]: " + detailMsg
        );
    }
}

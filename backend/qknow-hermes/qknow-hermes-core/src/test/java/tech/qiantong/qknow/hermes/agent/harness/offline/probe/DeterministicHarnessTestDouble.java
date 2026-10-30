package tech.qiantong.qknow.hermes.agent.harness.offline.probe;

import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import tech.qiantong.qknow.hermes.proto.DoneSignal;
import tech.qiantong.qknow.hermes.proto.StreamingChunk;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 确定性单测替身 (Deterministic Harness Test Double)。
 *
 * <p>严格限定在 src/test 路径下，用于本地自动化回归测试（mvn test），
 * 纯内存运行、零外部网络依赖、毫秒级响应，确保构建流水线 100% 确定性与稳定性。</p>
 */
public class DeterministicHarnessTestDouble implements HarnessExecutionDriver {

    private final Map<String, DeterministicResponse> fixtureMap;
    private final Map<String, HarnessEvidenceRecord> generatedEvidenceMap = new ConcurrentHashMap<>();

    public record DeterministicResponse(
            String textResponse,
            String toolName,
            boolean toolSuccess,
            long simulatedTokens,
            long simulatedDurationMs
    ) {}

    public DeterministicHarnessTestDouble(Map<String, DeterministicResponse> fixtures) {
        this.fixtureMap = fixtures == null ? Map.of() : Map.copyOf(fixtures);
    }

    @Override
    public Flux<ChatEvent> execute(ChatRequest request) {
        String reqId = request.getRequestId();
        String taskId = extractTaskId(reqId);
        DeterministicResponse resp = fixtureMap.getOrDefault(taskId,
                new DeterministicResponse("确定性兜底回答", "defaultTool", true, 500L, 20L));

        boolean isCandidate = reqId.contains("candidate");
        long tokens = isCandidate ? Math.max(100L, resp.simulatedTokens() - 40L) : resp.simulatedTokens();
        String answer = isCandidate ? resp.textResponse() + " (精炼客观)" : resp.textResponse() + " (基线冗余套话)";

        // 构造对应的不可变凭单存入内部 Map，强制标记为 DETERMINISTIC_TEST_DOUBLE
        HarnessEvidenceRecord record = new HarnessEvidenceRecord(
                "ev-test-" + reqId,
                reqId,
                request.getBotId(),
                isCandidate ? "v-cand-fingerprint" : "v-base-fingerprint",
                isCandidate ? "1.1.0-CAND" : "1.0.0-BASE",
                Instant.now(),
                request.getQuestion(),
                1,
                0.95,
                List.of(new HarnessEvidenceRecord.ToolExecutionEntry(
                        resp.toolName(),
                        10L,
                        resp.toolSuccess(),
                        "in_hash_sample",
                        "out_hash_sample"
                )),
                tokens,
                tokens * 0.0000015,
                resp.simulatedDurationMs(),
                answer,
                HarnessEvidenceRecord.ExecutionMode.DETERMINISTIC_TEST_DOUBLE
        );
        generatedEvidenceMap.put(reqId, record);

        return Flux.just(
                ChatEvent.newBuilder()
                        .setRequestId(reqId)
                        .setChunk(StreamingChunk.newBuilder().setText(answer).build())
                        .build(),
                ChatEvent.newBuilder()
                        .setRequestId(reqId)
                        .setDone(DoneSignal.newBuilder().build())
                        .build()
        );
    }

    @Override
    public HarnessEvidenceRecord getLatestEvidence(String requestId) {
        return generatedEvidenceMap.get(requestId);
    }

    @Override
    public DriverMode getMode() {
        return DriverMode.DETERMINISTIC_DOUBLE;
    }

    private static String extractTaskId(String requestId) {
        if (requestId == null) return "unknown";
        // 例如 probe-run01-baseline-kb-qa-01-xxxx
        String[] parts = requestId.split("-");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("qa".equals(parts[i]) || "route".equals(parts[i]) || "guard".equals(parts[i])
                    || "history".equals(parts[i]) || "tool".equals(parts[i])) {
                if (i > 0 && i + 1 < parts.length) {
                    return parts[i - 1] + "-" + parts[i] + "-" + parts[i + 1];
                }
            }
        }
        return requestId;
    }
}

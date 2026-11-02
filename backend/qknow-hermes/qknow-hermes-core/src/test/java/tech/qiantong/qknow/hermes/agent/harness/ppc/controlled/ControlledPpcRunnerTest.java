package tech.qiantong.qknow.hermes.agent.harness.ppc.controlled;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.Category;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.HistoryTurn;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.RetrievalHit;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.Rubric;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.Split;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledFixture.ToolFixture;
import tech.qiantong.qknow.hermes.agent.harness.ppc.controlled.ControlledPpcRunner.Status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ControlledPpcRunnerTest {
    @TempDir Path temp;

    @Test
    void controlledVouchersMatchFrozenFingerprints() {
        var baseline = ControlledPpcRunner.baseline();
        var mutator = new tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator();
        assertEquals("b0bef691ef7890671a96224d24dc60d245fb1ba068cdee287da58d5b11d0bfe5",
                baseline.computeFingerprint());
        assertEquals("8f1adbcb1ace26ca138a09ef6b55c55a754c818c3ef95049d2496bba1b024b5c",
                mutator.mutate(baseline,
                        tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator.CONCISE_SYSTEM_PROMPT)
                        .computeFingerprint());
        assertEquals("4b31f14a8d17ba1c3098be034e6accac902d10db85a97f282f1670b1ca276cd8",
                mutator.mutate(baseline,
                        tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator.ADAPTIVE_MEMORY_WINDOW)
                        .computeFingerprint());
    }

    @Test
    void actualPostgresRowsReachTheAgentWithoutGoldIdInjection() throws Exception {
        String url = System.getProperty("stage13.jdbcUrl");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "isolated controlled PostgreSQL is opt-in");
        try (var connection = DriverManager.getConnection(url)) {
            List<ControlledFixture> rows = new JdbcControlledFixtureSource(connection).load();
            assertEquals(10, rows.size());
            assertEquals(List.of("seg-b-payment-01", "seg-b-mesh-01", "seg-h-webhook-01", "seg-h-queue-01"),
                    rows.stream().filter(row -> row.retrieval() != null)
                            .map(row -> row.retrieval().segmentId()).toList());
            ChatModel model = mock(ChatModel.class);
            when(model.stream(any(Prompt.class))).thenAnswer(call -> Flux.just(realFixtureResponse(call.getArgument(0))));
            when(model.call(any(Prompt.class))).thenAnswer(call -> realFixtureResponse(call.getArgument(0)));

            var result = new ControlledPpcRunner(new JdbcControlledFixtureSource(connection), temp)
                    .runPreLive(model);

            assertEquals(Status.PRELIVE_COMPLETE, result.status(), result.reason());
            assertEquals(50, result.probes().size());
            assertEquals("CONCISE_SYSTEM_PROMPT", result.selectedOperator().name());
            assertTrue(result.probes().stream().allMatch(ControlledPpcRunner.ProbeRecord::valid));
            assertTrue(result.probes().stream().filter(probe -> probe.candidateId().equals("CONCISE_SYSTEM_PROMPT"))
                    .allMatch(ControlledPpcRunner.ProbeRecord::qualityPass));
            assertTrue(result.probes().stream().filter(probe -> probe.toolResultId() != null)
                    .allMatch(probe -> !probe.actualToolArgs().isBlank()));
            assertFalse(Files.exists(result.directory().resolve("experience")));
        }
    }

    @Test
    void changedDatabaseBodyCannotBeAuthenticatedAsFrozenFixture() throws Exception {
        String url = System.getProperty("stage13.jdbcUrl");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "isolated controlled PostgreSQL is opt-in");
        try (var connection = DriverManager.getConnection(url)) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    assertEquals(1, statement.executeUpdate("UPDATE h11b2a.segment SET body=body || ' drift' "
                            + "WHERE id='seg-b-payment-01'"));
                }
                assertThrows(IllegalStateException.class,
                        () -> new JdbcControlledFixtureSource(connection).load());
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void fiftyPreLiveProbesUseRealOrchestratorWithoutWritingAdoptedExperience() throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenAnswer(call -> Flux.just(fakeResponse(call.getArgument(0), true)));
        when(model.call(any(Prompt.class))).thenAnswer(call -> fakeResponse(call.getArgument(0), true));

        var result = new ControlledPpcRunner(fixtures(), temp).runPreLive(model);

        assertEquals(Status.PRELIVE_COMPLETE, result.status(), result.reason());
        assertEquals(50, result.probes().size());
        assertEquals("CONCISE_SYSTEM_PROMPT", result.selectedOperator().name());
        assertEquals(30, result.probes().stream().filter(probe -> probe.split() == Split.BOOTSTRAP).count());
        assertEquals(20, result.probes().stream().filter(probe -> probe.split() == Split.HOLDOUT).count());
        assertEquals(10, result.probes().stream().filter(probe -> probe.split() == Split.HOLDOUT
                && probe.candidateId().equals("CONCISE_SYSTEM_PROMPT")).count());
        assertEquals(0, result.probes().stream().filter(probe -> probe.split() == Split.HOLDOUT
                && probe.candidateId().equals("ADAPTIVE_MEMORY_WINDOW")).count());
        assertTrue(result.probes().stream().allMatch(ControlledPpcRunner.ProbeRecord::valid));
        assertTrue(result.probes().stream().allMatch(probe -> probe.agentEvidence() != null
                && probe.agentEvidence().executionMode() == HarnessEvidenceRecord.ExecutionMode.CONTROLLED_FIXTURE
                && probe.agentEvidence().computeDigest().equals(probe.agentEvidenceDigest())
                && probe.firstPromptSha256().length() == 64));
        assertTrue(result.probes().stream().filter(probe -> probe.candidateId().equals("CONCISE_SYSTEM_PROMPT"))
                .allMatch(ControlledPpcRunner.ProbeRecord::qualityPass));
        assertTrue(result.probes().stream().filter(probe -> probe.retrievalQuery() != null)
                .allMatch(probe -> probe.ragConsumed()
                        && probe.documentId() != null && probe.segmentId() != null
                        && probe.bodySha256().length() == 64));
        assertTrue(result.probes().stream().filter(probe -> probe.retrievalQuery() != null
                        && probe.candidateId().equals("CONCISE_SYSTEM_PROMPT"))
                .allMatch(probe -> probe.ragUsed() > 0));
        assertTrue(result.probes().stream().filter(probe -> probe.toolResultId() != null)
                .allMatch(probe -> probe.actualToolArgs().contains("fixtureId")));
        assertTrue(result.probes().stream().filter(probe -> probe.category() == Category.HISTORY)
                .allMatch(probe -> probe.historyConsumed() && probe.historySourceIds().size() == 4));
        assertTrue(Files.isRegularFile(result.directory().resolve("checksums.sha256")));
        assertTrue(Files.isRegularFile(result.directory().resolve("vouchers.json")));
        assertTrue(Files.isRegularFile(result.directory().resolve("selection.json")));
        assertFalse(Files.exists(result.directory().resolve("experience")),
                "fake-model pre-live results must never write ADOPTED");
    }

    @Test
    void missingUsageIsBlockedAndDoesNotBecomeNoAdopted() throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(
                List.of(new Generation(new AssistantMessage("answer-B01"))))));

        var result = new ControlledPpcRunner(fixtures(), temp).runPreLive(model);

        assertEquals(Status.BLOCKED, result.status());
        assertEquals("CONTROLLED_USAGE_UNKNOWN", result.reason());
        assertEquals(1, result.probes().size());
        assertEquals(1, result.modelCalls());
        assertNull(result.costCny(), "missing usage must not be reported as zero cost");
        assertNull(result.probes().getFirst().costCny());
        assertFalse(Files.exists(result.directory().resolve("experience")));
    }

    @Test
    void unqualifiedBootstrapStopsAtThirtyWithoutEnteringHoldout() throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(
                List.of(new Generation(new AssistantMessage("unable"))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(100, 40, 140)).build())));

        var result = new ControlledPpcRunner(fixtures(), temp).runPreLive(model);

        assertEquals(Status.PRELIVE_COMPLETE, result.status());
        assertNull(result.selectedOperator());
        assertEquals(30, result.probes().size());
        assertTrue(result.probes().stream().allMatch(probe -> probe.split() == Split.BOOTSTRAP));
        assertFalse(Files.exists(result.directory().resolve("experience")));
    }

    @Test
    void absentLiveCredentialBlocksBeforeAnyModelCallOrEvidenceWrite() throws Exception {
        var result = new ControlledPpcRunner(fixtures(), temp).runLive(null);
        assertEquals(Status.BLOCKED, result.status());
        assertEquals(0, result.modelCalls());
        assertTrue(result.probes().isEmpty());
        assertNull(result.directory());
    }

    @Test
    void syntheticFixtureCannotClaimVerifiedLiveProvenance() throws Exception {
        var result = new ControlledPpcRunner(fixtures(), temp)
                .runLive("test-key-not-a-real-credential");
        assertEquals(Status.BLOCKED, result.status());
        assertEquals(0, result.modelCalls());
        assertTrue(result.probes().isEmpty());
        assertNull(result.directory());
    }

    @Test
    void unauthorizedToolCallCannotQualifyAProbe() throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(
                List.of(new Generation(AssistantMessage.builder().content("").toolCalls(List.of(
                        new ToolCall("unauthorized", "function", "transaction_lookup", "{}"))).build())),
                ChatResponseMetadata.builder().usage(new DefaultUsage(100, 40, 140)).build())));

        var result = new ControlledPpcRunner(fixtures(), temp).runPreLive(model);

        assertEquals(Status.BLOCKED, result.status());
        assertEquals(1, result.probes().size());
        assertFalse(result.probes().getFirst().qualityPass());
        assertFalse(Files.exists(result.directory().resolve("experience")));
    }

    @Test
    void orchestratorKnowledgeAliasUsesOnlyControlledRagAndRemainsSafe() throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenAnswer(call -> Flux.just(responseWithKnowledgeAlias(call.getArgument(0))));
        when(model.call(any(Prompt.class))).thenAnswer(call -> responseWithKnowledgeAlias(call.getArgument(0)));

        var result = new ControlledPpcRunner(fixtures(), temp).runPreLive(model);

        assertEquals(Status.PRELIVE_COMPLETE, result.status(), result.reason());
        List<ControlledPpcRunner.ProbeRecord> ragProbes = result.probes().stream()
                .filter(probe -> probe.taskId().equals("B02")).toList();
        assertEquals(6, ragProbes.size());
        assertTrue(ragProbes.stream().allMatch(probe -> probe.safe() && probe.ragUsed() > 0
                && probe.agentEvidence().toolExecutions().stream()
                        .anyMatch(tool -> tool.toolName().equals("knowledgeQuery") && tool.success())));
    }

    @Test
    void budgetStopsBeforeTheNextModelCall() throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenAnswer(call -> Flux.just(new ChatResponse(
                List.of(new Generation(new AssistantMessage("answer-B01"))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(199_000, 100, 199_100)).build())));

        var result = new ControlledPpcRunner(fixtures(), temp).runPreLive(model);

        assertEquals(Status.BUDGET_EXHAUSTED, result.status());
        assertEquals(1, result.modelCalls());
        verify(model, times(1)).stream(any(Prompt.class));
        assertFalse(Files.exists(result.directory().resolve("experience")));
    }

    @Test
    void controlledClientForcesFlashNonThinkingAndOutputCeiling() {
        var client = new DeepSeekCompatibleChatModel("https://api.deepseek.com", "", "deepseek-flash",
                null, 2_000, false);
        var body = client.buildRequestBody(new Prompt("test",
                tech.qiantong.qknow.ai.deepseek.DeepSeekChatOptions.builder()
                        .model("deepseek-v4-pro").thinkingEnabled(true).maxTokens(4_000).build()), true);
        assertEquals("deepseek-flash", body.get("model"));
        assertEquals(Map.of("type", "disabled"), body.get("thinking"));
        assertEquals(2_000, body.get("max_tokens"));
    }

    private static ChatResponse fakeResponse(Prompt prompt, boolean metered) {
        String joined = prompt.getInstructions().stream().map(message -> message.getText() == null
                ? "" : message.getText()).reduce("", (left, right) -> left + "\n" + right);
        String id = List.of("B01", "B02", "B03", "B04", "B05", "H01", "H02", "H03", "H04", "H05")
                .stream().filter(joined::contains).findFirst().orElseThrow();
        boolean concise = prompt.getSystemMessage().getText()
                .contains("优先依据受控证据回答； 证据不足时明确说明");
        boolean toolTask = id.endsWith("03") || id.endsWith("05");
        boolean toolReturned = prompt.getInstructions().stream().anyMatch(ToolResponseMessage.class::isInstance);
        AssistantMessage answer;
        if (toolTask && !toolReturned) {
            String name = id.endsWith("03") ? "transaction_lookup" : "architecture_metrics";
            answer = AssistantMessage.builder().content("").toolCalls(List.of(
                    new ToolCall("call-" + id, "function", name, "{\"fixtureId\":\"" + id + "\"}"))).build();
        } else {
            answer = new AssistantMessage(concise ? "controlled fixture answer-" + id : "unable");
        }
        return metered ? new ChatResponse(List.of(new Generation(answer)),
                ChatResponseMetadata.builder().usage(new DefaultUsage(100, 40, 140)).build())
                : new ChatResponse(List.of(new Generation(answer)));
    }

    private static ChatResponse responseWithKnowledgeAlias(Prompt prompt) {
        String joined = prompt.getInstructions().stream().map(message -> message.getText() == null
                ? "" : message.getText()).reduce("", (left, right) -> left + "\n" + right);
        if (joined.contains("question-B02")
                && prompt.getInstructions().stream().noneMatch(ToolResponseMessage.class::isInstance)) {
            return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                    .toolCalls(List.of(
                            new ToolCall("kb-B02", "function", "knowledgeBase110201", "{\"query\":\"query-B02\"}"),
                            new ToolCall("alias-B02", "function", "knowledgeQuery", "{\"query\":\"query-B02\"}")))
                    .build())), ChatResponseMetadata.builder().usage(new DefaultUsage(100, 40, 140)).build());
        }
        return fakeResponse(prompt, true);
    }

    private static ChatResponse realFixtureResponse(Prompt prompt) {
        String joined = prompt.getInstructions().stream().map(message -> message.getText() == null
                ? "" : message.getText()).reduce("", (left, right) -> left + "\n" + right);
        String id = joined.contains("2026-11-31") ? "B01"
                : joined.contains("gateway retry") ? "B02"
                : joined.contains("TX-FIX-B-001") ? "B03"
                : joined.contains("尾延迟主要来自哪里") ? "B04"
                : joined.contains("CLUSTER-FIX-B") ? "B05"
                : joined.contains("2026-02-30") ? "H01"
                : joined.contains("webhook signature") ? "H02"
                : joined.contains("TX-FIX-H-001") ? "H03"
                : joined.contains("缓存迁移失败") ? "H04"
                : joined.contains("QUEUE-FIX-H") ? "H05" : null;
        assertNotNull(id, "frozen task must remain identifiable by its actual question");
        boolean concise = prompt.getSystemMessage().getText()
                .contains("优先依据受控证据回答； 证据不足时明确说明");
        boolean toolReturned = prompt.getInstructions().stream().anyMatch(ToolResponseMessage.class::isInstance);
        AssistantMessage answer;
        if (List.of("B03", "B05", "H03", "H05").contains(id) && !toolReturned) {
            String name = id.endsWith("03") ? "transaction_lookup"
                    : id.equals("B05") ? "architecture_metrics" : "queue_metrics";
            String args = switch (id) {
                case "B03" -> "{\"transactionId\":\"TX-FIX-B-001\"}";
                case "H03" -> "{\"transactionId\":\"TX-FIX-H-001\"}";
                case "B05" -> "{\"clusterId\":\"CLUSTER-FIX-B\"}";
                default -> "{\"queueId\":\"QUEUE-FIX-H\"}";
            };
            answer = AssistantMessage.builder().content("").toolCalls(List.of(
                    new ToolCall("call-" + id, "function", name, args))).build();
        } else {
            String correct = switch (id) {
                case "B01" -> "2026-11-31 无效，11月只有30天";
                case "B02" -> "controlled fixture 网关超时最多重试3次，退避1000 2000 4000，Idempotency-Key SHA-256";
                case "B03" -> "TX-FIX-B-001 SUCCESS ACC-FIX-B-01 未发凭证";
                case "B04" -> "方案 A 上下文切换和 socket 转发；方案 B 用 eBPF 缓解";
                case "B05" -> "controlled fixture 单体、Sidecar、eBPF 的 P99 与 CPU 是 12、17、13";
                case "H01" -> "2026-02-30 无效，2月只有28天";
                case "H02" -> "controlled fixture Webhook 用 HMAC-SHA256，窗口5分钟，nonce 只能使用一次";
                case "H03" -> "TX-FIX-H-001 PENDING ACC-FIX-H-02";
                case "H04" -> "选择方案 B，短期双写；失败时回滚旧缓存读路径";
                default -> "controlled fixture primary standby 的 P99 与 depth 分别为48和19，先停写再切换并核对游标";
            };
            answer = new AssistantMessage(concise ? correct : "证据不足");
        }
        return new ChatResponse(List.of(new Generation(answer)),
                ChatResponseMetadata.builder().usage(new DefaultUsage(100, 40, 140)).build());
    }

    private static List<ControlledFixture> fixtures() {
        List<ControlledFixture> tasks = new ArrayList<>();
        for (Split split : Split.values()) {
            for (int number = 1; number <= 5; number++) {
                String id = (split == Split.BOOTSTRAP ? "B" : "H") + "0" + number;
                Category category = Category.values()[number - 1];
                RetrievalHit retrieval = category == Category.RAG || category == Category.COMPOSITE
                        ? new RetrievalHit("doc-" + id, "seg-" + id, "retrieved body " + id, 1.0D) : null;
                ToolFixture tool = category == Category.TOOL || category == Category.COMPOSITE
                        ? new ToolFixture("result-" + id,
                                category == Category.TOOL ? "transaction_lookup" : "architecture_metrics",
                                "Read controlled value", "{\"type\":\"object\",\"properties\":{\"fixtureId\":{\"type\":\"string\"}},\"required\":[\"fixtureId\"]}",
                                "{\"fixtureId\":\"" + id + "\"}", "{\"answer\":\"" + id + "\"}") : null;
                List<HistoryTurn> history = category == Category.HISTORY ? List.of(
                        new HistoryTurn("src-" + id + "-1", "user", "history-" + id + "-1"),
                        new HistoryTurn("src-" + id + "-2", "assistant", "history-" + id + "-2"),
                        new HistoryTurn("src-" + id + "-3", "user", "history-" + id + "-3"),
                        new HistoryTurn("src-" + id + "-4", "assistant", "history-" + id + "-4")) : List.of();
                Rubric rubric = new Rubric(List.of("answer-" + id), List.of(),
                        retrieval == null ? null : retrieval.segmentId(),
                        tool == null ? null : tool.resultId(), history.stream().map(HistoryTurn::sourceId).toList());
                tasks.add(new ControlledFixture(id, split, category, "question-" + id,
                        retrieval == null ? null : "query-" + id, retrieval, tool, history, rubric));
            }
        }
        return List.copyOf(tasks);
    }
}

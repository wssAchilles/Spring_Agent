package tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessAssembler;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.experience.DefaultExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDisposition;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.FileSystemExperienceStore;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.PpcRoundEvidence.Score;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.TaskLevelRecursivePpc.Outcome;
import tech.qiantong.qknow.hermes.agent.harness.ppc.tasklevel.TaskLevelRecursivePpc.StopReason;

import static org.junit.jupiter.api.Assertions.*;

class TaskLevelRecursivePpcTest {
    private static final ExperienceDomain DOMAIN =
            new ExperienceDomain(12001, 12001, "synthetic_algorithm_benchmark");
    private static final List<Operator> OPERATORS = List.of(
            Operator.CONCISE_SYSTEM_PROMPT, Operator.ADAPTIVE_MEMORY_WINDOW);

    @TempDir Path temp;

    @Test
    void sixTaskBenchmarkClosesTwoRoundPpcAndTransfersOnlyAdoptedExperience() throws Exception {
        Benchmark benchmark = benchmark();
        assertEquals(TaskLevelRecursivePpc.MODE, benchmark.mode());
        assertEquals("hac1-v1", benchmark.id());
        assertEquals(6, benchmark.tasks().size());
        assertTrue(benchmark.tasks().stream().allMatch(task -> !task.question().isBlank()));
        assertEquals(3, benchmark.tasks().stream().filter(task -> task.split().equals("BOOTSTRAP")).count());
        assertEquals(3, benchmark.tasks().stream().filter(task -> task.split().equals("TRANSFER")).count());
        assertEquals(Set.of("PROMPT_ONLY", "RAG", "TOOL", "HISTORY", "COMPOSITE"),
                benchmark.tasks().stream().map(FixtureTask::category).collect(java.util.stream.Collectors.toSet()));

        ExperienceLibrary library = library();
        TaskLevelRecursivePpc engine = new TaskLevelRecursivePpc(library);
        HermesHarnessVoucher baseline = baseline();
        List<TaskLevelRecursivePpc.Result> bootstrap = new ArrayList<>();
        for (FixtureTask task : benchmark.tasks()) {
            if (task.split().equals("BOOTSTRAP")) {
                bootstrap.add(engine.runBootstrap(taskSpec(task, benchmark.cue()), baseline,
                        OPERATORS, oracle(task)));
            }
        }

        assertTrue(bootstrap.stream().allMatch(result -> result.baselineScore().quality() < 1.0D));
        assertTrue(bootstrap.stream().allMatch(result -> result.outcome() == Outcome.ADOPTED));
        assertEquals(List.of(0.0D, 0.0D, 0.0D),
                bootstrap.stream().map(result -> result.baselineScore().quality()).toList());
        assertEquals(List.of(1.0D, 1.0D, 1.0D),
                bootstrap.stream().map(result -> result.selectedScore().quality()).toList());
        TaskLevelRecursivePpc.Result twoRound = bootstrap.get(2);
        assertEquals(2, twoRound.rounds().size());
        assertTrue(twoRound.rounds().stream().allMatch(PpcRoundEvidence::improved));
        assertEquals(List.of(0.5D, 1.0D), twoRound.rounds().stream()
                .map(round -> round.candidates().stream()
                        .filter(candidate -> candidate.proposal().equals(round.selected()))
                        .findFirst().orElseThrow().score().quality()).toList());
        assertEquals(StopReason.ROUND_LIMIT, twoRound.stopReason());
        assertEquals(4, twoRound.probeCount());

        List<ExperienceEntry> adopted = library.recallForProposer(DOMAIN, benchmark.cue(), 10)
                .matchedEntries();
        assertEquals(4, adopted.size());
        assertTrue(adopted.stream().allMatch(entry -> entry.disposition() == ExperienceDisposition.ADOPTED
                && !entry.failureObserved()
                && entry.evidenceRef().executionMode().equals(TaskLevelRecursivePpc.MODE)));
        assertEquals(twoRound.rounds().get(1).computeDigest(),
                twoRound.adoptedEntries().get(1).evidenceRef().evidenceSha256());

        List<TaskLevelRecursivePpc.Result> transfer = new ArrayList<>();
        for (FixtureTask task : benchmark.tasks()) {
            if (task.split().equals("TRANSFER")) {
                transfer.add(engine.runTransfer(taskSpec(task, benchmark.cue()), baseline, oracle(task)));
            }
        }
        assertEquals(List.of(Outcome.TRANSFERRED, Outcome.TRANSFERRED, Outcome.NO_TRANSFER),
                transfer.stream().map(TaskLevelRecursivePpc.Result::outcome).toList());
        assertEquals(List.of(0.0D, 0.0D, 1.0D),
                transfer.stream().map(result -> result.baselineScore().quality()).toList());
        assertEquals(List.of(1.0D, 1.0D, 1.0D),
                transfer.stream().map(result -> result.selectedScore().quality()).toList());
        assertTrue(transfer.stream().allMatch(result -> !result.recalledEntryIds().isEmpty()));
        assertTrue(transfer.get(0).appliedEntryIds().stream().allMatch(id -> id.startsWith("hac1-B")));
        assertTrue(transfer.get(1).appliedEntryIds().stream().allMatch(id -> id.startsWith("hac1-B")));
        assertTrue(transfer.get(2).appliedEntryIds().isEmpty());
        assertEquals(StopReason.NO_IMPROVEMENT, transfer.get(2).stopReason());
        assertEquals(4, library.recallForProposer(DOMAIN, benchmark.cue(), 10).matchedEntries().size(),
                "transfer must not write its own experience");
        assertTrue(bootstrap.stream().allMatch(result -> result.rounds().size() <= 2
                && result.probeCount() <= 4));
    }

    @Test
    void failedProbeAndInsufficientBudgetKeepTheLastVerifiedVoucher() throws Exception {
        FixtureTask task = benchmark().tasks().getFirst();
        TaskLevelRecursivePpc.Task spec = taskSpec(task, benchmark().cue());
        HermesHarnessVoucher baseline = baseline();
        ExperienceLibrary library = library();

        TaskLevelRecursivePpc.Result failed = new TaskLevelRecursivePpc(library).runBootstrap(
                spec, baseline, OPERATORS, (ignored, voucher) -> {
                    if (!voucher.computeFingerprint().equals(baseline.computeFingerprint())) {
                        throw new IllegalStateException("synthetic probe failure");
                    }
                    return new Score(0.0D, true);
                });
        assertEquals(Outcome.NO_ADOPTION, failed.outcome());
        assertEquals(StopReason.PROBE_FAILED, failed.stopReason());
        assertEquals(baseline.computeFingerprint(), failed.selected().computeFingerprint());
        assertTrue(failed.adoptedEntries().isEmpty());

        TaskLevelRecursivePpc.Result bounded = new TaskLevelRecursivePpc(library,
                new PpcStopPolicy(2, 2)).runBootstrap(spec, baseline, OPERATORS, oracle(task));
        assertEquals(StopReason.BUDGET_EXHAUSTED, bounded.stopReason());
        assertEquals(1, bounded.probeCount());
        assertEquals(Outcome.NO_ADOPTION, bounded.outcome());
        assertTrue(library.recallForProposer(DOMAIN, benchmark().cue(), 10).matchedEntries().isEmpty());
    }

    @Test
    void secondRoundProbeFailurePreservesFirstRoundImprovement() throws Exception {
        FixtureTask task = benchmark().tasks().get(2);
        HermesHarnessVoucher baseline = baseline();
        TaskLevelRecursivePpc.Probe deterministic = oracle(task);
        TaskLevelRecursivePpc.Result result = new TaskLevelRecursivePpc(library()).runBootstrap(
                taskSpec(task, benchmark().cue()), baseline, OPERATORS, (spec, voucher) -> {
                    Score score = deterministic.evaluate(spec, voucher);
                    if (score.quality() == 1.0D) {
                        throw new IllegalStateException("synthetic second-round failure");
                    }
                    return score;
                });

        assertEquals(StopReason.PROBE_FAILED, result.stopReason());
        assertEquals(Outcome.ADOPTED, result.outcome());
        assertEquals(2, result.rounds().size());
        assertEquals(0.5D, result.selectedScore().quality());
        assertEquals(result.rounds().getFirst().selectedFingerprint(),
                result.selected().computeFingerprint());
        assertEquals(1, result.adoptedEntries().size());
    }

    @Test
    void negativeUnscopedAndCrossDomainEntriesCannotBecomeTransferProposals() throws Exception {
        Benchmark benchmark = benchmark();
        FixtureTask source = benchmark.tasks().getFirst();
        ExperienceLibrary library = library();
        TaskLevelRecursivePpc engine = new TaskLevelRecursivePpc(library);
        ExperienceEntry accepted = engine.runBootstrap(taskSpec(source, benchmark.cue()),
                baseline(), OPERATORS, oracle(source)).adoptedEntries().getFirst();
        library.recordExperience(new ExperienceEntry("negative-entry", DOMAIN, benchmark.cue(),
                "Rejected synthetic operator must not become a positive proposal",
                "An isolated negative observation", accepted.evidenceRef(), 1.0D,
                accepted.sourceRunId(), accepted.harnessFingerprint(), ExperienceDisposition.REJECTED,
                false, ExperienceEntry.AttributionStatus.NOT_APPLICABLE, 1, false, null, Instant.EPOCH));
        library.recordExperience(new ExperienceEntry("unresolved-entry", DOMAIN, benchmark.cue(),
                "Unresolved synthetic failure must not become a positive proposal",
                "An isolated unresolved observation", accepted.evidenceRef(), 1.0D,
                accepted.sourceRunId(), accepted.harnessFingerprint(), ExperienceDisposition.OBSERVED,
                true, ExperienceEntry.AttributionStatus.UNRESOLVED, 1, false, null, Instant.EPOCH));

        FixtureTask transferTask = benchmark.tasks().get(3);
        TaskLevelRecursivePpc.Result sameDomain = engine.runTransfer(
                taskSpec(transferTask, benchmark.cue()), baseline(), oracle(transferTask));
        assertEquals(List.of(accepted.entryId()), sameDomain.recalledEntryIds());
        assertEquals(Outcome.TRANSFERRED, sameDomain.outcome());

        ExperienceDomain other = new ExperienceDomain(12001, 12002, "synthetic_algorithm_benchmark");
        TaskLevelRecursivePpc.Result crossDomain = engine.runTransfer(
                new TaskLevelRecursivePpc.Task("cross", other, benchmark.cue()), baseline(),
                (ignored, voucher) -> new Score(0.0D, true));
        assertEquals(Outcome.NO_TRANSFER, crossDomain.outcome());
        assertTrue(crossDomain.recalledEntryIds().isEmpty());
        TaskLevelRecursivePpc.Result unscoped = engine.runTransfer(
                new TaskLevelRecursivePpc.Task("unscoped", ExperienceDomain.unscopedReplay(),
                        benchmark.cue()), baseline(), (ignored, voucher) -> new Score(0.0D, true));
        assertEquals(Outcome.NO_TRANSFER, unscoped.outcome());
        assertTrue(unscoped.recalledEntryIds().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> engine.runBootstrap(
                new TaskLevelRecursivePpc.Task("no-adopt", ExperienceDomain.unscopedReplay(),
                        benchmark.cue()), baseline(), OPERATORS, (ignored, voucher) -> new Score(0.0D, true)));
    }

    private ExperienceLibrary library() {
        return new DefaultExperienceLibrary(new FileSystemExperienceStore(temp));
    }

    private static HermesHarnessVoucher baseline() {
        return new HermesHarnessAssembler().createDefaultProductionVoucher(DOMAIN.botId());
    }

    private static TaskLevelRecursivePpc.Task taskSpec(FixtureTask task, String cue) {
        return new TaskLevelRecursivePpc.Task(task.id(), DOMAIN, cue);
    }

    private static TaskLevelRecursivePpc.Probe oracle(FixtureTask fixture) {
        return (task, voucher) -> {
            assertEquals(fixture.id(), task.id());
            if (fixture.requiredOperators().isEmpty()) {
                return new Score(1.0D, true);
            }
            EnumSet<Operator> active = EnumSet.noneOf(Operator.class);
            if (!voucher.invariantSystemPrompt().corePrinciples().contains("\n")) {
                active.add(Operator.CONCISE_SYSTEM_PROMPT);
            }
            if (voucher.memoryContext().shortTermWindowSize() < 20) {
                active.add(Operator.ADAPTIVE_MEMORY_WINDOW);
            }
            long satisfied = fixture.requiredOperators().stream().filter(active::contains).count();
            return new Score((double) satisfied / fixture.requiredOperators().size(), true);
        };
    }

    private static Benchmark benchmark() throws IOException {
        try (var stream = TaskLevelRecursivePpcTest.class
                .getResourceAsStream("/harness12/benchmark.json")) {
            assertNotNull(stream);
            JSONObject root = JSON.parseObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            JSONArray rows = root.getJSONArray("tasks");
            List<FixtureTask> tasks = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                List<Operator> required = row.getJSONArray("requiredOperators").stream()
                        .map(value -> Operator.valueOf(value.toString())).toList();
                tasks.add(new FixtureTask(row.getString("id"), row.getString("split"),
                        row.getString("category"), row.getString("question"), required));
            }
            return new Benchmark(root.getString("mode"), root.getString("benchmarkId"),
                    root.getString("cue"), tasks);
        }
    }

    private record Benchmark(String mode, String id, String cue, List<FixtureTask> tasks) {}

    private record FixtureTask(String id, String split, String category, String question,
                               List<Operator> requiredOperators) {}
}

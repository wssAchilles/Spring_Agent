package tech.qiantong.qknow.hermes.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RAGCheckerTest {

    @Mock
    private ChatModelFactory chatModelFactory;
    @Mock
    private ChatModel chatModel;
    @Mock
    private RagasEvalConfig config;

    private RAGChecker checker;

    @BeforeEach
    void setUp() {
        lenientConfig();
        checker = new RAGChecker(chatModelFactory, config);
    }

    @Test
    void evaluate_preservesClaimCountsAndComputesRates() {
        useChatModel(prompt -> {
            if (prompt.contains("Extract all factual claims")) {
                return "[\"supported\",\"wrong\",\"missing\"]";
            }
            if (prompt.contains("Claim: supported")) return "ENTAILED";
            if (prompt.contains("Claim: wrong")) return "CONTRADICTED";
            return "NOT_FOUND";
        });

        RAGChecker.RAGCheckerReport report = checker.evaluate("q", "answer", List.of("context"));

        assertEquals(EvaluationStatus.VALID, report.getStatus());
        assertEquals(3, report.getTotalClaims());
        assertEquals(1, report.getEntailedClaims());
        assertEquals(1, report.getContradictedClaims());
        assertEquals(1, report.getNotFoundClaims());
        assertEquals(1.0 / 3.0, report.getEntailedRate(), 0.001);
        assertEquals(1.0 / 3.0, report.getContradictedRate(), 0.001);
        assertEquals(1.0 / 3.0, report.getNotFoundRate(), 0.001);
    }

    @Test
    void evaluate_emptyClaims_isInvalidInsteadOfPerfect() {
        useChatModel(prompt -> "[]");

        RAGChecker.RAGCheckerReport report = checker.evaluate("q", "answer", List.of("context"));

        assertEquals(EvaluationStatus.INVALID, report.getStatus());
        assertEquals("NO_CLAIMS_EXTRACTED", report.getErrorCode());
        assertEquals("Claim extraction returned no claims", report.getReason());
        assertEquals(0, report.getTotalClaims());
        assertEquals(0.0, report.getEntailedRate());
    }

    @Test
    void evaluate_claimExtractionModelFailure_isInvalidAndSanitized() {
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("secret prompt answer api-key"));

        RAGChecker.RAGCheckerReport report = checker.evaluate("q", "answer", List.of("context"));

        assertEquals(EvaluationStatus.INVALID, report.getStatus());
        assertEquals("CLAIM_EXTRACTION_MODEL_FAILED", report.getErrorCode());
        assertEquals("Claim extraction model call failed", report.getReason());
        assertFalse(report.getReason().contains("secret"));
    }

    @Test
    void evaluate_claimExtractionParseFailure_isInvalid() {
        useChatModel(prompt -> "not-json");

        RAGChecker.RAGCheckerReport report = checker.evaluate("q", "answer", List.of("context"));

        assertEquals(EvaluationStatus.INVALID, report.getStatus());
        assertEquals("CLAIM_EXTRACTION_PARSE_FAILED", report.getErrorCode());
        assertEquals("Claim extraction response could not be parsed", report.getReason());
    }

    @Test
    void evaluate_entailmentFailure_isInvalidNotNotFound() {
        when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, Prompt.class).toString();
            if (prompt.contains("Extract all factual claims")) {
                return response("[\"claim\"]");
            }
            throw new IllegalStateException("secret judge failure");
        });

        RAGChecker.RAGCheckerReport report = checker.evaluate("q", "answer", List.of("context"));

        assertEquals(EvaluationStatus.INVALID, report.getStatus());
        assertEquals("ENTAILMENT_MODEL_FAILED", report.getErrorCode());
        assertEquals("Entailment model call failed", report.getReason());
        assertEquals(0, report.getNotFoundClaims());
    }

    @Test
    void evaluate_entailmentWithExtraText_isParseFailure() {
        useChatModel(prompt -> prompt.contains("Extract all factual claims")
                ? "[\"claim\"]" : "The result is ENTAILED");

        RAGChecker.RAGCheckerReport report = checker.evaluate("q", "answer", List.of("context"));

        assertEquals(EvaluationStatus.INVALID, report.getStatus());
        assertEquals("ENTAILMENT_PARSE_FAILED", report.getErrorCode());
        assertEquals(0, report.getEntailedClaims());
    }

    @Test
    void evaluate_claimExtractionAndEntailmentsShareOneTimeoutBudget() {
        checker = new RAGChecker(chatModelFactory, config, 40, TimeUnit.MILLISECONDS);
        CountDownLatch interrupted = new CountDownLatch(1);
        useChatModel(prompt -> {
            if (prompt.contains("Extract all factual claims")) {
                return "[\"one\",\"two\",\"three\"]";
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return "ENTAILED";
        });
        long started = System.nanoTime();

        RAGChecker.RAGCheckerReport report = checker.evaluate("q", "answer", List.of("context"));

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue(elapsedMillis < 300, "timeout must be one total budget, not per claim");
        assertEquals(EvaluationStatus.INVALID, report.getStatus());
        assertEquals("EVAL_TIMEOUT", report.getErrorCode());
        try {
            assertTrue(interrupted.await(1, TimeUnit.SECONDS), "running entailment task must be interrupted");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("test interrupted");
        }
    }

    @Test
    void evaluate_claimExtractionUsesSameTimeoutBudget() {
        checker = new RAGChecker(chatModelFactory, config, 30, TimeUnit.MILLISECONDS);
        useChatModel(prompt -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "[]";
        });

        RAGChecker.RAGCheckerReport report = checker.evaluate("q", "answer", List.of("context"));

        assertEquals(EvaluationStatus.INVALID, report.getStatus());
        assertEquals("EVAL_TIMEOUT", report.getErrorCode());
    }

    @Test
    void evaluate_interruptedWait_restoresInterruptAndMarksInvalid() {
        checker = new RAGChecker(chatModelFactory, config, 1, TimeUnit.SECONDS);
        useChatModel(prompt -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "[]";
        });
        Thread.currentThread().interrupt();

        try {
            RAGChecker.RAGCheckerReport report = checker.evaluate("q", "answer", List.of("context"));

            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(EvaluationStatus.INVALID, report.getStatus());
            assertEquals("EVALUATION_INTERRUPTED", report.getErrorCode());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void batchEvaluate_preservesUnexpectedFailuresAsInvalidReports() {
        RAGChecker throwingChecker = new RAGChecker(null, null) {
            @Override
            public RAGCheckerReport evaluate(String query, String answer, List<String> contexts) {
                if ("bad".equals(query)) {
                    throw new IllegalStateException("secret failure");
                }
                RAGCheckerReport report = new RAGCheckerReport();
                report.setQuery(query);
                report.setAnswer(answer);
                report.setStatus(EvaluationStatus.VALID);
                return report;
            }
        };
        RAGChecker.EvalSample good = sample("good", "good-answer");
        RAGChecker.EvalSample bad = sample("bad", "bad-answer");

        List<RAGChecker.RAGCheckerReport> reports = throwingChecker.batchEvaluate(List.of(good, bad));

        assertEquals(2, reports.size());
        RAGChecker.RAGCheckerReport failed = reports.get(1);
        assertEquals("bad", failed.getQuery());
        assertEquals("bad-answer", failed.getAnswer());
        assertEquals(EvaluationStatus.INVALID, failed.getStatus());
        assertEquals("ITEM_EVALUATION_FAILED", failed.getErrorCode());
        assertEquals("Evaluation item failed", failed.getReason());
    }

    @Test
    void summarize_onlyAggregatesValidReports() {
        RAGChecker.RAGCheckerReport valid = new RAGChecker.RAGCheckerReport();
        valid.setStatus(EvaluationStatus.VALID);
        valid.setTotalClaims(2);
        valid.setEntailedClaims(1);
        valid.setContradictedClaims(1);
        valid.setEntailedRate(0.5);
        valid.setContradictedRate(0.5);

        RAGChecker.RAGCheckerReport invalid = new RAGChecker.RAGCheckerReport();
        invalid.setStatus(EvaluationStatus.INVALID);
        invalid.setTotalClaims(100);
        invalid.setEntailedClaims(100);
        invalid.setEntailedRate(1.0);

        RAGChecker.RAGCheckerSummary summary = checker.summarize(List.of(valid, invalid));

        assertEquals(2, summary.getTotalSamples());
        assertEquals(1, summary.getValidSamples());
        assertEquals(1, summary.getInvalidSamples());
        assertEquals(0.5, summary.getAvgEntailedRate());
        assertEquals(0.5, summary.getAvgContradictedRate());
        assertEquals(0.0, summary.getAvgNotFoundRate());
        assertEquals(2, summary.getTotalClaims());
        assertEquals(1, summary.getTotalEntailed());
        assertEquals(1, summary.getTotalContradicted());
        assertEquals(0, summary.getTotalNotFound());
    }

    private void lenientConfig() {
        org.mockito.Mockito.lenient().when(config.getPlatform()).thenReturn("test");
        org.mockito.Mockito.lenient().when(config.getBaseUrl()).thenReturn(null);
        org.mockito.Mockito.lenient().when(config.getApiKey()).thenReturn(null);
        org.mockito.Mockito.lenient().when(config.getModelName()).thenReturn("test-model");
    }

    private void useChatModel(java.util.function.Function<String, String> responder) {
        org.mockito.Mockito.lenient().when(chatModelFactory.getChatModel(anyString(), isNull(), isNull(), anyString()))
                .thenReturn(chatModel);
        org.mockito.Mockito.lenient().when(chatModel.call(any(Prompt.class))).thenAnswer(invocation ->
                response(responder.apply(invocation.getArgument(0, Prompt.class).toString())));
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private RAGChecker.EvalSample sample(String query, String answer) {
        RAGChecker.EvalSample sample = new RAGChecker.EvalSample();
        sample.setQuery(query);
        sample.setAnswer(answer);
        sample.setContexts(List.of("context"));
        return sample;
    }
}

package tech.qiantong.qknow.rag.eval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowContractsTest {

    private ExecutorService executor;

    @AfterEach
    void stopExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void featureHashEmbeddingIsDeterministicNormalizedFiniteAndTextSensitive() throws Exception {
        assertEquals("feature-hash-v1", FeatureHashEmbeddingModel.VERSION);
        assertArrayEquals(
                new Class<?>[]{int.class, long.class, String.class},
                FeatureHashEmbeddingModel.class.getDeclaredConstructors()[0].getParameterTypes());

        FeatureHashEmbeddingModel firstModel = new FeatureHashEmbeddingModel(32, 17L,
                FeatureHashEmbeddingModel.VERSION);
        FeatureHashEmbeddingModel secondModel = new FeatureHashEmbeddingModel(32, 17L,
                FeatureHashEmbeddingModel.VERSION);

        float[] first = firstModel.embed("alpha beta");
        float[] repeated = secondModel.embed("alpha beta");
        float[] different = secondModel.embed("alpha gamma");

        assertArrayEquals(first, repeated, 0.0f);
        assertFalse(Arrays.equals(first, different));
        assertEquals(1.0, norm(first), 0.000001);
        for (float value : first) {
            assertTrue(Float.isFinite(value));
        }
    }

    @Test
    void featureHashEmbeddingPreservesBatchOrderAndRecordsInputsReadOnly() {
        FeatureHashEmbeddingModel model = new FeatureHashEmbeddingModel(32, 23L,
                FeatureHashEmbeddingModel.VERSION);

        List<float[]> batch = model.embed(List.of("beta", "alpha"));

        FeatureHashEmbeddingModel comparison = new FeatureHashEmbeddingModel(32, 23L,
                FeatureHashEmbeddingModel.VERSION);
        assertArrayEquals(comparison.embed("beta"), batch.get(0), 0.0f);
        assertArrayEquals(comparison.embed("alpha"), batch.get(1), 0.0f);
        assertEquals(List.of("beta", "alpha"), model.recordedInputs());
        assertThrows(UnsupportedOperationException.class, () -> model.recordedInputs().add("leak"));
    }

    @Test
    void featureHashEmbeddingPreservesEnglishTextSimilarity() {
        FeatureHashEmbeddingModel model = new FeatureHashEmbeddingModel(512, 29L,
                FeatureHashEmbeddingModel.VERSION);

        float[] query = model.embed("postgres vector retrieval");
        float[] related = model.embed("vector retrieval from a postgres database");
        float[] unrelated = model.embed("baking sourdough bread in a kitchen");

        assertTrue(cosine(query, related) > cosine(query, unrelated));
    }

    @Test
    void featureHashEmbeddingPreservesChineseTextSimilarity() {
        FeatureHashEmbeddingModel model = new FeatureHashEmbeddingModel(512, 31L,
                FeatureHashEmbeddingModel.VERSION);

        float[] query = model.embed("向量检索数据库");
        float[] related = model.embed("数据库向量检索系统");
        float[] unrelated = model.embed("周末天气晴朗适合散步");

        assertTrue(cosine(query, related) > cosine(query, unrelated));
    }

    @Test
    void sha256UsesLowercaseJdkHex() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ShadowContractSupport.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void datasetHashCoversFixedNamedResourcesInOrder() throws Exception {
        List<String> resources = List.of(
                "/rag-eval/corpus.jsonl",
                "/rag-eval/queries.jsonl",
                "/rag-eval/qrels.tsv");
        assertEquals(resources, ShadowContractSupport.DATASET_RESOURCES);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String resource : resources) {
            digest.update(resource.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = ShadowContractsTest.class.getResourceAsStream(resource)) {
                assertTrue(input != null, "missing test resource " + resource);
                digest.update(input.readAllBytes());
            }
            digest.update((byte) 0);
        }

        assertEquals(HexFormat.of().formatHex(digest.digest()), ShadowContractSupport.datasetHash());
    }

    @Test
    void configHashSortsNestedMapKeysButDetectsValueChanges() {
        Map<String, Object> firstInner = new LinkedHashMap<>();
        firstInner.put("z", 2);
        firstInner.put("a", List.of("x", "y"));
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("beta", firstInner);
        first.put("alpha", true);

        Map<String, Object> secondInner = new LinkedHashMap<>();
        secondInner.put("a", List.of("x", "y"));
        secondInner.put("z", 2);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("alpha", true);
        second.put("beta", secondInner);

        assertEquals(ShadowContractSupport.configHash(first), ShadowContractSupport.configHash(second));
        secondInner.put("z", 3);
        assertNotEquals(ShadowContractSupport.configHash(first), ShadowContractSupport.configHash(second));
    }

    @Test
    void sentinelChecksRunInOrderAndStopAtFirstFalse() {
        List<String> calls = new ArrayList<>();
        AtomicBoolean laterRan = new AtomicBoolean();
        SentinelPreflight.Result result = preflight(Duration.ofSeconds(1)).run(List.of(
                new SentinelPreflight.NamedCheck("first", () -> {
                    calls.add("first");
                    return true;
                }),
                new SentinelPreflight.NamedCheck("second", () -> {
                    calls.add("second");
                    return false;
                }),
                new SentinelPreflight.NamedCheck("later", () -> {
                    laterRan.set(true);
                    return true;
                })
        ));

        assertFalse(result.valid());
        assertEquals("second", result.failedCheck());
        assertEquals(RagBenchmarkReport.MetricErrorCode.SUT_SENTINEL_FAILED, result.errorCode());
        assertEquals(List.of("first", "second"), calls);
        assertFalse(laterRan.get());
    }

    @Test
    void sentinelExceptionReturnsOneInvalidResultAndStops() {
        AtomicBoolean laterRan = new AtomicBoolean();
        SentinelPreflight.Result result = preflight(Duration.ofSeconds(1)).run(List.of(
                new SentinelPreflight.NamedCheck("throws", () -> {
                    throw new IllegalStateException("boom");
                }),
                new SentinelPreflight.NamedCheck("later", () -> {
                    laterRan.set(true);
                    return true;
                })
        ));

        assertFalse(result.valid());
        assertEquals("throws", result.failedCheck());
        assertEquals(RagBenchmarkReport.MetricErrorCode.SUT_SENTINEL_FAILED, result.errorCode());
        assertFalse(laterRan.get());
    }

    @Test
    void sentinelTimeoutCancelsFailedTaskAndStops() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch allowBlocking = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean laterRan = new AtomicBoolean();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<SentinelPreflight.Result> resultFuture = caller.submit(() ->
                    preflight(Duration.ofSeconds(1)).run(List.of(
                            new SentinelPreflight.NamedCheck("slow", () -> {
                                started.countDown();
                                allowBlocking.await();
                                try {
                                    blocked.await();
                                    return true;
                                } catch (InterruptedException e) {
                                    interrupted.countDown();
                                    throw e;
                                }
                            }),
                            new SentinelPreflight.NamedCheck("later", () -> {
                                laterRan.set(true);
                                return true;
                            })
                    )));

            assertTrue(started.await(1, TimeUnit.SECONDS));
            allowBlocking.countDown();
            SentinelPreflight.Result result = resultFuture.get(2, TimeUnit.SECONDS);

            assertFalse(result.valid());
            assertEquals("slow", result.failedCheck());
            assertEquals(RagBenchmarkReport.MetricErrorCode.SUT_SENTINEL_FAILED, result.errorCode());
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            assertFalse(laterRan.get());
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    void sentinelAllPassingReturnsValid() {
        List<String> calls = new ArrayList<>();
        SentinelPreflight.Result result = preflight(Duration.ofSeconds(1)).run(List.of(
                new SentinelPreflight.NamedCheck("first", () -> calls.add("first")),
                new SentinelPreflight.NamedCheck("second", () -> calls.add("second"))
        ));

        assertTrue(result.valid());
        assertNull(result.failedCheck());
        assertNull(result.errorCode());
        assertEquals(List.of("first", "second"), calls);
    }

    @Test
    void snapshotJsonHasOnlyStableHashesAndQualityMetricsWithNullInvalidValues() {
        RagBenchmarkReport.MetricEstimate valid = RagBenchmarkReport.MetricEstimate.valid(0.75, 0.70, 0.80, 3);
        RagBenchmarkReport.MetricEstimate invalid = RagBenchmarkReport.MetricEstimate.invalid(
                RagBenchmarkReport.MetricErrorCode.SUT_SENTINEL_FAILED);
        RagBenchmarkReport.MetricEstimate notEvaluated = RagBenchmarkReport.MetricEstimate.notEvaluated();
        Map<String, RagBenchmarkReport.MetricEstimate> firstMetrics = new LinkedHashMap<>();
        firstMetrics.put("valid", valid);
        firstMetrics.put("invalid", invalid);
        firstMetrics.put("notEvaluated", notEvaluated);
        Map<String, RagBenchmarkReport.MetricEstimate> secondMetrics = new LinkedHashMap<>();
        secondMetrics.put("notEvaluated", notEvaluated);
        secondMetrics.put("invalid", invalid);
        secondMetrics.put("valid", valid);

        String firstJson = ShadowContractSupport.snapshotJson(report(firstMetrics));
        String secondJson = ShadowContractSupport.snapshotJson(report(secondMetrics));
        JSONObject snapshot = JSON.parseObject(firstJson);

        assertEquals(firstJson, secondJson);
        assertEquals(Set.of("datasetHash", "configHash", "metrics"), snapshot.keySet());
        assertTrue(snapshot.getJSONObject("metrics").getJSONObject("invalid").containsKey("value"));
        assertNull(snapshot.getJSONObject("metrics").getJSONObject("invalid").get("value"));
        assertTrue(snapshot.getJSONObject("metrics").getJSONObject("notEvaluated").containsKey("value"));
        assertNull(snapshot.getJSONObject("metrics").getJSONObject("notEvaluated").get("value"));
    }

    private SentinelPreflight preflight(Duration timeout) {
        executor = Executors.newSingleThreadExecutor();
        return new SentinelPreflight(executor, timeout);
    }

    private static RagBenchmarkReport report(Map<String, RagBenchmarkReport.MetricEstimate> metrics) {
        return new RagBenchmarkReport(
                "dataset-hash",
                "config-hash",
                RagBenchmarkReport.DatasetEvidenceLevel.ENGINEERING_BASELINE,
                metrics,
                Map.of("ignored", metrics),
                List.of(new RagBenchmarkReport.FailureSample("q", "family", "error", "detail")));
    }

    private static double norm(float[] values) {
        double sum = 0.0;
        for (float value : values) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private static double cosine(float[] left, float[] right) {
        double product = 0.0;
        for (int i = 0; i < left.length; i++) {
            product += left[i] * right[i];
        }
        return product;
    }
}

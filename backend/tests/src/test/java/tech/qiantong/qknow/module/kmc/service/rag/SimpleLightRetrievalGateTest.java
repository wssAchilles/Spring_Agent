package tech.qiantong.qknow.module.kmc.service.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.common.core.utils.SecurityUtils;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * H2 A0 vs A1 on frozen short-query slice.
 * A0: SIMPLE → empty context (baseline).
 * A1: SIMPLE → lightweight keyword retrieval topK=5, zero LLM.
 *
 * Enable: -Dqknow.rag.simple.light.ablation=true
 */
@EnabledIfSystemProperty(named = "qknow.rag.simple.light.ablation", matches = "true")
class SimpleLightRetrievalGateTest {

    private static final int TOP_K = 5;

    @Test
    void ablatesEmptySimpleVersusLightweightKeyword() throws Exception {
        List<ShortCase> cases = loadCases();
        assertFalse(cases.isEmpty(), "SHORT_SLICE_NOT_FROZEN");

        String jdbcUrl = System.getProperty("qknow.rag.simple.light.ablation.jdbc.url",
                "jdbc:postgresql://127.0.0.1:5432/ai_agent");
        String user = System.getProperty("qknow.rag.simple.light.ablation.jdbc.user", "achilles");
        String password = System.getProperty("qknow.rag.simple.light.ablation.jdbc.password",
                System.getenv().getOrDefault("POSTGRESQL_PASSWORD", ""));

        JdbcTemplate jdbc = new JdbcTemplate(new SimpleDriverDataSource(
                new org.postgresql.Driver(), jdbcUrl, user, password));
        KeywordRetriever retriever = new KeywordRetriever();
        ReflectionTestUtils.setField(retriever, "jdbcTemplate", jdbc);
        ReflectionTestUtils.setField(retriever, "identifierAware", false);

        QueryRouter.QueryRouterConfig cfg = new QueryRouter.QueryRouterConfig();
        cfg.setEnabled(true);
        QueryRouter router = new QueryRouter(null, cfg);

        PermissionFilter permissionFilter = mock(PermissionFilter.class);
        when(permissionFilter.getAccessibleKnowledgeBaseIds(any())).thenReturn(null);

        RagContextBuilder contextBuilder = mock(RagContextBuilder.class);
        when(contextBuilder.buildContext(anyList(), anyBoolean())).thenAnswer(inv -> {
            List<?> list = inv.getArgument(0);
            return list.stream()
                    .map(r -> String.valueOf(((RetrievalResult) r).getContent()))
                    .collect(Collectors.joining("\n"));
        });

        RagRetrievalService service = new RagRetrievalService();
        ReflectionTestUtils.setField(service, "queryRouter", router);
        ReflectionTestUtils.setField(service, "keywordRetriever", retriever);
        ReflectionTestUtils.setField(service, "permissionFilter", permissionFilter);
        ReflectionTestUtils.setField(service, "ragContextBuilder", contextBuilder);
        ReflectionTestUtils.setField(service, "simpleLightTopK", TOP_K);

        for (ShortCase c : cases) {
            assertEquals(QueryRouter.QueryRoute.SIMPLE, router.classify(c.query()),
                    "slice query not SIMPLE: " + c.query());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int a0Hits = 0;
        int a1Hits = 0;
        int a0Hit5 = 0;
        int a1Hit5 = 0;
        long a0Ms = 0;
        long a1Ms = 0;

        for (ShortCase item : cases) {
            ReflectionTestUtils.setField(service, "simpleLightRetrieval", false);
            long t0 = System.nanoTime();
            RagResult a0;
            try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
                sec.when(SecurityUtils::getUserId).thenReturn(1L);
                a0 = service.retrieve(8L, item.query(), 10, false);
            }
            a0Ms += (System.nanoTime() - t0) / 1_000_000L;
            List<String> a0Names = a0.getSources().stream().map(RetrievalResult::getDocumentName).toList();
            if (hit(a0Names, item.expectedSources(), 10)) a0Hits++;
            if (hit(a0Names, item.expectedSources(), 5)) a0Hit5++;

            ReflectionTestUtils.setField(service, "simpleLightRetrieval", true);
            long t1 = System.nanoTime();
            RagResult a1;
            try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
                sec.when(SecurityUtils::getUserId).thenReturn(1L);
                a1 = service.retrieve(8L, item.query(), 10, false);
            }
            a1Ms += (System.nanoTime() - t1) / 1_000_000L;
            List<String> a1Names = a1.getSources().stream().map(RetrievalResult::getDocumentName).toList();
            if (hit(a1Names, item.expectedSources(), 10)) a1Hits++;
            if (hit(a1Names, item.expectedSources(), 5)) a1Hit5++;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.id());
            row.put("query", item.query());
            row.put("expectedSources", item.expectedSources());
            row.put("a0Sources", a0Names);
            row.put("a1Sources", a1Names);
            row.put("a0HitAt10", hit(a0Names, item.expectedSources(), 10));
            row.put("a1HitAt10", hit(a1Names, item.expectedSources(), 10));
            rows.add(row);
        }

        int n = cases.size();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("hypothesis", "H2");
        report.put("evidenceLevel", "LIVE_KEYWORD_ABLATION");
        report.put("slice", "rag-short-query-slice-v1.jsonl");
        report.put("caseCount", n);
        report.put("topK", TOP_K);
        report.put("A0", Map.of(
                "hitAt5", round(a0Hit5 / (double) n),
                "hitAt10", round(a0Hits / (double) n),
                "avgMs", a0Ms / n));
        report.put("A1", Map.of(
                "hitAt5", round(a1Hit5 / (double) n),
                "hitAt10", round(a1Hits / (double) n),
                "avgMs", a1Ms / n));
        report.put("deltaHitAt10", round((a1Hits - a0Hits) / (double) n));
        report.put("deltaHitAt5", round((a1Hit5 - a0Hit5) / (double) n));
        report.put("llmCallsInSimple", 0);
        report.put("cases", rows);
        report.put("notes", List.of(
                "A0 = SIMPLE empty context (baseline).",
                "A1 = SIMPLE lightweight keyword retrieval topK=5, zero LLM.",
                "Short slice frozen before run; existing golden qrels untouched."));

        Path outDir = resolveEvidenceDirectory();
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("h2-a0-a1-short-slice-report.json");
        Files.writeString(outFile, JSON.toJSONString(report, JSONWriter.Feature.PrettyFormat),
                StandardCharsets.UTF_8);

        int minGain = Math.max(1, (int) Math.ceil(0.20 * n));
        assertTrue(a1Hits >= a0Hits + minGain,
                "METRICS_REGRESSION a1Hits=" + a1Hits + " a0Hits=" + a0Hits + " need+ " + minGain);
        assertTrue(Files.isRegularFile(outFile));
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static boolean hit(List<String> ranking, List<String> expected, int k) {
        List<String> top = ranking.subList(0, Math.min(k, ranking.size()));
        return top.stream().anyMatch(expected::contains);
    }

    private static List<ShortCase> loadCases() throws Exception {
        List<ShortCase> cases = new ArrayList<>();
        try (InputStream in = SimpleLightRetrievalGateTest.class
                .getResourceAsStream("/rag-short-query-slice-v1.jsonl")) {
            assertNotNull(in, "SHORT_SLICE_NOT_FROZEN");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                var json = JSON.parseObject(line);
                cases.add(new ShortCase(
                        json.getString("id"),
                        json.getString("query"),
                        json.getJSONArray("expectedSources").toList(String.class)));
            }
        }
        return cases;
    }

    private static Path resolveEvidenceDirectory() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path name = cwd.getFileName();
        String leaf = name == null ? "" : name.toString();
        if (leaf.equals("tests") && cwd.getParent() != null
                && "backend".equals(String.valueOf(cwd.getParent().getFileName()))) {
            return cwd.resolve("evidence/h2-simple-light-retrieval");
        }
        if (leaf.equals("backend")) {
            return cwd.resolve("tests/evidence/h2-simple-light-retrieval");
        }
        return cwd.resolve("backend/tests/evidence/h2-simple-light-retrieval");
    }

    private record ShortCase(String id, String query, List<String> expectedSources) {
    }
}

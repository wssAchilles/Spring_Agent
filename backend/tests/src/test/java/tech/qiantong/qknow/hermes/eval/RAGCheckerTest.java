package tech.qiantong.qknow.hermes.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RAGCheckerTest {

    @Test
    @DisplayName("空answer返回默认分数")
    void evaluate_withEmptyAnswer_returnsDefaultScores() {
        RAGChecker.RAGCheckerReport report = new RAGChecker.RAGCheckerReport();
        report.setQuery("Q");
        report.setAnswer("");
        report.setTotalClaims(0);
        report.setPrecision(1.0);
        report.setRecall(1.0);
        report.setF1(1.0);

        assertEquals(0, report.getTotalClaims());
        assertEquals(1.0, report.getPrecision());
    }

    @Test
    @DisplayName("批量评估汇总统计正确")
    void summarize_computesCorrectAverages() {
        RAGChecker checker = new RAGChecker(null, null);

        RAGChecker.RAGCheckerReport r1 = new RAGChecker.RAGCheckerReport();
        r1.setPrecision(0.8);
        r1.setRecall(0.6);
        r1.setF1(0.69);
        r1.setHallucination(0.2);
        r1.setTotalClaims(5);
        r1.setEntailedClaims(4);

        RAGChecker.RAGCheckerReport r2 = new RAGChecker.RAGCheckerReport();
        r2.setPrecision(0.9);
        r2.setRecall(0.7);
        r2.setF1(0.79);
        r2.setHallucination(0.1);
        r2.setTotalClaims(3);
        r2.setEntailedClaims(2);

        RAGChecker.RAGCheckerSummary summary = checker.summarize(List.of(r1, r2));

        assertEquals(2, summary.getTotalSamples());
        assertEquals(0.85, summary.getAvgPrecision(), 0.01);
        assertEquals(0.65, summary.getAvgRecall(), 0.01);
        assertEquals(8, summary.getTotalClaims());
        assertEquals(6, summary.getTotalEntailed());
    }

    @Test
    @DisplayName("空列表汇总返回默认值")
    void summarize_withEmptyList_returnsDefaults() {
        RAGChecker checker = new RAGChecker(null, null);
        RAGChecker.RAGCheckerSummary summary = checker.summarize(List.of());

        assertEquals(0, summary.getTotalSamples());
        assertEquals(0.0, summary.getAvgPrecision());
    }

    @Test
    @DisplayName("Report数据类getter/setter正确")
    void report_gettersSetters_work() {
        RAGChecker.RAGCheckerReport report = new RAGChecker.RAGCheckerReport();
        report.setQuery("test query");
        report.setAnswer("test answer");
        report.setTotalClaims(10);
        report.setEntailedClaims(8);
        report.setContradictedClaims(1);
        report.setNotFoundClaims(1);
        report.setPrecision(0.8);
        report.setRecall(0.7);
        report.setF1(0.75);
        report.setHallucination(0.2);

        assertEquals("test query", report.getQuery());
        assertEquals("test answer", report.getAnswer());
        assertEquals(10, report.getTotalClaims());
        assertEquals(8, report.getEntailedClaims());
        assertEquals(1, report.getContradictedClaims());
        assertEquals(1, report.getNotFoundClaims());
        assertEquals(0.8, report.getPrecision());
        assertEquals(0.7, report.getRecall());
        assertEquals(0.75, report.getF1());
        assertEquals(0.2, report.getHallucination());
    }
}

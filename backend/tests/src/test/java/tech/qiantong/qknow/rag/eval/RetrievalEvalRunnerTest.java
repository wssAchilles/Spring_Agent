package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.rag.LiveRetrievalMetrics;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RetrievalEvalRunnerTest {

    @Test
    @DisplayName("answerable case: hit@10 / mrr 与手工算例一致")
    void scoreAnswerableMatchesManual() {
        var in = new RetrievalEvalRunner.CaseInput(
                "c1", "q", "medium", true,
                List.of("Day01"),
                List.of("n1", "n2", "n3", "n4", "n5", "n6", "Day01.md"));
        var out = RetrievalEvalRunner.score(in);
        assertEquals(0.0, out.hitAt5(), 1e-9);
        assertEquals(1.0, out.hitAt10(), 1e-9);
        assertEquals(1.0 / 7.0, out.mrrAt10(), 1e-6);
    }

    @Test
    @DisplayName("negative case: 空 expected 不计 Hit，FPR 单独统计")
    void negativeCaseFpr() {
        var negMiss = new RetrievalEvalRunner.CaseInput(
                "n1", "q", "negative", false, List.of(), List.of("噪声.pdf"));
        var negHit = new RetrievalEvalRunner.CaseInput(
                "n2", "q", "negative", false, List.of(), List.of("人工智能.pdf"));
        // FPR: 若 retrieved 非空且 answerable=false，视为“不该给证据却给了”
        // 契约：negativeFpRate = 非空 retrieved 占比
        var report = RetrievalEvalRunner.aggregate(List.of(negMiss, negHit));
        assertEquals(2, report.negativeCount());
        assertEquals(1.0, report.negativeFpRate(), 1e-9);
    }

    @Test
    @DisplayName("分层宏平均：不被 medium 体量淹没 short")
    void stratumMacroAverage() {
        // short: 1/1 hit10=1.0; medium: 0/3 hit10=0 → overall 0.25, macro (1+0)/2=0.5
        var s1 = new RetrievalEvalRunner.CaseInput("s1", "q", "short", true,
                List.of("A"), List.of("A.pdf"));
        var m1 = new RetrievalEvalRunner.CaseInput("m1", "q", "medium", true,
                List.of("B"), List.of("x"));
        var m2 = new RetrievalEvalRunner.CaseInput("m2", "q", "medium", true,
                List.of("C"), List.of("y"));
        var m3 = new RetrievalEvalRunner.CaseInput("m3", "q", "medium", true,
                List.of("D"), List.of("z"));
        var report = RetrievalEvalRunner.aggregate(List.of(s1, m1, m2, m3));
        assertEquals(0.25, report.overall().hitAt10(), 1e-9);
        assertEquals(0.5, report.macroHitAt10(), 1e-9);
        assertEquals(1.0, report.byStratum().get("short").hitAt10(), 1e-9);
        assertEquals(0.0, report.byStratum().get("medium").hitAt10(), 1e-9);
    }

    @Test
    @DisplayName("paired bootstrap：A 优于 B 时 p 可检出方向")
    void pairedCompareDirection() {
        // 8 queries: A always 1, B always 0 → difference always 1
        double[] a = new double[8];
        double[] b = new double[8];
        java.util.Arrays.fill(a, 1.0);
        var cmp = RetrievalEvalRunner.pairedCompare(a, b, 42L);
        assertEquals(1.0, cmp.meanDiff(), 1e-9);
        assertTrue(cmp.ciLow() > 0);
    }

    @Test
    @DisplayName("空列表 aggregate 安全")
    void emptyAggregateSafe() {
        var report = RetrievalEvalRunner.aggregate(List.of());
        assertEquals(0, report.overall().n());
        assertEquals(0.0, report.macroHitAt10(), 1e-9);
    }

    @Test
    @DisplayName("LiveRetrievalMetrics 前缀匹配仍可用")
    void metricsPrefix() {
        var s = LiveRetrievalMetrics.score(List.of("Day01"), List.of("Day01.md"));
        assertEquals(1.0, s.hitAt10());
    }
}

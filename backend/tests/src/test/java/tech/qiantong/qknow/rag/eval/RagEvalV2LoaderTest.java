package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RagEvalV2LoaderTest {

    private static Map<String, Object> base() {
        return new java.util.LinkedHashMap<>(Map.of(
                "id", "eval-x-1",
                "query", "测试",
                "lang", "zh",
                "stratum", "medium",
                "split", "selection",
                "answerable", true,
                "kbId", 8,
                "expectedSources", List.of("人工智能.pdf")));
    }

    @Test
    @DisplayName("合法 v2 条目可通过")
    void validCasePasses() {
        var c = RagEvalV2Loader.validate(base(), "test");
        assertEquals("eval-x-1", c.id());
        assertTrue(c.answerable());
    }

    @Test
    @DisplayName("negative 允许空 expectedSources")
    void negativeAllowsEmptyExpected() {
        var m = base();
        m.put("stratum", "negative");
        m.put("answerable", false);
        m.put("expectedSources", List.of());
        var c = RagEvalV2Loader.validate(m, "test");
        assertFalse(c.answerable());
    }

    @Test
    @DisplayName("缺 id/query/split 拒绝")
    void missingRequiredRejected() {
        var m = base();
        m.remove("split");
        assertThrows(IllegalArgumentException.class, () -> RagEvalV2Loader.validate(m, "test"));
    }

    @Test
    @DisplayName("非法 stratum 拒绝")
    void badStratumRejected() {
        var m = base();
        m.put("stratum", "weird");
        assertThrows(IllegalArgumentException.class, () -> RagEvalV2Loader.validate(m, "test"));
    }

    @Test
    @DisplayName("answerable=true 时 expectedSources 不可为空")
    void answerableRequiresExpected() {
        var m = base();
        m.put("expectedSources", List.of());
        assertThrows(IllegalArgumentException.class, () -> RagEvalV2Loader.validate(m, "test"));
    }

    @Test
    @DisplayName("加载 classpath rag-eval-v2.jsonl（若存在）")
    void loadFromClasspath() throws Exception {
        List<RagEvalV2Loader.EvalCase> cases = RagEvalV2Loader.loadFromClasspath();
        if (cases.isEmpty()) {
            return; // dataset not yet present in this worktree snapshot
        }
        long ids = cases.stream().map(RagEvalV2Loader.EvalCase::id).distinct().count();
        assertEquals(cases.size(), ids);
    }
}

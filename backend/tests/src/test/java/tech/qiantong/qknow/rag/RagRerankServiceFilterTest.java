package tech.qiantong.qknow.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.RagRerankService;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RagRerankService 相关性过滤测试")
class RagRerankServiceFilterTest {

    @Test
    @DisplayName("全部过滤时回退到原始集")
    void filterIrrelevant_allFiltered_fallsBackToOriginal() throws Exception {
        RagRerankService service = new RagRerankService();

        List<RetrievalResult> candidates = List.of(
                buildResult("completely unrelated cooking recipe", 1.0),
                buildResult("another unrelated topic about sports", 0.9)
        );

        QueryIntent intent = new QueryIntent();
        intent.setKeywords(List.of("Day01", "项目"));

        Method method = RagRerankService.class.getDeclaredMethod("filterIrrelevant",
                String.class, List.class, QueryIntent.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<RetrievalResult> result = (List<RetrievalResult>) method.invoke(service,
                "Day01 的主要工作内容是什么？", candidates, intent);

        assertEquals(2, result.size(), "Should fall back to original when all filtered");
    }

    @Test
    @DisplayName("从 query 中提取中文和英文关键词")
    void extractKeywords_extractsChineseAndEnglish() throws Exception {
        RagRerankService service = new RagRerankService();

        Method method = RagRerankService.class.getDeclaredMethod("extractKeywords",
                String.class, QueryIntent.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) method.invoke(service,
                "Day01 项目架构熟悉与 Bug 排查", new QueryIntent());

        // Day01 不在中文 2-4 字范围内，但 query 中有 "项目", "架构", "排查"
        assertTrue(keywords.contains("项目"), "Should contain '项目': " + keywords);
        assertTrue(keywords.contains("架构"), "Should contain '架构': " + keywords);
        assertTrue(keywords.contains("排查"), "Should contain '排查': " + keywords);
        assertTrue(keywords.contains("bug"), "Should contain 'bug': " + keywords);
    }

    @Test
    @DisplayName("零关键词命中但高分数的 chunk 被保留")
    void filterIrrelevant_zeroHitsButHighScore_kept() throws Exception {
        RagRerankService service = new RagRerankService();

        // cooking chunk 分数很高（高于中位数*0.5），即使关键词零命中也保留
        List<RetrievalResult> candidates = List.of(
                buildResult("Day01 项目架构熟悉与Bug排查 工作日志", 1.0),
                buildResult("This is completely unrelated content about cooking recipes", 0.95),
                buildResult("Flutter GetX 状态管理框架 配置", 0.3)
        );

        QueryIntent intent = new QueryIntent();
        intent.setKeywords(List.of("Day01", "项目", "架构"));

        Method method = RagRerankService.class.getDeclaredMethod("filterIrrelevant",
                String.class, List.class, QueryIntent.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<RetrievalResult> result = (List<RetrievalResult>) method.invoke(service,
                "Day01 的主要工作内容是什么？", candidates, intent);

        // cooking chunk (0.95) 高于阈值 → 保留; Flutter chunk (0.3) 低于阈值且 0 命中 → 过滤
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("低分数且零命中的 chunk 被过滤")
    void filterIrrelevant_lowScoreAndZeroHits_filtered() throws Exception {
        RagRerankService service = new RagRerankService();

        List<RetrievalResult> candidates = List.of(
                buildResult("Day01 项目架构熟悉与Bug排查 工作日志", 1.0),
                buildResult("This is completely unrelated content about cooking", 0.1),
                buildResult("Flutter GetX 状态管理框架 配置", 0.05)
        );

        QueryIntent intent = new QueryIntent();
        intent.setKeywords(List.of("Day01", "项目", "架构"));

        Method method = RagRerankService.class.getDeclaredMethod("filterIrrelevant",
                String.class, List.class, QueryIntent.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<RetrievalResult> result = (List<RetrievalResult>) method.invoke(service,
                "Day01 的主要工作内容是什么？", candidates, intent);

        // median=0.1, threshold=0.075. cooking(0.1) >= threshold → 保留; Flutter(0.05) < threshold 且 0 命中 → 过滤
        assertEquals(2, result.size());
    }

    private RetrievalResult buildResult(String content, double score) {
        return RetrievalResult.builder()
                .content(content)
                .score(score)
                .segmentId(1L)
                .source("test")
                .build();
    }
}

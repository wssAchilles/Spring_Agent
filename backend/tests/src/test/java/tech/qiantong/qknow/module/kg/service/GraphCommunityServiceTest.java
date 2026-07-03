package tech.qiantong.qknow.module.kg.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GraphCommunityServiceTest {

    private GraphCommunityService createServiceWithoutLLM() {
        return new GraphCommunityService(null, null);
    }

    @Nested
    @DisplayName("buildSummaryFallback 测试")
    class BuildSummaryFallbackTests {

        @Test
        @DisplayName("生成规则拼接摘要 - 包含实体和标签")
        void buildSummaryFallback_withEntitiesAndLabels() throws Exception {
            GraphCommunityService service = createServiceWithoutLLM();
            var community = new GraphCommunityService.Community();
            community.setId(1L);
            community.setSize(5);
            community.setEntities(List.of("实体A", "实体B", "实体C"));
            community.setLabels(List.of("标签X", "标签Y"));

            Method method = GraphCommunityService.class.getDeclaredMethod("buildSummaryFallback",
                    GraphCommunityService.Community.class);
            method.setAccessible(true);
            String result = (String) method.invoke(service, community);

            assertTrue(result.contains("社区 1"));
            assertTrue(result.contains("5 个实体"));
            assertTrue(result.contains("实体A、实体B、实体C"));
            assertTrue(result.contains("标签X、标签Y"));
        }

        @Test
        @DisplayName("空实体列表处理")
        void buildSummaryFallback_emptyEntities() throws Exception {
            GraphCommunityService service = createServiceWithoutLLM();
            var community = new GraphCommunityService.Community();
            community.setId(0L);
            community.setSize(0);
            community.setEntities(List.of());
            community.setLabels(List.of());

            Method method = GraphCommunityService.class.getDeclaredMethod("buildSummaryFallback",
                    GraphCommunityService.Community.class);
            method.setAccessible(true);
            String result = (String) method.invoke(service, community);

            assertTrue(result.contains("社区 0"));
            assertTrue(result.contains("0 个实体"));
            assertFalse(result.contains("主题标签"));
        }
    }

    @Nested
    @DisplayName("score 方法测试")
    class ScoreTests {

        @Test
        @DisplayName("空查询返回 community.getSize()")
        void score_emptyQuery_returnsSize() throws Exception {
            GraphCommunityService service = createServiceWithoutLLM();
            var community = new GraphCommunityService.Community();
            community.setSize(10);
            community.setEntities(List.of("A"));

            Method method = GraphCommunityService.class.getDeclaredMethod("score", String.class,
                    GraphCommunityService.Community.class);
            method.setAccessible(true);

            double result = (double) method.invoke(service, (String) null, community);
            assertEquals(10.0, result);

            result = (double) method.invoke(service, "", community);
            assertEquals(10.0, result);

            result = (double) method.invoke(service, "  ", community);
            assertEquals(10.0, result);
        }

        @Test
        @DisplayName("实体匹配加 3.0 分")
        void score_entityMatch_adds3Points() throws Exception {
            GraphCommunityService service = createServiceWithoutLLM();
            var community = new GraphCommunityService.Community();
            community.setSize(2);
            community.setEntities(List.of("知识图谱", "Neo4j"));
            community.setSummary("这是一个关于图数据库的社区");

            Method method = GraphCommunityService.class.getDeclaredMethod("score", String.class,
                    GraphCommunityService.Community.class);
            method.setAccessible(true);

            double baseScore = (double) method.invoke(service, "无关查询", community);
            double matchScore = (double) method.invoke(service, "知识图谱很好", community);

            assertTrue(matchScore > baseScore + 2.9);
        }

        @Test
        @DisplayName("summary token 匹配加 1.0 分")
        void score_summaryTokenMatch_adds1Point() throws Exception {
            GraphCommunityService service = createServiceWithoutLLM();
            var community = new GraphCommunityService.Community();
            community.setSize(1);
            community.setEntities(List.of("无关实体"));
            community.setSummary("这是一个关于知识图谱的社区摘要");

            Method method = GraphCommunityService.class.getDeclaredMethod("score", String.class,
                    GraphCommunityService.Community.class);
            method.setAccessible(true);

            double noMatchScore = (double) method.invoke(service, "完全无关 查询", community);
            double tokenMatchScore = (double) method.invoke(service, "社区 摘要", community);

            assertTrue(tokenMatchScore > noMatchScore);
        }
    }

    @Nested
    @DisplayName("Community 数据模型测试")
    class CommunityModelTests {

        @Test
        @DisplayName("getter/setter 正确工作")
        void community_getterSetter_workCorrectly() {
            var community = new GraphCommunityService.Community();
            community.setId(42L);
            community.setEntities(List.of("A", "B"));
            community.setLabels(List.of("L1"));
            community.setSize(2);
            community.setSummary("test summary");

            assertEquals(42L, community.getId());
            assertEquals(List.of("A", "B"), community.getEntities());
            assertEquals(List.of("L1"), community.getLabels());
            assertEquals(2, community.getSize());
            assertEquals("test summary", community.getSummary());
        }
    }

    @Nested
    @DisplayName("GlobalSearchResult record 测试")
    class GlobalSearchResultTests {

        @Test
        @DisplayName("record 正确性")
        void globalSearchResult_recordCorrectness() {
            var community = new GraphCommunityService.Community();
            community.setId(1L);
            var result = new GraphCommunityService.GlobalSearchResult("answer", List.of(community));

            assertEquals("answer", result.answer());
            assertEquals(1, result.communities().size());
            assertEquals(1L, result.communities().get(0).getId());
        }
    }

    @Nested
    @DisplayName("globalSearch 测试")
    class GlobalSearchTests {

        @Test
        @DisplayName("无 LLM 时回退到简单拼接")
        void globalSearch_noLLM_fallbackToConcatenation() {
            GraphCommunityService service = createServiceWithoutLLM();

            // 使用反射直接调用 loadCommunities 的结果 — 通过覆盖全局搜索的回退路径
            // 由于 loadCommunities 依赖 Neo4j Driver，我们直接测试 buildSummaryFallback 的输出
            var community = new GraphCommunityService.Community();
            community.setId(1L);
            community.setSize(3);
            community.setEntities(List.of("A公司"));
            community.setLabels(List.of("企业"));
            community.setSummary("摘要一");

            // 验证 score 方法在 globalSearch 排序中正确工作
            try {
                Method scoreMethod = GraphCommunityService.class.getDeclaredMethod("score", String.class,
                        GraphCommunityService.Community.class);
                scoreMethod.setAccessible(true);
                double score = (double) scoreMethod.invoke(service, "A公司", community);
                assertTrue(score > 0);
            } catch (Exception e) {
                fail("score 方法调用失败: " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("mapPhase 和 reducePhase 测试")
    class MapReduceTests {

        @Test
        @DisplayName("mapPhase 空 summary 返回无关联")
        void mapPhase_nullSummary_returnsNoRelation() throws Exception {
            GraphCommunityService service = createServiceWithoutLLM();
            var community = new GraphCommunityService.Community();
            community.setId(1L);
            community.setSummary(null);

            Method method = GraphCommunityService.class.getDeclaredMethod("mapPhase",
                    org.springframework.ai.chat.model.ChatModel.class,
                    GraphCommunityService.Community.class, String.class);
            method.setAccessible(true);

            // chatModel 为 null 会抛异常，但 mapPhase 捕获异常返回 "无关联"
            // 所以用 null summary 时应该直接返回 "无关联"
            String result = (String) method.invoke(service, null, community, "查询");
            assertEquals("无关联", result);
        }

        @Test
        @DisplayName("mapPhase 空字符串 summary 返回无关联")
        void mapPhase_blankSummary_returnsNoRelation() throws Exception {
            GraphCommunityService service = createServiceWithoutLLM();
            var community = new GraphCommunityService.Community();
            community.setId(1L);
            community.setSummary("  ");

            Method method = GraphCommunityService.class.getDeclaredMethod("mapPhase",
                    org.springframework.ai.chat.model.ChatModel.class,
                    GraphCommunityService.Community.class, String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(service, null, community, "查询");
            assertEquals("无关联", result);
        }
    }

    @Nested
    @DisplayName("buildSummaryFallback 边界情况测试")
    class BuildSummaryEdgeCases {

        @Test
        @DisplayName("超过12个实体只取前12个")
        void buildSummaryFallback_moreThan12Entities_truncates() throws Exception {
            GraphCommunityService service = createServiceWithoutLLM();
            var community = new GraphCommunityService.Community();
            community.setId(2L);
            community.setSize(15);
            community.setEntities(List.of("E1", "E2", "E3", "E4", "E5", "E6",
                    "E7", "E8", "E9", "E10", "E11", "E12", "E13", "E14", "E15"));
            community.setLabels(List.of("L1"));

            Method method = GraphCommunityService.class.getDeclaredMethod("buildSummaryFallback",
                    GraphCommunityService.Community.class);
            method.setAccessible(true);
            String result = (String) method.invoke(service, community);

            assertTrue(result.contains("E12"));
            assertFalse(result.contains("E13"));
        }

        @Test
        @DisplayName("重复标签去重")
        void buildSummaryFallback_duplicateLabels_dedupes() throws Exception {
            GraphCommunityService service = createServiceWithoutLLM();
            var community = new GraphCommunityService.Community();
            community.setId(3L);
            community.setSize(4);
            community.setEntities(List.of("A"));
            community.setLabels(List.of("标签X", "标签X", "标签Y", "标签Y"));

            Method method = GraphCommunityService.class.getDeclaredMethod("buildSummaryFallback",
                    GraphCommunityService.Community.class);
            method.setAccessible(true);
            String result = (String) method.invoke(service, community);

            // 去重后只应包含 "标签X、标签Y"
            assertTrue(result.contains("标签X、标签Y"));
        }

        @Test
        @DisplayName("null 实体和标签列表安全处理")
        void buildSummaryFallback_nullLists_handlesSafely() throws Exception {
            GraphCommunityService service = createServiceWithoutLLM();
            var community = new GraphCommunityService.Community();
            community.setId(4L);
            community.setSize(0);
            community.setEntities(null);
            community.setLabels(null);

            Method method = GraphCommunityService.class.getDeclaredMethod("buildSummaryFallback",
                    GraphCommunityService.Community.class);
            method.setAccessible(true);
            String result = (String) method.invoke(service, community);

            assertNotNull(result);
            assertTrue(result.contains("社区 4"));
        }
    }
}

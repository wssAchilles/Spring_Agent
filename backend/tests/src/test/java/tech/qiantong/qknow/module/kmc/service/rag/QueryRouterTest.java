package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryRouterTest {

    @Test
    @DisplayName("空查询返回SIMPLE")
    void classify_withNullQuery_returnsSimple() {
        QueryRouter.QueryRouterConfig config = new QueryRouter.QueryRouterConfig();
        config.setEnabled(true);
        QueryRouter router = new QueryRouter(null, config);
        assertEquals(QueryRouter.QueryRoute.SIMPLE, router.classify(null));
    }

    @Test
    @DisplayName("空字符串返回SIMPLE")
    void classify_withBlankQuery_returnsSimple() {
        QueryRouter.QueryRouterConfig config = new QueryRouter.QueryRouterConfig();
        config.setEnabled(true);
        QueryRouter router = new QueryRouter(null, config);
        assertEquals(QueryRouter.QueryRoute.SIMPLE, router.classify("  "));
    }

    @Test
    @DisplayName("短查询返回SIMPLE")
    void classify_withShortQuery_returnsSimple() {
        QueryRouter.QueryRouterConfig config = new QueryRouter.QueryRouterConfig();
        config.setEnabled(true);
        QueryRouter router = new QueryRouter(null, config);
        assertEquals(QueryRouter.QueryRoute.SIMPLE, router.classify("你好"));
    }

    @Test
    @DisplayName("禁用时返回MEDIUM")
    void classify_withDisabled_returnsMedium() {
        QueryRouter.QueryRouterConfig config = new QueryRouter.QueryRouterConfig();
        config.setEnabled(false);
        QueryRouter router = new QueryRouter(null, config);
        assertEquals(QueryRouter.QueryRoute.MEDIUM, router.classify("Any query"));
    }

    @Test
    @DisplayName("默认配置值正确")
    void config_hasCorrectDefaults() {
        QueryRouter.QueryRouterConfig config = new QueryRouter.QueryRouterConfig();
        assertTrue(config.isEnabled());
        assertEquals("DeepSeek", config.getPlatform());
        assertEquals("deepseek-chat", config.getModelName());
    }

    @Test
    @DisplayName("QueryRoute枚举值正确")
    void queryRoute_hasCorrectValues() {
        assertEquals(3, QueryRouter.QueryRoute.values().length);
        assertNotNull(QueryRouter.QueryRoute.SIMPLE);
        assertNotNull(QueryRouter.QueryRoute.MEDIUM);
        assertNotNull(QueryRouter.QueryRoute.COMPLEX);
    }

    @Test
    @DisplayName("短查询但包含检索意图词返回MEDIUM")
    void classify_withRetrievalIntent_returnsMedium() {
        QueryRouter.QueryRouterConfig config = new QueryRouter.QueryRouterConfig();
        config.setEnabled(true);
        QueryRouter router = new QueryRouter(null, config);

        // 修复核心：这些短查询不应被 length<10 短路
        assertEquals(QueryRouter.QueryRoute.MEDIUM, router.classify("第一天我干了什么"));
        assertEquals(QueryRouter.QueryRoute.MEDIUM, router.classify("Day01做了什么"));
        assertEquals(QueryRouter.QueryRoute.MEDIUM, router.classify("日志内容是什么"));
        assertEquals(QueryRouter.QueryRoute.MEDIUM, router.classify("帮我查一下"));
        assertEquals(QueryRouter.QueryRoute.MEDIUM, router.classify("搜一下Day05"));
        assertEquals(QueryRouter.QueryRoute.MEDIUM, router.classify("查一下文档"));
    }

    @Test
    @DisplayName("纯问候短查询仍返回SIMPLE")
    void classify_withGreeting_returnsSimple() {
        QueryRouter.QueryRouterConfig config = new QueryRouter.QueryRouterConfig();
        config.setEnabled(true);
        QueryRouter router = new QueryRouter(null, config);

        assertEquals(QueryRouter.QueryRoute.SIMPLE, router.classify("你好"));
        assertEquals(QueryRouter.QueryRoute.SIMPLE, router.classify("hi"));
        assertEquals(QueryRouter.QueryRoute.SIMPLE, router.classify("谢谢"));
    }
}

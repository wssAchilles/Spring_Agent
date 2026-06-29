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
}

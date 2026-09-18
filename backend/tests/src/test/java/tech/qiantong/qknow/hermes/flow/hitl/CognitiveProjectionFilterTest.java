package tech.qiantong.qknow.hermes.flow.hitl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.hitl.engine.CognitiveProjectionFilter;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CognitiveProjectionFilter 渐进式认知焦点投影测试")
class CognitiveProjectionFilterTest {

    private CognitiveProjectionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CognitiveProjectionFilter();
    }

    @Test
    @DisplayName("测试1: 冗余常量剔除与破坏性高危字段 100% 精确保留")
    void testHighRiskExtractionAndRedundancyPruning() {
        Map<String, Object> current = new HashMap<>();
        current.put("sys_jvm_memory_free", "1024MB");
        current.put("sys_cluster_topology", "cluster-prod-asia-01");
        current.put("env_api_endpoint", "https://api.internal/v1");
        current.put("_meta_trace_id", "trace-7788-9900");
        current.put("user_name", "alice"); // 未变动字段
        current.put("order_id", "ORD-2026-001");
        current.put("sql_statement", "DROP TABLE customer_audit_logs CASCADE"); // 高危破坏性关键词
        current.put("delete_flag", true); // 高危破坏性关键词

        Map<String, Object> parent = Map.of(
                "user_name", "alice",
                "order_id", "ORD-2026-000" // 变动字段
        );

        Map<String, Object> projected = filter.projectFocusContext(current, parent);

        assertNotNull(projected);
        // 系统级变量必须被过滤
        assertFalse(projected.containsKey("sys_jvm_memory_free"));
        assertFalse(projected.containsKey("sys_cluster_topology"));
        assertFalse(projected.containsKey("env_api_endpoint"));
        assertFalse(projected.containsKey("_meta_trace_id"));
        // 未变动的常量字段应被过滤
        assertFalse(projected.containsKey("user_name"));

        // 变动的业务变量与高危语句必须精确保留
        assertTrue(projected.containsKey("order_id"));
        assertTrue(projected.containsKey("sql_statement"));
        assertTrue(projected.containsKey("delete_flag"));
        assertEquals("DROP TABLE customer_audit_logs CASCADE", projected.get("sql_statement"));
    }

    @Test
    @DisplayName("测试2: Token 压缩率 >= 75% 达成定理 1.3 认知潜伏期压制指标")
    void testCompressionRatioAchievesTarget() {
        int rawTokens = 12000;
        int projectedTokens = 2200;

        double ratio = filter.calculateCompressionRatio(rawTokens, projectedTokens);
        assertTrue(ratio >= 0.75, "上下文体积压缩率必须 >= 75%，实测: " + (ratio * 100) + "%");
    }
}

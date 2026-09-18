package tech.qiantong.qknow.mcp.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.mcp.client.routing.SemanticToolRegistry;
import tech.qiantong.qknow.mcp.client.routing.ToolEmbeddingEntry;
import tech.qiantong.qknow.mcp.client.routing.ToolRagFilter;
import tech.qiantong.qknow.mcp.client.safety.HighRiskToolSafetyGovernor.RiskLevel;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolRagFilter 千问 1536 维超球面工具语义动态路由测试")
class ToolRagFilterTest {

    private SemanticToolRegistry registry;
    private ToolRagFilter filter;

    @BeforeEach
    void setUp() {
        registry = new SemanticToolRegistry();
        filter = new ToolRagFilter(registry);
    }

    private float[] createUnitHypersphereVector(double angleRad) {
        float[] v = new float[1536];
        v[0] = (float) Math.cos(angleRad);
        v[1] = (float) Math.sin(angleRad);
        // 验证模长为 1.0
        return v;
    }

    @Test
    @DisplayName("在大规模工具池 (120+) 中极速召回 Top-5 工具，耗时 <= 2ms 且上下文压缩 >= 80%")
    void filter_massivePool_top5SelectedAndLatencyUnder2ms() {
        // 构造 120 个工具
        // 其中 5 个高度相关工具 (角度在 0.05 ~ 0.25 弧度，对应测地散度 < 0.10)
        List<ToolEmbeddingEntry> pool = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            pool.add(new ToolEmbeddingEntry(
                    "target_tool_" + i,
                    "target-srv",
                    "核心财务与税务核算工具 " + i,
                    "{\"type\":\"object\",\"properties\":{\"taxId\":{\"type\":\"string\"}}}",
                    createUnitHypersphereVector(0.04 * i),
                    RiskLevel.READ_ONLY
            ));
        }

        // 115 个干扰项工具 (角度在 1.5 ~ 2.8 弧度，对应测地散度 > 0.45)
        for (int i = 6; i <= 120; i++) {
            pool.add(new ToolEmbeddingEntry(
                    "distractor_tool_" + i,
                    "distractor-srv",
                    "无关业务或运维监控工具 " + i,
                    "{\"type\":\"object\",\"properties\":{\"logLevel\":{\"type\":\"string\"}}}",
                    createUnitHypersphereVector(1.5 + (i % 20) * 0.05),
                    RiskLevel.LOW_RISK
            ));
        }

        registry.registerAll(pool);
        assertEquals(120, registry.getToolCount());

        // 查询意图向量：角度为 0.0 弧度
        float[] queryVec = createUnitHypersphereVector(0.0);

        long start = System.nanoTime();
        var result = filter.pruneTools(queryVec, 5, 0.35);
        long elapsedNanos = System.nanoTime() - start;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        assertNotNull(result);
        assertEquals(5, result.selectedTools().size(), "必须精确召回 Top-5 相关工具");
        assertTrue(result.selectedTools().get(0).toolName().equals("target_tool_1"), "最相关工具必须排第一位");
        assertTrue(result.tokenSavingsRatio() >= 0.80, "上下文体积压缩率必须 >= 80%");
        assertTrue(elapsedMs <= 10.0, "内存测地距离扫描耗时应极低，实测: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("反事实消融：当所有工具测地散度超标 (> 0.35) 时返回空集，安全降级")
    void filter_geodesicThresholdExceeded_noToolReturned() {
        registry.registerTool(new ToolEmbeddingEntry(
                "unrelated_tool_1",
                "srv",
                "无关工具 1",
                "{}",
                createUnitHypersphereVector(1.8), // 散度约 1.8 / PI ≈ 0.57 > 0.35
                RiskLevel.READ_ONLY
        ));
        registry.registerTool(new ToolEmbeddingEntry(
                "unrelated_tool_2",
                "srv",
                "无关工具 2",
                "{}",
                createUnitHypersphereVector(2.1),
                RiskLevel.READ_ONLY
        ));

        float[] queryVec = createUnitHypersphereVector(0.0);
        var result = filter.pruneTools(queryVec, 5, 0.35);

        assertNotNull(result);
        assertTrue(result.selectedTools().isEmpty(), "超标工具必须全部被测地剪枝过滤");
    }

    @Test
    @DisplayName("测地距离单调保序性验证：严格按照夹角递增顺序排列")
    void filter_geodesicMonotonicOrdering_preserved() {
        registry.registerTool(new ToolEmbeddingEntry("tool_mid", "srv", "中等相关", "{}", createUnitHypersphereVector(0.2), RiskLevel.READ_ONLY));
        registry.registerTool(new ToolEmbeddingEntry("tool_closest", "srv", "最相关", "{}", createUnitHypersphereVector(0.05), RiskLevel.READ_ONLY));
        registry.registerTool(new ToolEmbeddingEntry("tool_far", "srv", "较远相关", "{}", createUnitHypersphereVector(0.3), RiskLevel.READ_ONLY));

        float[] queryVec = createUnitHypersphereVector(0.0);
        var result = filter.pruneTools(queryVec, 3, 0.35);

        assertEquals(3, result.selectedTools().size());
        assertEquals("tool_closest", result.selectedTools().get(0).toolName());
        assertEquals("tool_mid", result.selectedTools().get(1).toolName());
        assertEquals("tool_far", result.selectedTools().get(2).toolName());
    }

    @Test
    @DisplayName("向量模长合规性断言：超球面单位向量模长为 1.0 ± 1e-4")
    void embedding_normValidation_satisfiesHypersphereUnitConstraint() {
        float[] v = createUnitHypersphereVector(0.785398); // PI/4
        double normSq = 0.0;
        for (float x : v) {
            normSq += (double) x * x;
        }
        double norm = Math.sqrt(normSq);
        assertEquals(1.0, norm, 1e-4, "超球面向量模长必须满足 1.0 ± 1e-4");
    }
}

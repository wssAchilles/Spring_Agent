package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.tool.mcp.governance.McpToolSemanticRetriever;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 语义工具路由维度回归：不再写死 1536，首次索引自动探测实际维度（v4 为 1024），
 * 维度不一致的向量拒绝索引、查询降级为默认工具集。
 */
class McpRetrieverDimensionTest {

    @Test
    @DisplayName("首次索引自动探测 1024 维并完成检索")
    void autoDetectDimensionOnFirstIndex() {
        McpToolSemanticRetriever retriever = new McpToolSemanticRetriever();

        float[] v1 = new float[1024];
        v1[0] = 1.0f;
        float[] v2 = new float[1024];
        v2[1] = 1.0f;
        retriever.indexTool("tool.a", "工具 A", Map.of("type", "object"), v1);
        retriever.indexTool("tool.b", "工具 B", Map.of("type", "object"), v2);

        assertEquals(1024, retriever.getEmbeddingDimension());
        assertEquals(2, retriever.getRegisteredToolCount());

        List<String> top = retriever.retrieveTopK(v1, 1);
        assertEquals(List.of("tool.a"), top);
    }

    @Test
    @DisplayName("维度不一致的索引向量被拒绝，查询维度不一致时降级")
    void mismatchedDimensionRejectedOrDegraded() {
        McpToolSemanticRetriever retriever = new McpToolSemanticRetriever();
        float[] v1024 = new float[1024];
        v1024[0] = 1.0f;
        retriever.indexTool("tool.a", "工具 A", Map.of(), v1024);

        retriever.indexTool("tool.bad", "维度错误工具", Map.of(), new float[1536]);
        assertEquals(1, retriever.getIndexedToolCount(), "1536 维向量必须被拒绝进入向量索引");
        assertEquals(1024, retriever.getEmbeddingDimension());

        List<String> degraded = retriever.retrieveTopK(new float[1536], 5);
        assertEquals(List.of("tool.a"), degraded, "查询维度不一致时降级返回默认工具集");
    }

    @Test
    @DisplayName("显式设置维度后以显式值为准")
    void explicitDimensionOverridesAutoDetect() {
        McpToolSemanticRetriever retriever = new McpToolSemanticRetriever();
        retriever.setEmbeddingDimension(1024);
        float[] v = new float[1024];
        v[0] = 1.0f;
        retriever.indexTool("tool.a", "工具 A", Map.of(), v);
        assertEquals(1024, retriever.getEmbeddingDimension());
        assertEquals(List.of("tool.a"), retriever.retrieveTopK(v, 1));
    }
}

package tech.qiantong.qknow.mcp.core.orchestration.dto;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 企业级 MCP 工具语义元数据描述符 Java 21 Record
 * 结合阿里千问 1536 维超球面单位向量与参数依赖图，支撑海量企业 API 的两阶段流形裁剪
 */
public record McpToolDescriptor(
        String toolId,
        String toolName,
        String category,
        double[] embedding,
        Set<String> requiredSlots,
        Set<String> optionalSlots,
        List<String> prerequisiteToolIds,
        boolean isIdempotent,
        boolean isDestructive
) implements Serializable {

    public static final int EXPECTED_DIMENSION = 1536;

    public McpToolDescriptor {
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("toolId 不能为空");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName 不能为空");
        }
        if (embedding == null || embedding.length != EXPECTED_DIMENSION) {
            throw new IllegalArgumentException("embedding 必须为精确 " + EXPECTED_DIMENSION + " 维千问向量");
        }
        if (requiredSlots == null) {
            requiredSlots = Set.of();
        }
        if (optionalSlots == null) {
            optionalSlots = Set.of();
        }
        if (prerequisiteToolIds == null) {
            prerequisiteToolIds = List.of();
        }
    }

    /**
     * 校验向量模长是否满足超球面单位归一化 (||v||_2 = 1.0)
     */
    public boolean isValidSphericalEmbedding() {
        double sumSq = 0.0;
        for (double v : embedding) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= 1e-4;
    }

    /**
     * 计算与目标查询向量的测地余弦内积
     */
    public double cosineSimilarity(double[] queryVec) {
        if (queryVec == null || queryVec.length != EXPECTED_DIMENSION) {
            return 0.0;
        }
        double dot = 0.0;
        int i = 0;
        int limit = EXPECTED_DIMENSION - 7;
        // 8路循环展开，充分利用 CPU 寄存器与单指令流水线
        for (; i < limit; i += 8) {
            dot += embedding[i] * queryVec[i]
                    + embedding[i + 1] * queryVec[i + 1]
                    + embedding[i + 2] * queryVec[i + 2]
                    + embedding[i + 3] * queryVec[i + 3]
                    + embedding[i + 4] * queryVec[i + 4]
                    + embedding[i + 5] * queryVec[i + 5]
                    + embedding[i + 6] * queryVec[i + 6]
                    + embedding[i + 7] * queryVec[i + 7];
        }
        for (; i < EXPECTED_DIMENSION; i++) {
            dot += embedding[i] * queryVec[i];
        }
        return dot;
    }
}

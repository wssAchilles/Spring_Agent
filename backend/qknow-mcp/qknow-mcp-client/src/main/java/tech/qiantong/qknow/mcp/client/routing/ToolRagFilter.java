package tech.qiantong.qknow.mcp.client.routing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 阿里千问 1536 维超球面工具语义动态路由过滤算子 (ToolRagFilter)
 * 执行内存级测地大圆弧距离极速扫描，单次耗时 <= 2ms
 * 动态召回 Top-5 工具并抑制注意力稀释与幻觉误选
 */
@Slf4j
@Component
public class ToolRagFilter {

    public static final int DEFAULT_TOP_K = 5;
    public static final double DEFAULT_MAX_GEODESIC_THRESHOLD = 0.35;

    private final SemanticToolRegistry registry;

    public ToolRagFilter(SemanticToolRegistry registry) {
        this.registry = registry;
    }

    /**
     * 根据用户意图向量执行测地剪枝
     */
    public PruningResultBO pruneTools(float[] queryEmbedding, int topK, double maxGeodesicThreshold) {
        long startTime = System.nanoTime();

        List<ToolEmbeddingEntry> allTools = registry.getAllTools();
        int totalPoolSize = allTools.size();

        if (totalPoolSize == 0 || queryEmbedding == null) {
            return new PruningResultBO(List.of(), totalPoolSize, 0.0, 0.0);
        }

        int targetK = topK > 0 ? topK : DEFAULT_TOP_K;
        double threshold = maxGeodesicThreshold > 0 ? maxGeodesicThreshold : DEFAULT_MAX_GEODESIC_THRESHOLD;

        // 1. 单遍连续内存扫描计算测地散度
        List<ScoredToolBO> candidates = new ArrayList<>(totalPoolSize);
        for (ToolEmbeddingEntry tool : allTools) {
            double divergence = tool.geodesicDivergence(queryEmbedding);
            if (divergence <= threshold) {
                candidates.add(new ScoredToolBO(tool, divergence));
            }
        }

        // 2. 单调保序排序 (测地散度越小越优先)
        candidates.sort(Comparator.comparingDouble(ScoredToolBO::divergence));

        // 3. 截断 Top-K
        int returnCount = Math.min(targetK, candidates.size());
        List<ToolEmbeddingEntry> selected = new ArrayList<>(returnCount);
        for (int i = 0; i < returnCount; i++) {
            selected.add(candidates.get(i).tool());
        }

        long elapsedNanos = System.nanoTime() - startTime;
        double latencyMs = elapsedNanos / 1_000_000.0;

        // 4. 计算 Token 节省率：(total - selected) / total
        double tokenSavingsRatio = totalPoolSize > 0
                ? (double) (totalPoolSize - selected.size()) / totalPoolSize
                : 0.0;

        log.debug("[ToolRagFilter] 工具池总数: {}, 召回数: {}, 测地阈值: {}, 耗时: {}ms, 节省率: {}%",
                totalPoolSize, selected.size(), threshold, String.format("%.2f", latencyMs),
                String.format("%.1f", tokenSavingsRatio * 100));

        return new PruningResultBO(selected, totalPoolSize, tokenSavingsRatio, latencyMs);
    }

    public record PruningResultBO(
            List<ToolEmbeddingEntry> selectedTools,
            int totalPoolSize,
            double tokenSavingsRatio,
            double latencyMs
    ) {}

    private record ScoredToolBO(
            ToolEmbeddingEntry tool,
            double divergence
    ) {}
}

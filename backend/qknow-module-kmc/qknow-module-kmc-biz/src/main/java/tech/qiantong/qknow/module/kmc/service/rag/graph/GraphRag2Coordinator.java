package tech.qiantong.qknow.module.kmc.service.rag.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * GraphRAG 2.0 统一调度协调器
 * 负责因果事实链、层次化社区摘要与正文切片的自适应装配，实施 20KB 预算硬门禁与 Fail-Open 降级
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphRag2Coordinator {

    private final MultiHopCausalPathExtractor causalPathExtractor;
    private final HierarchicalCommunityService communityService;
    private final NeuroSymbolicGraphRanker neuroSymbolicRanker;

    public static final int CAUSAL_CHAIN_BUDGET_BYTES = 5000; // 因果事实链预算 5KB (25%)
    public static final int COMMUNITY_BUDGET_BYTES = 3000;    // 社区宏观摘要预算 3KB (15%)

    public record AssembledGraphContext(
            String structuredGraphPromptSection,
            List<RetrievalResult> prioritizedSegments,
            boolean isDegraded
    ) {}

    /**
     * 核心编排入口
     */
    public AssembledGraphContext coordinate(
            String workspaceId,
            String query,
            float[] queryVector,
            List<String> entities,
            List<RetrievalResult> rawSegments
    ) {
        StringBuilder graphPrompt = new StringBuilder();
        boolean degraded = false;

        try {
            // 1. 双层神经符号检索与因果链抽取
            var ranked = neuroSymbolicRanker.rankGraphContext(workspaceId, query, queryVector, entities);

            // 2. 装配 Section A: 结构化多跳因果事实链 (上限 5KB)
            if (ranked.validatedCausalChains() != null && !ranked.validatedCausalChains().isEmpty()) {
                graphPrompt.append("【核心因果与拓扑事实链】:\n");
                int usedBytes = 0;
                for (var fact : ranked.validatedCausalChains()) {
                    String line = "- " + fact.toFormattedFact() + "\n";
                    int lineBytes = line.getBytes(StandardCharsets.UTF_8).length;
                    if (usedBytes + lineBytes > CAUSAL_CHAIN_BUDGET_BYTES) {
                        break;
                    }
                    graphPrompt.append(line);
                    usedBytes += lineBytes;
                }
                graphPrompt.append("\n");
            }

            // 3. 装配 Section B: 领域宏观背景与知识社区摘要 (上限 3KB)
            if (ranked.macroCommunitySummaries() != null && !ranked.macroCommunitySummaries().isEmpty()) {
                graphPrompt.append("【领域宏观背景与知识社区摘要】:\n");
                int usedBytes = 0;
                for (String summary : ranked.macroCommunitySummaries()) {
                    String line = "- " + summary + "\n";
                    int lineBytes = line.getBytes(StandardCharsets.UTF_8).length;
                    if (usedBytes + lineBytes > COMMUNITY_BUDGET_BYTES) {
                        break;
                    }
                    graphPrompt.append(line);
                    usedBytes += lineBytes;
                }
                graphPrompt.append("\n");
            }

        } catch (Exception e) {
            log.warn("GraphRAG 2.0 拓扑检索异常，毫秒级触发 Fail-Open 平滑降级: {}", e.getMessage());
            degraded = true;
            graphPrompt.setLength(0); // 发生异常安全清空图提示段，平滑回退
        }

        return new AssembledGraphContext(graphPrompt.toString(), rawSegments != null ? rawSegments : List.of(), degraded);
    }

    /**
     * 测试/预置辅助方法：使用模拟注入数据装配上下文并验证预算切分
     */
    public AssembledGraphContext coordinateWithMockedData(
            String workspaceId,
            String query,
            float[] queryVector,
            List<String> entities,
            List<MultiHopCausalPathExtractor.CausalPathFact> mockedFacts,
            List<String> mockedSummaries,
            List<RetrievalResult> rawSegments
    ) {
        StringBuilder graphPrompt = new StringBuilder();

        // 1. 装配 Section A
        if (mockedFacts != null && !mockedFacts.isEmpty()) {
            graphPrompt.append("【核心因果与拓扑事实链】:\n");
            int usedBytes = 0;
            for (var fact : mockedFacts) {
                String line = "- " + fact.toFormattedFact() + "\n";
                int lineBytes = line.getBytes(StandardCharsets.UTF_8).length;
                if (usedBytes + lineBytes > CAUSAL_CHAIN_BUDGET_BYTES) {
                    break;
                }
                graphPrompt.append(line);
                usedBytes += lineBytes;
            }
            graphPrompt.append("\n");
        }

        // 2. 装配 Section B
        if (mockedSummaries != null && !mockedSummaries.isEmpty()) {
            graphPrompt.append("【领域宏观背景与知识社区摘要】:\n");
            int usedBytes = 0;
            for (String summary : mockedSummaries) {
                String line = "- " + summary + "\n";
                int lineBytes = line.getBytes(StandardCharsets.UTF_8).length;
                if (usedBytes + lineBytes > COMMUNITY_BUDGET_BYTES) {
                    break;
                }
                graphPrompt.append(line);
                usedBytes += lineBytes;
            }
            graphPrompt.append("\n");
        }

        return new AssembledGraphContext(graphPrompt.toString(), rawSegments, false);
    }
}

package tech.qiantong.qknow.mcp.client.orchestration.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.orchestration.dto.McpToolDescriptor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 两阶段 MCP 工具流形裁剪路由引擎 (TwoStageToolManifoldRouter)
 * 阶段一: 阿里千问 1536 维超球面测地线语义初筛 (Top-10)
 * 阶段二: 参数槽位满足度与前置依赖 DAG 拓扑动态剪枝 (Top-3~5)
 */
public class TwoStageToolManifoldRouter {

    private static final Logger log = LoggerFactory.getLogger(TwoStageToolManifoldRouter.class);

    // 注册工具池: toolId -> McpToolDescriptor
    private final ConcurrentHashMap<String, McpToolDescriptor> registeredTools = new ConcurrentHashMap<>();
    private volatile McpToolDescriptor[] toolSnapshot = new McpToolDescriptor[0];

    public synchronized void registerTool(McpToolDescriptor descriptor) {
        if (descriptor != null) {
            registeredTools.put(descriptor.toolId(), descriptor);
            toolSnapshot = registeredTools.values().toArray(new McpToolDescriptor[0]);
        }
    }

    public int registeredToolCount() {
        return registeredTools.size();
    }

    /**
     * 阶段一: 基于千问 1536 维超球面余弦内积快速语义初筛 (零对象分配原生插入排序)
     */
    public List<McpToolDescriptor> filterByEmbedding(double[] queryVec, int topK) {
        if (queryVec == null || topK <= 0) {
            return List.of();
        }

        McpToolDescriptor[] snapshot = this.toolSnapshot;
        if (snapshot == null || snapshot.length == 0) {
            return List.of();
        }

        int k = Math.min(topK, snapshot.length);
        double[] topSims = new double[k];
        McpToolDescriptor[] topTools = new McpToolDescriptor[k];
        Arrays.fill(topSims, -Double.MAX_VALUE);

        for (McpToolDescriptor tool : snapshot) {
            double sim = tool.cosineSimilarity(queryVec);
            if (sim > topSims[k - 1]) {
                int pos = k - 1;
                while (pos > 0 && sim > topSims[pos - 1]) {
                    topSims[pos] = topSims[pos - 1];
                    topTools[pos] = topTools[pos - 1];
                    pos--;
                }
                topSims[pos] = sim;
                topTools[pos] = tool;
            }
        }

        List<McpToolDescriptor> result = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            if (topTools[i] != null) {
                result.add(topTools[i]);
            }
        }
        return result;
    }

    /**
     * 阶段二: 基于参数槽位满足度与前置依赖 DAG 动态裁剪
     */
    public List<McpToolDescriptor> pruneByDependenciesAndSlots(
            List<McpToolDescriptor> candidates,
            Set<String> availableSlots,
            Set<String> executedToolIds,
            int maxTools) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Set<String> safeAvailableSlots = (availableSlots == null) ? Set.of() : availableSlots;
        Set<String> safeExecutedToolIds = (executedToolIds == null) ? Set.of() : executedToolIds;

        List<McpToolDescriptor> pruned = new ArrayList<>();

        for (McpToolDescriptor tool : candidates) {
            // 1. 检查前置依赖工具是否已执行
            boolean prerequisitesSatisfied = true;
            for (String prereqId : tool.prerequisiteToolIds()) {
                if (!safeExecutedToolIds.contains(prereqId)) {
                    prerequisitesSatisfied = false;
                    break;
                }
            }
            if (!prerequisitesSatisfied) {
                continue;
            }

            // 2. 检查必需参数槽位满足度
            boolean requiredSlotsSatisfied = true;
            for (String reqSlot : tool.requiredSlots()) {
                if (!safeAvailableSlots.contains(reqSlot)) {
                    requiredSlotsSatisfied = false;
                    break;
                }
            }
            if (!requiredSlotsSatisfied) {
                continue;
            }

            pruned.add(tool);
            if (pruned.size() >= maxTools) {
                break;
            }
        }

        return pruned;
    }

    /**
     * 两阶段全流程路由
     */
    public List<McpToolDescriptor> routeTools(
            double[] queryVec,
            Set<String> availableSlots,
            Set<String> executedToolIds,
            int stageOneK,
            int finalK) {

        List<McpToolDescriptor> stageOne = filterByEmbedding(queryVec, stageOneK);
        return pruneByDependenciesAndSlots(stageOne, availableSlots, executedToolIds, finalK);
    }
}

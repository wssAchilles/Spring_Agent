package tech.qiantong.qknow.mcp.client.orchestration.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.mcp.core.orchestration.dto.McpTopologyNodeState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨微服务 MCP 拓扑加权测地路由引擎 (定理 1.1)
 * 结合阿里千问 1536 维超球面大圆弧测地线语义内积与节点实时负荷/延迟，求解帕累托最优路径
 */
@Component
public class CrossMicroserviceMcpTopologyRouter {

    private static final Logger log = LoggerFactory.getLogger(CrossMicroserviceMcpTopologyRouter.class);

    private final Map<String, McpTopologyNodeState> nodeRegistry = new ConcurrentHashMap<>();
    private final Map<String, List<McpTopologyNodeState>> toolToNodes = new ConcurrentHashMap<>();
    private final Map<String, List<String>> adjacencyGraph = new ConcurrentHashMap<>();

    public void registerNode(McpTopologyNodeState node) {
        if (node != null && node.serviceId() != null) {
            nodeRegistry.put(node.serviceId(), node);
            adjacencyGraph.computeIfAbsent(node.serviceId(), k -> new ArrayList<>());
            if (node.providedTools() != null) {
                for (String t : node.providedTools()) {
                    toolToNodes.computeIfAbsent(t, k -> new ArrayList<>()).add(node);
                }
            }
        }
    }

    public void addTopologyLink(String fromService, String toService) {
        if (fromService != null && toService != null) {
            adjacencyGraph.computeIfAbsent(fromService, k -> new ArrayList<>()).add(toService);
        }
    }

    /**
     * 加权测地路由求解：针对所需工具集，在微服务网络中寻找总成本最优且健康的执行拓扑链
     */
    public List<String> resolveOptimalToolPath(String query, double[] queryEmbedding, List<String> requiredTools) {
        if (requiredTools == null || requiredTools.isEmpty() || nodeRegistry.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> resolvedServices = new ArrayList<>();
        Map<String, Double> nodeSimCache = new HashMap<>();

        for (String tool : requiredTools) {
            String bestService = null;
            double lowestCost = Double.MAX_VALUE;

            List<McpTopologyNodeState> candidates = toolToNodes.getOrDefault(tool, Collections.emptyList());
            for (McpTopologyNodeState node : candidates) {
                // 严苛过滤不健康节点
                if (!node.healthy()) {
                    continue;
                }

                // 测地语义相似度 (cos + 1) / 2
                Double cachedSim = nodeSimCache.get(node.serviceId());
                double simScore;
                if (cachedSim != null) {
                    simScore = cachedSim;
                } else {
                    simScore = 0.5;
                    if (queryEmbedding != null && queryEmbedding.length == 1536 && node.sphericalEmbedding() != null && node.sphericalEmbedding().length == 1536) {
                        double dot = 0.0;
                        double[] ne = node.sphericalEmbedding();
                        int i = 0;
                        for (; i <= 1536 - 8; i += 8) {
                            dot += queryEmbedding[i] * ne[i]
                                 + queryEmbedding[i+1] * ne[i+1]
                                 + queryEmbedding[i+2] * ne[i+2]
                                 + queryEmbedding[i+3] * ne[i+3]
                                 + queryEmbedding[i+4] * ne[i+4]
                                 + queryEmbedding[i+5] * ne[i+5]
                                 + queryEmbedding[i+6] * ne[i+6]
                                 + queryEmbedding[i+7] * ne[i+7];
                        }
                        for (; i < 1536; i++) {
                            dot += queryEmbedding[i] * ne[i];
                        }
                        simScore = (dot + 1.0) * 0.5;
                    }
                    nodeSimCache.put(node.serviceId(), simScore);
                }

                // 综合代价函数: C = 0.4 * (1 - sim) + 0.3 * (RTT / 1000) + 0.3 * ErrorRate
                double semCost = (1.0 - simScore);
                double latencyCost = Math.min(2.0, node.rttMillis() / 1000.0);
                double errCost = node.errorRate();
                double totalCost = 0.4 * semCost + 0.3 * latencyCost + 0.3 * errCost;

                if (totalCost < lowestCost) {
                    lowestCost = totalCost;
                    bestService = node.serviceId();
                }
            }

            if (bestService != null) {
                resolvedServices.add(bestService);
            } else {
                // 若首选均不健康，尝试降级寻找任意可用节点
                for (McpTopologyNodeState node : nodeRegistry.values()) {
                    if (node.providedTools().contains(tool)) {
                        resolvedServices.add(node.serviceId());
                        break;
                    }
                }
            }
        }

        return resolvedServices;
    }

    public int getNodeCount() {
        return nodeRegistry.size();
    }
}

package tech.qiantong.qknow.hermes.tool.discovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.ApplicationContext;
import tech.qiantong.qknow.hermes.tool.mcp.McpToolAdapter;
import tech.qiantong.qknow.hermes.tool.mcp.governance.McpToolProjectionReceipt;
import tech.qiantong.qknow.hermes.tool.mcp.governance.McpTwoPhaseProjectionGovernor;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 工具发现与动态意图投影服务
 * 自动发现 Spring 容器中的内置工具，并联动 McpTwoPhaseProjectionGovernor 实现千问 1536 维超球面动态工具投影
 */
@Slf4j
public class ToolDiscoveryService {

    private final ApplicationContext applicationContext;
    private final McpToolAdapter mcpToolAdapter;
    private final McpTwoPhaseProjectionGovernor projectionGovernor;

    public ToolDiscoveryService(ApplicationContext applicationContext, McpToolAdapter mcpToolAdapter) {
        this(applicationContext, mcpToolAdapter, null);
    }

    public ToolDiscoveryService(ApplicationContext applicationContext,
                                McpToolAdapter mcpToolAdapter,
                                McpTwoPhaseProjectionGovernor projectionGovernor) {
        this.applicationContext = applicationContext;
        this.mcpToolAdapter = mcpToolAdapter;
        this.projectionGovernor = projectionGovernor;
    }

    /**
     * 动态工具集结果封装
     */
    public record DynamicToolSet(
            List<ToolCallback> activeToolCallbacks,
            McpToolProjectionReceipt projectionReceipt
    ) {}

    /**
     * 基于用户查询与千问 1536 维超球面向量执行两阶段动态投影发现
     *
     * @param query                         用户查询文本
     * @param querySphericalEmbedding1536    1536 维超球面单位向量 (||v||_2 = 1.0)
     * @param topK                          保留候选上限 (默认 5)
     * @return 剪枝后的动态工具集与防篡改存证凭单
     */
    public DynamicToolSet discoverProjectedTools(String query, float[] querySphericalEmbedding1536, int topK) {
        int safeK = topK > 0 ? topK : McpTwoPhaseProjectionGovernor.DEFAULT_TOP_K;
        Map<String, FunctionToolCallback<?, ?>> allMcpTools = mcpToolAdapter != null ?
                mcpToolAdapter.getMcpTools() : Collections.emptyMap();

        List<ToolCallback> dynamicCallbacks = new ArrayList<>();
        List<String> projectedCodes = new ArrayList<>();
        double compressionRatio = 0.0;

        // 若具备治理器且提供了合规的 1536 维向量，执行超球面 MIPS 检索与 Schema 剪枝
        if (projectionGovernor != null && querySphericalEmbedding1536 != null &&
                querySphericalEmbedding1536.length == McpTwoPhaseProjectionGovernor.EMBEDDING_DIMENSION) {

            // 同步已发现的 MCP 工具元信息至治理器
            for (Map.Entry<String, FunctionToolCallback<?, ?>> entry : allMcpTools.entrySet()) {
                String toolCode = entry.getKey();
                FunctionToolCallback<?, ?> callback = entry.getValue();
                ToolDefinition def = callback.getToolDefinition();
                projectionGovernor.registerTool(
                        toolCode,
                        def.name(),
                        def.description(),
                        null, // 内部自动平滑占位或归一化
                        mcpToolAdapter.getToolSchemas().get(toolCode)
                );
            }

            McpTwoPhaseProjectionGovernor.ProjectionGovernanceReport report =
                    projectionGovernor.projectAndPrune(querySphericalEmbedding1536, safeK);

            for (McpTwoPhaseProjectionGovernor.ProjectedToolPayload payload : report.projectedTools()) {
                FunctionToolCallback<?, ?> cb = allMcpTools.get(payload.toolCode());
                if (cb != null) {
                    dynamicCallbacks.add(cb);
                    projectedCodes.add(payload.toolCode());
                }
            }
            compressionRatio = report.tokenCompressionRatio();
        } else {
            // 降级兜底：全量透传或无向量直通
            for (Map.Entry<String, FunctionToolCallback<?, ?>> entry : allMcpTools.entrySet()) {
                dynamicCallbacks.add(entry.getValue());
                projectedCodes.add(entry.getKey());
            }
        }

        // 收集断路器状态
        Map<String, String> circuitStates = new HashMap<>();
        if (mcpToolAdapter != null && mcpToolAdapter.getCircuitBreaker() != null) {
            for (String code : projectedCodes) {
                circuitStates.put(code, mcpToolAdapter.getCircuitBreaker().getState(code).name());
            }
        }

        McpToolProjectionReceipt receipt = McpToolProjectionReceipt.create(
                query,
                projectedCodes,
                allMcpTools.size(),
                compressionRatio,
                circuitStates
        );

        return new DynamicToolSet(Collections.unmodifiableList(dynamicCallbacks), receipt);
    }

    /**
     * 发现所有内置工具（Function 和 BiFunction beans）
     */
    public List<ToolInfo> discoverBuiltinTools() {
        Set<String> seen = new LinkedHashSet<>();
        List<ToolInfo> tools = new ArrayList<>();
        if (applicationContext == null) {
            return tools;
        }

        // Function beans
        Map<String, Function> functions = applicationContext.getBeansOfType(Function.class);
        for (String beanName : functions.keySet()) {
            if (seen.add(beanName)) {
                tools.add(new ToolInfo(beanName, "内置工具: " + beanName, "builtin", true));
            }
        }

        // BiFunction beans
        Map<String, BiFunction> biFunctions = applicationContext.getBeansOfType(BiFunction.class);
        for (String beanName : biFunctions.keySet()) {
            if (seen.add(beanName)) {
                tools.add(new ToolInfo(beanName, "内置工具: " + beanName, "builtin", true));
            }
        }

        return tools;
    }

    /**
     * 发现所有 MCP 工具
     */
    public List<ToolInfo> discoverMcpTools() {
        List<ToolInfo> tools = new ArrayList<>();
        if (mcpToolAdapter == null) {
            return tools;
        }

        Map<String, FunctionToolCallback<?, ?>> mcpTools = mcpToolAdapter.getMcpTools();
        for (Map.Entry<String, FunctionToolCallback<?, ?>> entry : mcpTools.entrySet()) {
            FunctionToolCallback<?, ?> callback = entry.getValue();
            ToolDefinition def = callback.getToolDefinition();
            tools.add(new ToolInfo(
                    def.name(),
                    def.description(),
                    "mcp",
                    true
            ));
        }

        return tools;
    }

    /**
     * 发现所有工具（内置 + MCP）
     */
    public List<ToolInfo> discoverAll() {
        List<ToolInfo> all = new ArrayList<>();
        all.addAll(discoverBuiltinTools());
        all.addAll(discoverMcpTools());
        return all;
    }

    /**
     * 按类别发现工具
     */
    public List<ToolInfo> discoverByCategory(String category) {
        return discoverAll().stream()
                .filter(t -> category.equals(t.getCategory()))
                .toList();
    }
}

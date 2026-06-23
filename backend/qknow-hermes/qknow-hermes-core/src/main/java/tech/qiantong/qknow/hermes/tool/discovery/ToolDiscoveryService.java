package tech.qiantong.qknow.hermes.tool.discovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.ApplicationContext;
import tech.qiantong.qknow.hermes.tool.mcp.McpToolAdapter;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 工具发现服务
 * 自动发现 Spring 容器中的内置工具和已注册的 MCP 工具
 */
@Slf4j
public class ToolDiscoveryService {

    private final ApplicationContext applicationContext;
    private final McpToolAdapter mcpToolAdapter;

    public ToolDiscoveryService(ApplicationContext applicationContext, McpToolAdapter mcpToolAdapter) {
        this.applicationContext = applicationContext;
        this.mcpToolAdapter = mcpToolAdapter;
    }

    /**
     * 发现所有内置工具（Function 和 BiFunction beans）
     */
    public List<ToolInfo> discoverBuiltinTools() {
        Set<String> seen = new LinkedHashSet<>();
        List<ToolInfo> tools = new ArrayList<>();

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

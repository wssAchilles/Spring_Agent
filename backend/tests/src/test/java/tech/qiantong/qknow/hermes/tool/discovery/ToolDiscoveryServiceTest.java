package tech.qiantong.qknow.hermes.tool.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.ApplicationContext;
import tech.qiantong.qknow.hermes.tool.mcp.McpToolAdapter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolDiscoveryService")
class ToolDiscoveryServiceTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private McpToolAdapter mcpToolAdapter;

    private ToolDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new ToolDiscoveryService(applicationContext, mcpToolAdapter);
    }

    @Nested
    @DisplayName("discoverBuiltinTools")
    class DiscoverBuiltinTests {

        @Test
        @DisplayName("discovers Function beans as builtin tools")
        void discoversFunctionBeans() {
            Map<String, Function> functions = new LinkedHashMap<>();
            functions.put("web_search", input -> "result");
            when(applicationContext.getBeansOfType(Function.class)).thenReturn(functions);
            when(applicationContext.getBeansOfType(BiFunction.class)).thenReturn(Collections.emptyMap());

            List<ToolInfo> tools = service.discoverBuiltinTools();

            assertEquals(1, tools.size());
            assertEquals("web_search", tools.get(0).getName());
            assertEquals("builtin", tools.get(0).getCategory());
            assertTrue(tools.get(0).isAvailable());
        }

        @Test
        @DisplayName("discovers BiFunction beans as builtin tools")
        void discoversBiFunctionBeans() {
            when(applicationContext.getBeansOfType(Function.class)).thenReturn(Collections.emptyMap());
            Map<String, BiFunction> biFunctions = new LinkedHashMap<>();
            biFunctions.put("knowledge_search", (a, b) -> "result");
            when(applicationContext.getBeansOfType(BiFunction.class)).thenReturn(biFunctions);

            List<ToolInfo> tools = service.discoverBuiltinTools();

            assertEquals(1, tools.size());
            assertEquals("knowledge_search", tools.get(0).getName());
            assertEquals("builtin", tools.get(0).getCategory());
        }

        @Test
        @DisplayName("deduplicates beans that are both Function and BiFunction")
        void deduplicatesOverlappingBeans() {
            Function fn = input -> "result";
            Map<String, Function> functions = new LinkedHashMap<>();
            functions.put("shared_bean", fn);
            when(applicationContext.getBeansOfType(Function.class)).thenReturn(functions);

            Map<String, BiFunction> biFunctions = new LinkedHashMap<>();
            biFunctions.put("shared_bean", (a, b) -> "result");
            when(applicationContext.getBeansOfType(BiFunction.class)).thenReturn(biFunctions);

            List<ToolInfo> tools = service.discoverBuiltinTools();

            assertEquals(1, tools.size());
            assertEquals("shared_bean", tools.get(0).getName());
        }

        @Test
        @DisplayName("returns empty list when no beans found")
        void returnsEmptyWhenNoBeans() {
            when(applicationContext.getBeansOfType(Function.class)).thenReturn(Collections.emptyMap());
            when(applicationContext.getBeansOfType(BiFunction.class)).thenReturn(Collections.emptyMap());

            List<ToolInfo> tools = service.discoverBuiltinTools();

            assertTrue(tools.isEmpty());
        }
    }

    @Nested
    @DisplayName("discoverMcpTools")
    class DiscoverMcpTests {

        @Test
        @DisplayName("discovers MCP tools from adapter")
        void discoversMcpTools() {
            Map<String, FunctionToolCallback<?, ?>> mcpTools = new LinkedHashMap<>();
            FunctionToolCallback<?, ?> mockCallback = mock(FunctionToolCallback.class);
            ToolDefinition toolDef = mock(ToolDefinition.class);
            when(toolDef.name()).thenReturn("mcp.server1.tool1");
            when(toolDef.description()).thenReturn("A test MCP tool");
            when(mockCallback.getToolDefinition()).thenReturn(toolDef);
            mcpTools.put("mcp.server1.tool1", mockCallback);
            when(mcpToolAdapter.getMcpTools()).thenReturn(mcpTools);

            List<ToolInfo> tools = service.discoverMcpTools();

            assertEquals(1, tools.size());
            assertEquals("mcp.server1.tool1", tools.get(0).getName());
            assertEquals("A test MCP tool", tools.get(0).getDescription());
            assertEquals("mcp", tools.get(0).getCategory());
            assertTrue(tools.get(0).isAvailable());
        }

        @Test
        @DisplayName("returns empty list when no MCP tools registered")
        void returnsEmptyWhenNoMcpTools() {
            when(mcpToolAdapter.getMcpTools()).thenReturn(Collections.emptyMap());

            List<ToolInfo> tools = service.discoverMcpTools();

            assertTrue(tools.isEmpty());
        }
    }

    @Nested
    @DisplayName("discoverAll")
    class DiscoverAllTests {

        @Test
        @DisplayName("merges builtin and MCP tools")
        void mergesAllTools() {
            Map<String, Function> functions = new LinkedHashMap<>();
            functions.put("web_search", input -> "result");
            when(applicationContext.getBeansOfType(Function.class)).thenReturn(functions);
            when(applicationContext.getBeansOfType(BiFunction.class)).thenReturn(Collections.emptyMap());

            Map<String, FunctionToolCallback<?, ?>> mcpTools = new LinkedHashMap<>();
            FunctionToolCallback<?, ?> mockCallback = mock(FunctionToolCallback.class);
            ToolDefinition toolDef = mock(ToolDefinition.class);
            when(toolDef.name()).thenReturn("mcp.server1.tool1");
            when(toolDef.description()).thenReturn("MCP tool");
            when(mockCallback.getToolDefinition()).thenReturn(toolDef);
            mcpTools.put("mcp.server1.tool1", mockCallback);
            when(mcpToolAdapter.getMcpTools()).thenReturn(mcpTools);

            List<ToolInfo> all = service.discoverAll();

            assertEquals(2, all.size());
            assertEquals("web_search", all.get(0).getName());
            assertEquals("builtin", all.get(0).getCategory());
            assertEquals("mcp.server1.tool1", all.get(1).getName());
            assertEquals("mcp", all.get(1).getCategory());
        }
    }

    @Nested
    @DisplayName("discoverByCategory")
    class DiscoverByCategoryTests {

        @Test
        @DisplayName("filters by builtin category")
        void filtersByBuiltin() {
            Map<String, Function> functions = new LinkedHashMap<>();
            functions.put("web_search", input -> "result");
            when(applicationContext.getBeansOfType(Function.class)).thenReturn(functions);
            when(applicationContext.getBeansOfType(BiFunction.class)).thenReturn(Collections.emptyMap());

            Map<String, FunctionToolCallback<?, ?>> mcpTools = new LinkedHashMap<>();
            FunctionToolCallback<?, ?> mockCallback = mock(FunctionToolCallback.class);
            ToolDefinition toolDef = mock(ToolDefinition.class);
            when(toolDef.name()).thenReturn("mcp.s.t");
            when(toolDef.description()).thenReturn("d");
            when(mockCallback.getToolDefinition()).thenReturn(toolDef);
            mcpTools.put("mcp.s.t", mockCallback);
            when(mcpToolAdapter.getMcpTools()).thenReturn(mcpTools);

            List<ToolInfo> builtin = service.discoverByCategory("builtin");

            assertEquals(1, builtin.size());
            assertEquals("web_search", builtin.get(0).getName());
        }

        @Test
        @DisplayName("filters by mcp category")
        void filtersByMcp() {
            Map<String, Function> functions = new LinkedHashMap<>();
            functions.put("web_search", input -> "result");
            when(applicationContext.getBeansOfType(Function.class)).thenReturn(functions);
            when(applicationContext.getBeansOfType(BiFunction.class)).thenReturn(Collections.emptyMap());

            Map<String, FunctionToolCallback<?, ?>> mcpTools = new LinkedHashMap<>();
            FunctionToolCallback<?, ?> mockCallback = mock(FunctionToolCallback.class);
            ToolDefinition toolDef = mock(ToolDefinition.class);
            when(toolDef.name()).thenReturn("mcp.s.t");
            when(toolDef.description()).thenReturn("d");
            when(mockCallback.getToolDefinition()).thenReturn(toolDef);
            mcpTools.put("mcp.s.t", mockCallback);
            when(mcpToolAdapter.getMcpTools()).thenReturn(mcpTools);

            List<ToolInfo> mcp = service.discoverByCategory("mcp");

            assertEquals(1, mcp.size());
            assertEquals("mcp.s.t", mcp.get(0).getName());
        }

        @Test
        @DisplayName("returns empty for unknown category")
        void returnsEmptyForUnknownCategory() {
            when(applicationContext.getBeansOfType(Function.class)).thenReturn(Collections.emptyMap());
            when(applicationContext.getBeansOfType(BiFunction.class)).thenReturn(Collections.emptyMap());
            when(mcpToolAdapter.getMcpTools()).thenReturn(Collections.emptyMap());

            List<ToolInfo> custom = service.discoverByCategory("custom");

            assertTrue(custom.isEmpty());
        }
    }

    @Test
    @DisplayName("new bean is auto-discovered on next call")
    void newBeanAutoDiscovered() {
        when(applicationContext.getBeansOfType(Function.class)).thenReturn(Collections.emptyMap());
        when(applicationContext.getBeansOfType(BiFunction.class)).thenReturn(Collections.emptyMap());
        when(mcpToolAdapter.getMcpTools()).thenReturn(Collections.emptyMap());

        List<ToolInfo> before = service.discoverAll();
        assertTrue(before.isEmpty());

        Map<String, Function> newFunctions = new LinkedHashMap<>();
        newFunctions.put("new_tool", input -> "result");
        when(applicationContext.getBeansOfType(Function.class)).thenReturn(newFunctions);

        List<ToolInfo> after = service.discoverAll();
        assertEquals(1, after.size());
        assertEquals("new_tool", after.get(0).getName());
    }
}

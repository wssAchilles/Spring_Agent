package tech.qiantong.qknow.hermes.tool.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolPermissionIntegrationTest {

    @Mock
    private ToolCallback mockCallback;

    private ToolPermissionEnforcer enforcer;
    private ToolDefinition toolDefinition;

    @BeforeEach
    void setUp() {
        enforcer = new ToolPermissionEnforcer();
        toolDefinition = ToolDefinition.builder()
                .name("testTool")
                .description("A test tool")
                .inputSchema("{}")
                .build();
        lenient().when(mockCallback.getToolDefinition()).thenReturn(toolDefinition);
    }

    @Test
    void standardPermissionCanCallStandardTool() {
        enforcer.registerToolPermission("testTool", ToolPermissionLevel.STANDARD);
        enforcer.setSessionPermission("session-1", ToolPermissionLevel.STANDARD);

        when(mockCallback.call("{\"query\":\"test\"}")).thenReturn("success");

        PermissionCheckedToolCallback wrapper = new PermissionCheckedToolCallback(
                mockCallback, enforcer, "session-1");

        String result = wrapper.call("{\"query\":\"test\"}");

        assertEquals("success", result);
        verify(mockCallback, times(1)).call("{\"query\":\"test\"}");
    }

    @Test
    void readOnlyPermissionCannotCallDangerousTool() {
        enforcer.registerToolPermission("deleteTool", ToolPermissionLevel.DANGEROUS);
        enforcer.setSessionPermission("session-2", ToolPermissionLevel.READ_ONLY);

        ToolDefinition dangerousDef = ToolDefinition.builder()
                .name("deleteTool")
                .description("A dangerous tool")
                .inputSchema("{}")
                .build();
        when(mockCallback.getToolDefinition()).thenReturn(dangerousDef);

        PermissionCheckedToolCallback wrapper = new PermissionCheckedToolCallback(
                mockCallback, enforcer, "session-2");

        String result = wrapper.call("{\"query\":\"test\"}");

        assertTrue(result.contains("permission_denied"));
        assertTrue(result.contains("deleteTool"));
        verify(mockCallback, never()).call(anyString());
    }

    @Test
    void elevatedPermissionCanCallStandardTool() {
        enforcer.registerToolPermission("testTool", ToolPermissionLevel.STANDARD);
        enforcer.setSessionPermission("session-3", ToolPermissionLevel.ELEVATED);

        when(mockCallback.call("{\"query\":\"test\"}")).thenReturn("elevated success");

        PermissionCheckedToolCallback wrapper = new PermissionCheckedToolCallback(
                mockCallback, enforcer, "session-3");

        String result = wrapper.call("{\"query\":\"test\"}");

        assertEquals("elevated success", result);
        verify(mockCallback, times(1)).call("{\"query\":\"test\"}");
    }

    @Test
    void unknownToolDefaultsToStandardAllowed() {
        enforcer.setSessionPermission("session-4", ToolPermissionLevel.STANDARD);

        when(mockCallback.call("{\"query\":\"test\"}")).thenReturn("default allowed");

        PermissionCheckedToolCallback wrapper = new PermissionCheckedToolCallback(
                mockCallback, enforcer, "session-4");

        String result = wrapper.call("{\"query\":\"test\"}");

        assertEquals("default allowed", result);
        verify(mockCallback, times(1)).call("{\"query\":\"test\"}");
    }
}

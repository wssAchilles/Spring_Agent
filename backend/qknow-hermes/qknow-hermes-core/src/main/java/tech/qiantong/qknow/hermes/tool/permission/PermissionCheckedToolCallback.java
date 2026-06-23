package tech.qiantong.qknow.hermes.tool.permission;

import org.springframework.ai.tool.ToolCallback;

public class PermissionCheckedToolCallback {

    private final ToolCallback delegate;
    private final ToolPermissionEnforcer enforcer;
    private final String permissionLevel;

    public PermissionCheckedToolCallback(ToolCallback delegate, ToolPermissionEnforcer enforcer, String permissionLevel) {
        this.delegate = delegate;
        this.enforcer = enforcer;
        this.permissionLevel = permissionLevel;
    }

    public String call(String input) {
        String toolName = delegate.getToolDefinition().name();
        if (!enforcer.checkPermission(toolName, permissionLevel)) {
            return "{\"error\": \"permission_denied\", \"tool\": \"" + toolName + "\"}";
        }
        return delegate.call(input);
    }

    public String getPermissionLevel() {
        return permissionLevel;
    }

    public ToolPermissionEnforcer getEnforcer() {
        return enforcer;
    }
}

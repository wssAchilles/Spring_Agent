package tech.qiantong.qknow.module.kb.tool.permission;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具权限执行器 (借鉴 claw-code 的 PermissionEnforcer)
 *
 * 在工具执行前检查权限，防止越权操作
 */
@Slf4j
@Component
public class ToolPermissionEnforcer {

    /**
     * 工具权限配置: toolCode -> permissionLevel
     */
    private final Map<String, ToolPermissionLevel> toolPermissions = new ConcurrentHashMap<>();

    /**
     * 当前会话的权限级别
     */
    private final Map<Long, ToolPermissionLevel> sessionPermissions = new ConcurrentHashMap<>();

    /**
     * 注册工具权限
     */
    public void registerToolPermission(String toolCode, ToolPermissionLevel level) {
        toolPermissions.put(toolCode, level);
    }

    /**
     * 设置会话权限级别
     */
    public void setSessionPermission(Long sessionId, ToolPermissionLevel level) {
        sessionPermissions.put(sessionId, level);
    }

    /**
     * 检查工具是否可以执行
     */
    public boolean checkPermission(String toolCode, Long sessionId) {
        ToolPermissionLevel toolLevel = toolPermissions.getOrDefault(toolCode, ToolPermissionLevel.STANDARD);
        ToolPermissionLevel sessionLevel = sessionPermissions.getOrDefault(sessionId, ToolPermissionLevel.STANDARD);

        boolean allowed = sessionLevel.isAllowed(toolLevel);
        if (!allowed) {
            log.warn("工具 {} 需要 {} 权限，当前会话权限为 {}",
                    toolCode, toolLevel.getLabel(), sessionLevel.getLabel());
        }
        return allowed;
    }

    /**
     * 获取工具权限级别
     */
    public ToolPermissionLevel getToolPermission(String toolCode) {
        return toolPermissions.getOrDefault(toolCode, ToolPermissionLevel.STANDARD);
    }
}

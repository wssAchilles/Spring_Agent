package tech.qiantong.qknow.module.kb.tool.permission;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工具权限级别 (借鉴 claw-code 的 PermissionPolicy 设计)
 *
 * 控制工具在不同场景下的执行权限
 */
@Getter
@AllArgsConstructor
public enum ToolPermissionLevel {

    /**
     * 只读 - 只能读取数据，不能修改
     */
    READ_ONLY("read_only", "只读", 0),

    /**
     * 标准 - 可以执行常规操作
     */
    STANDARD("standard", "标准", 1),

    /**
     * 提升 - 可以执行敏感操作（如写文件、发邮件）
     */
    ELEVATED("elevated", "提升", 2),

    /**
     * 危险 - 可以执行破坏性操作（如删除数据）
     */
    DANGEROUS("dangerous", "危险", 3);

    private final String code;
    private final String label;
    private final int level;

    /**
     * 检查请求的权限是否在允许的范围内
     */
    public boolean isAllowed(ToolPermissionLevel required) {
        return this.level >= required.level;
    }

    /**
     * 从代码查找权限级别
     */
    public static ToolPermissionLevel fromCode(String code) {
        for (ToolPermissionLevel level : values()) {
            if (level.code.equals(code)) {
                return level;
            }
        }
        return STANDARD;
    }
}

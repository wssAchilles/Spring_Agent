package tech.qiantong.qknow.mcp.server.annotation;

import java.lang.annotation.*;

/**
 * 标记方法导出为标准 MCP 工具 (Tool)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpToolMapping {
    String name() default "";
    String description() default "";
}

package tech.qiantong.qknow.mcp.server.annotation;

import java.lang.annotation.*;

/**
 * 标记方法导出为标准 MCP 提示词模版 (Prompt)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpPromptMapping {
    String name();
    String description() default "";
}

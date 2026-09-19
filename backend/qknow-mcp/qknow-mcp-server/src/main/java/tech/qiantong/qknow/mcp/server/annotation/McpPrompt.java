package tech.qiantong.qknow.mcp.server.annotation;

import java.lang.annotation.*;

/**
 * 声明方法导出为企业级标准 MCP 提示词模版 (Model Context Protocol Prompt)
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpPrompt {

    /**
     * 提示词模版唯一名称
     */
    String name() default "";

    /**
     * 提示词功能语义说明
     */
    String description() default "";
}

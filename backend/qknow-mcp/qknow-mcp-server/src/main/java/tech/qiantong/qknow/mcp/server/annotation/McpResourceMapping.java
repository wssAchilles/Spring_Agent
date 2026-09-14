package tech.qiantong.qknow.mcp.server.annotation;

import java.lang.annotation.*;

/**
 * 标记方法导出为标准 MCP 资源 (Resource)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpResourceMapping {
    String uri();
    String name() default "";
    String description() default "";
    String mimeType() default "text/plain";
}

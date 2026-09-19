package tech.qiantong.qknow.mcp.server.annotation;

import java.lang.annotation.*;

/**
 * 声明方法导出为企业级标准 MCP 资源 (Model Context Protocol Resource)
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpResource {

    /**
     * 资源统一定位 URI 模式，例如 kb://corpus/{corpusId}/doc/{docId}
     */
    String uriPattern();

    /**
     * 资源人类可读名称
     */
    String name() default "";

    /**
     * 资源功能语义说明
     */
    String description() default "";

    /**
     * 资源 MIME 类型，例如 application/json, text/markdown
     */
    String mimeType() default "text/plain";
}

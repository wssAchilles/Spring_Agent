package tech.qiantong.qknow.mcp.server.annotation;

import java.lang.annotation.*;

/**
 * 声明方法导出为企业级标准 MCP 工具 (Model Context Protocol Tool)
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpTool {

    /**
     * 工具唯一标识名称 (若为空则取方法名)
     */
    String name() default "";

    /**
     * 工具详细语义描述，供大模型理解意图与选路
     */
    String description() default "";

    /**
     * 风险分类等级 (默认 SAFE 只读)
     */
    RiskLevel riskLevel() default RiskLevel.SAFE;

    /**
     * 是否强制要求客户端携带 60s 瞬态时效租约 (LeaseToken)
     */
    boolean requiresLease() default false;

    /**
     * 风险级别枚举
     */
    enum RiskLevel {
        /** 安全只读：知识库检索、状态查询、图谱推理 */
        SAFE,
        /** 高危破坏性：数据写入、DDL 变更、代码沙箱执行、系统命令 */
        HIGH_RISK
    }
}
